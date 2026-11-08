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
ALLOWED_SUBJECTS = {"ISLAMIC_ARCHITECTURE", "ISLAMIC_PATTERN", "CALM_NATURE"}


def validate_catalog(items):
    if len(items) < 5:
        raise ValueError("The home gallery needs at least five approved images")
    for item in items:
        if (item.get("reviewed") is not True or item.get("human_figures") is not False
                or item.get("subject") not in ALLOWED_SUBJECTS or not item.get("relevance")):
            raise ValueError(f"Unapproved or irrelevant home artwork: {item.get('id')}")
        if item.get("license") != "https://creativecommons.org/publicdomain/zero/1.0/":
            raise ValueError(f"Home artwork must be CC0: {item.get('id')}")
    return items


def write_kotlin_catalog(items):
    """Keep the runtime allowlist in sync with the reviewed provenance catalog."""
    def quoted(value):
        return json.dumps(value, ensure_ascii=False).replace("$", "\\$")

    output = ROOT / "core/images/src/commonMain/kotlin/com/starception/submission/core/images/HomeArtworkCatalog.kt"
    header = (ROOT / "spotless/copyright.kt").read_text().replace("$YEAR", "2026")
    lines = [header, "package com.starception.submission.core.images\n",
             "import com.starception.submission.core.images.resources.Res"]
    for resource in sorted({item[key] for item in items for key in ("day", "night")}):
        lines.append(f"import com.starception.submission.core.images.resources.{resource}")
    lines += ["", "// Generated from artwork/getty-home-artwork.json. Add only reviewed, approved subjects.",
              "val homeArtworkGallery: List<HomeArtwork> = listOf("]
    for item in items:
        lines += ["    HomeArtwork(", f"        title = {quoted(item['title'].strip('[]'))},",
                  f"        artist = {quoted(item['artist'])},", f"        sourceUrl = {quoted(item['source'])},",
                  f"        day = Res.drawable.{item['day']},", f"        night = Res.drawable.{item['night']},",
                  f"        subject = HomeArtworkSubject.{item['subject']},", "    ),"]
    lines += [")", ""]
    output.write_text("\n".join(lines))


def main():
    items = validate_catalog(json.loads(CATALOG.read_text()))
    for item in items:
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
        w, h = image.size
        left, top, right, bottom = item["source_crop"]
        image = image.crop((round(w * left), round(h * top), round(w * right), round(h * bottom)))
        image = ImageOps.fit(image, SIZE, Image.Resampling.LANCZOS,
                             centering=(item["crop_center_x"], 0.5))
        image.save(OUTPUT / f"{item['day']}.webp", quality=84, method=6)
        # A separate night treatment retains the same composition for Prayer Now.
        night = ImageEnhance.Color(image).enhance(0.7)
        night = Image.blend(night, Image.new("RGB", SIZE, "#102238"), 0.48)
        night.save(OUTPUT / f"{item['night']}.webp", quality=84, method=6)
        print(f"Prepared {item['id']}: {item['title']}")
    write_kotlin_catalog(items)


if __name__ == "__main__":
    main()
