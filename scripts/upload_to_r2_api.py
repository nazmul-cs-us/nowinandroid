#!/usr/bin/env python3
"""
Upload assets to Cloudflare R2 using the S3-compatible API.

This script reads the manifest.json, maps CDN keys back to local file paths,
and uploads each file to the R2 bucket.

Usage:
    python3 scripts/upload_to_r2_api.py --token YOUR_API_TOKEN
    python3 scripts/upload_to_r2_api.py --token YOUR_API_TOKEN --dry-run
    python3 scripts/upload_to_r2_api.py --token YOUR_API_TOKEN --category model_kws
"""

import json
import os
import sys
import hashlib
import argparse
import subprocess
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
PROJECT_DIR = SCRIPT_DIR.parent
ASSETS_DIR = PROJECT_DIR / "app" / "src" / "main" / "assets"
MANIFEST_FILE = SCRIPT_DIR / "manifest.json"

ACCOUNT_ID = "a535d591f409b4a31c39625dc1ffd6c7"
BUCKET_NAME = "starception-assets"


def cdn_key_to_local_path(cdn_key: str) -> str:
    """Reverse-map CDN key to local asset path."""
    # json/ prefix -> root level
    if cdn_key.startswith("json/"):
        return cdn_key[5:]  # Remove json/ prefix

    # models/sherpa/ -> sherpa/
    if cdn_key.startswith("models/sherpa/"):
        return cdn_key[7:]  # Remove models/ prefix
    if cdn_key.startswith("models/kws/"):
        return cdn_key[7:]
    if cdn_key.startswith("models/whisper/"):
        return cdn_key[7:]
    if cdn_key.startswith("models/tts/"):
        return cdn_key[7:]
    if cdn_key.startswith("models/"):
        return cdn_key  # Keep as-is (e.g. models/ggml-tiny.en.bin)

    # databases/quran/file.db -> databases/file.db
    if cdn_key.startswith("databases/quran/"):
        return "databases/" + os.path.basename(cdn_key)

    return cdn_key


def format_size(size_bytes: int) -> str:
    """Format bytes to human-readable."""
    if size_bytes >= 1024 * 1024 * 1024:
        return f"{size_bytes / (1024*1024*1024):.1f} GB"
    elif size_bytes >= 1024 * 1024:
        return f"{size_bytes / (1024*1024):.1f} MB"
    elif size_bytes >= 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes} B"


def upload_file(cdn_key: str, local_path: str, token: str) -> bool:
    """Upload a single file to R2 using wrangler."""
    try:
        env = os.environ.copy()
        env["CLOUDFLARE_API_TOKEN"] = token

        result = subprocess.run(
            [
                "wrangler", "r2", "object", "put",
                f"{BUCKET_NAME}/{cdn_key}",
                f"--file={local_path}",
                "--content-type=application/octet-stream",
                # --remote is REQUIRED: without it wrangler (v3+) writes to the LOCAL
                # simulator, not the real R2 bucket.
                "--remote",
            ],
            capture_output=True,
            text=True,
            env=env,
            timeout=300,
        )
        return result.returncode == 0
    except Exception as e:
        print(f"    Error: {e}")
        return False


