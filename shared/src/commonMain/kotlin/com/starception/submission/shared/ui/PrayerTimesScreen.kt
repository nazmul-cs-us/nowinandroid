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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.settings.formatOffset
import kotlinx.datetime.LocalDate
import com.starception.submission.shared.SharedPrayerSlot

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
    modifier: Modifier = Modifier,
    isLocating: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Text(
                text = "Prayer Times",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                // Marked while the fix is pending, because the times on screen
                // are for the fallback location and silently showing them as if
                // they were the user's would be the worst of the options.
                text = if (isLocating) "$placeName · locating…" else placeName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // The pager and the schedule scroll together: on a phone the tiles
            // alone fill most of the screen, so a fixed header would leave the
            // list a few rows tall.
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(day.slots) { slot ->
                    PrayerCard(
                        slot = slot,
                        offsetMinutes = offsets.getOffset(slot.name),
                        onAdjust = { delta -> onAdjustPrayer(slot.name, delta) },
                    )
                }
            }
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
    val isCurrent = slot.isCurrent
    val container = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = slot.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = content,
                )
                val label = formatOffset(offsetMinutes)
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.7f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AdjustButton(symbol = "\u2212", tint = content) { onAdjust(-1) }
                Text(
                    text = slot.display,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                AdjustButton(symbol = "+", tint = content) { onAdjust(1) }
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
private fun AdjustButton(
    symbol: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = tint,
        )
    }
}
