# Reciter catalog and portraits offline

Android and iOS keep the last valid reciter catalog in persistent app storage.
After a successful fetch, opening the picker or searching reciters reuses that
catalog for 24 hours. The next request after that window refreshes it. Retry
bypasses the freshness window. Concurrent requests share a single refresh.

An unavailable service, empty response or malformed response does not replace the
last good catalog. Existing installations reuse their saved catalog; without a
saved freshness timestamp they check the service once and keep the saved list if
the check fails. Selected recording IDs and downloaded audio are unchanged.

Storage:

- Android: `quran_reciter` preferences, `catalog_json` and `catalog_updated_at`.
- iOS: the existing key/value store, `quran_reciter_catalog` and
  `quran_reciter_catalog_updated_at`.

Portraits for seven reciters ship in `core/images` as small WebP resources. They
are available offline immediately, survive restarts and normal cache cleanup, and
need no network fetch. Other names show initials. Portrait source credits and
coverage are recorded in [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md).

This caches names, recording metadata and portrait availability. Recitation audio
continues to use the explicit download controls and its existing offline storage;
loading the catalog does not download every reciter's audio.

## Verification

`ReciterCatalogCacheTest` checks persistence across cache recreation, the 24-hour
window, forced refresh, offline/invalid-response fallback, clock changes,
concurrent requests and cancellation. Existing reciter UI tests check that the
cached list stays selectable offline and bundled portraits are rendered.

On iOS, fetch the catalog once, quit the app, disconnect the network, and reopen
the picker and search. Names and bundled portraits should remain visible. A new
installation with no previously fetched catalog still needs a first connection.
