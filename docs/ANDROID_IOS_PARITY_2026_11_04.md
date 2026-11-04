# Android → iOS parity audit — 4 November 2026

Scope: commits from **2 November 2026 00:00 Asia/Dubai** through `a1c8cce4e` (21 commits). This uses calendar days so the changes made on 2 November are included. Existing shared-module implementations were reviewed before adding iOS counterparts.

## Feature coverage

| Android changes | iOS implementation / disposition |
| --- | --- |
| Spark launch animation; removal of video/blank startup | Shared `DeenlySparkSplash`, bundled launcher mark, and a SwiftUI startup overlay. Respects lifecycle and skips startup for existing direct-screen launch hooks. Android Activity/manifest code remains platform-specific. |
| Ubuntu Sans weights and Home editorial scale | Existing common font resources plus shared `HomeTypography`; same cover/reflection/gallery scale and schedule font family. |
| Day/night poster composition and restrained lower scrim | Ported `ImmersiveHomeCover`, image transitions, cover copy, header contrast, and backdrop scrim; retains the iOS celestial renderer. Updated prayer poster images were already in `core:images`. |
| Greeting, rotating location/weather, engraved search/settings actions | Shared greeting and rotating metadata, copied engraved action drawing and press response. iOS search/Settings callbacks retain their existing navigation. |
| Expandable Explore panel and page-aware gallery | Shared `FluidHomeReveal` and revised artwork rail/reflection; same separation animation, non-actionable transition semantics, bottom-navigation clearance, and collapse action. |
| Grounded recommendation per cover page/day; persistence | `HomePageReflections` retains the last evidence per page across launches, refreshes one turn per page/day, and uses the iOS grounded knowledge service. |
| Expanded reader resizing and compact handle | Latest handle rename/motion was already in shared components; retained. Reader presentation stays expanded while its viewport is resized. |
| Quran reader blur, continuous Arabic passage, inline verse markers, compact heading | Shared reader now uses flowing RTL Arabic with inline verse markers, preserved tajweed, translation paragraphs, compact Surah metadata, stronger artwork blur, and engraved close controls. |
| Opening Bismillah normalization | Shared letter-based opening matcher handles Quran marks, tatweel, whitespace, and alif variants while preserving the rest of the ayah. Callers retain the Surah 1/9 exceptions. |
| Reader/Settings navigation chrome | Shared navigation now reports full-screen destinations to SwiftUI; the native tabs and bot action hide on detail/Settings routes. Native iOS push/pop and Liquid Glass remain the platform equivalents of Android navigation and Backdrop drawing. |
| Header/status contrast and native bar coordination | Shared contrast rules drive per-tab native status requests. App theme preferences remain separate from artwork contrast. |
| Reciter naming, spelling aliases, cache-first search | Al-Sudais display identity and normalized alias search match Android. Cached and remembered offline voices remain discoverable. |
| Offline reciter metadata and library organization | iOS stores recording metadata independently of catalog refreshes, merges cached/selected/downloaded voices, and exposes downloaded voices before discovery. Content & Storage has a compact summary card opening the reciter library sheet. |
| Reciter results in global search | Cache-first results, capped at eight matches, open one voice's live download/selection sheet without selecting it automatically. |
| Reciter selection versus translated audio | Selecting an Arabic voice also selects Arabic audio; translation audio does not incorrectly mark an Arabic voice as selected. Existing shared selection revision flow keeps open pickers synchronized. |
| Reciter previews, style/era filters, reciter metadata, Surah player layout | Style/era filtering, selection metadata and Surah player layout already had shared implementations. Preview callbacks are now wired to a sheet-scoped iOS streaming player with a loading timeout, a 30-second sample limit, and cancellation on dismissal; previewing never selects the voice. |
| New theme presets and custom accents | Preset enums, schemes and shared Appearance UI were already present. Added iOS hue/saturation/brightness selection, all three accent persistence, and the same seed-derived Material roles as Android. |
| Offline fetch fail-fast | iOS text/catalog fetches now stop immediately when the shared reachability feed is offline; cached catalog and downloaded recitations remain available. |
| Download state across Settings/reader/Home; cancellation and model refresh | Existing iOS keyed transfer/category state and URLSession cancellation already cover these behaviors. Reciter transfer progress remains connected to the global strip. Model capability checks use current native paths rather than Android's TTL cache, so Android's force-refresh cache bypass is not needed. |
| Android widgets/sample typography, Gradle and manifest changes | Android-specific integration. Shared font/image/theme resources reach iOS; SwiftUI widgets and Xcode signing/lifecycle remain native. |

