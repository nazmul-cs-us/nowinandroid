#!/usr/bin/env python3
"""
Generate news.db from source databases for upload to the CDN.

This is the OFFLINE counterpart to the app's Kotlin generator
(core/contentdatabase/.../NewsDbGenerator.kt -> regenerateWithRoom). The output MUST match
what the app produces at runtime, so that the CDN-downloaded news.db and the app's
runtime-regenerated fallback are equivalent in content.

Sources (all under app/src/main/assets):
  - databases/quran.db                     -> Surahs        (topic 7,  ids 2001+)
  - databases/quranic_duas.db              -> Quranic Duas  (topic 11, ids 101+)
  - databases/fortress_of_the_muslim_v2.db -> Fortress duas (topics 21-37, ids 1001+)
  - sahih_bukhari.json                     -> Bukhari       (topic 8,  ids 3001+)

Run whenever any source is updated, then regenerate the manifest and upload to R2:
  python3 scripts/generate_news_db.py
  python3 scripts/generate_manifest.py --base-url <r2-url>
  ./scripts/upload_to_r2.sh --category news

Note: fortress_of_the_muslim_v2.db is itself a build artifact produced by
scripts/build_fortress_v2.py (needs network). Build it first if missing.
"""

import json
import os
import re
import sqlite3
from datetime import datetime

# Paths
ASSETS_DIR = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')
DB_DIR = os.path.join(ASSETS_DIR, 'databases')
QURAN_DB = os.path.join(DB_DIR, 'quran.db')
FORTRESS_V2_DB = os.path.join(DB_DIR, 'fortress_of_the_muslim_v2.db')
QURANIC_DUAS_DB = os.path.join(DB_DIR, 'quranic_duas.db')
BUKHARI_JSON = os.path.join(ASSETS_DIR, 'sahih_bukhari.json')
NEWS_DB = os.path.join(DB_DIR, 'news.db')

# Header images (match Kotlin generator)
IMG_MAKKAH = 'drawable://masjid_al_haram'
IMG_MADINAH = 'drawable://masjid_al_nawabi'

# Topic IDs
TOPIC_HOLY_QURAN = 7
TOPIC_SAHIH_BUKHARI = 8
TOPIC_QURANIC_DUAS = 11
TOPIC_MORNING_EVENING = 21
TOPIC_PRAYER = 22
TOPIC_HOME_DAILY = 23
TOPIC_FOOD_DRINK = 24
TOPIC_TRAVEL = 25
TOPIC_PROTECTION = 26
TOPIC_DISTRESS_ANXIETY = 27
TOPIC_HEALTH_SICKNESS = 28
TOPIC_SOCIAL_ETIQUETTE = 29
TOPIC_DEATH_FUNERAL = 30
TOPIC_WEATHER_NATURE = 31
TOPIC_HAJJ_UMRAH = 32
TOPIC_FORGIVENESS_REPENTANCE = 33
TOPIC_GUIDANCE_FAITH = 34
TOPIC_REMEMBRANCE_DHIKR = 35
TOPIC_FAMILY_MARRIAGE = 36
TOPIC_SACRIFICE_WORSHIP = 37

