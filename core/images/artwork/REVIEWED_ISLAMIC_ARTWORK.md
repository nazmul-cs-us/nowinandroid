# Reviewed online Home artwork

Home no longer uses live Getty search. Its online collection is an allowlist of
25 photographs: 20 from **The Metropolitan Museum of Art, Islamic Art department**
and five from **Cleveland Museum of Art** (Ottoman floral tiles, lotus tilework,
and geometric and floral carpets).
The Met's `isPublicDomain: true` and Cleveland's `share_license_status: CC0` were
checked through the museum APIs. The Met photographs were reviewed on 2026-11-10 for geometric
or botanical decoration without human or animal figures. Museum object titles
are usually "Tile" or "Tile Panel"; the app uses descriptive display labels.

Registry: `src/commonMain/kotlin/com/starception/submission/core/images/ReviewedIslamicArtwork.kt`.
The Cleveland additions are in `ReviewedClevelandArtwork.kt`, visually reviewed on
2026-11-11. Public-domain status was checked against the museum API and object pages.
The official website image proxy is used because direct Open Access CDN downloads
returned HTTP 403 from the local client. Only exact pinned proxy URLs are allowed;
all source bytes still pass the same hash review gate. No remote proxy input is accepted.
Licensing: https://www.metmuseum.org/about-the-met/policies-and-documents/open-access
API documentation: https://metmuseum.github.io/

## Review gate

1. Verify Islamic artistic/cultural provenance and explicit public-domain status. Cleveland
   classifies some carpets under Textiles rather than Islamic Art. Do not treat
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
