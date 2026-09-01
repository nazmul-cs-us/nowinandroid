/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.model.data.BukhariBook
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.feature.quran.Surah
import com.starception.submission.feature.quran.subtitle
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.shared.content.CatalogResult
import com.starception.submission.shared.content.DailyRecommendation
import com.starception.submission.shared.content.LocalProfile
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.content.SharedTopic
import com.starception.submission.shared.content.SharedTopicArticle
import com.starception.submission.shared.content.SharedTopics
import com.starception.submission.shared.content.createSharedTopicRepository
import com.starception.submission.shared.content.dailyRecommendation
import com.starception.submission.shared.content.searchCatalog
import com.starception.submission.shared.content.sharedTopic
import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.createQuranVerseRepository
import com.starception.submission.shared.quran.filterQuranVerses
import com.starception.submission.shared.quran.metadataLabel
import com.starception.submission.shared.hadith.SharedHadith
import com.starception.submission.shared.hadith.createSharedHadithRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

internal data class CourseLesson(val number: Int, val title: String, val summary: String)

internal val SharedCourseLessons = listOf(
    CourseLesson(1, "Begin with intention", "Set a specific, realistic purpose for daily worship."),
    CourseLesson(2, "Build around the prayers", "Use the five prayers as dependable anchors in the day."),
    CourseLesson(3, "Read consistently", "A small daily Quran practice is easier to sustain than occasional bursts."),
    CourseLesson(4, "Study with context", "Keep chapter and collection context visible while learning."),
    CourseLesson(5, "Review and continue", "Notice what helped, then choose the next small action."),
)

private sealed interface QuranAyahState {
    data object Loading : QuranAyahState
    data class Loaded(val verses: List<QuranVerse>) : QuranAyahState
    data class Error(val message: String) : QuranAyahState
}

private sealed interface TopicArticlesState {
    data object Loading : TopicArticlesState
    data class Loaded(val articles: List<SharedTopicArticle>) : TopicArticlesState
    data class Error(val message: String) : TopicArticlesState
}

private sealed interface HadithsState {
    data object Loading : HadithsState
    data class Loaded(val hadiths: List<SharedHadith>) : HadithsState
    data class Error(val message: String) : HadithsState
}

