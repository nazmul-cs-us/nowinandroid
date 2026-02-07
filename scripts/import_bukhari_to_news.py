#!/usr/bin/env python3
"""
Import Sahih Bukhari hadiths from JSON into news.db database.
Links all hadiths to the Sahih Bukhari topic (ID 8).
"""

import json
import sqlite3
import re
import os

# Paths
JSON_PATH = "/Users/nazmul/Documents/GitHub/nowinandroid/app/src/main/assets/sahih_bukhari.json"
NEWS_DB_PATH = "/Users/nazmul/Documents/GitHub/nowinandroid/app/src/main/assets/databases/news.db"

# Topic ID for Sahih Bukhari
TOPIC_SAHIH_BUKHARI = 8

# Starting ID for Bukhari hadiths
START_ID = 3001

def extract_hadith_number(info):
    """Extract hadith number from info string."""
    match = re.search(r'Number\s*(\d+)', info, re.IGNORECASE)
    if match:
        return int(match.group(1))
    return None

def main():
    # Load JSON
    print(f"Loading JSON from {JSON_PATH}...")
    with open(JSON_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Connect to database
    print(f"Connecting to {NEWS_DB_PATH}...")
    conn = sqlite3.connect(NEWS_DB_PATH)
    cursor = conn.cursor()

    # First, remove any existing Bukhari hadiths (IDs >= 3001 and < 4000)
    cursor.execute("DELETE FROM news_topics WHERE news_id >= 3001 AND news_id < 10000")
    cursor.execute("DELETE FROM news_resources WHERE id >= 3001 AND id < 10000")
    conn.commit()
    print("Cleared existing Bukhari hadiths...")

    # Parse and insert hadiths
    news_id = START_ID
    hadith_count = 0

    for volume in data:
        volume_name = volume.get('name', '')
        books = volume.get('books', [])

        for book in books:
            book_name = book.get('name', '')
            hadiths = book.get('hadiths', [])

            for hadith in hadiths:
                info = hadith.get('info', '')
                narrator = hadith.get('by', '')
                text = hadith.get('text', '').strip()

                hadith_number = extract_hadith_number(info)
                if hadith_number is None:
                    continue

                # Create title
                title = f"Hadith {hadith_number} - {book_name}"

                # Create content with narrator
                content = ""
                if narrator:
                    content = f"{narrator}\n\n{text}"
                else:
                    content = text

                # URL pointing to hadith detail
                url = f"hadith://sahih_bukhari/{hadith_number}"

                # Insert news resource
                cursor.execute("""
                    INSERT INTO news_resources (id, title, content, url, header_image_url, publish_date, type, is_system, is_user_created, source, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    news_id,
                    title,
                    content,
                    url,
                    "drawable://ic_bukhari",  # Placeholder image
                    "2024-01-01T00:00:00.000Z",
                    "Hadith 📖",
                    1,  # is_system
                    0,  # is_user_created
                    "Sahih Bukhari",
                    None,
                    None
                ))

                # Link to topic
                cursor.execute("""
                    INSERT INTO news_topics (news_id, topic_id)
                    VALUES (?, ?)
                """, (news_id, TOPIC_SAHIH_BUKHARI))

                news_id += 1
                hadith_count += 1

    conn.commit()
    conn.close()

    print(f"Successfully imported {hadith_count} Bukhari hadiths!")
    print(f"IDs range: {START_ID} to {news_id - 1}")
    print(f"All linked to topic ID {TOPIC_SAHIH_BUKHARI} (Sahih Bukhari)")

if __name__ == "__main__":
    main()
