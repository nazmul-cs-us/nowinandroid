"""Regression: a corpus row ID must never become a publisher citation."""

import importlib.util
from pathlib import Path
import sqlite3
import tempfile
import unittest


SCRIPT = Path(__file__).parents[1] / 'generate_news_db.py'
spec = importlib.util.spec_from_file_location('generate_news_db', SCRIPT)
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


class HadithSourceIntegrityTest(unittest.TestCase):
    def test_feed_quote_and_target_use_same_record(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'sahih_bukhari.db'
            source = sqlite3.connect(path)
            source.execute('CREATE TABLE hadiths (id INTEGER PRIMARY KEY, text_plain TEXT)')
            narration = 'Narrated Malik bin Huwairith: Two men came to the Prophet with the intention of a journey.'
            source.execute('INSERT INTO hadiths VALUES (615, ?)', (narration,))
            source.execute("INSERT INTO hadiths VALUES (630, 'A different local narration')")
            source.commit()
            source.close()
            old = generator.BUKHARI_DB
            generator.BUKHARI_DB = str(path)
            try:
                target = sqlite3.connect(':memory:')
                target.executescript('''
                    CREATE TABLE news_resources (id INTEGER PRIMARY KEY, title TEXT, content TEXT,
                        url TEXT, header_image_url TEXT, publish_date TEXT, type TEXT,
                        is_system INTEGER, is_user_created INTEGER, source TEXT,
                        created_at TEXT, updated_at TEXT);
                    CREATE TABLE news_topics (news_id INTEGER, topic_id INTEGER);
                ''')
                self.assertEqual(2, generator.generate_bukhari(target))
                title, text, url = target.execute('SELECT title, content, url FROM news_resources WHERE id = 1000615').fetchone()
                self.assertEqual('Sahih al-Bukhari · Local record 615', title)
                self.assertEqual(narration, text)
                self.assertEqual('hadith-local://sahih_bukhari/615', url)
                self.assertEqual(0, target.execute("SELECT COUNT(*) FROM news_resources WHERE url LIKE 'hadith://%'").fetchone()[0])
                target.close()
            finally:
                generator.BUKHARI_DB = old


if __name__ == '__main__':
    unittest.main()
