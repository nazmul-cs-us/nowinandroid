#!/usr/bin/env python3
"""Merge a locally trained LoRA adapter into its Hugging Face base model for GGUF export."""

from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adapter", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()

    from peft import PeftConfig, PeftModel
    from transformers import AutoModelForCausalLM, AutoTokenizer

    config = PeftConfig.from_pretrained(args.adapter)
    base = AutoModelForCausalLM.from_pretrained(config.base_model_name_or_path)
    merged = PeftModel.from_pretrained(base, args.adapter).merge_and_unload()
    merged.save_pretrained(args.output_dir, safe_serialization=True)
    AutoTokenizer.from_pretrained(args.adapter).save_pretrained(args.output_dir)


if __name__ == "__main__":
    main()