# Fortress chapter -> topic mapping (must match NewsDbGenerator.CHAPTER_TO_TOPIC)
CHAPTER_TO_TOPIC = {
    # Morning & Evening (21)
    27: TOPIC_MORNING_EVENING, 28: TOPIC_MORNING_EVENING, 29: TOPIC_MORNING_EVENING,
    30: TOPIC_MORNING_EVENING, 31: TOPIC_MORNING_EVENING,
    # Prayer (22)
    12: TOPIC_PRAYER, 13: TOPIC_PRAYER, 14: TOPIC_PRAYER, 15: TOPIC_PRAYER,
    16: TOPIC_PRAYER, 17: TOPIC_PRAYER, 18: TOPIC_PRAYER, 19: TOPIC_PRAYER,
    20: TOPIC_PRAYER, 21: TOPIC_PRAYER, 22: TOPIC_PRAYER, 23: TOPIC_PRAYER,
    24: TOPIC_PRAYER, 25: TOPIC_PRAYER, 32: TOPIC_PRAYER, 33: TOPIC_PRAYER,
    # Home & Daily (23)
    1: TOPIC_HOME_DAILY, 2: TOPIC_HOME_DAILY, 3: TOPIC_HOME_DAILY, 4: TOPIC_HOME_DAILY,
    5: TOPIC_HOME_DAILY, 6: TOPIC_HOME_DAILY, 7: TOPIC_HOME_DAILY, 8: TOPIC_HOME_DAILY,
    9: TOPIC_HOME_DAILY, 10: TOPIC_HOME_DAILY, 11: TOPIC_HOME_DAILY,
    # Food & Drink (24)
    69: TOPIC_FOOD_DRINK, 70: TOPIC_FOOD_DRINK, 71: TOPIC_FOOD_DRINK,
    72: TOPIC_FOOD_DRINK, 73: TOPIC_FOOD_DRINK,
    # Travel (25)
    95: TOPIC_TRAVEL, 96: TOPIC_TRAVEL, 97: TOPIC_TRAVEL, 98: TOPIC_TRAVEL,
    99: TOPIC_TRAVEL, 100: TOPIC_TRAVEL, 101: TOPIC_TRAVEL, 102: TOPIC_TRAVEL,
    103: TOPIC_TRAVEL, 104: TOPIC_TRAVEL, 105: TOPIC_TRAVEL,
    # Protection (26)
    38: TOPIC_PROTECTION, 39: TOPIC_PROTECTION, 45: TOPIC_PROTECTION,
    88: TOPIC_PROTECTION, 125: TOPIC_PROTECTION, 128: TOPIC_PROTECTION,
    36: TOPIC_PROTECTION, 37: TOPIC_PROTECTION,
    # Distress & Anxiety (27)
    34: TOPIC_DISTRESS_ANXIETY, 35: TOPIC_DISTRESS_ANXIETY, 43: TOPIC_DISTRESS_ANXIETY,
    46: TOPIC_DISTRESS_ANXIETY, 41: TOPIC_DISTRESS_ANXIETY, 82: TOPIC_DISTRESS_ANXIETY,
    83: TOPIC_DISTRESS_ANXIETY, 106: TOPIC_DISTRESS_ANXIETY, 126: TOPIC_DISTRESS_ANXIETY,
    # Health & Sickness (28)
    49: TOPIC_HEALTH_SICKNESS, 50: TOPIC_HEALTH_SICKNESS, 51: TOPIC_HEALTH_SICKNESS,
    124: TOPIC_HEALTH_SICKNESS,
    # Social & Etiquette (29)
    77: TOPIC_SOCIAL_ETIQUETTE, 78: TOPIC_SOCIAL_ETIQUETTE, 84: TOPIC_SOCIAL_ETIQUETTE,
    85: TOPIC_SOCIAL_ETIQUETTE, 86: TOPIC_SOCIAL_ETIQUETTE, 87: TOPIC_SOCIAL_ETIQUETTE,
    107: TOPIC_SOCIAL_ETIQUETTE, 108: TOPIC_SOCIAL_ETIQUETTE, 109: TOPIC_SOCIAL_ETIQUETTE,
    110: TOPIC_SOCIAL_ETIQUETTE, 111: TOPIC_SOCIAL_ETIQUETTE, 112: TOPIC_SOCIAL_ETIQUETTE,
    113: TOPIC_SOCIAL_ETIQUETTE, 114: TOPIC_SOCIAL_ETIQUETTE,
    # Death & Funeral (30)
    52: TOPIC_DEATH_FUNERAL, 53: TOPIC_DEATH_FUNERAL, 54: TOPIC_DEATH_FUNERAL,
    55: TOPIC_DEATH_FUNERAL, 56: TOPIC_DEATH_FUNERAL, 57: TOPIC_DEATH_FUNERAL,
    58: TOPIC_DEATH_FUNERAL, 59: TOPIC_DEATH_FUNERAL, 60: TOPIC_DEATH_FUNERAL,
    # Weather & Nature (31)
    61: TOPIC_WEATHER_NATURE, 62: TOPIC_WEATHER_NATURE, 63: TOPIC_WEATHER_NATURE,
    64: TOPIC_WEATHER_NATURE, 65: TOPIC_WEATHER_NATURE, 66: TOPIC_WEATHER_NATURE,
    67: TOPIC_WEATHER_NATURE, 76: TOPIC_WEATHER_NATURE,
    # Hajj & Umrah (32)
    115: TOPIC_HAJJ_UMRAH, 116: TOPIC_HAJJ_UMRAH, 117: TOPIC_HAJJ_UMRAH,
    118: TOPIC_HAJJ_UMRAH, 119: TOPIC_HAJJ_UMRAH, 120: TOPIC_HAJJ_UMRAH,
    121: TOPIC_HAJJ_UMRAH,
    # Forgiveness & Repentance (33)
    44: TOPIC_FORGIVENESS_REPENTANCE, 129: TOPIC_FORGIVENESS_REPENTANCE,
    # Guidance & Faith (34)
    26: TOPIC_GUIDANCE_FAITH, 40: TOPIC_GUIDANCE_FAITH, 42: TOPIC_GUIDANCE_FAITH,
    # Remembrance & Dhikr (35)
    130: TOPIC_REMEMBRANCE_DHIKR, 131: TOPIC_REMEMBRANCE_DHIKR, 132: TOPIC_REMEMBRANCE_DHIKR,
    # Family & Marriage (36)
    47: TOPIC_FAMILY_MARRIAGE, 48: TOPIC_FAMILY_MARRIAGE, 79: TOPIC_FAMILY_MARRIAGE,
    80: TOPIC_FAMILY_MARRIAGE, 81: TOPIC_FAMILY_MARRIAGE,
    # Sacrifice & Worship (37)
    68: TOPIC_SACRIFICE_WORSHIP, 74: TOPIC_SACRIFICE_WORSHIP, 75: TOPIC_SACRIFICE_WORSHIP,
    127: TOPIC_SACRIFICE_WORSHIP, 122: TOPIC_SACRIFICE_WORSHIP, 123: TOPIC_SACRIFICE_WORSHIP,
    89: TOPIC_SACRIFICE_WORSHIP, 90: TOPIC_SACRIFICE_WORSHIP, 91: TOPIC_SACRIFICE_WORSHIP,
    92: TOPIC_SACRIFICE_WORSHIP, 93: TOPIC_SACRIFICE_WORSHIP, 94: TOPIC_SACRIFICE_WORSHIP,
}


