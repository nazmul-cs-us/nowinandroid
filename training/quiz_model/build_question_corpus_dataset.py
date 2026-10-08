#!/usr/bin/env python3
"""Build the question-contract dataset with REAL corpus records.

The question model's first release (deenly-question-v2) trained only on 313
synthetic contract sentences. Measured against real Bukhari and Quran sources,
the 0.5B regurgitated memorised training examples instead of quoting the
supplied source — every quiz turn silently fell back to the deterministic bank.

This dataset keeps the contract examples (they teach the four-option JSON
shape) and adds real passages: topical questions whose answer and evidence are
exact contiguous spans of that passage, with distractors drawn from other real
passages. The model learns to quote THIS source, not its training set.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
from pathlib import Path

from build_answer_contract_dataset import (
    TOPIC_ALIASES,
    answer_clause,
    bound_source,
    contains_arabic,
    is_clean_english,
    passage_corpus,
    sentence_spans,
    topic_for,
)
from build_semantic_contract_dataset import KINDS, rotate_options, semantic_example
from prepare_hf_dataset import SYSTEM_PROMPT


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--quran-translation", type=Path)
    parser.add_argument("--hadith-db", action="append", default=[], type=Path)
    parser.add_argument("--contract-examples-per-kind", type=int, default=30)
    parser.add_argument("--corpus-answer-target", type=int, default=700)
    parser.add_argument("--max-source-chars", type=int, default=600)
    parser.add_argument("--seed", type=int, default=20261008)
    return parser.parse_args()


# Natural, self-contained phrasings. A single template made the model lock
# onto one sentence shape (and pair it with mismatched spans); rotating
# through several keeps the questions varied while every one remains
# answerable by the paired span.
TOPIC_QUESTION_PHRASINGS = {
    "fasting": (
        "What does Islam teach about fasting?",
        "What is connected to fasting in the teachings?",
        "How does fasting appear in this guidance?",
        "What practice is tied to fasting?",
    ),
    "prayer": (
        "What is taught about prayer?",
        "How is prayer described in the teachings?",
        "What is connected to prayer?",
        "What practice relates to prayer?",
    ),
    "charity": (
        "What does Islam say about charity?",
        "How is charity described?",
        "What is encouraged regarding charity?",
        "What is tied to giving charity?",
    ),
    "patience": (
        "What is said about patience?",
        "How is patience described in the teachings?",
        "What is connected to patience?",
    ),
    "mercy": (
        "What is said about mercy?",
        "How is mercy described?",
        "What is connected to mercy?",
    ),
    "forgiveness": (
        "What is taught about forgiveness?",
        "How is forgiveness described?",
        "What is connected to forgiveness?",
    ),
    "parents": (
        "What is taught about parents?",
        "How should parents be treated?",
        "What is said about one's parents?",
    ),
    "neighbors": (
        "What is taught about neighbors?",
        "How should neighbors be treated?",
        "What is said about neighbors?",
    ),
    "knowledge": (
        "What is said about knowledge?",
        "How is knowledge described in the teachings?",
        "What is connected to seeking knowledge?",
    ),
    "honesty": (
        "What is taught about honesty?",
        "How is truthfulness described?",
        "What is said about honesty?",
    ),
    "gratitude": (
        "What is taught about gratitude?",
        "How is gratitude described?",
        "What is connected to being grateful?",
    ),
    "generosity": (
        "What is said about generosity?",
        "How is giving described in the teachings?",
        "What is encouraged about generosity?",
    ),
    "kindness": (
        "What is taught about kindness?",
        "How is kindness described?",
        "What is connected to kindness?",
    ),
    "water": (
        "What is said about water?",
        "How is water described in the teachings?",
        "What is connected to giving water?",
    ),
    "orphans": (
        "What is taught about orphans?",
        "How should orphans be treated?",
        "What is said about orphans?",
    ),
    "smiling": (
        "What is said about smiling?",
        "How is smiling described?",
        "What is connected to smiling?",
    ),
}

DEFAULT_QUESTION_PHRASINGS = (
    "What is this teaching about?",
    "How is this described in the teachings?",
    "What is encouraged here?",
)

# Verb patterns turn the answer clause itself into a specific question:
# "The Prophet loved dates" -> "What did the Prophet love?". These are the
# questions that read naturally instead of topic-template mechanically.
VERB_PATTERNS = (
    (re.compile(r"\b(loved|loves)\b", re.IGNORECASE), "love"),
    (re.compile(r"\b(liked|likes)\b", re.IGNORECASE), "like"),
    (re.compile(r"\b(preferred|prefers)\b", re.IGNORECASE), "prefer"),
    (re.compile(r"\b(ate|eats)\b", re.IGNORECASE), "eat"),
    (re.compile(r"\b(drank|drinks)\b", re.IGNORECASE), "drink"),
    (re.compile(r"\b(gave|gives)\b", re.IGNORECASE), "give"),
)


def specific_question_for_clause(clause: str) -> str | None:
    """A question that asks exactly what the clause states, when its
    structure allows one; otherwise the caller falls back to a topic
    phrasing."""
    for pattern, verb in VERB_PATTERNS:
        match = pattern.search(clause)
        if match is None:
            continue
        # Subject: up to three words before the verb.
        before = clause[: match.start()].strip(" ,;:")
        words = [w for w in before.split() if w]
        if not words:
            continue
        subject_words = words[-3:]
        subject = " ".join(subject_words).strip(" ,;:")
        subject = re.sub(r"^\W+", "", subject)
        if not subject or len(subject) < 3:
            continue
        # Drop dangling sentence fragments in the subject.
        if any(c in subject for c in ".!?"):
            continue
        return f"What did {subject} {verb}?"
    return None


def question_for(topic: str, clause: str, reference: str) -> str:
    specific = specific_question_for_clause(clause)
    if specific is not None:
        return specific
    phrasings = TOPIC_QUESTION_PHRASINGS.get(topic, DEFAULT_QUESTION_PHRASINGS)
    index = int.from_bytes(
        hashlib.sha256(f"{reference}|{topic}|q".encode()).digest()[:4], "big"
    ) % len(phrasings)
    return phrasings[index]


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def word_boundary_contains(sentence: str, alias: str) -> bool:
    import re

    return (
        re.search(rf"\b{re.escape(alias)}\b", sentence, flags=re.IGNORECASE) is not None
    )


def build_record(
    *,
    record_id: str,
    collection: str,
    reference: str,
    topic: str,
    source_text: str,
    payload: dict,
) -> dict:
    user = (
        "Task: create a question item.\n"
        f"Collection: {collection}\n"
        f"Reference: {reference}\n"
        f"Topic: {topic}\n"
        f"Source text:\n{source_text}"
    )
    return {
        "id": record_id,
        "sourceCollection": collection,
        "sourceReference": reference,
        "sourceText": source_text,
        "sourceTextSha256": sha256(source_text),
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


def contract_records(examples_per_kind: int) -> list[dict]:
    records = []
    for kind in KINDS:
        for index in range(examples_per_kind):
            source, question, answer, evidence, options = semantic_example(kind, index)
            if answer not in source or evidence not in source or answer not in evidence:
                raise ValueError(
                    f"Contract example {kind}/{index} is not span grounded"
                )
            records.append(
                build_record(
                    record_id=f"question-contract-{kind}-{index:04d}",
                    collection="verified_local_database",
                    reference=f"Contract example question-contract-{kind}-{index:04d}",
                    topic=kind,
                    source_text=source,
                    payload={
                        "contentType": "question",
                        "questionKind": kind,
                        "question": question,
                        "answer": answer,
                        "evidence": evidence,
                        "options": options,
                    },
                )
            )
    return records


def corpus_records(
    passages: list[dict],
    answer_target: int,
    max_source_chars: int,
    rng: random.Random,
) -> list[dict]:
    """Topical QA on real passages; answer/evidence are exact source spans."""
    rng.shuffle(passages)
    by_collection: dict[str, list[dict]] = {}
    for passage in passages:
        by_collection.setdefault(passage["collection"], []).append(passage)

    # A distractor pool of real short clauses per collection keeps the wrong
    # options in the corpus's own language instead of synthetic phrasing.
    distractor_pool: dict[str, list[str]] = {}
    seen_clauses: dict[str, set[str]] = {}
    for passage in passages:
        source_text = bound_source(passage["text"], max_source_chars)
        for span_start, span_end in sentence_spans(source_text):
            sentence = source_text[span_start:span_end].strip()
            if not (40 <= len(sentence) <= 240) or not is_clean_english(sentence):
                continue
            located = topic_for(sentence)
            if located is None:
                continue
            clause = answer_clause(sentence, located[1])
            if clause and len(clause) <= 96 and not contains_arabic(clause):
                # Identical clauses recur across similar passages; keep one of
                # each so the four options stay unique.
                seen = seen_clauses.setdefault(passage["collection"], set())
                if clause in seen:
                    continue
                seen.add(clause)
                distractor_pool.setdefault(passage["collection"], []).append(clause)

    records: list[dict] = []
    collections = sorted(by_collection)
    passage_index = 0
    while len(records) < answer_target and collections:
        made_progress = False
        for collection in collections:
            if len(records) >= answer_target:
                break
            pool = by_collection[collection]
            if passage_index >= len(pool):
                continue
            passage = pool[passage_index]
            made_progress = True
            source_text = bound_source(passage["text"], max_source_chars)
            for span_start, span_end in sentence_spans(source_text):
                sentence = source_text[span_start:span_end].strip()
                if not (40 <= len(sentence) <= 240) or not is_clean_english(sentence):
                    continue
                located = topic_for(sentence)
                if located is None:
                    continue
                topic, alias = located
                # The answer must read as a real phrase: the punctuation-bounded
                # clause around the alias, kept only when it is short. Long
                # clauses made v3's answers unwieldy; character windows made
                # broken fragments. Skipping both keeps every answer clean.
                answer = answer_clause(sentence, alias)
                if (
                    answer is None
                    or answer not in sentence
                    or answer not in source_text
                ):
                    continue
                if contains_arabic(answer) or not (8 <= len(answer) <= 56):
                    continue
                answer_end = sentence.find(answer) + len(answer)
                evidence_cut = min(len(sentence), max(answer_end, 110))
                evidence = sentence[:evidence_cut]
                if evidence_cut < len(sentence):
                    evidence = evidence.rstrip(" ,;:")
                    boundary = evidence.rsplit(" ", 1)
                    if len(boundary) > 1 and len(boundary[0]) >= answer_end:
                        evidence = boundary[0]
                if answer not in evidence or evidence not in source_text:
                    continue

                # Distractors stay short too — full-quote options made the quiz
                # read like a wall of citations, and fragments that begin
                # mid-word (comma-split leftovers) look broken.
                distractors = []
                for candidate in distractor_pool.get(collection, []):
                    if candidate == answer or candidate in evidence:
                        continue
                    trimmed = candidate[:64].rsplit(" ", 1)[0].strip(" ,;:\"'")
                    if len(trimmed) < 12 or trimmed == answer:
                        continue
                    if not trimmed[0].isalpha():
                        continue
                    distractors.append(trimmed)
                seen_distractors = set()
                unique_distractors = []
                for candidate in distractors:
                    key = candidate.casefold()
                    if key in seen_distractors:
                        continue
                    seen_distractors.add(key)
                    unique_distractors.append(candidate)
                rng.shuffle(unique_distractors)
                if len(unique_distractors) < 3:
                    continue
                options = rotate_options(
                    unique_distractors[:3] + [answer],
                    answer,
                    len(records),
                )

                # The specific verb-pattern questions read the full sentence,
                # not the short answer window.
                question = question_for(topic, sentence, passage["reference"])
                records.append(
                    build_record(
                        record_id=f"question-corpus-{topic}-{len(records):05d}",
                        collection=passage["collection"],
                        reference=passage["reference"],
                        topic=passage["topic"],
                        source_text=source_text,
                        payload={
                            "contentType": "question",
                            "questionKind": kind_for_topic(topic),
                            "question": question,
                            "answer": answer,
                            "evidence": evidence,
                            "options": options,
                        },
                    )
                )
                break
        passage_index += 1
        if not made_progress:
            break
    return records


def short_answer_window(sentence: str, alias: str, limit: int = 48) -> str | None:
    """A short contiguous span centred on the topic alias: one or two words
    on each side, cut at punctuation or the sentence edge. Stays an exact
    substring of the sentence."""
    match = re.search(rf"\b{re.escape(alias)}\b", sentence, flags=re.IGNORECASE)
    if match is None:
        return None
    words = sentence.split()
    # Character-offset based word window.
    index = len(sentence[: match.start()].split())
    low = max(0, index - 1)
    high = min(len(words), index + 2)
    window = " ".join(words[low:high]).strip(" ,;:\"'")
    if not window:
        return None
    if window not in sentence:
        # Fallback: the alias alone, verified below against the sentence.
        window = alias
    if window not in sentence:
        return None
    if len(window) > limit:
        # Trim to the alias plus its following word at a boundary.
        window = window[:limit].rsplit(" ", 1)[0].strip(" ,;:\"'")
        if alias.lower() not in window.lower():
            return None
        if window not in sentence:
            return None
    return window


def kind_for_topic(topic: str) -> str:
    """Maps a topical answer onto one of the allowed question kinds."""
    for kind, aliases in TOPIC_ALIASES:
        if topic == kind:
            break
    return {
        "fasting": "action",
        "prayer": "action",
        "charity": "action",
        "patience": "description",
        "mercy": "description",
        "forgiveness": "teaching",
        "parents": "teaching",
        "neighbors": "teaching",
        "knowledge": "teaching",
        "honesty": "teaching",
        "gratitude": "description",
        "generosity": "description",
        "kindness": "description",
        "water": "object",
        "orphans": "person",
        "smiling": "action",
    }.get(topic, "teaching")


def validate_record(record: dict) -> None:
    if record["sourceTextSha256"] != sha256(record["sourceText"]):
        raise ValueError(f"Source hash mismatch for {record['id']}")
    payload = json.loads(record["messages"][-1]["content"])
    source = record["sourceText"]
    if payload["answer"] not in source or payload["evidence"] not in source:
        raise ValueError(f"Question spans are not grounded in {record['id']}")
    if payload["answer"] not in payload["evidence"]:
        raise ValueError(f"Answer is not inside evidence for {record['id']}")
    if len(payload["options"]) != 4 or payload["options"].count(payload["answer"]) != 1:
        raise ValueError(f"Options shape invalid for {record['id']}")
    if len({o.casefold() for o in payload["options"]}) != 4:
        raise ValueError(f"Options are not unique for {record['id']}")


def split_for(record: dict) -> str:
    key = f"{record['sourceCollection']}|{record['sourceReference']}|{record['sourceTextSha256']}"
    bucket = int.from_bytes(hashlib.sha256(key.encode()).digest()[:8], "big") % 100
    if bucket < 80:
        return "train"
    if bucket < 90:
        return "validation"
    return "test"


def build(args: argparse.Namespace) -> dict:
    rng = random.Random(args.seed)
    contract = contract_records(args.contract_examples_per_kind)
    passages = passage_corpus(args.quran_translation, args.hadith_db)
    corpus = corpus_records(
        passages,
        answer_target=args.corpus_answer_target,
        max_source_chars=args.max_source_chars,
        rng=rng,
    )

    records = contract + corpus
    ids = [record["id"] for record in records]
    if len(ids) != len(set(ids)):
        raise ValueError("Question dataset ids are not unique")
    for record in records:
        validate_record(record)

    splits: dict[str, list[dict]] = {"train": [], "validation": [], "test": []}
    for record in records:
        splits[split_for(record)].append(record)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    for split, items in splits.items():
        rng.shuffle(items)
        with (args.output_dir / f"{split}.jsonl").open("w", encoding="utf-8") as target:
            for record in items:
                target.write(json.dumps(record, ensure_ascii=False) + "\n")

    by_family: dict[str, int] = {"contract": len(contract), "corpus": len(corpus)}
    manifest = {
        "schemaVersion": 2,
        "purpose": "question_contract_with_real_corpus_spans",
        "baseModel": "Qwen/Qwen2.5-0.5B-Instruct",
        "religiousFactsAdded": False,
        "runtimeKnowledgePolicy": "verified_source_envelope_only",
        "answerPolicy": "exact_contiguous_source_spans",
        "counts": {split: len(items) for split, items in splits.items()},
        "countsByFamily": by_family,
        "corpusPassagesAvailable": len(passages),
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
