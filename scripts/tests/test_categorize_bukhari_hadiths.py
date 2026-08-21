import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "categorize_bukhari_hadiths.py"
SPEC = importlib.util.spec_from_file_location("categorize_bukhari_hadiths", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def canonical_data(hadiths):
    chapters = [
        {"id": index, "english": f"Book {index}", "arabic": f"كتاب {index}"}
        for index in range(1, 98)
    ]
    return {"chapters": chapters, "hadiths": hadiths}


class ContentCategoryMatcherTest(unittest.TestCase):
    def test_normalized_exact_match_handles_edition_wording_and_spacing(self):
        data = canonical_data(
            [
                {
                    "id": 1,
                    "chapterId": 1,
                    "arabic": "إنما الأعمال بالنيات",
                    "english": {
                        "text": "Allah's Messenger (ﷺ) spoke in bright daylight about intentions."
                    },
                }
            ]
        )
        source = [
            {
                "name": "Volume 1",
                "books": [
                    {
                        "name": "Legacy book",
                        "hadiths": [
                            {
                                "info": "Number 1",
                                "text": "Allah's Apostle spoke in bright day light about intentions.",
                            }
                        ],
                    }
                ],
            }
        ]

        result = MODULE.categorize(source, data)

        self.assertEqual(1, result["matches"][0]["category_id"])
        self.assertEqual("exact_english", result["matches"][0]["method"])

    def test_fuzzy_match_retrieves_category(self):
        data = canonical_data(
            [
                {
                    "id": 10,
                    "chapterId": 7,
                    "arabic": "",
                    "english": {
                        "text": "A believer gives food to the hungry neighbor and cares for the poor every day."
                    },
                },
                {
                    "id": 11,
                    "chapterId": 8,
                    "arabic": "",
                    "english": {
                        "text": "The prayer begins when the caller announces the appointed time at the mosque."
                    },
                },
            ]
        )
        source = {
            "hadiths": [
                {
                    "id": 99,
                    "english": {
                        "text": "A believer gives food to the hungry neighbor and cares for poor people every day."
                    },
                }
            ]
        }

        result = MODULE.categorize(source, data, min_similarity=0.50)

        self.assertEqual(7, result["matches"][0]["category_id"])
        self.assertEqual("fuzzy_english", result["matches"][0]["method"])

    def test_same_content_in_different_books_is_not_forced(self):
        repeated = "The same report is intentionally repeated in this collection."
        data = canonical_data(
            [
                {"id": 20, "chapterId": 3, "arabic": "", "english": {"text": repeated}},
                {"id": 21, "chapterId": 4, "arabic": "", "english": {"text": repeated}},
            ]
        )
        source = {"hadiths": [{"id": 1, "english": {"text": repeated}}]}

        result = MODULE.categorize(source, data)
        match = result["matches"][0]

        self.assertIsNone(match["category_id"])
        self.assertEqual("ambiguous_exact", match["method"])
        self.assertEqual([3, 4], match["candidate_category_ids"])

    def test_unrelated_content_is_unresolved(self):
        data = canonical_data(
            [
                {
                    "id": 30,
                    "chapterId": 5,
                    "arabic": "",
                    "english": {"text": "Prayer and remembrance bring tranquility to the heart."},
                }
            ]
        )
        source = {"hadiths": [{"id": 2, "english": {"text": "A completely unrelated sentence."}}]}

        result = MODULE.categorize(source, data)

        self.assertIsNone(result["matches"][0]["category_id"])
        self.assertEqual("no_content_match", result["matches"][0]["method"])


if __name__ == "__main__":
    unittest.main()
