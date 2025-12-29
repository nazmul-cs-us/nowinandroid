#!/usr/bin/env python3
"""
Generate news.db from source databases (quran.db, fortress_of_the_muslim.db, quranic_duas.db)
This script creates all news_resources and news_topics mappings dynamically.

Run this script whenever source databases are updated to regenerate news.db.
"""

import sqlite3
import os
from datetime import datetime

# Paths
ASSETS_DIR = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'databases')
QURAN_DB = os.path.join(ASSETS_DIR, 'quran.db')
FORTRESS_DB = os.path.join(ASSETS_DIR, 'fortress_of_the_muslim.db')
QURANIC_DUAS_DB = os.path.join(ASSETS_DIR, 'quranic_duas.db')
NEWS_DB = os.path.join(ASSETS_DIR, 'news.db')

# Topic IDs
TOPIC_HOLY_QURAN = 7
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

# Fortress chapter to topic mapping
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


def create_news_db():
    """Create fresh news.db with schema"""
    if os.path.exists(NEWS_DB):
        os.remove(NEWS_DB)

    conn = sqlite3.connect(NEWS_DB)
    cursor = conn.cursor()

    # Create tables
    cursor.execute('''
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

    cursor.execute('''
        CREATE TABLE news_topics (
            news_id INTEGER NOT NULL,
            topic_id INTEGER NOT NULL,
            PRIMARY KEY (news_id, topic_id)
        )
    ''')

    cursor.execute('''
        CREATE TABLE android_metadata (locale TEXT)
    ''')
    cursor.execute("INSERT INTO android_metadata VALUES ('en_US')")

    conn.commit()
    return conn


def generate_surahs(conn, start_id=2001):
    """Generate Surah news resources from quran.db"""
    print("Generating Surahs from quran.db...")

    quran_conn = sqlite3.connect(QURAN_DB)
    quran_cursor = quran_conn.cursor()

    quran_cursor.execute("""
        SELECT number, name_en, name_en_translation, name_ar, type, total_verses
        FROM surahs ORDER BY number
    """)
    surahs = quran_cursor.fetchall()

    cursor = conn.cursor()
    now = datetime.now().isoformat()

    for surah in surahs:
        number, name_en, name_translation, name_ar, surah_type, total_verses = surah
        news_id = start_id + number - 1

        ordinal = lambda n: "%d%s" % (n, {1:"st", 2:"nd", 3:"rd"}.get(n if n < 20 else n % 10, "th"))

        title = f"Surah {number}: {name_en} ({name_translation})"
        content = f"""**Arabic:** {name_ar}

**Type:** {surah_type}

**Verses:** {total_verses}

Read and listen to Surah {name_en}, the {name_translation}. This is the {ordinal(number)} chapter of the Holy Quran with {total_verses} verses."""

        cursor.execute("""
            INSERT INTO news_resources (id, title, content, type, is_system, created_at, updated_at)
            VALUES (?, ?, ?, 'Surah 📖', 1, ?, ?)
        """, (news_id, title, content, now, now))

        cursor.execute("INSERT INTO news_topics (news_id, topic_id) VALUES (?, ?)",
                      (news_id, TOPIC_HOLY_QURAN))

    quran_conn.close()
    conn.commit()
    print(f"  Generated {len(surahs)} Surahs")
    return start_id + len(surahs)


def generate_quranic_duas(conn, start_id=101):
    """Generate Quranic Duas from quranic_duas.db"""
    print("Generating Quranic Duas from quranic_duas.db...")

    if not os.path.exists(QURANIC_DUAS_DB):
        print("  quranic_duas.db not found, skipping...")
        return start_id

    duas_conn = sqlite3.connect(QURANIC_DUAS_DB)
    duas_cursor = duas_conn.cursor()

    duas_cursor.execute("""
        SELECT dua_number, title, surah_reference, arabic, transliteration, translation, explanation
        FROM quranic_duas ORDER BY dua_number
    """)
    duas = duas_cursor.fetchall()

    cursor = conn.cursor()
    now = datetime.now().isoformat()

    for dua in duas:
        dua_number, title, surah_ref, arabic, transliteration, translation, explanation = dua
        news_id = start_id + dua_number - 1

        content_parts = []
        if arabic:
            content_parts.append(f"**Arabic:**\n{arabic}")
        if transliteration:
            content_parts.append(f"**Transliteration:**\n{transliteration}")
        if translation:
            content_parts.append(f"**Translation:**\n{translation}")
        if explanation:
            content_parts.append(f"**Explanation:**\n{explanation}")

        content = "\n\n".join(content_parts)
        full_title = f"Quranic Dua {dua_number}: {title}"
        if surah_ref:
            full_title += f" ({surah_ref})"

        cursor.execute("""
            INSERT INTO news_resources (id, title, content, type, is_system, created_at, updated_at)
            VALUES (?, ?, ?, 'Dua 🤲', 1, ?, ?)
        """, (news_id, full_title, content, now, now))

        cursor.execute("INSERT INTO news_topics (news_id, topic_id) VALUES (?, ?)",
                      (news_id, TOPIC_QURANIC_DUAS))

    duas_conn.close()
    conn.commit()
    print(f"  Generated {len(duas)} Quranic Duas")
    return start_id + len(duas)


def generate_fortress_duas(conn, start_id=1001):
    """Generate Fortress of the Muslim duas"""
    print("Generating Fortress of the Muslim duas...")

    if not os.path.exists(FORTRESS_DB):
        print("  fortress_of_the_muslim.db not found, skipping...")
        return start_id

    fortress_conn = sqlite3.connect(FORTRESS_DB)
    fortress_cursor = fortress_conn.cursor()

    # Get chapters with invocations
    fortress_cursor.execute("""
        SELECT c.id, c.title, i.id, i.position, i.arabic, i.transliteration,
               i.translation, i.context, i.instruction, i.note, i.post_context
        FROM chapters c
        JOIN invocations i ON c.id = i.chapter_id
        ORDER BY c.id, i.position
    """)
    invocations = fortress_cursor.fetchall()

    cursor = conn.cursor()
    now = datetime.now().isoformat()
    news_id = start_id

    for inv in invocations:
        chapter_id, chapter_title, inv_id, position, arabic, transliteration, translation, context, instruction, note, post_context = inv

        # Build content
        content_parts = []
        if context:
            content_parts.append(f"**Context:**\n{context}")
        if arabic:
            content_parts.append(f"**Arabic:**\n{arabic}")
        if transliteration:
            content_parts.append(f"**Transliteration:**\n{transliteration}")
        if translation:
            content_parts.append(f"**Translation:**\n{translation}")
        if instruction:
            content_parts.append(f"**Instruction:**\n{instruction}")
        if note:
            content_parts.append(f"**Note:**\n{note}")
        if post_context:
            content_parts.append(f"**Additional Context:**\n{post_context}")

        content = "\n\n".join(content_parts)
        title = f"{chapter_title}: Dua {position}"

        cursor.execute("""
            INSERT INTO news_resources (id, title, content, type, is_system, created_at, updated_at)
            VALUES (?, ?, ?, 'Dua 🤲', 1, ?, ?)
        """, (news_id, title, content, now, now))

        # Map to topic based on chapter
        topic_id = CHAPTER_TO_TOPIC.get(chapter_id)
        if topic_id:
            cursor.execute("INSERT INTO news_topics (news_id, topic_id) VALUES (?, ?)",
                          (news_id, topic_id))

        news_id += 1

    fortress_conn.close()
    conn.commit()
    print(f"  Generated {len(invocations)} Fortress duas")
    return news_id


def main():
    print("=" * 60)
    print("Generating news.db from source databases")
    print("=" * 60)

    # Create fresh database
    conn = create_news_db()

    # Generate content from each source
    next_id = generate_quranic_duas(conn, start_id=101)
    next_id = generate_fortress_duas(conn, start_id=1001)
    next_id = generate_surahs(conn, start_id=2001)

    # Get stats
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM news_resources")
    total_news = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM news_topics")
    total_mappings = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(DISTINCT topic_id) FROM news_topics")
    total_topics = cursor.fetchone()[0]

    conn.close()

    print("=" * 60)
    print(f"Generated news.db successfully!")
    print(f"  Total news resources: {total_news}")
    print(f"  Total topic mappings: {total_mappings}")
    print(f"  Topics with content: {total_topics}")
    print("=" * 60)


if __name__ == "__main__":
    main()
