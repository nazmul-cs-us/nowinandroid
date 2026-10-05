#!/usr/bin/env python3
"""Download the app's Quran/Hadith sources and verify them against its manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import urllib.request
from pathlib import Path


CORE_SOURCE_KEYS = (
    "databases/quran/quran.db",
    "databases/quran/quran_en.db",
    "json/sahih_bukhari.json",
    "databases/hadith/shamayele_tirmidhi_complete.db",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("app/src/main/assets/manifest.json"),
    )
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument(
        "--core-only",
        action="store_true",
        help="Fetch only Quran, Bukhari JSON, and Shama'il instead of every Hadith database",
    )
    return parser.parse_args()


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fetch_sources(manifest_path: Path, output_dir: Path, include_all_hadith: bool = True) -> dict:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    base_url = manifest["base_url"].rstrip("/")
    output_dir.mkdir(parents=True, exist_ok=True)
    downloaded = []

    source_keys = list(CORE_SOURCE_KEYS)
    if include_all_hadith:
        source_keys.extend(
            key
            for key, value in manifest["assets"].items()
            if key.startswith("databases/hadith/")
            and key.endswith(".db")
            and value["category"].startswith("hadith_")
        )
    source_keys = sorted(set(source_keys))

    for key in source_keys:
        expected = manifest["assets"][key]
        destination = output_dir / Path(key).name
        if not destination.is_file() or file_sha256(destination) != expected["sha256"]:
            temporary = destination.with_suffix(destination.suffix + ".part")
            request = urllib.request.Request(
                f"{base_url}/{key}",
                headers={"User-Agent": "DeenlyLocalTrainer/1.0"},
            )
            print(f"Fetching {key}", flush=True)
            with urllib.request.urlopen(request, timeout=60) as response:
                with temporary.open("wb") as target:
                    while block := response.read(1024 * 1024):
                        target.write(block)
            if temporary.stat().st_size != expected["size"]:
                temporary.unlink(missing_ok=True)
                raise ValueError(f"Size mismatch for {key}")
            actual_hash = file_sha256(temporary)
            if actual_hash != expected["sha256"]:
                temporary.unlink(missing_ok=True)
                raise ValueError(f"SHA-256 mismatch for {key}")
            temporary.replace(destination)

        downloaded.append(
            {
                "key": key,
                "path": str(destination),
                "size": destination.stat().st_size,
                "sha256": file_sha256(destination),
            }
        )

    result = {
        "manifestVersion": manifest["version"],
        "scope": "core" if not include_all_hadith else "all_hadith",
        "sources": downloaded,
    }
    (output_dir / "sources.lock.json").write_text(
        json.dumps(result, indent=2) + "\n",
        encoding="utf-8",
    )
    return result


def main() -> None:
    args = parse_args()
    result = fetch_sources(args.manifest, args.output_dir, include_all_hadith=not args.core_only)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
