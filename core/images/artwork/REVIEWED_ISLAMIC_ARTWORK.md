# Home artwork pull and sync

## Runtime contract

Settings control the actual network sync, not rotation over Kotlin image constants.
The app pulls `home-artwork-feed.json` from the published repository's `main` branch
when its selected cadence is due or **Refresh now** is pressed. Resuming catches up
missed deadlines; UI scheduling does not promise background execution at an exact
time while Android/iOS has suspended the app. **Keep current** pauses automatic pulls.

The online image catalog is remote data, not compiled into the APK/framework.
Only service endpoints, supported themes, and security constraints are configuration.
Changing/publishing the approved feed can add new images without an app release.
The bundled, reviewed Alhambra artwork remains a stable offline fallback.

Each sync fetches the remote feed, selects five unseen entries for the chosen
Islamic/Space/Nature theme, downloads and verifies the exact approved image bytes,
and atomically publishes the set only after every image succeeds. Feed/network/disk
failures retain the last-good set. Successfully downloaded images and their approved
catalog are stored privately for offline use. Legacy cached images require validation
against the new feed before reuse. No new approved entries means a clear error and
the current set stays; there is no silent shuffle or unrestricted random-photo fallback.
**Refresh now** bypasses the retry delay and HTTP cache to check the latest feed,
but never substitutes previously viewed images for new artwork. Explicitly changing
the theme may revisit that theme's reviewed collection; this is labelled separately.

### Restricted mode and personal preview gallery

Settings now has a **Restricted mode** switch, on by default. Turning it off first
asks for explicit consent: unreviewed images may contain people or animals and their
reuse rights have not been verified. Confirmation opens a separate manual-review
gallery and fetches candidates directly from Picsum (Nature), NASA (Space), or the
Cleveland open-access API (Islamic). This does not wait for a GitHub feed update.
**Fetch more** searches a different provider result page rather than shuffling the
Home cache. Known published IDs and already viewed preview IDs/hashes are skipped.

The gallery offers full-image, source and rights links and a dismiss action. It
does **not** approve, publish, or automatically apply images to Home. Manual content
and rights review plus the publication workflow below are still required for Home.
Switching **Restrict** back on cancels the request and clears the in-memory gallery.
There are no scheduled preview fetches and no unreviewed writes to Home's offline
collection. Previews are bounded to five images, resized to at most 720 pixels on
their longest side; the original image remains available through the full-image link.
Downloads use a separate HTTPS endpoint/redirect allowlist and byte/dimension limits.
Android preview-network diagnostics use the `ArtworkReview` logcat tag.

## Providers and rights

The initial remote feed contains 25 reviewed Islamic images (The Met/Cleveland),
five NASA Image Library images, and five fixed-ID Picsum nature photographs.
Exact images were visually reviewed, including borders, for people/animal figures.
Picsum image 37 was excluded because a distant object could not be confidently ruled out.
Only selected, reviewed image bytes are pinned; metadata keyword filters are not proof
of image content. The seed catalog is intentionally finite, but it can now expand remotely.

The Met/Cleveland use CC0 for the selected images. NASA images retain original
institutional credits and link to NASA's media usage guidelines; they are **not**
labelled CC0 and no NASA endorsement is implied. NASA-branded/third-party-marked
posters were not selected. Picsum photographs retain the creator, original Unsplash
record and Unsplash License. This does not use the Unsplash developer API.

APOD/Unsplash/Pexels/Pixabay require configured credentials and API-terms compliance.
They are disabled, not represented as working integrations. No demo or private keys
are embedded. ESA/Hubble/Webb require per-image licensing review before connection.
Openverse discovery supports CC0/Public Domain Mark candidates, which still require
rights/content review. Placehold.co is a development utility, not a production feed.

## Discover, review, publish

`tools/artwork_feed.py` discovers candidates from NASA Image Library, Picsum, The Met,
and Openverse. Example (use temporary output, not repository screenshots):

```bash
python tools/artwork_feed.py discover --source nasa --query nebula --limit 10 \
  --output "$TMPDIR/artwork-review/nasa.json" --download-previews
```

Discovery **never** sets `approved: true`. Inspect the full image and source rights.
Reject figures, uncertain imagery, misleading branding and unresolved copyright.
Choose a suitable crop that avoids photographic frames and sacred inscriptions.
Then explicitly approve the exact reviewed file:

```bash
python tools/artwork_feed.py approve --candidates "$TMPDIR/artwork-review/nasa.json" \
  --id nasa-REVIEWED_ID --reviewed-image "$TMPDIR/artwork-review/previews/nasa-REVIEWED_ID.jpg" \
  --feed core/images/artwork/home-artwork-feed.json --reviewer YOUR_NAME \
  --confirm-no-people-or-animals --confirm-reuse-rights
```

Approval checks that the source still matches the reviewed bytes. Review/commit/publish
the feed normally.

## Automatic discovery and review queue

`.github/workflows/ArtworkCandidates.yaml` runs daily at 03:17 UTC and supports
**Run workflow**. It discovers candidates from NASA (Space), The Met/Cleveland (Islamic), and
Openverse/Picsum (Nature). Queries and result pages change each day, rather than
repeatedly scanning the same first page. Published images, already queued IDs,
duplicate image hashes and `rejected-artwork-ids.json` are excluded.

The workflow downloads bounded, dimension-checked previews and produces a
`artwork-review-candidates` artifact with `review.html`, `candidates.json`, previews
and `summary.json`. Provider failures are reported individually; a working provider
can still contribute when another is unavailable. Open the HTML after extracting
the artifact. Inspect each full-size preview, source record, attribution and rights.
Keyword queries are **not** a content-safety guarantee.

Pending metadata survives subsequent runs through the GitHub Actions cache; it is
not written to the repository. The queue stops growing at 300 pending entries until
reviews free space. Cache eviction may rediscover unreviewed entries, but published
and rejected entries are still excluded. Artifacts are retained for 30 days. For a
pending candidate whose preview was in an older artifact, download its exact bytes:

```bash
python tools/artwork_feed.py fetch-preview --candidates candidates.json \
  --id CANDIDATE_ID --output "$TMPDIR/artwork-review/previews"
```

This verifies the original discovery hash; changed source bytes require discovery
and review again. Approve using the existing `approve` command with the downloaded
`candidates.json`, reviewed file, reviewer name and both explicit confirmations.
To reject a candidate permanently, add its ID to `rejected-artwork-ids.json`.

For a local discovery run, install Pillow 12.3.0 in an isolated Python environment:

```bash
python tools/discover_artwork_queue.py \
  --feed core/images/artwork/home-artwork-feed.json \
  --rejected core/images/artwork/rejected-artwork-ids.json \
  --state "$TMPDIR/artwork-review/pending.json" \
  --output "$TMPDIR/artwork-review/current"
```

Discovery never approves, commits or publishes images. New images become available
to the app only after explicit review and publication of the approved feed. Publishing
this workflow to the default branch is required to activate the scheduled discovery;
until reviews are published, the app correctly reports that no new images are available.
Feed publication is required before a new app can use this endpoint. No Family VM
module deployment or restart is needed for artwork updates.
