#!/usr/bin/env python3
"""
Script to download Sahih Bukhari data and create SQLite database
Data source: https://github.com/AhmedBaset/hadith-json
"""

import json
import sqlite3
import urllib.request
import os

# Output path
OUTPUT_PATH = "../app/src/main/assets/databases/hadith/sahih_bukhari.db"

# Data source
DATA_URL = "https://raw.githubusercontent.com/AhmedBaset/hadith-json/main/db/by_book/the_9_books/bukhari.json"

def download_data():
    """Download the JSON data from GitHub"""
    print("Downloading Sahih Bukhari data...")
    with urllib.request.urlopen(DATA_URL) as response:
        data = json.loads(response.read().decode('utf-8'))
    print(f"Downloaded {len(data.get('hadiths', []))} hadiths")
    return data

def create_database(data, output_path):
    """Create SQLite database with the required schema"""

    # Remove existing database
    if os.path.exists(output_path):
        os.remove(output_path)

    # Create directory if needed
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    conn = sqlite3.connect(output_path)
    cursor = conn.cursor()

    # Create metadata table
    cursor.execute('''
        CREATE TABLE metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    ''')

    # Insert metadata
    metadata = data.get('metadata', {})
    metadata_entries = [
        ('collection_id', '1'),
        ('name', metadata.get('english', {}).get('title', 'Sahih al-Bukhari')),
        ('name_arabic', metadata.get('arabic', {}).get('title', 'صحيح البخاري')),
        ('name_english', metadata.get('english', {}).get('title', 'Sahih al-Bukhari')),
        ('author', metadata.get('english', {}).get('author', 'Imam Muhammad ibn Ismail al-Bukhari')),
        ('author_arabic', metadata.get('arabic', {}).get('author', 'الإمام محمد بن إسماعيل البخاري')),
        ('has_elaboration', '0'),
        ('hadith_count', str(len(data.get('hadiths', []))))
    ]

    cursor.executemany('INSERT INTO metadata (key, value) VALUES (?, ?)', metadata_entries)

    # Create hadiths table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS hadiths (
            id INTEGER NOT NULL PRIMARY KEY,
            text_arabic TEXT NOT NULL,
            text_plain TEXT,
            elaboration TEXT
        )
    ''')

    # Insert hadiths
    hadiths = data.get('hadiths', [])
    print(f"Inserting {len(hadiths)} hadiths...")

    for hadith in hadiths:
        hadith_id = hadith.get('id')
        text_arabic = hadith.get('arabic', '')

        # Combine narrator and text for English
        english = hadith.get('english', {})
        narrator = english.get('narrator', '')
        text = english.get('text', '')
        text_plain = f"{narrator}\n\n{text}".strip() if narrator or text else None

        cursor.execute(
            'INSERT INTO hadiths (id, text_arabic, text_plain, elaboration) VALUES (?, ?, ?, ?)',
            (hadith_id, text_arabic, text_plain, None)
        )

    # Create FTS5 virtual table for full-text search
    cursor.execute('''
        CREATE VIRTUAL TABLE hadiths_fts USING fts5(
            text_plain,
            content='hadiths',
            content_rowid='id'
        )
    ''')

    # Populate FTS table
    cursor.execute('''
        INSERT INTO hadiths_fts(hadiths_fts) VALUES('rebuild')
    ''')

    conn.commit()

    # Get final count
    cursor.execute('SELECT COUNT(*) FROM hadiths')
    count = cursor.fetchone()[0]
    print(f"Created database with {count} hadiths")

    conn.close()

    # Get file size
    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"Database size: {size_mb:.2f} MB")

    return output_path

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, OUTPUT_PATH)

    data = download_data()
    db_path = create_database(data, output_path)
    print(f"\nDatabase created successfully: {db_path}")

if __name__ == '__main__':
    main()
