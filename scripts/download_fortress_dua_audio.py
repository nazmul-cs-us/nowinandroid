#!/usr/bin/env python3
"""
Download per-DUA Fortress-of-the-Muslim recitations and stage them for upload to our CDN.

Fortress duas are shown one card per dua, but the older per-CHAPTER audio (one recitation for the
whole chapter, starting at Dua 1) meant Dua 2/3/... played audio that didn't match their Arabic.
hisnmuslim.com also hosts a distinct clip PER DUA at audio/ar/<duaId>.mp3, mapped by the
wafaaelmaandy JSON (TEXT[].ID). build_fortress_v2.py writes each dua's CDN URL
(audio/fortress/arabic/dua/<duaId>.mp3) into invocations.audio_url; this script downloads the
matching source clip for every dua id our v2 db actually uses.

stdlib only. Run AFTER build_fortress_v2.py (so invocations.audio_url is populated).

Usage:
    python3 scripts/download_fortress_dua_audio.py
"""
import os
import re
import sqlite3
import sys
import time
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
V2_DB = os.path.join(REPO, 'app', 'src', 'main', 'assets', 'databases',
                     'fortress_of_the_muslim_v2.db')
OUT_DIR = os.path.join(REPO, 'app', 'src', 'main', 'assets', 'audio', 'fortress', 'arabic', 'dua')

SRC_FMT = 'https://www.hisnmuslim.com/audio/ar/{}.mp3'
UA = {'User-Agent': 'Mozilla/5.0 (fortress-dua-audio)'}


def dua_ids_from_db():
    """Extract the set of per-dua audio ids our v2 db references (from invocations.audio_url)."""
    if not os.path.exists(V2_DB):
        sys.exit(f'ERROR: {V2_DB} not found. Run scripts/build_fortress_v2.py first.')
    con = sqlite3.connect(V2_DB)
    rows = con.execute(
        "SELECT audio_url FROM invocations WHERE audio_url IS NOT NULL",
    ).fetchall()
    con.close()
    ids = []
    for (url,) in rows:
        m = re.search(r'/dua/(\d+)\.mp3', url)
        if m:
            ids.append(int(m.group(1)))
    return sorted(set(ids))


def download_one(url, dest, retries=3):
    last = None
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
        except Exception as e:  # noqa: BLE001
            last = e
            if attempt < retries:
                time.sleep(0.5 * attempt)
    raise last


def main():
    ids = dua_ids_from_db()
    if not ids:
        sys.exit('No per-dua audio ids found in v2 db (invocations.audio_url empty). '
                 'Rebuild with the updated build_fortress_v2.py first.')
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f'{len(ids)} per-dua clips to fetch (ids {ids[0]}..{ids[-1]})')

    ok = 0
    failed = []
    total = 0
    for dua_id in ids:
        dest = os.path.join(OUT_DIR, f'{dua_id}.mp3')
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            total += os.path.getsize(dest)
            ok += 1
            print(f'  dua {dua_id:3d}  cached')
            continue
        url = SRC_FMT.format(dua_id)
        try:
            n = download_one(url, dest)
            total += n
            ok += 1
            print(f'  dua {dua_id:3d}  {n:>9,} B')
        except Exception as e:  # noqa: BLE001
            failed.append((dua_id, str(e)))
            print(f'  dua {dua_id:3d}  FAILED  {e}')
        time.sleep(0.1)

    print(f'\nDownloaded {ok}/{len(ids)} clips, {total / (1024*1024):.1f} MB total -> {OUT_DIR}')
    if failed:
        print(f'FAILURES ({len(failed)}):')
        for did, err in failed:
            print(f'  dua {did}: {err}')
        sys.exit(1)

    print('\nNext: python3 scripts/generate_news_db.py  (uses invocations.audio_url)')


if __name__ == '__main__':
    main()
