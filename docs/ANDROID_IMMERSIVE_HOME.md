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
