# Android Living Day home

The portrait home now separates the prayer anchor from the content reader.
The existing landscape layout continues to provide its two-column dashboard.

## Structure

- A day/night prayer image, location, next obligatory prayer, `HH:mm:ss`
  countdown, and five-prayer status ribbon.
- Actions for marking an elapsed prayer, opening the compass, playing/pausing
  today's reading, and opening the complete schedule.
- A full-width content deck with Reading, Salah, For you, and Qibla tabs.
  Content stays selected until the user changes it; this deck does not auto-page.
- The existing editable schedule appears on request and scrolls into view.
  Prayer notifications, adhan controls, adjustments, and location/weather
  details keep their existing handlers.

The content deck retains the current source links, asset-download actions,
reading preferences, recommendation state, and playback integration. The
existing app navigation destinations and pull-to-sync/media strip remain in use.

## Expansion and accessibility

Expanding a reader folds away the prayer anchor and brings the reader to the
top of the viewport. Collapsing restores the anchor. Reading width is constant;
content measurements control expanded height. The measured heading and tabs
are included in the available-height budget above bottom navigation. Existing
explicit immersive expansion and back/collapse behavior are retained.

The header uses theme colors with a contrast fallback over its photograph,
respects system time formatting, and uses the shared Friday prayer-name adapter.
Large text switches the prayer ribbon and actions into multiple rows. The
content heading reserves space for its Expand/Collapse button.

## Validation

`LivingDayHeaderTest` exercises feature callbacks, midnight countdown rollover,
and action reachability at 2x text size. It also verifies these rendered component
baselines (these are header previews, not full-app screenshots):

- [Normal text](../app/src/testDemo/screenshots/living_day_header.png)
- [Large text, scrolled](../app/src/testDemo/screenshots/living_day_large_text.png)

```sh
./gradlew :app:testDemoDebugUnitTest --tests '*LivingDayHeaderTest'
./gradlew :app:assembleDemoDebug
```

Device review should cover tab selection, expansion/collapse while scrolled,
schedule editing, navigation clearance, system animation scale zero, and the
existing landscape dashboard. Robolectric screenshots do not verify gesture
feel or frame pacing on a physical phone.

This change does not implement the separately requested audio-interruption
coordinator or the other-page/iOS redesign plan.
