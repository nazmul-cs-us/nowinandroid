# Published tafsir languages

Surah Details now has a visible **Tafsir** toolbar control and a Tafsir entry in
Reading settings on Android and iOS; users no longer have to discover an ayah action
menu first. The top-level control opens the current audio ayah on Android, or ayah 1
when no verse is active. The shared reader starts with ayah 1. Individual ayah
actions still open commentary for that exact ayah.

The Android and iOS ayah tafsir readers offer a single book-and-language selector,
including the Arabic sources and published language editions. The selector and
compact header stay visible while the verse and commentary share one scrolling
reading area. The native toolbar uses a simple Tafsir text button. The iOS reader
opens in a modal sheet rather than an inline panel above the ayah list. Selecting an edition changes its title, attribution, source link, chapter
cache and text together. The Quran translation language supplies the default when
there is no explicit tafsir preference. Existing Arabic book choices and Bengali
book key / storage index / offline files remain compatible.

| Language | Published edition | Original catalog |
| --- | --- | --- |
| English | Ibn Kathir (abridged) | [QUL 35](https://qul.tarteel.ai/resources/tafsir/35) |
| Bengali | Ibn Kathir — Tawheed Publication | [QUL 31](https://qul.tarteel.ai/resources/tafsir/31) |
| Hindi | Al-Mukhtasar | [QUL 535](https://qul.tarteel.ai/resources/tafsir/535) |
| Spanish | Al-Mukhtasar | [QUL 268](https://qul.tarteel.ai/resources/tafsir/268) |
| French | Al-Mukhtasar | [QUL 259](https://qul.tarteel.ai/resources/tafsir/259) |
| Indonesian | Al-Mukhtasar | [QUL 260](https://qul.tarteel.ai/resources/tafsir/260) |
| Russian | Ibn Kathir | [QUL 307](https://qul.tarteel.ai/resources/tafsir/307) |
| Turkish | Al-Mukhtasar | [QUL 258](https://qul.tarteel.ai/resources/tafsir/258) |
| Urdu | Ibn Kathir | [QUL 30](https://qul.tarteel.ai/resources/tafsir/30) |
| Chinese | Al-Mukhtasar | [QUL 264](https://qul.tarteel.ai/resources/tafsir/264) |

No verified published Swedish tafsir was found in the checked catalogs. Its menu
entry is explicitly unavailable. The Android reader retains its existing automatic
translation option for Arabic commentary and labels that output accordingly. It
is never presented as a published Swedish edition. Roman transliteration is a
reading aid and is not treated as a tafsir language. Existing Arabic As-Sa'di,
Al-Moyassar, Al-Baghawi and word meanings remain available in their original reader.

## Source and coverage

Source snapshot: `spa5k/tafsir_api`, commit
`311a038bf532fa84566ca1bb2cdbeb210da3d5ed`. Each selected edition has **114 surahs,
6,236 ayah entries and no empty passages**. Indexed chapter files restore the exact
source text, including punctuation, Unicode, paragraph breaks and repeated grouped
commentary. They are not machine-generated translations. English is explicitly
labeled abridged; completeness means source chapter/ayah coverage, not unabridged
coverage of an author's original work.

The initially inspected Indonesian As-Sa'di dataset had one empty ayah, so the
complete Al-Mukhtasar edition is used. The Turkish Ibn Kathir listing appeared to
contain verse translations without commentary in the inspected samples; it was
excluded and its uncataloged staging objects were deleted. Turkish Al-Mukhtasar
contains explanatory commentary and passed the complete coverage audit.

[Integrity and provenance](published-tafsirs-integrity.json) records edition
metadata, pinned source revision, notices, per-chapter source/data hashes and
coverage. The existing Bengali release is documented in
[BENGALI_IBN_KATHIR.md](BENGALI_IBN_KATHIR.md). Attribution is preserved; published
source text is not designated public domain by this integration.

## CDN and offline behavior

Each new edition has a separate immutable prefix and optional `quran_tafsir_XX`
asset category. Opening a reader only looks up saved, verified chapters; it does
not silently download commentary. A missing chapter displays the platform's
standard missing-content asset card, with the selected edition's category.
The card downloads the complete edition (114 chapter files plus its manifest and
notice), then reloads the requested ayah. Failed transfers retain completed files
for retry. Existing cached chapters can still be read while an edition is partial.

**Settings → Content & Storage → Tafsir** lists every published language edition
with its size, availability, download and removal controls. The shared storage UI
also supports per-category cancellation. Downloads use the same category jobs and
SHA-256 verification as other content; the reader has no separate download button.
Only two decoded chapters per repository remain in memory. Changing editions
cancels old reads, resets text/scroll/progress and uses a distinct cache namespace.
Urdu paragraphs use right alignment and content-aware text direction. Fonts use
system glyph fallback for Arabic, Devanagari and CJK; no font downloads are needed.

Publishing stages and publicly checksum-verifies all objects first, then merges
only new optional entries into the live R2 manifest under an origin ETag condition.
Existing assets and the Bengali prefix are preserved. No source corpus is bundled
into the app binary; metadata and integrity manifests are committed for audit.

## Validation

Catalog tests cover language availability, legacy Bengali indexes, unique cache
paths, round-trip preference lookup and invalid addresses. UI checks cover language
switching, source attribution, unavailable Swedish, Hindi/Chinese glyphs, Urdu
layout, and full-screen dialog selection. Existing tests continue to cover Bengali
reading, retries, complete/interrupted offline downloads and stale ayah reads.
CDN contract checks bind every chapter to its verified source checksum. The shared
asset-cache suite validates integrity and offline resolution. Native iOS device
validation still requires macOS/Xcode.

## Spoken tafsir

Loaded commentary includes **Play tafsir**, **Pause / Resume**, and **Stop**. These
read the selected commentary using the device's system TTS in its language; they
are synthesized narration, not a scholar's recorded lesson. Missing assets must
be downloaded before playback becomes available. No audio corpus is downloaded.
A missing or unsupported voice produces an inline message and retry control.

Android lazily initializes TextToSpeech, splits long passages below the engine
input limit, and resumes at the current word (or chunk if an engine has no range
callbacks). It requests speech audio focus and pauses any active Quran recitation.
iOS uses AVSpeechSynthesizer through the existing playback audio session, with a
matching-language voice check and native pause/resume. Its Quran player pauses
before narration. Available voices and offline capability depend on the device.

Speech starts only on a tap. Changing the ayah or edition, closing the reader, or
backgrounding its screen stops narration. Changing or closing the reader releases
its speech player.
The new control is available for both published language editions and Arabic books.
