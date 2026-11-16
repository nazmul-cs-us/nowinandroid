# Third-party assets

## Today's Prayers NASA sun and moon

- Sun asset: [NASA Solar Dynamics Observatory 171 Å image](https://sdo.gsfc.nasa.gov/assets/img/latest/latest_1024_0171.jpg)
- SDO usage guidance: [SDO Data Rights and Rules](https://sdo.gsfc.nasa.gov/data/rules.php)
- Moon asset: [NASA SVS Moon Phase and Libration](https://svs.gsfc.nasa.gov/5587)
- Local files:
  - `app/src/main/res/drawable-nodpi/prayer_widget_sun_real_nasa_v1.webp`
  - `app/src/main/res/drawable-nodpi/prayer_widget_moon_real_nasa_v1.webp`
- Credit: Sun imagery courtesy of NASA/SDO and the AIA science team; Moon
  visualization by NASA Scientific Visualization Studio using Lunar Reconnaissance
  Orbiter data.

The 1024 px source images are tightly cropped into 512 px WebP textures. Circular
masking, directional shading, and glow are rendered by the widget at runtime.

## Prayer Now native sun and moon

- Source package: `/Users/smarterai/Downloads/celestial-native`
- License: MIT
- Android: Kotlin, `GLSurfaceView`, and OpenGL ES shaders
- iOS: Swift, `MTKView`, and Metal shaders
- Bundled license: `app/src/main/assets/licenses/celestial_native_mit.txt`

The Prayer Now tile renders both bodies procedurally at native display resolution.
It requires no WebView, JavaScript, remote model, texture download, or network access.

## Legacy Today's Prayers 3D sun and moon

- Assets: Fluent Emoji `Sun` and `Crescent moon` 3D artwork
- Source: [Microsoft Fluent Emoji](https://github.com/microsoft/fluentui-emoji)
- Local files:
  - `app/src/main/res/drawable-nodpi/prayer_timeline_sun_3d.webp`
  - `app/src/main/res/drawable-nodpi/prayer_timeline_moon_3d.webp`
- License: MIT; bundled at
  `app/src/main/assets/licenses/microsoft_fluentui_emoji.txt`

These former prayer-timeline assets are retained for compatibility but are no longer
referenced by the current widget renderer. Retain the bundled Microsoft copyright and
MIT license when redistributing them.

## Settings widget icon

- Asset: Widget icon (`8338851`)
- Source: [Flaticon](https://www.flaticon.com/free-icon/widget_8338851)
- Local file: `app/src/main/res/drawable-nodpi/flaticon_widget_8338851.png`

The icon is used at the user's explicit request. Retain this source attribution when replacing,
redistributing, or reviewing the asset under Flaticon's applicable license terms.

## Shamai'l At-Tirmidhi topic icon

- Asset: Quran icon (`4556746`)
- Source: [Flaticon](https://www.flaticon.com/free-icon/quran_4556746)
- Local file: `core/designsystem/src/main/res/drawable-nodpi/topic_shamayele_tirmidhi.png`

The icon is used at the user's explicit request. Retain this source attribution when replacing,
redistributing, or reviewing the asset under Flaticon's applicable license terms.

## Shamai'l At-Tirmidhi text dataset

- Arabic and Bengali selection: [Hadith.one](https://hadith.one/bn/book/357/1)
- English parallel edition: [Habibur.com](https://hadith.habibur.com/shamail/)
- Four-field validation (Arabic, Bengali, English, explanation):
  [HadithBD](https://www.hadithbd.com/hadith/shamail-tirmidhi/section/1)
- Local fetch/build script: `scripts/fetch_shamayele_tirmidhi_online.py`

The delivered database contains 56 chapters and 322 narration records. The printed source
numbering ends at 320 because numbers 261 and 264 are each used for two distinct records.

## Getty home artwork

The five home covers, Explore cards, and expanded readers share a bundled gallery
of twelve reviewed images: eight Islamic architectural or ornamental details from
the Alhambra and four calm, human-free nature scenes. Android and iOS use the same
images and persisted rotation policy. Every day is the default; Appearance settings
also offer hourly, weekly, monthly, and Keep current. The gallery cycles offline;
it does not download arbitrary Getty search results.

The compact settings card shows today's five images and one refresh picker.
Artwork credits and source links appear in the About the artwork dialog.
Controls stack vertically on narrow screens or with larger text.

Digital image courtesy of Getty’s Open Content Program.

- Image license: [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)
- Usage guidance: [Getty Open Content FAQs](https://www.getty.edu/projects/open-content-program/faqs/)
- Reviewed allowlist, source URLs, source SHA-256 digests, category, and review notes:
  `core/images/artwork/getty-home-artwork.json`
- Rebuild: `python tools/prepare_getty_home_artwork.py` (requires Pillow)
- Approval checks: `python -m unittest discover -s tools -p test_prepare_getty_home_artwork.py`
- Generated runtime catalog: `core/images/src/commonMain/kotlin/com/starception/submission/core/images/HomeArtworkCatalog.kt`
- Local images: `core/images/src/commonMain/composeResources/drawable/`
- Adaptations: remove scan borders using recorded crop rectangles, portrait cropping,
  WebP compression, and a darker, desaturated version for Prayer Now at night.

| Artwork | Artist/photographer | Category | Getty record |
| --- | --- | --- | --- |
| [Archways, the Alhambra] | Unknown | Islamic Architecture | [107JW5](https://www.getty.edu/art/collection/object/107JW5) |
| [Archway, the Alhambra] | Unknown | Islamic Architecture | [107JW6](https://www.getty.edu/art/collection/object/107JW6) |
| [Interior of Alhambra] | Unknown | Islamic Architecture | [104KMH](https://www.getty.edu/art/collection/object/104KMH) |
| Alhambra | Unknown | Islamic Architecture | [104G6V](https://www.getty.edu/art/collection/object/104G6V) |
| [Archway in the Alhambra] | Unknown | Islamic Pattern | [107JSE](https://www.getty.edu/art/collection/object/107JSE) |
| [Archway in the Alhambra] | Unknown | Islamic Pattern | [107JVV](https://www.getty.edu/art/collection/object/107JVV) |
| Panel from the Alhambra | Hugh Owen | Islamic Pattern | [107VD6](https://www.getty.edu/art/collection/object/107VD6) |
| [Three tiles from The Alhambra] | William Chaffers | Islamic Pattern | [108VZD](https://www.getty.edu/art/collection/object/108VZD) |
| Study of Clouds with a Sunset near Rome | Simon Alexandre Clément Denis | Calm Nature | [10986B](https://www.getty.edu/art/collection/object/10986B) |
| Mount Snowdon through Clearing Clouds | Alfred William Hunt | Calm Nature | [109KKN](https://www.getty.edu/art/collection/object/109KKN) |
| [Seascape] | Paul Martin | Calm Nature | [10492H](https://www.getty.edu/art/collection/object/10492H) |
| [Water Lilies] | Eugène Atget | Calm Nature | [104JKW](https://www.getty.edu/art/collection/object/104JKW) |

The CC0 metadata and reviewed source images were checked by 2026-11-08. The builder
rejects missing review metadata, human figures, subjects outside Islamic architecture,
Islamic patterns, and calm nature, and non-CC0 licenses. It then rechecks each source
record's license and image digest before rebuilding. Review the complete source image
before setting `reviewed: true`; category or keyword matching alone is insufficient.
There is no automatic image-classification or religious-compliance claim. No Getty
endorsement is implied.

## Reciter identification portraits

The compact reciter portraits come from Quran.com's public frontend image set,
[pinned revision aff1a035b09b66f28047b3216edcae4c5c949a49](https://github.com/quran/quran.com-frontend-next/tree/aff1a035b09b66f28047b3216edcae4c5c949a49/public/images/reciters).
They are identification photos, separate from Home's reviewed artwork feed.
Source credit: Quran.com / Quran Foundation. The source repository does not supply
individual photographer credits or per-image license metadata; these photos are
not designated CC0/public domain by Deenly.

| Reciter | Source file | Bundled resource |
| --- | --- | --- |
| Abdul Basit Abdul Samad | `1.jpg` | `reciter_abdul_basit.webp` |
| Abdul Rahman Al-Sudais | `3.jpeg` | `reciter_sudais.webp` |
| Mahmoud Khalil Al-Husary | `6.jpg` | `reciter_husary.webp` |
| Mishary Rashid Al-Alafasy | `7.jpg` | `reciter_alafasy.webp` |
| Muhammad Siddiq Al-Minshawi | `9.jpg` | `reciter_minshawi.webp` |
| Saud Al-Shuraim | `10.jpg` | `reciter_shuraim.webp` |
| Maher Al-Muaiqly | `12.jpg` | `reciter_maher.webp` |
| Abu Bakr Al-Shatri | `4.jpg` | `reciter_abu_bakr_shatri.webp` |
| Hani Ar-Rifai | `5.jpg` | `reciter_hani_rifai.webp` |

Images are resized proportionally to a maximum of 192 pixels and compressed to
WebP (quality 85). They ship in `core/images` for offline Android/iOS use.
Explicit name aliases select portraits; unmatched names display initials rather
than an unrelated person's photo.

Selection lists remove repeated IDs and equivalent recordings (same person,
server, style and surah coverage). Different riwayat/styles or recording servers
remain selectable when their catalog contains all 114 surahs. Existing selected/downloaded IDs remain accessible; deduplication
never deletes audio. The bundled Alafasy default and MP3Quran recordings are
separate audio sources, labelled as default versus the catalog's recording style.

Reciter pickers and the offline library now render one person per row. All eligible complete
recordings, including Alafasy's separate default/CDN option, remain in that
person's recording selector. Known names use one consistent display spelling.
Full recording labels wrap; unsupported names still use initials. Library
recording changes only change the preview/download target. In a selection picker,
choosing a recording selects that original source ID. Existing saved files stay
under their original recording IDs and are never migrated or deleted by grouping.