## Validation

- Android `:app:assembleDemoDebug` passed after the shared resource/theme changes.
- iOS Debug simulator build passed.
- Shared native tests: **144 passed**, zero failures.
- iOS UI smoke tests: expanded Quran heading/close action and hidden native tabs in Settings passed. Screenshots are retained with the xcresult under ignored `work/`.
- Local build/test logs are under ignored `work/ios-parity-*`.
- The full native test run exposed a stale topic-catalog expectation omitting Shama’il and the additional Hadith collections (topic 9 and 38–45). The expected IDs were brought up to date; production catalog code is unchanged.
- Native simulator screenshots cover Home and expanded reader layouts. Physical-device testing remains separate from simulator/build validation.

## Commits reviewed

- `a1c8cce4e` — 2026-11-04T20:50:21+04:00 — fdf
- `865628b8f` — 2026-11-04T20:39:08+04:00 — dd
- `576ca6cbe` — 2026-11-04T20:22:18+04:00 — fdf
- `d288308e6` — 2026-11-04T17:50:12+04:00 — DFDF
- `373e0b5f4` — 2026-11-04T17:13:00+04:00 — dfdf
- `684244063` — 2026-11-03T20:30:04+04:00 — dd
- `b4b6422c9` — 2026-11-03T20:14:56+04:00 — fdfd
- `905d0f6ad` — 2026-11-03T16:32:09+04:00 — dd
- `89a6c8983` — 2026-11-03T12:16:17+04:00 — Perfect!
- `55d87b437` — 2026-11-03T10:53:55+04:00 — dfdf
- `b0937dc70` — 2026-11-03T07:46:21+04:00 — cc
- `234932801` — 2026-11-02T14:28:02Z — Keep reciter selection UI in sync and improve playback and Settings layout
- `8b238b6ec` — 2026-11-02T14:08:02Z — Group reciters by style and era across Android and iOS pickers
- `78c751585` — 2026-11-02T14:04:22Z — Add reciter previews and refine Hadith reader layout
- `be71d4365` — 2026-11-02T13:39:35Z — Fix CDN reciter labels and align banner curves and Home header spacing
- `baacbf5a7` — 2026-11-02T12:52:56Z — Fix reader titles and reciter labels, improve Home contrast and navigation motion
- `c6607e992` — 2026-11-02T12:28:22Z — Fix Surah player layout, show reciter metadata and format grounded suggestions
- `41fc31ec0` — 2026-11-02T12:00:42Z — Polish reciter picker, dismiss banners and move iOS storage work off Main
- `98e755185` — 2026-11-02T10:54:50Z — Expose Surah reciter picker and add Fortress news card playback
- `37f7355d5` — 2026-11-02T10:16:55Z — Improve cross-platform reciter selection and iOS download banners
- `17191baba` — 2026-11-02T09:11:54Z — docs: document private calling and coturn setup with architecture diagrams

