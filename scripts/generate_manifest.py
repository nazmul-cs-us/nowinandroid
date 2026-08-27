#!/usr/bin/env python3
"""
Generate manifest.json for Cloudflare R2 asset CDN.

Scans the assets directory, computes SHA-256 checksums, and produces a manifest
file that the Android app uses to know what to download and verify integrity.

Usage:
    python3 scripts/generate_manifest.py
    python3 scripts/generate_manifest.py --base-url https://cdn.starception.com
    python3 scripts/generate_manifest.py --output /tmp/manifest.json

IMPORTANT — partial vs full manifest:
    This script only sees files that are actually present under app/src/main/assets. The
    full CDN manifest describes ~6400 files (audio recitations, TTS/ASR models, hadith and
    translation DBs) that are NOT kept on disk locally. Running this script from a sparse
    checkout therefore produces a PARTIAL manifest that would drop those entries if uploaded.

    For a targeted update of a single asset (e.g. regenerating news.db), do NOT regenerate
    from scratch. Instead patch that one asset's size+sha256 into the live CDN manifest:
        curl -s <base_url>/manifest.json -o /tmp/cdn_manifest.json
        # update assets["databases/news.db"].{size,sha256} + total_size, write scripts/manifest.json
    Only run a full regenerate when every CDN asset is present locally.
"""

import hashlib
import json
import os
import sys
import argparse
from pathlib import Path

ASSETS_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "assets"

# Files that STAY bundled in the APK (not uploaded to CDN)
BUNDLED_FILES = {
    "country_prayer_methods.json",
    "salah_detector.tflite",
    "salah_norm_params.json",
    # Read directly from the APK by IndoPakTextRepository. Listing it in the CDN
    # manifest would make the downloader fetch a duplicate copy.
    "databases/quran_indopak.db",
    # v2 is a local build input for news.db (see scripts/generate_news_db.py); the app
    # never downloads it, so it must NOT be uploaded to the CDN.
    "databases/fortress_of_the_muslim_v2.db",
    "databases/quranic_duas.db",
    "databases/topics.db",
    "databases/hadith/hadith_index.db",
    "tts/tokens.txt",
    "tts/MODEL_CARD",
}

# Category mapping: asset path prefix -> category name
CATEGORY_RULES = [
    ("databases/quran_enhanced.db", "quran_core"),
    ("databases/quran.db", "quran_core"),
    ("databases/quran_", "quran_translation"),
    ("databases/hadith/sahih_bukhari.db", "hadith_sahih_bukhari"),
    ("databases/hadith/sahih_muslim.db", "hadith_sahih_muslim"),
    ("databases/hadith/sunan_abu_dawud.db", "hadith_sunan_abu_dawud"),
    ("databases/hadith/sunan_tirmidhi.db", "hadith_sunan_tirmidhi"),
    ("databases/hadith/sunan_nasai.db", "hadith_sunan_nasai"),
    ("databases/hadith/sunan_ibn_majah.db", "hadith_sunan_ibn_majah"),
    ("databases/hadith/musnad_ahmad.db", "hadith_musnad_ahmad"),
    ("databases/hadith/muwatta_malik.db", "hadith_muwatta_malik"),
    ("databases/hadith/sunan_darimi.db", "hadith_sunan_darimi"),
    ("databases/news.db", "news"),
    ("audio/fortress/arabic", "fortress_audio_arabic"),
    ("databases/fortress_of_the_muslim", "dua"),
    ("databases/quranic_duas.db", "dua"),
    ("databases/topics.db", "content"),
    ("sahih_bukhari.json", "json_data"),
    ("tajweed.json", "json_data"),
    ("fortress_of_the_muslim.json", "json_data"),
    ("sherpa/", "model_asr"),
    ("kws/", "model_kws"),
    ("whisper/", "model_whisper"),
    ("models/", "model_whisper"),
    ("tts/kokoro", "model_tts_kokoro"),
    ("tts/vits", "model_tts_vits"),
    ("tts/en_US-ryan", "model_tts_ryan"),
    ("tts/espeak-ng-data", "model_tts_espeak"),
    ("tts/", "model_tts"),
]

# Required assets that must be downloaded at first launch
REQUIRED_CATEGORIES = {"quran_core", "json_data", "news"}

