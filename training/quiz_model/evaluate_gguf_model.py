#!/usr/bin/env python3
"""Evaluate the deployable GGUF with llama.cpp and the source-lock contract."""

from __future__ import annotations

import argparse
import json
import random
import unicodedata
import hashlib
import subprocess
import time
from collections import Counter, defaultdict, deque
from pathlib import Path

try:
    from .validate_generated_content import validate_output
except ImportError:
    # Keep direct script execution working from the repository root.
    from validate_generated_content import validate_output


KNOWLEDGE_JSON_SCHEMA = json.dumps(
    {
        "type": "object",
        "properties": {
            "contentType": {"const": "knowledge"},
            "title": {"type": "string", "minLength": 1, "maxLength": 84},
        },
        "required": ["contentType", "title"],
        "additionalProperties": False,
    },
    separators=(",", ":"),
)
QUESTION_JSON_SCHEMA = json.dumps(
    {
        "type": "object",
        "properties": {
            "contentType": {"const": "question"},
            "questionKind": {
                "enum": [
                    "person",
                    "place",
                    "food",
                    "color",
                    "action",
                    "number",
                    "description",
                    "teaching",
                    "outcome",
                    "object",
                    "time",
                ]
            },
            "question": {"type": "string", "minLength": 8, "maxLength": 180},
            "answer": {"type": "string", "minLength": 1, "maxLength": 96},
            "evidence": {"type": "string", "minLength": 1, "maxLength": 420},
            "options": {
                "type": "array",
                "minItems": 4,
                "maxItems": 4,
                "items": {"type": "string", "minLength": 1, "maxLength": 96},
            },
        },
        "required": [
            "contentType",
            "questionKind",
            "question",
            "answer",
            "evidence",
            "options",
        ],
        "additionalProperties": False,
    },
    separators=(",", ":"),
)