## Android source/configuration inventory reviewed

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/starception/submission/VideoSplashActivity.kt`
- `app/src/main/kotlin/com/starception/submission/core/qurandatabase/QuranEnhancedEntity.kt`
- `app/src/main/kotlin/com/starception/submission/download/AndroidAssetPlatform.kt`
- `app/src/main/kotlin/com/starception/submission/download/AssetDownloadManager.kt`
- `app/src/main/kotlin/com/starception/submission/download/AssetDownloadViewModel.kt`
- `app/src/main/kotlin/com/starception/submission/download/MissingAiModelCards.kt`
- `app/src/main/kotlin/com/starception/submission/download/MissingContentCard.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/FluidHomeReveal.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/HomeArtworkRail.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/HomePageRecommendationStore.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/HomeSurfaceContrast.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/HomeTypography.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/ImmersiveHomeCover.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/PrayerLocationLabel.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/PrayerTimesScreen.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/PreparedInsightReadings.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/SwipeableBigTiles.kt`
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/navigation/PrayerTimesNavigation.kt`
- `app/src/main/kotlin/com/starception/submission/feature/quran/QuranPlaybackService.kt`
- `app/src/main/kotlin/com/starception/submission/feature/quran/ReciterCatalog.kt`
- `app/src/main/kotlin/com/starception/submission/feature/quran/ReciterDownloadManager.kt`
- `app/src/main/kotlin/com/starception/submission/feature/quran/ReciterSelectionStore.kt`
- `app/src/main/kotlin/com/starception/submission/feature/surah/SurahDetailScreen.kt`
- `app/src/main/kotlin/com/starception/submission/media/GlobalMediaViewModel.kt`
- `app/src/main/kotlin/com/starception/submission/media/MediaMiniBar.kt`
- `app/src/main/kotlin/com/starception/submission/media/MediaPlaybackState.kt`
- `app/src/main/kotlin/com/starception/submission/ml/DeenlyKnowledgeModel.kt`
- `app/src/main/kotlin/com/starception/submission/navigation/NiaNavHost.kt`
- `app/src/main/kotlin/com/starception/submission/settings/UnifiedSettingsScreen.kt`
- `app/src/main/kotlin/com/starception/submission/settings/UnifiedSettingsViewModel.kt`
- `app/src/main/kotlin/com/starception/submission/settings/components/ContentManagementSection.kt`
- `app/src/main/kotlin/com/starception/submission/settings/components/HsvColorWheelDialog.kt`
- `app/src/main/kotlin/com/starception/submission/settings/components/ReciterDownloadSheet.kt`
- `app/src/main/kotlin/com/starception/submission/settings/components/ReciterPreviewPlayer.kt`
- `app/src/main/kotlin/com/starception/submission/settings/components/ReciterStorageSection.kt`
- `app/src/main/kotlin/com/starception/submission/ui/AppHeaderActions.kt`
- `app/src/main/kotlin/com/starception/submission/ui/AppTopSearchBar.kt`
- `app/src/main/kotlin/com/starception/submission/ui/DeenlySparkSplash.kt`
- `app/src/main/kotlin/com/starception/submission/ui/HomeRotatingHeaderText.kt`
- `app/src/main/kotlin/com/starception/submission/ui/HomeSystemBarAppearance.kt`
- `app/src/main/kotlin/com/starception/submission/ui/MiniBarShell.kt`
- `app/src/main/kotlin/com/starception/submission/ui/NavigationGlass.kt`
- `app/src/main/kotlin/com/starception/submission/ui/NavigationGlassAppearance.kt`
- `app/src/main/kotlin/com/starception/submission/ui/NiaApp.kt`
- `app/src/main/kotlin/com/starception/submission/ui/NiaAppState.kt`
- `app/src/main/kotlin/com/starception/submission/ui/SyncBarRows.kt`
- `app/src/main/kotlin/com/starception/submission/ui/TopBarSearchViewModel.kt`
- `app/src/main/kotlin/com/starception/submission/widget/samples/ui/theme/Type.kt`
- `app/src/main/res/drawable/ic_home_search_fine.xml`
- `app/src/main/res/drawable/ic_home_settings_fine.xml`
- `app/src/main/res/font/ubuntu_sans.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `core/asset-cache/src/commonMain/kotlin/com/starception/submission/core/assetcache/CloudAssetRepository.kt`
- `core/components/src/commonMain/kotlin/com/starception/submission/core/designsystem/component/InsightHadithText.kt`
- `core/components/src/commonMain/kotlin/com/starception/submission/core/designsystem/component/InsightResizeHandle.kt`
- `core/components/src/commonMain/kotlin/com/starception/submission/feature/prayertimes/wobble/PullToSyncContainer.kt`
- `core/components/src/commonMain/kotlin/com/starception/submission/feature/quran/ReciterChoiceRow.kt`
- `core/components/src/commonMain/kotlin/com/starception/submission/feature/quran/ReciterGroups.kt`
- `core/components/src/commonMain/kotlin/com/starception/submission/settings/components/AppearanceSection.kt`
- `core/datastore-proto/src/main/proto/com/starception/submission/data/theme_brand.proto`
- `core/datastore/src/main/kotlin/com/starception/submission/core/datastore/NiaPreferencesDataSource.kt`
- `core/designsystem/src/main/kotlin/com/starception/submission/core/designsystem/theme/Theme.kt`
- `core/designsystem/src/main/kotlin/com/starception/submission/core/designsystem/theme/Type.kt`
- `core/model/src/commonMain/kotlin/com/starception/submission/core/model/data/ThemeBrand.kt`
- `core/theme/src/commonMain/kotlin/com/starception/submission/core/designsystem/theme/PresetColorSchemes.kt`
- `core/theme/src/commonMain/kotlin/com/starception/submission/core/designsystem/theme/SharedTypography.kt`
- `feature/settings/src/main/kotlin/com/starception/submission/feature/settings/SettingsDialog.kt`
