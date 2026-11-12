import json
from hashlib import sha256
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parent.parent
CATALOGS = (
    ROOT / "iosApp/PrayerLiveActivity/Assets.xcassets/DeenlyLiveGlyph.imageset",
    ROOT / "iosApp/Sources/Assets.xcassets/DeenlyNowPlayingArtwork.imageset",
)


class IosNotificationArtworkTest(unittest.TestCase):
    def test_notification_artwork_is_the_exact_existing_app_icon(self):
        master = sha256((ROOT / "iosApp/Sources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png").read_bytes()).hexdigest()
        for catalog in CATALOGS:
            with self.subTest(catalog=catalog.name):
                manifest = json.loads((catalog / "Contents.json").read_text())
                for image in manifest["images"]:
                    self.assertEqual(master, sha256((catalog / image["filename"]).read_bytes()).hexdigest())

    def test_both_catalogs_preserve_the_original_brand_colours(self):
        for catalog in CATALOGS:
            with self.subTest(catalog=catalog.name):
                manifest = json.loads((catalog / "Contents.json").read_text())
                self.assertEqual("original", manifest["properties"]["template-rendering-intent"])


if __name__ == "__main__":
    unittest.main()
