#!/usr/bin/env python3
"""Assign canonical Sahih al-Bukhari book categories by hadith content.

The matcher intentionally does not use the legacy ``Volume/Book/Number`` value as
an identity.  Those numbers restart and are not unique in the JSON used by the
news feed.  Instead it aligns English or Arabic hadith content with a pinned
structured Bukhari corpus and returns one of its 97 canonical books.

Only Python's standard library is required.

Example:
    python3 scripts/categorize_bukhari_hadiths.py \
      --input https://example.com/sahih_bukhari.json \
      --output /tmp/bukhari_category_matches.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
import urllib.request
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable


CANONICAL_URL = (
    "https://raw.githubusercontent.com/AhmedBaset/hadith-json/v1.2.0/"
    "db/by_book/the_9_books/bukhari.json"
)
DEFAULT_MIN_SIMILARITY = 0.60
DEFAULT_MIN_MARGIN = 0.08


@dataclass(frozen=True)
class Category:
    id: int
    name_en: str
    name_ar: str
    hadith_count: int


@dataclass(frozen=True)
class CanonicalHadith:
    id: int
    category_id: int
    english: str
    arabic: str


@dataclass(frozen=True)
class InputHadith:
    source_index: int
    english: str
    arabic: str
    source_volume: str | None = None
    source_book: str | None = None
    source_reference: str | None = None


@dataclass(frozen=True)
class MatchResult:
    source_index: int
    canonical_id: int | None
    category_id: int | None
    category_name_en: str | None
    category_name_ar: str | None
    similarity: float
    method: str
    candidate_category_ids: list[int]
    source_volume: str | None = None
    source_book: str | None = None
    source_reference: str | None = None


def _english_tokens(text: str) -> list[str]:
    normalized = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    normalized = " ".join(re.findall(r"[a-z0-9]+", normalized.lower()))
    replacements = (
        ("allah s apostle", "allah messenger"),
        ("allahs apostle", "allah messenger"),
        ("allah s messenger", "allah messenger"),
        ("allahs messenger", "allah messenger"),
        ("the prophet s", "the prophet"),
        ("prophet s", "prophet"),
    )
    for old, new in replacements:
        normalized = normalized.replace(old, new)
    return normalized.split()


def _arabic_tokens(text: str) -> list[str]:
    normalized = unicodedata.normalize("NFKD", text)
    normalized = "".join(
        character
        for character in normalized
        if unicodedata.category(character) != "Mn" and character != "ـ"
    )
    normalized = normalized.translate(
        str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي"})
    )
    return re.findall(r"[\u0621-\u063a\u0641-\u064a0-9]+", normalized)


def _compact_key(tokens: Iterable[str]) -> str:
    # Removing spaces also tolerates harmless edition differences such as
    # "day light" versus "daylight".
    return "".join(tokens)


def _shingles(tokens: list[str], size: int = 3) -> frozenset[str]:
    if len(tokens) < size:
        return frozenset(tokens)
    return frozenset(" ".join(tokens[index : index + size]) for index in range(len(tokens) - size + 1))


def _load_json(location: str) -> Any:
    if location.startswith(("https://", "http://")):
        request = urllib.request.Request(location, headers={"User-Agent": "nowinandroid-bukhari-categorizer/1"})
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    with Path(location).open(encoding="utf-8") as source:
        return json.load(source)


def load_canonical(data: dict[str, Any]) -> tuple[list[Category], list[CanonicalHadith]]:
    raw_hadiths = data.get("hadiths", [])
    counts = Counter(int(item["chapterId"]) for item in raw_hadiths)
    categories = [
        Category(
            id=int(chapter["id"]),
            name_en=str(chapter.get("english", "")).strip(),
            name_ar=str(chapter.get("arabic", "")).strip(),
            hadith_count=counts[int(chapter["id"])],
        )
        for chapter in data.get("chapters", [])
    ]
    hadiths = [
        CanonicalHadith(
            id=int(item["id"]),
            category_id=int(item["chapterId"]),
            english=str(item.get("english", {}).get("text", "")).strip(),
            arabic=str(item.get("arabic", "")).strip(),
        )
        for item in raw_hadiths
    ]
    if len(categories) != 97:
        raise ValueError(f"Expected 97 canonical Bukhari categories, found {len(categories)}")
    return categories, hadiths


def load_input(data: Any) -> list[InputHadith]:
    """Read either the legacy volume JSON or a structured flat corpus."""
    result: list[InputHadith] = []
    if isinstance(data, list):
        for volume in data:
            volume_name = str(volume.get("name", "")).strip() or None
            for book in volume.get("books", []):
                book_name = str(book.get("name", "")).strip() or None
                for item in book.get("hadiths", []):
                    result.append(
                        InputHadith(
                            source_index=len(result),
                            english=str(item.get("text", "")).strip(),
                            arabic=str(item.get("arabic", "")).strip(),
                            source_volume=volume_name,
                            source_book=book_name,
                            source_reference=str(item.get("info", "")).strip() or None,
                        )
                    )
        return result

    if not isinstance(data, dict) or not isinstance(data.get("hadiths"), list):
        raise ValueError("Input must be a legacy volume array or an object containing 'hadiths'")
    for item in data["hadiths"]:
        english = item.get("english", "")
        if isinstance(english, dict):
            english = english.get("text", "")
        result.append(
            InputHadith(
                source_index=len(result),
                english=str(english).strip(),
                arabic=str(item.get("arabic", "")).strip(),
                source_reference=str(item.get("id", "")).strip() or None,
            )
        )
    return result


class ContentCategoryMatcher:
    """Content-based exact/fuzzy matcher for canonical Bukhari categories."""

    def __init__(self, categories: list[Category], hadiths: list[CanonicalHadith]) -> None:
        self.categories = {category.id: category for category in categories}
        self.hadiths = hadiths
        self._tokens: dict[str, list[list[str]]] = {"english": [], "arabic": []}
        self._shingles: dict[str, list[frozenset[str]]] = {"english": [], "arabic": []}
        self._exact: dict[str, dict[str, list[int]]] = {
            "english": defaultdict(list),
            "arabic": defaultdict(list),
        }
        self._inverted: dict[str, dict[str, list[int]]] = {
            "english": defaultdict(list),
            "arabic": defaultdict(list),
        }

        for index, hadith in enumerate(hadiths):
            for language, text, tokenizer in (
                ("english", hadith.english, _english_tokens),
                ("arabic", hadith.arabic, _arabic_tokens),
            ):
                tokens = tokenizer(text)
                shingles = _shingles(tokens)
                self._tokens[language].append(tokens)
                self._shingles[language].append(shingles)
                if tokens:
                    self._exact[language][_compact_key(tokens)].append(index)
                for shingle in shingles:
                    self._inverted[language][shingle].append(index)

    def match(
        self,
        item: InputHadith,
        min_similarity: float = DEFAULT_MIN_SIMILARITY,
        min_margin: float = DEFAULT_MIN_MARGIN,
    ) -> MatchResult:
        query_by_language = {
            "english": _english_tokens(item.english),
            "arabic": _arabic_tokens(item.arabic),
        }
        exact_candidates: set[int] = set()
        exact_languages: list[str] = []
        for language, tokens in query_by_language.items():
            if not tokens:
                continue
            candidates = self._exact[language].get(_compact_key(tokens), [])
            if candidates:
                exact_candidates.update(candidates)
                exact_languages.append(language)

        if exact_candidates:
            category_ids = sorted({self.hadiths[index].category_id for index in exact_candidates})
            if len(category_ids) == 1:
                best_index = min(exact_candidates, key=lambda index: self.hadiths[index].id)
                return self._resolved(
                    item,
                    best_index,
                    1.0,
                    f"exact_{'_'.join(exact_languages)}",
                    category_ids,
                )
            return self._unresolved(item, 1.0, "ambiguous_exact", category_ids)

        scores_by_hadith: dict[int, float] = {}
        language_by_hadith: dict[int, str] = {}
        for language, tokens in query_by_language.items():
            query_shingles = _shingles(tokens)
            if not query_shingles:
                continue
            overlaps: Counter[int] = Counter()
            for shingle in query_shingles:
                overlaps.update(self._inverted[language].get(shingle, ()))
            for index, overlap in overlaps.most_common(150):
                canonical_shingles = self._shingles[language][index]
                similarity = (2.0 * overlap) / (len(query_shingles) + len(canonical_shingles))
                if similarity > scores_by_hadith.get(index, -1.0):
                    scores_by_hadith[index] = similarity
                    language_by_hadith[index] = language

        if not scores_by_hadith:
            return self._unresolved(item, 0.0, "no_content_match", [])

        best_by_category: dict[int, tuple[float, int]] = {}
        for index, similarity in scores_by_hadith.items():
            category_id = self.hadiths[index].category_id
            if similarity > best_by_category.get(category_id, (-1.0, -1))[0]:
                best_by_category[category_id] = (similarity, index)
        ranked = sorted(
            ((score, category_id, index) for category_id, (score, index) in best_by_category.items()),
            reverse=True,
        )
        best_similarity, _, best_index = ranked[0]
        second_similarity = ranked[1][0] if len(ranked) > 1 else 0.0
        candidate_ids = [category_id for _, category_id, _ in ranked[:3]]
        if best_similarity < min_similarity:
            return self._unresolved(item, best_similarity, "low_similarity", candidate_ids)
        if best_similarity - second_similarity < min_margin:
            return self._unresolved(item, best_similarity, "ambiguous_fuzzy", candidate_ids)
        return self._resolved(
            item,
            best_index,
            best_similarity,
            f"fuzzy_{language_by_hadith[best_index]}",
            candidate_ids,
        )

    def _resolved(
        self,
        item: InputHadith,
        canonical_index: int,
        similarity: float,
        method: str,
        candidates: list[int],
    ) -> MatchResult:
        hadith = self.hadiths[canonical_index]
        category = self.categories[hadith.category_id]
        return MatchResult(
            source_index=item.source_index,
            canonical_id=hadith.id,
            category_id=category.id,
            category_name_en=category.name_en,
            category_name_ar=category.name_ar,
            similarity=round(similarity, 6),
            method=method,
            candidate_category_ids=candidates,
            source_volume=item.source_volume,
            source_book=item.source_book,
            source_reference=item.source_reference,
        )

    @staticmethod
    def _unresolved(
        item: InputHadith,
        similarity: float,
        method: str,
        candidates: list[int],
    ) -> MatchResult:
        return MatchResult(
            source_index=item.source_index,
            canonical_id=None,
            category_id=None,
            category_name_en=None,
            category_name_ar=None,
            similarity=round(similarity, 6),
            method=method,
            candidate_category_ids=candidates,
            source_volume=item.source_volume,
            source_book=item.source_book,
            source_reference=item.source_reference,
        )


def categorize(
    input_data: Any,
    canonical_data: dict[str, Any],
    min_similarity: float = DEFAULT_MIN_SIMILARITY,
    min_margin: float = DEFAULT_MIN_MARGIN,
) -> dict[str, Any]:
    categories, canonical_hadiths = load_canonical(canonical_data)
    inputs = load_input(input_data)
    matcher = ContentCategoryMatcher(categories, canonical_hadiths)
    matches = [matcher.match(item, min_similarity, min_margin) for item in inputs]
    resolved = [match for match in matches if match.category_id is not None]
    methods = Counter(match.method for match in matches)
    return {
        "schema_version": 1,
        "canonical_source": CANONICAL_URL,
        "settings": {
            "min_similarity": min_similarity,
            "min_margin": min_margin,
        },
        "summary": {
            "input_count": len(matches),
            "resolved_count": len(resolved),
            "unresolved_count": len(matches) - len(resolved),
            "coverage": round(len(resolved) / len(matches), 6) if matches else 0.0,
            "methods": dict(sorted(methods.items())),
        },
        "categories": [asdict(category) for category in categories],
        "matches": [asdict(match) for match in matches],
    }


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, help="Legacy/flat Bukhari JSON path or URL")
    parser.add_argument("--canonical", default=CANONICAL_URL, help="Canonical JSON path or URL")
    parser.add_argument("--output", help="Write full category mapping JSON to this path")
    parser.add_argument("--min-similarity", type=float, default=DEFAULT_MIN_SIMILARITY)
    parser.add_argument("--min-margin", type=float, default=DEFAULT_MIN_MARGIN)
    parser.add_argument(
        "--fail-on-unresolved",
        action="store_true",
        help="Return a non-zero status when any input cannot be categorized confidently",
    )
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    if not 0.0 <= args.min_similarity <= 1.0 or not 0.0 <= args.min_margin <= 1.0:
        print("Similarity and margin must be between 0 and 1", file=sys.stderr)
        return 2
    result = categorize(
        _load_json(args.input),
        _load_json(args.canonical),
        min_similarity=args.min_similarity,
        min_margin=args.min_margin,
    )
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["summary"], indent=2))
    if args.fail_on_unresolved and result["summary"]["unresolved_count"]:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
