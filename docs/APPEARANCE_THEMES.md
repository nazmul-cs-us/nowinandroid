# Appearance themes

In **Settings → Appearance**, choose from one curated theme collection:

- Purple & Gold
- Chocolate Truffle
- Damson & Blue Curaçao
- Chili Spice

The old **Color Theme** row, custom color picker and wallpaper-color switch have been removed from this shared Android/iOS settings section. **System**, **Light** and **Dark** display modes remain available. Selecting a curated theme uses the existing saved theme setting and applies across the app, including Family.

Existing Default, Android, Coastal, Royal or custom selections continue to render until the user chooses a curated theme. This UI change does not reset saved settings or remove legacy palette support. Theme selection exposes its selected state to accessibility services.

Family now reuses Home's theme-dependent canvas gradient: Android calls `mainPageBackgroundBrush`, while native iOS receives the shared Home gradient stops through `FamilyThemePalette`. Translucent Family panels sit over that canvas; live video keeps its readability overlays.

The app-icon selection setting and alternative Emerald/Neon/Horizon artwork have been removed. The purple Deenly crescent is the only app icon. Android density/adaptive/monochrome variants and iOS light/dark/tinted assets are required renderings of that same icon and remain. Historical Android launcher component names point to Deenly so existing installations retain a usable shortcut even if they previously selected another icon. This removes files from the current Git tree without rewriting history.

Home's immersive photographic feed now also follows the selected theme. Its former fixed violet/blue glows, lower gradient, cover fade and feed base use a shared `HomeFeedPalette` derived from the current Material primary/secondary roles on Android and iOS. The feed keeps a dark tonal base in both display modes for its white text; switching a curated theme changes the glow hues immediately. The ordinary page canvas described above remains separate from this photographic feed.