def now_ts():
    """Match Kotlin: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")."""
    return datetime.now().strftime('%Y-%m-%dT%H:%M:%S')


def ordinal(n):
    if 11 <= n <= 13:
        suffix = 'th'
    else:
        suffix = {1: 'st', 2: 'nd', 3: 'rd'}.get(n % 10, 'th')
    return f'{n}{suffix}'


def create_news_db():
    """Create a fresh news.db with the Room-compatible schema."""
    if os.path.exists(NEWS_DB):
        os.remove(NEWS_DB)
    conn = sqlite3.connect(NEWS_DB)
    c = conn.cursor()
    c.execute('''
        CREATE TABLE news_resources (
            id INTEGER NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            content TEXT,
            url TEXT,
            header_image_url TEXT,
            publish_date TEXT,
            type TEXT,
            is_system INTEGER NOT NULL DEFAULT 1,
            is_user_created INTEGER NOT NULL DEFAULT 0,
            source TEXT,
            created_at TEXT,
            updated_at TEXT
        )
    ''')
    c.execute('''
        CREATE TABLE news_topics (
            news_id INTEGER NOT NULL,
            topic_id INTEGER NOT NULL,
            PRIMARY KEY (news_id, topic_id)
        )
    ''')
    c.execute('CREATE TABLE android_metadata (locale TEXT)')
    c.execute("INSERT INTO android_metadata VALUES ('en_US')")
    conn.commit()
    return conn


def insert_news(cursor, news_id, title, content, url, image, type_, source, now):
    cursor.execute('''
        INSERT INTO news_resources
            (id, title, content, url, header_image_url, publish_date, type,
             is_system, is_user_created, source, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, ?)
    ''', (news_id, title, content, url, image, now, type_, source, now, now))


def link_topic(cursor, news_id, topic_id):
    cursor.execute('INSERT OR IGNORE INTO news_topics (news_id, topic_id) VALUES (?, ?)',
                   (news_id, topic_id))


