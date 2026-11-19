# Bengali tafsir sources

## Why Ibn Kathir is missing Arabic quotations

The Tawheed-attributed Bengali Ibn Kathir export (QUL 31 / Quran.com 164)
already contains `(আরবি)` / `(আরবী)` instead of many Arabic quotations. The app
preserves its source text; a font change cannot restore quotations absent from
the dataset. The current source audit found 10,112 placeholders across 1,474 of
1,914 distinct passages. Quran.com's live API and Quran Mazid's Ibn Kathir
edition reproduce the omissions.

## Added alternative: Tafsir Abu Bakr Zakaria

- Author: Dr. Abu Bakr Muhammad Zakaria.
- Catalog attribution: King Fahd Quran Printing Complex.
- Source: https://qul.tarteel.ai/resources/tafsir/33
- Live comparison: https://api.quran.com/api/v4/tafsirs/166/by_ayah/1:1
- Mirror: https://github.com/spa5k/tafsir_api, pinned revision
  `311a038bf532fa84566ca1bb2cdbeb210da3d5ed`.
- Complete address coverage: 114 surahs / 6,236 ayah entries.
- 29,458 Arabic letters in distinct chapter passages; paragraphs, Arabic,
  punctuation and repeated source groupings are preserved losslessly.
- Source limitations: five Arabic placeholders remain at 5:100 (two), 6:38,
  13:38 and 16:125. They are retained, not reconstructed. Coverage does not
  imply that every source quotation is complete.
- QUL's licence page says it has no copyright information for this resource.
  Attribution and the original source are included in the downloadable NOTICE;
  the app does not label the underlying text public domain.

Zakaria is the Bengali commentary available in the app. The incomplete Bengali
Ibn Kathir option and download category have been retired. Saved `ibn_kathir_bn`
book keys and iOS index 4 migrate to Zakaria (index 14), including when the Quran
translation language has since changed. Arabic and other language choices remain
unchanged. The retired edition is absent from the public and bundled catalogs,
and filtered out even when an older manifest is cached.

CDN prefix: `tafsir/bn-zakaria/v1`; category: `quran_tafsir_bn_zakaria`.
The package contains 114 chapters, an integrity manifest and an attribution notice.
