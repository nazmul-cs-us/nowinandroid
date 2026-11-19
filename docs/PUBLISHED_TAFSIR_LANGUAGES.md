# Published tafsir languages

Surah Details has a **Tafsir** toolbar control and a Reading settings entry on
Android and iOS. They enable inline commentary beneath each Arabic ayah and its
translation in the existing surah list. No separate tafsir dialog opens. The ayah
action also enables inline commentary at that verse. **Hide tafsir** restores the
normal reader, including the user's saved Mushaf preference; enabling tafsir does
not overwrite that preference.

One book/language selector above the first ayah changes the inline edition across
the surah. Chapters use the same verified offline repository; only visible lazy
items request their commentary. Each ayah has its own label and Play/Pause control.
A narration session prevents two ayahs speaking together. All commentary shares
the Surah Details scroll container, without nested scrolling. Missing-content cards
remain inline and a finished download refreshes the visible verses. Word meanings
retain their separate study UI.

The Quran translation language supplies the default when there is no explicit
tafsir preference. Existing Arabic book choices remain compatible. Retired Bengali Ibn Kathir
selections migrate to Abu Bakr Zakaria. Sources that explain several ayahs in one
passage retain that passage for each associated ayah; this UI does not invent
ayah-specific divisions of the author's text.

| Language | Published edition | Original catalog |
| --- | --- | --- |
| English | Ibn Kathir (abridged) | [QUL 35](https://qul.tarteel.ai/resources/tafsir/35) |
| Bengali (default) | Abu Bakr Zakaria — King Fahd Quran Printing Complex | [QUL 33](https://qul.tarteel.ai/resources/tafsir/33) |
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

## Full-surah narration

The inline book selector includes **Play full surah tafsir**. It reads the selected
surah, from ayah 1 to its last ayah, using the selected edition. This is a current-
surah queue, not automatic playback of all 114 surahs. It preloads that surah's
verified saved commentary and shows the normal asset card if content is missing.
It does not silently download anything or begin speaking after a download.

Pause / Resume and Stop control the queue, with the current ayah or grouped ayah
range displayed. Consecutive ayahs sharing identical commentary are spoken once,
with their source range retained. Each next passage starts only after confirmed
speech completion. Voice errors and interruptions stop the queue. Pausing at the
end of a passage also pauses before the next passage.

The full-surah queue belongs to the app. Leaving Surah Details, hiding the inline
controls, or backgrounding the app keeps narration running. The PullToSync media
banner shows the surah, tafsir language and current ayah range, with Pause/Resume;
system media controls use the same queue. Returning to the same surah and edition
reattaches to the active player. Visiting another book does not start it or replace
the current audio; pressing Play does. Starting individual-ayah speech or another
media source stops the full-surah queue. Explicit Stop, completion and errors clear
the banner. Individual-ayah speech retains its page-owned lifetime.

The full-surah player uses a theme-colored reading card with the surah name,
ready/listening/paused status, ayah-based progress and a prominent playback action.
The progress bar tracks completed ayah ranges, not an estimated duration. Loading
uses an indeterminate indicator. Controls adapt to narrow screens and larger text;
no decorative waveform or invented playback time is shown.

## Ayah scrolling and guidance

Inline tafsir gently settles near ayah boundaries after a swipe. Inside a long
explanation, scrolling remains free: the reader does not snap back to the start
or skip the rest of the commentary. A brief hint appears on the first manual
scroll, explaining how to continue reading a long ayah or move between ayahs.

Full-surah narration follows the current ayah and highlights its reading group;
a shared explanation highlights all ayahs in its range. Follow positioning keeps
the Arabic ayah below the toolbar and skips the selector/player above ayah 1.
Manual scrolling pauses auto-follow, not speech, and displays **Follow ayah**.
That action returns to the narrated ayah and re-enables following. Stop, completion,
changing the surah/book, or hiding inline tafsir clears the active highlight.
Touch dragging, pointer-wheel scrolling, and Android's scrollbar use this guidance;
programmatic scrolling does not trigger a manual-scroll hint.

## Bengali honorific narration

Speech input expands common Bengali honorific abbreviations before reaching the
Android or iOS system voice. This applies to individual ayahs and the full-surah
queue. Source files, checksums, grouping and displayed wording remain unchanged.

| Source abbreviation | Spoken Bengali |
| --- | --- |
| রাঃ / (রা.) / (র.) | রাদিয়াল্লাহু আনহু |
| রঃ / রহঃ | রহমাতুল্লাহি আলাইহি |
| সঃ / সাঃ | সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম |
| আঃ | আলাইহিস সালাম |

The companion abbreviation uses the requested Arabic phrase in Bengali script.
The other abbreviations use their own full Arabic phrases in Bengali script.
In this edition, রঃ/রহঃ occurs after scholars and is kept distinct from companions’ রাঃ.
Already written full forms, including আনহা and আনহুম, are preserved. Known honorific symbols
receive equivalent expansions.
Embedded abbreviations such as `(হে নবী সঃ!)` are also handled. Numeric references,
ordinary words, initials, and `(আরবি)` placeholders are preserved. Other narration
languages are unaffected. Android splits the expanded speech into utterances so
chunk limits and pause/resume offsets apply to what is actually spoken.

## First-run content setup

The selected reading language determines both its offline Quran translation and
its matching published tafsir. The matching edition is required during first-run
setup, including all 114 chapters, manifest and attribution notice. Setup checks
those files before reporting completion, retries incomplete downloads and avoids
redownloading cached files. Languages without a published edition add no extra
book; Arabic commentary remains part of Quran core content. Existing users who
already finished setup can still download editions through Content & Storage.

Bengali source quality, Arabic quotation coverage and compatibility details are in [BENGALI_TAFSIR_SOURCES.md](BENGALI_TAFSIR_SOURCES.md).

Surah Details groups each Arabic ayah with its translation and tafsir. The reading
column stays at most 680 dp wide on tablets. Each explanation shows up to five
lines, with **Read full tafsir** and **Show less** for longer passages. During
full-surah playback the current passage opens automatically. The original source
paragraphs, mixed scripts and reader font scaling apply in both views.

The edition picker, source credit and full-surah playback controls appear together
at the beginning. Each ayah also has its own listening control. Opening tafsir
shows the available translation. Closing tafsir restores the saved translation
preference.
