#!/usr/bin/env python3
"""Compute hashes, update manifest, and upload Quran audio files to R2."""

import hashlib
import json
import os
import sys
from pathlib import Path

# Import upload function from s3_upload
sys.path.insert(0, str(Path(__file__).parent))
from s3_upload import upload_file, format_size, MANIFEST_FILE

SCRIPT_DIR = Path(__file__).parent
PROJECT_DIR = SCRIPT_DIR.parent
AUDIO_DIR = PROJECT_DIR / "audio" / "quran"

# Language configs: (local_subdir, cdn_prefix, category, actual_nested_dir)
LANGUAGES = [
    ("arabic", "audio/quran/arabic", "quran_audio_arabic", "Arabic"),
    ("bengali", "audio/quran/bengali", "quran_audio_bengali", "Bengali"),
    ("english", "audio/quran/english", "quran_audio_english", "English"),
]


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(8192)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def scan_audio_files():
    """Scan all audio files and compute hashes."""
    all_files = []
    for local_subdir, cdn_prefix, category, nested_dir in LANGUAGES:
        lang_dir = AUDIO_DIR / local_subdir / nested_dir
        if not lang_dir.exists():
            print(f"  WARNING: {lang_dir} does not exist, skipping")
            continue

        files = sorted(lang_dir.iterdir())
        audio_files = [f for f in files if f.is_file() and not f.name.startswith('.')]
        print(f"  {category}: {len(audio_files)} files in {lang_dir}")

        for f in audio_files:
            size = f.stat().st_size
            cdn_key = f"{cdn_prefix}/{f.name}"
            all_files.append({
                "cdn_key": cdn_key,
                "local_path": str(f),
                "size": size,
                "category": category,
                "name": f.name,
            })

    return all_files


def compute_hashes(files):
    """Compute SHA-256 for all files with progress."""
    total = len(files)
    for i, f in enumerate(files, 1):
        print(f"\r  [{i}/{total}] Hashing {f['name'][:50]}...", end="", flush=True)
        f["sha256"] = sha256_file(f["local_path"])
    print()
    return files


def update_manifest(files):
    """Update manifest.json with audio file entries."""
    with open(MANIFEST_FILE) as mf:
        manifest = json.load(mf)

    # Add category summaries
    for _, _, category, _ in LANGUAGES:
        cat_files = [f for f in files if f["category"] == category]
        if cat_files:
            manifest["categories"][category] = {
                "total_size": sum(f["size"] for f in cat_files),
                "file_count": len(cat_files),
                "required": False,
            }

    # Add individual file entries
    for f in files:
        manifest["assets"][f["cdn_key"]] = {
            "size": f["size"],
            "sha256": f["sha256"],
            "category": f["category"],
            "required": False,
        }

    # Update totals
    manifest["total_size"] = sum(
        info["size"] for info in manifest["assets"].values()
    )
    manifest["total_files"] = len(manifest["assets"])

    with open(MANIFEST_FILE, "w") as mf:
        json.dump(manifest, mf, indent=2, ensure_ascii=False)

    print(f"  Manifest updated: {manifest['total_files']} files, {format_size(manifest['total_size'])}")
    return manifest


def upload_all(files, start_from=0):
    """Upload all files to R2."""
    total = len(files)
    uploaded = 0
    failed = 0
    skipped = 0

    for i, f in enumerate(files):
        if i < start_from:
            skipped += 1
            continue

        size_str = format_size(f["size"])
        print(f"  [{i+1}/{total}] {f['cdn_key'][:70]} ({size_str})... ", end="", flush=True)
        if upload_file(f["cdn_key"], f["local_path"], content_type="audio/ogg"):
            print("OK")
            uploaded += 1
        else:
            print("FAILED")
            failed += 1

    print()
    print(f"  Uploaded: {uploaded}")
    if failed:
        print(f"  Failed:   {failed}")
    if skipped:
        print(f"  Skipped:  {skipped}")
    return failed


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Upload Quran audio to R2")
    parser.add_argument("--hash-only", action="store_true", help="Only compute hashes and update manifest")
    parser.add_argument("--upload-only", action="store_true", help="Skip hashing, just upload")
    parser.add_argument("--start-from", type=int, default=0, help="Start upload from file index N")
    parser.add_argument("--lang", choices=["arabic", "bengali", "english"], help="Only process one language")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be uploaded")
    args = parser.parse_args()

    print("=" * 60)
    print("  Quran Audio → R2 Upload")
    print("=" * 60)

    # Step 1: Scan files
    print("\n[1] Scanning audio files...")
    all_files = scan_audio_files()

    if args.lang:
        cat = f"quran_audio_{args.lang}"
        all_files = [f for f in all_files if f["category"] == cat]

    total_size = sum(f["size"] for f in all_files)
    print(f"  Total: {len(all_files)} files, {format_size(total_size)}")

    if args.dry_run:
        for f in all_files:
            print(f"  {f['cdn_key']} ({format_size(f['size'])})")
        return

    if not args.upload_only:
        # Step 2: Compute hashes
        print("\n[2] Computing SHA-256 hashes...")
        all_files = compute_hashes(all_files)

        # Step 3: Update manifest
        print("\n[3] Updating manifest.json...")
        update_manifest(all_files)

    if args.hash_only:
        print("\n  Done (hash-only mode)")
        return

    if args.upload_only:
        # Reload hashes from manifest
        with open(MANIFEST_FILE) as mf:
            manifest = json.load(mf)
        for f in all_files:
            if f["cdn_key"] in manifest["assets"]:
                f["sha256"] = manifest["assets"][f["cdn_key"]]["sha256"]

    # Step 4: Upload
    print(f"\n[4] Uploading {len(all_files)} files to R2...")
    failed = upload_all(all_files, start_from=args.start_from)

    if failed:
        print(f"\n  {failed} files failed. Re-run with --upload-only to retry.")
        sys.exit(1)

    # Step 5: Upload updated manifest
    print("\n[5] Uploading manifest.json...")
    if upload_file("manifest.json", str(MANIFEST_FILE), content_type="application/json"):
        print("  manifest.json uploaded OK")
    else:
        print("  manifest.json upload FAILED")

    print("\n  All done!")


if __name__ == "__main__":
    main()
