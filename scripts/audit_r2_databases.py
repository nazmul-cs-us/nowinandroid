#!/usr/bin/env python3
"""Audit Cloudflare R2 database objects against the app and live manifests."""

from __future__ import annotations

import hashlib
import hmac
import json
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

import s3_upload


PROJECT_DIR = Path(__file__).resolve().parent.parent
APP_MANIFEST = PROJECT_DIR / "app/src/main/assets/manifest.json"
PUBLIC_BASE_URL = "https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev"
DATABASE_PREFIX = ""
INTENTIONALLY_LOCAL_DATABASES = {
    # Source DB used to generate news.db; never downloaded independently.
    "fortress_of_the_muslim_v2.db",
    # Read directly from the APK by IndoPakTextRepository.
    "quran_indopak.db",
}


def _signed_list_request(continuation_token: str | None = None) -> bytes:
    now = datetime.now(timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    host = f"{s3_upload.ACCOUNT_ID}.r2.cloudflarestorage.com"
    payload_hash = hashlib.sha256(b"").hexdigest()
    query = {"list-type": "2"}
    if DATABASE_PREFIX:
        query["prefix"] = DATABASE_PREFIX
    if continuation_token:
        query["continuation-token"] = continuation_token
    canonical_query = urllib.parse.urlencode(sorted(query.items()), quote_via=urllib.parse.quote)
    canonical_uri = f"/{s3_upload.BUCKET_NAME}"
    canonical_headers = (
        f"host:{host}\n"
        f"x-amz-content-sha256:{payload_hash}\n"
        f"x-amz-date:{amz_date}\n"
    )
    signed_headers = "host;x-amz-content-sha256;x-amz-date"
    canonical_request = (
        f"GET\n{canonical_uri}\n{canonical_query}\n{canonical_headers}\n"
        f"{signed_headers}\n{payload_hash}"
    )
    credential_scope = f"{date_stamp}/{s3_upload.REGION}/{s3_upload.SERVICE}/aws4_request"
    string_to_sign = (
        f"AWS4-HMAC-SHA256\n{amz_date}\n{credential_scope}\n"
        f"{hashlib.sha256(canonical_request.encode()).hexdigest()}"
    )
    signing_key = s3_upload.get_signature_key(
        s3_upload.SECRET_KEY,
        date_stamp,
        s3_upload.REGION,
        s3_upload.SERVICE,
    )
    signature = hmac.new(signing_key, string_to_sign.encode(), hashlib.sha256).hexdigest()
    authorization = (
        "AWS4-HMAC-SHA256 "
        f"Credential={s3_upload.ACCESS_KEY}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )
    request = urllib.request.Request(
        f"https://{host}{canonical_uri}?{canonical_query}",
        headers={
            "Authorization": authorization,
            "x-amz-content-sha256": payload_hash,
            "x-amz-date": amz_date,
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def list_database_objects() -> dict[str, dict[str, object]]:
    objects: dict[str, dict[str, object]] = {}
    continuation_token: str | None = None
    while True:
        root = ET.fromstring(_signed_list_request(continuation_token))
        namespace = {"s3": "http://s3.amazonaws.com/doc/2006-03-01/"}
        for entry in root.findall("s3:Contents", namespace):
            key = entry.findtext("s3:Key", namespaces=namespace)
            if key and key.endswith(".db"):
                objects[key] = {
                    "size": int(entry.findtext("s3:Size", "0", namespace)),
                    "etag": entry.findtext("s3:ETag", "", namespace).strip('"'),
                    "last_modified": entry.findtext("s3:LastModified", "", namespace),
                }
        is_truncated = root.findtext("s3:IsTruncated", "false", namespace) == "true"
        if not is_truncated:
            break
        continuation_token = root.findtext("s3:NextContinuationToken", namespaces=namespace)
        if not continuation_token:
            break
    return objects


def load_json_url(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": "Starception/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def database_assets(manifest: dict) -> dict[str, dict]:
    return {
        key: value
        for key, value in manifest.get("assets", {}).items()
        if key.endswith(".db")
    }


def sha256_url(key: str) -> str:
    digest = hashlib.sha256()
    url = f"{PUBLIC_BASE_URL}/{urllib.parse.quote(key, safe='/')}"
    request = urllib.request.Request(url, headers={"User-Agent": "Starception/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response:
        while chunk := response.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def local_databases() -> list[Path]:
    roots = [
        PROJECT_DIR / "app/src/main/assets/databases",
        PROJECT_DIR / "core/contentdatabase/src/main/assets/databases",
    ]
    return sorted(path for root in roots for path in root.glob("*.db"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    remote_objects = list_database_objects()
    app_assets = database_assets(json.loads(APP_MANIFEST.read_text()))
    live_assets = database_assets(load_json_url(f"{PUBLIC_BASE_URL}/manifest.json"))

    print("R2 database objects:", len(remote_objects))
    print("App-manifest databases:", len(app_assets))
    print("Live-manifest databases:", len(live_assets))

    print("\nManifest comparison:")
    manifest_differences = 0
    for key in sorted(set(app_assets) | set(live_assets)):
        app = app_assets.get(key)
        live = live_assets.get(key)
        if app != live:
            manifest_differences += 1
            print(f"  DIFFERENT {key}: app={app} live={live}")
    if manifest_differences == 0:
        print("  App and live database entries match.")

    print("\nRemote object verification:")
    remote_failures = 0
    for index, (key, expected) in enumerate(sorted(app_assets.items()), 1):
        remote = remote_objects.get(key)
        if remote is None:
            remote_failures += 1
            print(f"  [{index}/{len(app_assets)}] MISSING {key}")
            continue
        if remote["size"] != expected["size"]:
            remote_failures += 1
            print(
                f"  [{index}/{len(app_assets)}] SIZE {key}: "
                f"app={expected['size']} remote={remote['size']}"
            )
            continue
        actual_sha = sha256_url(key)
        if actual_sha != expected["sha256"]:
            remote_failures += 1
            print(
                f"  [{index}/{len(app_assets)}] SHA {key}: "
                f"app={expected['sha256']} remote={actual_sha}"
            )
        else:
            print(f"  [{index}/{len(app_assets)}] OK {key}")

    print("\nLocal packaged databases:")
    remote_by_name: dict[str, list[str]] = {}
    for key in remote_objects:
        remote_by_name.setdefault(Path(key).name, []).append(key)
    local_differences = 0
    for path in local_databases():
        size = path.stat().st_size
        if path.name in INTENTIONALLY_LOCAL_DATABASES:
            print(f"  BUNDLED_ONLY {path.relative_to(PROJECT_DIR)}")
            continue
        if size == 0:
            print(f"  PLACEHOLDER {path.relative_to(PROJECT_DIR)}")
            continue
        keys = remote_by_name.get(path.name, [])
        local_sha = sha256_file(path)
        if not keys:
            local_differences += 1
            print(
                f"  MISSING_REMOTE {path.relative_to(PROJECT_DIR)} "
                f"size={size} sha256={local_sha}"
            )
            continue
        for key in keys:
            remote = remote_objects[key]
            if remote["size"] != size:
                local_differences += 1
                print(
                    f"  DIFFERENT {path.relative_to(PROJECT_DIR)} -> {key}: "
                    f"local={size} remote={remote['size']} local_sha256={local_sha}"
                )
                continue
            remote_sha = sha256_url(key)
            if remote_sha != local_sha:
                local_differences += 1
                print(
                    f"  DIFFERENT {path.relative_to(PROJECT_DIR)} -> {key}: "
                    f"local_sha256={local_sha} remote_sha256={remote_sha}"
                )
            else:
                print(f"  OK {path.relative_to(PROJECT_DIR)} -> {key}")

    remote_only = sorted(set(remote_objects) - set(app_assets))
    print("\nRemote database objects not in the app manifest:", len(remote_only))
    for key in remote_only:
        print(f"  {key} ({remote_objects[key]['size']} bytes)")

    print(
        "\nSUMMARY "
        f"manifest_differences={manifest_differences} "
        f"remote_failures={remote_failures} "
        f"local_differences={local_differences}"
    )


if __name__ == "__main__":
    main()
