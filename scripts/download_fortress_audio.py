#!/usr/bin/env python3
"""
Mirror Fortress-of-the-Muslim chapter audio from hisnmuslim.com to our own CDN.

Why: fortress_of_the_muslim_v2.db (chapters.audio_url) currently points at
hisnmuslim.com, an external host we don't control. This script downloads each chapter's
MP3 locally (keyed by chapter id, decoupled from hisnmuslim's off-by-one numbering) and
rewrites chapters.audio_url to our R2 CDN URL. After running, regenerate news.db
(scripts/generate_news_db.py) and upload audio + news.db + manifest to R2.

CDN layout mirrors the existing quran audio convention (audio/quran/arabic/...):
    audio/fortress/arabic/<chapterId:03d>.mp3

stdlib only (urllib + sqlite3).

Usage:
    python3 scripts/download_fortress_audio.py                 # download + rewrite db
    python3 scripts/download_fortress_audio.py --no-rewrite    # download only
    python3 scripts/download_fortress_audio.py --rewrite-only  # rewrite db, skip download
"""
import argparse
import os
import sqlite3
import sys
import time
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
V2_DB = os.path.join(REPO, 'app', 'src', 'main', 'assets', 'databases',
                     'fortress_of_the_muslim_v2.db')
AUDIO_DIR = os.path.join(REPO, 'app', 'src', 'main', 'assets', 'audio', 'fortress', 'arabic')

CDN_BASE = 'https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev'
CDN_PREFIX = 'audio/fortress/arabic'

UA = {'User-Agent': 'Mozilla/5.0 (fortress-audio-mirror)'}


def cdn_url(chapter_id):
    return f'{CDN_BASE}/{CDN_PREFIX}/{chapter_id:03d}.mp3'


def local_path(chapter_id):
    return os.path.join(AUDIO_DIR, f'{chapter_id:03d}.mp3')


def read_chapters():
    """Return [(chapter_id, source_url)] for chapters that have audio, from the v2 db."""
    if not os.path.exists(V2_DB):
        sys.exit(f'ERROR: {V2_DB} not found. Build it first: python3 scripts/build_fortress_v2.py')
    con = sqlite3.connect(V2_DB)
    rows = con.execute(
        'SELECT id, audio_url FROM chapters WHERE audio_url IS NOT NULL ORDER BY id'
    ).fetchall()
    con.close()
    return rows


def download_one(url, dest, retries=3):
    """Download url -> dest with simple backoff. Returns bytes written, or raises."""
    last_err = None
    for attempt in range(1, retries + 1):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            if not data:
                raise IOError('empty response')
            tmp = dest + '.part'
            with open(tmp, 'wb') as f:
                f.write(data)
            os.replace(tmp, dest)
            return len(data)
        except Exception as e:  # noqa: BLE001 - report and retry
            last_err = e
            if attempt < retries:
                time.sleep(0.5 * attempt)
    raise last_err


def do_download(chapters):
    os.makedirs(AUDIO_DIR, exist_ok=True)
    ok = 0
    failed = []
    total_bytes = 0
    for chapter_id, source_url in chapters:
        dest = local_path(chapter_id)
        # Skip if already present and non-empty (idempotent re-runs).
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            total_bytes += os.path.getsize(dest)
            ok += 1
            print(f'  ch {chapter_id:3d}  cached  {os.path.basename(dest)}')
            continue
        try:
            n = download_one(source_url, dest)
            total_bytes += n
            ok += 1
            print(f'  ch {chapter_id:3d}  {n:>9,} B  <- {source_url}')
        except Exception as e:  # noqa: BLE001
            failed.append((chapter_id, source_url, str(e)))
            print(f'  ch {chapter_id:3d}  FAILED  {e}  ({source_url})')
        time.sleep(0.1)
    print(f'\nDownloaded {ok}/{len(chapters)} files, {total_bytes / (1024*1024):.1f} MB total')
    if failed:
        print(f'FAILURES ({len(failed)}):')
        for cid, url, err in failed:
            print(f'  ch {cid}: {err}  {url}')
    return not failed


def do_rewrite(chapters):
    """Point chapters.audio_url at the CDN (idempotent)."""
    con = sqlite3.connect(V2_DB)
    cur = con.cursor()
    for chapter_id, _src in chapters:
        cur.execute('UPDATE chapters SET audio_url = ? WHERE id = ?',
                    (cdn_url(chapter_id), chapter_id))
    con.commit()
    con.close()
    print(f'Rewrote audio_url -> CDN for {len(chapters)} chapters in {os.path.basename(V2_DB)}')


def main():
    ap = argparse.ArgumentParser(description='Mirror Fortress audio to CDN')
    ap.add_argument('--no-rewrite', action='store_true', help='download only, keep source URLs')
    ap.add_argument('--rewrite-only', action='store_true', help='rewrite db only, skip download')
    args = ap.parse_args()

    chapters = read_chapters()
    print(f'{len(chapters)} chapters with audio (ids {chapters[0][0]}..{chapters[-1][0]})')

    all_ok = True
    if not args.rewrite_only:
        all_ok = do_download(chapters)

    if not args.no_rewrite:
        if not all_ok and not args.rewrite_only:
            print('\nRefusing to rewrite audio_url because some downloads FAILED.')
            print('Fix the failures (or re-run) before rewriting. Use --rewrite-only to force.')
            sys.exit(1)
        do_rewrite(chapters)

    print('\nNext steps:')
    print('  python3 scripts/generate_news_db.py           # bake CDN URLs into news.db')
    print('  # then patch scripts/manifest.json and upload audio + news.db + manifest to R2')


if __name__ == '__main__':
    main()
