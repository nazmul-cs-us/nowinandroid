#!/usr/bin/env python3
"""Discover provider candidates; explicitly reviewed bytes alone may enter the public feed.

No API credentials are embedded. Discovery never publishes unreviewed results.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

IMAGE_PREFIXES = (
    "https://images.metmuseum.org/CRDImages/is/web-large/",
    "https://www.clevelandart.org/_next/image?url=https%3A%2F%2Fpiction.clevelandart.org%2F",
    "https://openaccess-cdn.clevelandart.org/",
    "https://images-assets.nasa.gov/image/", "https://fastly.picsum.photos/id/",
    "https://api.openverse.org/v1/images/", "https://live.staticflickr.com/", "https://upload.wikimedia.org/",
)
USER_AGENT = "StarceptionArtworkDiscovery/1.0 (+https://github.com/nazmul-cs-us/nowinandroid)"

def get_json(url):
    with urlopen(Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT}), timeout=30) as response:
        data = response.read(2_000_001)
    if len(data) > 2_000_000:
        raise ValueError("Provider response exceeded metadata limit")
    return json.loads(data)

def candidate(id, title, credit, source, image, theme, license_name, license_url):
    return dict(id=id, title=title, credit=credit, sourceUrl=source, imageUrl=image,
                theme=theme, licenseName=license_name, licenseUrl=license_url,
                approved=False, status="pending-content-and-rights-review", crop=[0, 0, 1, 1])

def discover(source, query, limit, fetch=get_json, page=1):
    if source == "nasa":
        rows = fetch("https://images-api.nasa.gov/search?" + urlencode(dict(q=query, media_type="image", page_size=limit, page=page)))
        for item in rows["collection"]["items"]:
            meta = item["data"][0]
            links = [x for x in item.get("links", []) if x.get("render") == "image"
                     and 100 <= x.get("width", 0) <= 1800 and 100 <= x.get("height", 0) <= 1800]
            if not links or meta.get("copyright"):
                continue
            image = max(links, key=lambda x: x["width"] * x["height"])["href"]
            id = meta["nasa_id"]
            yield candidate("nasa-" + id, meta["title"], meta.get("secondary_creator", "NASA; verify full credit"),
                            "https://images.nasa.gov/details/" + id, image, "SPACE",
                            "NASA media usage guidelines", "https://www.nasa.gov/nasa-brand-center/images-and-media/")
    elif source == "picsum":
        for meta in fetch("https://picsum.photos/v2/list?" + urlencode(dict(limit=limit, page=page))):
            id = str(meta["id"])
            yield candidate("picsum-" + id, "Photograph " + id, meta["author"], meta["url"],
                            "https://picsum.photos/id/" + id + "/1000/1400", "NATURE",
                            "Unsplash License", "https://unsplash.com/license")
    elif source == "met":
        rows = fetch("https://collectionapi.metmuseum.org/public/collection/v1/search?" +
                        urlencode(dict(q=query, hasImages="true", departmentId=14)))
        for id in (rows.get("objectIDs") or [])[(page - 1) * limit:page * limit]:
            meta = fetch("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + str(id))
            if meta.get("isPublicDomain") and meta.get("primaryImageSmall"):
                yield candidate("met-" + str(id), meta["title"], meta.get("artistDisplayName") or "Unknown artist · The Met",
                                meta["objectURL"], meta["primaryImageSmall"], "ISLAMIC", "CC0",
                                "https://creativecommons.org/publicdomain/zero/1.0/")
    elif source == "cleveland":
        params = dict(q=query, cc0=1, has_image=1, department="Islamic Art", limit=limit, skip=(page - 1) * limit)
        rows = fetch("https://openaccess-api.clevelandart.org/api/artworks/?" + urlencode(params))
        total = rows.get("info", {}).get("total", 0)
        if not rows.get("data") and total:
            params["skip"] %= total
            rows = fetch("https://openaccess-api.clevelandart.org/api/artworks/?" + urlencode(params))
        for meta in rows.get("data", []):
            image = (meta.get("images") or {}).get("web") or {}
            if meta.get("share_license_status") != "CC0" or meta.get("department") != "Islamic Art" or not image.get("url"):
                continue
            accession = meta["accession_number"]
            credit = meta.get("image_credit") or "The Cleveland Museum of Art · " + (meta.get("creditline") or "CC0 collection")
            yield candidate("cma-" + accession.replace(".", "-"), meta["title"], credit,
                            meta["url"], image["url"], "ISLAMIC", "CC0",
                            "https://creativecommons.org/publicdomain/zero/1.0/")
    elif source == "openverse":
        rows = fetch("https://api.openverse.org/v1/images/?" + urlencode(dict(q=query, license="cc0,pdm", page_size=limit, page=page)))
        for meta in rows.get("results", []):
            if meta.get("license") not in ["cc0", "pdm"]:
                continue
            id = meta["id"]
            yield candidate("openverse-" + id, meta.get("title") or "Untitled photograph",
                            (meta.get("creator") or "Creator must be verified") + " · via Openverse",
                            "https://openverse.org/image/" + id,
                            "https://api.openverse.org/v1/images/" + id + "/thumb/", "NATURE",
                            "CC0" if meta["license"] == "cc0" else "Public Domain Mark", meta["license_url"])

def preview(item, directory):
    id = item["id"]
    if not 1 <= len(id) <= 80 or not all(c.isascii() and (c.isalnum() or c in "_-") for c in id):
        raise ValueError("Unsafe candidate identifier")
    url = item["imageUrl"]
    if not url.startswith(IMAGE_PREFIXES + ("https://picsum.photos/id/",)):
        raise ValueError("Candidate image host is not enabled")
    with urlopen(Request(url, headers={"Accept": "image/jpeg", "User-Agent": USER_AGENT}), timeout=30) as response:
        if not response.url.startswith(IMAGE_PREFIXES):
            raise ValueError("Image redirected outside approved image services")
        if response.headers.get_content_type() not in ["image/jpeg", "image/png", "image/webp"]:
            raise ValueError("Only supported raster images may enter the review queue")
        data = response.read(8_000_001)
        item["imageUrl"] = response.url
    if not data or len(data) > 8_000_000:
        raise ValueError("Invalid image size")
    path = directory / (item["id"] + ".jpg")
    path.write_bytes(data)
    item["sha256"] = hashlib.sha256(data).hexdigest()
    item["previewFile"] = str(path.resolve())

def approve(candidate_file, id, feed_file, reviewed_image, reviewer):
    candidates = json.loads(candidate_file.read_text())["artworks"]
    item = next(x for x in candidates if x["id"] == id)
    expected = hashlib.sha256(reviewed_image.read_bytes()).hexdigest()
    if expected != item.get("sha256"):
        raise ValueError("Reviewed bytes do not match the candidate preview")
    if not item["imageUrl"].startswith(IMAGE_PREFIXES):
        raise ValueError("Image service not enabled")
    with urlopen(Request(item["imageUrl"], headers={"Accept": "image/jpeg", "User-Agent": USER_AGENT}), timeout=30) as response:
        actual = hashlib.sha256(response.read(8_000_001)).hexdigest()
    if actual != expected:
        raise ValueError("Source image changed; download and review it again")
    for field in ["title", "credit", "sourceUrl", "licenseName", "licenseUrl"]:
        if not item.get(field):
            raise ValueError("Complete source, credit and rights metadata is required")
    approved = {k: v for k, v in item.items() if k not in ["previewFile", "status"]}
    if approved.get("theme") not in ["ISLAMIC", "SPACE", "NATURE"]:
        raise ValueError("Unknown artwork theme")
    if not approved["sourceUrl"].startswith("https://") or not approved["licenseUrl"].startswith("https://"):
        raise ValueError("Source and license URLs must use HTTPS")
    approved.update(approved=True, reviewedAt=datetime.date.today().isoformat(), reviewer=reviewer)
    feed = json.loads(feed_file.read_text())
    feed["artworks"] = [x for x in feed["artworks"] if x["id"] != id] + [approved]
    feed["updatedAt"] = datetime.date.today().isoformat()
    feed_file.write_text(json.dumps(feed, ensure_ascii=False, indent=2) + "\n")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    collect = commands.add_parser("discover")
    collect.add_argument("--source", choices=["nasa", "picsum", "met", "cleveland", "openverse"], required=True)
    collect.add_argument("--query", default="nebula")
    collect.add_argument("--limit", type=int, default=10)
    collect.add_argument("--page", type=int, default=1)
    collect.add_argument("--output", type=Path, required=True)
    collect.add_argument("--download-previews", action="store_true")
    fetch_preview = commands.add_parser("fetch-preview")
    fetch_preview.add_argument("--candidates", type=Path, required=True)
    fetch_preview.add_argument("--id", required=True)
    fetch_preview.add_argument("--output", type=Path, required=True)
    review = commands.add_parser("approve")
    review.add_argument("--candidates", type=Path, required=True)
    review.add_argument("--id", required=True)
    review.add_argument("--feed", type=Path, required=True)
    review.add_argument("--reviewed-image", type=Path, required=True)
    review.add_argument("--reviewer", required=True)
    review.add_argument("--confirm-no-people-or-animals", action="store_true", required=True)
    review.add_argument("--confirm-reuse-rights", action="store_true", required=True)
    args = parser.parse_args()
    if args.command == "discover":
        if not 1 <= args.limit <= 30:
            parser.error("limit must be 1..30")
        if not 1 <= args.page <= 20:
            parser.error("page must be 1..20")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        items = list(discover(args.source, args.query, args.limit, page=args.page))
        if args.download_previews:
            directory = args.output.parent / "previews"
            directory.mkdir(exist_ok=True)
            for item in items:
                preview(item, directory)
        args.output.write_text(json.dumps(dict(schemaVersion=1, artworks=items), ensure_ascii=False, indent=2) + "\n")
        print(f"Discovered {len(items)} candidates; none published or automatically approved.")
    elif args.command == "fetch-preview":
        item = next(x for x in json.loads(args.candidates.read_text())["artworks"] if x["id"] == args.id).copy()
        expected = item.get("sha256")
        args.output.mkdir(parents=True, exist_ok=True)
        preview(item, args.output)
        if not expected or item["sha256"] != expected:
            parser.error("Image changed since discovery; it must be discovered and reviewed again")
        print("Verified review preview: " + item["previewFile"])
    else:
        approve(args.candidates, args.id, args.feed, args.reviewed_image, args.reviewer)
        print("Reviewed entry added. Publish the feed through the normal repository review process.")

if __name__ == "__main__":
    main()
