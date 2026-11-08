# Android immersive home

The portrait home uses the app's existing insight artwork and content in the
dark, artwork-led hierarchy shown in the shared fluid-card reference. The
landscape dashboard keeps its existing layout.

At rest, Today's Reading is the featured card. Its neighboring edge indicates
the horizontal carousel. Below it, artwork cards open Today's Salah, For this
moment, and Qibla in the same reader. The existing prayer schedule and location
details remain available further down the page.

Tapping a featured card or an artwork card opens the selected insight to the
immersive height. The current Quran, Salah, recommendation, and Qibla content
and actions are reused; Back returns to the feed. Auto paging is disabled in
this layout so the featured card does not change while someone is reading.
The featured card's gutter, width, height, corner radius, shadow, and artwork move with
one expansion progress. Reader content appears after the card has grown, while
the navigation and heading leave near the end of the transition; collapse
reverses that sequence. The full-screen height is held constant across the
navigation handoff so the card does not jump at the last frame.
The home navigation capsule and system bar icons use the dark feed contrast;
other destinations keep their selected theme.

The [artwork rail preview](../app/src/testDemo/screenshots/home_artwork_rail.png)
is a component render at phone width. It verifies image treatment and labels,
not the full app shell. Tapping a smaller card carries its actual artwork from
the tapped window bounds into the selected reader, then fades the overlay once
the reader is immersive. [Mid-transition](../app/src/testDemo/screenshots/artwork_morph_mid.png)
and [handoff](../app/src/testDemo/screenshots/artwork_morph_overlay.png) renders
verify the moving artwork and its final bounds. `HomeArtworkRailTest` checks all
three destinations and the overlay presence.

```sh
./gradlew :app:testDemoDebugUnitTest --tests '*HomeArtworkRailTest'
./gradlew :app:assembleDemoDebug
```

Physical-device review is still required for frame pacing, the search bar,
bottom navigation, and Back during card expansion.

Today's Reading positions its Surah heading below the measured header controls,
including larger accessibility text, instead of reserving a fixed 104dp gap.
Scrolling inside an expanded passage now forwards the reader's existing chrome
visibility to Home, so the navigation capsule can hide without scrolling the
outer feed. “Show controls” restores the capsule; closing the reader clears the
hidden state. The existing outer-feed scroll behavior remains available.

Android and iOS share a dark-backed Surah/status header and dark-backed gold ayah
markers so bright artwork cannot wash out the labels. iOS already hides its
navigation when the reader opens. `ExpandedReadingLayoutTest` checks header
spacing, larger text and nested passage scrolling; `ImmersiveHomeReaderTest`
checks that the carousel forwards hide/show changes to Home.

Home artwork now rotates through eight reviewed Getty Open Content nature works.
Every day is the default. Settings → Appearance → Home artwork offers Every hour,
Every day, Every week, Every month, and Keep current. The preference and rotation
anchor persist across app launches. Daily changes follow local midnight, weekly
changes follow each seven local days from the selected cadence, and monthly
changes follow calendar-month boundaries. While Home remains open, the date is
checked once a minute, so a change appears within a minute of its boundary.
Keep current freezes the currently displayed set, including across relaunches.
Switching cadence preserves the current set and starts the new cadence from that
point. The eight-work gallery repeats rather than fetching unreviewed artwork.
The cover, rail, and readers resolve the same artwork for each page; five different
works are displayed at any one rotation. Prayer Now retains its night treatment.
Artwork credits and links to the current five Getty records appear below the
frequency control. See [third-party assets](THIRD_PARTY_ASSETS.md#getty-home-artwork)
for provenance and regeneration instructions.
