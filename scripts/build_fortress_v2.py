#!/usr/bin/env python3
"""
Build fortress_of_the_muslim_v2.db from islamawareness.net (clean source) + per-chapter
audio from hisnmuslim.com.

Fixes the original parse's problems: mid-sentence line breaks, references dumped into the
`note` field, and missing field separation. Schema matches fortress_of_the_muslim.db, plus
an `audio_url` column on `chapters`.

stdlib only (urllib + html.parser).
"""
import html
import json
import re
import sqlite3
import sys
import time
import urllib.request
from html.parser import HTMLParser

BASE = "https://www.islamawareness.net/Dua/Fortress/"
# Chapter audio is mirrored to our own R2 CDN (keyed by chapter id), not streamed from
# hisnmuslim.com directly. The MP3s are fetched + uploaded by scripts/download_fortress_audio.py;
# here we just emit the resulting CDN URL so a rebuilt v2 db already points at the CDN.
AUDIO_CDN_FMT = "https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev/audio/fortress/arabic/{:03d}.mp3"
# Per-dua recitation: each individual dua has its own clip (keyed by the wafaaelmaandy dua id),
# so a dua card plays audio matching its own Arabic (not the whole-chapter recitation).
AUDIO_DUA_CDN_FMT = "https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev/audio/fortress/arabic/dua/{}.mp3"
WAF_URL = "https://raw.githubusercontent.com/wafaaelmaandy/Hisn-Muslim-Json/master/husn_en.json"
UA = {"User-Agent": "Mozilla/5.0 (fortress-parser)"}
N_CHAPTERS = 132


def load_waf():
    """{chapter_id: [(arabic, dua_audio_id) per dua]} from wafaaelmaandy's JSON.
    Used to (a) fill the Arabic that islamawareness omits and (b) map each dua to its own
    per-dua recitation id (TEXT[].ID → hisnmuslim.com/audio/ar/<id>.mp3)."""
    raw = fetch(WAF_URL, encoding="utf-8").lstrip("﻿")
    data = json.loads(raw)["English"]
    out = {}
    for ch in data:
        out[ch["ID"]] = [
            (collapse(t.get("ARABIC_TEXT", "")), t.get("ID"))
            for t in ch.get("TEXT", [])
        ]
    return out


def fetch(url, encoding=None):
    # islamawareness.net declares no charset and is MIXED-encoding: 125 of the 132 Fortress
    # pages are genuine UTF-8 (Arabic), while 7 are Windows-1252 carrying "smart punctuation"
    # — 0x92 ' , 0x93/0x94 " " , 0x96/0x97 – — , 0x85 … — which is invalid UTF-8.
    # Neither codec alone works: decoding everything as UTF-8 with errors="replace" turns the
    # cp1252 punctuation into U+FFFD (�) and corrupts titles/translations, while decoding
    # everything as cp1252 raises on the UTF-8 pages (byte 0x8f is undefined in cp1252) and
    # silently drops those chapters. So sniff: valid UTF-8 wins, otherwise fall back to cp1252
    # (a single-byte codec, so it cannot fail on the remaining pages). Callers that already
    # know the encoding — e.g. the wafaaelmaandy JSON — can pass it explicitly.
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read()
    if encoding:
        return raw.decode(encoding)
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode("cp1252")


def parse_index():
    """Return {chapter_num: title} from the index page."""
    htmltxt = fetch(BASE)
    titles = {}
    for m in re.finditer(r'href="0*?(\d+)\.html"[^>]*>(.*?)</a>', htmltxt, re.I | re.S):
        num = int(m.group(1))
        title = html.unescape(re.sub(r"<[^>]+>", "", m.group(2))).strip()
        if 1 <= num <= N_CHAPTERS and title:
            titles[num] = title
    return titles