@Composable
internal fun SearchScreen(
    onBack: () -> Unit,
    onOpenQuranLibrary: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { searchCatalog(query) }
    SharedDetailScaffold(title = "Search", onBack = onBack) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search Quran and Bukhari") },
            leadingIcon = { Icon(NiaIcons.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Search all Quran surahs and Sahih al-Bukhari books"
            },
        )
        Spacer(Modifier.height(12.dp))
        when {
            query.isBlank() -> SupportingCard(
                title = "Two complete catalogs",
                body = "Search all 114 Quran chapters and all 97 Sahih al-Bukhari books by name or number. Tap to browse the Quran library.",
                onClick = onOpenQuranLibrary,
            )
            results.isEmpty() -> SupportingCard(
                title = "No catalog matches",
                body = "Try a chapter name, Bukhari book topic, or catalog number.",
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(results) { result ->
                    when (result) {
                        is CatalogResult.Quran -> SurahRow(
                            surah = result.surah,
                            saved = false,
                            onClick = { onOpenSurah(result.surah.number) },
                        )
                        is CatalogResult.Bukhari -> BukhariBookRow(
                            book = result.book,
                            saved = false,
                            onClick = { onOpenBukhariBook(result.book.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileScreen(
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    var profile by remember { mutableStateOf(store.profile()) }
    var saved by remember { mutableStateOf(false) }
    SharedDetailScaffold(title = "Local profile", onBack = onBack) {
        SupportingCard(
            title = "Private on this device",
            body = "No external account is connected. These preferences are stored locally and are not synced.",
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = profile.displayName,
            onValueChange = { profile = profile.copy(displayName = it.take(40)); saved = false },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Text("Daily reading goal", style = MaterialTheme.typography.titleMedium)
        Text(
            "${profile.dailyReadingGoalMinutes} minutes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = profile.dailyReadingGoalMinutes.toFloat(),
            onValueChange = {
                profile = profile.copy(dailyReadingGoalMinutes = it.roundToInt())
                saved = false
            },
            valueRange = 5f..60f,
            steps = 10,
            modifier = Modifier.semantics { contentDescription = "Daily Quran reading goal" },
        )
        Button(
            onClick = { store.saveProfile(profile); profile = store.profile(); saved = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saved) "Saved locally" else "Save preferences")
        }
    }
}

@Composable
internal fun QuranLibraryScreen(
    store: SharedContentStore,
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(store.bookmarkedSurahs()) }
    val surahs = remember(query) {
        val term = query.trim()
        QuranData.surahs.filter {
            term.isEmpty() || term in it.nameArabic ||
                term.lowercase() in it.nameEnglish.lowercase() || it.number == term.toIntOrNull()
        }
    }
    SharedDetailScaffold(title = "The Quran", onBack = onBack) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search 114 surahs") },
            leadingIcon = { Icon(NiaIcons.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(surahs, key = { it.number }) { surah ->
                SurahRow(
                    surah = surah,
                    saved = surah.number in saved,
                    onClick = { onOpenSurah(surah.number) },
                    onToggleSaved = { saved = store.toggleSurah(surah.number) },
                )
            }
        }
    }
}

@Composable
internal fun QuranDetailScreen(
    number: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    val surah = QuranData.surahs.firstOrNull { it.number == number }
    if (surah == null) {
        SharedDetailScaffold(title = "Quran", onBack = onBack) { Text("Surah not found") }
        return
    }
    var saved by remember(number) { mutableStateOf(number in store.bookmarkedSurahs()) }
    var playing by remember(number) { mutableStateOf(false) }
    var query by remember(number) { mutableStateOf("") }
    var loadAttempt by remember(number) { mutableStateOf(0) }
    var ayahState by remember(number) { mutableStateOf<QuranAyahState>(QuranAyahState.Loading) }
    val repository = remember { createQuranVerseRepository() }
    val player = remember { QuranAudioPlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    LaunchedEffect(number, loadAttempt) {
        ayahState = QuranAyahState.Loading
        ayahState = try {
            val verses = repository.getVersesBySurah(number)
            if (verses.isEmpty()) {
                QuranAyahState.Error("No ayahs were found for this surah.")
            } else {
                QuranAyahState.Loaded(verses)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            QuranAyahState.Error(error.message ?: "The Quran database could not be read.")
        }
    }
    var showTranslation by remember(number) { mutableStateOf(true) }
    SharedDetailScaffold(title = surah.nameEnglish, onBack = onBack) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Surah ${surah.number} · ${surah.nameEnglish}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        surah.subtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    surah.nameArabic,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (playing) {
                        player.pause()
                        playing = false
                    } else {
                        playing = player.play(quranAudioUrl(number))
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                Spacer(Modifier.size(6.dp))
                Text(if (playing) "Pause" else "Play")
            }
            OutlinedButton(
                onClick = { saved = number in store.toggleSurah(number) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(if (saved) NiaIcons.Bookmark else NiaIcons.BookmarkBorder, null)
                Spacer(Modifier.size(6.dp))
                Text(if (saved) "Saved" else "Save")
            }
        }
        Spacer(Modifier.height(10.dp))
        when (val state = ayahState) {
            QuranAyahState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Loading Quran ayahs" },
                )
            }
            is QuranAyahState.Error -> Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SupportingCard(title = "Unable to load ayahs", body = state.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadAttempt++ }) { Text("Try again") }
            }
            is QuranAyahState.Loaded -> {
                val filteredVerses = remember(state.verses, query) {
                    filterQuranVerses(state.verses, query)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search ayah, Arabic, or translation") },
                    leadingIcon = { Icon(NiaIcons.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (query.isBlank()) "${state.verses.size} ayahs" else "${filteredVerses.size} matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (showTranslation) "Double-tap to hide translation" else "Double-tap to show translation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (filteredVerses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No matching ayahs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(filteredVerses, key = { it.id }) { verse ->
                            QuranAyahReadingBlock(
                                verse = verse,
                                showTranslation = showTranslation,
                                onToggleTranslation = { showTranslation = !showTranslation },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuranAyahReadingBlock(
    verse: QuranVerse,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onDoubleClick = onToggleTranslation)
            .semantics {
            contentDescription = verse.metadataLabel()
        }
            .padding(horizontal = 4.dp, vertical = 14.dp),
    ) {
        Text(
            verse.metadataLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${verse.arabicText} \u06DD${verse.numberInSurah.toArabicIndicDigits()}",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 30.sp,
            lineHeight = 48.sp,
            textAlign = TextAlign.Justify,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showTranslation && verse.translation.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${verse.translation} \u06DD${verse.numberInSurah}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

private fun Int.toArabicIndicDigits(): String = toString().map { digit ->
    if (digit in '0'..'9') ('٠'.code + (digit - '0')).toChar() else digit
}.joinToString("")

@Composable
internal fun BukhariBookDetailScreen(
    id: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
    onOpenHadith: (Int) -> Unit,
) {
    val book = BukhariBooks.find(id)
    if (book == null) {
        SharedDetailScaffold(title = "Sahih al-Bukhari", onBack = onBack) { Text("Book not found") }
        return
    }
    var saved by remember(id) { mutableStateOf(id in store.savedBukhariBooks()) }
    var query by remember(id) { mutableStateOf("") }
    var loadAttempt by remember(id) { mutableStateOf(0) }
    var state by remember(id) { mutableStateOf<HadithsState>(HadithsState.Loading) }
    val repository = remember { createSharedHadithRepository() }
    LaunchedEffect(id, loadAttempt) {
        state = try {
            val hadiths = repository.getHadiths(book.firstHadithId, book.lastHadithId)
            if (hadiths.isEmpty()) HadithsState.Error("No narrations were found for this book.")
            else HadithsState.Loaded(hadiths)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            HadithsState.Error(error.message ?: "The Sahih al-Bukhari database could not be read.")
        }
    }
    SharedDetailScaffold(title = book.nameEnglish, onBack = onBack) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopicArtwork("Sahih Bukhari", Modifier.size(56.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(book.nameEnglish, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        book.nameArabic,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${book.hadithCount} hadiths · ${book.firstHadithId}–${book.lastHadithId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FilledIconToggleButton(
                    checked = saved,
                    onCheckedChange = { saved = id in store.toggleBukhariBook(id) },
                ) {
                    Icon(if (saved) NiaIcons.Bookmark else NiaIcons.BookmarkBorder, "Save book")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when (val current = state) {
            HadithsState.Loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is HadithsState.Error -> Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SupportingCard("Unable to load this book", current.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadAttempt++ }) { Text("Try again") }
            }
            is HadithsState.Loaded -> {
                val filtered = remember(current.hadiths, query) {
                    val term = query.trim().lowercase()
                    if (term.isEmpty()) current.hadiths else current.hadiths.filter {
                        it.id.toString() == term || it.english.lowercase().contains(term) || it.arabic.contains(query.trim())
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search this book") },
                    leadingIcon = { Icon(NiaIcons.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    if (query.isBlank()) "${current.hadiths.size} hadiths" else "${filtered.size} matches",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(7.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    items(filtered, key = { it.id }) { hadith ->
                        BukhariHadithTile(hadith, onClick = { onOpenHadith(hadith.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BukhariHadithTile(hadith: SharedHadith, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            ReaderTag("HADITH ${hadith.id}")
            if (hadith.arabic.isNotBlank()) {
                Text(
                    hadith.arabic,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    fontSize = 22.sp,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.End,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hadith.english.isNotBlank()) {
                Text(
                    hadith.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun BukhariHadithDetailScreen(
    hadithId: Int,
    onBack: () -> Unit,
) {
    val book = BukhariBooks.findByHadithId(hadithId)
    val repository = remember { createSharedHadithRepository() }
    var state by remember(hadithId) { mutableStateOf<HadithsState>(HadithsState.Loading) }
    LaunchedEffect(hadithId) {
        state = try {
            repository.getHadith(hadithId)?.let { HadithsState.Loaded(listOf(it)) }
                ?: HadithsState.Error("Hadith $hadithId was not found.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            HadithsState.Error(error.message ?: "Unable to read this hadith.")
        }
    }
    SharedDetailScaffold(title = "Hadith $hadithId", onBack = onBack) {
        when (val current = state) {
            HadithsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is HadithsState.Error -> SupportingCard("Unable to load hadith", current.message)
            is HadithsState.Loaded -> {
                val hadith = current.hadiths.first()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item {
                        NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)))
                    }
                    item {
                        Column {
                            Text("Sahih al-Bukhari", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReaderTag("HADITH $hadithId")
                                if (book != null) {
                                    ReaderTag("BOOK ${book.id} · ${book.nameEnglish.uppercase()}", selected = false)
                                }
                            }
                        }
                    }
                    if (hadith.arabic.isNotBlank()) item {
                        ReaderSection("Arabic", MaterialTheme.colorScheme.primary) {
                            Text(
                                hadith.arabic,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 29.sp,
                                lineHeight = 46.sp,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    if (hadith.english.isNotBlank()) item {
                        ReaderSection("English translation", MaterialTheme.colorScheme.secondary) {
                            Text(hadith.english, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                        }
                    }
                    if (hadith.explanation.isNotBlank()) item {
                        ReaderSection("Explanation", MaterialTheme.colorScheme.tertiary) {
                            Text(hadith.explanation, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecommendationScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
) {
    val recommendation = remember(date) { dailyRecommendation(date) }
    SharedDetailScaffold(title = "Daily suggestion", onBack = onBack) {
        Text(recommendation.category, color = MaterialTheme.colorScheme.primary)
        Text(
            recommendation.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(recommendation.summary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        SupportingCard(
            title = "How this was selected",
            body = "${recommendation.reason} This is a deterministic on-device recommendation, not a response from a remote AI service.",
        )
        Spacer(Modifier.height(16.dp))
        RecommendationAction(recommendation, onOpenSurah, onOpenBukhariBook)
    }
}

@Composable
private fun RecommendationAction(
    recommendation: DailyRecommendation,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
) {
    when {
        recommendation.surahNumber != null -> Button(
            onClick = { onOpenSurah(recommendation.surahNumber) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Quran detail") }
        recommendation.bukhariBookId != null -> Button(
            onClick = { onOpenBukhariBook(recommendation.bukhariBookId) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Bukhari book") }
    }
}

@Composable
internal fun ForYouScreen(
    date: LocalDate,
    store: SharedContentStore,
    onOpenRecommendation: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onSelectBottom: (Int) -> Unit,
) {
    val recommendation = remember(date) { dailyRecommendation(date) }
    val interests = store.interests()
    TopLevelScaffold(title = "For you", selectedIndex = 1, onSelectBottom = onSelectBottom) {
        item {
            SupportingCard(
                title = recommendation.title,
                body = recommendation.summary,
                onClick = onOpenRecommendation,
            )
        }
        item {
            val surah = QuranData.surahs[(date.day - 1) % QuranData.surahs.size]
            SupportingCard(
                title = "Continue with ${surah.nameEnglish}",
                body = "A daily chapter chosen from the shared Quran catalog.",
                onClick = { onOpenSurah(surah.number) },
            )
        }
        item {
            SupportingCard(
                title = "Your interests",
                body = interests.takeIf { it.isNotEmpty() }
                    ?.mapNotNull { id -> sharedTopic(id.toIntOrNull() ?: -1)?.name }
                    ?.sorted()
                    ?.joinToString()
                    ?: "Choose topics in Interests to shape this local feed.",
            )
        }
    }
}

@Composable
internal fun SavedScreen(
    store: SharedContentStore,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
    onSelectBottom: (Int) -> Unit,
) {
    val surahs = store.bookmarkedSurahs()
    val books = store.savedBukhariBooks()
    TopLevelScaffold(title = "Saved", selectedIndex = 2, onSelectBottom = onSelectBottom) {
        if (surahs.isEmpty() && books.isEmpty()) {
            item {
                SupportingCard(
                    title = "Nothing saved yet",
                    body = "Bookmark a Quran chapter or Bukhari book and it will stay here across launches.",
                )
            }
        }
        items(QuranData.surahs.filter { it.number in surahs }) {
            SurahRow(it, saved = true, onClick = { onOpenSurah(it.number) })
        }
        items(BukhariBooks.all.filter { it.id in books }) {
            BukhariBookRow(it, saved = true, onClick = { onOpenBukhariBook(it.id) })
        }
    }
}

@Composable
internal fun CourseScreen(store: SharedContentStore, onSelectBottom: (Int) -> Unit) {
    var completed by remember { mutableStateOf(store.completedLessons()) }
    TopLevelScaffold(title = "Course", selectedIndex = 3, onSelectBottom = onSelectBottom) {
        item {
            Text(
                "Foundations · ${completed.size}/${SharedCourseLessons.size} complete",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(SharedCourseLessons, key = { it.number }) { lesson ->
            SupportingCard(
                title = "${lesson.number}. ${lesson.title}",
                body = lesson.summary,
                action = if (lesson.number in completed) "Completed" else "Mark complete",
                onAction = { completed = store.toggleLesson(lesson.number) },
            )
        }
    }
}

@Composable
internal fun InterestsScreen(
    store: SharedContentStore,
    onSelectBottom: (Int) -> Unit,
    onOpenTopic: (Int) -> Unit,
) {
    val repository = remember { createSharedTopicRepository() }
    var topics by remember { mutableStateOf(SharedTopics) }
    var selected by remember { mutableStateOf(store.interests()) }
    LaunchedEffect(repository) {
        topics = runCatching { repository.topics() }
            .getOrDefault(SharedTopics)
            .ifEmpty { SharedTopics }
    }
    TopLevelScaffold(
        title = "Interests",
        selectedIndex = 4,
        onSelectBottom = onSelectBottom,
        itemSpacing = 0.dp,
    ) {
        items(topics, key = { it.id }) { topic ->
            TopicInterestRow(
                topic = topic,
                following = topic.id.toString() in selected,
                onOpen = { onOpenTopic(topic.id) },
                onToggle = { selected = store.toggleInterest(topic.id.toString()) },
            )
        }
    }
}

@Composable
private fun TopicInterestRow(
    topic: SharedTopic,
    following: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    ListItem(
        leadingContent = {
            TopicArtwork(topic.name, Modifier.size(48.dp).padding(2.dp))
        },
        headlineContent = {
            Text(topic.name, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(topic.shortDescription, style = MaterialTheme.typography.bodyMedium)
        },
        trailingContent = {
            FilledIconToggleButton(
                checked = following,
                onCheckedChange = { onToggle() },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = if (following) NiaIcons.Check else NiaIcons.Add,
                    contentDescription = if (following) {
                        "Unfollow ${topic.name}"
                    } else {
                        "Follow ${topic.name}"
                    },
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            // TopLevelScaffold already supplies 16 dp; Android's list supplies 24 dp.
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .semantics {
                contentDescription = "${topic.name} topic${if (following) ", followed" else ""}"
            }
            .clickable(onClick = onOpen),
    )
}

@Composable
internal fun TopicNewsScreen(
    topicId: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
    onOpenArticle: (Int, Int) -> Unit,
) {
    val repository = remember { createSharedTopicRepository() }
    var topic by remember(topicId) { mutableStateOf(sharedTopic(topicId)) }
    var state by remember(topicId) { mutableStateOf<TopicArticlesState>(TopicArticlesState.Loading) }
    var followedTopics by remember { mutableStateOf(store.interests()) }
    var savedArticles by remember { mutableStateOf(store.bookmarkedTopicArticles()) }

    LaunchedEffect(topicId, repository) {
        topic = runCatching { repository.topics().firstOrNull { it.id == topicId } }
            .getOrNull() ?: sharedTopic(topicId)
        if (topicId != 7 && topicId != 8) {
            state = runCatching { repository.articles(topicId) }
                .fold(
                    onSuccess = { TopicArticlesState.Loaded(it) },
                    onFailure = { TopicArticlesState.Error(it.message ?: "Unable to read topic content") },
                )
        }
    }

    val currentTopic = topic
    if (currentTopic == null) {
        SharedDetailScaffold(title = "Topic", onBack = onBack) {
            SupportingCard("Topic unavailable", "This topic is not part of the current catalog.")
        }
        return
    }

    TopicPageScaffold(
        followed = currentTopic.id.toString() in followedTopics,
        onBack = onBack,
        onFollowChanged = {
            followedTopics = store.toggleInterest(currentTopic.id.toString())
        },
    ) {
        if (topicId != 8) {
            item { TopicPageHeader(currentTopic) }
        }
        when (topicId) {
            7 -> items(QuranData.surahs, key = { it.number }) { surah ->
                QuranNewsCard(surah = surah, onClick = { onOpenSurah(surah.number) })
            }
            8 -> items(BukhariBooks.all, key = { it.id }) { book ->
                BukhariBookRow(
                    book = book,
                    saved = book.id in store.savedBukhariBooks(),
                    onClick = { onOpenBukhariBook(book.id) },
                )
            }
            else -> when (val current = state) {
                TopicArticlesState.Loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                is TopicArticlesState.Error -> item {
                    Box(Modifier.padding(horizontal = 24.dp)) {
                        SupportingCard("Unable to load news", current.message)
                    }
                }
                is TopicArticlesState.Loaded -> {
                    if (current.articles.isEmpty()) {
                        item {
                            Box(Modifier.padding(horizontal = 24.dp)) {
                                SupportingCard("No content yet", "There are no items in this topic.")
                            }
                        }
                    } else {
                        items(current.articles, key = { it.id }) { article ->
                            val articleKey = "$topicId:${article.id}"
                            TopicArticleRow(
                                article = article,
                                topic = currentTopic,
                                bookmarked = articleKey in savedArticles,
                                onToggleBookmark = {
                                    savedArticles = store.toggleTopicArticle(topicId, article.id)
                                },
                                onClick = { onOpenArticle(topicId, article.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicPageScaffold(
    followed: Boolean,
    onBack: () -> Unit,
    onFollowChanged: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Column(
                modifier = Modifier
                    .widthIn(max = if (wide) 900.dp else 680.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconTapTarget(
                        icon = NiaIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = onBack,
                    )
                    FilterChip(
                        selected = followed,
                        onClick = onFollowChanged,
                        label = { Text(if (followed) "FOLLOWING" else "NOT FOLLOWING") },
                        leadingIcon = if (followed) {
                            { Icon(NiaIcons.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else {
                            { Icon(NiaIcons.Add, contentDescription = null, Modifier.size(18.dp)) }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.padding(end = 24.dp),
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun TopicPageHeader(topic: SharedTopic) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        TopicArtwork(
            topicName = topic.name,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(132.dp).padding(bottom = 12.dp),
        )
        Text(topic.name, style = MaterialTheme.typography.displayMedium)
        if (topic.longDescription.isNotBlank()) {
            Text(
                topic.longDescription,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TopicArticleRow(
    article: SharedTopicArticle,
    topic: SharedTopic,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            NewsHeaderArtwork(
                resourceName = "masjid_al_nawabi",
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconToggleButton(
                        checked = bookmarked,
                        onCheckedChange = { onToggleBookmark() },
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = if (bookmarked) NiaIcons.Bookmark else NiaIcons.BookmarkBorder,
                            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Dua 🤲", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(14.dp))
                if (article.arabic.isNotBlank()) {
                    Text(
                        article.arabic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    article.translation.ifBlank { article.context },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        topic.name.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuranNewsCard(surah: Surah, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            NewsHeaderArtwork(
                resourceName = if (surah.revelationType == "Medinan") {
                    "masjid_al_nawabi"
                } else {
                    "masjid_al_haram"
                },
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Surah ${surah.number}: ${surah.nameEnglish}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(14.dp))
                Text("Surah 📖", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(14.dp))
                Text(
                    surah.nameArabic,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Read and listen to ${surah.nameEnglish}, the ${surah.number.ordinal()} chapter of the Holy Quran.",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        "HOLY QURAN",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun Int.ordinal(): String {
    val suffix = when {
        this % 100 in 11..13 -> "th"
        this % 10 == 1 -> "st"
        this % 10 == 2 -> "nd"
        this % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$this$suffix"
}

@Composable
internal fun TopicArticleDetailScreen(
    topicId: Int,
    articleId: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    val topic = sharedTopic(topicId)
    val repository = remember { createSharedTopicRepository() }
    var state by remember(topicId, articleId) {
        mutableStateOf<TopicArticlesState>(TopicArticlesState.Loading)
    }
    LaunchedEffect(topicId, articleId, repository) {
        state = runCatching { repository.articles(topicId) }
            .fold(
                onSuccess = { TopicArticlesState.Loaded(it) },
                onFailure = { TopicArticlesState.Error(it.message ?: "Unable to read this item") },
            )
    }

    SharedDetailScaffold(title = topic?.name ?: "Reading", onBack = onBack) {
        when (val current = state) {
            TopicArticlesState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is TopicArticlesState.Error -> SupportingCard("Unable to load item", current.message)
            is TopicArticlesState.Loaded -> {
                val article = current.articles.firstOrNull { it.id == articleId }
                if (article == null) {
                    SupportingCard("Item unavailable", "This item is no longer in the topic database.")
                } else {
                    var saved by remember(topicId, articleId) {
                        mutableStateOf("$topicId:$articleId" in store.bookmarkedTopicArticles())
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        item {
                            NewsHeaderArtwork(
                                resourceName = "masjid_al_nawabi",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        article.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(7.dp))
                                    ReaderTag((topic?.name ?: "DUA").uppercase())
                                }
                                FilledIconToggleButton(
                                    checked = saved,
                                    onCheckedChange = {
                                        saved = "$topicId:$articleId" in store.toggleTopicArticle(topicId, articleId)
                                    },
                                ) {
                                    Icon(if (saved) NiaIcons.Bookmark else NiaIcons.BookmarkBorder, "Save dua")
                                }
                            }
                        }
                        if (article.context.isNotBlank()) item {
                            ReaderSection("Context", MaterialTheme.colorScheme.secondary) {
                                Text(article.context, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                            }
                        }
                        if (article.arabic.isNotBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                                        Text(
                                            "ARABIC",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            article.arabic,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontSize = 31.sp,
                                            lineHeight = 49.sp,
                                            textAlign = TextAlign.End,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                        if (article.transliteration.isNotBlank()) {
                            item {
                                ReaderSection("Transliteration", MaterialTheme.colorScheme.secondary) {
                                    Text(
                                        article.transliteration,
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (article.translation.isNotBlank()) {
                            item {
                                ReaderSection("Translation", MaterialTheme.colorScheme.primary) {
                                    Text(article.translation, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                                }
                            }
                        }
                        if (article.instruction.isNotBlank()) {
                            item {
                                ReaderSection("Guidance", MaterialTheme.colorScheme.tertiary) {
                                    Text(article.instruction, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
                                }
                            }
                        }
                        if (article.additionalContext.isNotBlank()) {
                            item {
                                ReaderSection("Additional context", MaterialTheme.colorScheme.tertiary) {
                                    Text(article.additionalContext, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
                                }
                            }
                        }
                        if (article.reference.isNotBlank()) {
                            item {
                                ReaderSection("Reference", MaterialTheme.colorScheme.primary) {
                                    Text(
                                        article.reference,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSection(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 15.dp, vertical = 14.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

/** Same compact filled/unfilled tag treatment used by NiA topic chips. */
@Composable
private fun ReaderTag(text: String, selected: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SharedDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Column(
                modifier = Modifier
                    .widthIn(max = if (wide) 900.dp else 680.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
            ) {
                ScreenHeader(title, onBack)
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun TopLevelScaffold(
    title: String,
    selectedIndex: Int,
    onSelectBottom: (Int) -> Unit,
    itemSpacing: Dp = 10.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
            ) {
                ScreenHeader(title)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                    content = content,
                )
            }
            FloatingBottomBar(
                items = SharedBottomBarItems,
                selectedIndex = selectedIndex,
                onSelect = onSelectBottom,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            IconTapTarget(
                icon = NiaIcons.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = onBack,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SurahRow(
    surah: Surah,
    saved: Boolean,
    onClick: () -> Unit,
    onToggleSaved: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Surah ${surah.number}, ${surah.nameEnglish}"
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(surah.number.toString())
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(surah.nameEnglish, fontWeight = FontWeight.SemiBold)
                Text(surah.nameArabic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onToggleSaved != null) {
                IconTapTarget(
                    icon = if (saved) NiaIcons.Bookmark else NiaIcons.BookmarkBorder,
                    contentDescription = if (saved) "Remove ${surah.nameEnglish} from saved" else "Save ${surah.nameEnglish}",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onToggleSaved,
                )
            } else if (saved) {
                Icon(NiaIcons.Bookmark, contentDescription = "Saved")
            }
        }
    }
}

@Composable
private fun BukhariBookRow(book: BukhariBook, saved: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Sahih al-Bukhari book ${book.id}, ${book.nameEnglish}"
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(book.id.toString())
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(book.nameEnglish, fontWeight = FontWeight.SemiBold)
                Text("${book.hadithCount} narrations", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (saved) Icon(NiaIcons.Bookmark, contentDescription = "Saved")
        }
    }
}

@Composable
private fun NumberBadge(text: String) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun SupportingCard(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
            }
        }
    }
}
