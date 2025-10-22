#!/usr/bin/env python3
"""
Script to convert quran.sql to SQLite database
This will create a quran.db file that can be used with Room database
"""

import sqlite3
import sys
import os
import re

def convert_sql_to_db(sql_file_path, output_db_path):
    """
    Convert SQL file to SQLite database
    """
    print(f"📥 Reading SQL file: {sql_file_path}")
    
    if not os.path.exists(sql_file_path):
        print(f"❌ Error: SQL file not found: {sql_file_path}")
        return False
    
    try:
        # Read the SQL file
        with open(sql_file_path, 'r', encoding='utf-8') as f:
            sql_content = f.read()
        
        print(f"✅ SQL file read successfully ({len(sql_content)} bytes)")
        
        # Remove existing database if it exists
        if os.path.exists(output_db_path):
            os.remove(output_db_path)
            print(f"🗑️  Removed existing database")
        
        # Create SQLite database
        print(f"🔨 Creating SQLite database: {output_db_path}")
        conn = sqlite3.connect(output_db_path)
        cursor = conn.cursor()
        
        # Clean up the SQL content
        # Remove MySQL specific commands
        sql_content = re.sub(r'SET .*?;', '', sql_content)
        sql_content = re.sub(r'/\*.*?\*/', '', sql_content, flags=re.DOTALL)
        sql_content = re.sub(r'START TRANSACTION;', '', sql_content)
        sql_content = re.sub(r'COMMIT;', '', sql_content)
        sql_content = re.sub(r'-- .*?\n', '\n', sql_content)
        
        # Fix MySQL specific data types
        sql_content = sql_content.replace('ENGINE=InnoDB', '')
        sql_content = sql_content.replace('DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci', '')
        sql_content = sql_content.replace('COLLATE utf8mb4_unicode_ci', '')
        sql_content = sql_content.replace('int(10) UNSIGNED', 'INTEGER')
        sql_content = sql_content.replace('int(11)', 'INTEGER')
        sql_content = sql_content.replace('tinyint(1)', 'INTEGER')
        sql_content = sql_content.replace('varchar(255)', 'TEXT')
        sql_content = sql_content.replace('timestamp NULL DEFAULT NULL', 'TEXT')
        
        # Execute the SQL script using executescript (which handles transactions)
        print(f"📝 Executing SQL script...")
        try:
            conn.executescript(sql_content)
            print(f"✅ SQL script executed successfully")
            executed = sql_content.count(';')
            errors = 0
        except sqlite3.Error as e:
            print(f"⚠️  Warning during execution: {str(e)[:200]}")
            errors = 1
            executed = 0
        
        # Create a new cursor after executescript
        cursor = conn.cursor()
        
        # Verify the database
        print(f"\n📊 Verifying database...")
        
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
        tables = cursor.fetchall()
        print(f"   Tables created: {len(tables)}")
        for table in tables:
            print(f"      - {table[0]}")
        
        # Check Surahs
        cursor.execute("SELECT COUNT(*) FROM surahs")
        surah_count = cursor.fetchone()[0]
        print(f"\n✅ Surahs: {surah_count}")
        
        # Check Ayahs
        cursor.execute("SELECT COUNT(*) FROM ayahs")
        ayah_count = cursor.fetchone()[0]
        print(f"✅ Ayahs: {ayah_count}")
        
        # Close connection
        conn.close()
        
        print(f"\n🎉 Database created successfully!")
        print(f"📁 Output: {output_db_path}")
        print(f"📈 Statistics:")
        print(f"   - Statements executed: {executed}")
        print(f"   - Errors: {errors}")
        print(f"   - Tables: {len(tables)}")
        print(f"   - Surahs: {surah_count}")
        print(f"   - Ayahs: {ayah_count}")
        
        # Calculate file size
        file_size = os.path.getsize(output_db_path)
        file_size_mb = file_size / (1024 * 1024)
        print(f"   - Database size: {file_size_mb:.2f} MB")
        
        return True
        
    except Exception as e:
        print(f"❌ Error: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """
    Main function
    """
    print("=" * 60)
    print("🕌 Quran SQL to SQLite Database Converter")
    print("=" * 60)
    print()
    
    # Default paths
    sql_file = "/Users/smarterai/Desktop/quran.sql"
    output_db = "app/src/main/assets/databases/quran.db"
    
    # Check if custom paths provided
    if len(sys.argv) > 1:
        sql_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_db = sys.argv[2]
    
    print(f"📥 Input:  {sql_file}")
    print(f"📤 Output: {output_db}")
    print()
    
    # Create assets/databases directory if it doesn't exist
    os.makedirs(os.path.dirname(output_db), exist_ok=True)
    
    # Convert
    success = convert_sql_to_db(sql_file, output_db)
    
    if success:
        print()
        print("=" * 60)
        print("✅ CONVERSION SUCCESSFUL!")
        print("=" * 60)
        print()
        print("📋 Next steps:")
        print("   1. The database is now in: app/src/main/assets/databases/")
        print("   2. Build and run your app")
        print("   3. Room will automatically load it from assets")
        print()
        return 0
    else:
        print()
        print("=" * 60)
        print("❌ CONVERSION FAILED!")
        print("=" * 60)
        print()
        return 1

if __name__ == "__main__":
    exit(main())

