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
    def test_cleveland_candidates_require_cc0_and_the_islamic_department(self):
        def fetch(url):
            valid = dict(accession_number="2004.70", share_license_status="CC0", department="Islamic Art",
                         title="Floral Tile", url="https://clevelandart.org/art/2004.70", creditline="Museum collection",
                         images={"web": {"url": "https://openaccess-cdn.clevelandart.org/2004.70/2004.70_web.jpg"}})
            return {"data": [valid, dict(valid, accession_number="other", share_license_status="Copyright"),
                              dict(valid, accession_number="not-islamic", department="Modern Art")]}
        rows = list(artwork_feed.discover("cleveland", "tile", 3, fetch=fetch))
        self.assertEqual(["cma-2004-70"], [x["id"] for x in rows])
        self.assertIs(rows[0]["approved"], False)

    def test_met_discovery_uses_the_supported_endpoint_and_bounds_object_requests(self):
        calls = []
        def fetch(url):
            calls.append(url)
            if "/search?" in url:
                return {"objectIDs": [1, 2, 3, 4, 5]}
            identifier = url.rsplit("/", 1)[-1]
            return dict(isPublicDomain=True, title="Tile", primaryImageSmall="https://images.metmuseum.org/CRDImages/is/web-large/" + identifier + ".jpg",
                        objectURL="https://www.metmuseum.org/art/collection/search/" + identifier)
        rows = list(artwork_feed.discover("met", "tile", 2, fetch=fetch, page=2))
        self.assertEqual(["met-3", "met-4"], [x["id"] for x in rows])
        self.assertEqual(3, len(calls))
        self.assertIn("/v1/search?", calls[0])

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