def generate_surahs(conn):
    """Surahs from quran.db, with a FirstAyah preview line (matches Kotlin)."""
    print('Generating Surahs from quran.db...')
    q = sqlite3.connect(QURAN_DB)
    qc = q.cursor()
    qc.execute('SELECT number, name_en, name_en_translation, name_ar, type, total_verses '
               'FROM surahs ORDER BY number')
    surahs = qc.fetchall()
    c = conn.cursor()
    now = now_ts()
    for number, name_en, name_tr, name_ar, stype, total_verses in surahs:
        name_en = name_en or ''
        name_tr = name_tr or ''
        name_ar = name_ar or ''
        stype = stype or 'Meccan'
        news_id = 2001 + number - 1
        image = IMG_MAKKAH if stype == 'Meccan' else IMG_MADINAH if stype == 'Medinan' else IMG_MAKKAH

        # FirstAyah: ayahs 2..6 joined (ayah 1 is usually Bismillah); fall back to ayah 1.
        qc.execute('SELECT text FROM ayahs WHERE surah_number = ? AND number_in_surah >= 2 '
                   'ORDER BY number_in_surah ASC LIMIT 6', (number,))
        parts = [r[0].strip() for r in qc.fetchall() if r[0] and r[0].strip()]
        first_ayah = ' '.join(parts).strip() if parts else None
        if not first_ayah:
            qc.execute('SELECT text FROM ayahs WHERE surah_number = ? '
                       'ORDER BY number_in_surah ASC LIMIT 1', (number,))
            row = qc.fetchone()
            first_ayah = row[0].strip() if row and row[0] and row[0].strip() else None

        title = f'Surah {number}: {name_en} ({name_tr})'
        content = f'**Arabic:** {name_ar}\n\n'
        if first_ayah:
            content += f'**FirstAyah:** {first_ayah}\n\n'
        content += f'**Type:** {stype}\n\n'
        content += f'**Verses:** {total_verses}\n\n'
        content += (f'Read and listen to Surah {name_en}, the {name_tr}. This is the '
                    f'{ordinal(number)} chapter of the Holy Quran with {total_verses} verses.')

        insert_news(c, news_id, title, content, '', image, 'Surah 📖', None, now)
        link_topic(c, news_id, TOPIC_HOLY_QURAN)
    q.close()
    conn.commit()
    print(f'  Generated {len(surahs)} Surahs')
    return len(surahs)


def generate_quranic_duas(conn):
    """Quranic Duas from quranic_duas.db (topic 11)."""
    print('Generating Quranic Duas from quranic_duas.db...')
    if not os.path.exists(QURANIC_DUAS_DB):
        print('  quranic_duas.db not found, skipping...')
        return 0
    d = sqlite3.connect(QURANIC_DUAS_DB)
    dc = d.cursor()
    dc.execute('SELECT dua_number, title, surah_reference, arabic, transliteration, '
               'translation, explanation FROM quranic_duas ORDER BY dua_number')
    rows = dc.fetchall()
    c = conn.cursor()
    now = now_ts()
    for dua_number, title, surah_ref, arabic, translit, translation, explanation in rows:
        news_id = 101 + dua_number - 1
        parts = []
        if arabic:
            parts.append(f'**Arabic:**\n{arabic}')
        if translit:
            parts.append(f'**Transliteration:**\n{translit}')
        if translation:
            parts.append(f'**Translation:**\n{translation}')
        if explanation:
            parts.append(f'**Explanation:**\n{explanation}')
        content = '\n\n'.join(parts)
        full_title = f'Quranic Dua {dua_number}: {title or ""}'
        if surah_ref:
            full_title += f' ({surah_ref})'
        insert_news(c, news_id, full_title, content, '', IMG_MADINAH, 'Dua 🤲', None, now)
        link_topic(c, news_id, TOPIC_QURANIC_DUAS)
    d.close()
    conn.commit()
    print(f'  Generated {len(rows)} Quranic Duas')
    return len(rows)


