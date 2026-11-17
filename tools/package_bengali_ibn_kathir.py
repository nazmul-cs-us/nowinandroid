#!/usr/bin/env python3
"""Package a verified source SQLite corpus into lossless per-surah app resources."""
import argparse, hashlib, json, sqlite3
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('database', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = args.output
out.mkdir(parents=True, exist_ok=True)
with sqlite3.connect(f'file:{args.database.resolve()}?mode=ro', uri=True) as db:
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    edition = json.loads(db.execute("SELECT value FROM metadata WHERE key='edition'").fetchone()[0])
    assert edition['slug'] == 'bn-tafseer-ibn-e-kaseer' and edition['language_name'] == 'bengali'
    assert edition['author_name'] == 'Tawheed Publication', 'Wrong publisher attribution'
    commit = json.loads(db.execute("SELECT value FROM metadata WHERE key='source_commit'").fetchone()[0])
    assert commit == '311a038bf532fa84566ca1bb2cdbeb210da3d5ed', 'Unreviewed source revision'
    assert db.execute('SELECT COUNT(*), COUNT(DISTINCT surah) FROM tafsir').fetchone() == (6236, 114)
    chapters = db.execute('SELECT surah, ayah_count FROM chapters ORDER BY surah').fetchall()
    manifest = {'edition': 'Bengali Tafsir Ibn Kathir', 'attribution': 'Tawheed Publication',
        'source': 'https://qul.tarteel.ai/resources/tafsir/31',
        'mirror': 'https://github.com/spa5k/tafsir_api',
        'source_commit': '311a038bf532fa84566ca1bb2cdbeb210da3d5ed',
        'quran_com_resource_id': 164, 'qul_resource_id': 31,
        'ayah_count': 6236, 'surah_count': 114, 'files': []}
    for surah, expected in chapters:
        rows = db.execute('SELECT ayah, text FROM tafsir WHERE surah=? ORDER BY ayah', (surah,)).fetchall()
        assert [r[0] for r in rows] == list(range(1, expected + 1))
        passages, lookup, refs = [], {}, []
        for ayah, text in rows:
            assert text.strip(), (surah, ayah)
            if text not in lookup:
                lookup[text] = len(passages)
                passages.append(text)
            refs.append(lookup[text])
        compact = {'surah': surah, 'ayahCount': expected, 'passages': passages, 'ayahPassage': refs}
        raw = json.dumps(compact, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        decoded = json.loads(raw)
        assert [decoded['passages'][n] for n in decoded['ayahPassage']] == [r[1] for r in rows]
        (out / f'{surah}.json').write_bytes(raw)
        manifest['files'].append({'surah': surah, 'ayahs': expected, 'bytes': len(raw),
            'sha256': hashlib.sha256(raw).hexdigest(),
            'text_sha256': hashlib.sha256(json.dumps(rows, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()})
    (out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    (out / 'NOTICE.txt').write_text('''Bengali Tafsir Ibn Kathir — attributed to Tawheed Publication.
Original resource: https://qul.tarteel.ai/resources/tafsir/31
JSON mirror: https://github.com/spa5k/tafsir_api
Pinned commit: 311a038bf532fa84566ca1bb2cdbeb210da3d5ed
Quran.com resource ID 164; QUL resource ID 31.
All 114 surahs / 6,236 ayah entries are preserved. Repeated identical passages are stored once per chapter, with an exact ayah index. Text is not rewritten, translated or corrected. Commentary may cover groups of ayahs and repeat across them in the source.
Attribution is retained; the underlying published text is not designated public domain. See docs/BENGALI_IBN_KATHIR.md for provenance and validation.
''')
    print(f'Packaged {sum(c[1] for c in chapters)} ayahs, {sum(p.stat().st_size for p in out.glob("*.json")):,} bytes')
