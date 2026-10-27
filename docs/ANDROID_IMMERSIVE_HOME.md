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
The home navigation capsule and system bar icons use the dark feed contrast;
other destinations keep their selected theme.

The [artwork rail preview](../app/src/testDemo/screenshots/home_artwork_rail.png)
is a component render at phone width. It verifies image treatment and labels,
not the full app shell. `HomeArtworkRailTest` checks all three destinations.

```sh
./gradlew :app:testDemoDebugUnitTest --tests '*HomeArtworkRailTest'
./gradlew :app:assembleDemoDebug
```

Physical-device review is still required for frame pacing, the search bar,
bottom navigation, and Back during card expansion.
