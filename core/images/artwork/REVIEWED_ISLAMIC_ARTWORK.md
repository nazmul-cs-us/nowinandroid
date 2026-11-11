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
the feed normally. The daily GitHub workflow discovers NASA metadata into a temporary
review artifact only; it does not auto-approve images or commit new screenshots.
Feed publication is required before a new app can use this endpoint. No Family VM
module deployment or restart is needed for artwork updates.
