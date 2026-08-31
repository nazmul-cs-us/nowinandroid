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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.dashboardSlots
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.LocationOn
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.settings.formatOffset
import kotlinx.datetime.LocalDate
import com.starception.submission.shared.SharedPrayerSlot
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
    onOpenQuran: (Int) -> Unit = {},
    onOpenQibla: () -> Unit = {},
    onOpenRecommendation: () -> Unit = {},
    selectedBottomIndex: Int = 0,
    onSelectBottom: (Int) -> Unit = {},
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

    PullToSyncContainer(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        syncResultText = syncResultText,
        modifier = modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(homeCanvas)) {
            val useTwoPaneLayout = maxWidth > maxHeight
            val useSideNavigation = useTwoPaneLayout
            val portraitInsightHeight = (maxHeight - 576.dp).coerceIn(208.dp, 288.dp)
            val landscapeInsightHeight = (maxHeight - 182.dp).coerceIn(220.dp, 560.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
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
                )
                Spacer(Modifier.height(10.dp))

                if (useTwoPaneLayout) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
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
                                    onOpenQuran = onOpenQuran,
                                    onOpenQibla = onOpenQibla,
                                    onOpenRecommendation = onOpenRecommendation,
                                    tileHeight = landscapeInsightHeight,
                                    isLandscape = true,
                                )
                            }
                            item {
                                LocationWeatherRow(
                                    placeName = placeName,
                                    temperatureCelsius = day.temperatureCelsius,
                                    conditionLabel = day.conditionLabel,
                                    isLocating = isLocating,
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
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
                                    compact = true,
                                    onRefresh = onRefresh,
                                )
                            }
                        }
                    }
                } else {
                    // The pager and schedule scroll together on a phone so the
                    // artwork never leaves only a couple of prayer rows visible.
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                onOpenQuran = onOpenQuran,
                                onOpenQibla = onOpenQibla,
                                onOpenRecommendation = onOpenRecommendation,
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
                                isTuning = isTuningSchedule,
                                onToggleTuning = { isTuningSchedule = !isTuningSchedule },
                                notifications = notifications,
                                onTogglePrayerNotification = onTogglePrayerNotification,
                                showExpandControl = true,
                                compact = false,
                                onRefresh = onRefresh,
                            )
                        }
                        if (!showAllPrayers) {
                            item {
                                LocationWeatherRow(
                                    placeName = placeName,
                                    temperatureCelsius = day.temperatureCelsius,
                                    conditionLabel = day.conditionLabel,
                                    isLocating = isLocating,
                                )
                            }
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
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTapTarget(
            icon = NiaIcons.Person,
            contentDescription = "Open local profile",
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = onOpenProfile,
        )
        Surface(
            onClick = onOpenSearch,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = NiaIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Search Quran, Hadith and more",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconTapTarget(
            icon = NiaIcons.Settings,
            contentDescription = "Prayer settings",
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = onOpenSettings,
        )
    }
}

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
    onRefresh: () -> Unit,
) {
    var revealedCard by remember { mutableStateOf<RevealedPrayerCard?>(null) }

    LaunchedEffect(isTuning) {
        if (!isTuning) revealedCard = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Prayer times",
                    style = if (compact) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.Bold,
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
                modifier = Modifier.height(if (compact) 36.dp else 42.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isTuning) NiaIcons.Check else NiaIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                    )
                    Text(
                        text = if (isTuning) "Done" else "Tune schedule",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        val prayerRows = day.dashboardSlots().chunked(2)
        prayerRows.take(2).forEach { pair ->
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
            prayerRows.getOrNull(2)?.let { pair ->
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
                )
            }
        }
        if (showExpandControl) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showAllPrayers) {
                    Surface(
                        onClick = onRefresh,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "Refresh location",
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(if (showAllPrayers) "Show less" else "Show all prayers")
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
                .height(if (compact) 78.dp else 106.dp)
            .clip(cardShape),
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
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = if (notificationEnabled) {
                                        "Disable ${slot.name} notification"
                                    } else {
                                        "Enable ${slot.name} notification"
                                    },
                                    tint = accentColor.copy(
                                        alpha = if (notificationEnabled) 0.9f else 0.25f,
                                    ),
                                    modifier = Modifier.size(if (compact) 18.dp else 20.dp),
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
                    if (isTuning || offsetMinutes != 0) {
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
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (isLocating) "$placeName · Locating" else placeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
            }
            // Only shown once the forecast has arrived; an empty slot reads
            // better than a placeholder temperature that might be wrong.
            if (temperatureCelsius != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${temperatureCelsius.toInt()}\u00B0C",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (conditionLabel.isNotEmpty()) {
                        Text(
                            text = conditionLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
