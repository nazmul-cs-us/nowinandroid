# Published Bengali Tafsir Ibn Kathir

Edition: **Bengali Tafsir Ibn Kathir, attributed to Tawheed Publication**.

- Original catalog: https://qul.tarteel.ai/resources/tafsir/31
- Quran.com edition ID: **164**. QUL/mirror edition ID: **31**. These are different ID systems.
- JSON mirror: https://github.com/spa5k/tafsir_api/tree/311a038bf532fa84566ca1bb2cdbeb210da3d5ed/tafsir/bn-tafseer-ibn-e-kaseer
- Source commit: `311a038bf532fa84566ca1bb2cdbeb210da3d5ed`.
- Verified coverage: **114 surahs / 6,236 ayah entries**, no empty text entries.

## CDN and offline access

The original downloaded SQLite file is published at
`databases/quran/bengali_ibn_kathir.sqlite` on the existing public R2 CDN.
It is a locally generated SQLite copy of the mirror, not QUL's sign-in-protected export.

The app uses the lossless chapter representation at
`tafsir/bn-ibn-kathir/v1/1.json` through `114.json`. Identical commentary passages are
stored once within each chapter, indexed by ayah. All decoded text is identical
to the source SQLite corpus. This reduces the complete app-download payload from
100 MB to about 25 MB, without rewriting, translating, stripping or correcting text.
See [the corpus manifest](bengali-ibn-kathir-manifest.json) for per-file checksums.

The optional asset category is `quran_tafsir_bn`. Existing Android/iOS asset-cache
adapters validate size and SHA-256 before exposing a file, commit downloads
atomically, and reuse persistent copies offline. Opening an ayah fetches only
its chapter. “Download full tafsir for offline reading” saves all 114 chapters;
interrupted downloads retain completed files and can resume by reusing them.
The reader keeps only two decoded chapters in memory.

Android exposes “Ibn Kathir · বাংলা” in the ayah tafsir sheet and full-screen
tafsir dialog. Selecting Bengali as the tafsir language chooses this published
book, rather than translating one of the Arabic commentaries. Selecting an
Arabic book restores Arabic. On iOS the same book is available in the tafsir
sheet, and Bengali readers open it by default unless they explicitly chose another book.
That choice persists across reader sessions. Arabic word meanings remain a
separate choice. A missing Arabic enhanced database does not gate Bengali text.

The shared reader shows the edition and publisher and links to QUL. Load errors
show a retry action, never Arabic or another commentary mislabeled as Bengali.
Switching ayahs cancels old loading work and resets text scroll position.

## Reproducing the payload

Use the verified source SQLite download (available from the preceding research
artifact), then run:

```bash
python3 tools/package_bengali_ibn_kathir.py /path/to/bengali-ibn-kathir.sqlite \
  --output /path/to/staged-cdn-corpus
```

The packager compares every decoded ayah text against the source database,
checks the 114 chapter counts / 6,236 keys, and emits hashes and source attribution.
The source may repeat the same group commentary for adjacent ayahs; preserving
that behavior is intentional. Coverage validation is not a new editorial review
of the published text. Publisher attribution does not imply the underlying text
is public domain.

## Validation

Repository tests exercise grouped ayah mapping, Unicode preservation, bad
addresses, wrong chapters, missing/invalid indexes, blank passages, retry,
bounded caching, complete downloads and interrupted progress. UI checks cover
publisher/edition labels, Bengali rendering, full-book download and switching
away from an in-flight ayah. The existing asset-cache suite covers integrity and
offline resolution. A native iOS build requires macOS/Xcode.

## Published files

[CDN verification report](bengali-ibn-kathir-cdn.json) records the live URLs, sizes and hashes.

Full SQLite download: https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev/databases/quran/bengali_ibn_kathir.sqlite
