#!/usr/bin/env python3
"""Build source-checked extractive QA, with synthetic and domain results kept distinct."""
from __future__ import annotations

import argparse
import hashlib
import json
import random
import sqlite3
from collections import Counter
from contextlib import closing
from pathlib import Path

try:
    from .build_answer_contract_dataset import build_record, validate_record
    from .build_semantic_contract_dataset import KINDS, semantic_example
    from .fetch_sources import file_sha256
except ImportError:
    from build_answer_contract_dataset import build_record, validate_record
    from build_semantic_contract_dataset import KINDS, semantic_example
    from fetch_sources import file_sha256


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def group_split(key: str) -> str:
    bucket = int(digest(key)[:8], 16) % 10
    return "test" if bucket == 0 else "validation" if bucket == 1 else "train"


def source_text(root: Path, case: dict) -> tuple[str, dict]:
    path = root / Path(case["sourceKey"]).name
    with closing(sqlite3.connect(f"file:{path}?mode=ro", uri=True)) as db:
        if case["sourceKey"] == "databases/quran/quran_en.db":
            surah, ayah = map(int, case["reference"].split(":"))
            original = db.execute("SELECT text FROM ayahs WHERE surah_number=? AND number_in_surah=?", (surah, ayah)).fetchone()[0]
            text, start = original, 0
        elif case["sourceKey"] == "databases/hadith/shamayele_tirmidhi_complete.db":
            original = db.execute("SELECT text_plain FROM hadiths WHERE id=?", (int(case["reference"]),)).fetchone()[0]
            # Keep the English section byte-for-byte, without rewriting whitespace or punctuation.
            start = len("English\n") if original.startswith("English\n") else 0
            end = original.find("\n\nবাংলা", start)
            text = original[start:end if end >= 0 else len(original)]
        else:
            raise ValueError(f"Unsupported source: {case['sourceKey']}")
    assert text == original[start:start + len(text)]
    return text, {"sourceKey": case["sourceKey"], "originalRowSha256": digest(original),
                  "sourceStart": start, "sourceEnd": start + len(text)}


def build(root: Path, specification: Path, output: Path) -> dict:
    lock = json.loads((root / "sources.lock.json").read_text())
    spec = json.loads(specification.read_text())
    required = {c["sourceKey"] for c in spec["cases"]}
    sources = {s["key"]: s for s in lock["sources"]}
    for key in required:
        if (key not in sources or spec.get("sourceFiles", {}).get(key) != sources[key]["sha256"]
                or file_sha256(root / Path(key).name) != sources[key]["sha256"]):
            raise ValueError(f"Source checksum mismatch: {key}")
    splits = {s: [] for s in ("train", "validation", "test")}
    groups = {}
    for case in spec["cases"]:
        text, provenance = source_text(root, case)
        split = case["split"]
        source_hash = digest(text)
        if case.get("sourceTextSha256") != source_hash:
            raise ValueError(f"Checked source changed: {case['reference']}")
        if groups.setdefault(source_hash, split) != split:
            raise ValueError("A source passage crosses dataset splits")
        for index, qa in enumerate(case["questions"]):
            answer = qa["answer"]
            if answer is not None and (not answer or answer not in text):
                raise ValueError(f"Missing checked answer in {case['reference']}: {answer!r}")
            if len(text) > 420:
                raise ValueError("Checked evidence must fit the contract; never truncate a source")
            row = build_record(record_id=f"checked-{Path(case['sourceKey']).stem}-{case['reference']}-{index}",
                collection="quran" if "quran/" in case["sourceKey"] else "shamai'l_at-tirmidhi",
                reference=case["reference"], topic="Source reading", source_text=text,
                question=qa["question"], payload={"contentType": "answer" if answer is not None else "unanswered",
                    "answer": answer or "", "evidence": text if answer is not None else ""})
            row.update(benchmarkFamily="source_checked", reviewStatus=spec["reviewStatus"], provenance=provenance)
            splits[split].append(row)

    # No topical aliases, substring windows, or absence-by-keyword labels.
    # All variants of the underlying target sentence stay in the same split.
    seen = set()
    for kind_index, kind in enumerate(KINDS):
        for index in range(36):
            core, question, answer, evidence, _ = semantic_example(kind, index)
            if (core, question) in seen:
                continue
            seen.add((core, question))
            split = group_split(core)
            for variant in range(3):
                other_kind = KINDS[(kind_index + variant + 1) % len(KINDS)]
                candidates = [semantic_example(other_kind, j)[0] for j in range(36)
                              if group_split(semantic_example(other_kind, j)[0]) == split]
                distractor = candidates[(index + variant) % len(candidates)] if candidates else "The report ends here."
                source = f"{distractor} {core}" if variant % 2 else f"{core} {distractor}"
                # A missing category is guaranteed by the templates, not inferred from missing keywords.
                asked = (question, answer, evidence)
                absent_kind = next(k for k in KINDS if k not in {kind, other_kind})
                _, absent_question, _, _, _ = semantic_example(absent_kind, index)
                for label, q, a, e in (("answer", *asked), ("unanswered", absent_question, "", "")):
                    row = build_record(record_id=f"checked-contract-{kind}-{index}-{variant}-{label}",
                        collection="contract_examples", reference=f"Synthetic source {digest(core)[:16]}",
                        topic="Source reading", source_text=source, question=q,
                        payload={"contentType": label, "answer": a, "evidence": e})
                    row.update(benchmarkFamily="synthetic_contract", splitGroup=digest(core))
                    splits[split].append(row)
    # Weight scarce source-checked training pairs without presenting repeats as new examples.
    checked_train = [r for r in splits["train"] if r["benchmarkFamily"] == "source_checked"]
    for repeat in range(1, 8):
        splits["train"].extend(dict(r, id=f"{r['id']}-repeat-{repeat}") for r in checked_train)
    output.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for split, rows in splits.items():
        random.Random(20261118).shuffle(rows)
        for row in rows:
            validate_record(row)
        content = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
        (output / f"{split}.jsonl").write_text(content)
        hashes[split] = digest(content)
    manifest = {"schemaVersion": 3, "policy": "explicit_questions_verbatim_sources_v1",
        "reviewStatus": spec["reviewStatus"], "scope": spec["scope"],
        "sourceCheckedTrainingWeight": 8,
        "specificationSha256": file_sha256(specification),
        "sourceFiles": {key: sources[key]["sha256"] for key in sorted(required)},
        "counts": {s: len(rows) for s, rows in splits.items()}, "splitSha256": hashes,
        "families": {s: dict(Counter(r["benchmarkFamily"] for r in rows)) for s, rows in splits.items()}}
    (output / "dataset_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--specification", type=Path, default=Path("training/quiz_model/datasets/answer-source-checks-v1/questions.json"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.sources, args.specification, args.output), indent=2))