class ChapterParser(HTMLParser):
    """Streams the messy Word-exported HTML into duas.

    Markers: Arabic = <font size=6>; transliteration = <font face=algerian>;
    translation = <font face="book antiqua">; a dua starts at "<p>N.".
    The Arabic block precedes its number, so we buffer it until the number appears.
    'Reference:' (plain text) starts the reference, captured until the next dua.
    """

    def __init__(self):
        super().__init__()
        self.duas = []
        self.cur = None            # current dua dict
        self.pending_arabic = []   # arabic seen before the next number
        self.face = None
        self.size = None
        self.ref_mode = False

    def _flush(self):
        if self.cur:
            for k in ("arabic", "transliteration", "translation", "reference"):
                self.cur[k] = collapse(self.cur[k])
            # Arabic: strip the source's surrounding ASCII quotes/period artifacts.
            self.cur["arabic"] = re.sub(r'^[\s"“”.]+|[\s"“”.]+$', "", self.cur["arabic"])
            # Reference: the page footer (ad script + nav links) follows the citation
            # as plain text and gets swept up. Truncate at the first junk boundary.
            self.cur["reference"] = strip_ref_junk(self.cur["reference"])
            self.duas.append(self.cur)
            self.cur = None

    def handle_starttag(self, tag, attrs):
        if tag == "font":
            a = {k.strip().lower(): (v or "").strip().lower() for k, v in attrs}
            self.size = a.get("size")
            self.face = a.get("face")

    def handle_endtag(self, tag):
        if tag == "font":
            self.size = None
            self.face = None

    def handle_data(self, data):
        text = data.replace("\xa0", " ")
        if not text.strip():
            return
        # Arabic is detected by Arabic-script characters (robust to varying font markup),
        # and always precedes its number, so it accumulates in pending_arabic.
        if re.search(r"[؀-ۿ]", text):
            self.pending_arabic.append(text)
            return
        # New dua marker: "N." at the start of a (non-Arabic) chunk.
        m = re.match(r"\s*(\d{1,3})\.\s*(.*)$", text, re.S)
        if m:
            self._flush()
            self.cur = {"arabic": "".join(self.pending_arabic),
                        "transliteration": "", "translation": "", "reference": ""}
            self.pending_arabic = []
            self.ref_mode = False
            text = m.group(2)
            if not text.strip():
                return
        if self.face == "algerian":    # transliteration
            self.ref_mode = False
            if self.cur:
                self.cur["transliteration"] += text
            return
        if self.face == "book antiqua":  # translation
            self.ref_mode = False
            if self.cur:
                self.cur["translation"] += text
            return
        # Plain text: handle "Reference:" capture.
        rm = re.search(r"Reference\s*:\s*(.*)$", text, re.S | re.I)
        if rm:
            self.ref_mode = True
            if self.cur:
                self.cur["reference"] += rm.group(1)
            return
        if self.ref_mode and self.cur:
            self.cur["reference"] += " " + text

    def close(self):
        super().close()
        self._flush()


def collapse(s):
    """Join wrapped lines into flowing text; trim stray quotes/space."""
    s = html.unescape(s or "")
    s = s.replace("\r", " ").replace("\n", " ")
    s = re.sub(r"\s+", " ", s).strip()
    s = s.strip('"“”').strip()
    return s


# islamawareness.net appends its page footer (an adsbygoogle script and the
# "Back Back To Islam Awareness Homepage ..." nav block) right after each
# citation. Cut the reference at whichever of these boundaries comes first.
REF_JUNK_MARKERS = (
    "(adsbygoogle", "adsbygoogle", "window.adsbygoogle", "<!--", "google_ad",
    "google_color", "Back Back To", "Back To Islam", "Islam Awareness Homepage",
    "Latest News about", "IslamAwareness@",
)


def strip_ref_junk(s):
    if not s:
        return s
    cut = len(s)
    for m in REF_JUNK_MARKERS:
        i = s.find(m)
        if i != -1:
            cut = min(cut, i)
    return s[:cut].rstrip().rstrip("(").rstrip()


