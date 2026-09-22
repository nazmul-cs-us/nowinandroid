#!/usr/bin/env python3
"""Convert the verified online Shama'il source database into the app's Hadith schema.

The supplied database keeps Arabic, Bengali, and English text in a rich source schema. The Android
reader expects a four-column ``hadiths`` table, so this script creates a delivery database that has
that exact table while retaining every original field in ``hadith_details`` and all 56 chapters.
"""

from __future__ import annotations

import argparse
import sqlite3
from pathlib import Path


EXPECTED_CHAPTERS = 56
EXPECTED_HADITHS = 322


def joined(*parts: tuple[str, str | None]) -> str | None:
    sections = [f"{label}\n{value.strip()}" for label, value in parts if value and value.strip()]
    return "\n\n".join(sections) or None


def prepare(source: Path, output: Path) -> None:
    if source.resolve() == output.resolve():
        raise ValueError("Output must be different from the source database")

    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)

    with sqlite3.connect(source) as source_db, sqlite3.connect(output) as output_db:
        source_db.row_factory = sqlite3.Row
        chapter_count = source_db.execute("SELECT COUNT(*) FROM chapters").fetchone()[0]
        hadith_count = source_db.execute("SELECT COUNT(*) FROM hadiths").fetchone()[0]
        if (chapter_count, hadith_count) != (EXPECTED_CHAPTERS, EXPECTED_HADITHS):
            raise ValueError(
                f"Expected {EXPECTED_CHAPTERS} chapters and {EXPECTED_HADITHS} hadiths, "
                f"found {chapter_count} and {hadith_count}",
            )

        # Bengali commentary is optional per narration, but it must come from the source page.
        # A previous delivery database filled missing rows with a generic Bengali template, which
        # both hid the real parsed commentary and incorrectly claimed every hadith had an
        # explanation. Refuse that dataset so it cannot be published again.
        synthetic_explanations = source_db.execute(
            """
            SELECT COUNT(*) FROM hadiths
            WHERE bengali_explanation LIKE '%এ হাদিস থেকে%সুস্পষ্টভাবে প্রমাণিত হয়%'
            """,
        ).fetchone()[0]
        if synthetic_explanations:
            raise ValueError(
                f"Source contains {synthetic_explanations} generated Bengali explanations",
            )

        output_db.executescript(
            """
            PRAGMA journal_mode = DELETE;
            PRAGMA foreign_keys = ON;

            CREATE TABLE hadiths (
                id INTEGER NOT NULL PRIMARY KEY,
                text_arabic TEXT NOT NULL,
                text_plain TEXT,
                elaboration TEXT
            );

            CREATE TABLE metadata (
                key TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE TABLE book_info (
                id INTEGER PRIMARY KEY,
                title_bn TEXT,
                title_en TEXT,
                compiler_bn TEXT,
                compiler_en TEXT,
                total_hadiths INTEGER,
                total_chapters INTEGER,
                publisher_bn TEXT,
                publisher_en TEXT,
                languages TEXT,
                dataset_status TEXT
            );

            CREATE TABLE chapters (
                chapter_no INTEGER PRIMARY KEY,
                title_bn TEXT,
                title_en TEXT,
                hadith_range TEXT,
                start_hadith INTEGER,
                end_hadith INTEGER,
                total_hadiths INTEGER
            );

            CREATE TABLE hadith_details (
                hadith_id INTEGER PRIMARY KEY,
                chapter_id INTEGER NOT NULL,
                chapter_title_bn TEXT,
                chapter_title_en TEXT,
                hadith_no_in_book INTEGER,
                narrator_bn TEXT,
                narrator_en TEXT,
                arabic_text TEXT NOT NULL,
                bengali_text TEXT,
                english_text TEXT,
                bengali_explanation TEXT,
                grade_bn TEXT,
                grade_en TEXT,
                references_text TEXT,
                FOREIGN KEY(chapter_id) REFERENCES chapters(chapter_no)
            );

            CREATE INDEX index_hadith_details_chapter_id
                ON hadith_details(chapter_id, hadith_id);
            """,
        )

        book = source_db.execute("SELECT * FROM book_info ORDER BY id LIMIT 1").fetchone()
        output_db.execute(
            """INSERT INTO book_info VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            tuple(book),
        )

        chapters = source_db.execute("SELECT * FROM chapters ORDER BY chapter_no").fetchall()
        output_db.executemany(
            """INSERT INTO chapters VALUES (?, ?, ?, ?, ?, ?, ?)""",
            [tuple(chapter) for chapter in chapters],
        )

        details = source_db.execute("SELECT * FROM hadiths ORDER BY hadith_id").fetchall()
        output_db.executemany(
            """INSERT INTO hadith_details VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            [tuple(detail) for detail in details],
        )

        reader_rows = []
        for detail in details:
            text_plain = joined(
                ("English", detail["english_text"]),
                ("বাংলা", detail["bengali_text"]),
            )
            elaboration = joined(
                ("Narrator", detail["narrator_en"]),
                ("বর্ণনাকারী", detail["narrator_bn"]),
                ("ব্যাখ্যা", detail["bengali_explanation"]),
                ("Grade", detail["grade_en"]),
                ("মান", detail["grade_bn"]),
                ("References", detail["references_text"]),
            )
            reader_rows.append(
                (detail["hadith_id"], detail["arabic_text"], text_plain, elaboration),
            )
        output_db.executemany("INSERT INTO hadiths VALUES (?, ?, ?, ?)", reader_rows)

        metadata = {
            "collection_id": "9",
            "name": "Shamai'l At-Tirmidhi",
            "name_arabic": "الشمائل المحمدية",
            "name_english": "Shamai'l At-Tirmidhi",
            "author": book["compiler_en"] or "Imam Muhammad bin Isa At-Tirmidhi",
            "author_arabic": "الإمام محمد بن عيسى الترمذي",
            "has_elaboration": "1",
            "hadith_count": str(EXPECTED_HADITHS),
            "record_count": str(EXPECTED_HADITHS),
            "printed_number_count": "320",
            "chapter_count": str(EXPECTED_CHAPTERS),
            "source_bengali": "https://hadith.one/bn/book/357/1",
            "source_english": "https://hadith.habibur.com/shamail/",
        }
        output_db.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata.items())
        output_db.execute("PRAGMA user_version = 2")
        output_db.commit()

        integrity = output_db.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"Generated database failed integrity check: {integrity}")

    print(f"Prepared {output}: {EXPECTED_CHAPTERS} chapters, {EXPECTED_HADITHS} hadiths")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    prepare(args.source, args.output)


if __name__ == "__main__":
    main()
