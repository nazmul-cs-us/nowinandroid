#!/usr/bin/env python3
"""Prepare auditable training splits without malformed targets or shared source passages."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

try:
    from .validate_generated_content import validate_output
except ImportError:
    from validate_generated_content import validate_output

PLACEHOLDER = re.compile(r"<\s*(?:english|translation|question|answer)\b|\[end of text\]", re.I)
ARABIC = re.compile(r"[\u0600-\u06ff]")


def rejection_reason(record: dict) -> str | None:
    target = record["messages"][-1]["content"]
    if PLACEHOLDER.search(target):
        return "placeholder_target"
    if ARABIC.search(target):
        return "arabic_target_for_english_contract"
    accepted, reason = validate_output(record, target)
    return None if accepted else reason


def prepare(source: Path, output: Path) -> dict:
    splits = {}
    rejected = {}
    input_counts = {}
    for split in ("test", "validation", "train"):
        rows = [json.loads(line) for line in (source / f"{split}.jsonl").read_text().splitlines()]
        input_counts[split] = len(rows)
        counts = Counter()
        kept = []
        # Reserve held-out passages first. Topic-only prompts intentionally have no
        # passage and can generate many distinct questions; they are not source leaks.
        reserved_sources = {r["sourceTextSha256"] for records in splits.values() for r in records if r.get("sourceText")}
        reserved_questions = {json.loads(r["messages"][-1]["content"]).get("question")
                              for records in splits.values() for r in records}
        reserved_questions.discard(None)
        for row in rows:
            reason = rejection_reason(row)
            target = json.loads(row["messages"][-1]["content"]) if not reason else {}
            if not reason and row.get("sourceText") and row["sourceTextSha256"] in reserved_sources:
                reason = "source_passage_in_held_out_split"
            if not reason and target.get("question") in reserved_questions:
                reason = "question_in_held_out_split"
            if reason:
                counts[reason] += 1
            else:
                kept.append(row)
        if not kept:
            raise ValueError(f"No usable {split} examples remain in {source}")
        splits[split] = kept
        rejected[split] = dict(counts)
    output.mkdir(parents=True, exist_ok=True)
    for split, rows in splits.items():
        (output / f"{split}.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows))
    manifest = {"source": str(source), "policy": "english_contract_no_placeholders_disjoint_source_passages_v1",
                "inputSha256": {s: hashlib.sha256((source / f"{s}.jsonl").read_bytes()).hexdigest() for s in splits},
                "inputCounts": input_counts, "counts": {s: len(r) for s, r in splits.items()}, "rejected": rejected}
    (output / "dataset_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(prepare(args.source, args.output), indent=2))
