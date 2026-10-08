#!/usr/bin/env python3
"""Build topic-mode question records from the Dorar.net question bank.

The bank (IslamicQuizAPI) holds 5,820 real Arabic questions in six categories,
each with three options and a verified Dorar.net source link. These records
teach the model the topic-composition mode: given a Knowledge area, compose a
natural Arabic question with four unique options. The bank's own three-option
shape gains a fourth distractor drawn from another question in the same topic;
a held-out slice becomes the accuracy test set.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
from pathlib import Path

from prepare_hf_dataset import SYSTEM_PROMPT


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=20261008)
    return parser.parse_args()


TOPIC_KIND = {
    "tafseer": "teaching",
    "akida": "teaching",
    "hadith": "teaching",
    "figh": "teaching",
    "history": "time",
    "arabia": "description",
}


def build_record(*, record: dict) -> dict:
    # Translated records carry English text; the Arabic original stays with
    # the bank asset for citations. Training targets English questions.
    prompt = record.get("promptEn") or record["prompt"]
    options = record.get("optionsEn") or record["options"]
    user = (
        "Task: create a question item.\n"
        f"Collection: dorar_{record['category']}\n"
        f"Reference: Dorar.net · {record['category']} · {record['topic']}\n"
        f"Topic: {record['topic']}\n"
        f"Knowledge area: {record['category']} — {record['topic']}\n"
    )
    payload = {
        "contentType": "question",
        "questionKind": TOPIC_KIND.get(record["category"], "teaching"),
        "question": prompt,
        "answer": options[record["correctOption"]],
        "evidence": options[record["correctOption"]],
        "options": options,
    }
    return {
        "id": record["id"],
        "sourceCollection": f"dorar_{record['category']}",
        "sourceReference": f"الدرر السنية · {record['categoryArabic']} · {record['topic']}",
        "sourceText": "",
        "sourceTextSha256": hashlib.sha256(b"").hexdigest(),
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user},
            {
                "role": "assistant",
                "content": json.dumps(
                    payload, ensure_ascii=False, separators=(",", ":")
                ),
            },
        ],
    }


def split_for(record: dict) -> str:
    key = f"{record['sourceCollection']}|{record['sourceReference']}|{record['id']}"
    bucket = int.from_bytes(hashlib.sha256(key.encode()).digest()[:8], "big") % 100
    if bucket < 80:
        return "train"
    if bucket < 90:
        return "validation"
    return "test"


def build(args: argparse.Namespace) -> dict:
    rng = random.Random(args.seed)
    bank = json.loads(args.input.read_text(encoding="utf-8"))
    # Answer pool per (category, topic) supplies fourth distractors. Pools are
    # built from the SAME language fields each record will use.
    pool: dict[tuple, list[str]] = {}
    for record in bank:
        options = record.get("optionsEn") or record["options"]
        pool.setdefault((record["category"], record["topic"]), []).append(
            options[record["correctOption"]]
        )

    records: list[dict] = []
    skipped = 0
    for record in bank:
        # Some bank entries carry null answer slots; they cannot become options.
        base_options = record.get("optionsEn") or record["options"]
        if any(option is None or not str(option).strip() for option in base_options):
            skipped += 1
            continue
        options = [str(option).strip() for option in base_options]
        candidates = [
            answer
            for answer in pool.get((record["category"], record["topic"]), [])
            if answer not in options
        ]
        if candidates:
            fourth = rng.choice(sorted(set(candidates)))
            options.append(fourth)
        else:
            # Fall back to any answer from the same category.
            category_pool = [
                answer
                for (category, _), answers in pool.items()
                if category == record["category"]
                for answer in answers
                if answer not in options
            ]
            if not category_pool:
                skipped += 1
                continue
            options.append(rng.choice(sorted(set(category_pool))))
        if len({o.strip() for o in options}) != 4:
            skipped += 1
            continue
        translated = dict(record)
        if "optionsEn" in record:
            translated["optionsEn"] = options
        else:
            translated["options"] = options
        records.append(build_record(record=translated))

    splits: dict[str, list[dict]] = {"train": [], "validation": [], "test": []}
    for record in records:
        splits[split_for(record)].append(record)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    for split, items in splits.items():
        rng.shuffle(items)
        with (args.output_dir / f"{split}.jsonl").open("w", encoding="utf-8") as target:
            for record in items:
                target.write(json.dumps(record, ensure_ascii=False) + "\n")

    manifest = {
        "schemaVersion": 2,
        "purpose": "dorar_topic_question_composition",
        "bankQuestions": len(bank),
        "records": len(records),
        "skippedNoFourthOption": skipped,
        "counts": {split: len(items) for split, items in splits.items()},
    }
    (args.output_dir / "dataset_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


def main() -> None:
    args = parse_args()
    print(json.dumps(build(args), indent=2))


if __name__ == "__main__":
    main()
