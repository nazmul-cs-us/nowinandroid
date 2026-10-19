#!/usr/bin/env python3
"""Merge tohed.com crawl output (JSONL) into the standard hadith-json format.

scripts/fetch_tohed_collection.py produces JSONL records:
  {"n": 1, "english": "...", "arabic": "...", "chapter_en": "...",
   "chapter_ar": "...", "hierarchy": [["ar", "en"], ...]}

This script merges one or more shards, sorts by hadith number, and emits the
same shape the rest of the pipeline already consumes
(scripts/create_hadith_collection_db.py, scripts/generate_hadith_collection_models.py):

  {"metadata": {...}, "chapters": [{"id", "english", "arabic"}],
   "hadiths": [{"chapterId", "arabic", "english": {"narrator", "text"}}]}

Chapters are derived from the page hierarchy's book level: the first entry
whose English name is not just the collection title (e.g. skip the
"Musnad Ahmad" root; keep "Musnad Abu Bakr as-Siddiq").

Usage:
  python3 scripts/tohed_jsonl_to_hadith_json.py --shards a.jsonl b.jsonl \
      --output merged.json --english-title "Sunan ad-Darimi" \
      --arabic-title "سنن الدارمي" --english-author "..." --arabic-author "..."
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def book_level(record: dict) -> tuple[str, str] | None:
    """The book = the hierarchy level below the collection root.

    tohed breadcrumbs start with the collection itself ('Darimi', 'Musnad
    Ahmad'), so the book is always the second entry when present.
    """
    hierarchy = record.get("hierarchy") or []
    if len(hierarchy) >= 2:
        arabic, english = hierarchy[1]
        return arabic.strip(), english.strip()
    if hierarchy:
        arabic, english = hierarchy[0]
        return arabic.strip(), english.strip()
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shards", nargs="+", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--english-title", required=True)
    parser.add_argument("--arabic-title", required=True)
    parser.add_argument("--english-author", default="")
    parser.add_argument("--arabic-author", default="")
    args = parser.parse_args()

    records: dict[int, dict] = {}
    for shard in args.shards:
        with Path(shard).open(encoding="utf-8") as source:
            for line in source:
                line = line.strip()
                if not line:
                    continue
                record = json.loads(line)
                records[int(record["n"])] = record

    if not records:
        raise SystemExit("No records found in shards")

    collection_titles = {args.english_title.strip().lower(), "musnad ahmad", "sunan ad-darimi"}

    chapters: list[dict] = []
    chapter_ids: dict[tuple[str, str], int] = {}
    hadiths: list[dict] = []
    missing_english = 0
    for n in sorted(records):
        record = records[n]
        book = book_level(record)
        if book is None:
            book = (record.get("chapter_ar") or "", record.get("chapter_en") or "")
        key = (book[0], book[1])
        if key not in chapter_ids:
            chapter_ids[key] = len(chapters) + 1
            chapters.append({"id": chapter_ids[key], "english": book[1], "arabic": book[0]})
        english = (record.get("english") or "").strip()
        arabic = (record.get("arabic") or "").strip()
        if not english:
            missing_english += 1
        hadiths.append(
            {
                "id": n,
                "chapterId": chapter_ids[key],
                "arabic": arabic,
                "english": {"narrator": "", "text": english},
            }
        )

    # Contiguity check: hadith numbers should cover 1..max with no duplicates
    # (missing ids are tolerated — the crawl logs them as gaps).
    numbers = [h["id"] for h in hadiths]
    gaps = sorted(set(range(1, max(numbers) + 1)) - set(numbers))

    output = {
        "metadata": {
            "english": {"title": args.english_title, "author": args.english_author},
            "arabic": {"title": args.arabic_title, "author": args.arabic_author},
        },
        "chapters": chapters,
        "hadiths": hadiths,
    }
    Path(args.output).write_text(json.dumps(output, ensure_ascii=False) + "\n", encoding="utf-8")
    print(
        f"merged {len(hadiths)} hadiths into {len(chapters)} books "
        f"({missing_english} without English); gaps: {gaps[:20]}"
        + (" …" if len(gaps) > 20 else "")
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
