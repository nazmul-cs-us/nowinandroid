#!/usr/bin/env python3
"""Create leakage-safe Hugging Face chat splits from source-locked candidates."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

SYSTEM_PROMPT = """You create one grounded Islamic learning item from the supplied source.
Use only the supplied source, never memory. Return one minified JSON object and nothing else.
A knowledge object has exactly contentType and title. For a question, create a natural,
self-contained global knowledge question that does not say "this narration", "this hadith",
"this ayah", "this verse", "this passage", "according to", or ask where the text came from.
A question object has exactly contentType, questionKind, question, answer, evidence, and options.
questionKind must be one of person, place, food, color, action, number, description, teaching,
outcome, object, or time. answer must be a short exact contiguous span copied from Source text.
evidence must be one exact contiguous span copied from Source text and must contain answer.
options must contain exactly four short unique strings including answer exactly once. Distractors
must not contradict anything stated in the source. Never output Arabic, IDs, citations, rulings,
or claims unsupported by the source. Prefer a simple explicit detail over interpretation.
When the request provides a Knowledge area instead of a Source text, compose one natural
question about that topic from the verified Dorar.net question bank in Arabic, with evidence
copied equal to answer and four unique Arabic options including answer exactly once. In that
mode never use English words in the question or options."""

ANSWER_SYSTEM_PROMPT = """You answer the user's question from the supplied source.
Use only the supplied source, never memory. Return one minified JSON object and nothing else.
When the supplied source answers the question, contentType is answer, answer is a short exact
contiguous span copied from Source text, and evidence is one exact contiguous span copied from
Source text that contains answer. When the supplied source does not answer the question,
contentType is unanswered and answer and evidence are empty strings. Never output Arabic, IDs,
citations, rulings, or claims unsupported by the source."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--max-source-chars", type=int, default=1400)
    return parser.parse_args()


def split_for(record: dict) -> str:
    # Question/knowledge pairs sharing a reference must remain in the same split.
    # Some legacy Bukhari exports reuse a displayed hadith number for multiple records, so the
    # immutable source hash is part of the identity as well.
    key = (
        f"{record['sourceCollection']}|{record['sourceReference']}|"
        f"{record['sourceTextSha256']}"
    )
    bucket = int.from_bytes(hashlib.sha256(key.encode()).digest()[:8], "big") % 100
    if bucket < 80:
        return "train"
    if bucket < 90:
        return "validation"
    return "test"


def build_example(record: dict, max_source_chars: int) -> dict:
    source_text = record["sourceText"]
    context_text = source_text[:max_source_chars]
    task = record["contentType"]
    user = (
        f"Task: create a {task} item.\n"
        f"Collection: {record['sourceCollection']}\n"
        f"Reference: {record['sourceReference']}\n"
        f"Topic: {record['topic']}\n"
        f"Source text:\n{context_text}"
    )
    if task == "knowledge":
        answer = {
            "contentType": "knowledge",
            "title": record["title"],
        }
    else:
        if (
            record.get("questionKind")
            and record.get("answer")
            and record.get("evidence")
        ):
            answer = {
                "contentType": "question",
                "questionKind": record["questionKind"],
                "question": record["prompt"],
                "answer": record["answer"],
                "evidence": record["evidence"],
                "options": record["options"],
            }
        else:
            answer = {
                "contentType": "question",
                "questionKind": "source_location",
            }
    return {
        "id": record["id"],
        "sourceCollection": record["sourceCollection"],
        "sourceReference": record["sourceReference"],
        "sourceText": source_text,
        "sourceTextSha256": record["sourceTextSha256"],
        "sourceArabicSha256": record.get("sourceArabicSha256"),
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user},
            {
                "role": "assistant",
                "content": json.dumps(
                    answer, ensure_ascii=False, separators=(",", ":")
                ),
            },
        ],
    }


def prepare(input_path: Path, output_dir: Path, max_source_chars: int) -> dict:
    splits: dict[str, list[dict]] = {"train": [], "validation": [], "test": []}
    with input_path.open(encoding="utf-8") as source:
        for line in source:
            record = json.loads(line)
            actual_hash = hashlib.sha256(record["sourceText"].encode()).hexdigest()
            if actual_hash != record["sourceTextSha256"]:
                raise ValueError(f"Source hash mismatch for {record['id']}")
            splits[split_for(record)].append(build_example(record, max_source_chars))

    output_dir.mkdir(parents=True, exist_ok=True)
    for name, records in splits.items():
        with (output_dir / f"{name}.jsonl").open("w", encoding="utf-8") as target:
            for record in records:
                target.write(json.dumps(record, ensure_ascii=False) + "\n")

    manifest = {
        "schemaVersion": 2,
        "baseModel": "Qwen/Qwen2.5-0.5B-Instruct",
        "splitStrategy": "sha256(sourceCollection|sourceReference|sourceTextSha256), 80/10/10",
        "sourceTextPolicy": "immutable_sha256",
        "quranArabicPolicy": "never_model_generated",
        "counts": {name: len(records) for name, records in splits.items()},
        "countsBySource": {
            name: dict(sorted(Counter(r["sourceCollection"] for r in records).items()))
            for name, records in splits.items()
        },
    }
    (output_dir / "dataset_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n",
        encoding="utf-8",
    )
    return manifest


def main() -> None:
    args = parse_args()
    manifest = prepare(args.input, args.output_dir, args.max_source_chars)
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
