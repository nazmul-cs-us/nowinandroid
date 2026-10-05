/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.images.resources.flaticon_sound_14925198
import com.starception.submission.core.images.resources.flaticon_sound_14925297
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.feature.prayertimes.wobble.AlertPhase
import com.starception.submission.feature.prayertimes.wobble.PrayerAlertState
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.SharedPrayerSlot
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.content.SharedNewsResource
import com.starception.submission.shared.content.canonicalSearchQuery
import com.starception.submission.shared.content.createSharedNewsRepository
import com.starception.submission.shared.content.expandedSearchQueries
import com.starception.submission.shared.content.searchCatalog
import com.starception.submission.shared.dashboardSlots
import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.createQuranVerseRepository
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.settings.formatOffset
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.CupertinoIconButton
import io.github.alexzhirkevich.cupertino.CupertinoSliderNative
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults.plainButtonColors
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveWidget
import io.github.alexzhirkevich.cupertino.adaptive.ExperimentalAdaptiveApi
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import com.starception.submission.core.images.resources.Res as ImageRes

// Shared copies of the Android dashboard's reference palette. Keeping these
// values identical makes light-mode prayer status read the same on both hosts.
private val PrayerReferenceInk = Color(0xFF0A0808)
private val PrayerReferenceCard = Color(0xFFFFFDF7)
private val PrayerReferenceSlate = Color(0xFF5D6574)
private val PrayerReferenceBlue = Color(0xFF4F779D)
private val PrayerReferenceRust = Color(0xFF99593C)
private val PrayerReferenceGold = Color(0xFFD8AB59)

private enum class PrayerCardRevealSide { Adjust, Reset }

private data class RevealedPrayerCard(
    val prayerName: String,
    val side: PrayerCardRevealSide,
)

/**
 * The prayer schedule, written once in Compose and rendered by both platforms.
 *
 * This is the first shared *UI*, as opposed to shared calculation. It follows the
 * card conventions in CLAUDE.md — 16.dp rounded corners, 16.dp padding, surface
 * container — so that when the Android home screen moves over it does not have to
 * be restyled.
 *
 * Stateless on purpose: it takes a list and draws it. Where the times come from,
 * and how location and settings are resolved, belongs to the caller.
 */
