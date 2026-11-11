#!/usr/bin/env python3
"""Find new Internet artwork for a persistent, unapproved content/rights review queue.

Provider results never enter the public feed automatically. Images and review HTML
belong in temporary storage/CI artifacts, not Git. Only metadata persists between runs.
"""
import argparse
import datetime
import html
import json
from pathlib import Path
from urllib.parse import urlparse

import artwork_feed

SEARCHES = {
    "nasa": ("nebula", "galaxy", "planet surface", "star cluster", "lunar landscape"),
    "openverse": ("mountain landscape", "waterfall forest", "desert dunes", "ocean landscape", "autumn forest"),
    "met": ("tile", "geometric ornament", "floral panel", "carpet", "architectural ornament"),
    "cleveland": ("tile", "geometric", "floral", "carpet", "textile"),
    "picsum": ("nature",),
}


def validate_candidate(item):
    identifier = item["id"]
    if not 1 <= len(identifier) <= 80 or not all(c.isascii() and (c.isalnum() or c in "_-") for c in identifier):
        raise ValueError("Unsafe candidate identifier")
    if item["theme"] not in {"ISLAMIC", "SPACE", "NATURE"}:
        raise ValueError("Unsupported theme")
    for field in ("title", "credit", "sourceUrl", "licenseUrl", "licenseName"):
        if not isinstance(item[field], str) or not 1 <= len(item[field]) <= 2000:
            raise ValueError("Missing or oversized " + field)
    for field in ("sourceUrl", "licenseUrl"):
        url = urlparse(item[field])
        if url.scheme != "https" or not url.hostname or url.username or url.password:
            raise ValueError("Unsafe source or license URL")
    if not item["imageUrl"].startswith(artwork_feed.IMAGE_PREFIXES + ("https://picsum.photos/id/",)):
        raise ValueError("Image service is not enabled")


def search_for_day(source, day):
    """Rotate queries AND pages instead of returning the same first ten results forever."""
    index = day.toordinal()
    queries = SEARCHES[source]
    return queries[index % len(queries)], 1 + (index // len(queries)) % 10


def validate_preview(item):
    from PIL import Image
    with Image.open(item["previewFile"]) as image:
        if not all(100 <= size <= 1800 for size in image.size):
            raise ValueError("Preview is outside the app's supported dimensions")
        image.verify()


def run_discovery(feed, state, output, day, limit=10, discover=artwork_feed.discover,
                  preview=artwork_feed.preview, validate=validate_preview, max_queue=300, rejected_ids=()):
    output.mkdir(parents=True, exist_ok=True)
    preview_dir = output / "previews"
    preview_dir.mkdir(exist_ok=True)
    published_ids = {row["id"] for row in feed["artworks"]}
    excluded_ids = published_ids | set(rejected_ids)
    published_hashes = {row.get("sha256") for row in feed["artworks"]}
    queue = []
    for row in state.get("artworks", []):
        validate_candidate(row)
        if row["id"] not in excluded_ids:
            item = dict(row, approved=False, status="pending-content-and-rights-review")
            item["previewFile"] = "previews/" + item["id"] + ".jpg"
            queue.append(item)
    known_ids = excluded_ids | {row["id"] for row in queue}
    known_hashes = published_hashes | {row.get("sha256") for row in queue}
    added, failures, providers = [], [], []
    for source in SEARCHES:
        if len(queue) >= max_queue:
            break
        query, page = search_for_day(source, day)
        try:
            # Bound even a provider implementation that ignores its requested limit.
            from itertools import islice
            rows = list(islice(discover(source, query, limit, page=page), limit))
            providers.append(dict(source=source, query=query, page=page, returned=len(rows)))
        except Exception as error:
            failures.append(dict(source=source, stage="metadata", error=str(error)))
            continue
        for row in rows:
            if row["id"] in known_ids or len(queue) >= max_queue:
                continue
            item = dict(row)
            # Explicitly override any approval supplied by a provider or a previous artifact.
            item.update(approved=False, status="pending-content-and-rights-review")
            try:
                validate_candidate(item)
                preview(item, preview_dir)
                validate(item)
                digest = item["sha256"]
                if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                    raise ValueError("Invalid preview hash")
                if item["sha256"] in known_hashes:
                    continue
                item["previewFile"] = "previews/" + Path(item["previewFile"]).name
                item["discoveredAt"] = day.isoformat()
            except Exception as error:
                failures.append(dict(source=source, id=item["id"], stage="preview", error=str(error)))
                continue
            queue.append(item)
            added.append(item)
            known_ids.add(item["id"])
            known_hashes.add(item["sha256"])
    result = dict(schemaVersion=1, discoveredAt=day.isoformat(), artworks=queue)
    summary = dict(newCandidates=len(added), pendingCandidates=len(queue), providers=providers,
                   failures=failures, queueFull=len(queue) >= max_queue)
    (output / "candidates.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    write_report(output, queue, summary)
    return result, summary


def write_report(output, queue, summary):
    escape = html.escape
    cards = []
    for row in queue:
        preview_path = output / row["previewFile"]
        image = (f'<a href="{escape(row["previewFile"], quote=True)}"><img width="300" '
                 f'src="{escape(row["previewFile"], quote=True)}" alt="Candidate preview"></a>'
                 if preview_path.is_file() else "<p>Preview from an earlier run; re-download and verify its hash before approval.</p>")
        cards.append(f'<section><h2>{escape(row["title"])}</h2><p>{escape(row["id"])} · '
                     f'{escape(row["theme"])} · PENDING REVIEW</p>{image}'
                     f'<p>{escape(row["credit"])}</p><p><a href="{escape(row["sourceUrl"], quote=True)}">Source record</a> · '
                     f'<a href="{escape(row["licenseUrl"], quote=True)}">{escape(row["licenseName"])}</a></p>'
                     f'<p>SHA-256: <code>{escape(row["sha256"])}</code></p></section>')
    report = ('<!doctype html><html lang="en"><meta charset="utf-8"><title>Artwork review queue</title>'
              '<body><h1>Artwork review queue</h1><p>Nothing here is approved or published.</p>'
              '<p>Inspect the full image, including borders, for people and animals. Verify credits, '
              'reuse rights and a suitable crop. Discovery is not proof of safe image content.</p>'
              f'<p>{summary["newCandidates"]} new candidates; {summary["pendingCandidates"]} awaiting review; '
              f'{len(summary["failures"])} provider/preview errors. See summary.json for details.</p>'
              + "\n".join(cards) + '</body></html>')
    (output / "review.html").write_text(report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--feed", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--rejected", type=Path)
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--day", type=datetime.date.fromisoformat, default=datetime.date.today())
    args = parser.parse_args()
    if not 1 <= args.limit <= 30:
        parser.error("limit must be 1..30")
    state = json.loads(args.state.read_text()) if args.state.exists() else {}
    rejected = json.loads(args.rejected.read_text())["ids"] if args.rejected else []
    result, summary = run_discovery(json.loads(args.feed.read_text()), state, args.output, args.day, args.limit,
                                    rejected_ids=rejected)
    args.state.parent.mkdir(parents=True, exist_ok=True)
    staging = args.state.with_suffix(".tmp")
    staging.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    staging.replace(args.state)
    print(json.dumps(summary, ensure_ascii=False))
    if not summary["providers"] and summary["failures"]:
        raise SystemExit("All discovery providers failed; no new candidates were available for review")


if __name__ == "__main__":
    main()
