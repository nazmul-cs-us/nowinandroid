# Hadith source-integrity containment

## Confirmed defect (2026-11-27)

The app displayed the narration beginning “Narrated Malik bin Huwairith: Two men
came to the Prophet … with the intention of a journey” with the citation “Sahih
al-Bukhari 615”.

Evidence checked independently:

- `https://sunnah.com/bukhari:630` contains this narration.
- `https://sunnah.com/bukhari:615` instead contains Abu Huraira's narration about
  the rewards for the Adhan, first row, and congregational prayers.
- The app's source,
  `https://raw.githubusercontent.com/AhmedBaset/hadith-json/v1.2.0/db/by_book/the_9_books/bukhari.json`,
  assigns `id = 615` and `idInBook = 615` to the first narration. These are NOT a
  verified Sunnah.com numbering scheme. Merely switching to `idInBook` does not fix it.

Other collection builders explicitly assign sequential IDs by array position.
There is no safe universal numeric offset or inferred mapping.

## Identity rules

1. A local target consists of **collection database + local record ID**.
2. Local record IDs must be identified as **Local record**, not published hadith numbers.
3. Reader text, translated text, excerpts and synthesized speech must derive from
   that same row. Explanation text is not a substitute for a missing narration.
4. A publisher citation is a separate identity. It may not open an equal-numbered
   local record without a separately verified crosswalk.
5. A number in an audio filename does not verify the narration's identity.
6. Cached text/reference/target associations from before this correction must be
   regenerated, not cosmetically relabeled.

`HadithSourceIdentity` owns the shared local-reference policy. Public reference
numbers are intentionally withheld where their mapping has not been verified.
**“Local record 615” is not a claim that Bukhari's published hadith number is 615.**

## Audited surfaces and containment

| Surface | Defect or risk | Containment |
| --- | --- | --- |
| Android Home, prayer recommendations, search, widgets | Database IDs shown as publisher numbers | Explicit local references |
| Shared/iOS Home, search, book/detail readers, Now Playing | Same assumption | Explicit local references |
| Android detail, courses, activity-triggered and driving playback | Legacy JSON indexed by `Number` alone, which restarts between volumes/books | Compatibility facade reads English from the exact database row; no legacy JSON fallback |
| Feed generators (Kotlin and Python) | Legacy JSON quotation linked to an unrelated equal-numbered database row | Text and local link come from the same database row |
| Old generated/downloaded feed content | Invalid associations could survive an upgrade | Old system `hadith://` links and quotations quarantined before reading; new links use `hadith-local://`; new Bukhari feed IDs cannot retarget legacy bookmarks |
| Fortress external references | Publisher number treated as local row ID | Preserve source citation, withhold unverified local navigation |
| Bengali Bukhari recordings | CDN/SD-card numeric filenames used as identity | Refuse association and on-demand download; use source-text narration fallback |
| Reader swipes and asynchronous translation | Previous record's translated text could appear under a new record | Record-keyed translation state |
| Generic Android hadith TTS | Bukhari introduction used for other collections | Actual collection passed to both TTS renderers |
| Audio-engine asynchronous synthesis | Stopped record A could finish after record B reset a shared stop flag | Monotonic request tokens checked atomically at AudioTrack start, including deferred travel callbacks; stale workers cannot clear the successor |
| TTS persisted/memory cache | 32-bit text hashes could collide; voice/speaker/speed omitted | SHA-256 over unambiguously encoded text and synthesis identity; separate v2 cache directory, no legacy-cache fallback, atomic disk writes |
| Android/iOS persisted Home/read-ahead content | Incorrect old references could be restored | Versioned cache namespaces; Android candidate decoder rejects old versions |
| Training-data generation | Database IDs described as published references | Local-record references for database-derived examples |

## Important limits / release gates

This change is **application-level containment, not certification of every
narration, translation, source asset, or publisher number**.

