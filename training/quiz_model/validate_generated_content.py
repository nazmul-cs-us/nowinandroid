#!/usr/bin/env python3
"""Validate model output and reject every item that is not exactly source grounded."""

from __future__ import annotations

import hashlib
import json
from typing import Any


def validate_output(example: dict, generated: str) -> tuple[bool, str]:
    try:
        output: dict[str, Any] = json.loads(generated)
        expected = json.loads(example["messages"][-1]["content"])
    except (json.JSONDecodeError, KeyError, TypeError) as error:
        return False, f"invalid_json:{type(error).__name__}"

    if output.get("contentType") != expected["contentType"]:
        return False, "content_type_mismatch"
    if hashlib.sha256(example["sourceText"].encode()).hexdigest() != example["sourceTextSha256"]:
        return False, "input_source_hash_mismatch"

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
        if output != {"contentType": "question", "questionKind": "source_location"}:
            return False, "unverified_question_template"

    # IDs, references, exact source text, and Arabic stay outside the model. The app resolves them
    # from the already validated request envelope and immutable database row.
    forbidden = ("id", "reference", "body", "source", "arabic")
    if any(any(token in key.lower() for token in forbidden) for key in output):
        return False, "model_generated_arabic_forbidden"
    return True, "accepted"
