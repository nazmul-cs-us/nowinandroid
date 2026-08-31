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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.starception.submission.shared.content.dailyRecommendation
import com.starception.submission.shared.content.searchCatalog
import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.createQuranVerseRepository
import com.starception.submission.shared.quran.filterQuranVerses
import com.starception.submission.shared.quran.metadataLabel
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

internal val SharedInterests = listOf(
    "Quran",
    "Prayer",
    "Hadith",
    "Dua and remembrance",
    "Character",
    "Family",
    "Travel",
    "Learning",
)

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
    SharedDetailScaffold(title = surah.nameEnglish, onBack = onBack) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(surah.nameArabic, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(surah.subtitle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Whole-surah recitation",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Streamed from QuranicAudio using Mishari Alafasy's murattal recitation. Network access is required.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(if (playing) "Pause" else "Play recitation")
            }
            OutlinedButton(
                onClick = { saved = number in store.toggleSurah(number) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (saved) NiaIcons.Bookmark else NiaIcons.BookmarkBorder,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(if (saved) "Saved" else "Save")
            }
        }
        Spacer(Modifier.height(18.dp))
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
                    label = { Text("Search Arabic ayahs or number") },
                    leadingIcon = { Icon(NiaIcons.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (query.isBlank()) {
                        "${state.verses.size} ayahs"
                    } else {
                        "${filteredVerses.size} matching ayahs"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(filteredVerses, key = { it.id }) { verse -> QuranAyahCard(verse) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranAyahCard(verse: QuranVerse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = verse.metadataLabel()
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                verse.metadataLabel(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                verse.arabicText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
internal fun BukhariBookDetailScreen(
    id: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    val book = BukhariBooks.find(id)
    if (book == null) {
        SharedDetailScaffold(title = "Sahih al-Bukhari", onBack = onBack) { Text("Book not found") }
        return
    }
    var saved by remember(id) { mutableStateOf(id in store.savedBukhariBooks()) }
    SharedDetailScaffold(title = book.nameEnglish, onBack = onBack) {
        Text(book.nameArabic, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        SupportingCard(
            title = "Book ${book.id} · ${book.hadithCount} narrations",
            body = "Canonical collection range ${book.firstHadithId}-${book.lastHadithId}.",
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { saved = id in store.toggleBukhariBook(id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (saved) NiaIcons.Bookmark else NiaIcons.BookmarkBorder, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (saved) "Saved collection" else "Save collection")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "The shared iOS catalog includes authoritative book boundaries and metadata. Full narration text remains in the Android downloadable database and is not presented here as if it were bundled.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                body = interests.takeIf { it.isNotEmpty() }?.sorted()?.joinToString()
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
internal fun InterestsScreen(store: SharedContentStore, onSelectBottom: (Int) -> Unit) {
    var selected by remember { mutableStateOf(store.interests()) }
    TopLevelScaffold(title = "Interests", selectedIndex = 4, onSelectBottom = onSelectBottom) {
        item {
            Text(
                "Select topics for the local For You feed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(SharedInterests.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { interest ->
                    FilterChip(
                        selected = interest in selected,
                        onClick = { selected = store.toggleInterest(interest) },
                        label = { Text(interest) },
                        leadingIcon = if (interest in selected) {
                            { Icon(NiaIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
