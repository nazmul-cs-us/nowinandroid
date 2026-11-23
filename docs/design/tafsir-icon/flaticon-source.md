# Selected Tafsir icon

- **Asset:** Quran, icon 5452447
- **Creator:** smashingstocks (Flaticon)
- **Source:** https://www.flaticon.com/free-icon/quran_5452447
- **Original PNG:** https://cdn-icons-png.flaticon.com/512/5452/5452447.png
- **License:** Flaticon License; free for personal and commercial purposes with attribution, as displayed on the source page.
- **Bundled file:** `core/images/src/commonMain/composeResources/drawable/flaticon_quran_5452447.png`

The original PNG is bundled byte-for-byte without redrawing, recoloring, or conversion.
The UI leaves the background transparent and applies the theme's foreground color at
display time, as approved by the user. The original PNG bytes are unchanged.
All Tafsir entry points use the same image. Attribution is visible in Settings > About
and included in `app/src/main/assets/licenses/flaticon_uicons.txt`.

## Reused collapsible reader UI

Home's `FluidHomeReveal` and the reader's inline Tafsir panels both use the shared
`FluidReveal` component. Inline Tafsir keeps a compact, tappable header containing
this Quran icon, its verse reference, playback controls and a chevron. Opening and
closing the commentary does not reload it or dispose its speech player. Full-surah
narration opens the current passage, and the user can still collapse it manually.
