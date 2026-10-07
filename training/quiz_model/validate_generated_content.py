#!/usr/bin/env python3
"""Validate model output and reject every item that is not exactly source grounded."""

from __future__ import annotations

import hashlib
import json
from typing import Any

ALLOWED_QUESTION_KINDS = {
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
}
FORBIDDEN_SCOPE_PHRASES = (
    "this narration",
    "this hadith",
    "this ayah",
    "this verse",
    "this passage",
    "this text",
    "according to",
    "which source",
    "which collection",
    "which surah contains",
)


def contains_arabic(text: str) -> bool:
    return any(
        "\u0600" <= character <= "\u06ff"
        or "\u0750" <= character <= "\u077f"
        or "\u08a0" <= character <= "\u08ff"
        for character in text
    )


def validate_semantic_question(
    source_text: str, output: dict[str, Any]
) -> tuple[bool, str]:
    expected_keys = {
        "contentType",
        "questionKind",
        "question",
        "answer",
        "evidence",
        "options",
    }
    if set(output) != expected_keys:
        return False, "unexpected_question_fields"
    if output.get("questionKind") not in ALLOWED_QUESTION_KINDS:
        return False, "unsupported_question_kind"
    question = output.get("question")
    answer = output.get("answer")
    evidence = output.get("evidence")
    options = output.get("options")
    if (
        not isinstance(question, str)
        or not question.endswith("?")
        or not 8 <= len(question) <= 180
    ):
        return False, "invalid_question"
    if any(phrase in question.lower() for phrase in FORBIDDEN_SCOPE_PHRASES):
        return False, "passage_dependent_question"
    if (
        not isinstance(answer, str)
        or not 1 <= len(answer) <= 96
        or answer not in source_text
    ):
        return False, "answer_not_exact_source_span"
    if (
        not isinstance(evidence, str)
        or not 1 <= len(evidence) <= 420
        or evidence not in source_text
        or answer not in evidence
    ):
        return False, "evidence_not_exact_source_span"
    if (
        not isinstance(options, list)
        or len(options) != 4
        or not all(
            isinstance(option, str) and 1 <= len(option) <= 96 for option in options
        )
    ):
        return False, "invalid_options"
    distractors = list(
        dict.fromkeys(
            option.casefold()
            for option in options
            if option.casefold() != answer.casefold()
        )
    )
    options_are_ready = (
        len({option.casefold() for option in options}) == 4
        and options.count(answer) == 1
    )
    options_are_repairable = len(distractors) >= 3
    if not options_are_ready and not options_are_repairable:
        return False, "invalid_options"
    if any(contains_arabic(value) for value in [question, answer, evidence, *options]):
        return False, "model_generated_arabic_forbidden"
    return True, "accepted"


def validate_answer(source_text: str, output: dict[str, Any]) -> tuple[bool, str]:
    if set(output) != {"contentType", "answer", "evidence"}:
        return False, "unexpected_answer_fields"
    if output.get("contentType") == "unanswered":
        if output.get("answer") == "" and output.get("evidence") == "":
            return True, "accepted"
        return False, "unanswered_with_spans"
    if output.get("contentType") != "answer":
        return False, "content_type_mismatch"
    answer = output.get("answer")
    evidence = output.get("evidence")
    if (
        not isinstance(answer, str)
        or not 1 <= len(answer) <= 96
        or answer not in source_text
    ):
        return False, "answer_not_exact_source_span"
    if (
        not isinstance(evidence, str)
        or not 1 <= len(evidence) <= 420
        or evidence not in source_text
        or answer not in evidence
    ):
        return False, "evidence_not_exact_source_span"
    if any(contains_arabic(value) for value in [answer, evidence]):
        return False, "model_generated_arabic_forbidden"
    return True, "accepted"


def validate_output(example: dict, generated: str) -> tuple[bool, str]:
    try:
        output: dict[str, Any] = json.loads(generated)
        expected = json.loads(example["messages"][-1]["content"])
    except (json.JSONDecodeError, KeyError, TypeError) as error:
        return False, f"invalid_json:{type(error).__name__}"

    if (
        hashlib.sha256(example["sourceText"].encode()).hexdigest()
        != example["sourceTextSha256"]
    ):
        return False, "input_source_hash_mismatch"

    # The answer model may legitimately return "unanswered" for an "answer" example,
    # so dispatch on the answer family before any strict content-type equality check.
    if expected["contentType"] in {"answer", "unanswered"}:
        return validate_answer(example["sourceText"], output)

    if output.get("contentType") != expected["contentType"]:
        return False, "content_type_mismatch"

    if output["contentType"] == "knowledge":
        if "body" in output:
            return False, "model_generated_source_text_forbidden"
        if any("arabic" in key.lower() for key in output):
            return False, "model_generated_arabic_forbidden"
        if set(output) != {"contentType", "title"}:
            return False, "unexpected_knowledge_fields"
        if not isinstance(output.get("title"), str) or not output["title"].strip():
            return False, "missing_title"
    else:
        if output == {"contentType": "question", "questionKind": "source_location"}:
            return True, "accepted"
        accepted, reason = validate_semantic_question(example["sourceText"], output)
        if not accepted:
            return False, reason

    # Exact key sets were validated above. Avoid substring matching here: for example,
    # "evidence" legitimately contains the letters "id" but is not an identifier field.
    return True, "accepted"