# CDN path mapping: local asset path -> CDN key
def get_cdn_key(asset_rel_path: str) -> str:
    """Map local asset path to CDN object key."""
    rel = asset_rel_path

    # Audio (recitations) — CDN key mirrors the local path, e.g.
    # audio/fortress/arabic/001.mp3, audio/quran/arabic/...
    if rel.startswith("audio/"):
        return rel

    # Databases
    if rel.startswith("databases/hadith/"):
        return rel  # Keep as databases/hadith/file.db
    if rel.startswith("databases/quran"):
        filename = os.path.basename(rel)
        return f"databases/quran/{filename}"
    if rel.startswith("databases/"):
        return rel

    # JSON files
    if rel.endswith(".json") and not rel.startswith("tts/"):
        return f"json/{os.path.basename(rel)}"

    # ML models
    if rel.startswith("sherpa/"):
        return f"models/{rel}"
    if rel.startswith("kws/"):
        return f"models/{rel}"
    if rel.startswith("whisper/"):
        return f"models/{rel}"
    if rel.startswith("models/"):
        return rel  # Already has models/ prefix

    # TTS models
    if rel.startswith("tts/"):
        return f"models/{rel}"

    return rel


def get_category(asset_rel_path: str) -> str:
    """Determine the category for an asset."""
    for prefix, category in CATEGORY_RULES:
        if asset_rel_path.startswith(prefix) or asset_rel_path == prefix:
            return category
    return "other"


def sha256_file(filepath: Path) -> str:
    """Compute SHA-256 hash of a file."""
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def should_skip(rel_path: str) -> bool:
    """Check if file should be skipped (stays bundled or is metadata)."""
    if rel_path in BUNDLED_FILES:
        return True
    if rel_path.startswith("."):
        return True
    if rel_path.endswith(".DS_Store"):
        return True
    return False


def scan_assets(assets_dir: Path) -> list:
    """Scan assets directory and collect file info."""
    entries = []

    for root, dirs, files in os.walk(assets_dir):
        # Skip hidden directories
        dirs[:] = [d for d in dirs if not d.startswith(".")]

        for filename in sorted(files):
            if filename.startswith("."):
                continue

            filepath = Path(root) / filename
            rel_path = str(filepath.relative_to(assets_dir))

            if should_skip(rel_path):
                continue

            size = filepath.stat().st_size
            checksum = sha256_file(filepath)
            cdn_key = get_cdn_key(rel_path)
            category = get_category(rel_path)

            entries.append({
                "local_path": rel_path,
                "cdn_key": cdn_key,
                "size": size,
                "sha256": checksum,
                "category": category,
                "required": category in REQUIRED_CATEGORIES,
            })

    return entries


def generate_manifest(assets_dir: Path, base_url: str) -> dict:
    """Generate the complete manifest."""
    entries = scan_assets(assets_dir)

    # Build assets map keyed by cdn_key
    assets_map = {}
    for entry in entries:
        assets_map[entry["cdn_key"]] = {
            "size": entry["size"],
            "sha256": entry["sha256"],
            "category": entry["category"],
            "required": entry["required"],
        }

    # Compute category summaries
    categories = {}
    for entry in entries:
        cat = entry["category"]
        if cat not in categories:
            categories[cat] = {"total_size": 0, "file_count": 0, "required": entry["required"]}
        categories[cat]["total_size"] += entry["size"]
        categories[cat]["file_count"] += 1

    total_size = sum(e["size"] for e in entries)

    manifest = {
        "version": 1,
        "base_url": base_url,
        "total_size": total_size,
        "total_files": len(entries),
        "categories": categories,
        "assets": assets_map,
    }

    return manifest


def main():
    parser = argparse.ArgumentParser(description="Generate Cloudflare R2 asset manifest")
    parser.add_argument(
        "--base-url",
        default="https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev",
        help="Base URL for the R2 bucket (update after creating bucket)",
    )
    parser.add_argument(
        "--output",
        default=str(Path(__file__).parent / "manifest.json"),
        help="Output path for manifest.json",
    )
    parser.add_argument(
        "--assets-dir",
        default=str(ASSETS_DIR),
        help="Path to assets directory",
    )
    args = parser.parse_args()

    assets_dir = Path(args.assets_dir)
    if not assets_dir.exists():
        print(f"Error: Assets directory not found: {assets_dir}")
        sys.exit(1)

    print(f"Scanning assets in: {assets_dir}")
    manifest = generate_manifest(assets_dir, args.base_url)

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with open(output_path, "w") as f:
        json.dump(manifest, f, indent=2)

    print(f"\nManifest written to: {output_path}")
    print(f"Total files: {manifest['total_files']}")
    print(f"Total size: {manifest['total_size'] / (1024*1024):.1f} MB")
    print(f"\nCategories:")
    for cat, info in sorted(manifest["categories"].items()):
        req = " (REQUIRED)" if info["required"] else ""
        print(f"  {cat}: {info['file_count']} files, {info['total_size'] / (1024*1024):.1f} MB{req}")

    # Also print the list of files that will stay bundled
    print(f"\nFiles staying bundled in APK:")
    for f in sorted(BUNDLED_FILES):
        fp = assets_dir / f
        if fp.exists():
            size = fp.stat().st_size
            print(f"  {f} ({size / 1024:.0f} KB)")
        else:
            print(f"  {f} (not found)")


if __name__ == "__main__":
    main()