def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else N_CHAPTERS
    titles = parse_index()
    print(f"index: {len(titles)} chapter titles")

    all_chapters = []  # (num, title, audio_url, [duas])
    for num in range(1, limit + 1):
        url = f"{BASE}{num:03d}.html"
        try:
            raw = fetch(url)
        except Exception as e:
            print(f"  ! chapter {num} fetch failed: {e}")
            all_chapters.append((num, titles.get(num, f"Chapter {num}"), None, []))
            continue
        p = ChapterParser()
        p.feed(raw)
        p.close()
        duas = [d for d in p.duas if d["arabic"] or d["translation"]]
        audio = AUDIO_CDN_FMT.format(num)  # CDN file is keyed by chapter id
        all_chapters.append((num, titles.get(num, f"Chapter {num}"), audio, duas))
        print(f"  ch {num:3d} [{titles.get(num,'?')[:34]:34}] duas={len(duas)}")
        time.sleep(0.15)

    # From wafaaelmaandy: (a) fill missing Arabic (only when counts match, safe), and
    # (b) assign each dua its own per-dua audio id by position. zip() maps our leading duas to
    # waf's leading duas in order; when waf has extras (4 chapters) they're simply unused, so
    # every one of our duas still gets a correct, in-order per-dua clip.
    filled = 0
    audio_mapped = 0
    try:
        waf = load_waf()
        for num, title, audio, duas in all_chapters:
            war = waf.get(num, [])
            counts_match = len(war) == len(duas)
            for d, (ar, dua_id) in zip(duas, war):
                if counts_match and not d["arabic"] and ar:
                    d["arabic"] = ar
                    filled += 1
                if dua_id is not None:
                    d["audio_dua_id"] = dua_id
                    audio_mapped += 1
        print(f"filled {filled} missing Arabic from wafaaelmaandy")
        print(f"mapped {audio_mapped} per-dua audio ids from wafaaelmaandy")
    except Exception as e:
        print(f"  ! wafaaelmaandy load skipped: {e}")

    if limit < N_CHAPTERS:
        for num, title, audio, duas in all_chapters:
            for i, d in enumerate(duas, 1):
                print(f"\n--- ch{num} dua{i} ---")
                print("AR :", d["arabic"][:80])
                print("TR :", d["transliteration"][:80])
                print("EN :", d["translation"][:80])
                print("REF:", d["reference"][:80])
        return

    build_db(all_chapters)


def build_db(all_chapters):
    out = "app/src/main/assets/databases/fortress_of_the_muslim_v2.db"
    import os
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    c = db.cursor()
    # Schema replicates fortress_of_the_muslim.db exactly (so Room accepts the asset),
    # plus an audio_url column on chapters.
    c.executescript("""
        CREATE TABLE metadata (
            id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, subtitle TEXT, publisher TEXT, source_ids TEXT);
        CREATE TABLE chapters (
            id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, audio_url TEXT);
        CREATE TABLE invocations (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, chapter_id INTEGER NOT NULL, position INTEGER NOT NULL,
            arabic TEXT, transliteration TEXT, translation TEXT, context TEXT, instruction TEXT, note TEXT,
            post_context TEXT, description TEXT, source_ids TEXT, audio_url TEXT,
            FOREIGN KEY (chapter_id) REFERENCES chapters(id));
        CREATE TABLE footnotes (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, chapter_id INTEGER NOT NULL, term TEXT, definition TEXT,
            note TEXT, source_ids TEXT, FOREIGN KEY (chapter_id) REFERENCES chapters(id));
        CREATE INDEX idx_invocations_chapter ON invocations(chapter_id);
        CREATE INDEX idx_footnotes_chapter ON footnotes(chapter_id);
        CREATE TABLE IF NOT EXISTS "hadith_references" (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, invocation_id INTEGER NOT NULL, collection_id INTEGER,
            collection_name TEXT, hadith_number INTEGER, reference_str TEXT, database_file TEXT);
    """)
    c.execute("INSERT INTO metadata VALUES (1, ?, ?, ?, ?)",
              ("Fortress of the Muslim", "Hisn al-Muslim", "islamawareness.net", None))
    total_duas = 0
    for num, title, audio, duas in all_chapters:
        c.execute("INSERT INTO chapters (id, title, audio_url) VALUES (?, ?, ?)", (num, title, audio))
        for pos, d in enumerate(duas, 1):
            dua_audio = None
            dua_id = d.get("audio_dua_id")
            if dua_id is not None:
                dua_audio = AUDIO_DUA_CDN_FMT.format(dua_id)
            c.execute("""INSERT INTO invocations (chapter_id, position, arabic, transliteration, translation,
                         context, instruction, note, post_context, description, source_ids, audio_url)
                         VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, ?)""",
                      (num, pos, d["arabic"] or None, d["transliteration"] or None, d["translation"] or None,
                       dua_audio))
            inv_id = c.lastrowid
            if d["reference"]:
                c.execute("INSERT INTO hadith_references (invocation_id, reference_str) VALUES (?, ?)",
                          (inv_id, d["reference"]))
            total_duas += 1
    db.commit()
    db.close()
    print(f"\nBuilt {out}: {len(all_chapters)} chapters, {total_duas} duas")


if __name__ == "__main__":
    main()
