"""Consolidate salah recordings into one training-ready directory.

The raw `data/` tree mixes three things: genuine hand-labelled recordings, the
same files copied into several folders, and live recordings whose labels came
from the model itself. Training on that last group teaches the model its own
mistakes, so this script decides file by file and writes a clean copy into
`data/train_ready/` instead of editing anything in place.

Usage:
    python prepare_dataset.py                 # writes data/train_ready/
    python prepare_dataset.py --dry-run       # report decisions only
"""

import argparse
import hashlib
import json
import shutil
from collections import Counter
from pathlib import Path

DATA_ROOT = Path(__file__).parent / "data"
OUTPUT_DIR = DATA_ROOT / "train_ready"

# Live recordings are labelled by the model under evaluation. They only become
# ground truth after the in-app Review & Label pass rewrites them as
# salah_reviewed_*. Anything still carrying live-origin labels stays out.
QUARANTINE = {
    "salah_live_20260720_181503_1d6b7f63.jsonl":
        "live labels, never reviewed in-app (the file the shipped model was trained on)",
    "salah_data_20260721_174405_e035130b.jsonl":
        "live labels under a manual filename: 51 posture segments, several under 0.5s",
}

# Manual recordings predate schema v2 and so have no collection_mode field. Each
# holds exactly one posture chosen by hand before recording started, which is
# what manual mode means, so the provenance is reconstructed rather than invented.
LEGACY_MANUAL_DIR = "archive_march_2026"


MODE_PREFIXES = ("salah_reviewed_", "salah_guided_", "salah_live_", "salah_data_")

POSTURE_SLUGS = {
    "QIYAM": "qiyam",
    "RUKU": "ruku",
    "GOING_TO_SUJUD": "going2sujud",
    "SUJUD": "sujud",
    "JALSA": "jalsa",
    "TASHAHHUD": "tashahhud",
    "QIYAM_RISING": "qiyamrising",
    "RISING_TO_QIYAM": "rising2qiyam",
}


def looks_like_manual(rows: list[dict]) -> bool:
    """A manual recording is one posture, one session, held continuously."""
    return (
        len({r.get("posture") for r in rows}) == 1
        and len({r.get("session_id") for r in rows}) == 1
    )


def descriptive_name(name: str, rows: list[dict]) -> str:
    """Filename that states what the recording holds.

    Mirrors SalahRecordingName.kt on the app side so a file keeps one name whether it
    was written by the phone or consolidated here. The descriptor goes after the mode
    prefix, which keeps the `startswith` provenance gates in feature_engineering.py
    working untouched.
    """
    prefix = next((p for p in MODE_PREFIXES if name.startswith(p)), None)
    if prefix is None:
        return name
    rest = name[len(prefix):]
    # A descriptor, when present, sits before the 8-digit date stamp.
    tail = rest if rest[:8].isdigit() else rest.split("_", 1)[1]

    # Live labels come from the model, so describing them would dress a guess up as truth.
    if prefix == "salah_live_":
        return prefix + tail

    postures = {r.get("posture") for r in rows} - {None}
    if not rows:
        descriptor = "empty"
    elif len(postures) == 1:
        descriptor = POSTURE_SLUGS.get(next(iter(postures)), "unknown")
    elif prefix == "salah_reviewed_" and rows[0].get("target_rakah_count") is not None:
        descriptor = f"{rows[0]['target_rakah_count']}rakah"
    elif len(postures) == len(POSTURE_SLUGS):
        descriptor = "full"
    else:
        descriptor = f"partial{len(postures)}"
    return f"{prefix}{descriptor}_{tail}"


def main(dry_run: bool) -> None:
    sources = sorted(p for p in DATA_ROOT.rglob("*.jsonl") if OUTPUT_DIR not in p.parents)

    seen: dict[str, Path] = {}
    kept: list[tuple[Path, int, str]] = []
    skipped: list[tuple[Path, str]] = []

    for path in sources:
        digest = hashlib.md5(path.read_bytes()).hexdigest()
        if digest in seen:
            skipped.append((path, f"duplicate of {seen[digest].relative_to(DATA_ROOT)}"))
            continue
        seen[digest] = path

        if path.name in QUARANTINE:
            skipped.append((path, QUARANTINE[path.name]))
            continue

        rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
        if not rows:
            skipped.append((path, "empty file"))
            continue

        if rows[0].get("collection_mode") is not None:
            note = f"schema v{rows[0].get('schema_version')} {rows[0]['collection_mode']}"
        elif path.parent.name == LEGACY_MANUAL_DIR and looks_like_manual(rows):
            for row in rows:
                row["schema_version"] = 2
                row["collection_mode"] = "manual"
                row["label_source"] = "manual_selection"
            note = f"legacy manual, provenance reconstructed ({rows[0]['posture']})"
        else:
            skipped.append((path, "no collection_mode and not a single-posture manual take"))
            continue

        out_name = descriptive_name(path.name, rows)
        kept.append((path, out_name, len(rows), note))
        if not dry_run:
            OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
            if rows[0].get("label_source") == "manual_selection":
                (OUTPUT_DIR / out_name).write_text(
                    "".join(json.dumps(r) + "\n" for r in rows)
                )
            else:
                shutil.copy2(path, OUTPUT_DIR / out_name)

    print(f"{'Would keep' if dry_run else 'Kept'} {len(kept)} files:")
    for _, out_name, count, note in kept:
        print(f"  {out_name:56s} {count:5d} rows  [{note}]")
    print(f"\nExcluded {len(skipped)} files:")
    for path, reason in skipped:
        print(f"  {path.relative_to(DATA_ROOT)}\n      -> {reason}")

    sessions_per_class: dict[str, set] = {}
    for path, _, _, _ in kept:
        for line in path.read_text().splitlines():
            if not line.strip():
                continue
            row = json.loads(line)
            sessions_per_class.setdefault(row["posture"], set()).add(row["session_id"])

    print("\nIndependent sessions per class (train/val/test split needs 3):")
    for posture, ids in sorted(sessions_per_class.items(), key=lambda kv: len(kv[1])):
        mark = "ok " if len(ids) >= 3 else "SHORT"
        print(f"  {mark} {posture:16s} {len(ids)}")

    if not dry_run:
        print(f"\nWrote {OUTPUT_DIR}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="report decisions only")
    main(parser.parse_args().dry_run)
