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

The five home covers, their Explore cards, and expanded readers share a bundled
gallery of eight nature artworks without human figures. Android and iOS use the
same images and persisted rotation policy. Every day is the default; Appearance
settings also offer hourly, weekly, monthly, and Keep current. The bundled gallery
cycles; it does not download new, unreviewed images from Getty. All artwork works
offline.

Digital image courtesy of Getty’s Open Content Program.

- Image license: [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)
- Usage guidance: [Getty Open Content FAQs](https://www.getty.edu/projects/open-content-program/faqs/)
- Catalog, original image URLs, license metadata, and SHA-256 source digests:
  `core/images/artwork/getty-home-artwork.json`
- Rebuild: `python tools/prepare_getty_home_artwork.py` (requires Pillow)
- Local files: `core/images/src/commonMain/composeResources/drawable/`
- Adaptations: border trimming, portrait cropping, WebP compression, and a
  darker, desaturated night version of each image. The runtime header and footer
  scrims keep white text readable over every artwork.

| Artwork | Artist | Getty record | Day resource |
| --- | --- | --- | --- |
| Study of Clouds with a Sunset near Rome | Simon Alexandre Clément Denis | [10986B](https://www.getty.edu/art/collection/object/10986B) | `home_cover_prayer_day_portrait.webp` |
| Wheatstacks, Snow Effect, Morning (Meules, Effet de Neige, Le Matin) | Claude Monet | [103RK8](https://www.getty.edu/art/collection/object/103RK8) | `home_cover_salah_portrait.webp` |
| Irises | Vincent van Gogh | [103JNH](https://www.getty.edu/art/collection/object/103JNH) | `home_cover_quran_portrait.webp` |
| Mount Snowdon through Clearing Clouds | Alfred William Hunt | [109KKN](https://www.getty.edu/art/collection/object/109KKN) | `home_cover_qibla_portrait.webp` |
| [Seascape] | Paul Martin | [10492H](https://www.getty.edu/art/collection/object/10492H) | `home_cover_suggestion_portrait.webp` |
| Grotto of Sarrazine near Nans-sous-Sainte-Anne | Gustave Courbet | [1096PR](https://www.getty.edu/art/collection/object/1096PR) | `home_getty_grotto_day.webp` |
| Still Life with Flowers and Fruit | Claude Monet | [103RF8](https://www.getty.edu/art/collection/object/103RF8) | `home_getty_flowers_day.webp` |
| [Water Lilies] | Eugène Atget | [104JKW](https://www.getty.edu/art/collection/object/104JKW) | `home_getty_lilies_day.webp` |

Each source record declared the image CC0 when retrieved on 2026-11-07. The
preparation script rechecks the license and source digest before recreating an
asset. Review the full image for human figures before adding any catalog entry;
CC0 alone does not establish suitability. No Getty endorsement is implied.
