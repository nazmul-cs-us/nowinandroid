#!/usr/bin/env python3
"""Upload files to Cloudflare R2 using S3-compatible API with AWS Signature V4.
No external dependencies required (uses only Python stdlib).
"""

import hashlib
import hmac
import json
import os
import sys
import urllib.parse
import urllib.request
import urllib.error
from datetime import datetime, timezone
from pathlib import Path

ACCOUNT_ID = "a535d591f409b4a31c39625dc1ffd6c7"
BUCKET_NAME = "starception-assets"
ENDPOINT = f"https://{ACCOUNT_ID}.r2.cloudflarestorage.com"
REGION = "auto"
SERVICE = "s3"

ACCESS_KEY = "de52f276c7a3a4b19e2a0aba392ea85e"
SECRET_KEY = "e4681d6b8b96e999ec7e4a6e96a548e7aac6418cf349c32364008a22f8c842e0"

SCRIPT_DIR = Path(__file__).parent
PROJECT_DIR = SCRIPT_DIR.parent
ASSETS_DIR = PROJECT_DIR / "app" / "src" / "main" / "assets"
MANIFEST_FILE = SCRIPT_DIR / "manifest.json"


def sign(key, msg):
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def get_signature_key(key, date_stamp, region, service):
    k_date = sign(("AWS4" + key).encode("utf-8"), date_stamp)
    k_region = sign(k_date, region)
    k_service = sign(k_region, service)
    k_signing = sign(k_service, "aws4_request")
    return k_signing


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