ANSWER_JSON_SCHEMA = json.dumps(
    {
        "type": "object",
        "properties": {
            "contentType": {"enum": ["answer", "unanswered"]},
            "answer": {"type": "string", "maxLength": 96},
            "evidence": {"type": "string", "maxLength": 420},
        },
        "required": ["contentType", "answer", "evidence"],
        "additionalProperties": False,
    },
    separators=(",", ":"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--runner", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--samples", type=int, default=30)
    parser.add_argument("--max-new-tokens", type=int, default=256)
    parser.add_argument("--seed", type=int, default=20261005)
    parser.add_argument(
        "--no-gpu",
        action="store_true",
        help="Run the runner with all layers on CPU (-ngl 0) to avoid GPU contention",
    )
    return parser.parse_args()


def chatml_prompt(example: dict) -> str:
    messages = example["messages"][:-1]
    return (
        "".join(
            f"<|im_start|>{message['role']}\n{message['content']}<|im_end|>\n"
            for message in messages
        )
        + "<|im_start|>assistant\n"
    )


def runtime_request(example: dict) -> tuple[str, str, str]:
    """Return the exact system, user, and schema inputs used by Android."""
    messages = example["messages"]
    expected = json.loads(messages[-1]["content"])
    if expected["contentType"] in {"answer", "unanswered"}:
        schema = ANSWER_JSON_SCHEMA
    elif expected["contentType"] == "knowledge":
        schema = KNOWLEDGE_JSON_SCHEMA
    else:
        schema = QUESTION_JSON_SCHEMA
    # The example's system message is the runtime contract; it equals SYSTEM_PROMPT
    # for question/knowledge records and the answer prompt for answer records.
    return messages[0]["content"], messages[-2]["content"], schema


def extract_json(text: str) -> str:
    start = text.find("{")
    end = text.rfind("}")
    return text[start : end + 1] if start >= 0 and end >= start else text.strip()


def balanced_sample(records: list[dict], count: int, seed: int) -> list[dict]:
    """Round-robin source/content groups so a large collection cannot dominate evaluation."""
    rng = random.Random(seed)
    groups: dict[tuple[str, str], deque[dict]] = defaultdict(deque)
    for record in records:
        content_type = json.loads(record["messages"][-1]["content"])["contentType"]
        groups[(record["sourceCollection"], content_type)].append(record)
    for values in groups.values():
        shuffled = list(values)
        rng.shuffle(shuffled)
        values.clear()
        values.extend(shuffled)

    keys = sorted(groups)
    rng.shuffle(keys)
    selected: list[dict] = []
    while len(selected) < min(count, len(records)):
        made_progress = False
        for key in keys:
            if groups[key] and len(selected) < count:
                selected.append(groups[key].popleft())
                made_progress = True
        if not made_progress:
            break
    return selected


def normalized_answer(text: str) -> list[str]:
    text = unicodedata.normalize("NFKC", text).casefold()
    text = "".join(" " if unicodedata.category(c).startswith("P") else c for c in text)
    return [token for token in text.split() if token not in {"a", "an", "the"}]


def answer_f1(prediction: str, reference: str) -> float:
    predicted, expected = normalized_answer(prediction), normalized_answer(reference)
    if not predicted or not expected:
        return float(predicted == expected)
    overlap = sum((Counter(predicted) & Counter(expected)).values())
    return 2 * overlap / (len(predicted) + len(expected))


def reference_answer_score(example: dict, generated: str, accepted: bool) -> dict | None:
    """Reference matching is stricter than source-span acceptance; abstaining is not an answer."""
    expected = json.loads(example["messages"][-1]["content"])
    if expected["contentType"] not in {"answer", "unanswered"}:
        return None
    try:
        output = json.loads(generated)
    except (ValueError, TypeError):
        output = {}
    if not isinstance(output, dict):
        output = {}
    answerable = expected["contentType"] == "answer"
    abstained = output.get("contentType") == "unanswered"
    correct = accepted and output.get("contentType") == expected["contentType"] and (
        output.get("answer") == expected["answer"]
    )
    references = example.get("referenceAnswers", [expected["answer"]])
    type_matches = output.get("contentType") == expected["contentType"]
    normalized_match = accepted and type_matches and any(
        normalized_answer(output.get("answer", "")) == normalized_answer(ref) for ref in references)
    f1 = max(answer_f1(output.get("answer", ""), ref) for ref in references) if accepted and type_matches else 0.0
    return {"answerable": answerable, "abstained": abstained, "referenceMatch": correct,
            "normalizedReferenceMatch": normalized_match, "tokenF1": f1}


def summarize_answer_quality(results: list[dict]) -> dict | None:
    scores = [r["answerQuality"] for r in results if r.get("answerQuality") is not None]
    if not scores:
        return None
    answerable = [s for s in scores if s["answerable"]]
    correct = sum(s["referenceMatch"] for s in scores)
    correct_answers = sum(s["referenceMatch"] for s in answerable)
    return {
        "metric": "exact_reference_answer_and_correct_abstention",
        "samples": len(scores), "referenceMatches": correct,
        "referenceMatchRate": correct / len(scores),
        "normalizedReferenceMatchRate": sum(s.get("normalizedReferenceMatch", s["referenceMatch"]) for s in scores) / len(scores),
        "meanTokenF1": sum(s.get("tokenF1", float(s["referenceMatch"])) for s in scores) / len(scores),
        "answerableSamples": len(answerable), "correctAnswers": correct_answers,
        "answerableExactMatchRate": correct_answers / len(answerable) if answerable else 0.0,
        "abstentions": sum(s["abstained"] for s in scores),
        "incorrectAbstentions": sum(s["abstained"] for s in answerable),
    }


def main() -> None:
    args = parse_args()
    records = [
        json.loads(line) for line in args.data.read_text(encoding="utf-8").splitlines()
    ]
    records = balanced_sample(records, args.samples, args.seed)

    results = []
    reasons: Counter = Counter()
    durations_ms: list[int] = []
    for index, example in enumerate(records, start=1):
        started = time.monotonic()
        system_prompt, user_prompt, output_schema = runtime_request(example)
        command = [
            str(args.runner),
            "--model",
            str(args.model),
            "-sys",
            system_prompt,
            "--prompt",
            user_prompt,
            "--single-turn",
            "--predict",
            str(args.max_new_tokens),
            "--json-schema",
            output_schema,
            "--temp",
            "0",
            "--no-display-prompt",
            "--no-warmup",
            "--simple-io",
            "--no-conversation",
        ]
        if args.no_gpu:
            command.extend(["-ngl", "0"])
        completed = subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        )
        duration_ms = round((time.monotonic() - started) * 1_000)
        durations_ms.append(duration_ms)
        payload = extract_json(completed.stdout)
        accepted, reason = validate_output(example, payload)
        reasons[reason] += 1
        results.append(
            {
                "id": example["id"],
                "sourceCollection": example["sourceCollection"],
                "benchmarkFamily": example.get("benchmarkFamily", "legacy_unreviewed"),
                "accepted": accepted,
                "reason": reason,
                "generated": payload,
                "durationMs": duration_ms,
                "answerQuality": reference_answer_score(example, payload, accepted),
            }
        )
        print(
            f"[{index}/{len(records)}] {example['id']}: {reason} ({duration_ms} ms)",
            flush=True,
        )

    accepted_count = sum(item["accepted"] for item in results)
    report = {
        "schemaVersion": 2,
        "runtime": "llama.cpp",
        "runtimeContract": "android_system_prompt_json_schema_single_turn",
        "model": args.model.name,
        "samples": len(results),
        "accepted": accepted_count,
        "rejected": len(results) - accepted_count,
        "acceptanceRate": accepted_count / len(results) if results else 0.0,
        "answerQuality": summarize_answer_quality(results),
        "answerQualityByFamily": {family: summarize_answer_quality([r for r in results if r["benchmarkFamily"] == family])
                                  for family in sorted({r["benchmarkFamily"] for r in results})},
        "testDataSha256": hashlib.sha256(args.data.read_bytes()).hexdigest(),
        "qualityNote": "Contract acceptance is not factual accuracy. Topic-only questions require factual review.",
        "sourceIntegrityViolationsShownToUser": 0,
        "meanDurationMs": round(sum(durations_ms) / len(durations_ms))
        if durations_ms
        else 0,
        "reasons": dict(sorted(reasons.items())),
        "results": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(
        json.dumps(
            {key: value for key, value in report.items() if key != "results"}, indent=2
        )
    )


if __name__ == "__main__":
    main()
