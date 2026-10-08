#!/usr/bin/env python3
"""LoRA-tune a small Hugging Face causal LM on the grounded local dataset."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument(
        "--model",
        default="Qwen/Qwen2.5-0.5B-Instruct",
    )
    parser.add_argument("--model-version", default="deenly-knowledge-v0")
    parser.add_argument("--max-train-samples", type=int, default=1200)
    parser.add_argument("--max-eval-samples", type=int, default=160)
    parser.add_argument("--max-steps", type=int, default=120)
    parser.add_argument("--max-length", type=int, default=768)
    parser.add_argument("--seed", type=int, default=20261005)
    parser.add_argument(
        "--cpu",
        action="store_true",
        help="Force CPU training; avoids the GPU contention that makes the desktop unresponsive",
    )
    return parser.parse_args()


def read_sample(path: Path, limit: int, seed: int) -> list[dict]:
    records = [
        json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
    ]
    groups: dict[str, list[dict]] = {}
    for record in records:
        groups.setdefault(record["sourceCollection"], []).append(record)
    rng = random.Random(seed)
    for group in groups.values():
        rng.shuffle(group)

    # Round-robin prevents Musnad Ahmad (the largest collection) from dominating a small edge run.
    balanced: list[dict] = []
    group_names = sorted(groups)
    while len(balanced) < limit:
        added = False
        for name in group_names:
            if groups[name]:
                balanced.append(groups[name].pop())
                added = True
                if len(balanced) == limit:
                    break
        if not added:
            break
    rng.shuffle(balanced)
    return balanced


def encode_example(tokenizer, example: dict, max_length: int) -> dict[str, list[int]]:
    messages = example["messages"]
    prompt = tokenizer.apply_chat_template(
        messages[:-1], tokenize=False, add_generation_prompt=True
    )
    completion = messages[-1]["content"] + tokenizer.eos_token
    completion_ids = tokenizer(completion, add_special_tokens=False)["input_ids"]
    if len(completion_ids) >= max_length - 32:
        raise ValueError(f"Completion for {example['id']} exceeds max length")
    prompt_ids = tokenizer(prompt, add_special_tokens=False)["input_ids"]
    prompt_ids = prompt_ids[: max_length - len(completion_ids)]
    input_ids = prompt_ids + completion_ids
    return {
        "input_ids": input_ids,
        "attention_mask": [1] * len(input_ids),
        "labels": [-100] * len(prompt_ids) + completion_ids,
    }


def main() -> None:
    args = parse_args()
    try:
        import torch
        from peft import LoraConfig, get_peft_model
        from torch.utils.data import Dataset
        from transformers import (
            AutoModelForCausalLM,
            AutoTokenizer,
            Trainer,
            TrainingArguments,
            set_seed,
        )
    except ImportError as error:
        raise SystemExit(
            "Install training/quiz_model/requirements-hf.txt in a local virtual environment"
        ) from error

    set_seed(args.seed)
    tokenizer = AutoTokenizer.from_pretrained(args.model)
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token

    class GroundedDataset(Dataset):
        def __init__(self, records: list[dict]):
            self.records = records

        def __len__(self) -> int:
            return len(self.records)

        def __getitem__(self, index: int) -> dict[str, list[int]]:
            return encode_example(tokenizer, self.records[index], args.max_length)

    train_records = read_sample(
        args.data_dir / "train.jsonl", args.max_train_samples, args.seed
    )
    eval_records = read_sample(
        args.data_dir / "validation.jsonl", args.max_eval_samples, args.seed + 1
    )

    model = AutoModelForCausalLM.from_pretrained(args.model, torch_dtype=torch.float32)
    model.config.use_cache = False
    model = get_peft_model(
        model,
        LoraConfig(
            task_type="CAUSAL_LM",
            r=16,
            lora_alpha=32,
            lora_dropout=0.05,
            target_modules=[
                "q_proj",
                "k_proj",
                "v_proj",
                "o_proj",
                "gate_proj",
                "up_proj",
                "down_proj",
            ],
        ),
    )

    def collate(batch: list[dict[str, list[int]]]) -> dict[str, torch.Tensor]:
        length = max(len(item["input_ids"]) for item in batch)
        inputs, masks, labels = [], [], []
        for item in batch:
            padding = length - len(item["input_ids"])
            inputs.append(item["input_ids"] + [tokenizer.pad_token_id] * padding)
            masks.append(item["attention_mask"] + [0] * padding)
            labels.append(item["labels"] + [-100] * padding)
        return {
            "input_ids": torch.tensor(inputs, dtype=torch.long),
            "attention_mask": torch.tensor(masks, dtype=torch.long),
            "labels": torch.tensor(labels, dtype=torch.long),
        }

    args.output_dir.mkdir(parents=True, exist_ok=True)
    training_args = TrainingArguments(
        output_dir=str(args.output_dir / "checkpoints"),
        max_steps=args.max_steps,
        per_device_train_batch_size=1,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        warmup_ratio=0.05,
        weight_decay=0.01,
        logging_steps=5,
        eval_strategy="steps",
        eval_steps=max(20, args.max_steps // 3),
        save_strategy="no",
        report_to=[],
        remove_unused_columns=False,
        dataloader_pin_memory=False,
        seed=args.seed,
        use_cpu=args.cpu,
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=GroundedDataset(train_records),
        eval_dataset=GroundedDataset(eval_records),
        data_collator=collate,
    )
    train_result = trainer.train()
    eval_metrics = trainer.evaluate()

    adapter_dir = args.output_dir / "adapter"
    model.save_pretrained(adapter_dir)
    tokenizer.save_pretrained(adapter_dir)
    dataset_manifest = args.data_dir / "dataset_manifest.json"
    manifest = {
        "schemaVersion": 1,
        "modelVersion": args.model_version,
        "baseModel": args.model,
        "trainingMethod": "LoRA",
        "edgeFormat": "GGUF Q4_K_M",
        "trainSamples": len(train_records),
        "evalSamples": len(eval_records),
        "maxSteps": args.max_steps,
        "maxLength": args.max_length,
        "seed": args.seed,
        "datasetManifestSha256": hashlib.sha256(
            dataset_manifest.read_bytes()
        ).hexdigest(),
        "trainLoss": train_result.training_loss,
        "evalLoss": eval_metrics.get("eval_loss"),
        "evalPerplexity": (
            math.exp(eval_metrics["eval_loss"])
            if eval_metrics.get("eval_loss") is not None
            and eval_metrics["eval_loss"] < 20
            else None
        ),
        "quranArabicPolicy": "never_model_generated",
        "sourceResolution": "verified_runtime_source_envelope",
    }
    (args.output_dir / "training_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
