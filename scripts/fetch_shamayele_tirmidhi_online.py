#!/usr/bin/env python3
"""Build a verified Shama'il source database from public online editions.

The previously supplied SQLite file contains generated placeholder rows whose Arabic, Bengali, and
English texts do not describe the same narration. This script instead combines:

* the Bengali/Arabic *Sahih Shama'il* selection published by Hadith.one; and
* the chapter-matched English/Arabic Shama'il edition published by Habibur.com.

The Bengali edition has 56 chapters and 322 narration records. Its printed numbering ends at 320
because two source numbers are each used twice: 261 in chapter 47 and 264 in chapter 48. The app
uses stable record IDs 1-322 while retaining those original printed numbers in the detail table.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sqlite3
import unicodedata
import urllib.request
from dataclasses import dataclass
from difflib import SequenceMatcher
from html.parser import HTMLParser
from pathlib import Path


HADITH_ONE_URL = "https://hadith.one/bn/book/357/{description_id}"
HABIBUR_URL = "https://hadith.habibur.com/shamail/{chapter}/"
USER_AGENT = "Mozilla/5.0 (compatible; SubmissionContentBuilder/1.0)"
EXPECTED_CHAPTERS = 56
EXPECTED_HADITHS = 322
EXPECTED_SOURCE_NUMBERS = 320

# Printed source-number ranges. The two duplicate source numbers explain why these ranges end at
# 320 while the edition contains 322 actual narration records.
SOURCE_CHAPTER_RANGES = (
    (1, 11), (12, 18), (19, 26), (27, 29), (30, 37), (38, 40), (41, 43),
    (44, 55), (56, 57), (58, 58), (59, 68), (69, 74), (75, 80), (81, 81),
    (82, 83), (84, 85), (86, 89), (90, 92), (93, 94), (95, 95), (96, 97),
    (98, 101), (102, 102), (103, 106), (107, 112), (113, 136), (137, 138),
    (139, 143), (144, 145), (146, 149), (150, 151), (152, 160), (161, 164),
    (165, 167), (168, 173), (174, 179), (180, 187), (188, 188), (189, 194),
    (195, 215), (216, 222), (223, 223), (224, 239), (240, 246), (247, 252),
    (253, 253), (254, 263), (264, 274), (275, 275), (276, 281), (282, 283),
    (284, 289), (290, 294), (295, 305), (306, 312), (313, 320),
)
SOURCE_DUPLICATE_NUMBERS = {
    (254, 263): (261,),
    (264, 274): (264,),
}

BN_TO_ASCII = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")
NEXT_DATA_RE = re.compile(
    r'self\.__next_f\.push\(\[1,("(?:\\.|[^"\\])*")\]\)</script>',
    re.DOTALL,
)
BN_NUMBER_RE = re.compile(r"(?<![০-৯])([০-৯]{1,3})\.\s+")
PARAGRAPH_RE = re.compile(r"\n\s*\n")
ARABIC_CHAR_RE = re.compile(r"[\u0600-\u06ff]")
TRAILING_REFERENCE_RE = re.compile(r"\s*(\[[^\]]+\])\s*$")

ARABIC_PRIVATE_GLYPHS = str.maketrans(
    {
        "\uf06a": "صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ",
        "\uf074": "رَضِيَ اللَّهُ عَنْهُ",
    },
)


def expected_source_sequence(start: int, end: int) -> list[int]:
    sequence = list(range(start, end + 1))
    for duplicate in SOURCE_DUPLICATE_NUMBERS.get((start, end), ()):
        sequence.insert(sequence.index(duplicate) + 1, duplicate)
    return sequence


@dataclass(frozen=True)
class BengaliHadith:
    number: int
    arabic: str
    bengali: str
    explanation: str | None
    narrator_bengali: str | None
    references: str | None


@dataclass(frozen=True)
class EnglishHadith:
    number_in_chapter: str
    narrator: str | None
    english: str | None
    arabic: str


@dataclass(frozen=True)
class CombinedHadith:
    bengali: BengaliHadith
    english: EnglishHadith
    match_score: float


def fetch(url: str, cache_file: Path) -> str:
    if cache_file.exists():
        return cache_file.read_text(encoding="utf-8")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        content = response.read().decode("utf-8")
    cache_file.parent.mkdir(parents=True, exist_ok=True)
    cache_file.write_text(content, encoding="utf-8")
    return content


def decode_next_data_strings(page: str) -> list[str]:
    decoded: list[str] = []
    for match in NEXT_DATA_RE.finditer(page):
        try:
            decoded.append(json.loads(match.group(1)))
        except json.JSONDecodeError:
            continue
    return decoded


def arabic_count(value: str) -> int:
    return len(ARABIC_CHAR_RE.findall(value))


def arabic_before_marker(value: str, marker_start: int) -> tuple[int, str]:
    prefix = value[:marker_start].rstrip()
    arabic_start = prefix.rfind("\n\n") + 2
    # Short/single-record pages sometimes leave the narration nested inside a larger Next.js JSON
    # object. In that representation the current description field is the reliable start boundary.
    description_tokens = ('"desc":"', '\\"desc\\":\\"')
    description_start, description_token = max(
        ((prefix.rfind(token), token) for token in description_tokens),
        key=lambda item: item[0],
    )
    if description_start >= arabic_start:
        arabic_start = description_start + len(description_token)
    last_line_start = prefix.rfind("\n") + 1
    text_payload = re.match(r"\d+:T[0-9A-Za-z]+,", prefix[last_line_start:])
    if text_payload is not None and last_line_start >= arabic_start:
        arabic_start = last_line_start + text_payload.end()
    return arabic_start, prefix[arabic_start:].strip()


def extract_bengali_chapter(page: str, expected_start: int, expected_end: int) -> list[BengaliHadith]:
    # Depending on which Next.js flight chunk contains the record, line breaks may still be
    # represented as the two characters ``\\n`` after the outer JSON layer is decoded.
    strings = [value.replace("\\n", "\n") for value in decode_next_data_strings(page)]
    candidates = [value for value in strings if arabic_count(value) > 30 and "।" in value]
    if not candidates:
        raise ValueError(f"Could not locate chapter text for {expected_start}-{expected_end}")

    def valid_marker_count(value: str) -> int:
        count = 0
        for marker in BN_NUMBER_RE.finditer(value):
            number = int(marker.group(1).translate(BN_TO_ASCII))
            _, preceding_paragraph = arabic_before_marker(value, marker.start())
            if expected_start <= number <= expected_end and arabic_count(preceding_paragraph) >= 10:
                count += 1
        return count

    content = max(candidates, key=lambda value: (valid_marker_count(value), len(value)))
    content = content.replace("\r\n", "\n")
    if (expected_start, expected_end) == (189, 194):
        # The online Bengali page has a visible numbering typo (২৯৪) for Abu Qatada's final
        # narration in chapter 39. Its surrounding sequence and the parallel edition identify it
        # as hadith 194.
        content = content.replace("২৯৪. আবু কাতাদা", "১৯৪. আবু কাতাদা", 1)

    markers: list[tuple[re.Match[str], int, str]] = []
    for match in BN_NUMBER_RE.finditer(content):
        number = int(match.group(1).translate(BN_TO_ASCII))
        if number < expected_start or number > expected_end:
            continue
        arabic_start, arabic = arabic_before_marker(content, match.start())
        if arabic_count(arabic) >= 10:
            markers.append((match, arabic_start, arabic))

    expected_numbers = expected_source_sequence(expected_start, expected_end)
    actual_numbers = [int(match.group(1).translate(BN_TO_ASCII)) for match, _, _ in markers]
    if actual_numbers != expected_numbers:
        raise ValueError(
            f"Expected numbered hadiths {expected_start}-{expected_end}, found {actual_numbers}",
        )

    records: list[BengaliHadith] = []
    for index, (match, _, arabic) in enumerate(markers):
        record_end = markers[index + 1][1] if index + 1 < len(markers) else len(content)
        paragraphs = [part.strip() for part in PARAGRAPH_RE.split(content[match.end():record_end])]
        paragraphs = [part for part in paragraphs if part]
        if not paragraphs:
            raise ValueError(f"Hadith {actual_numbers[index]} has no Bengali translation")
        bengali_with_references = paragraphs[0]
        bengali = TRAILING_REFERENCE_RE.sub("", bengali_with_references).strip()

        explanation_parts = paragraphs[1:]
        # Hadith.one places the next narration's short section heading immediately before its
        # Arabic text. Our Arabic boundary therefore used to attach that heading to the previous
        # hadith's explanation (for example, hadith 1 ended with "তিনি ছিলেন গৌরবর্ণের :").
        while (
            explanation_parts
            and len(explanation_parts[-1]) <= 300
            and explanation_parts[-1].rstrip().endswith((":", "："))
        ):
            explanation_parts.pop()
        explanation = "\n\n".join(explanation_parts).strip() or None
        if explanation:
            explanation = re.sub(r"^ব্যাখ্যা\s*[:：]\s*", "", explanation).strip() or None
        narrator = extract_bengali_narrator(bengali)
        reference_matches = re.findall(r"\[[^\]]+\]", "\n".join(paragraphs))
        references = "\n".join(dict.fromkeys(reference_matches)) or None
        records.append(
            BengaliHadith(
                number=actual_numbers[index],
                arabic=arabic.translate(ARABIC_PRIVATE_GLYPHS),
                bengali=bengali,
                explanation=explanation,
                narrator_bengali=narrator,
                references=references,
            ),
        )
    return records


def extract_bengali_narrator(text: str) -> str | None:
    for separator in (" থেকে বর্ণিত", " হতে বর্ণিত", " বর্ণনা করেন"):
        if separator in text:
            narrator = text.split(separator, 1)[0].strip(" :-।")
            return narrator if len(narrator) <= 100 else None
    return None


class HabiburParser(HTMLParser):
    """Extract the four fields from each nested ``div.hadith`` block."""

    FIELDS = {"book", "narrator", "english", "arabic"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.stack: list[tuple[set[str], str | None]] = []
        self.current: dict[str, list[str]] | None = None
        self.field: str | None = None
        self.records: list[EnglishHadith] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "div":
            return
        attributes = dict(attrs)
        classes = set((attributes.get("class") or "").split())
        previous_field = self.field
        if "hadith" in classes:
            self.current = {field: [] for field in self.FIELDS}
        target = next((field for field in self.FIELDS if field in classes), None)
        if target is not None:
            self.field = target
        self.stack.append((classes, previous_field))

    def handle_endtag(self, tag: str) -> None:
        if tag != "div" or not self.stack:
            return
        classes, previous_field = self.stack.pop()
        self.field = previous_field
        if "hadith" in classes and self.current is not None:
            cleaned = {key: collapse(value) for key, value in self.current.items()}
            number_match = re.search(r"(\d+\.\d+)", cleaned["book"])
            # A few Habibur rows have an empty Arabic column but still provide a complete English
            # translation. Keep them as alignment candidates; chapter ordering can disambiguate
            # them if the Bengali selection includes the same narration.
            if not number_match:
                raise ValueError(f"Invalid Habibur hadith block: {cleaned}")
            self.records.append(
                EnglishHadith(
                    number_in_chapter=number_match.group(1),
                    narrator=cleaned["narrator"] or None,
                    english=cleaned["english"] or None,
                    arabic=cleaned["arabic"],
                ),
            )
            self.current = None
            self.field = None

    def handle_data(self, data: str) -> None:
        if self.current is not None and self.field is not None:
            self.current[self.field].append(data)


def collapse(parts: list[str]) -> str:
    return re.sub(r"\s+", " ", html.unescape("".join(parts))).strip()


def extract_english_chapter(page: str) -> list[EnglishHadith]:
    parser = HabiburParser()
    parser.feed(page)
    if not parser.records:
        raise ValueError("No English hadiths found in Habibur chapter")
    return parser.records


def normalize_arabic(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    normalized = "".join(character for character in normalized if unicodedata.category(character) != "Mn")
    normalized = normalized.translate(str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي"}))
    return "".join(character for character in normalized if "\u0621" <= character <= "\u064a")


def match_score(left: str, right: str) -> float:
    normalized_left = normalize_arabic(left)
    normalized_right = normalize_arabic(right)
    if not normalized_left or not normalized_right:
        return 0.0
    if normalized_left in normalized_right or normalized_right in normalized_left:
        return 1.0
    matcher = SequenceMatcher(None, normalized_left, normalized_right, autojunk=False)
    longest = matcher.find_longest_match(0, len(normalized_left), 0, len(normalized_right)).size
    coverage = longest / min(len(normalized_left), len(normalized_right))
    return max(coverage, matcher.ratio())


def combine_chapter(
    bengali_records: list[BengaliHadith],
    english_records: list[EnglishHadith],
) -> list[CombinedHadith]:
    if len(bengali_records) > len(english_records):
        raise ValueError(
            f"Only {len(english_records)} English candidates for "
            f"{len(bengali_records)} Bengali narrations",
        )

    # Find the highest-scoring monotonic alignment for the complete chapter. Greedily taking the
    # best remaining row can consume a later match when one edition phrases a narration very
    # differently; dynamic programming keeps both editions in their published order.
    row_count = len(bengali_records)
    candidate_count = len(english_records)
    scores = [
        [match_score(bengali.arabic, candidate.arabic) for candidate in english_records]
        for bengali in bengali_records
    ]
    impossible = float("-inf")
    totals = [[impossible] * (candidate_count + 1) for _ in range(row_count + 1)]
    took_match = [[False] * (candidate_count + 1) for _ in range(row_count + 1)]
    for candidate_index in range(candidate_count + 1):
        totals[0][candidate_index] = 0.0
    for row_index in range(1, row_count + 1):
        for candidate_index in range(1, candidate_count + 1):
            skip_total = totals[row_index][candidate_index - 1]
            match_total = (
                totals[row_index - 1][candidate_index - 1]
                + scores[row_index - 1][candidate_index - 1]
            )
            if match_total > skip_total:
                totals[row_index][candidate_index] = match_total
                took_match[row_index][candidate_index] = True
            else:
                totals[row_index][candidate_index] = skip_total

    match_indices: list[int] = []
    row_index = row_count
    candidate_index = candidate_count
    while row_index > 0 and candidate_index > 0:
        if took_match[row_index][candidate_index]:
            match_indices.append(candidate_index - 1)
            row_index -= 1
        candidate_index -= 1
    if row_index != 0:
        raise ValueError("Could not align all chapter narrations")
    match_indices.reverse()

    combined: list[CombinedHadith] = []
    for bengali, match_index in zip(bengali_records, match_indices):
        english_record = english_records[match_index]
        score = scores[len(combined)][match_index]
        # A low floor catches a missing/reordered row while allowing legitimate wording differences
        # between the Arabic editions. Chapter-wide ordering supplies the additional confidence.
        if score < 0.20:
            raise ValueError(
                f"Low-confidence English match for hadith {bengali.number}: "
                f"{english_record.number_in_chapter} scored {score:.3f}",
            )
        combined.append(CombinedHadith(bengali, english_record, score))
    return combined


def build(template: Path, output: Path, cache_dir: Path) -> None:
    if not template.exists():
        raise FileNotFoundError(template)
    if len(SOURCE_CHAPTER_RANGES) != EXPECTED_CHAPTERS:
        raise AssertionError("Chapter range table is incomplete")

    all_records: list[tuple[int, CombinedHadith]] = []
    for chapter, (expected_start, expected_end) in enumerate(SOURCE_CHAPTER_RANGES, start=1):
        # Hadith.one presents two early "living and lifestyle" selections as chapter 9. Habibur's
        # chapter 9 page is an empty notes placeholder and keeps both narrations in chapter 52.
        english_chapter = 52 if chapter == 9 else chapter
        hadith_one_page = fetch(
            HADITH_ONE_URL.format(description_id=chapter + 3),
            cache_dir / f"hadith-one-chapter-{chapter:02d}.html",
        )
        habibur_page = fetch(
            HABIBUR_URL.format(chapter=english_chapter),
            cache_dir / f"habibur-chapter-{english_chapter:02d}.html",
        )
        bengali_records = extract_bengali_chapter(hadith_one_page, expected_start, expected_end)
        english_records = extract_english_chapter(habibur_page)
        if chapter == 9:
            # These are Habibur 52.1 and 52.2 specifically. The first online English row omits its
            # Arabic column, so use the already verified Bengali-edition Arabic for alignment.
            english_records = english_records[:2]
            english_records = [
                EnglishHadith(
                    number_in_chapter=english.number_in_chapter,
                    narrator=english.narrator,
                    english=english.english,
                    arabic=english.arabic or bengali.arabic,
                )
                for bengali, english in zip(bengali_records, english_records)
            ]
        chapter_records = combine_chapter(bengali_records, english_records)
        all_records.extend((chapter, record) for record in chapter_records)
        print(
            f"Chapter {chapter:02d}: {len(chapter_records)} selected from "
            f"{len(english_records)}; min match {min(record.match_score for record in chapter_records):.3f}",
        )

    numbers = [record.bengali.number for _, record in all_records]
    expected_numbers = [
        number
        for source_range in SOURCE_CHAPTER_RANGES
        for number in expected_source_sequence(*source_range)
    ]
    if numbers != expected_numbers or len(numbers) != EXPECTED_HADITHS:
        raise ValueError(f"Online sequence is incomplete: {numbers}")

    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)
    with sqlite3.connect(template) as template_db, sqlite3.connect(output) as output_db:
        template_db.row_factory = sqlite3.Row
        output_db.executescript(
            """
            PRAGMA journal_mode = DELETE;
            CREATE TABLE book_info (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
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
            CREATE TABLE hadiths (
                hadith_id INTEGER PRIMARY KEY,
                chapter_id INTEGER,
                chapter_title_bn TEXT,
                chapter_title_en TEXT,
                hadith_no_in_book INTEGER,
                narrator_bn TEXT,
                narrator_en TEXT,
                arabic_text TEXT,
                bengali_text TEXT,
                english_text TEXT,
                bengali_explanation TEXT,
                grade_bn TEXT,
                grade_en TEXT,
                references_text TEXT,
                FOREIGN KEY(chapter_id) REFERENCES chapters(chapter_no)
            );
            """,
        )

        book = template_db.execute("SELECT * FROM book_info ORDER BY id LIMIT 1").fetchone()
        output_db.execute(
            """INSERT INTO book_info VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                book["id"], book["title_bn"], book["title_en"], book["compiler_bn"],
                book["compiler_en"], EXPECTED_HADITHS, EXPECTED_CHAPTERS,
                book["publisher_bn"], book["publisher_en"], book["languages"],
                "Online source matched and validated",
            ),
        )

        template_chapters = {
            row["chapter_no"]: row
            for row in template_db.execute("SELECT * FROM chapters ORDER BY chapter_no")
        }
        delivery_start = 1
        for chapter, source_range in enumerate(SOURCE_CHAPTER_RANGES, start=1):
            record_count = len(expected_source_sequence(*source_range))
            delivery_end = delivery_start + record_count - 1
            template_chapter = template_chapters[chapter]
            output_db.execute(
                "INSERT INTO chapters VALUES (?, ?, ?, ?, ?, ?, ?)",
                (
                    chapter,
                    template_chapter["title_bn"],
                    template_chapter["title_en"],
                    f"{delivery_start} - {delivery_end}",
                    delivery_start,
                    delivery_end,
                    record_count,
                ),
            )
            delivery_start = delivery_end + 1

        for record_id, (chapter, combined) in enumerate(all_records, start=1):
            source = combined.bengali
            translation = combined.english
            # Habibur places the attribution/setup in ``narrator`` and the quoted continuation in
            # ``english``. Joining both fields preserves the complete translated narration for
            # reading and TTS, including rows where either field holds most of the text.
            english_text = " ".join(
                part.strip()
                for part in (translation.narrator, translation.english)
                if part and part.strip()
            )
            chapter_row = template_chapters[chapter]
            output_db.execute(
                "INSERT INTO hadiths VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    record_id,
                    chapter,
                    chapter_row["title_bn"],
                    chapter_row["title_en"],
                    source.number,
                    source.narrator_bengali,
                    translation.narrator,
                    source.arabic,
                    source.bengali,
                    english_text,
                    source.explanation,
                    "সহীহ (Sahih)",
                    "Sahih",
                    source.references,
                ),
            )
        output_db.execute("PRAGMA user_version = 2")
        output_db.commit()
        integrity = output_db.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"Generated source database failed integrity check: {integrity}")

    print(
        f"Built {output}: {EXPECTED_CHAPTERS} chapters, {EXPECTED_HADITHS} records, "
        f"{EXPECTED_SOURCE_NUMBERS} printed numbers",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=Path("build/downloaded-content/shamayele-tirmidhi"),
    )
    args = parser.parse_args()
    build(args.template, args.output, args.cache_dir)


if __name__ == "__main__":
    main()
