#!/usr/bin/env python3
"""Build source-locked Quran and Hadith learning content from Deenly assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import sqlite3
from collections import Counter
from pathlib import Path
from typing import Iterable


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--quran", required=True, type=Path, help="Arabic Quran SQLite database")
    parser.add_argument("--quran-translation", required=True, type=Path)
    parser.add_argument("--bukhari-json", required=True, type=Path)
    parser.add_argument("--shamayel", required=True, type=Path, help="Shama'il SQLite database")
    parser.add_argument(
        "--hadith-db",
        action="append",
        default=[],
        type=Path,
        help="Additional app Hadith SQLite database; repeat for every collection",
    )
    parser.add_argument("--output", required=True, type=Path, help="Candidate JSONL output")
    return parser.parse_args()


def compact(text: str) -> str:
    return " ".join(text.split())


def excerpt(text: str, limit: int = 360) -> str:
    clean = compact(text)
    if len(clean) <= limit:
        return clean
    shortened = clean[: limit - 1].rsplit(" ", 1)[0]
    return shortened + "…"


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def stable_random(key: str) -> random.Random:
    seed = int.from_bytes(hashlib.sha256(key.encode("utf-8")).digest()[:8], "big")
    return random.Random(seed)


def options_for(answer: str, pool: Iterable[str], key: str) -> tuple[list[str], int]:
    distractors = sorted({compact(item) for item in pool if compact(item) != compact(answer)})
    if len(distractors) < 3:
        raise ValueError(f"At least three unique distractors are required for {key}")
    rng = stable_random(key)
    options = rng.sample(distractors, 3) + [compact(answer)]
    rng.shuffle(options)
    return options, options.index(compact(answer))


def question(
    *,
    question_id: str,
    prompt: str,
    answer: str,
    option_pool: Iterable[str],
    explanation: str,
    source_collection: str,
    source_reference: str,
    source_url: str,
    topic: str,
    source_text: str,
    source_arabic: str | None = None,
    difficulty: str = "intermediate",
) -> dict:
    options, correct_option = options_for(answer, option_pool, question_id)
    return {
        "schemaVersion": 2,
        "contentType": "question",
        "id": question_id,
        "prompt": prompt,
        "options": options,
        "correctOption": correct_option,
        "explanation": explanation,
        "sourceLabel": source_reference,
        "sourceUrl": source_url,
        "sourceCollection": source_collection,
        "sourceReference": source_reference,
        "difficulty": difficulty,
        "topic": topic,
        "sourceText": source_text,
        "sourceTextSha256": sha256(source_text),
        "sourceArabic": source_arabic,
        "sourceArabicSha256": sha256(source_arabic) if source_arabic is not None else None,
        "reviewStatus": "source_locked",
    }


def knowledge(
    *,
    content_id: str,
    title: str,
    body: str,
    source_collection: str,
    source_reference: str,
    source_url: str,
    topic: str,
    source_arabic: str | None = None,
) -> dict:
    return {
        "schemaVersion": 2,
        "contentType": "knowledge",
        "id": content_id,
        "title": compact(title),
        "body": body,
        "sourceLabel": source_reference,
        "sourceUrl": source_url,
        "sourceCollection": source_collection,
        "sourceReference": source_reference,
        "topic": compact(topic),
        "sourceText": body,
        "sourceTextSha256": sha256(body),
        "sourceArabic": source_arabic,
        "sourceArabicSha256": sha256(source_arabic) if source_arabic is not None else None,
        "reviewStatus": "source_locked",
    }


def quran_questions(arabic_path: Path, translation_path: Path) -> Iterable[dict]:
    arabic = sqlite3.connect(f"file:{arabic_path}?mode=ro", uri=True)
    connection = sqlite3.connect(f"file:{translation_path}?mode=ro", uri=True)
    try:
        canonical_ayahs = {
            (surah_number, ayah_number): arabic_text
            for surah_number, ayah_number, arabic_text in arabic.execute(
                "SELECT surah_number, number_in_surah, text FROM ayahs"
            )
        }
        surahs = connection.execute(
            "SELECT number, name_en, name_en_translation, type FROM surahs ORDER BY number"
        ).fetchall()
        names = [compact(row[1]) for row in surahs]
        metadata = {row[0]: row for row in surahs}
        for surah_number, ayah_number, translated_text in connection.execute(
            """
            SELECT surah_number, number_in_surah, text
            FROM ayahs
            WHERE TRIM(text) <> ''
            ORDER BY surah_number, number_in_surah
            """
        ):
            source_arabic = canonical_ayahs.get((surah_number, ayah_number))
            if source_arabic is None:
                raise ValueError(
                    f"Translation has no matching Arabic source: {surah_number}:{ayah_number}"
                )
            _, surah_name, translated_name, revelation_type = metadata[surah_number]
            reference = f"Quran {surah_number}:{ayah_number}"
            yield question(
                question_id=f"quran-{surah_number}-{ayah_number}-surah",
                prompt=(
                    "Which surah contains this translated ayah?\n\n"
                    f"“{translated_text}”"
                ),
                answer=surah_name,
                option_pool=names,
                explanation=f"This is {reference}, in Surah {surah_name}.",
                source_collection="quran",
                source_reference=reference,
                source_url=f"https://quran.com/{surah_number}/{ayah_number}",
                topic=compact(translated_name or revelation_type or surah_name),
                source_text=translated_text,
                source_arabic=source_arabic,
            )
            yield knowledge(
                content_id=f"quran-{surah_number}-{ayah_number}-knowledge",
                title=f"From Surah {surah_name}",
                body=translated_text,
                source_collection="quran",
                source_reference=reference,
                source_url=f"https://quran.com/{surah_number}/{ayah_number}",
                topic=compact(translated_name or revelation_type or surah_name),
                source_arabic=source_arabic,
            )
    finally:
        connection.close()
        arabic.close()


def extract_hadith_number(info: str) -> int | None:
    match = re.search(r"Number\s*(\d+)", info, flags=re.IGNORECASE)
    return int(match.group(1)) if match else None


def extract_named_number(label: str, fallback: int) -> int:
    match = re.search(r"(\d+)", label)
    return int(match.group(1)) if match else fallback


def bukhari_questions(path: Path) -> Iterable[dict]:
    volumes = json.loads(path.read_text(encoding="utf-8"))
    books = [compact(book["name"]) for volume in volumes for book in volume["books"]]
    for volume_index, volume in enumerate(volumes, start=1):
        volume_number = extract_named_number(volume.get("name", ""), volume_index)
        for book_index, book in enumerate(volume["books"], start=1):
            book_number = extract_named_number(book.get("name", ""), book_index)
            book_name = compact(book["name"])
            for entry_index, hadith in enumerate(book["hadiths"], start=1):
                hadith_number = extract_hadith_number(hadith.get("info", ""))
                text = hadith.get("text", "")
                if hadith_number is None or not compact(text):
                    continue
                record_key = (
                    f"v{volume_number}-b{book_number}-n{hadith_number}-r{entry_index}"
                )
                reference = (
                    "Sahih al-Bukhari, "
                    f"Volume {volume_number}, Book {book_number}, Hadith {hadith_number}"
                )
                yield question(
                    question_id=f"bukhari-{record_key}-book",
                    prompt=(
                        "In which Sahih al-Bukhari book is this narration recorded?\n\n"
                        f"“{excerpt(text)}”"
                    ),
                    answer=book_name,
                    option_pool=books,
                    explanation=f"{reference} is recorded in {book_name}.",
                    source_collection="sahih_al_bukhari",
                    source_reference=reference,
                    source_url="https://sunnah.com/bukhari",
                    topic=book_name,
                    source_text=text,
                )
                yield knowledge(
                    content_id=f"bukhari-{record_key}-knowledge",
                    title=book_name,
                    body=text,
                    source_collection="sahih_al_bukhari",
                    source_reference=reference,
                    source_url="https://sunnah.com/bukhari",
                    topic=book_name,
                )


def shamayel_questions(path: Path) -> Iterable[dict]:
    connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        chapters = [
            compact(row[0])
            for row in connection.execute(
                "SELECT title_en FROM chapters WHERE TRIM(title_en) <> '' ORDER BY chapter_no"
            )
        ]
        for hadith_id, chapter_title, english_text in connection.execute(
            """
            SELECT d.hadith_id, d.chapter_title_en,
                   CASE
                       WHEN TRIM(d.english_text) <> '' THEN d.english_text
                       ELSE h.text_plain
                   END
            FROM hadith_details d
            JOIN hadiths h ON h.id = d.hadith_id
            WHERE TRIM(d.chapter_title_en) <> ''
              AND TRIM(COALESCE(NULLIF(d.english_text, ''), h.text_plain)) <> ''
            ORDER BY d.hadith_id
            """
        ):
            chapter_title = compact(chapter_title)
            reference = f"Shama'il At-Tirmidhi {hadith_id}"
            yield question(
                question_id=f"shamayel-{hadith_id}-chapter",
                prompt=(
                    "Which Shama'il At-Tirmidhi chapter contains this narration?\n\n"
                    f"“{excerpt(english_text)}”"
                ),
                answer=chapter_title,
                option_pool=chapters,
                explanation=f"{reference} is recorded in the chapter “{chapter_title}.”",
                source_collection="shamayel_at_tirmidhi",
                source_reference=reference,
                source_url=f"hadith://shamayel/{hadith_id}",
                topic=chapter_title,
                source_text=english_text,
            )
            yield knowledge(
                content_id=f"shamayel-{hadith_id}-knowledge",
                title=chapter_title,
                body=english_text,
                source_collection="shamayel_at_tirmidhi",
                source_reference=reference,
                source_url=f"hadith://shamayel/{hadith_id}",
                topic=chapter_title,
            )
    finally:
        connection.close()


def hadith_metadata(path: Path) -> dict[str, str]:
    connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        return dict(connection.execute("SELECT key, value FROM metadata"))
    finally:
        connection.close()


def collection_hadith_questions(paths: list[Path]) -> Iterable[dict]:
    """Build safe collection-location questions and exact-text cards for generic Hadith DBs."""
    collections = []
    for path in paths:
        metadata = hadith_metadata(path)
        collections.append(
            (
                path,
                compact(metadata.get("name", path.stem)).lower().replace(" ", "_"),
                compact(metadata.get("name_english", metadata.get("name", path.stem))),
            )
        )
    names = [name for _, _, name in collections]
    if collections and len(set(names)) < 4:
        raise ValueError("At least four Hadith collections are required for collection questions")

    for path, slug, display_name in collections:
        connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        try:
            for hadith_id, text_plain, text_arabic in connection.execute(
                """
                SELECT id, text_plain, text_arabic
                FROM hadiths
                WHERE TRIM(text_plain) <> ''
                ORDER BY id
                """
            ):
                reference = f"{display_name} {hadith_id}"
                source_arabic = text_arabic if compact(text_arabic or "") else None
                yield question(
                    question_id=f"{slug}-{hadith_id}-collection",
                    prompt=(
                        "Which Hadith collection records this narration?\n\n"
                        f"“{excerpt(text_plain)}”"
                    ),
                    answer=display_name,
                    option_pool=names,
                    explanation=f"This narration is recorded as {reference}.",
                    source_collection=slug,
                    source_reference=reference,
                    source_url=f"hadith://{slug}/{hadith_id}",
                    topic=display_name,
                    source_text=text_plain,
                    source_arabic=source_arabic,
                )
                yield knowledge(
                    content_id=f"{slug}-{hadith_id}-knowledge",
                    title=f"From {display_name}",
                    body=text_plain,
                    source_collection=slug,
                    source_reference=reference,
                    source_url=f"hadith://{slug}/{hadith_id}",
                    topic=display_name,
                    source_arabic=source_arabic,
                )
        finally:
            connection.close()


def validate(record: dict) -> None:
    if record["sourceTextSha256"] != sha256(record["sourceText"]):
        raise ValueError(f"Source text hash mismatch for {record['id']}")
    source_arabic = record.get("sourceArabic")
    if source_arabic is not None and record["sourceArabicSha256"] != sha256(source_arabic):
        raise ValueError(f"Arabic source hash mismatch for {record['id']}")
    if record["contentType"] == "question":
        if len(record["options"]) != 4 or len(set(record["options"])) != 4:
            raise ValueError(f"Question {record['id']} does not have four unique options")
        if record["correctOption"] not in range(4):
            raise ValueError(f"Question {record['id']} has an invalid correct option")
        if record["sourceCollection"] == "quran" and record["sourceText"] not in record["prompt"]:
            raise ValueError(f"Quran text was altered in question {record['id']}")
    elif not record.get("body"):
        raise ValueError(f"Knowledge card {record['id']} has no body")
    elif record["body"] != record["sourceText"]:
        raise ValueError(f"Source text was altered in knowledge card {record['id']}")
    if record["sourceCollection"] == "quran" and not source_arabic:
        raise ValueError(f"Quran content {record['id']} has no canonical Arabic ayah")
    if not record["sourceReference"]:
        raise ValueError(f"Question {record['id']} has no source reference")


def main() -> None:
    args = parse_args()
    for source in (
        args.quran,
        args.quran_translation,
        args.bukhari_json,
        args.shamayel,
        *args.hadith_db,
    ):
        if not source.is_file() or source.stat().st_size == 0:
            raise FileNotFoundError(f"Missing source asset: {source}")

    records = [
        *quran_questions(args.quran, args.quran_translation),
        *bukhari_questions(args.bukhari_json),
        *shamayel_questions(args.shamayel),
        *collection_hadith_questions(
            [path for path in args.hadith_db if path.resolve() != args.shamayel.resolve()]
        ),
    ]
    ids = [record["id"] for record in records]
    if len(ids) != len(set(ids)):
        raise ValueError("Generated learning-content IDs are not unique")
    for record in records:
        validate(record)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as target:
        for record in records:
            target.write(json.dumps(record, ensure_ascii=False) + "\n")

    counts = Counter(record["sourceCollection"] for record in records)
    content_type_counts = Counter(record["contentType"] for record in records)
    summary = {
        "schemaVersion": 2,
        "contentCount": len(records),
        "questionCount": content_type_counts["question"],
        "knowledgeCount": content_type_counts["knowledge"],
        "reviewStatus": "source_locked",
        "countsBySource": dict(sorted(counts.items())),
        "countsByContentType": dict(sorted(content_type_counts.items())),
    }
    args.output.with_suffix(".summary.json").write_text(
        json.dumps(summary, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