def generate_fortress_duas(conn):
    """Fortress duas from fortress_of_the_muslim_v2.db (topics 21-37, ids 1001+)."""
    print('Generating Fortress of the Muslim duas (v2)...')
    if not os.path.exists(FORTRESS_V2_DB):
        raise FileNotFoundError(
            f'{FORTRESS_V2_DB} not found. Build it first: python3 scripts/build_fortress_v2.py')
    f = sqlite3.connect(FORTRESS_V2_DB)
    fc = f.cursor()
    fc.execute('''
        SELECT c.id, c.title, i.id, i.position, i.arabic, i.transliteration,
               i.translation, i.context, i.instruction, i.note, i.post_context,
               (SELECT h.reference_str FROM hadith_references h
                  WHERE h.invocation_id = i.id LIMIT 1) AS reference,
               c.audio_url
        FROM chapters c
        JOIN invocations i ON c.id = i.chapter_id
        ORDER BY c.id, i.position
    ''')
    rows = fc.fetchall()
    c = conn.cursor()
    now = now_ts()
    news_id = 1001
    count = 0
    for (chapter_id, chapter_title, _inv_id, position, arabic, translit, translation,
         context, instruction, note, post_context, reference, audio_url) in rows:
        parts = []
        if context:
            parts.append(f'**Context:**\n{context}')
        if arabic:
            parts.append(f'**Arabic:**\n{arabic}')
        if translit:
            parts.append(f'**Transliteration:**\n{translit}')
        if translation:
            parts.append(f'**Translation:**\n{translation}')
        if instruction:
            parts.append(f'**Instruction:**\n{instruction}')
        if note:
            parts.append(f'**Note:**\n{note}')
        if reference:
            parts.append(f'**Reference:**\n{reference}')
        if post_context:
            parts.append(f'**Additional Context:**\n{post_context}')
        # Hidden marker consumed by the news card to show a play button.
        if audio_url:
            parts.append(f'**Audio:**\n{audio_url}')
        content = '\n\n'.join(parts)
        title = f'{chapter_title or ""}: Dua {position}'
        insert_news(c, news_id, title, content, '', IMG_MADINAH, 'Dua 🤲', None, now)
        topic_id = CHAPTER_TO_TOPIC.get(chapter_id)
        if topic_id:
            link_topic(c, news_id, topic_id)
        news_id += 1
        count += 1
    f.close()
    conn.commit()
    print(f'  Generated {count} Fortress duas')
    return count


def generate_bukhari(conn):
    """Sahih Bukhari hadiths from sahih_bukhari.json (topic 8, ids 3001+)."""
    print('Generating Sahih Bukhari hadiths from JSON...')
    if not os.path.exists(BUKHARI_JSON):
        print('  sahih_bukhari.json not found, skipping...')
        return 0
    with open(BUKHARI_JSON, 'r', encoding='utf-8') as fh:
        data = json.load(fh)
    c = conn.cursor()
    now = now_ts()
    news_id = 3001
    count = 0
    num_re = re.compile(r'Number\s*(\d+)', re.IGNORECASE)
    for volume in data:
        for book in volume.get('books', []):
            book_name = book.get('name', '')
            for hadith in book.get('hadiths', []):
                info = hadith.get('info', '')
                narrator = hadith.get('by', '') or ''
                text = (hadith.get('text', '') or '').strip()
                m = num_re.search(info)
                if not m:
                    continue
                hadith_number = int(m.group(1))
                title = f'Hadith {hadith_number} - {book_name}'
                content = f'{narrator}\n\n{text}' if narrator else text
                url = f'hadith://sahih_bukhari/{hadith_number}'
                insert_news(c, news_id, title, content, url, IMG_MADINAH,
                            'Hadith 📖', 'Sahih Bukhari', now)
                link_topic(c, news_id, TOPIC_SAHIH_BUKHARI)
                news_id += 1
                count += 1
    conn.commit()
    print(f'  Generated {count} Bukhari hadiths')
    return count


def main():
    print('=' * 60)
    print('Generating news.db from source databases')
    print('=' * 60)

    conn = create_news_db()
    surahs = generate_surahs(conn)
    duas = generate_quranic_duas(conn)
    fortress = generate_fortress_duas(conn)
    bukhari = generate_bukhari(conn)

    c = conn.cursor()
    c.execute('SELECT COUNT(*) FROM news_resources')
    total_news = c.fetchone()[0]
    c.execute('SELECT COUNT(*) FROM news_topics')
    total_mappings = c.fetchone()[0]
    c.execute('SELECT COUNT(DISTINCT topic_id) FROM news_topics')
    total_topics = c.fetchone()[0]
    conn.close()

    print('=' * 60)
    print('Generated news.db successfully!')
    print(f'  Surahs: {surahs}  Quranic Duas: {duas}  Fortress: {fortress}  Bukhari: {bukhari}')
    print(f'  Total news resources: {total_news}')
    print(f'  Total topic mappings: {total_mappings}')
    print(f'  Topics with content:  {total_topics}')
    print(f'  Output: {NEWS_DB}')
    print('=' * 60)


if __name__ == '__main__':
    main()
