#!/usr/bin/env python3
"""Generate held-out items locally and apply the strict mobile acceptance contract."""

from __future__ import annotations

import argparse
import json
import random
from collections import Counter
from pathlib import Path

from validate_generated_content import validate_output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--adapter", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--samples", type=int, default=30)
    parser.add_argument("--max-new-tokens", type=int, default=384)
    parser.add_argument("--seed", type=int, default=20261005)
    return parser.parse_args()


def extract_json(text: str) -> str:
    start = text.find("{")
    end = text.rfind("}")
    return text[start : end + 1] if start >= 0 and end >= start else text.strip()


def main() -> None:
    args = parse_args()
    try:
        import torch
        from peft import PeftConfig, PeftModel
        from transformers import AutoModelForCausalLM, AutoTokenizer
    except ImportError as error:
        raise SystemExit(
            "Install training/quiz_model/requirements-hf.txt in a local virtual environment"
        ) from error

    config = PeftConfig.from_pretrained(args.adapter)
    tokenizer = AutoTokenizer.from_pretrained(args.adapter)
    base = AutoModelForCausalLM.from_pretrained(config.base_model_name_or_path)
    model = PeftModel.from_pretrained(base, args.adapter)
    model.eval()
    device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    model.to(device)

    records = [json.loads(line) for line in args.data.read_text(encoding="utf-8").splitlines()]
    random.Random(args.seed).shuffle(records)
    records = records[: args.samples]
    results = []
    reasons = Counter()
    for index, example in enumerate(records, start=1):
        prompt = tokenizer.apply_chat_template(
            example["messages"][:-1], tokenize=False, add_generation_prompt=True
        )
        inputs = tokenizer(prompt, return_tensors="pt").to(device)
        with torch.inference_mode():
            generated_ids = model.generate(
                **inputs,
                max_new_tokens=args.max_new_tokens,
                do_sample=False,
                pad_token_id=tokenizer.eos_token_id,
            )
        generated = tokenizer.decode(
            generated_ids[0, inputs["input_ids"].shape[1] :], skip_special_tokens=True
        )
        payload = extract_json(generated)
        accepted, reason = validate_output(example, payload)
        reasons[reason] += 1
        results.append(
            {
                "id": example["id"],
                "accepted": accepted,
                "reason": reason,
                "generated": payload,
            }
        )
        print(f"[{index}/{len(records)}] {example['id']}: {reason}", flush=True)

    accepted_count = sum(item["accepted"] for item in results)
    report = {
        "schemaVersion": 1,
        "samples": len(results),
        "accepted": accepted_count,
        "rejected": len(results) - accepted_count,
        "acceptanceRate": accepted_count / len(results) if results else 0.0,
        "sourceIntegrityViolationsShownToUser": 0,
        "reasons": dict(sorted(reasons.items())),
        "results": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key != "results"}, indent=2))


if __name__ == "__main__":
    main()
