import json
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class GroundedQuizDatasetTest(unittest.TestCase):
    def test_builds_cited_questions_from_every_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            quran = root / "quran.db"
            translation = root / "quran_en.db"
            bukhari = root / "bukhari.json"
            shamayel = root / "shamayel.db"
            output = root / "questions.jsonl"
            self._create_arabic_quran(quran)
            self._create_quran_translation(translation)
            self._create_bukhari(bukhari)
            self._create_shamayel(shamayel)

            script = Path(__file__).with_name("build_grounded_quiz_dataset.py")
            subprocess.run(
                [
                    sys.executable,
                    str(script),
                    "--quran",
                    str(quran),
                    "--quran-translation",
                    str(translation),
                    "--bukhari-json",
                    str(bukhari),
                    "--shamayel",
                    str(shamayel),
                    "--output",
                    str(output),
                ],
                check=True,
            )

            records = [json.loads(line) for line in output.read_text().splitlines()]
            self.assertEqual(24, len(records))
            self.assertEqual(
                {"quran", "sahih_al_bukhari", "shamayel_at_tirmidhi"},
                {record["sourceCollection"] for record in records},
            )
            self.assertTrue(all(record["reviewStatus"] == "source_locked" for record in records))
            self.assertEqual({"question", "knowledge"}, {record["contentType"] for record in records})
            questions = [record for record in records if record["contentType"] == "question"]
            knowledge = [record for record in records if record["contentType"] == "knowledge"]
            self.assertTrue(all(len(set(record["options"])) == 4 for record in questions))
            self.assertTrue(all(record["body"] for record in knowledge))
            self.assertTrue(all(record["sourceReference"] for record in records))
            quran_records = [record for record in records if record["sourceCollection"] == "quran"]
            self.assertTrue(all(record["sourceArabic"] for record in quran_records))
            self.assertTrue(all(record["sourceText"] in record.get("prompt", record.get("body", "")) for record in quran_records))
            preserved = next(record for record in quran_records if record["id"] == "quran-1-1-knowledge")
            self.assertEqual("  Translated  ayah 1\nwith exact spacing  ", preserved["body"])

    @staticmethod
    def _create_arabic_quran(path):
        connection = sqlite3.connect(path)
        connection.execute(
            "CREATE TABLE ayahs (surah_number INTEGER, number_in_surah INTEGER, text TEXT)"
        )
        for number in range(1, 5):
            arabic = "  Arabic  ayah 1\nwith exact spacing  " if number == 1 else f"Arabic {number}"
            connection.execute("INSERT INTO ayahs VALUES (?, 1, ?)", (number, arabic))
        connection.commit()
        connection.close()

    @staticmethod
    def _create_quran_translation(path):
        connection = sqlite3.connect(path)
        connection.executescript(
            """
            CREATE TABLE surahs (
              number INTEGER, name_en TEXT, name_en_translation TEXT, type TEXT
            );
            CREATE TABLE ayahs (
              surah_number INTEGER, number_in_surah INTEGER, text TEXT
            );
            """
        )
        for number in range(1, 5):
            connection.execute(
                "INSERT INTO surahs VALUES (?, ?, ?, ?)",
                (number, f"Surah {number}", f"Topic {number}", "Meccan"),
            )
            translation = (
                "  Translated  ayah 1\nwith exact spacing  "
                if number == 1
                else f"Translated ayah {number}"
            )
            connection.execute(
                "INSERT INTO ayahs VALUES (?, 1, ?)",
                (number, translation),
            )
        connection.commit()
        connection.close()

    @staticmethod
    def _create_bukhari(path):
        books = []
        for number in range(1, 5):
            books.append(
                {
                    "name": f"{number}. Book {number}",
                    "hadiths": [
                        {
                            "info": f"Volume 1, Book {number}, Number {number}",
                            "by": f"Narrator {number}",
                            "text": f"Bukhari narration {number}",
                        }
                    ],
                }
            )
        path.write_text(json.dumps([{"name": "Volume 1", "books": books}]))

    @staticmethod
    def _create_shamayel(path):
        connection = sqlite3.connect(path)
        connection.executescript(
            """
            CREATE TABLE chapters (chapter_no INTEGER, title_en TEXT);
            CREATE TABLE hadiths (id INTEGER, text_plain TEXT);
            CREATE TABLE hadith_details (
              hadith_id INTEGER, chapter_title_en TEXT, english_text TEXT
            );
            """
        )
        for number in range(1, 5):
            connection.execute("INSERT INTO chapters VALUES (?, ?)", (number, f"Chapter {number}"))
            connection.execute("INSERT INTO hadiths VALUES (?, ?)", (number, f"Fallback {number}"))
            connection.execute(
                "INSERT INTO hadith_details VALUES (?, ?, ?)",
                (number, f"Chapter {number}", f"Shamayel narration {number}"),
            )
        connection.commit()
        connection.close()


if __name__ == "__main__":
    unittest.main()
