#!/usr/bin/env python3
"""Publish only the Bengali model category, preserving all other live CDN entries."""

import argparse
import hashlib
import importlib.util
import json
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATEGORY = "model_tts_mms_bengali"
PREFIX = "models/tts/vits-mms-ben/"
EXPECTED = {
    "model.onnx": "308bc58be09aa8a4ad4a6e09bed6bcc7c91ec636d210ea7b06911e1db1738cf9",
    "tokens.txt": "a74190ec42b8f0afb349276430c716f1b585090d8fe6d3c1d6d5fe510798a387",  # gitleaks:allow -- public file SHA-256
}


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def get(url):
    request = urllib.request.Request(
        url, headers={"Cache-Control": "no-cache", "User-Agent": "curl/8.7.1"}
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--confirm-noncommercial-distribution", action="store_true", required=True
    )
    parser.add_argument(
        "--publish",
        action="store_true",
        help="Without this flag, prepare a local manifest only",
    )
    args = parser.parse_args()
    assets = ROOT / "work/tts/mms-bengali/assets/tts/vits-mms-ben"
    for name, expected in EXPECTED.items():
        if digest(assets / name) != expected:
            raise RuntimeError(f"Unexpected model input: {name}")
    spec = importlib.util.spec_from_file_location(
        "mms_setup", ROOT / "tools/tts/setup_mms_bengali.py"
    )
    setup = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(setup)
    (assets / "LICENSE.txt").write_text(setup.LICENSE_NOTICE)
    # Retain the complete license as well as human-readable source/changes attribution.
    (assets / "CC-BY-NC-4.0.txt").write_bytes(
        get("https://creativecommons.org/licenses/by-nc/4.0/legalcode.txt")
    )
    base = json.loads((ROOT / "scripts/manifest.json").read_text())["base_url"]
    manifest_url = base.rstrip("/") + "/manifest.json"
    original = json.loads(get(manifest_url + f"?mms_before={time.time_ns()}"))
    work = ROOT / "work/tts/mms-bengali/publication"
    work.mkdir(parents=True, exist_ok=True)
    backup = work / f"manifest-before-{time.time_ns()}.json"
    backup.write_text(json.dumps(original, indent=2) + "\n")
    manifest = json.loads(json.dumps(original))
    names = ["model.onnx", "tokens.txt", "LICENSE.txt", "CC-BY-NC-4.0.txt"]
    for name in names:
        file = assets / name
        manifest["assets"][PREFIX + name] = {
            "size": file.stat().st_size,
            "sha256": digest(file),
            "category": CATEGORY,
            "required": False,
        }
    entries = [
        entry for entry in manifest["assets"].values() if entry["category"] == CATEGORY
    ]
    manifest["categories"][CATEGORY] = {
        "total_size": sum(entry["size"] for entry in entries),
        "file_count": len(entries),
        "required": False,
    }
    manifest["total_size"] = sum(entry["size"] for entry in manifest["assets"].values())
    manifest["total_files"] = len(manifest["assets"])
    candidate = work / "manifest.json"
    candidate.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Prepared {len(names)} files; live manifest backup: {backup}")
    if not args.publish:
        return
    spec = importlib.util.spec_from_file_location(
        "r2_upload", ROOT / "scripts/s3_upload.py"
    )
    uploader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(uploader)
    for name in names:
        key = PREFIX + name
        if not uploader.upload_file(
            key,
            str(assets / name),
            "text/plain" if name.endswith(".txt") else "application/octet-stream",
        ):
            raise RuntimeError(f"Upload failed: {name}; manifest was not published")
        downloaded = get(base.rstrip("/") + "/" + key + f"?verify={time.time_ns()}")
        if hashlib.sha256(downloaded).hexdigest() != manifest["assets"][key]["sha256"]:
            raise RuntimeError(
                f"CDN verification failed: {name}; manifest was not published"
            )
        print(f"Uploaded and SHA-256 verified: {key}")
    latest = json.loads(get(manifest_url + f"?mms_recheck={time.time_ns()}"))
    if latest != original:
        raise RuntimeError(
            "Live manifest changed during upload; refusing to overwrite concurrent changes"
        )
    if not uploader.upload_file("manifest.json", str(candidate), "application/json"):
        raise RuntimeError("Manifest publication failed")
    live = json.loads(get(manifest_url + f"?mms_after={time.time_ns()}"))
    if live != manifest:
        raise RuntimeError(
            "Manifest CDN verification failed; inspect publication before retrying"
        )
    for destination in (
        ROOT / "scripts/manifest.json",
        ROOT / "app/src/main/assets/manifest.json",
    ):
        destination.write_bytes(candidate.read_bytes())
    print(
        f"Published and verified {CATEGORY}; all unrelated live manifest entries preserved"
    )


if __name__ == "__main__":
    main()