def upload_file_curl(cdn_key: str, local_path: str, token: str) -> bool:
    """Upload a single file to R2 using curl and the Cloudflare API."""
    try:
        url = f"https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/r2/buckets/{BUCKET_NAME}/objects/{cdn_key}"
        result = subprocess.run(
            [
                "curl", "-s", "-X", "PUT", url,
                "-H", f"Authorization: Bearer {token}",
                "-H", "Content-Type: application/octet-stream",
                "--data-binary", f"@{local_path}",
                "--max-time", "300",
            ],
            capture_output=True,
            text=True,
            timeout=600,
        )
        if result.returncode == 0:
            try:
                resp = json.loads(result.stdout)
                return resp.get("success", False)
            except json.JSONDecodeError:
                # Non-JSON response might still be OK
                return result.returncode == 0
        return False
    except Exception as e:
        print(f"    Error: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Upload assets to Cloudflare R2")
    parser.add_argument("--token", required=True, help="Cloudflare API token")
    parser.add_argument("--dry-run", action="store_true", help="Preview without uploading")
    parser.add_argument("--category", help="Only upload this category")
    parser.add_argument("--use-curl", action="store_true", help="Use curl instead of wrangler")
    args = parser.parse_args()

    if not MANIFEST_FILE.exists():
        print(f"Error: Manifest not found at {MANIFEST_FILE}")
        sys.exit(1)

    with open(MANIFEST_FILE) as f:
        manifest = json.load(f)

    assets = manifest["assets"]

    # Also check for news.db in contentdatabase module
    content_db_dir = PROJECT_DIR / "core" / "contentdatabase" / "src" / "main" / "assets"

    # Build upload list
    upload_list = []
    missing_list = []

    for cdn_key, info in sorted(assets.items()):
        if args.category and info["category"] != args.category:
            continue

        local_rel = cdn_key_to_local_path(cdn_key)
        local_path = ASSETS_DIR / local_rel

        # Also check content DB module for news.db
        if not local_path.exists() and cdn_key.startswith("databases/"):
            alt_path = content_db_dir / local_rel
            if alt_path.exists():
                local_path = alt_path

        if local_path.exists():
            upload_list.append((cdn_key, str(local_path), info))
        else:
            missing_list.append((cdn_key, local_rel, info))

    # Summary
    total_upload_size = sum(info["size"] for _, _, info in upload_list)
    total_missing_size = sum(info["size"] for _, _, info in missing_list)

    print("=" * 60)
    print("  Cloudflare R2 Asset Upload")
    print("=" * 60)
    print(f"  Bucket:     {BUCKET_NAME}")
    print(f"  Account:    {ACCOUNT_ID}")
    print(f"  Assets dir: {ASSETS_DIR}")
    print(f"  Upload:     {len(upload_list)} files ({format_size(total_upload_size)})")
    print(f"  Missing:    {len(missing_list)} files ({format_size(total_missing_size)})")
    if args.category:
        print(f"  Category:   {args.category}")
    if args.dry_run:
        print(f"  Mode:       DRY RUN")
    print()

    if missing_list:
        print(f"Missing files ({len(missing_list)}):")
        # Group by category
        cats = {}
        for cdn_key, local_rel, info in missing_list:
            cat = info["category"]
            cats.setdefault(cat, []).append((cdn_key, format_size(info["size"])))
        for cat, files in sorted(cats.items()):
            print(f"  [{cat}] ({len(files)} files)")
            for cdn_key, size in files[:3]:
                print(f"    - {cdn_key} ({size})")
            if len(files) > 3:
                print(f"    ... and {len(files)-3} more")
        print()

    if args.dry_run:
        print(f"Files to upload ({len(upload_list)}):")
        cats = {}
        for cdn_key, local_path, info in upload_list:
            cat = info["category"]
            cats.setdefault(cat, []).append((cdn_key, format_size(info["size"])))
        for cat, files in sorted(cats.items()):
            cat_size = sum(info["size"] for k, p, info in upload_list if info["category"] == cat)
            print(f"  [{cat}] {len(files)} files ({format_size(cat_size)})")
            for cdn_key, size in files[:5]:
                print(f"    - {cdn_key} ({size})")
            if len(files) > 5:
                print(f"    ... and {len(files)-5} more")
        print()
        print("Run without --dry-run to upload.")
        return

    # Upload
    upload_fn = upload_file_curl if args.use_curl else upload_file
    uploaded = 0
    failed = 0

    for i, (cdn_key, local_path, info) in enumerate(upload_list, 1):
        size_str = format_size(info["size"])
        print(f"  [{i}/{len(upload_list)}] {cdn_key} ({size_str})... ", end="", flush=True)

        if upload_fn(cdn_key, local_path, args.token):
            print("OK")
            uploaded += 1
        else:
            print("FAILED")
            failed += 1

    # Upload manifest itself
    manifest_path = str(MANIFEST_FILE)
    print(f"  [manifest] manifest.json... ", end="", flush=True)
    if upload_fn("manifest.json", manifest_path, args.token):
        print("OK")
    else:
        print("FAILED")

    print()
    print("=" * 60)
    print("  Upload Complete")
    print("=" * 60)
    print(f"  Uploaded: {uploaded}")
    print(f"  Failed:   {failed}")
    print(f"  Missing:  {len(missing_list)}")


if __name__ == "__main__":
    main()
