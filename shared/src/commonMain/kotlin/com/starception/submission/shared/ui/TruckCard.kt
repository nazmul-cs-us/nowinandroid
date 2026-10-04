/*
 * Copyright 2026 The Android Open Source Project
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.starception.submission.shared.content.CONTEXTUAL_DUA_CHAPTER_IDS
import com.starception.submission.shared.content.createSharedFortressRepository
import com.starception.submission.shared.content.contextualRecommendation
import com.starception.submission.feature.quran.dailyReading
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.salah.FARD_PRAYERS
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The Food Truck card component — Apple's sample renders each dashboard card
 * as a rounded surface with a borderless tappable header (icon + label) and
 * free content beneath. This is the shared Compose counterpart every
 * overhauled surface composes with.
 */
@Composable
internal fun TruckCard(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { row ->
                        if (onHeaderClick != null) {
                            row.clickable(onClick = onHeaderClick)
                        } else {
                            row
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The accent dot stands in for Food Truck's card header icon.
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, CircleShape),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
            if (footer != null) {
                Spacer(Modifier.height(10.dp))
                footer()
            }
        }
    }
}

/** A Food Truck card's small caption footer with a light divider above it. */
@Composable
internal fun TruckCardFooter(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/* ─────────────────── the Food Truck home dashboard ─────────────────── */

/**
 * The home page as Food Truck's Truck view: an adaptive card grid — prayer
 * now, today's salah, reading, Qibla, the AI suggestion, weather — with the
 * day's schedule as a full-width card beneath. Each card is a [TruckCard]
 * with a borderless header; tapping a card navigates like the sample's
 * CardNavigationHeader.
 */
@Composable
internal fun TruckHomeDashboard(
    day: com.starception.submission.shared.SharedPrayerDay,
    salah: com.starception.submission.shared.salah.SalahProgress,
    placeName: String,
    latitude: Double,
    longitude: Double,
    today: kotlinx.datetime.LocalDate,
    isLocating: Boolean,
    onRefresh: () -> Unit,
    onTogglePrayer: (String) -> Unit,
    onOpenQuran: (Int) -> Unit,
    onOpenQibla: () -> Unit,
    onOpenFortressChapter: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
    onOpenShamayelBook: (Int) -> Unit,
    modifier: Modifier = Modifier,
    schedule: (@Composable () -> Unit)? = null,
) {
    val quranPlayer = LocalQuranAudioPlayer.current
    val repository = remember { createSharedFortressRepository() }
    var fortressInvocations by remember {
        mutableStateOf<Map<Int, List<com.starception.submission.shared.content.FortressInvocation>>>(emptyMap())
    }
    var fortressTitles by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(today) {
        runCatching {
            repository.getChapters().forEach { chapter ->
                fortressTitles = fortressTitles + (chapter.id to chapter.title)
            }
            CONTEXTUAL_DUA_CHAPTER_IDS.forEach { chapterId ->
                runCatching {
                    fortressInvocations =
                        fortressInvocations + (chapterId to repository.getChapterInvocations(chapterId))
                }
            }
        }
    }
    val currentHour = remember(today) {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    }
    val recommendation = remember(today, currentHour, fortressInvocations) {
        contextualRecommendation(
            date = today,
            hour = currentHour,
            fortressInvocationsByChapter = fortressInvocations,
            chapterTitles = fortressTitles,
        )
    }
    val qiblaBearing = remember(latitude, longitude) {
        qiblaBearing(latitude, longitude).toInt()
    }
    val readingSurah = remember(today) { dailyReading(today) }
    var isReadingAudio by remember { mutableStateOf(false) }

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(340.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        item {
            TruckCard(
                title = "Prayer now",
                accent = MaterialTheme.colorScheme.tertiary,
                footer = {
                    TruckCardFooter {
                        Text(
                            "Open the live compass",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onHeaderClick = onOpenQibla,
            ) {
                Text(
                    day.nextPrayer?.let { "$it in ${day.countdown}" } ?: "Times updated",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                day.currentPrayer?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Now: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            TruckCard(
                title = "Today's salah",
                accent = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    salah.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    salah.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SalahMarkers(
                    completed = salah.completed,
                    available = day.slots
                        .filter { it.hasStarted && it.name in FARD_PRAYERS }
                        .mapTo(mutableSetOf()) { it.name },
                    onToggle = onTogglePrayer,
                )
            }
        }

        item {
            TruckCard(
                title = "Today's reading",
                accent = MaterialTheme.colorScheme.secondary,
                trailing = {
                    Surface(
                        onClick = {
                            quranPlayer.stop()
                            if (isReadingAudio) {
                                isReadingAudio = false
                            } else {
                                isReadingAudio = quranPlayer.play(quranAudioUrl(readingSurah.number))
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Text(
                            if (isReadingAudio) "❚❚" else "▶",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                onHeaderClick = {
                    quranPlayer.stop()
                    isReadingAudio = false
                    onOpenQuran(readingSurah.number)
                },
            ) {
                Text(
                    readingSurah.nameEnglish,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    readingSurah.nameArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TruckCard(
                title = "Qibla",
                accent = MaterialTheme.colorScheme.tertiary,
                footer = {
                    TruckCardFooter {
                        Text(
                            "Open the live compass and 3D globe",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onHeaderClick = onOpenQibla,
            ) {
                Text(
                    "$qiblaBearing° toward Makkah",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item {
            TruckCard(
                title = "AI suggested",
                accent = MaterialTheme.colorScheme.primary,
                footer = {
                    TruckCardFooter {
                        Text(
                            recommendation.footerText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onHeaderClick = {
                    when (val target = recommendation.target) {
                        is com.starception.submission.shared.content.ContextualRecommendation.Target.Surah ->
                            onOpenQuran(target.number)
                        is com.starception.submission.shared.content.ContextualRecommendation.Target.FortressDua ->
                            onOpenFortressChapter(target.chapterId)
                        is com.starception.submission.shared.content.ContextualRecommendation.Target.Bukhari ->
                            onOpenBukhariBook(target.bookId)
                        is com.starception.submission.shared.content.ContextualRecommendation.Target.Shamayel ->
                            onOpenShamayelBook(target.bookId)
                    }
                },
            ) {
                Text(
                    recommendation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    recommendation.supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        schedule?.let { section ->
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                section()
            }
        }

        item {
            TruckCard(
                title = placeName.ifEmpty { "Locating…" },
                accent = MaterialTheme.colorScheme.primary,
                footer = {
                    TruckCardFooter {
                        Text(
                            if (isLocating) "Finding your location" else "Tap to refresh location and times",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onHeaderClick = onRefresh,
            ) {
                val temperature = day.temperatureCelsius
                Text(
                    if (temperature != null) {
                        "${temperature.toInt()}° · ${day.conditionLabel}"
                    } else {
                        day.conditionLabel.ifEmpty { "Weather unavailable" }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
