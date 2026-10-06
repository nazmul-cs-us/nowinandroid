#!/usr/bin/env python3
"""Build reviewed-format examples that teach the edge model the semantic question contract.

The records are deliberately ordinary, non-religious sentences. They teach exact-span extraction,
self-contained wording, varied question kinds, and four-choice JSON without adding religious facts
to the model. Quran and Hadith knowledge continues to come only from the runtime source envelope.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
from pathlib import Path

from prepare_hf_dataset import SYSTEM_PROMPT


KINDS = (
    "person", "place", "food", "color", "action", "number", "description",
    "teaching", "outcome", "object", "time",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--examples-per-kind", type=int, default=36)
    parser.add_argument("--seed", type=int, default=20261006)
    return parser.parse_args()


def values() -> dict[str, list[str]]:
    return {
        "names": ["Amina", "Yusuf", "Maryam", "Khalid", "Sara", "Hamza"],
        "people": ["Yusuf", "Maryam", "Khalid", "Sara", "Hamza", "Amina"],
        "places": ["Makkah", "Madinah", "the library", "the garden", "the market", "the school"],
        "foods": ["dates", "honey", "bread", "milk", "olives", "pumpkin"],
        "colors": ["white", "green", "red", "black", "yellow", "blue"],
        "actions": [
            "washed both hands", "shared the meal", "opened the gate",
            "helped the traveler", "returned the book", "watered the trees",
        ],
        "numbers": ["two", "three", "four", "five", "seven", "ten"],
        "descriptions": ["peaceful", "bright", "generous", "patient", "gentle", "grateful"],
        "teachings": [
            "honesty builds trust", "patience brings calm", "kindness helps neighbors",
            "gratitude protects joy", "sharing strengthens friendship", "care prevents waste",
        ],
        "outcomes": [
            "the travelers reached shelter safely", "the garden remained healthy",
            "the family finished before sunset", "the lost book was returned",
            "the neighbors resolved the problem", "the class completed the work",
        ],
        "objects": ["a lantern", "the blue cup", "a wooden bowl", "the map", "a book", "the key"],
        "times": ["before dawn", "after sunset", "on Friday", "at noon", "in the morning", "at night"],
    }


def rotate_options(pool: list[str], answer: str, index: int) -> list[str]:
    distractors = [value for value in pool if value != answer]
    chosen = [distractors[(index + offset) % len(distractors)] for offset in range(3)] + [answer]
    random.Random(f"{answer}|{index}").shuffle(chosen)
    return chosen


def semantic_example(kind: str, index: int) -> tuple[str, str, str, str, list[str]]:
    data = values()
    name = data["names"][index % len(data["names"])]
    answer_index = index // len(data["names"])
    if kind == "person":
        person_index = (answer_index + index + 1) % len(data["people"])
        if data["people"][person_index] == name:
            person_index = (person_index + 1) % len(data["people"])
        answer = data["people"][person_index]
        evidence = f"{name} gave the sealed letter to {answer} after class."
        question = f"Who received the sealed letter from {name}?"
        pool = data["people"]
    elif kind == "place":
        answer = data["places"][answer_index % len(data["places"])]
        evidence = f"{name} traveled to {answer} before the meeting."
        question = f"Where did {name} travel before the meeting?"
        pool = data["places"]
    elif kind == "food":
        answer = data["foods"][answer_index % len(data["foods"])]
        evidence = f"{name} served {answer} at the community meal."
        question = f"Which food did {name} serve at the community meal?"
        pool = data["foods"]
    elif kind == "color":
        answer = data["colors"][answer_index % len(data["colors"])]
        evidence = f"{name} carried a {answer} book to school."
        question = f"What color was the book {name} carried to school?"
        pool = data["colors"]
    elif kind == "action":
        answer = data["actions"][answer_index % len(data["actions"])]
        evidence = f"Before leaving the room, {name} {answer}."
        question = f"What did {name} do before leaving the room?"
        pool = data["actions"]
    elif kind == "number":
        answer = data["numbers"][answer_index % len(data["numbers"])]
        evidence = f"{name} planted {answer} trees beside the path."
        question = f"How many trees did {name} plant beside the path?"
        pool = data["numbers"]
    elif kind == "description":
        answer = data["descriptions"][answer_index % len(data["descriptions"])]
        evidence = f"The guide described {name} as {answer} during the difficult journey."
        question = f"How was {name} described during the difficult journey?"
        pool = data["descriptions"]
    elif kind == "teaching":
        answer = data["teachings"][answer_index % len(data["teachings"])]
        evidence = f"The teacher reminded {name} that {answer}."
        question = f"What principle did the teacher remind {name} about?"
        pool = data["teachings"]
    elif kind == "outcome":
        answer = data["outcomes"][answer_index % len(data["outcomes"])]
        evidence = f"After everyone cooperated, {answer}."
        question = "What happened after everyone cooperated?"
        pool = data["outcomes"]
    elif kind == "object":
        answer = data["objects"][answer_index % len(data["objects"])]
        evidence = f"{name} placed {answer} beside the doorway."
        question = f"What did {name} place beside the doorway?"
        pool = data["objects"]
    else:
        answer = data["times"][answer_index % len(data["times"])]
        evidence = f"{name} began the journey {answer}."
        question = f"When did {name} begin the journey?"
        pool = data["times"]
    return evidence, question, answer, evidence, rotate_options(pool, answer, index)


def build_record(kind: str, index: int) -> dict:
    source, question, answer, evidence, options = semantic_example(kind, index)
    record_id = f"contract-{kind}-{index:04d}"
    user = (
        "Task: create a question item.\n"
        "Collection: verified_local_database\n"
        f"Reference: Contract example {record_id}\n"
        f"Topic: {kind}\n"
        f"Source text:\n{source}"
    )
    payload = {
        "contentType": "question",
        "questionKind": kind,
        "question": question,
        "answer": answer,
        "evidence": evidence,
        "options": options,
    }
    return {
        "id": record_id,
        "sourceCollection": "contract_examples",
        "sourceReference": f"Contract example {record_id}",
        "sourceText": source,
        "sourceTextSha256": hashlib.sha256(source.encode()).hexdigest(),
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user},
            {"role": "assistant", "content": json.dumps(payload, separators=(",", ":"))},
        ],
    }


def build(output_dir: Path, examples_per_kind: int, seed: int) -> dict:
    splits = {"train": [], "validation": [], "test": []}
    for kind in KINDS:
        for index in range(examples_per_kind):
            record = build_record(kind, index)
            split_key = f"{kind}|{record['sourceText']}"
            bucket = int.from_bytes(hashlib.sha256(split_key.encode()).digest()[:8], "big") % 100
            split = "train" if bucket < 80 else "validation" if bucket < 90 else "test"
            splits[split].append(record)
    rng = random.Random(seed)
    output_dir.mkdir(parents=True, exist_ok=True)
    for split, records in splits.items():
        rng.shuffle(records)
        with (output_dir / f"{split}.jsonl").open("w", encoding="utf-8") as target:
            for record in records:
                target.write(json.dumps(record, ensure_ascii=False) + "\n")
    manifest = {
        "schemaVersion": 2,
        "purpose": "semantic_question_contract_instruction_tuning",
        "baseModel": "Qwen/Qwen2.5-0.5B-Instruct",
        "religiousFactsAdded": False,
        "runtimeKnowledgePolicy": "verified_source_envelope_only",
        "questionKinds": list(KINDS),
        "counts": {split: len(records) for split, records in splits.items()},
    }
    (output_dir / "dataset_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


def main() -> None:
    args = parse_args()
    print(json.dumps(build(args.output_dir, args.examples_per_kind, args.seed), indent=2))


if __name__ == "__main__":
    main()