- A complete canonical crosswalk still needs independent, record-by-record
  validation against named editions, including repeated narrations and suffixes.
  It must bind collection, dataset revision/hash, record ID, exact text hashes,
  numbering scheme and publisher reference. Ambiguous matches must stay unavailable.
- Existing remote/bundled corpora and trained model binaries are not rewritten or
  republished here. Runtime model answers must remain extractive and use the
  caller-supplied source identity, never a model-invented citation.
- Authored external citations (for example the starter quiz's Bukhari 8) remain
  external citations, not internal row lookups. The change does not authenticate
  all historical training examples or the third-party Arabic quiz bank.
- Changing a dataset's row identities in place requires content-version changes
  and cache invalidation. A filename, row count, or successful download checksum
  alone does not prove semantic alignment with an external edition.
- Unverified recordings and external-reference navigation remain unavailable
  until their mappings have been reviewed. Downloaded user files are not deleted.
- An Android/iOS build, regression suite, and device checks must pass before this
  work can be represented as released. Do not claim all-app integrity from a
  single corrected screenshot.

## Regression checks

- `HadithSourceIdentityTest`: record 615 is not presented as published Bukhari 615;
  local labels apply to every registered collection; no unverified recording ID.
- `HadithSourceIntegrityTest`: reader/playback/feed agree on the same row;
  ambiguous legacy JSON does not override it; external reference 630 cannot open
  local row 630; old system feed text/links are quarantined on reopening.
- `HadithCacheIntegrityTest`: old cached quote/target pairs are rejected.
- `HadithRecordingIntegrityTest`: an existing numbered download is insufficient
  authorization to play it or fetch its replacement.
- `HadithLinkIntegrityTest`: only explicit, registered local targets are accepted.
- `HadithSpeechRequestGateTest`: stopped/superseded synthesis and delayed callbacks
  cannot play or clear a successor; accepted playback is not owned by the reader's scope.
- `HadithTtsCacheIdentityTest`: deliberately colliding Java string hashes cannot
  select each other's audio; synthesis parameters are part of cache identity.
- iOS `HadithFeedIntegrityTest`: real legacy SQLite feed rows cannot leak through
  topic pages, search, direct reads, or bookmark reads.
- `test_hadith_source_integrity.py`: offline feed generator preserves row text,
  local ID, and target together.

Retain existing unrelated tests. Do not regenerate screenshot baselines to hide
source-integrity failures.

## Local verification on this change

- Passed: `python3 -m unittest discover -s scripts/tests` (9 tests).
- Passed: `python3 -m unittest discover -s training/quiz_model -p 'test_*.py'` (41 tests).
- Passed: actual pinned upstream Bukhari JSON through the collection builder and
  corrected Python feed builder, preserving record 615's text and local target.
- Passed: all 7,277 pinned Bukhari source rows agree with the app's 97 local
  book/chapter ranges. This is internal categorization, NOT publisher-number validation.
- Passed: `:core:model:testDebugUnitTest`, `:core:ui:testDemoDebugUnitTest`, and
  targeted `:app:testDemoDebugUnitTest` regressions (Hadith, ChapterRecitation,
  GlobalMedia, EnglishTtsTextNormalizer, HomePageRecommendationStore, HomeScreenBugFix).
- Passed: `:shared:iosSimulatorArm64Test`, including the real legacy-feed SQLite test.
- Passed: independent plan and code reviews; `git diff --check`.
- The combined APK/lint attempt exhausted local disk space during DEX generation.
  A separate lint attempt also exposes a pre-existing included-build mismatch:
  `:compose-cupertino:cupertino-adaptive` uses `compileSdk 34`, while its AndroidX
  dependencies require API 35 or later. That third-party build configuration was
  not changed as part of this integrity fix. APK/lint verification is not green.
  No APK has been installed, and no on-device/native-model audio playback is
  claimed by these tests. The request-token tests verify scheduling/identity
  policy, not the semantic contents of an audio waveform.
- No content CDN, remote model, remote repository branch, or release has been
  published or changed by this local work.
