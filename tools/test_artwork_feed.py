import json
from pathlib import Path
import unittest
import artwork_feed

class FakeProvider:
    def __call__(self, url):
        return {"collection": {"items": [{"data": [{"nasa_id": "TEST", "title": "Nebula"}],
            "links": [{"render": "image", "width": 640, "height": 640,
                "href": "https://images-assets.nasa.gov/image/TEST/TEST~small.jpg"}]}]}}

class ArtworkFeedTest(unittest.TestCase):
    def test_discovery_never_automatically_approves_provider_results(self):
        rows = list(artwork_feed.discover("nasa", "nebula", 1, fetch=FakeProvider()))
        self.assertEqual(1, len(rows))
        self.assertIs(rows[0]["approved"], False)
        self.assertNotIn("sha256", rows[0])

    def test_published_manifest_has_complete_reviewed_records_for_each_theme(self):
        path = Path(__file__).resolve().parents[1] / "core/images/artwork/home-artwork-feed.json"
        feed = json.loads(path.read_text())
        self.assertEqual(1, feed["schemaVersion"])
        rows = feed["artworks"]
        self.assertEqual(len(rows), len({x["id"] for x in rows}))
        for theme in ["ISLAMIC", "SPACE", "NATURE"]:
            self.assertGreaterEqual(sum(x["theme"] == theme for x in rows), 5)
        for row in rows:
            self.assertIs(row["approved"], True)
            self.assertEqual(64, len(row["sha256"]))
            self.assertTrue(all(c in "0123456789abcdef" for c in row["sha256"]))
            for field in ["sourceUrl", "imageUrl", "licenseUrl"]:
                self.assertTrue(row[field].startswith("https://"))
            self.assertTrue(row["credit"])
            self.assertTrue(row["licenseName"])
        self.assertNotIn("picsum-37", {x["id"] for x in rows})

if __name__ == "__main__":
    unittest.main()
