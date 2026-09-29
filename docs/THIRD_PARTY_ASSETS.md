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
