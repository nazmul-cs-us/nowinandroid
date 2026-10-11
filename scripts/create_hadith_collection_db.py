#!/usr/bin/env python3
"""
Build English hadith collection databases from the published English editions
in AhmedBaset/hadith-json — the same source as the app's Sahih al-Bukhari DB
(scripts/create_bukhari_db.py).

The English text is the published book translation (Sahih Muslim, Muwatta
Malik, Sunan Abu Dawud, Sunan an-Nasa'i, Sunan Ibn Majah, Jami' at-Tirmidhi);
nothing is machine-translated. The source uses global ids across all books
(Muslim starts at 7278), so rows are renumbered sequentially from 1 to match
the app's daily-rotation id contract (getHadithById probes 1..hadithCount).
text_plain carries the published English (narrator + text) exactly like the
Bukhari database, text_arabic keeps the Arabic edition, and the FTS5 index is
rebuilt over text_plain so English typed questions reach these collections at
runtime.

Usage:
    python3 scripts/create_hadith_collection_db.py \
        --book-json /tmp/muslim.json --collection-id 2 --output build/.../sahih_muslim.db
"""

import argparse
import json
import os
import sqlite3


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--book-json", required=True, help="Downloaded AhmedBaset/hadith-json book file"
    )
    parser.add_argument(
        "--collection-id",
        required=True,
        help="Stable collection id for the metadata table",
    )
    parser.add_argument(
        "--output", required=True, help="SQLite database path to create"
    )
    parser.add_argument(
        "--display-name",
        help=(
            "Override the English display name (name + name_english) so the "
            "app's collection identity and the model's Collection prompt stay "
            "stable across the content swap (e.g. 'Sunan Abu Dawud' even when "
            "the published edition titles it 'Sunan Abi Dawud')."
        ),
    )
    return parser.parse_args()


def create_database(
    book_json: str, collection_id: str, output: str, display_name: str | None
) -> None:
    with open(book_json, encoding="utf-8") as source:
        data = json.load(source)

    hadiths = data.get("hadiths", [])
    metadata = data.get("metadata", {})
    english_meta = metadata.get("english", {})
    arabic_meta = metadata.get("arabic", {})

    if os.path.exists(output):
        os.remove(output)

    connection = sqlite3.connect(output)
    cursor = connection.cursor()

    cursor.execute(
        """
        CREATE TABLE metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """
    )
    metadata_entries = [
        ("collection_id", str(collection_id)),
        ("name", display_name or english_meta.get("title", "")),
        ("name_arabic", arabic_meta.get("title", "")),
        ("name_english", display_name or english_meta.get("title", "")),
        ("author", english_meta.get("author", "")),
        ("author_arabic", arabic_meta.get("author", "")),
        ("has_elaboration", "0"),
        ("hadith_count", str(len(hadiths))),
    ]
    cursor.executemany(
        "INSERT INTO metadata (key, value) VALUES (?, ?)", metadata_entries
    )

    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS hadiths (
            id INTEGER NOT NULL PRIMARY KEY,
            text_arabic TEXT NOT NULL,
            text_plain TEXT,
            elaboration TEXT
        )
        """
    )

    with_english = 0
    for sequential_id, hadith in enumerate(hadiths, start=1):
        text_arabic = hadith.get("arabic", "")
        english = hadith.get("english", {}) or {}
        narrator = english.get("narrator", "")
        text = english.get("text", "")
        if narrator or text:
            text_plain = f"{narrator}\n\n{text}".strip()
            with_english += 1
        else:
            # Keep the row (Arabic stays searchable); English flows reject it
            # exactly like the Bukhari database does.
            text_plain = None
        cursor.execute(
            "INSERT INTO hadiths (id, text_arabic, text_plain, elaboration) VALUES (?, ?, ?, ?)",
            (sequential_id, text_arabic, text_plain, None),
        )

    cursor.execute(
        """
        CREATE VIRTUAL TABLE hadiths_fts USING fts5(
            text_plain,
            content='hadiths',
            content_rowid='id'
        )
        """
    )
    cursor.execute("INSERT INTO hadiths_fts(hadiths_fts) VALUES('rebuild')")

    connection.commit()

    count = cursor.execute("SELECT COUNT(*) FROM hadiths").fetchone()[0]
    connection.close()
    print(
        f"Created {output}: {count} hadiths, {with_english} with published "
        f"English ({english_meta.get('title', '?')})"
    )


def main() -> None:
    args = parse_args()
    create_database(args.book_json, args.collection_id, args.output, args.display_name)


if __name__ == "__main__":
    main()
