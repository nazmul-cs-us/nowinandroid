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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.prayer.model.PrayerTimeOffsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.LocationOn
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.settings.formatOffset
import kotlinx.datetime.LocalDate
import com.starception.submission.shared.SharedPrayerSlot

// Shared copies of the Android dashboard's reference palette. Keeping these
// values identical makes light-mode prayer status read the same on both hosts.
private val PrayerReferenceInk = Color(0xFF0A0808)
private val PrayerReferenceCard = Color(0xFFFFFDF7)
private val PrayerReferenceSlate = Color(0xFF5D6574)
private val PrayerReferenceBlue = Color(0xFF4F779D)
private val PrayerReferenceRust = Color(0xFF99593C)
private val PrayerReferenceGold = Color(0xFFD8AB59)

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
    modifier: Modifier = Modifier,
    isLocating: Boolean = false,
) {
    var showAllPrayers by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Prayer Times",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // An icon, not a "\u2699" glyph: Ubuntu Sans has no gear, so the
                // character rendered as a missing-glyph box.
                IconTapTarget(
                    icon = NiaIcons.Settings,
                    contentDescription = "Prayer settings",
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = onOpenSettings,
                )
            }
            Text(
                // Marked while the fix is pending, because the times on screen
                // are for the fallback location and silently showing them as if
                // they were the user's would be the worst of the options.
                text = if (isLocating) "$placeName · locating…" else placeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            // The pager and the schedule scroll together: on a phone the tiles
            // alone fill most of the screen, so a fixed header would leave the
            // list a few rows tall.
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
                    )
                }
                item {
                    Text(
                        text = "Prayer times",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Two to a row, as on Android: the schedule is glanceable rather
                // than a list to read down.
                items(day.slots.take(if (showAllPrayers) 6 else 4).chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { slot ->
                            PrayerCard(
                                slot = slot,
                                offsetMinutes = offsets.getOffset(slot.name),
                                onAdjust = { delta -> onAdjustPrayer(slot.name, delta) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps the last tile half-width when the count is odd,
                        // instead of stretching it across the row.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                item {
                    TextButton(
                        onClick = { showAllPrayers = !showAllPrayers },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text(if (showAllPrayers) "Show Less" else "Show All Prayers")
                    }
                }

                item {
                    LocationWeatherRow(
                        placeName = placeName,
                        temperatureCelsius = day.temperatureCelsius,
                        conditionLabel = day.conditionLabel,
                    )
                }
            }
        }

        FloatingBottomBar(
            items = SharedBottomBarItems,
            selectedIndex = 0,
            onSelect = { },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        }
    }
}

@Composable
private fun PrayerCard(
    slot: SharedPrayerSlot,
    offsetMinutes: Int,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = isSystemInDarkTheme()
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

    Surface(
        modifier = modifier.fillMaxWidth().height(104.dp),
        shape = RoundedCornerShape(28.dp),
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
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (slot.name != "Sunrise") {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Prayer notification",
                            tint = accentColor.copy(alpha = 0.42f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (slot.localName.isNotEmpty()) {
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
                        style = MaterialTheme.typography.titleLarge,
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
                        .combinedClickable(
                            onClick = { onAdjust(1) },
                            onLongClick = { onAdjust(-1) },
                        )
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
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = placeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
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
