#!/usr/bin/env python3
"""Compute hashes, update manifest, and upload Bukhari hadith audio files to R2."""

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
AUDIO_DIR = PROJECT_DIR / "audio" / "bukhari" / "bukhari_audio_bn"

CATEGORY = "bukhari_audio_bn"
CDN_PREFIX = "audio/bukhari/bn"


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
    """Scan all Bukhari audio files."""
    if not AUDIO_DIR.exists():
        print(f"  ERROR: {AUDIO_DIR} does not exist")
        sys.exit(1)

    files = sorted(AUDIO_DIR.iterdir())
    audio_files = [f for f in files if f.is_file() and not f.name.startswith('.')]
    print(f"  {CATEGORY}: {len(audio_files)} files in {AUDIO_DIR}")

    all_files = []
    for f in audio_files:
        size = f.stat().st_size
        cdn_key = f"{CDN_PREFIX}/{f.name}"
        # Determine content type based on extension
        ext = f.suffix.lower()
        if ext == ".mp3":
            content_type = "audio/mpeg"
        else:
            content_type = "audio/ogg"

        all_files.append({
            "cdn_key": cdn_key,
            "local_path": str(f),
            "size": size,
            "category": CATEGORY,
            "name": f.name,
            "content_type": content_type,
        })

    return all_files


def compute_hashes(files):
    """Compute SHA-256 for all files with progress."""
    total = len(files)
    for i, f in enumerate(files, 1):
        if i % 100 == 0 or i == total:
            print(f"\r  [{i}/{total}] Hashing {f['name'][:50]}...", end="", flush=True)
        f["sha256"] = sha256_file(f["local_path"])
    print()
    return files


def update_manifest(files):
    """Update manifest.json with Bukhari audio file entries."""
    with open(MANIFEST_FILE) as mf:
        manifest = json.load(mf)

    # Add category summary
    manifest["categories"][CATEGORY] = {
        "total_size": sum(f["size"] for f in files),
        "file_count": len(files),
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
        if upload_file(f["cdn_key"], f["local_path"], content_type=f["content_type"]):
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
    parser = argparse.ArgumentParser(description="Upload Bukhari hadith audio to R2")
    parser.add_argument("--hash-only", action="store_true", help="Only compute hashes and update manifest")
    parser.add_argument("--upload-only", action="store_true", help="Skip hashing, just upload")
    parser.add_argument("--start-from", type=int, default=0, help="Start upload from file index N")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be uploaded")
    args = parser.parse_args()

    print("=" * 60)
    print("  Bukhari Hadith Audio -> R2 Upload")
    print("=" * 60)

    # Step 1: Scan files
    print("\n[1] Scanning audio files...")
    all_files = scan_audio_files()

    total_size = sum(f["size"] for f in all_files)
    print(f"  Total: {len(all_files)} files, {format_size(total_size)}")

    if args.dry_run:
        for f in all_files[:20]:
            print(f"  {f['cdn_key']} ({format_size(f['size'])})")
        if len(all_files) > 20:
            print(f"  ... and {len(all_files) - 20} more files")
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
