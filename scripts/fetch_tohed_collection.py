#!/usr/bin/env python3
"""Fetch a tohed.com hadith collection with Arabic + English text.

en.tohed.com is currently the only source with a complete human-quality
English translation of Sunan ad-Darimi and Musnad Ahmad. Each hadith page
exposes the Arabic text (class "hadith-arabic"), the English translation (an
application/ld+json "articleBody" block), and the chapter hierarchy, so this
crawler rebuilds a structured bilingual corpus one page at a time.

The crawler is polite and resumable:
  * hadith pages are not disallowed by robots.txt
  * a fixed delay between requests, with linear backoff on errors
  * every fetched page is parsed and appended to a JSONL file; already
    present ids are skipped, so a killed run continues where it stopped

Output JSONL record:
  {"n": 1, "chapter_en": "...", "chapter_ar": "...", "english": "...", "arabic": "..."}

Usage:
  python3 scripts/fetch_tohed_collection.py --collection sunan-ad-darimi \
      --hadiths 1-3535 --output /tmp/tohed/sunan-ad-darimi.jsonl
  python3 scripts/fetch_tohed_collection.py --collection musnad-ahmad \
      --hadiths 1-27647 --output /tmp/tohed/musnad-ahmad.jsonl --delay 0.4
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from pathlib import Path

BASE_URL = "https://en.tohed.com/hadith"
USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) nowinandroid-hadith-fetcher/1.0"
FETCH_TIMEOUT = 60

LDJSON_BLOCKS = re.compile(
    r'<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>', re.S
)
ARABIC_BLOCK = re.compile(r'class="hadith-arabic"[^>]*>(.*?)</(?:div|p)>', re.S)
HIER_BLOCKS = re.compile(
    r'<span class="hadith-hier-arabic"[^>]*>(.*?)</span>\s*'
    r'<div class="hadith-hier-english">(.*?)</div>',
    re.S,
)
TAGS = re.compile(r"<[^>]+>")


def strip_tags(fragment: str) -> str:
    text = TAGS.sub(" ", fragment)
    text = html.unescape(text)
    return " ".join(text.split()).strip()


def parse_page(raw: str) -> dict | None:
    english = ""
    for block in LDJSON_BLOCKS.findall(raw):
        try:
            data = json.loads(block)
        except json.JSONDecodeError:
            continue
        body = data.get("articleBody")
        if isinstance(body, str) and body.strip():
            english = " ".join(body.split())
            break
    if not english:
        return None

    arabic_match = ARABIC_BLOCK.search(raw)
    arabic = strip_tags(arabic_match.group(1)) if arabic_match else ""

    # Full breadcrumb: book > chapter > sub-chapter. The DB builder picks the
    # level it needs; the deepest entry is the leaf section.
    hierarchy = [
        [strip_tags(ar), strip_tags(en)]
        for ar, en in HIER_BLOCKS.findall(raw)
    ]
    chapter_ar, chapter_en = "", ""
    if hierarchy:
        chapter_ar, chapter_en = hierarchy[-1]
    return {
        "english": english,
        "arabic": arabic,
        "chapter_en": chapter_en,
        "chapter_ar": chapter_ar,
        "hierarchy": hierarchy,
    }


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=FETCH_TIMEOUT) as response:
        return response.read().decode("utf-8", errors="replace")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--collection", required=True, help="tohed collection slug, e.g. sunan-ad-darimi")
    parser.add_argument(
        "--hadiths",
        required=True,
        help="Inclusive id range to fetch, e.g. 1-3535 (404s end the run when --stop-on-404)",
    )
    parser.add_argument("--output", required=True, help="JSONL output path")
    parser.add_argument("--delay", type=float, default=0.35, help="Seconds between requests")
    parser.add_argument(
        "--stop-on-404",
        action="store_true",
        help="Stop when a hadith number 404s (collection end) instead of treating it as an error",
    )
    args = parser.parse_args()

    if "-" in args.hadiths:
        start, end = (int(part) for part in args.hadiths.split("-", 1))
    else:
        start = end = int(args.hadiths)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    done: set[int] = set()
    if output.exists():
        with output.open(encoding="utf-8") as existing:
            for line in existing:
                try:
                    done.add(json.loads(line)["n"])
                except (json.JSONDecodeError, KeyError):
                    continue
        print(f"resuming: {len(done)} hadiths already fetched", flush=True)

    consecutive_failures = 0
    fetched = 0
    with output.open("a", encoding="utf-8") as sink:
        for n in range(start, end + 1):
            if n in done:
                continue
            url = f"{BASE_URL}/{args.collection}/{n}/"
            try:
                raw = fetch(url)
            except urllib.error.HTTPError as error:
                if error.code == 404 and args.stop_on_404:
                    print(f"{n}: 404 — collection end, stopping", flush=True)
                    break
                consecutive_failures += 1
                if consecutive_failures >= 10:
                    print(f"{n}: giving up after 10 consecutive failures", flush=True)
                    return 1
                time.sleep(args.delay * (consecutive_failures + 1))
                continue
            except (urllib.error.URLError, TimeoutError, OSError) as error:
                consecutive_failures += 1
                if consecutive_failures >= 10:
                    print(f"{n}: giving up after 10 consecutive failures ({error})", flush=True)
                    return 1
                time.sleep(args.delay * (consecutive_failures + 1))
                continue
            consecutive_failures = 0
            record = parse_page(raw)
            if record is None:
                # A page that exists but has no parseable English is a gap
                # worth seeing in the logs; it is not retried.
                print(f"{n}: no English body on page", flush=True)
                record = {"english": "", "arabic": "", "chapter_en": "", "chapter_ar": ""}
            record["n"] = n
            sink.write(json.dumps(record, ensure_ascii=False) + "\n")
            sink.flush()
            fetched += 1
            if fetched % 50 == 0:
                print(f"progress: {n} ({fetched} new this run)", flush=True)
            time.sleep(args.delay)
    print(f"done: {fetched} new hadiths fetched", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
