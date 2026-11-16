# App guide and first-use tips

Android and iOS store section visits on the device. No guide visit events, search
queries, message contents or timestamps are sent to a server. The records are a
set of visited-section flags, separately remembered tips and the first-use-tips preference, independent of
the signed-in account.

A small nonmodal, theme-matched card introduces a section on its first actual
visit. It has no dimmed backdrop, does not take keyboard focus, and leaves the
rest of the page usable. Compose tips wait briefly for navigation to settle.
Returning to a visited section does not show its tip again, even after restarting.
A tip already displayed in the full guide is not repeated on a later first visit.
Disabled/inactive hosts do not record visits. Embedded expanded Quran reading
records Reading as visited without adding a card over the reader.

Coverage: Home, Reading, Search, Family, Saved, Reciters and Settings. Android's
app navigation and the shared iOS navigation supply actual section entries;
iOS Family records its native screen appearance using the same iOS keys. Incoming
Family calls dismiss the native Family tip, and Android suppresses tips while
a Family call is present or owns audio. Nested Compose sections share a single active-tip slot.

Settings → App guide offers:

- **Show first-use tips**: a persistent on/off switch for future tips.
- **Explore the app guide**: a replayable seven-part walkthrough. Unvisited
  sections come first; visited and unvisited topics are labelled separately.
  Reading this walkthrough never marks the actual sections as visited.

Automatic tips stay compact. The explicitly opened walkthrough is a themed,
scrollable dialog with fixed Back/Next/Close controls and no automatic navigation,
permission requests or account changes.

## Persistence

Android uses `deenly_app_guide` SharedPreferences (`visited_SECTION`,
`tip_seen_SECTION`, `tips_enabled`). iOS uses standard UserDefaults (`deenly_guide_visited_SECTION`,
`deenly_guide_tip_seen_SECTION`, `deenly_guide_tips_enabled`). Section IDs are stable uppercase enum names.
Uninstalling/clearing app data resets this local history. Disabling tips does not
reset visits or remove replay access.

## Verification

`AppGuideTest` checks first-use behavior, nonmodal interaction, revisits, replay
ordering, persistence through composition recreation, disabled tips and large-text
landscape navigation. Screenshot baselines cover the compact light tip and the
manually opened dark/landscape walkthroughs. Reciter UI tests disable unrelated
first-use tips so audio controls are tested independently.

On a real iPhone/iPad/Mac, verify that inactive native tabs do not show tips,
Family tip dismissal during incoming calls, Settings replay and persistence after
relaunch. Xcode/device validation is required for the native Family integration.
