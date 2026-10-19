import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "generate_hadith_collection_models.py"
SPEC = importlib.util.spec_from_file_location("generate_hadith_collection_models", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def collection_data(hadiths, chapters):
    return {"chapters": chapters, "hadiths": hadiths}


class ComputeBooksTest(unittest.TestCase):
    def test_ranges_are_contiguous_and_ordered(self):
        data = collection_data(
            [{"chapterId": 1}] * 3 + [{"chapterId": 2}] * 2 + [{"chapterId": 3}] * 4,
            [
                {"id": 1, "english": "Purification", "arabic": "كتاب الطهارة"},
                {"id": 2, "english": "Prayer", "arabic": "كتاب الصلاة"},
                {"id": 3, "english": "Zakat", "arabic": "كتاب الزكاة"},
            ],
        )
        books = MODULE.compute_books(data)
        self.assertEqual(
            [(book.first, book.last, book.count) for book in books],
            [(1, 3, 3), (4, 5, 2), (6, 9, 4)],
        )
        self.assertEqual([book.name_en for book in books], ["Purification", "Prayer", "Zakat"])

    def test_null_chapter_uses_the_unnumbered_chapter_entry(self):
        data = collection_data(
            [{"chapterId": 1}] * 2 + [{"chapterId": None}] * 3 + [{"chapterId": 2}] * 2,
            [
                {"id": 1, "english": "Oaths", "arabic": "كتاب الأيمان"},
                {"id": None, "english": "The Book of Agriculture", "arabic": "كتاب المزارعة"},
                {"id": 2, "english": "Women", "arabic": "كتاب النساء"},
            ],
        )
        books = MODULE.compute_books(data)
        self.assertEqual(len(books), 3)
        self.assertEqual(books[1].name_en, "The Book of Agriculture")
        self.assertEqual((books[1].first, books[1].last, books[1].count), (3, 5, 3))
        # The synthetic id stays stable and unique alongside the source ids.
        self.assertNotEqual(books[1].id, books[0].id)
        self.assertNotEqual(books[1].id, books[2].id)

    def test_non_contiguous_ranges_are_rejected(self):
        data = collection_data(
            [{"chapterId": 1}] * 2 + [{"chapterId": 2}] * 2 + [{"chapterId": 1}] * 2,
            [
                {"id": 1, "english": "A", "arabic": "أ"},
                {"id": 2, "english": "B", "arabic": "ب"},
            ],
        )
        with self.assertRaises(ValueError):
            MODULE.compute_books(data)


class KotlinStringTest(unittest.TestCase):
    def test_escapes_kotlin_specials_and_collapses_spacing(self):
        self.assertEqual(
            MODULE.kotlin_string('He  said  "prayer"\\$1'),
            '"He said \\"prayer\\"\\\\\\$1"',
        )
        self.assertEqual(MODULE.kotlin_string("line\nbreak"), '"line\\nbreak"')


if __name__ == "__main__":
    unittest.main()
