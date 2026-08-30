package com.starception.submission.feature.prayertimes.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.starception.submission.feature.prayertimes.PrayerTimesScreen
import com.starception.submission.prayer.service.EnhancedLocationService
import kotlinx.serialization.Serializable

@Serializable
object PrayerTimesRoute

fun NavController.navigateToPrayerTimes() = navigate(PrayerTimesRoute)

fun NavGraphBuilder.prayerTimesScreen(
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    locationService: EnhancedLocationService? = null,
    onSurahClick: (Int) -> Unit = {},
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onFortressDuaClick: (com.starception.submission.core.duadatabase.Dua) -> Unit = {},
    onBukhariBookPlayClick: (Int) -> Unit = {},
    onMediaSourceClick: ((com.starception.submission.media.MediaSource) -> Unit)? = null,
    downloadProgress: Float = 0f,
    downloadLabel: String = "Downloading content",
    mediaState: com.starception.submission.media.MediaControllerUiState = com.starception.submission.media.MediaControllerUiState(),
    onMediaAction: (com.starception.submission.media.MediaAction) -> Unit = {},
    isTtsPreparing: Boolean = false,
    onPrayerAlertChanged: (com.starception.submission.feature.prayertimes.wobble.PrayerAlertState) -> Unit = {},
    prayerAlertOverride: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState = com.starception.submission.feature.prayertimes.wobble.PrayerAlertState(),
    forbiddenPrayerTimeState: com.starception.submission.feature.prayertimes.wobble.ForbiddenPrayerTimeState = com.starception.submission.feature.prayertimes.wobble.ForbiddenPrayerTimeState(),
    onSearchSubmit: (query: String) -> Unit = {},
    isSyncingExternal: Boolean = false,
    onSetSyncing: (Boolean) -> Unit = {},
) {
    composable<PrayerTimesRoute> {
        PrayerTimesScreen(
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onSurahClick = onSurahClick,
            onSurahClickWithAyah = onSurahClickWithAyah,
            onFortressDuaClick = onFortressDuaClick,
            onBukhariBookPlayClick = onBukhariBookPlayClick,
            onMediaSourceClick = onMediaSourceClick,
            downloadProgress = downloadProgress,
            downloadLabel = downloadLabel,
            mediaState = mediaState,
            onMediaAction = onMediaAction,
            isTtsPreparing = isTtsPreparing,
            onPrayerAlertChanged = onPrayerAlertChanged,
            prayerAlertOverride = prayerAlertOverride,
            forbiddenPrayerTimeState = forbiddenPrayerTimeState,
            onSearchSubmit = onSearchSubmit,
            isSyncingExternal = isSyncingExternal,
            onSetSyncing = onSetSyncing,
        )
    }
}