def upload_file(cdn_key, local_path, content_type="application/octet-stream"):
    """Upload a file to R2 using AWS Signature V4."""
    with open(local_path, "rb") as f:
        body = f.read()

    now = datetime.now(timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")

    host = f"{ACCOUNT_ID}.r2.cloudflarestorage.com"
    canonical_uri = "/" + "/".join(urllib.parse.quote(seg, safe="") for seg in f"{BUCKET_NAME}/{cdn_key}".split("/"))

    payload_hash = sha256_hex(body)

    canonical_headers = (
        f"content-type:{content_type}\n"
        f"host:{host}\n"
        f"x-amz-content-sha256:{payload_hash}\n"
        f"x-amz-date:{amz_date}\n"
    )
    signed_headers = "content-type;host;x-amz-content-sha256;x-amz-date"

    canonical_request = (
        f"PUT\n"
        f"{canonical_uri}\n"
        f"\n"
        f"{canonical_headers}\n"
        f"{signed_headers}\n"
        f"{payload_hash}"
    )

    credential_scope = f"{date_stamp}/{REGION}/{SERVICE}/aws4_request"
    string_to_sign = (
        f"AWS4-HMAC-SHA256\n"
        f"{amz_date}\n"
        f"{credential_scope}\n"
        f"{sha256_hex(canonical_request.encode('utf-8'))}"
    )

    signing_key = get_signature_key(SECRET_KEY, date_stamp, REGION, SERVICE)
    signature = hmac.new(signing_key, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()

    authorization = (
        f"AWS4-HMAC-SHA256 "
        f"Credential={ACCESS_KEY}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, "
        f"Signature={signature}"
    )

    url = f"{ENDPOINT}/{BUCKET_NAME}/{urllib.parse.quote(cdn_key, safe='/')}"
    headers = {
        "Content-Type": content_type,
        "x-amz-content-sha256": payload_hash,
        "x-amz-date": amz_date,
        "Authorization": authorization,
    }

    req = urllib.request.Request(url, data=body, headers=headers, method="PUT")
    try:
        response = urllib.request.urlopen(req, timeout=600)
        return response.status == 200
    except urllib.error.HTTPError as e:
        print(f" HTTP {e.code}: {e.read().decode('utf-8', errors='replace')[:200]}")
        return False
    except Exception as e:
        print(f" Error: {e}")
        return False


def cdn_key_to_local_path(cdn_key):
    """Reverse-map CDN key to local asset path."""
    if cdn_key.startswith("json/"):
        return cdn_key[5:]
    if cdn_key.startswith("models/sherpa/"):
        return cdn_key[7:]
    if cdn_key.startswith("models/kws/"):
        return cdn_key[7:]
    if cdn_key.startswith("models/whisper/"):
        return cdn_key[7:]
    if cdn_key.startswith("models/tts/"):
        return cdn_key[7:]
    if cdn_key.startswith("models/"):
        return cdn_key
    if cdn_key.startswith("databases/quran/"):
        return "databases/" + os.path.basename(cdn_key)
    return cdn_key


def format_size(size_bytes):
    if size_bytes >= 1024 * 1024 * 1024:
        return f"{size_bytes / (1024*1024*1024):.1f} GB"
    elif size_bytes >= 1024 * 1024:
        return f"{size_bytes / (1024*1024):.1f} MB"
    elif size_bytes >= 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes} B"


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Upload assets to R2 via S3 API")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--category", help="Only upload this category")
    parser.add_argument("--test", action="store_true", help="Test with small upload")
    args = parser.parse_args()

    if args.test:
        print("Testing upload...")
        ok = upload_file("test.txt", "/dev/null")
        # Actually test with inline data
        import tempfile
        with tempfile.NamedTemporaryFile(mode='wb', suffix='.txt', delete=False) as f:
            f.write(b"hello from s3_upload.py test")
            tmp = f.name
        ok = upload_file("test_upload.txt", tmp)
        os.unlink(tmp)
        print(f"Test upload: {'OK' if ok else 'FAILED'}")
        return

    if not MANIFEST_FILE.exists():
        print(f"Error: Manifest not found at {MANIFEST_FILE}")
        sys.exit(1)

    with open(MANIFEST_FILE) as f:
        manifest = json.load(f)

    assets = manifest["assets"]
    content_db_dir = PROJECT_DIR / "core" / "contentdatabase" / "src" / "main" / "assets"

    upload_list = []
    missing_list = []

    for cdn_key, info in sorted(assets.items()):
        if args.category and info["category"] != args.category:
            continue

        local_rel = cdn_key_to_local_path(cdn_key)
        local_path = ASSETS_DIR / local_rel

        if not local_path.exists() and cdn_key.startswith("databases/"):
            alt_path = content_db_dir / local_rel
            if alt_path.exists():
                local_path = alt_path

        if local_path.exists():
            upload_list.append((cdn_key, str(local_path), info))
        else:
            missing_list.append((cdn_key, local_rel, info))

    total_upload_size = sum(info["size"] for _, _, info in upload_list)

    print("=" * 60)
    print("  R2 S3 Upload")
    print("=" * 60)
    print(f"  Upload:  {len(upload_list)} files ({format_size(total_upload_size)})")
    print(f"  Missing: {len(missing_list)} files")
    if args.category:
        print(f"  Category: {args.category}")
    print()

    if args.dry_run:
        for cdn_key, local_path, info in upload_list:
            print(f"  {cdn_key} ({format_size(info['size'])})")
        return

    uploaded = 0
    failed = 0
    for i, (cdn_key, local_path, info) in enumerate(upload_list, 1):
        size_str = format_size(info["size"])
        print(f"  [{i}/{len(upload_list)}] {cdn_key} ({size_str})... ", end="", flush=True)
        if upload_file(cdn_key, local_path):
            print("OK")
            uploaded += 1
        else:
            print("FAILED")
            failed += 1

    # Upload manifest
    manifest_path = str(MANIFEST_FILE)
    print(f"  [manifest] manifest.json... ", end="", flush=True)
    if upload_file("manifest.json", manifest_path):
        print("OK")
    else:
        print("FAILED")

    print()
    print(f"  Uploaded: {uploaded}/{len(upload_list)}")
    if failed:
        print(f"  Failed:   {failed}")


if __name__ == "__main__":
    main()
