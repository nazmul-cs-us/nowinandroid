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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.dashboardSlots
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.feature.prayertimes.wobble.AlertPhase
import com.starception.submission.feature.prayertimes.wobble.PrayerAlertState
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.settings.formatOffset
import kotlinx.datetime.LocalDate
import com.starception.submission.shared.SharedPrayerSlot
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ExpandMore
import kotlin.math.roundToInt

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
    onOpenProfile: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onVoiceTap: (() -> Unit)? = null,
    onOpenQuran: (Int) -> Unit = {},
    onOpenQibla: () -> Unit = {},
    onOpenRecommendation: () -> Unit = {},
    selectedBottomIndex: Int = 0,
    onSelectBottom: (Int) -> Unit = {},
    quranPlayer: QuranAudioPlayer = LocalQuranAudioPlayer.current,
) {
    var showAllPrayers by remember { mutableStateOf(false) }
    var isTuningSchedule by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val homeCanvas = if (isDarkTheme) {
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
    val nextPrayerState = remember(day, notifications) {
        day.prayerAlertState(notifications)
    }

    PullToSyncContainer(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        syncResultText = syncResultText,
        prayerAlertState = nextPrayerState,
        modifier = modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(homeCanvas)) {
                // Requiring room in both axes keeps wide, short phones out of tablet sizing.
                val isTablet = maxWidth >= 600.dp && maxHeight >= 600.dp
                val isTabletPortrait = isTablet && maxWidth <= maxHeight
                // The phone and tablet share one design language: identical
                // cards at identical aspect ratios. Portrait tablets therefore
                // reuse the single-pane layout and simply show more of the
                // carousel; only landscape tablets split into two panes.
                val useTwoPaneLayout = isTablet && !isTabletPortrait
                val useSideNavigation = isTablet
                // iOS has taller status/navigation safe areas than Android. Reserving
                // their measured space keeps the location card above the floating bar.
                val portraitInsightHeight = if (isTablet) {
                    // Carousel pages keep the phone card's 250:288 aspect; the
                    // height just scales up so pages stay proportionate.
                    (maxHeight * 0.34f).coerceIn(320.dp, 400.dp)
                } else {
                    (maxHeight - 652.dp).coerceIn(192.dp, 280.dp)
                }
                // Measured two-pane sizing: both panes share the height below
                // the search header (~150dp of status bar, header, paddings).
                val paneContentHeight = maxHeight - 150.dp
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
                    (maxHeight - 182.dp).coerceIn(220.dp, 400.dp)
                }
                // Landscape schedule: three rows of two cards split the pane
                // minus the schedule header (44) and 8dp row gaps evenly.
                val tabletCardHeight = (
                    (paneContentHeight - 44.dp - (8.dp * 3)) / 3
                ).coerceIn(96.dp, 190.dp)
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
                    onOpenSearch = onOpenSearch,
                    searchTerm = day.nextPrayer ?: day.currentPrayer,
                    onVoiceTap = onVoiceTap,
                )
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
                                    isTuning = isTuningSchedule,
                                    onToggleTuning = { isTuningSchedule = !isTuningSchedule },
                                    notifications = notifications,
                                    onTogglePrayerNotification = onTogglePrayerNotification,
                                    showExpandControl = false,
                                    compact = !isTablet,
                                    // Portrait tablets have a tall, narrow right
                                    // pane; one card per row fills it evenly.
                                    columns = if (isTabletPortrait) 1 else 2,
                                    cardMinHeight = if (isTablet) tabletCardHeight else null,
                                )
                            }
                        }
                    }
                } else {
                    // The pager and schedule scroll together on a phone so the
                    // artwork never leaves only a couple of prayer rows visible.
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 112.dp),
                    ) {
                        item {
                            InsightPager(
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
                                maxPageWidth = if (isTablet) 420.dp else null,
                            )
                        }
                        item {
                            PrayerScheduleSection(
                                day = day,
                                offsets = offsets,
                                showAllPrayers = showAllPrayers,
                                onToggleExpanded = { showAllPrayers = !showAllPrayers },
                                onAdjustPrayer = onAdjustPrayer,
                                isTuning = isTuningSchedule,
                                onToggleTuning = { isTuningSchedule = !isTuningSchedule },
                                notifications = notifications,
                                    onTogglePrayerNotification = onTogglePrayerNotification,
                                    showExpandControl = true,
                                    compact = false,
                                )
                        }
                        item {
                            LocationWeatherRow(
                                placeName = placeName,
                                temperatureCelsius = day.temperatureCelsius,
                                conditionLabel = day.conditionLabel,
                                isLocating = isLocating,
                                compact = showAllPrayers,
                                onRefresh = onRefresh,
                            )
                        }
                    }
                }
                    }

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
                            onVoiceTap = onVoiceTap,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerHomeHeader(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
    searchTerm: String?,
    onVoiceTap: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
            onClick = onOpenProfile,
        )
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

private fun SharedPrayerDay.prayerAlertState(
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
                        Icon(
                            imageVector = if (isTuning) NiaIcons.Check else Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                        )
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
                        isTuning = isTuning,
                        revealedCard = revealedCard,
                        onRevealChange = { revealedCard = it },
                        notifications = notifications,
                        onTogglePrayerNotification = onTogglePrayerNotification,
                        compact = compact,
                        cardMinHeight = cardMinHeight,
                    )
                }
            }
        }
        if (showExpandControl) {
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.weight(1f).height(32.dp),
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
            .heightIn(min = minHeight ?: if (compact) 78.dp else 96.dp)
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
                                FlaticonIcon(
                                    glyph = if (notificationEnabled) {
                                        FlaticonIcons.NOTIFICATIONS_ACTIVE
                                    } else {
                                        FlaticonIcons.NOTIFICATIONS
                                    },
                                    contentDescription = if (notificationEnabled) {
                                        "Disable ${slot.name} notification"
                                    } else {
                                        "Enable ${slot.name} notification"
                                    },
                                    tint = accentColor.copy(
                                        alpha = if (notificationEnabled) 0.9f else 0.25f,
                                    ),
                                    fontSize = if (compact) 13.sp else 16.sp,
                                )
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

/**
 * A minus/plus control for nudging a prayer by a minute.
 *
 * The Android app does this with a long-press circular dial, which is a better
 * fit for a large adjustment. Steppers are the honest stand-in until that dial is
 * ported: they reach the same stored value, one minute at a time.
 */
@Composable
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
            Surface(
                onClick = onRefresh,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                LocationMarkerArtwork(
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp),
                )
            }
        }
        return
    }
    val placeParts = remember(placeName) { placeName.split(',', limit = 2).map(String::trim) }
    val placeTitle = placeParts.firstOrNull().orEmpty().ifEmpty { placeName }
    val placeDetail = placeParts.getOrNull(1).orEmpty()
    Surface(
        onClick = onRefresh,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
        shadowElevation = 0.dp,
    ) {
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        text = if (isLocating) "$placeTitle · Locating" else placeTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                        ),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (placeDetail.isNotEmpty()) {
                        Text(
                            text = placeDetail,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    modifier = Modifier.padding(start = 6.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "${temperatureCelsius.toInt()}\u00B0C",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (conditionLabel.isNotEmpty()) {
                        Text(
                            text = conditionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
