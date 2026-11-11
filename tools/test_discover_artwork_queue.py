import datetime
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import artwork_feed
import discover_artwork_queue as discovery


def row(identifier, theme="SPACE"):
    return artwork_feed.candidate(identifier, "Landscape " + identifier, "Test creator",
        "https://images.nasa.gov/details/" + identifier,
        "https://images-assets.nasa.gov/image/" + identifier + "/image.jpg", theme,
        "NASA media usage guidelines", "https://www.nasa.gov/nasa-brand-center/images-and-media/")


class DiscoveryQueueTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)
        self.day = datetime.date(2026, 11, 11)
        self.previews = []

    def preview(self, item, directory):
        self.previews.append(item["id"])
        path = directory / (item["id"] + ".jpg")
        path.write_bytes(item["id"].encode())
        item.update(sha256=hashlib.sha256(path.read_bytes()).hexdigest(), previewFile=str(path))

    def provider(self, source, query, limit, page):
        theme = "ISLAMIC" if source in {"met", "cleveland"} else "SPACE" if source == "nasa" else "NATURE"
        return [dict(row(source + str(i), theme), approved=True) for i in range(limit)]

    def run_queue(self, state=None, feed=None, provider=None, **kwargs):
        return discovery.run_discovery(feed or {"artworks": []}, state or {}, self.output, self.day,
            limit=2, discover=provider or self.provider, preview=self.preview, validate=lambda _: None, **kwargs)

    def test_all_themes_are_discovered_and_nothing_is_approved_or_published(self):
        feed = {"artworks": [dict(row("existing"), sha256="a" * 64)]}
        before = json.dumps(feed)
        queue, summary = self.run_queue(feed=feed)
        self.assertEqual({"ISLAMIC", "SPACE", "NATURE"}, {x["theme"] for x in queue["artworks"]})
        self.assertEqual(10, summary["newCandidates"])
        self.assertTrue(all(x["approved"] is False for x in queue["artworks"]))
        self.assertEqual(before, json.dumps(feed))
        self.assertTrue((self.output / "review.html").is_file())

    def test_persisted_pending_queue_does_not_download_the_same_candidates_again(self):
        queue, _ = self.run_queue()
        count = len(self.previews)
        restored, summary = self.run_queue(state=queue)
        self.assertEqual(0, summary["newCandidates"])
        self.assertEqual(count, len(self.previews))
        self.assertEqual(queue["artworks"], restored["artworks"])

    def test_published_ids_are_skipped_and_removed_from_pending_queue(self):
        queue, _ = self.run_queue()
        published = queue["artworks"][0]
        restored, _ = self.run_queue(state=queue, feed={"artworks": [published]})
        self.assertNotIn(published["id"], {x["id"] for x in restored["artworks"]})

    def test_one_provider_failure_does_not_hide_other_candidates(self):
        def provider(source, query, limit, page):
            if source == "nasa":
                raise OSError("Provider unavailable")
            return self.provider(source, query, limit, page)
        queue, summary = self.run_queue(provider=provider)
        self.assertEqual(8, len(queue["artworks"]))
        self.assertEqual("nasa", summary["failures"][0]["source"])

    def test_changing_queries_and_pages_avoids_a_permanent_first_page_catalog(self):
        for source in discovery.SEARCHES:
            choices = {discovery.search_for_day(source, self.day + datetime.timedelta(days=i)) for i in range(10)}
            self.assertEqual(10, len(choices))

    def test_invalid_source_urls_are_not_included_in_review_html(self):
        def provider(source, query, limit, page):
            return [dict(row("unsafe-" + source), sourceUrl="javascript:alert(1)")]
        queue, summary = self.run_queue(provider=provider)
        self.assertEqual([], queue["artworks"])
        self.assertEqual([], self.previews)
        self.assertEqual(5, len(summary["failures"]))

    def test_html_escapes_provider_text(self):
        def provider(source, query, limit, page):
            return [dict(row(source), title="<script>alert(1)</script>")]
        self.run_queue(provider=provider)
        report = (self.output / "review.html").read_text()
        self.assertNotIn("<script>", report)
        self.assertIn("&lt;script&gt;", report)

    def test_queue_is_bounded_without_dropping_existing_pending_work(self):
        queue, summary = self.run_queue(max_queue=3)
        self.assertEqual(3, len(queue["artworks"]))
        self.assertTrue(summary["queueFull"])
        restored, summary = self.run_queue(state=queue, max_queue=3)
        self.assertEqual(queue["artworks"], restored["artworks"])
        self.assertEqual(0, summary["newCandidates"])

    def test_rejected_candidates_are_not_downloaded_or_kept_in_pending_queue(self):
        queue, _ = self.run_queue()
        rejected = queue["artworks"][0]["id"]
        count = len(self.previews)
        restored, _ = self.run_queue(state=queue, rejected_ids=[rejected])
        self.assertNotIn(rejected, {x["id"] for x in restored["artworks"]})
        self.assertEqual(count, len(self.previews))

    def test_duplicate_image_hashes_cannot_pad_the_queue_with_repeat_photos(self):
        def same_preview(item, directory):
            self.preview(item, directory)
            item["sha256"] = "f" * 64
        queue, summary = discovery.run_discovery({"artworks": []}, {}, self.output, self.day, limit=2,
            discover=self.provider, preview=same_preview, validate=lambda _: None)
        self.assertEqual(1, len(queue["artworks"]))
        self.assertEqual(1, summary["newCandidates"])


if __name__ == "__main__":
    unittest.main()