@Composable
fun PrayerTimesScreen(
    placeName: String,
    day: SharedPrayerDay,
    salah: SalahProgress,
    onTogglePrayer: (String) -> Unit,
    today: LocalDate,
    offsets: PrayerTimeOffsets,
    onAdjustPrayer: (prayer: String, delta: Int) -> Unit,
    onOpenSettings: () -> Unit,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    isLocating: Boolean = false,
    isRefreshing: Boolean = false,
    syncResultText: String? = null,
    onRefresh: () -> Unit = {},
    notifications: PrayerNotificationPreferences = PrayerNotificationPreferences(),
    onTogglePrayerNotification: (String) -> Unit = {},
    onTogglePrayerAdhan: (String) -> Unit = {},
    onAdhanVolumeChange: (String, Int) -> Unit = { _, _ -> },
    onOpenProfile: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    searchController: SharedSearchController? = null,
    onOpenQuran: (Int) -> Unit = {},
    onOpenQibla: () -> Unit = {},
    onOpenDrivingMode: () -> Unit = {},
    onOpenRecommendation: () -> Unit = {},
    onOpenBukhariBook: (Int) -> Unit = {},
    onOpenBukhariHadith: (Int) -> Unit = {},
    onOpenQuranicDua: (Int) -> Unit = {},
    onOpenFortressChapter: (Int) -> Unit = {},
    onOpenShamayelBook: (Int) -> Unit = {},
    selectedBottomIndex: Int = 0,
    onSelectBottom: (Int) -> Unit = {},
    quranPlayer: QuranAudioPlayer = LocalQuranAudioPlayer.current,
    hasVerifiedLocation: Boolean = true,
    nudgeOverride: com.starception.submission.core.model.deenly.DeenlyNudge? = null,
    onNudgeChanged: (com.starception.submission.core.model.deenly.DeenlyNudge?) -> Unit = {},
    externalNudgeActionRequests: kotlinx.coroutines.flow.Flow<Unit>? = null,
) {
    var showAllPrayers by remember { mutableStateOf(false) }
    var isTuningSchedule by remember { mutableStateOf(false) }
    var showIslamicQuiz by remember { mutableStateOf(false) }
    // Inline search + profile sheet — the Android pattern: both open in
    // place instead of navigating to separate pages.
    // ONE search surface app-wide: the shared controller hosted by the nav
    // renders the overlay above every page; the home's pill, mic, and FAB all
    // drive it instead of keeping a private copy.
    var showProfileSheet by remember { mutableStateOf(false) }
    // The floating iOS volume HUD: active prayer + its adhan percent while
    // the user adjusts; null hides the capsule.
    var activeAdhanVolume by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val contentStore = remember { SharedContentStore() }
    val downloadStatus by com.starception.submission.shared.assets.ContentDownloadBus.state.collectAsState()
    val isOnline by com.starception.submission.shared.connectivity.appConnectivity.isOnline.collectAsState()
    val homeCanvas = screenCanvasBrush()
    val nextPrayerState = remember(day, notifications) {
        day.prayerAlertState(notifications)
    }
    val nudgeDate = today.toString()
    var dismissedNudgeIds by remember(nudgeDate) {
        mutableStateOf(contentStore.dismissedNudgeIds(nudgeDate))
    }
    val rankedNudge = remember(day, salah.completed, dismissedNudgeIds, hasVerifiedLocation) {
        val currentSlot = day.currentPrayer?.let { name -> day.slots.firstOrNull { it.name == name } }
        val nextSlot = day.nextPrayer?.let { name -> day.slots.firstOrNull { it.name == name } }
        com.starception.submission.core.model.deenly.selectDeenlyNudge(
            com.starception.submission.core.model.deenly.DeenlyNudgeContext(
                nowMinute = day.nowMinute,
                currentPrayer = currentSlot?.name,
                currentPrayerMinute = currentSlot?.let { (it.hour * 60) + it.minute },
                nextPrayer = nextSlot?.name,
                nextPrayerMinute = nextSlot?.let { (it.hour * 60) + it.minute },
                completedPrayers = salah.completed,
                hasVerifiedLocation = hasVerifiedLocation,
                hasQuizAvailable = true,
                dismissedIds = dismissedNudgeIds,
            ),
        )
    }
    val deenlyNudge = nudgeOverride
        ?.takeUnless { it.id in dismissedNudgeIds }
        ?: rankedNudge

    fun dismissNudge(id: String) {
        dismissedNudgeIds = contentStore.dismissNudge(nudgeDate, id)
    }

    fun performNudgeAction(nudge: com.starception.submission.core.model.deenly.DeenlyNudge) {
        when (nudge.action) {
            com.starception.submission.core.model.deenly.DeenlyNudgeAction.MARK_PRAYED ->
                nudge.prayerName?.let(onTogglePrayer)
            com.starception.submission.core.model.deenly.DeenlyNudgeAction.OPEN_QIBLA ->
                onOpenQibla()
            com.starception.submission.core.model.deenly.DeenlyNudgeAction.PLAY_TRAVEL_DUA ->
                onOpenDrivingMode()
            com.starception.submission.core.model.deenly.DeenlyNudgeAction.PLAY_QUIZ ->
                showIslamicQuiz = true
            com.starception.submission.core.model.deenly.DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION ->
                onOpenRecommendation()
        }
        dismissNudge(nudge.id)
    }

    androidx.compose.runtime.DisposableEffect(deenlyNudge) {
        onNudgeChanged(deenlyNudge)
        onDispose { onNudgeChanged(null) }
    }

    LaunchedEffect(deenlyNudge?.id, externalNudgeActionRequests) {
        externalNudgeActionRequests?.collect {
            deenlyNudge?.let(::performNudgeAction)
        }
    }

    PullToSyncContainer(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        syncResultText = syncResultText,
        isOffline = !isOnline,
        prayerAlertState = nextPrayerState,
        downloadProgress = downloadStatus?.progress ?: 0f,
        downloadLabel = downloadStatus?.label?.let { label ->
            val percent = ((downloadStatus?.progress ?: 0f) * 100).toInt()
            "$label $percent%"
        }.orEmpty(),
        modifier = modifier.fillMaxSize(),
    ) { syncState ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(homeCanvas)) {
                // A persistent sync/status strip is implemented as top padding, which
                // reduces this child's measured height. Add that inset back for all
                // responsive sizing so cards do not shrink and reflow while the strip
                // opens; the LazyColumn remains responsible for the smaller viewport.
                val layoutHeight = maxHeight + syncState.heldContentInsetTop
                // Requiring room in both axes keeps wide, short phones out of tablet sizing.
                val isTablet = maxWidth >= 600.dp && layoutHeight >= 600.dp
                val isTabletPortrait = isTablet && maxWidth <= maxHeight
                // The phone and tablet share one design language: identical
                // cards at identical aspect ratios. Portrait tablets therefore
                // reuse the single-pane layout and simply show more of the
                // carousel; only landscape tablets split into two panes.
                val useTwoPaneLayout = isTablet && !isTabletPortrait
                // Match Android: the floating bottom pill in portrait, the side
                // rail only in landscape two-pane layouts.
                val useSideNavigation = useTwoPaneLayout
                // iOS has taller status/navigation safe areas than Android. Reserving
                // their measured space keeps the location card above the floating bar.
                val portraitInsightHeight = if (isTablet) {
                    // Carousel pages keep the phone card's 250:288 aspect; the
                    // height just scales up so pages stay proportionate.
                    (layoutHeight * 0.34f).coerceIn(320.dp, 400.dp)
                } else {
                    // Use the idle space above navigation for a taller card, then
                    // yield that space as the sync strip opens. Never go below the
                    // height at which the header/footer can remain on one line.
                    (layoutHeight - 627.dp - syncState.heldContentInsetTop)
                        .coerceIn(220.dp, 280.dp)
                }
                // Measured two-pane sizing: both panes share the height below
                // the search header (~150dp of status bar, header, paddings).
                val paneContentHeight = layoutHeight - 150.dp
                // The hero pane is 5/11 of the row; keep the card's 288/250
                // aspect instead of stretching it to an arbitrary height.
                val heroPaneWidth = (maxWidth - 124.dp) * (5f / 11f)
                val landscapeInsightHeight = if (isTablet) {
                    minOf(
                        // Pane minus the "Insights" title row (44), location
                        // card (58), and their gap (12).
                        paneContentHeight - 114.dp,
                        heroPaneWidth * (288f / 250f),
                    ).coerceAtLeast(320.dp)
                } else {
                    (layoutHeight - 182.dp).coerceIn(220.dp, 400.dp)
                }
                // Landscape schedule: three rows of two cards split the pane
                // minus the schedule header (44) and 8dp row gaps evenly.
                val tabletCardHeight = (
                    (paneContentHeight - 44.dp - (8.dp * 3)) / 3
                    ).coerceIn(96.dp, 190.dp)
                // Portrait tablet single-pane: the schedule always shows all
                // six prayers, and its three rows absorb the height left after
                // the header (58), hero block (~454), schedule header (44),
                // location card (58), and paddings, so nothing pools below.
                // Budget: safe areas + header (58) + insights title (44) +
                // hero (400) + gaps + schedule header (52) + location (58) +
                // bottom-bar clearance (112) ≈ 810dp; rows share the rest.
                val portraitCardHeight = (
                    (layoutHeight - 810.dp) / 3
                    ).coerceIn(96.dp, 132.dp)
                Box(
                    modifier = Modifier
                        .widthIn(max = 1200.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .safeDrawingPadding()
                            .padding(
                                start = if (useSideNavigation) 80.dp else 24.dp,
                                end = 24.dp,
                            )
                            .padding(top = 8.dp),
                    ) {
                        PrayerHomeHeader(
                            onOpenSettings = onOpenSettings,
                            onOpenProfile = onOpenProfile,
                            onOpenSearch = { searchController?.open() ?: onOpenSearch() },
                            searchTerm = day.nextPrayer ?: day.currentPrayer,
                            onVoiceTap = { searchController?.openVoice() },
                            onShowProfile = { showProfileSheet = true },
                        )
                        if (showProfileSheet) {
                            HomeProfileSheet(
                                onOpenProfile = {
                                    showProfileSheet = false
                                    onOpenProfile()
                                },
                                onSelectSaved = {
                                    showProfileSheet = false
                                    onSelectBottom(2)
                                },
                                onOpenSettings = {
                                    showProfileSheet = false
                                    onOpenSettings()
                                },
                                onDismiss = { showProfileSheet = false },
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        if (useTwoPaneLayout) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                LazyColumn(
                                    modifier = Modifier.weight(if (isTablet) 5f else 1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 84.dp),
                                ) {
                                    item {
                                        InsightPager(
                                            onOpenFortressChapter = onOpenFortressChapter,
                                            onOpenBukhariBook = onOpenBukhariBook,
                                            onOpenShamayelBook = onOpenShamayelBook,
                                            day = day,
                                            placeName = placeName,
                                            salah = salah,
                                            onTogglePrayer = onTogglePrayer,
                                            today = today,
                                            latitude = latitude,
                                            longitude = longitude,
                                            quranPlayer = quranPlayer,
                                            onOpenQuran = onOpenQuran,
                                            onOpenQibla = onOpenQibla,
                                            onOpenRecommendation = onOpenRecommendation,
                                            notifications = notifications,
                                            tileHeight = landscapeInsightHeight,
                                            fullWidthPage = true,
                                            maxPageWidth = if (isTablet) 520.dp else null,
                                        )
                                    }
                                    item {
                                        LocationWeatherRow(
                                            placeName = placeName,
                                            temperatureCelsius = day.temperatureCelsius,
                                            conditionLabel = day.conditionLabel,
                                            isLocating = isLocating,
                                            compact = false,
                                            onRefresh = onRefresh,
                                        )
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier.weight(if (isTablet) 6f else 1f),
                                    contentPadding = PaddingValues(bottom = 84.dp),
                                ) {
                                    item {
                                        PrayerScheduleSection(
                                            day = day,
                                            offsets = offsets,
                                            showAllPrayers = true,
                                            onToggleExpanded = { showAllPrayers = !showAllPrayers },
                                            onAdjustPrayer = onAdjustPrayer,
                                            onTogglePrayerAdhan = onTogglePrayerAdhan,
                                            onAdhanVolumeChange = onAdhanVolumeChange,
 onOpenAdhanVolume = { name, volume -> activeAdhanVolume = name to volume },
                                            isTuning = isTuningSchedule,
                                            onToggleTuning = { isTuningSchedule = !isTuningSchedule },
                                            notifications = notifications,
                                            onTogglePrayerNotification = onTogglePrayerNotification,
                                            showExpandControl = false,
                                            compact = !isTablet,
                                            cardMinHeight = if (isTablet) tabletCardHeight else null,
                                        )
                                    }
                                }
                            }
                        } else if (isTabletPortrait) {
                            // Portrait tablets never need to scroll: every fixed block
                            // takes its natural height and the hero absorbs whatever
                            // the window measurement leaves over, so nothing can
                            // overlap the floating bar or pool at the bottom.
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    BoxWithConstraints(Modifier.fillMaxSize()) {
                                        // The pager draws its own 34dp title row plus
                                        // an 8dp gap above the artwork.
                                        val heroTile = (maxHeight - 42.dp).coerceAtLeast(220.dp)
                                        InsightPager(
                                            onOpenFortressChapter = onOpenFortressChapter,
                                            onOpenBukhariBook = onOpenBukhariBook,
                                            onOpenShamayelBook = onOpenShamayelBook,
                                            day = day,
                                            placeName = placeName,
                                            salah = salah,
                                            onTogglePrayer = onTogglePrayer,
                                            today = today,
                                            latitude = latitude,
                                            longitude = longitude,
                                            quranPlayer = quranPlayer,
                                            onOpenQuran = onOpenQuran,
                                            onOpenQibla = onOpenQibla,
                                            onOpenRecommendation = onOpenRecommendation,
                                            notifications = notifications,
                                            tileHeight = heroTile,
                                            maxPageWidth = 420.dp,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                PrayerScheduleSection(
                                    day = day,
                                    offsets = offsets,
                                    showAllPrayers = true,
                                    onToggleExpanded = { showAllPrayers = !showAllPrayers },
                                    onAdjustPrayer = onAdjustPrayer,
                                    onTogglePrayerAdhan = onTogglePrayerAdhan,
                                    onAdhanVolumeChange = onAdhanVolumeChange,
 onOpenAdhanVolume = { name, volume -> activeAdhanVolume = name to volume },
                                    isTuning = isTuningSchedule,
                                    onToggleTuning = { isTuningSchedule = !isTuningSchedule },
                                    notifications = notifications,
                                    onTogglePrayerNotification = onTogglePrayerNotification,
                                    showExpandControl = false,
                                    compact = false,
                                    cardMinHeight = 104.dp,
                                )
                                Spacer(Modifier.height(10.dp))
                                LocationWeatherRow(
                                    placeName = placeName,
                                    temperatureCelsius = day.temperatureCelsius,
                                    conditionLabel = day.conditionLabel,
                                    isLocating = isLocating,
                                    compact = false,
                                    onRefresh = onRefresh,
                                )
                                // Keeps the location card clear of the floating pill:
                                // 56dp pill + 8dp padding + safe-area breathing room.
                                Spacer(Modifier.height(88.dp))
                            }
                        } else {
                            // The pager and schedule scroll together on a phone so the
                            // artwork never leaves only a couple of prayer rows visible.
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 112.dp),
                            ) {
                                item {
                                    InsightPager(
                                        onOpenFortressChapter = onOpenFortressChapter,
                                        onOpenBukhariBook = onOpenBukhariBook,
                                        onOpenShamayelBook = onOpenShamayelBook,
                                        day = day,
                                        placeName = placeName,
                                        salah = salah,
                                        onTogglePrayer = onTogglePrayer,
                                        today = today,
                                        latitude = latitude,
                                        longitude = longitude,
                                        quranPlayer = quranPlayer,
                                        onOpenQuran = onOpenQuran,
                                        onOpenQibla = onOpenQibla,
                                        onOpenRecommendation = onOpenRecommendation,
                                        notifications = notifications,
                                        tileHeight = portraitInsightHeight,
                                    )
                                }
                                item {
                                    PrayerScheduleSection(
                                        day = day,
                                        offsets = offsets,
                                        showAllPrayers = showAllPrayers,
                                        onToggleExpanded = { showAllPrayers = !showAllPrayers },
                                        onAdjustPrayer = onAdjustPrayer,
                                        onTogglePrayerAdhan = onTogglePrayerAdhan,
                                        onAdhanVolumeChange = onAdhanVolumeChange,
 onOpenAdhanVolume = { name, volume -> activeAdhanVolume = name to volume },
                                        isTuning = isTuningSchedule,
                                        onToggleTuning = { isTuningSchedule = !isTuningSchedule },
                                        notifications = notifications,
                                        onTogglePrayerNotification = onTogglePrayerNotification,
                                        showExpandControl = true,
                                        compact = showAllPrayers,
                                        cardMinHeight = if (showAllPrayers) 88.dp else null,
                                    )
                                }
                                item {
                                    AnimatedVisibility(
                                        visible = !showAllPrayers,
                                        enter = expandVertically(
                                            animationSpec = tween(
                                                durationMillis = 840,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeIn(animationSpec = tween(durationMillis = 280)),
                                        exit = shrinkVertically(
                                            animationSpec = tween(
                                                durationMillis = 680,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeOut(animationSpec = tween(durationMillis = 180)),
                                    ) {
                                        LocationWeatherRow(
                                            placeName = placeName,
                                            temperatureCelsius = day.temperatureCelsius,
                                            conditionLabel = day.conditionLabel,
                                            isLocating = isLocating,
                                            compact = false,
                                            onRefresh = onRefresh,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // The search overlay itself lives at the NAV level
                    // (SharedSearchOverlay above every destination), so the
                    // home no longer renders a private copy.

                    // UIKit-backed per-prayer volume HUD. Hardware volume keys
                    // update the selected prayer while this native slider is open.
                    activeAdhanVolume?.let { active ->
                        val (prayerName, volume) = active
                        androidx.compose.runtime.key(prayerName) {
                            val capture = remember {
                                com.starception.submission.shared.voice.SystemVolumeCapture()
                            }
                            androidx.compose.runtime.DisposableEffect(prayerName) {
                                capture.start { percent ->
                                    onAdhanVolumeChange(prayerName, percent)
                                    activeAdhanVolume = prayerName to percent
                                }
                                onDispose { capture.stop() }
                            }
                            LaunchedEffect(prayerName, volume) {
                                kotlinx.coroutines.delay(2_400)
                                activeAdhanVolume = null
                            }
                            NativePrayerVolumeController(
                                prayerName = prayerName,
                                volume = volume,
                                onVolumeChange = { next ->
                                    onAdhanVolumeChange(prayerName, next)
                                    activeAdhanVolume = prayerName to next
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 20.dp, vertical = 116.dp)
                                    .zIndex(6f),
                            )
                        }
                    }

                    if (LocalShowBottomNavigation.current) {
                        if (useSideNavigation) {
                            FloatingSideBar(
                                items = SharedBottomBarItems,
                                selectedIndex = selectedBottomIndex,
                                onSelect = onSelectBottom,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        } else {
                            FloatingBottomBar(
                                items = SharedBottomBarItems,
                                selectedIndex = selectedBottomIndex,
                                onSelect = onSelectBottom,
                                // The floating voice button is voice SEARCH — the same
                                // shared surface as the header mic, matching Android.
                                onVoiceTap = { searchController?.openVoice() },
                                nudge = deenlyNudge,
                                onNudgeAction = {
                                    deenlyNudge?.let(::performNudgeAction)
                                },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showIslamicQuiz) {
        IslamicQuizDialog(onDismiss = { showIslamicQuiz = false })
    }
}

@Composable
private fun NativePrayerVolumeController(
    prayerName: String,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CupertinoTheme.colorScheme
    CupertinoSurface(
        shape = RoundedCornerShape(20.dp),
        color = colors.secondarySystemGroupedBackground,
        contentColor = colors.label,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth().widthIn(max = 420.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CupertinoText(
                    text = "$prayerName adhan volume",
                    style = CupertinoTheme.typography.subhead,
                    fontWeight = FontWeight.SemiBold,
                )
                CupertinoText(
                    text = if (volume == 0) "Muted" else "$volume%",
                    style = CupertinoTheme.typography.subhead,
                    color = colors.secondaryLabel,
                )
            }
            CupertinoSliderNative(
                value = volume.toFloat(),
                onValueChange = { onVolumeChange(it.roundToInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
                steps = 99,
                modifier = Modifier.fillMaxWidth(),
            )
            CupertinoText(
                text = "Drag the slider or use the volume buttons",
                style = CupertinoTheme.typography.footnote,
                color = colors.secondaryLabel,
            )
        }
    }
}

@Composable
internal fun screenCanvasBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    return if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                colorScheme.background,
                colorScheme.surface,
                colorScheme.primary.copy(alpha = 0.05f).compositeOver(colorScheme.surface),
                colorScheme.background,
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                colorScheme.background,
                colorScheme.surfaceContainerLow,
                colorScheme.secondary.copy(alpha = 0.14f)
                    .compositeOver(colorScheme.surfaceContainerLow),
            ),
        )
    }
}

@Composable
private fun PrayerHomeHeader(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
    searchTerm: String?,
    onVoiceTap: (() -> Unit)? = null,
    onShowProfile: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .layout { measurable, constraints ->
                val bleed = 8.dp.roundToPx()
                val expanded = constraints.copy(
                    minWidth = constraints.minWidth + (bleed * 2),
                    maxWidth = constraints.maxWidth + (bleed * 2),
                )
                val placeable = measurable.measure(expanded)
                layout(constraints.maxWidth, placeable.height) {
                    placeable.placeRelative(-bleed, 0)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTapTarget(
            // Android's logged-out state uses its filled circular profile asset.
            icon = Icons.Filled.AccountCircle,
            contentDescription = "Open local profile",
            tint = MaterialTheme.colorScheme.onBackground,
            visualSize = 34.dp,
            iconSize = 34.dp,
            showBackground = false,
            onClick = onShowProfile,
        )
        io.github.alexzhirkevich.cupertino.adaptive.AdaptiveWidget(
            material = {
                Surface(
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = if (onVoiceTap != null) 6.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = NiaIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = searchTerm?.let { "Search '$it'" } ?: "Search Quran, Hadith and more",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (onVoiceTap != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onVoiceTap),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Voice search",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            cupertino = {
                io.github.alexzhirkevich.cupertino.CupertinoSearchBarNative(
                    placeholder = searchTerm?.let { "Search '$it'" } ?: "Search",
                    onSearchClick = onOpenSearch,
                    onVoiceClick = onVoiceTap,
                    modifier = Modifier.weight(1f),
                )
            },
        )
        IconTapTarget(
            icon = Icons.Outlined.Settings,
            contentDescription = "Prayer settings",
            tint = MaterialTheme.colorScheme.onBackground,
            visualSize = 36.dp,
            iconSize = 26.dp,
            showBackground = false,
            onClick = onOpenSettings,
        )
    }
}

/** The SearchView overlay's scrollable suggestion list — Android's
 *  renderSuggestions sections: catalog names, full-text ayah and hadith
 *  matches, Fortress/Quranic duas, recent chips, and popular shortcuts. */
@Composable
internal fun SearchSuggestionsOverlayContent(
    query: String,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
    onOpenBukhariHadith: (Int) -> Unit,
    onOpenQuranicDua: (Int) -> Unit,
    onOpenFortressChapter: (Int) -> Unit,
    onOpenNews: (Int) -> Unit,
    onQuerySelected: (String) -> Unit,
    onRecordRecent: (String) -> Unit,
) {
    val trimmed = query.trim()
    val effectiveQuery = canonicalSearchQuery(trimmed)
    val expandedQueries = expandedSearchQueries(trimmed)
    val contentStore = remember { SharedContentStore() }
    val recents = remember { contentStore.recentSearches() }
    val newsRepository = remember { createSharedNewsRepository() }

    // Android's curated empty-state shortcuts, so the staples are one tap away.
    data class Popular(val label: String, val surah: Int, val ayah: Int? = null)
    val popular = remember {
        listOf(
            Popular("Ayatul Kursi", 2, 255),
            Popular("Al-Fatiha", 1),
            Popular("Ar-Rahman", 55),
            Popular("Surah Yasin", 36),
            Popular("Al-Kahf", 18),
            Popular("Al-Mulk", 67),
        )
    }

    // Catalog matches are synchronous; content searches run against the DBs.
    val catalog = remember(trimmed) {
        if (trimmed.isEmpty()) emptyList() else searchCatalog(effectiveQuery).take(4)
    }
    var verses by remember { mutableStateOf<List<QuranVerse>>(emptyList()) }
    var news by remember { mutableStateOf<List<SharedNewsResource>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    LaunchedEffect(trimmed) {
        if (trimmed.length < 2) {
            verses = emptyList()
            news = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        news = runCatching {
            buildList {
                addAll(newsRepository.searchNewsByType(expandedQueries, "Dua", 6))
                addAll(newsRepository.searchNewsByType(expandedQueries, "Hadith", 8))
                addAll(newsRepository.searchNewsByType(expandedQueries, "Surah", 4))
            }.distinctBy(SharedNewsResource::id)
        }.getOrDefault(emptyList())
        runCatching {
            verses = createQuranVerseRepository().searchAyahs(effectiveQuery, 4)
        }
        isSearching = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        if (trimmed.isEmpty()) {
            if (recents.isNotEmpty()) {
                SuggestionHeader("RECENT")
                SuggestionChips(
                    items = recents,
                    onClick = onQuerySelected,
                )
            }
            SuggestionHeader("POPULAR")
            SuggestionChips(
                items = popular.map { it.label },
                onClick = { label ->
                    onRecordRecent(label)
                    popular.firstOrNull { it.label == label }?.let {
                        onOpenSurah(it.surah)
                    }
                },
            )
        } else {
            catalog.forEach { result ->
                when (result) {
                    is com.starception.submission.shared.content.CatalogResult.Quran -> {
                        SuggestionRow(
                            badge = "${result.surah.number}",
                            title = result.surah.nameEnglish,
                            trailing = result.surah.nameArabic,
                            onClick = {
                                onRecordRecent(trimmed)
                                onOpenSurah(result.surah.number)
                            },
                        )
                    }
                    is com.starception.submission.shared.content.CatalogResult.Bukhari -> {
                        SuggestionRow(
                            badge = "${result.book.id}",
                            title = result.book.nameEnglish,
                            onClick = {
                                onRecordRecent(trimmed)
                                onOpenBukhariBook(result.book.id)
                            },
                        )
                    }
                }
            }
            if (news.isNotEmpty()) {
                listOf("Dua" to "DUAS", "Hadith" to "HADITHS", "Surah" to "SURAHS").forEach {
                        (type, heading) ->
                    val results = news.filter { displayNewsType(it.type) == type }
                    if (results.isNotEmpty()) {
                        SuggestionHeader(heading)
                        results.forEach { result ->
                            SuggestionRow(
                                badge = searchResultNumber(result),
                                title = result.title,
                                trailing = result.source.takeIf(String::isNotBlank),
                                onClick = {
                                    onRecordRecent(trimmed)
                                    contentStore.markNewsViewed(result.id)
                                    onOpenNews(result.id)
                                },
                            )
                        }
                    }
                }
            }
            if (verses.isNotEmpty()) {
                SuggestionHeader("QURAN VERSES")
                verses.forEach { verse ->
                    SuggestionRow(
                        badge = "${verse.surahNumber}:${verse.numberInSurah}",
                        title = verse.arabicText,
                        titleArabic = true,
                        onClick = {
                            onRecordRecent(trimmed)
                            onOpenSurah(verse.surahNumber)
                        },
                    )
                }
            }
            if (isSearching && catalog.isEmpty() && news.isEmpty() && verses.isEmpty()) {
                Text(
                    "Searching…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else if (catalog.isEmpty() && news.isEmpty() && verses.isEmpty()) {
                Text(
                    "No matches for \"$trimmed\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun searchResultNumber(news: SharedNewsResource): String =
    Regex("(?:Dua|Hadith|Surah)\\s+(\\d+)", RegexOption.IGNORE_CASE)
        .find(news.title)
        ?.groupValues
        ?.getOrNull(1)
        ?: news.id.toString()

@Composable
private fun SuggestionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SuggestionChips(items: List<String>, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            androidx.compose.material3.AssistChip(
                onClick = { onClick(item) },
                label = { Text(item, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    badge: String,
    title: String,
    trailing: String? = null,
    titleArabic: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            badge,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (titleArabic) TextAlign.End else null,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Android's ProfileSheet pattern: the avatar opens a sheet, not a page. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeProfileSheet(
    onOpenProfile: () -> Unit,
    onSelectSaved: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val store = remember { com.starception.submission.shared.content.SharedContentStore() }
    val profile = remember { store.profile() }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    profile.displayName.trim().firstOrNull()?.uppercase() ?: "R",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                profile.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onSelectSaved,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(NiaIcons.Bookmark, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Saved")
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Settings")
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open full profile")
            }
        }
    }
}

internal fun SharedPrayerDay.prayerAlertState(
    notifications: PrayerNotificationPreferences,
): PrayerAlertState {
    val prayers = slots.filterNot { it.name == "Sunrise" }
    currentPrayer
        ?.let { name -> prayers.firstOrNull { it.name == name } }
        ?.let { current ->
            val duration = notifications.getGoToMosqueDurationForPrayer(current.name)
            val elapsed = (nowMinute - (current.hour * 60 + current.minute))
                .mod(MINUTES_PER_DAY)
            val minutesLeft = duration - elapsed
            if (minutesLeft > 0) {
                return PrayerAlertState(
                    isActive = true,
                    prayerName = current.name,
                    phase = AlertPhase.GO_TO_MOSQUE,
                    countdownMinutes = minutesLeft,
                    totalMinutes = duration,
                    displayText = "${current.name} · Go now to mosque, ${minutesLeft}m left",
                )
            }
        }

    val next = nextPrayer
        ?.let { name -> prayers.firstOrNull { it.name == name } }
        ?: return PrayerAlertState()
    val minutesUntil = ((next.hour * 60 + next.minute) - nowMinute).mod(MINUTES_PER_DAY)
    val priorMinutes = notifications.getPriorMinutesForPrayer(next.name)
    if (minutesUntil !in 1..priorMinutes) {
        return PrayerAlertState()
    }

    return PrayerAlertState(
        isActive = true,
        prayerName = next.name,
        phase = AlertPhase.BEFORE_PRAYER,
        countdownMinutes = minutesUntil,
        totalMinutes = priorMinutes,
        displayText = "${next.name} in ${minutesUntil}m",
    )
}

private const val MINUTES_PER_DAY = 24 * 60

@Composable
private fun PrayerScheduleSection(
    day: SharedPrayerDay,
    offsets: PrayerTimeOffsets,
    showAllPrayers: Boolean,
    onToggleExpanded: () -> Unit,
    onAdjustPrayer: (prayer: String, delta: Int) -> Unit,
    isTuning: Boolean,
    onToggleTuning: () -> Unit,
    notifications: PrayerNotificationPreferences,
    onTogglePrayerNotification: (String) -> Unit,
    onTogglePrayerAdhan: (String) -> Unit,
    onAdhanVolumeChange: (String, Int) -> Unit = { _, _ -> },
    onOpenAdhanVolume: (String, Int) -> Unit = { _, _ -> },
    showExpandControl: Boolean,
    compact: Boolean,
    columns: Int = 2,
    cardMinHeight: Dp? = null,
) {
    var revealedCard by remember { mutableStateOf<RevealedPrayerCard?>(null) }

    LaunchedEffect(isTuning) {
        if (!isTuning) revealedCard = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Prayer times",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Today's schedule",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = {
                    revealedCard = null
                    onToggleTuning()
                },
                shape = CircleShape,
                color = if (isTuning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (isTuning) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier
                    .widthIn(min = if (compact) 126.dp else 148.dp)
                    .heightIn(min = if (compact) 40.dp else 44.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (compact) 5.dp else 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 26.dp else 32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTuning) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Same fade+scale morph as the bell <-> weather alert and
                        // the speaker/badge swaps on the tune tile.
                        androidx.compose.animation.AnimatedContent(
                            targetState = isTuning,
                            transitionSpec = {
                                (
                                    fadeIn(tween(220)) +
                                        scaleIn(
                                            initialScale = 0.72f,
                                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                                        )
                                    ) togetherWith (
                                    fadeOut(tween(150)) +
                                        scaleOut(
                                            targetScale = 0.78f,
                                            animationSpec = tween(190),
                                        )
                                    )
                            },
                            label = "tuneScheduleIconMorph",
                        ) { tuning ->
                            Icon(
                                imageVector = if (tuning) NiaIcons.Check else Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                            )
                        }
                    }
                    Text(
                        text = if (isTuning) "Done" else "Tune schedule",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        val prayerRows = day.dashboardSlots().chunked(columns.coerceIn(1, 2))
        // The first four prayers always stay visible; the rest sit behind the
        // Show All control, regardless of how many cards each row holds.
        val alwaysVisibleRows = (4 + columns - 1) / columns
        prayerRows.take(alwaysVisibleRows).forEach { pair ->
            PrayerCardRow(
                slots = pair,
                offsets = offsets,
                onAdjustPrayer = onAdjustPrayer,
                isTuning = isTuning,
                revealedCard = revealedCard,
                onRevealChange = { revealedCard = it },
                notifications = notifications,
                onTogglePrayerNotification = onTogglePrayerNotification,
                onTogglePrayerAdhan = onTogglePrayerAdhan,
                onOpenAdhanVolume = onOpenAdhanVolume,
                compact = compact,
                cardMinHeight = cardMinHeight,
            )
        }
        AnimatedVisibility(
            visible = showAllPrayers,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 840, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(durationMillis = 180)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prayerRows.drop(alwaysVisibleRows).forEach { pair ->
                    PrayerCardRow(
                        slots = pair,
                        offsets = offsets,
                        onAdjustPrayer = onAdjustPrayer,
                        onTogglePrayerAdhan = onTogglePrayerAdhan,
                        isTuning = isTuning,
                        revealedCard = revealedCard,
                        onRevealChange = { revealedCard = it },
                        notifications = notifications,
                        onTogglePrayerNotification = onTogglePrayerNotification,
                        onOpenAdhanVolume = onOpenAdhanVolume,
                        compact = compact,
                        cardMinHeight = cardMinHeight,
                    )
                }
            }
        }
        if (showExpandControl) {
            Box(
                modifier = Modifier.fillMaxWidth().height(40.dp),
            ) {
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.align(Alignment.Center).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = if (showAllPrayers) 180f else 0f },
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(if (showAllPrayers) "Show Less" else "Show All Prayers")
                }
                androidx.compose.animation.AnimatedContent(
                    targetState = showAllPrayers,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    transitionSpec = {
                        fadeIn(tween(280)) + scaleIn(
                            initialScale = 0.72f,
                            animationSpec = tween(840, easing = FastOutSlowInEasing),
                        ) togetherWith fadeOut(tween(180)) + scaleOut(
                            targetScale = 0.72f,
                            animationSpec = tween(680, easing = FastOutSlowInEasing),
                        )
                    },
                    label = "locationPinMorph",
                ) { expanded ->
                    if (expanded) {
                        LocationPinButton(
                            onClick = onToggleExpanded,
                            modifier = Modifier.offset(y = (-10).dp),
                        )
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerCardRow(
    slots: List<SharedPrayerSlot>,
    offsets: PrayerTimeOffsets,
    onAdjustPrayer: (prayer: String, delta: Int) -> Unit,
    isTuning: Boolean,
    revealedCard: RevealedPrayerCard?,
    onRevealChange: (RevealedPrayerCard?) -> Unit,
    notifications: PrayerNotificationPreferences,
    onTogglePrayerNotification: (String) -> Unit,
    onTogglePrayerAdhan: (String) -> Unit,
    onAdhanVolumeChange: (String, Int) -> Unit = { _, _ -> },
    onOpenAdhanVolume: (String, Int) -> Unit = { _, _ -> },
    compact: Boolean,
    cardMinHeight: Dp? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        slots.forEach { slot ->
            PrayerCard(
                slot = slot,
                offsetMinutes = offsets.getOffset(slot.name),
                onAdjust = { delta -> onAdjustPrayer(slot.name, delta) },
                isTuning = isTuning,
                revealedSide = revealedCard
                    ?.takeIf { it.prayerName == slot.name }
                    ?.side,
                onRevealChange = { side ->
                    onRevealChange(side?.let { RevealedPrayerCard(slot.name, it) })
                },
                notificationEnabled = notifications.isNotificationEnabledForPrayer(slot.name),
                onToggleNotification = { onTogglePrayerNotification(slot.name) },
                adhanEnabled = slot.name != "Sunrise" &&
                    notifications.isAdhanEnabledForPrayer(slot.name),
                onToggleAdhan = { onTogglePrayerAdhan(slot.name) },
                adhanVolume = notifications.getAdhanVolumeForPrayer(slot.name),
                onAdhanVolumeChange = { volume -> onAdhanVolumeChange(slot.name, volume) },
                onOpenAdhanVolume = onOpenAdhanVolume,
                compact = compact,
                minHeight = cardMinHeight,
                modifier = Modifier.weight(1f),
            )
        }
        if (slots.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PrayerCard(
    slot: SharedPrayerSlot,
    offsetMinutes: Int,
    onAdjust: (Int) -> Unit,
    isTuning: Boolean,
    revealedSide: PrayerCardRevealSide?,
    onRevealChange: (PrayerCardRevealSide?) -> Unit,
    notificationEnabled: Boolean,
    onToggleNotification: () -> Unit,
    adhanEnabled: Boolean,
    onToggleAdhan: () -> Unit,
    adhanVolume: Int = 5,
    onAdhanVolumeChange: (Int) -> Unit = {},
    onOpenAdhanVolume: (String, Int) -> Unit = { _, _ -> },
    compact: Boolean,
    minHeight: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = if (isDarkTheme) {
        when {
            slot.isCurrent -> MaterialTheme.colorScheme.tertiary
            slot.isNext -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        }
    } else {
        when {
            slot.isCurrent -> PrayerReferenceRust
            slot.isNext -> PrayerReferenceBlue
            slot.name == "Sunrise" -> PrayerReferenceGold
            else -> PrayerReferenceSlate
        }
    }
    val container = if (isDarkTheme) {
        when {
            slot.isCurrent -> lerp(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.tertiary,
                0.12f,
            )
            slot.isNext -> lerp(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.primary,
                0.10f,
            )
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    } else {
        when {
            slot.isCurrent -> lerp(PrayerReferenceCard, PrayerReferenceRust, 0.12f)
            slot.isNext -> lerp(PrayerReferenceCard, PrayerReferenceBlue, 0.11f)
            else -> PrayerReferenceCard
        }
    }
    val titleColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else PrayerReferenceInk
    val supportingColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        PrayerReferenceSlate
    }
    val hour12 = when {
        slot.hour == 0 -> 12
        slot.hour > 12 -> slot.hour - 12
        else -> slot.hour
    }
    val displayTime = "$hour12:${slot.minute.toString().padStart(2, '0')}"
    val period = if (slot.hour < 12) "AM" else "PM"
    val offsetLabel = formatOffset(offsetMinutes).ifEmpty { "±0m" }
    val adjustWidth = 112.dp
    val resetWidth = 80.dp
    val density = LocalDensity.current
    val adjustWidthPx = with(density) { adjustWidth.toPx() }
    val resetWidthPx = with(density) { resetWidth.toPx() }
    var dragOffset by remember(slot.name) { mutableFloatStateOf(0f) }
    val restingOffset = when (revealedSide) {
        PrayerCardRevealSide.Adjust -> -adjustWidthPx
        PrayerCardRevealSide.Reset -> resetWidthPx
        null -> 0f
    }
    val animatedOffset by animateFloatAsState(
        targetValue = restingOffset + dragOffset,
        label = "${slot.name}SwipeOffset",
    )
    val cardShape = RoundedCornerShape(if (compact) 20.dp else 28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Fixed height: the card's reveal layers use fillMaxSize, which
            // would otherwise expand to any loose parent constraint.
            .height(minHeight ?: if (compact) 78.dp else 96.dp)
            .clip(cardShape),
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = resetWidth, max = resetWidth)
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .clickable(enabled = isTuning) {
                        onAdjust(-offsetMinutes)
                        onRevealChange(null)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = adjustWidth, max = adjustWidth)
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = isTuning) { onAdjust(-1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Decrease ${slot.name} time",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = isTuning) { onAdjust(1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Increase ${slot.name} time",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(slot.name, isTuning, revealedSide) {
                    if (!isTuning) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = when (revealedSide) {
                                PrayerCardRevealSide.Adjust -> adjustWidthPx * 0.25f
                                PrayerCardRevealSide.Reset -> resetWidthPx * 0.25f
                                null -> minOf(adjustWidthPx, resetWidthPx) * 0.25f
                            }
                            when {
                                revealedSide == PrayerCardRevealSide.Adjust &&
                                    dragOffset > threshold -> onRevealChange(null)
                                revealedSide == PrayerCardRevealSide.Reset &&
                                    dragOffset < -threshold -> onRevealChange(null)
                                revealedSide == null && dragOffset < -threshold ->
                                    onRevealChange(PrayerCardRevealSide.Adjust)
                                revealedSide == null && dragOffset > threshold ->
                                    onRevealChange(PrayerCardRevealSide.Reset)
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { _, dragAmount ->
                        dragOffset = when (revealedSide) {
                            PrayerCardRevealSide.Adjust ->
                                (dragOffset + dragAmount).coerceIn(0f, adjustWidthPx)
                            PrayerCardRevealSide.Reset ->
                                (dragOffset + dragAmount).coerceIn(-resetWidthPx, 0f)
                            null -> (dragOffset + dragAmount)
                                .coerceIn(-adjustWidthPx, resetWidthPx)
                        }
                    }
                }
                .combinedClickable(
                    enabled = isTuning,
                    onClick = {
                        if (revealedSide != null) onRevealChange(null)
                    },
                    onDoubleClick = {
                        onAdjust(-offsetMinutes)
                        onRevealChange(null)
                    },
                ),
            shape = cardShape,
            color = container,
            border = if (isDarkTheme) {
                BorderStroke(
                    width = 1.dp,
                    color = when {
                        slot.isCurrent -> accentColor.copy(alpha = 0.38f)
                        slot.isNext -> accentColor.copy(alpha = 0.32f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                    },
                )
            } else {
                null
            },
            tonalElevation = if (isDarkTheme) 1.dp else 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            color = accentColor.copy(alpha = if (isDarkTheme) 0.13f else 0.065f),
                            radius = size.minDimension * 0.42f,
                            center = Offset(size.width * 0.96f, size.height * 0.98f),
                        )
                    }
                    .padding(
                        start = if (compact) 12.dp else 14.dp,
                        end = if (compact) 10.dp else 14.dp,
                        top = if (compact) 6.dp else 8.dp,
                        bottom = if (compact) 6.dp else 8.dp,
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = slot.name,
                            style = if (compact) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (slot.name != "Sunrise") {
                            Box(
                                modifier = Modifier
                                    .size(if (compact) 28.dp else 32.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = isTuning, onClick = onToggleNotification),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = notificationEnabled,
                                    transitionSpec = {
                                        (
                                            fadeIn(tween(220)) +
                                                scaleIn(
                                                    initialScale = 0.72f,
                                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                                )
                                            ) togetherWith (
                                            fadeOut(tween(150)) +
                                                scaleOut(
                                                    targetScale = 0.78f,
                                                    animationSpec = tween(190),
                                                )
                                            )
                                    },
                                    label = "${slot.name}BellToggleMorph",
                                ) { bellEnabled ->
                                    FlaticonIcon(
                                        glyph = if (bellEnabled) {
                                            FlaticonIcons.NOTIFICATIONS_ACTIVE
                                        } else {
                                            FlaticonIcons.NOTIFICATIONS
                                        },
                                        contentDescription = if (bellEnabled) {
                                            "Disable ${slot.name} notification"
                                        } else {
                                            "Enable ${slot.name} notification"
                                        },
                                        tint = accentColor.copy(
                                            alpha = if (bellEnabled) 0.9f else 0.25f,
                                        ),
                                        fontSize = if (compact) 13.sp else 16.sp,
                                    )
                                }
                            }
                        }
                    }
                    if (!compact && slot.localName.isNotEmpty()) {
                        Text(
                            text = slot.localName,
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = displayTime,
                            style = if (compact) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        Spacer(Modifier.size(3.dp))
                        Text(
                            text = period,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = accentColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    // The offset badge spot: while tuning, it morphs into this
                    // prayer's adhan speaker toggle (mirroring the Android tile);
                    // outside tuning it is the "+Xm" offset badge.
                    androidx.compose.animation.AnimatedContent(
                        targetState = isTuning && slot.name != "Sunrise",
                        transitionSpec = {
                            (
                                fadeIn(tween(220)) +
                                    scaleIn(
                                        initialScale = 0.72f,
                                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    )
                                ) togetherWith (
                                fadeOut(tween(150)) +
                                    scaleOut(
                                        targetScale = 0.78f,
                                        animationSpec = tween(190),
                                    )
                                )
                        },
                        label = "tileAdhanBadgeMorph",
                    ) { showAdhanControl ->
                        if (showAdhanControl) {
                            // iOS equivalent of Android's system volume bar: the
                            // tile cannot surface the OS volume HUD, so tapping
                            // the speaker opens an inline slider whose percent
                            // picks the pre-scaled adhan at scheduling.
                            Box(
                                modifier = Modifier
                                    .size(if (compact) 28.dp else 32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        accentColor.copy(
                                            alpha = if (adhanVolume > 0) 0.12f else 0.04f,
                                        ),
                                    )
                                    .clickable { onOpenAdhanVolume(slot.name, adhanVolume) },
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = adhanVolume > 0,
                                    transitionSpec = {
                                        (
                                            fadeIn(tween(220)) +
                                                scaleIn(
                                                    initialScale = 0.72f,
                                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                                )
                                            ) togetherWith (
                                            fadeOut(tween(150)) +
                                                scaleOut(
                                                    targetScale = 0.78f,
                                                    animationSpec = tween(190),
                                                )
                                            )
                                    },
                                    label = "${slot.name}SpeakerToggleMorph",
                                ) { audible ->
                                    // The settings' on/mute flaticons — one set everywhere.
                                    androidx.compose.material3.Icon(
                                        painter = painterResource(
                                            if (audible) {
                                                ImageRes.drawable.flaticon_sound_14925297
                                            } else {
                                                ImageRes.drawable.flaticon_sound_14925198
                                            },
                                        ),
                                        contentDescription = if (audible) {
                                            "Adjust ${slot.name} adhan volume"
                                        } else {
                                            "${slot.name} adhan is muted — tap to raise the volume"
                                        },
                                        tint = accentColor.copy(alpha = if (audible) 0.9f else 0.45f),
                                        modifier = Modifier.size(if (compact) 15.dp else 18.dp),
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.08f))
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = offsetLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accentColor.copy(alpha = 0.78f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A minus/plus control for nudging a prayer by a minute.
 *
 * The Android app does this with a long-press circular dial, which is a better
 * fit for a large adjustment. Steppers are the honest stand-in until that dial is
 * ported: they reach the same stored value, one minute at a time.
 */
@Composable
@OptIn(ExperimentalAdaptiveApi::class, ExperimentalCupertinoApi::class)
internal fun IconTapTarget(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    visualSize: Dp = 48.dp,
    iconSize: Dp = if (visualSize < 48.dp) 24.dp else 26.dp,
    showBackground: Boolean = true,
    onClick: () -> Unit,
) {
    AdaptiveWidget(
        material = {
            Box(
                modifier = modifier
                    .size(48.dp)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(visualSize)
                        .clip(CircleShape)
                        .background(if (showBackground) tint.copy(alpha = 0.08f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = tint,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        },
        cupertino = {
            CupertinoIconButton(
                onClick = onClick,
                modifier = modifier.size(48.dp),
                colors = plainButtonColors(),
            ) {
                CupertinoIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
        },
    )
}

/**
 * Where the times are for, and what the sky is doing there.
 *
 * Sits below the schedule as on Android: it answers "where is this?", which only
 * matters once the times themselves have been read.
 */
@Composable
private fun LocationWeatherRow(
    placeName: String,
    temperatureCelsius: Double?,
    conditionLabel: String,
    isLocating: Boolean,
    compact: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        Box(
            modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            LocationPinButton(onClick = onRefresh)
        }
        return
    }
    val placeParts = remember(placeName) { placeName.split(',', limit = 2).map(String::trim) }
    val placeTitle = placeParts.firstOrNull().orEmpty().ifEmpty { placeName }
    val placeDetail = placeParts.getOrNull(1).orEmpty()
    AdaptiveWidget(
        material = {
            Surface(
                onClick = onRefresh,
                modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                shadowElevation = 0.dp,
            ) {
                LocationWeatherContent(
                    placeTitle = placeTitle,
                    placeDetail = placeDetail,
                    temperatureCelsius = temperatureCelsius,
                    conditionLabel = conditionLabel,
                    isLocating = isLocating,
                    cupertino = false,
                )
            }
        },
        cupertino = {
            val colors = CupertinoTheme.colorScheme
            CupertinoSurface(
                onClick = onRefresh,
                modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.secondarySystemGroupedBackground,
                contentColor = colors.label,
                border = BorderStroke(0.5.dp, colors.separator),
            ) {
                LocationWeatherContent(
                    placeTitle = placeTitle,
                    placeDetail = placeDetail,
                    temperatureCelsius = temperatureCelsius,
                    conditionLabel = conditionLabel,
                    isLocating = isLocating,
                    cupertino = true,
                )
            }
        },
    )
}

@Composable
@OptIn(ExperimentalAdaptiveApi::class)
private fun LocationPinButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveWidget(
        material = {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = modifier.size(40.dp),
            ) {
                LocationMarkerArtwork(
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp),
                )
            }
        },
        cupertino = {
            val colors = CupertinoTheme.colorScheme
            CupertinoSurface(
                onClick = onClick,
                shape = CircleShape,
                color = colors.quaternarySystemFill,
                contentColor = colors.accent,
                modifier = modifier.size(40.dp),
            ) {
                LocationMarkerArtwork(
                    tint = colors.accent,
                    modifier = Modifier.padding(9.dp),
                )
            }
        },
    )
}

@Composable
private fun LocationWeatherContent(
    placeTitle: String,
    placeDetail: String,
    temperatureCelsius: Double?,
    conditionLabel: String,
    isLocating: Boolean,
    cupertino: Boolean,
) {
    val accent = if (cupertino) CupertinoTheme.colorScheme.accent else MaterialTheme.colorScheme.primary
    val primaryText = if (cupertino) CupertinoTheme.colorScheme.label else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (cupertino) {
        CupertinoTheme.colorScheme.secondaryLabel
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocationMarkerArtwork(
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    if (cupertino) {
                        CupertinoText(
                            text = if (isLocating) "$placeTitle · Locating" else placeTitle,
                            style = CupertinoTheme.typography.subhead,
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = if (isLocating) "$placeTitle · Locating" else placeTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 17.sp,
                            ),
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (placeDetail.isNotEmpty()) {
                        if (cupertino) {
                            CupertinoText(
                                text = placeDetail,
                                style = CupertinoTheme.typography.footnote,
                                color = secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text = placeDetail,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                ),
                                color = secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // Only shown once the forecast has arrived; an empty slot reads
            // better than a placeholder temperature that might be wrong.
            if (temperatureCelsius != null) {
                Spacer(Modifier.size(12.dp))
                Icon(
                    imageVector = Icons.Outlined.Thermostat,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    modifier = Modifier.padding(start = 6.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    if (cupertino) {
                        CupertinoText(
                            text = "${temperatureCelsius.toInt()}\u00B0C",
                            style = CupertinoTheme.typography.subhead,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                        )
                    } else {
                        Text(
                            text = "${temperatureCelsius.toInt()}\u00B0C",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                        )
                    }
                    if (conditionLabel.isNotEmpty()) {
                        if (cupertino) {
                            CupertinoText(
                                text = conditionLabel,
                                style = CupertinoTheme.typography.footnote,
                                color = secondaryText,
                            )
                        } else {
                            Text(
                                text = conditionLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = secondaryText.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
}
