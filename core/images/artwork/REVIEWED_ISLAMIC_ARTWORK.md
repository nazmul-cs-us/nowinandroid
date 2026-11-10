# Reviewed online Home artwork

Home no longer uses live Getty search. Its online collection is an allowlist of
20 photographs from **The Metropolitan Museum of Art, Islamic Art department**.
Each object was checked through the museum API for `isPublicDomain: true`, and
each full source photograph was visually reviewed on 2026-11-10 for geometric
or botanical decoration without human or animal figures. Museum object titles
are usually "Tile" or "Tile Panel"; the app uses descriptive display labels.

Registry: `src/commonMain/kotlin/com/starception/submission/core/images/ReviewedIslamicArtwork.kt`.
Licensing: https://www.metmuseum.org/about-the-met/policies-and-documents/open-access
API documentation: https://metmuseum.github.io/

## Review gate

1. Verify the object is in Islamic Art and explicitly public domain. Do not treat
   a keyword, department, or metadata tag as proof that no figures are present.
2. Inspect the complete photograph at full size, including small border motifs.
   Reject humans, animals, figurative scenes, and uncertain imagery.
3. Select a crop that removes museum mounts, frames, and photographic backgrounds.
   Avoid cropped sacred inscriptions; prefer geometric and botanical patterns.
4. Record its object link, exact HTTPS image URL, and SHA-256 of the reviewed bytes.
   Downloads and restored caches must match that hash. Changed photos require review.
5. Extend the catalog tests and verify both Android and iOS builds.

## Runtime behaviour

- Refresh downloads five unseen reviewed images; it never silently repeats them.
- A complete collection is published only after all five verified downloads succeed.
- When fewer than five unseen reviewed entries remain, the current collection stays
  and Settings explains that the reviewed collection is exhausted. More entries
  require review and an app update; there is no unrestricted search fallback.
- Legacy Getty search caches are not displayed. The existing reviewed, bundled
  Alhambra architecture remains the offline fallback until a new set downloads.
- Review images and contact sheets are temporary working files, not repository assets.
