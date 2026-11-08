"""Verify that the home-artwork builder rejects unreviewed or unrelated content."""

import copy
import json
import unittest

from prepare_getty_home_artwork import CATALOG, validate_catalog


class HomeArtworkApprovalTest(unittest.TestCase):
    def setUp(self):
        self.items = json.loads(CATALOG.read_text())

    def test_approved_catalog_includes_islamic_art_and_calm_nature(self):
        approved = validate_catalog(self.items)
        self.assertEqual({"ISLAMIC_ARCHITECTURE", "ISLAMIC_PATTERN", "CALM_NATURE"},
                         {item["subject"] for item in approved})

    def test_rejects_people_unreviewed_images_and_unrelated_subjects(self):
        for key, value in [("human_figures", True), ("reviewed", False),
                           ("subject", "PORTRAIT"), ("relevance", "")]:
            with self.subTest(key=key):
                items = copy.deepcopy(self.items)
                items[0][key] = value
                with self.assertRaises(ValueError):
                    validate_catalog(items)

    def test_rejects_missing_review_metadata(self):
        del self.items[0]["reviewed"]
        with self.assertRaises(ValueError):
            validate_catalog(self.items)

    def test_rejects_non_cc0_images(self):
        self.items[0]["license"] = "all rights reserved"
        with self.assertRaises(ValueError):
            validate_catalog(self.items)


if __name__ == "__main__":
    unittest.main()
