#!/usr/bin/env python3
"""
Generate news entries for all 114 Surahs
"""

import json
import sqlite3
from datetime import datetime, timedelta

def get_ayah_count(cursor, surah_id):
    """Get the number of ayahs in a surah"""
    cursor.execute("SELECT COUNT(*) FROM ayahs WHERE surah_id = ?", (surah_id,))
    return cursor.fetchone()[0]

def get_first_ayah(cursor, surah_id):
    """Get the first ayah of a surah"""
    cursor.execute("SELECT text FROM ayahs WHERE surah_id = ? ORDER BY number_in_surah LIMIT 1", (surah_id,))
    result = cursor.fetchone()
    return result[0] if result else ""

def generate_quran_news():
    """Generate news entries for all Surahs"""
    
    # Connect to the database
    db_path = "app/src/main/assets/databases/quran.db"
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Get all surahs
    cursor.execute("SELECT id, number, name_en, name_ar, name_en_translation, type FROM surahs ORDER BY number")
    surahs = cursor.fetchall()
    
    # Read existing news
    with open("core/network/src/main/assets/news.json", "r", encoding="utf-8") as f:
        existing_news = json.load(f)
    
    # Get the highest existing ID
    max_id = max([int(news["id"]) for news in existing_news])
    
    # Generate news entries
    news_entries = []
    base_date = datetime(2024, 1, 1, 6, 0, 0)  # Start from Jan 1, 2024
    
    for idx, surah in enumerate(surahs):
        surah_id, number, name_en, name_ar, translation, revelation_type = surah
        ayah_count = get_ayah_count(cursor, surah_id)
        first_ayah = get_first_ayah(cursor, surah_id)
        
        # Calculate publish date (one per day)
        publish_date = base_date + timedelta(days=idx)
        
        # Determine emoji based on revelation type
        emoji = "🕌" if revelation_type == "Meccan" else "🌙"
        
        # Create news entry
        news_entry = {
            "id": str(max_id + idx + 1),
            "title": f"Surah {number}: {name_en} ({name_ar}) {emoji}",
            "content": f"Surah {name_en} ({translation}) is a {revelation_type} surah consisting of {ayah_count} verses. "
                      f"This chapter reveals divine guidance and wisdom. "
                      f"{'It was revealed in Mecca before the migration to Medina, focusing on fundamental Islamic beliefs and moral teachings.' if revelation_type == 'Meccan' else 'It was revealed in Medina after the migration, often addressing legal matters, social issues, and community building.'} "
                      f"\\n\\nReflect on its teachings and recite with understanding. May Allah guide us through His words.",
            "url": f"https://quran.com/{number}",
            "headerImageUrl": f"https://via.placeholder.com/600x300/1976D2/FFFFFF?text=Surah+{number}:+{name_en.replace(' ', '+')}",
            "publishDate": publish_date.strftime("%Y-%m-%dT%H:%M:%S.000Z"),
            "type": "Quran 📖",
            "topics": ["7"],  # Topic ID 7 is "Holy Quran"
            "authors": ["1"]
        }
        
        news_entries.append(news_entry)
        
        # Print progress
        if (idx + 1) % 10 == 0:
            print(f"✅ Generated {idx + 1}/114 Surah news entries...")
    
    conn.close()
    
    # Combine existing news with new quran entries
    all_news = existing_news + news_entries
    
    # Write to news.json
    with open("core/network/src/main/assets/news.json", "w", encoding="utf-8") as f:
        json.dump(all_news, f, ensure_ascii=False, indent=2)
    
    print(f"\n🎉 Successfully generated {len(news_entries)} Surah news entries!")
    print(f"📊 Total news entries: {len(all_news)}")
    print(f"📁 Updated: core/network/src/main/assets/news.json")
    
    return len(news_entries)

if __name__ == "__main__":
    print("=" * 60)
    print("📖 Generating Quran Surah News Entries")
    print("=" * 60)
    print()
    
    count = generate_quran_news()
    
    print()
    print("=" * 60)
    print("✅ COMPLETE!")
    print("=" * 60)
    print()
    print("🕌 All 114 Surahs have been added as news items under")
    print("   the 'Holy Quran' topic!")
    print()
    print("📱 Users can now browse all Surahs in the For You feed!")
    print()

