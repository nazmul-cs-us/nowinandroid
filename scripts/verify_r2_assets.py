#!/usr/bin/env python3
"""
Verify assets on Cloudflare R2 against manifest.

Usage:
    python3 scripts/verify_r2_assets.py --token YOUR_API_TOKEN
    python3 scripts/verify_r2_assets.py --token YOUR_API_TOKEN --category quran_audio_arabic
"""

import json
import argparse
import subprocess
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
MANIFEST_FILE = SCRIPT_DIR / "manifest.json"
APP_MANIFEST = SCRIPT_DIR.parent / "app" / "src" / "main" / "assets" / "manifest.json"

ACCOUNT_ID = "a535d591f409b4a31c39625dc1ffd6c7"
BUCKET_NAME = "starception-assets"
BASE_URL = "https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev"


def format_size(size_bytes: int) -> str:
    if size_bytes >= 1024 * 1024 * 1024:
        return f"{size_bytes / (1024*1024*1024):.1f} GB"
    elif size_bytes >= 1024 * 1024:
        return f"{size_bytes / (1024*1024):.1f} MB"
    elif size_bytes >= 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes} B"


def list_r2_objects(token: str, prefix: str = "") -> dict:
    """List all objects in R2 bucket using Cloudflare API."""
    objects = {}
    cursor = ""

    while True:
        url = f"https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/r2/buckets/{BUCKET_NAME}/objects"
        if prefix:
            url += f"?prefix={prefix}"
        if cursor:
            url += f"&cursor={cursor}" if "?" in url else f"?cursor={cursor}"

        result = subprocess.run(
            [
                "curl", "-s", url,
                "-H", f"Authorization: Bearer {token}",
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )

        if result.returncode != 0:
            print(f"Error listing objects: {result.stderr}")
            break

        try:
            data = json.loads(result.stdout)
            if not data.get("success"):
                print(f"API error: {data.get('errors', 'Unknown error')}")
                break

            for obj in data.get("result", {}).get("objects", []):
                objects[obj["key"]] = obj.get("size", 0)

            # Check for pagination
            cursor = data.get("result", {}).get("cursor", "")
            if not cursor:
                break
        except json.JSONDecodeError as e:
            print(f"JSON parse error: {e}")
            print(f"Response: {result.stdout[:500]}")
            break

    return objects


def check_file_exists(cdn_key: str) -> tuple:
    """Check if file exists on CDN using HEAD request."""
    result = subprocess.run(
        ["curl", "-sI", f"{BASE_URL}/{cdn_key}"],
        capture_output=True,
        text=True,
        timeout=30,
    )

    lines = result.stdout.split("\n")
    status = "000"
    size = 0

    for line in lines:
        if line.startswith("HTTP/"):
            status = line.split()[1] if len(line.split()) > 1 else "000"
        if line.lower().startswith("content-length:"):
            try:
                size = int(line.split(":")[1].strip())
            except (ValueError, IndexError):
                pass

    return status, size


def main():
    parser = argparse.ArgumentParser(description="Verify R2 assets against manifest")
    parser.add_argument("--token", help="Cloudflare API token (optional, uses HEAD requests if not provided)")
    parser.add_argument("--category", help="Only check this category")
    parser.add_argument("--verbose", "-v", action="store_true", help="Show all files")
    args = parser.parse_args()

    # Load manifest
    manifest_path = APP_MANIFEST if APP_MANIFEST.exists() else MANIFEST_FILE
    if not manifest_path.exists():
        print(f"Error: Manifest not found")
        return

    with open(manifest_path) as f:
        manifest = json.load(f)

    assets = manifest["assets"]

    print("=" * 60)
    print("  Cloudflare R2 Asset Verification")
    print("=" * 60)
    print(f"  Manifest: {manifest_path.name}")
    print(f"  Total assets: {len(assets)}")
    print(f"  Base URL: {BASE_URL}")
    print()

    # Filter by category if specified
    if args.category:
        assets = {k: v for k, v in assets.items() if v["category"] == args.category}
        print(f"  Filtering by category: {args.category}")
        print(f"  Assets to check: {len(assets)}")
        print()

    # Verify each asset
    found = []
    missing = []
    size_mismatch = []
    errors = []

    total = len(assets)
    for i, (cdn_key, info) in enumerate(sorted(assets.items()), 1):
        expected_size = info["size"]
        category = info["category"]

        if args.verbose or i % 100 == 0:
            print(f"\r  Checking [{i}/{total}] {cdn_key[:50]}...", end="", flush=True)

        status, actual_size = check_file_exists(cdn_key)

        if status == "200":
            if expected_size > 0 and actual_size != expected_size:
                size_mismatch.append((cdn_key, category, expected_size, actual_size))
            else:
                found.append((cdn_key, category, actual_size))
        elif status == "429":
            # Rate limited - count as unknown
            errors.append((cdn_key, category, "rate_limited"))
        elif status == "404":
            missing.append((cdn_key, category, expected_size))
        else:
            errors.append((cdn_key, category, f"HTTP {status}"))

    print("\r" + " " * 70 + "\r", end="")  # Clear progress line

    # Summary
    print("=" * 60)
    print("  Verification Results")
    print("=" * 60)
    print(f"  Found:          {len(found)}")
    print(f"  Missing:        {len(missing)}")
    print(f"  Size mismatch:  {len(size_mismatch)}")
    print(f"  Errors:         {len(errors)}")
    print()

    if missing:
        print(f"Missing files ({len(missing)}):")
        # Group by category
        by_cat = {}
        for cdn_key, cat, size in missing:
            by_cat.setdefault(cat, []).append((cdn_key, size))
        for cat in sorted(by_cat.keys()):
            files = by_cat[cat]
            cat_size = sum(s for _, s in files)
            print(f"  [{cat}] {len(files)} files ({format_size(cat_size)})")
            for cdn_key, size in files[:5]:
                print(f"    - {cdn_key} ({format_size(size)})")
            if len(files) > 5:
                print(f"    ... and {len(files) - 5} more")
        print()

    if size_mismatch:
        print(f"Size mismatches ({len(size_mismatch)}):")
        for cdn_key, cat, expected, actual in size_mismatch[:10]:
            print(f"  - {cdn_key}: expected {format_size(expected)}, got {format_size(actual)}")
        if len(size_mismatch) > 10:
            print(f"  ... and {len(size_mismatch) - 10} more")
        print()

    if errors:
        print(f"Errors ({len(errors)}):")
        # Group by error type
        by_err = {}
        for cdn_key, cat, err in errors:
            by_err.setdefault(err, []).append((cdn_key, cat))
        for err, files in by_err.items():
            print(f"  {err}: {len(files)} files")
            for cdn_key, cat in files[:3]:
                print(f"    - {cdn_key}")
            if len(files) > 3:
                print(f"    ... and {len(files) - 3} more")
        print()


if __name__ == "__main__":
    main()
