#!/usr/bin/env python3
"""Build the grounded answer-contract dataset for the Now Nudge knowledge model.

The dataset teaches one skill: answer the user's question by quoting the supplied source,
or honestly report that the source does not answer it. It mixes three record families:

- Ordinary, non-religious contract sentences that teach exact-span extraction and the
  bounded JSON shape, exactly like the question-contract dataset.
- Real Quran and Hadith passages with topical questions whose answers are exact spans of
  the passage, so the model adapts to the corpus language it will see at runtime.
- Unanswered records where the question is provably absent from the source, teaching the
  model to reject instead of guessing.

Religious knowledge is never memorised: every answer and evidence is a verbatim span of the
immutable source that ships with the record.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import sqlite3
from pathlib import Path

from build_semantic_contract_dataset import KINDS, semantic_example
from prepare_hf_dataset import ANSWER_SYSTEM_PROMPT

TOPIC_ALIASES = [
    ("fasting", ("fast", "fasting", "fasts", "fasted", "ramadan", "sawm")),
    (
        "prayer",
        (
            "prayer",
            "prayers",
            "pray",
            "prays",
            "prayed",
            "salah",
            "prostration",
            "mosque",
        ),
    ),
    ("charity", ("charity", "alms", "sadaqah", "zakat", "sadaqa")),
    ("patience", ("patience", "patient", "persevere", "perseverance")),
    ("mercy", ("mercy", "merciful", "compassion", "compassionate")),
    ("forgiveness", ("forgive", "forgiveness", "forgiving", "pardon")),
    ("parents", ("parents", "parent", "mother", "father")),
    ("neighbors", ("neighbor", "neighbours", "neighbor's")),
    ("knowledge", ("knowledge", "learn", "teaching", "taught", "scholar")),
    ("honesty", ("honesty", "truthful", "truth", "lying", "liar")),
    ("gratitude", ("gratitude", "grateful", "thank", "thanks", "thankful")),
    ("generosity", ("generous", "generosity", "giving", "gave")),
    ("kindness", ("kind", "kindness", "kindly")),
    ("water", ("water", "drink", "thirsty")),
    ("orphans", ("orphan", "orphans")),
    ("smiling", ("smile", "smiling", "smiled")),
]

QUESTION_TEMPLATES = (
    "What does the source say about {topic}?",
    "What does the source teach about {topic}?",
    "What does the source mention about {topic}?",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--quran-translation", type=Path)
    parser.add_argument("--hadith-db", action="append", default=[], type=Path)
    parser.add_argument("--contract-examples-per-kind", type=int, default=20)
    parser.add_argument("--corpus-answer-target", type=int, default=300)
    parser.add_argument("--corpus-unanswered-target", type=int, default=160)
    parser.add_argument("--contract-unanswered-per-kind", type=int, default=3)
    parser.add_argument("--max-source-chars", type=int, default=700)
    parser.add_argument("--seed", type=int, default=20261007)
    return parser.parse_args()


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def stable_random(key: str) -> random.Random:
    seed = int.from_bytes(hashlib.sha256(key.encode("utf-8")).digest()[:8], "big")
    return random.Random(seed)


def contains_arabic(text: str) -> bool:
    return any(
        "\u0600" <= character <= "\u06ff"
        or "\u0750" <= character <= "\u077f"
        or "\u08a0" <= character <= "\u08ff"
        for character in text
    )


def is_clean_english(text: str) -> bool:
    if not text.strip() or contains_arabic(text):
        return False
    latin = sum(1 for character in text if character.isascii() and character.isalpha())
    return latin >= 12


def word_boundary_contains(sentence: str, alias: str) -> bool:
    pattern = rf"\b{re.escape(alias)}\b"
    if alias == "kind":
        # "every [kind of] fruit" is not a kindness statement.
        pattern = r"\bkind(?! of)\b"
    return re.search(pattern, sentence, flags=re.IGNORECASE) is not None


def topic_for(sentence: str) -> tuple[str, str] | None:
    for topic, aliases in TOPIC_ALIASES:
        for alias in aliases:
            if word_boundary_contains(sentence, alias):
                return topic, alias
    return None


def sentence_spans(text: str) -> list[tuple[int, int]]:
    """Character spans of every sentence-like segment in [text]."""
    spans = []
    for match in re.finditer(r"[^.!?]+[.!?]+(?=\s|[\"'\u201d])|[^.!?]+$", text):
        segment = match.span()
        stripped = text[segment[0] : segment[1]].strip()
        if stripped:
            spans.append(segment)
    return spans


def answer_clause(sentence: str, alias: str) -> str | None:
    """The smallest punctuation-bounded clause of [sentence] containing [alias]."""
    segments = re.split(r"([,;:])", sentence)
    clauses: list[str] = []
    buffer = ""
    for part in segments:
        buffer += part
        if part in {",", ";", ":"}:
            clauses.append(buffer)
            buffer = ""
    clauses.append(buffer)
    for clause in clauses:
        if word_boundary_contains(clause, alias):
            trimmed = clause.strip(" ,;:").strip()
            if trimmed and len(trimmed) <= 96:
                return trimmed
    # The alias survives only inside one long clause; fall back to a bounded window
    # around it. The window is always a contiguous slice of the clause.
    for clause in clauses:
        if word_boundary_contains(clause, alias):
            match = re.search(rf"\b{re.escape(alias)}\b", clause, flags=re.IGNORECASE)
            if match is None:
                return None
            center = (match.start() + match.end()) // 2
            window = clause[max(0, center - 48) : center + 48].strip()
            if len(window) > 96:
                window = window[:96].rsplit(" ", 1)[0]
            return window if window else None
    return None


def bound_source(text: str, limit: int) -> str:
    compact = " ".join(text.split())
    if len(compact) <= limit:
        return compact
    trimmed = compact[:limit].rsplit(" ", 1)[0]
    return trimmed


def build_record(
    *,
    record_id: str,
    collection: str,
    reference: str,
    topic: str,
    source_text: str,
    question: str,
    payload: dict,
) -> dict:
    user = (
        "Task: answer the user's question.\n"
        f"Collection: {collection}\n"
        f"Reference: {reference}\n"
        f"Topic: {topic}\n"
        f"User question: {question}\n"
        f"Source text:\n{source_text}"
    )
    return {
        "id": record_id,
        "sourceCollection": collection,
        "sourceReference": reference,
        "sourceText": source_text,
        "sourceTextSha256": sha256(source_text),
        "messages": [
            {"role": "system", "content": ANSWER_SYSTEM_PROMPT},
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
            source, question, answer, evidence, _ = semantic_example(kind, index)
            if answer not in source or evidence not in source or answer not in evidence:
                raise ValueError(
                    f"Contract example {kind}/{index} is not span grounded"
                )
            records.append(
                build_record(
                    record_id=f"answer-contract-{kind}-{index:04d}",
                    collection="verified_local_database",
                    reference=f"Contract example answer-contract-{kind}-{index:04d}",
                    topic=kind,
                    source_text=source,
                    question=question,
                    payload={
                        "contentType": "answer",
                        "answer": answer,
                        "evidence": evidence,
                    },
                )
            )
    return records


def contract_unanswered_records(per_kind: int) -> list[dict]:
    records = []
    for asked_kind in KINDS:
        for index in range(per_kind):
            other_kind = KINDS[(KINDS.index(asked_kind) + index + 1) % len(KINDS)]
            asked_source, question, asked_answer, _, _ = semantic_example(
                asked_kind, index
            )
            other_source, _, _, _, _ = semantic_example(other_kind, index)
            # The question must be genuinely absent from the paired source.
            if word_boundary_contains(other_source, asked_answer):
                continue
            _ = asked_source
            records.append(
                build_record(
                    record_id=f"answer-unanswered-contract-{asked_kind}-{index:04d}",
                    collection="verified_local_database",
                    reference=(
                        f"Contract example answer-unanswered-contract-{asked_kind}-{index:04d}"
                    ),
                    topic=other_kind,
                    source_text=other_source,
                    question=question,
                    payload={
                        "contentType": "unanswered",
                        "answer": "",
                        "evidence": "",
                    },
                )
            )
    return records


def passage_corpus(
    translation_path: Path | None, hadith_paths: list[Path]
) -> list[dict]:
    """Real corpus passages as (collection, reference, topic, text) dictionaries."""
    passages = []

    if translation_path is not None and translation_path.is_file():
        connection = sqlite3.connect(f"file:{translation_path}?mode=ro", uri=True)
        try:
            surahs = {
                row[0]: row
                for row in connection.execute(
                    "SELECT number, name_en, name_en_translation FROM surahs"
                )
            }
            for surah_number, ayah_number, text in connection.execute(
                """
                SELECT surah_number, number_in_surah, text
                FROM ayahs
                WHERE TRIM(text) <> ''
                ORDER BY surah_number, number_in_surah
                """
            ):
                if not is_clean_english(text):
                    continue
                passages.append(
                    {
                        "collection": "quran",
                        "reference": f"Quran {surah_number}:{ayah_number}",
                        "topic": (
                            (surahs.get(surah_number) or ("", "", ""))[2]
                            or (surahs.get(surah_number) or ("", "", ""))[1]
                            or f"Surah {surah_number}"
                        ).strip(),
                        "text": text,
                    }
                )
        finally:
            connection.close()

    for path in hadith_paths:
        if not path.is_file() or path.name.startswith("quran"):
            continue
        connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        try:
            tables = {
                row[0]
                for row in connection.execute(
                    "SELECT name FROM sqlite_master WHERE type='table'"
                )
            }
            if "hadiths" not in tables:
                continue
            if "metadata" in tables:
                metadata = dict(connection.execute("SELECT key, value FROM metadata"))
                display_name = (
                    metadata.get("name_english") or metadata.get("name") or path.stem
                )
            else:
                # The Shama'il database has no metadata table; its rows carry the
                # chapter title instead.
                display_name = path.stem
            if "hadith_details" in tables:
                rows = connection.execute(
                    """
                    SELECT d.hadith_id,
                           CASE
                               WHEN TRIM(d.english_text) <> '' THEN d.english_text
                               ELSE h.text_plain
                           END
                    FROM hadith_details d
                    JOIN hadiths h ON h.id = d.hadith_id
                    WHERE TRIM(COALESCE(NULLIF(d.english_text, ''), h.text_plain)) <> ''
                    ORDER BY d.hadith_id
                    """
                ).fetchall()
            else:
                rows = connection.execute(
                    "SELECT id, text_plain FROM hadiths WHERE TRIM(text_plain) <> '' ORDER BY id"
                ).fetchall()
        finally:
            connection.close()
        slug = " ".join(str(display_name).split()).lower().replace(" ", "_")
        for hadith_id, text in rows:
            if not is_clean_english(text):
                continue
            passages.append(
                {
                    "collection": slug,
                    "reference": f"{display_name} {hadith_id}",
                    "topic": " ".join(str(display_name).split()),
                    "text": text,
                }
            )

    return passages


def corpus_records(
    passages: list[dict],
    answer_target: int,
    unanswered_target: int,
    max_source_chars: int,
) -> list[dict]:
    """Topical QA spans and honest rejections drawn from the real corpus."""
    rng = random.Random(20261007)
    rng.shuffle(passages)

    by_collection: dict[str, list[dict]] = {}
    for passage in passages:
        by_collection.setdefault(passage["collection"], []).append(passage)

    answered_records: list[dict] = []
    unanswered_records: list[dict] = []
    collections = sorted(by_collection)
    answer_index = 0
    while len(answered_records) < answer_target and collections:
        made_progress = False
        for collection in collections:
            if len(answered_records) >= answer_target:
                break
            pool = by_collection[collection]
            if answer_index >= len(pool):
                continue
            passage = pool[answer_index]
            made_progress = True
            source_text = bound_source(passage["text"], max_source_chars)
            for span_start, span_end in sentence_spans(source_text):
                sentence = source_text[span_start:span_end].strip()
                # A 0.5B model copies short spans reliably and long ones poorly
                # (measured: accepted evidence averages ~78 chars, rejected
                # ~120). Skip sentences too long to quote cleanly.
                if not (40 <= len(sentence) <= 240) or not is_clean_english(sentence):
                    continue
                located = topic_for(sentence)
                if located is None:
                    continue
                topic, alias = located
                answer = answer_clause(sentence, alias)
                if (
                    answer is None
                    or answer not in sentence
                    or answer not in source_text
                ):
                    continue
                if contains_arabic(answer):
                    continue
                # Evidence: the shortest sentence prefix that still contains the
                # whole answer, cut at a word boundary so it remains a contiguous
                # source span while staying short enough for the model to quote.
                answer_end = sentence.find(answer) + len(answer)
                evidence_cut = min(len(sentence), max(answer_end, 110))
                evidence = sentence[:evidence_cut]
                if evidence_cut < len(sentence):
                    evidence = evidence.rstrip(" ,;:")
                    evidence = (
                        evidence[:answer_end]
                        if len(evidence) < answer_end
                        else evidence
                    )
                    boundary = evidence.rsplit(" ", 1)
                    if len(boundary) > 1 and len(boundary[0]) >= answer_end:
                        evidence = boundary[0]
                if answer not in evidence or evidence not in source_text:
                    continue
                template = QUESTION_TEMPLATES[
                    int.from_bytes(
                        hashlib.sha256(
                            f"{passage['reference']}|{topic}".encode()
                        ).digest()[:4],
                        "big",
                    )
                    % len(QUESTION_TEMPLATES)
                ]
                answered_records.append(
                    build_record(
                        record_id=f"answer-corpus-{topic}-{len(answered_records):05d}",
                        collection=passage["collection"],
                        reference=passage["reference"],
                        topic=passage["topic"],
                        source_text=source_text,
                        question=template.format(topic=topic),
                        payload={
                            "contentType": "answer",
                            "answer": answer,
                            "evidence": evidence,
                        },
                    )
                )
                break
        answer_index += 1
        if not made_progress:
            break

    # Unanswered corpus records: a topical question the passage does not address.
    unanswered_index = 0
    while len(unanswered_records) < unanswered_target and collections:
        made_progress = False
        for collection in collections:
            if len(unanswered_records) >= unanswered_target:
                break
            pool = by_collection[collection]
            if unanswered_index >= len(pool):
                continue
            passage = pool[unanswered_index]
            made_progress = True
            source_text = bound_source(passage["text"], max_source_chars)
            present_topics = set()
            for span_start, span_end in sentence_spans(source_text):
                located = topic_for(source_text[span_start:span_end])
                if located is not None:
                    present_topics.add(located[0])
            absent = [
                topic for topic, _ in TOPIC_ALIASES if topic not in present_topics
            ]
            if not absent:
                continue
            topic = absent[
                int.from_bytes(
                    hashlib.sha256(f"{passage['reference']}|absent".encode()).digest()[
                        :4
                    ],
                    "big",
                )
                % len(absent)
            ]
            template = QUESTION_TEMPLATES[
                int.from_bytes(
                    hashlib.sha256(f"{passage['reference']}|{topic}".encode()).digest()[
                        :4
                    ],
                    "big",
                )
                % len(QUESTION_TEMPLATES)
            ]
            unanswered_records.append(
                build_record(
                    record_id=(
                        f"answer-unanswered-corpus-{topic}-{len(unanswered_records):05d}"
                    ),
                    collection=passage["collection"],
                    reference=passage["reference"],
                    topic=passage["topic"],
                    source_text=source_text,
                    question=template.format(topic=topic),
                    payload={
                        "contentType": "unanswered",
                        "answer": "",
                        "evidence": "",
                    },
                )
            )
        unanswered_index += 1
        if not made_progress:
            break

    return answered_records + unanswered_records


def validate_record(record: dict) -> None:
    if record["sourceTextSha256"] != sha256(record["sourceText"]):
        raise ValueError(f"Source hash mismatch for {record['id']}")
    payload = json.loads(record["messages"][-1]["content"])
    source = record["sourceText"]
    if payload["contentType"] == "answer":
        if payload["answer"] not in source or payload["evidence"] not in source:
            raise ValueError(f"Answer spans are not grounded in {record['id']}")
        if payload["answer"] not in payload["evidence"]:
            raise ValueError(f"Answer is not inside evidence for {record['id']}")
        if contains_arabic(payload["answer"]) or contains_arabic(payload["evidence"]):
            raise ValueError(f"Arabic in model output for {record['id']}")
    elif payload["contentType"] == "unanswered":
        if payload["answer"] != "" or payload["evidence"] != "":
            raise ValueError(f"Unanswered record carries spans for {record['id']}")
    else:
        raise ValueError(f"Unknown contentType for {record['id']}")


def split_for(record: dict) -> str:
    key = f"{record['sourceCollection']}|{record['sourceReference']}|{record['sourceTextSha256']}"
    bucket = int.from_bytes(hashlib.sha256(key.encode()).digest()[:8], "big") % 100
    if bucket < 80:
        return "train"
    if bucket < 90:
        return "validation"
    return "test"


def build(args: argparse.Namespace) -> dict:
    contract = contract_records(args.contract_examples_per_kind)
    # Unanswered examples must be provably off-topic or the model learns to
    # fight its own reading. Only the synthetic contract pairs guarantee that:
    # a corpus passage without the topic *keyword* can still be about the
    # topic, and those ambiguous labels produced degenerate output.
    contract_unanswered = contract_unanswered_records(args.contract_unanswered_per_kind)
    passages = passage_corpus(args.quran_translation, args.hadith_db)
    corpus = corpus_records(
        passages,
        answer_target=args.corpus_answer_target,
        unanswered_target=0,
        max_source_chars=args.max_source_chars,
    )

    records = contract + contract_unanswered + corpus
    ids = [record["id"] for record in records]
    if len(ids) != len(set(ids)):
        raise ValueError("Answer dataset ids are not unique")
    for record in records:
        validate_record(record)

    splits: dict[str, list[dict]] = {"train": [], "validation": [], "test": []}
    for record in records:
        splits[split_for(record)].append(record)

    rng = random.Random(args.seed)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for split, items in splits.items():
        rng.shuffle(items)
        with (args.output_dir / f"{split}.jsonl").open("w", encoding="utf-8") as target:
            for record in items:
                target.write(json.dumps(record, ensure_ascii=False) + "\n")

    content_types: dict[str, int] = {}
    for record in records:
        payload = json.loads(record["messages"][-1]["content"])["contentType"]
        content_types[payload] = content_types.get(payload, 0) + 1

    manifest = {
        "schemaVersion": 2,
        "purpose": "grounded_answer_contract_instruction_tuning",
        "baseModel": "Qwen/Qwen2.5-0.5B-Instruct",
        "religiousFactsAdded": False,
        "runtimeKnowledgePolicy": "verified_source_envelope_only",
        "answerPolicy": "exact_contiguous_source_spans_or_unanswered",
        "counts": {split: len(items) for split, items in splits.items()},
        "countsByContentType": content_types,
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
