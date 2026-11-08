#!/usr/bin/env python3
"""Rebuild bundled Getty portraits. Requires Pillow; never uses unreviewed images.

Run from any directory: python tools/prepare_getty_home_artwork.py
Only add catalog entries after checking both CC0 metadata and the full image for
human figures. Source digests prevent silent changes to the reviewed image.
"""

import hashlib
import io
import json
import re
import urllib.request
from pathlib import Path

from PIL import Image, ImageEnhance, ImageOps

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "core/images/artwork/getty-home-artwork.json"
OUTPUT = ROOT / "core/images/src/commonMain/composeResources/drawable"
SIZE = (1080, 1935)


def main():
    for item in json.loads(CATALOG.read_text()):
        with urllib.request.urlopen(item["source"], timeout=60) as response:
            html = response.read().decode()
        metadata = json.loads(re.search(
            r'<script type="application/ld\+json">(.*?)</script>', html, re.S,
        ).group(1))
        if metadata.get("license", "").replace("http:", "https:") != item["license"]:
            raise ValueError(f"License changed for {item['id']}; review before downloading")
        with urllib.request.urlopen(item["image_url"], timeout=60) as response:
            source = response.read()
        if hashlib.sha256(source).hexdigest() != item["source_sha256"]:
            raise ValueError(f"Image changed for {item['id']}; review before bundling")
        image = Image.open(io.BytesIO(source)).convert("RGB")
        border = item["trim_border"]
        w, h = image.size
        image = image.crop((round(w * border), round(h * border),
                            round(w * (1 - border)), round(h * (1 - border))))
        image = ImageOps.fit(image, SIZE, Image.Resampling.LANCZOS,
                             centering=(item["crop_center_x"], 0.5))
        image.save(OUTPUT / f"{item['day']}.webp", quality=84, method=6)
        # A separate night treatment retains the same composition for Prayer Now.
        night = ImageEnhance.Color(image).enhance(0.7)
        night = Image.blend(night, Image.new("RGB", SIZE, "#102238"), 0.48)
        night.save(OUTPUT / f"{item['night']}.webp", quality=84, method=6)
        print(f"Prepared {item['id']}: {item['title']}")


if __name__ == "__main__":
    main()
