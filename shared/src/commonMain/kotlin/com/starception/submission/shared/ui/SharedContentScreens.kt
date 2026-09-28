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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.model.data.BukhariBook
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.feature.quran.Surah
import com.starception.submission.feature.quran.subtitle
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.shared.content.CatalogResult
import com.starception.submission.shared.content.DailyRecommendation
import com.starception.submission.shared.content.FortressChapter
import com.starception.submission.shared.content.FortressInvocation
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.content.SharedNewsResource
import com.starception.submission.shared.content.SharedQuranicDua
import com.starception.submission.shared.content.SharedTopic
import com.starception.submission.shared.content.SharedTopicArticle
import com.starception.submission.shared.content.createSharedDuaRepository
import com.starception.submission.shared.content.createSharedFortressRepository
import com.starception.submission.shared.content.createSharedNewsRepository
import com.starception.submission.shared.content.createSharedTopicRepository
import com.starception.submission.shared.content.dailyRecommendation
import com.starception.submission.shared.content.searchCatalog
import com.starception.submission.shared.content.sharedTopic
import com.starception.submission.shared.hadith.SharedHadith
import com.starception.submission.shared.hadith.createSharedHadithRepository
import com.starception.submission.shared.quran.AyahNumberChip
import com.starception.submission.shared.quran.QuranArabicFonts
import com.starception.submission.shared.quran.QuranTranslationLanguage
import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.SharedTajweedAnnotation
import com.starception.submission.shared.quran.createQuranVerseRepository
import com.starception.submission.shared.quran.createSharedTajweedRepository
import com.starception.submission.shared.quran.filterQuranVerses
import com.starception.submission.shared.quran.metadataLabel
import com.starception.submission.shared.quran.tajweedAnnotatedString
import com.starception.submission.shared.translation.SharedTranslationService
import com.starception.submission.shared.voice.PlatformSpeechSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.items as gridItems

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

private sealed interface SharedNewsState {
    data object Loading : SharedNewsState
    data class Loaded(val news: List<SharedNewsResource>) : SharedNewsState
    data class Error(val message: String) : SharedNewsState
}

private sealed interface TopicNewsState {
    data object Loading : TopicNewsState
    data class Loaded(
        val news: List<SharedNewsResource>,
        val nextOffset: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : TopicNewsState
    data class Error(val message: String) : TopicNewsState
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
    SharedDetailScaffold(title = "Search", onBack = onBack, maxContentWidth = 900.dp) {
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column {
                    SupportingCard(
                        title = "Private on this device",
                        body = "No external account is connected. These preferences are stored locally and are not synced.",
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = profile.displayName,
                        onValueChange = {
                            profile = profile.copy(displayName = it.take(40))
                            saved = false
                        },
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
                        onClick = {
                            store.saveProfile(profile)
                            profile = store.profile()
                            saved = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (saved) "Saved locally" else "Save preferences")
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuranLibraryScreen(
    store: SharedContentStore,
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenDuas: () -> Unit = {},
    onOpenFortress: () -> Unit = {},
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
    SharedDetailScaffold(title = "The Quran", onBack = onBack, maxContentWidth = 900.dp) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenFortress,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Fortress of the Muslim", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "132 chapters of authentic invocations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenDuas,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Quranic Duas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "40 invocations from the Quran",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        Spacer(Modifier.height(12.dp))
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
    player: QuranAudioPlayer,
    onOpenSurah: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val surah = QuranData.surahs.firstOrNull { it.number == number }
    if (surah == null) {
        SharedDetailScaffold(title = "Quran", onBack = onBack) { Text("Surah not found") }
        return
    }
    var saved by remember(number) { mutableStateOf(number in store.bookmarkedSurahs()) }
    var playing by remember(number) { mutableStateOf(false) }
    LaunchedEffect(number) {
        if (store.quranAutoplayPending()) {
            store.saveQuranAutoplayPending(false)
            playing = player.play(quranAudioUrl(number))
        }
    }
    var query by remember(number) { mutableStateOf("") }
    var loadAttempt by remember(number) { mutableStateOf(0) }
    var translationLanguage by remember {
        mutableStateOf(QuranTranslationLanguage.fromCode(store.quranTranslationLanguage()))
    }
    val tafseerRepository = remember { com.starception.submission.shared.quran.createQuranTafseerRepository() }
    val tafseerScope = androidx.compose.runtime.rememberCoroutineScope()
    var tafseerRequest by remember {
        mutableStateOf<com.starception.submission.shared.quran.AyahTafseer?>(null)
    }
    var tafseerLoading by remember { mutableStateOf(false) }
    var tafseerSelectedBook by remember { mutableStateOf(0) }
    LaunchedEffect(tafseerRequest) {
        tafseerLoading = tafseerRequest != null
    }
    var ayahState by remember(number) { mutableStateOf<QuranAyahState>(QuranAyahState.Loading) }
    val repository = remember { createQuranVerseRepository() }
    DisposableEffect(player) { onDispose { player.stop() } }
    LaunchedEffect(number, loadAttempt) {
        ayahState = QuranAyahState.Loading
        ayahState = try {
            val verses = repository.getVersesBySurah(number, translationLanguage)
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
    // Reading settings — the Android SurahDetailViewModel set, persisted here.
    var showTranslation by remember { mutableStateOf(store.quranShowTranslation()) }
    var selectedArabicFont by remember { mutableStateOf(store.quranArabicFont()) }
    var arabicFontSize by remember { mutableStateOf(store.quranArabicFontSize()) }
    var textAlignment by remember { mutableStateOf(store.quranTextAlignment()) }
    var mushafMode by remember { mutableStateOf(store.quranMushafMode()) }
    var tajweedEnabled by remember { mutableStateOf(store.quranTajweedEnabled()) }
    var tajweedAnnotations by remember { mutableStateOf<Map<Int, List<SharedTajweedAnnotation>>?>(null) }
    var tajweedUnavailable by remember { mutableStateOf(false) }
    val tajweedRepository = remember { createSharedTajweedRepository() }
    LaunchedEffect(number, tajweedEnabled) {
        if (tajweedEnabled && tajweedAnnotations == null) {
            // First enable downloads/loads the 5.5 MB CDN asset once, then caches.
            val annotations = runCatching { tajweedRepository.annotationsForSurah(number) }.getOrNull()
            if (annotations == null) {
                tajweedUnavailable = true
                tajweedEnabled = false
                store.saveQuranTajweedEnabled(false)
            } else {
                tajweedUnavailable = false
                tajweedAnnotations = annotations
            }
        }
    }
    LaunchedEffect(number, translationLanguage) {
        // Reload the ayahs when the translation language changes.
        loadAttempt += 1
    }
    val savedSurah = remember(number) { number in store.bookmarkedSurahs() }
    var isBookmarked by remember(number) { mutableStateOf(savedSurah) }
    val bookmarkAction = DetailAction(
        id = "bookmark",
        label = if (isBookmarked) "Remove bookmark" else "Add bookmark",
        icon = NiaIcons.Bookmark.takeIf { isBookmarked } ?: NiaIcons.BookmarkBorder,
        selected = isBookmarked,
    ) {
        isBookmarked = number in store.toggleSurah(number)
    }
    // The ⋮ sheet mirrors the Android Reading Settings options (SurahDetailViewModel):
    // Tajweed, Show Translation, Mushaf Page, Text Alignment, Arabic Font, Translation Language.
    val playAudioAction = DetailAction(
        id = "play_audio",
        label = if (playing) "Pause recitation" else "Play surah audio",
        selected = playing,
    ) {
        if (playing) {
            player.pause()
            playing = false
        } else {
            playing = player.play(quranAudioUrl(number))
        }
    }
    val showTranslationAction = DetailAction(
        id = "show_translation",
        label = if (showTranslation) "Hide translation" else "Show translation",
        selected = showTranslation,
        trailingText = if (showTranslation) "ON" else "OFF",
    ) {
        showTranslation = !showTranslation
        store.saveQuranShowTranslation(showTranslation)
    }
    val tajweedAction = DetailAction(
        id = "tajweed",
        label = "Tajweed colors",
        selected = tajweedEnabled,
        trailingText = when {
            tajweedEnabled -> "ON"
            tajweedUnavailable -> "N/A"
            else -> "OFF"
        },
    ) {
        tajweedEnabled = !tajweedEnabled
        store.saveQuranTajweedEnabled(tajweedEnabled)
    }
    val mushafPageAction = DetailAction(
        id = "mushaf_page",
        label = if (mushafMode) "Ayah list view" else "Mushaf page view",
        selected = mushafMode,
        trailingText = if (mushafMode) "ON" else "OFF",
    ) {
        mushafMode = !mushafMode
        store.saveQuranMushafMode(mushafMode)
    }
    val textAlignmentAction = DetailAction(
        id = "text_alignment",
        label = "Text alignment",
        trailingText = if (textAlignment == "center") "Center" else "Justify",
    ) {
        textAlignment = if (textAlignment == "center") "justify" else "center"
        store.saveQuranTextAlignment(textAlignment)
    }
    val arabicFontAction = DetailAction(
        id = "arabic_font",
        label = "Arabic font",
        trailingText = QuranArabicFonts.displayName(selectedArabicFont),
    ) {
        val order = QuranArabicFonts.selectionOrder
        val currentIndex = order.indexOf(selectedArabicFont).coerceAtLeast(0)
        val next = order[(currentIndex + 1) % order.size]
        selectedArabicFont = next
        store.saveQuranArabicFont(next)
    }
    val translationLanguageAction = DetailAction(
        id = "translation_language",
        label = "Translation language",
        trailingText = translationLanguage.displayName,
    ) {
        val entries = QuranTranslationLanguage.entries
        translationLanguage = entries[(entries.indexOf(translationLanguage) + 1) % entries.size]
        store.saveQuranTranslationLanguage(translationLanguage.code)
    }
    val tafseerAction = DetailAction(
        id = "tafseer",
        label = "Word study / Tafseer",
    ) { }
    val bookmarkSheetAction = DetailAction(
        id = "bookmark_sheet",
        label = if (isBookmarked) "Remove bookmark" else "Bookmark surah",
        icon = NiaIcons.Bookmark.takeIf { isBookmarked } ?: NiaIcons.BookmarkBorder,
        selected = isBookmarked,
    ) {
        isBookmarked = number in store.toggleSurah(number)
    }
    ImmersiveDetailScaffold(onBack = onBack, header = {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
        ) {
            com.starception.submission.shared.quran.SurahArtworkHeader(
                surahNumber = number,
                contentDescription = "Symbolic artwork for Surah ${surah.nameEnglish}",
                modifier = Modifier.fillMaxSize(),
            )
            Column {
                DetailToolbar(
                    onBack = onBack,
                    inlineActions = listOf(bookmarkAction),
                    sheetActions = listOf(
                        playAudioAction,
                        showTranslationAction,
                        tajweedAction,
                        mushafPageAction,
                        textAlignmentAction,
                        arabicFontAction,
                        translationLanguageAction,
                        tafseerAction,
                        bookmarkSheetAction,
                    ),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    toolbarTitle = "Surah ${surah.number} · ${surah.nameEnglish}",
                )
            }
            // Android's album-header scrim: surah title overlaid on the artwork.
            ImmersiveDetailHeaderScrim(
                title = "Surah ${surah.number}",
                supportingText = surah.nameEnglish,
                arabicTitle = surah.nameArabic,
                modifier = Modifier.matchParentSize(),
            )
        }
    }) {
        // Surah-to-surah swipe, matching Android's SurahSwipeContainer. In
        // mushaf mode the pager owns horizontal gestures, so the detector
        // only runs in the ayah-list mode.
        var swipeTotalX by remember(number) { mutableStateOf(0f) }
        val swipeModifier = if (!mushafMode) {
            Modifier.pointerInput(number) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeTotalX < -80f && number < 114) {
                            onOpenSurah(number + 1)
                        } else if (swipeTotalX > 80f && number > 1) {
                            onOpenSurah(number - 1)
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    swipeTotalX += dragAmount
                }
            }
        } else {
            Modifier
        }
        Column(swipeModifier.weight(1f)) {
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
            // Android-style surah audio bar: prev / play / next with a progress
            // slider and time labels. Prev/next move between surahs (and continue
            // playback when active), matching the Android mini-bar behavior.
            var positionSeconds by remember(number) { mutableStateOf(0f) }
            var durationSeconds by remember(number) { mutableStateOf(0f) }
            LaunchedEffect(playing, number) {
                if (playing) {
                    while (true) {
                        positionSeconds = player.positionSeconds()
                        durationSeconds = player.durationSeconds()
                        // Auto-advance to the next surah when the recitation ends,
                        // matching the Android playback service's onSurahChanged.
                        if (durationSeconds > 0f && positionSeconds >= durationSeconds - 0.5f) {
                            if (number < 114) {
                                player.stop()
                                store.saveQuranAutoplayPending(true)
                                onOpenSurah(number + 1)
                                return@LaunchedEffect
                            }
                            player.pause()
                            playing = false
                            return@LaunchedEffect
                        }
                        kotlinx.coroutines.delay(500)
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { onOpenSurah(number - 1) }, enabled = number > 1) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous surah")
                        }
                        IconButton(
                            onClick = {
                                if (playing) {
                                    player.pause()
                                    playing = false
                                } else {
                                    playing = player.play(quranAudioUrl(number))
                                }
                            },
                        ) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Pause recitation" else "Play surah",
                            )
                        }
                        IconButton(onClick = { onOpenSurah(number + 1) }, enabled = number < 114) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next surah")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { saved = number in store.toggleSurah(number) }) {
                            Icon(
                                if (saved) NiaIcons.Bookmark else NiaIcons.BookmarkBorder,
                                contentDescription = if (saved) "Remove bookmark" else "Bookmark surah",
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatAudioSeconds(positionSeconds.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = if (durationSeconds > 0f) {
                                (positionSeconds / durationSeconds).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            onValueChange = { fraction ->
                                if (durationSeconds > 0f) {
                                    positionSeconds = fraction * durationSeconds
                                    player.seekTo(positionSeconds)
                                }
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(
                            formatAudioSeconds(durationSeconds.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Translation language selector; non-English DBs download from the CDN
            // on demand, Arabic-only included as the "None" chip.
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                QuranTranslationLanguage.entries.forEach { language ->
                    androidx.compose.material3.FilterChip(
                        selected = language == translationLanguage,
                        onClick = {
                            translationLanguage = language
                            store.saveQuranTranslationLanguage(language.code)
                        },
                        label = { Text(language.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Tafseer / word-study sheet. Word study jumps straight to the word
            // meanings book; Tafseer opens on As-Sa'di. Switching books is instant
            // since the whole ayah row (all books + meanings) was loaded at once.
            val tafseerSheet = tafseerRequest
            if (tafseerSheet != null) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (tafseerSelectedBook == 3) "Word meanings" else "Tafseer · Ayah ${tafseerSheet.ayahNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            androidx.compose.material3.TextButton(
                                onClick = { tafseerRequest = null },
                            ) {
                                Text("Close", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            listOf("As-Sa'di" to 0, "Al-Moyassar" to 1, "Al-Baghawi" to 2, "Word meanings" to 3).forEach { (label, index) ->
                                androidx.compose.material3.FilterChip(
                                    selected = tafseerSelectedBook == index,
                                    onClick = { tafseerSelectedBook = index },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (tafseerLoading) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        } else {
                            val body = when (tafseerSelectedBook) {
                                0 -> tafseerSheet.tafseerSaadi
                                1 -> tafseerSheet.tafseerMoysar
                                2 -> tafseerSheet.tafseerBaghawi
                                else -> tafseerSheet.ayahMeanings
                            }
                            if (body.isBlank()) {
                                Text(
                                    "The enhanced Quran database has no content for this ayah yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    body,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
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
                    if (!mushafMode) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search ayah, Arabic, or translation") },
                            leadingIcon = { Icon(NiaIcons.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (mushafMode) {
                                "${versesMushafPages(state.verses)} mushaf pages"
                            } else if (query.isBlank()) {
                                "${state.verses.size} ayahs"
                            } else {
                                "${filteredVerses.size} matches"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Android's Arabic font-size stepper (28..60sp).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "A-",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        if (arabicFontSize > 28f) {
                                            arabicFontSize -= 1f
                                            store.saveQuranArabicFontSize(arabicFontSize)
                                        }
                                    }
                                    .padding(horizontal = 6.dp),
                            )
                            Text(
                                arabicFontSize.toInt().toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "A+",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        if (arabicFontSize < 60f) {
                                            arabicFontSize += 1f
                                            store.saveQuranArabicFontSize(arabicFontSize)
                                        }
                                    }
                                    .padding(horizontal = 6.dp),
                            )
                        }
                    }
                    if (mushafMode) {
                        MushafPagerView(
                            surah = surah,
                            verses = state.verses,
                            arabicFont = selectedArabicFont,
                            arabicFontSize = arabicFontSize,
                            showTranslation = showTranslation,
                            textAlignment = textAlignment,
                            tajweedAnnotations = if (tajweedEnabled) tajweedAnnotations else null,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else if (filteredVerses.isEmpty()) {
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
                                    arabicFont = selectedArabicFont,
                                    arabicFontSize = arabicFontSize,
                                    textAlignment = textAlignment,
                                    tajweedAnnotations = if (tajweedEnabled) {
                                        tajweedAnnotations?.get(verse.numberInSurah)
                                    } else {
                                        null
                                    },
                                    onToggleTranslation = { showTranslation = !showTranslation },
                                    onOpenTafseer = { verseId, preselectBook ->
                                        tafseerSelectedBook = preselectBook
                                        tafseerLoading = true
                                        tafseerScope.launch {
                                            val tafseer = runCatching {
                                                tafseerRepository.getTafseer(
                                                    number,
                                                    verseId,
                                                )
                                            }.getOrNull()
                                            tafseerRequest = tafseer ?: com.starception.submission.shared.quran.AyahTafseer.EMPTY
                                            tafseerLoading = false
                                        }
                                    },
                                )
                            }
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
    arabicFont: String,
    arabicFontSize: Float,
    textAlignment: String,
    tajweedAnnotations: List<SharedTajweedAnnotation>? = null,
    onToggleTranslation: () -> Unit,
    onOpenTafseer: (ayahNumber: Int, preselectBook: Int) -> Unit = { _, _ -> },
) {
    val arabicFontFamily = QuranArabicFonts.fontFamily(arabicFont)
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
        // Arabic in the selected Quran typeface at Android's default 41sp with
        // 1.7x leading; the ayah number sits in its rosette chip.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = tajweedAnnotatedString(verse.arabicText, tajweedAnnotations),
                modifier = Modifier.weight(1f),
                fontFamily = arabicFontFamily,
                fontSize = arabicFontSize.sp,
                lineHeight = (arabicFontSize * 1.7f).sp,
                textAlign = if (textAlignment == "center") TextAlign.Center else TextAlign.Justify,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AyahNumberChip(
                ayahNumber = verse.numberInSurah,
                fontSize = (arabicFontSize * 0.5f).sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
        }
        if (showTranslation && verse.translation.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${verse.translation} \u06DD${verse.numberInSurah}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Ayah actions: word meanings + tafseer, matching the Android detail
        // screen's ayah actions.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(
                onClick = { onOpenTafseer(verse.numberInSurah, 3) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("Word study", style = MaterialTheme.typography.labelMedium)
            }
            androidx.compose.material3.TextButton(
                onClick = { onOpenTafseer(verse.numberInSurah, 0) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("Tafseer", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

private fun Int.toArabicIndicDigits(): String = toString().map { digit ->
    if (digit in '0'..'9') ('٠'.code + (digit - '0')).toChar() else digit
}.joinToString("")

private fun versesMushafPages(verses: List<QuranVerse>): Int =
    verses.map { it.page }.distinct().size

private fun formatAudioSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:" + if (seconds < 10) "0$seconds" else seconds.toString()
}

@Composable
internal fun FortressLibraryScreen(
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val repository = remember { createSharedFortressRepository() }
    var loadAttempt by remember { mutableStateOf(0) }
    var chapters by remember { mutableStateOf<List<FortressChapter>>(emptyList()) }
    var state by remember { mutableStateOf<FortressListState>(FortressListState.Loading) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(loadAttempt) {
        state = FortressListState.Loading
        try {
            chapters = repository.getChapters()
            state = if (chapters.isEmpty()) {
                FortressListState.Error("No chapters were found in the database.")
            } else {
                FortressListState.Loaded
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state = FortressListState.Error(error.message ?: "The Fortress database could not be read.")
        }
    }
    SharedDetailScaffold(title = "Fortress of the Muslim", onBack = onBack) {
        when (val current = state) {
            FortressListState.Loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is FortressListState.Error -> Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SupportingCard("Unable to load chapters", current.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadAttempt++ }) { Text("Try again") }
            }
            FortressListState.Loaded -> {
                val term = query.trim().lowercase()
                val visible = if (term.isEmpty()) {
                    chapters
                } else {
                    chapters.filter { it.title.lowercase().contains(term) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search ${chapters.size} chapters") },
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
                    items(visible, key = { it.id }) { chapter ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenChapter(chapter.id) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${chapter.id}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 14.dp),
                                )
                                Text(
                                    chapter.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FortressChapterScreen(
    chapterId: Int,
    player: QuranAudioPlayer,
    onBack: () -> Unit,
) {
    val repository = remember { createSharedFortressRepository() }
    var loadAttempt by remember { mutableStateOf(0) }
    var invocations by remember { mutableStateOf<List<FortressInvocation>>(emptyList()) }
    var references by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var chapter by remember { mutableStateOf<FortressChapter?>(null) }
    var state by remember { mutableStateOf<DuaDetailState>(DuaDetailState.Loading) }
    LaunchedEffect(chapterId, loadAttempt) {
        state = DuaDetailState.Loading
        try {
            chapter = repository.getChapters().firstOrNull { it.id == chapterId }
            invocations = repository.getChapterInvocations(chapterId)
            references = repository.getChapterReferences(chapterId)
            state = if (invocations.isEmpty()) {
                DuaDetailState.Error("No invocations were found for chapter $chapterId.")
            } else {
                DuaDetailState.Loaded(
                    invocations.first().let {
                        SharedQuranicDua(
                            id = it.id,
                            duaNumber = it.position,
                            title = chapter?.title.orEmpty(),
                            surahReference = "",
                            arabic = it.arabic,
                            transliteration = it.transliteration,
                            translation = it.translation,
                            explanation = it.description,
                        )
                    },
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state = DuaDetailState.Error(error.message ?: "The Fortress database could not be read.")
        }
    }
    var playingId by remember { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { player.stop() }
    }
    ImmersiveDetailScaffold(onBack = onBack, header = {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxSize())
            Column {
                DetailToolbar(
                    onBack = onBack,
                    sheetActions = listOf(
                        DetailAction(
                            id = "stop_audio",
                            label = if (playingId == null) "Stop recitation" else "Stop recitation",
                            selected = playingId != null,
                        ) {
                            player.stop()
                            playingId = null
                        },
                    ),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    toolbarTitle = "Chapter $chapterId",
                )
            }
            ImmersiveDetailHeaderScrim(
                title = "Fortress of the Muslim",
                supportingText = chapter?.title ?: "Chapter $chapterId",
                arabicTitle = "حصن المسلم",
            )
        }
    }) {
        when (val current = state) {
            DuaDetailState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DuaDetailState.Error -> Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                SupportingCard("Unable to load chapter", current.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadAttempt++ }) { Text("Try again") }
            }
            is DuaDetailState.Loaded -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(invocations, key = { it.id }) { invocation ->
                    FortressInvocationCard(
                        invocation = invocation,
                        reference = references[invocation.id].orEmpty(),
                        playing = playingId == invocation.id,
                        onTogglePlay = {
                            if (playingId == invocation.id) {
                                player.pause()
                                playingId = null
                            } else if (invocation.audioUrl.isNotBlank()) {
                                player.stop()
                                player.play(invocation.audioUrl)
                                playingId = invocation.id
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One Fortress invocation: numbered position, Arabic, transliteration,
 *  translation, context/instruction/note, and the recorded recitation. */
@Composable
private fun FortressInvocationCard(
    invocation: FortressInvocation,
    reference: String,
    playing: Boolean,
    onTogglePlay: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${invocation.chapterId}:${invocation.position}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (invocation.audioUrl.isNotBlank()) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause recitation" else "Play recitation",
                        )
                    }
                }
            }
            if (invocation.arabic.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    invocation.arabic,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = QuranArabicFonts.fontFamily(QuranArabicFonts.PDMS_SALEEM),
                    fontSize = 27.sp,
                    lineHeight = 46.sp,
                    textAlign = TextAlign.End,
                )
            }
            if (invocation.transliteration.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    invocation.transliteration,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (invocation.translation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    invocation.translation,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                )
            }
            if (invocation.context.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    invocation.context,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (invocation.instruction.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    invocation.instruction,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (invocation.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    invocation.note,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reference.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(6.dp))
                Text(
                    reference,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
internal fun SharedDuaLibraryScreen(
    onBack: () -> Unit,
    onOpenDua: (Int) -> Unit,
) {
    val repository = remember { createSharedDuaRepository() }
    var loadAttempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<DuaDetailState>(DuaDetailState.Loading) }
    var duas by remember { mutableStateOf<List<SharedQuranicDua>>(emptyList()) }
    LaunchedEffect(loadAttempt) {
        state = DuaDetailState.Loading
        try {
            duas = repository.getQuranicDuas()
            state = if (duas.isEmpty()) {
                DuaDetailState.Error("No duas were found in the database.")
            } else {
                DuaDetailState.Loaded(duas.first())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state = DuaDetailState.Error(error.message ?: "The Quranic duas database could not be read.")
        }
    }
    SharedDetailScaffold(title = "Quranic Duas", onBack = onBack) {
        when (val current = state) {
            DuaDetailState.Loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is DuaDetailState.Error -> Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SupportingCard("Unable to load duas", current.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadAttempt++ }) { Text("Try again") }
            }
            is DuaDetailState.Loaded -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(duas, key = { it.id }) { dua ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenDua(dua.duaNumber) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${dua.duaNumber}. ${dua.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Surah ${dua.surahReference}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedDuaDetailScreen(
    number: Int,
    onBack: () -> Unit,
) {
    val repository = remember { createSharedDuaRepository() }
    var loadAttempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<DuaDetailState>(DuaDetailState.Loading) }
    LaunchedEffect(number, loadAttempt) {
        state = DuaDetailState.Loading
        state = try {
            val dua = repository.getQuranicDuas().firstOrNull { it.duaNumber == number }
            dua?.let { DuaDetailState.Loaded(it) }
                ?: DuaDetailState.Error("Dua $number was not found.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DuaDetailState.Error(error.message ?: "The Quranic duas database could not be read.")
        }
    }
    var listening by remember(number) { mutableStateOf(false) }
    ImmersiveDetailScaffold(onBack = onBack, header = {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxSize())
            Column {
                DetailToolbar(
                    onBack = onBack,
                    sheetActions = listOf(
                        DetailAction(
                            id = "listen",
                            label = if (listening) "Stop narration" else "Listen (TTS)",
                            selected = listening,
                        ) { listening = !listening },
                    ),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    toolbarTitle = "Quranic Dua $number",
                )
            }
            ImmersiveDetailHeaderScrim(
                title = "Quranic Dua",
                supportingText = "Dua $number",
                arabicTitle = "دعاء",
            )
        }
    }) {
        when (val current = state) {
            DuaDetailState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DuaDetailState.Error -> Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                SupportingCard("Unable to load dua", current.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadAttempt++ }) { Text("Try again") }
            }
            is DuaDetailState.Loaded -> {
                val dua = current.dua
                val arabicFontFamily = QuranArabicFonts.fontFamily(QuranArabicFonts.PDMS_SALEEM)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item {
                        Column {
                            Text(
                                "${dua.duaNumber}. ${dua.title}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReaderTag("DUA ${dua.duaNumber}")
                                ReaderTag("SURAH ${dua.surahReference}", selected = false)
                            }
                        }
                    }
                    item {
                        HadithListenButton(dua.translation) { enabled ->
                            if (enabled) {
                                PlatformSpeechSynthesizer().speak(text = dua.translation)
                            } else {
                                PlatformSpeechSynthesizer().stop()
                            }
                        }
                    }
                    if (dua.arabic.isNotBlank()) {
                        item {
                            ReaderSection("Arabic", MaterialTheme.colorScheme.primary) {
                                Text(
                                    dua.arabic,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontFamily = arabicFontFamily,
                                    fontSize = 34.sp,
                                    lineHeight = 56.sp,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                    if (dua.transliteration.isNotBlank()) {
                        item {
                            ReaderSection("Transliteration", MaterialTheme.colorScheme.secondary) {
                                Text(
                                    dua.transliteration,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                )
                            }
                        }
                    }
                    if (dua.translation.isNotBlank()) {
                        item {
                            ReaderSection("Translation", MaterialTheme.colorScheme.tertiary) {
                                Text(
                                    dua.translation,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                )
                            }
                        }
                    }
                    if (dua.explanation.isNotBlank()) {
                        item {
                            ReaderSection("Explanation", MaterialTheme.colorScheme.primary) {
                                Text(
                                    dua.explanation,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface FortressListState {
    data object Loading : FortressListState
    data object Loaded : FortressListState
    data class Error(val message: String) : FortressListState
}

private sealed interface DuaDetailState {
    data object Loading : DuaDetailState
    data class Loaded(val dua: SharedQuranicDua) : DuaDetailState
    data class Error(val message: String) : DuaDetailState
}

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
            if (hadiths.isEmpty()) {
                HadithsState.Error("No narrations were found for this book.")
            } else {
                HadithsState.Loaded(hadiths)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            HadithsState.Error(error.message ?: "The Sahih al-Bukhari database could not be read.")
        }
    }
    SharedDetailScaffold(title = book.nameEnglish, onBack = onBack, maxContentWidth = 900.dp) {
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
                    if (term.isEmpty()) {
                        current.hadiths
                    } else {
                        current.hadiths.filter {
                            it.id.toString() == term || it.english.lowercase().contains(term) || it.arabic.contains(query.trim())
                        }
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
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    val book = BukhariBooks.findByHadithId(hadithId)
    val repository = remember { createSharedHadithRepository() }
    var state by remember { mutableStateOf<HadithsState>(HadithsState.Loading) }
    LaunchedEffect(hadithId) {
        state = HadithsState.Loading
        state = try {
            // Load the whole book so the pager can swipe between narrations —
            // matching Android's prev/next hadith navigation.
            val hadiths = if (book != null) {
                repository.getHadiths(book.firstHadithId, book.lastHadithId)
            } else {
                listOfNotNull(repository.getHadith(hadithId))
            }
            if (hadiths.isEmpty()) {
                HadithsState.Error("Hadith $hadithId was not found.")
            } else {
                HadithsState.Loaded(hadiths)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            HadithsState.Error(error.message ?: "Unable to read this hadith.")
        }
    }
    var listening by remember { mutableStateOf(false) }
    var selectedArabicFont by remember { mutableStateOf(store.quranArabicFont()) }
    var translationLanguage by remember { mutableStateOf(store.hadithTranslationLanguage()) }
    var translationProvider by remember { mutableStateOf(store.hadithTranslationProvider()) }
    val arabicFontAction = DetailAction(
        id = "arabic_font",
        label = "Arabic font",
        trailingText = QuranArabicFonts.displayName(selectedArabicFont),
    ) {
        val order = QuranArabicFonts.selectionOrder
        val currentIndex = order.indexOf(selectedArabicFont).coerceAtLeast(0)
        val next = order[(currentIndex + 1) % order.size]
        selectedArabicFont = next
        store.saveQuranArabicFont(next)
    }
    // Play-all queue: narrates from the current hadith to the end of the
    // book, auto-advancing the pager as each narration finishes — the shared
    // counterpart of the Android hadith playlist.
    var queueIndex by remember { mutableStateOf<Int?>(null) }
    val queueSynthesizer = remember { PlatformSpeechSynthesizer() }
    when (val current = state) {
        HadithsState.Loading -> SharedDetailScaffold(title = "Sahih al-Bukhari", onBack = onBack) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HadithsState.Error -> SharedDetailScaffold(title = "Sahih al-Bukhari", onBack = onBack) {
            SupportingCard("Unable to load hadith", current.message)
        }
        is HadithsState.Loaded -> {
            val hadiths = current.hadiths
            val initialIndex = hadiths.indexOfFirst { it.id == hadithId }.coerceAtLeast(0)
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = initialIndex,
            ) { hadiths.size }
            val queueScope = androidx.compose.runtime.rememberCoroutineScope()
            // Drives the queue: scrolls to the queued hadith, narrates it,
            // then enqueues the next one. A null index or a stop cancels.
            LaunchedEffect(queueIndex) {
                val index = queueIndex ?: return@LaunchedEffect
                if (index >= hadiths.size) {
                    queueIndex = null
                    return@LaunchedEffect
                }
                pagerState.animateScrollToPage(index)
                listening = true
                val utterance = hadiths[index].english
                if (utterance.isBlank()) {
                    queueIndex = index + 1
                    return@LaunchedEffect
                }
                queueSynthesizer.speak(text = utterance) { error ->
                    if (error == null && queueIndex == index) {
                        queueIndex = index + 1
                    } else if (error != null) {
                        queueIndex = null
                        listening = false
                    }
                }
            }
            // Stop narration when the user swipes to another hadith manually.
            LaunchedEffect(pagerState.currentPage) {
                if (queueIndex == null) {
                    PlatformSpeechSynthesizer().stop()
                    listening = false
                }
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    queueSynthesizer.stop()
                    PlatformSpeechSynthesizer().stop()
                }
            }
            val currentHadith = hadiths.getOrNull(pagerState.currentPage) ?: hadiths.first()
            var translatedText by remember { mutableStateOf<String?>(null) }
            var isTranslating by remember { mutableStateOf(false) }
            LaunchedEffect(currentHadith.id, translationLanguage, translationProvider) {
                if (translationLanguage == "en") {
                    translatedText = null
                } else {
                    isTranslating = true
                    translatedText = SharedTranslationService.translateFromEnglish(
                        text = currentHadith.english,
                        targetLang = translationLanguage,
                        provider = translationProvider,
                    )
                    isTranslating = false
                }
            }
            val translationLanguageAction = DetailAction(
                id = "translation_language",
                label = "Translation language",
                trailingText = SharedTranslationService.displayName(translationLanguage),
            ) {
                val codes = SharedTranslationService.languages.map { it.first }
                translationLanguage = codes[(codes.indexOf(translationLanguage) + 1) % codes.size]
                store.saveHadithTranslationLanguage(translationLanguage)
            }
            val translationProviderAction = DetailAction(
                id = "translation_provider",
                label = "Translation provider",
                trailingText = translationProvider.replaceFirstChar { it.uppercase() },
            ) {
                val providers = listOf(
                    SharedTranslationService.PROVIDER_AUTO,
                    SharedTranslationService.PROVIDER_GOOGLE,
                    SharedTranslationService.PROVIDER_REVERSO,
                )
                translationProvider = providers[(providers.indexOf(translationProvider) + 1) % providers.size]
                store.saveHadithTranslationProvider(translationProvider)
            }
            val listenAction = DetailAction(
                id = "listen",
                label = if (listening && queueIndex == null) "Stop narration" else "Listen (TTS)",
                selected = listening && queueIndex == null,
            ) {
                queueIndex = null
                queueSynthesizer.stop()
                listening = !listening
                if (listening) {
                    PlatformSpeechSynthesizer().speak(text = currentHadith.english)
                } else {
                    PlatformSpeechSynthesizer().stop()
                }
            }
            val playAllAction = DetailAction(
                id = "play_all",
                label = if (queueIndex != null) "Stop queue" else "Play all from here",
                selected = queueIndex != null,
                trailingText = if (queueIndex != null) {
                    "${(queueIndex ?: 0) + 1}/${hadiths.size}"
                } else {
                    "${hadiths.size - pagerState.currentPage} left"
                },
            ) {
                if (queueIndex != null) {
                    queueSynthesizer.stop()
                    queueIndex = null
                    listening = false
                } else {
                    queueIndex = pagerState.currentPage
                }
            }
            ImmersiveDetailScaffold(onBack = onBack, header = {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxSize())
                    Column {
                        DetailToolbar(
                            onBack = onBack,
                            sheetActions = listOf(
                                listenAction,
                                playAllAction,
                                translationLanguageAction,
                                translationProviderAction,
                                arabicFontAction,
                            ),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            toolbarTitle = "Sahih al-Bukhari · Hadith ${currentHadith.id}",
                        )
                    }
                    ImmersiveDetailHeaderScrim(
                        title = "Sahih al-Bukhari",
                        supportingText = "Hadith ${currentHadith.id} of ${hadiths.size}",
                        arabicTitle = "صحيح البخاري",
                    )
                }
            }) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { index ->
                    HadithDetailPage(
                        hadith = hadiths[index],
                        bookTag = book?.let { "BOOK ${it.id} · ${it.nameEnglish.uppercase()}" },
                        arabicFont = selectedArabicFont,
                        listening = listening && index == pagerState.currentPage,
                        onListeningChange = { enabled ->
                            listening = enabled
                            if (enabled) {
                                PlatformSpeechSynthesizer().speak(text = hadiths[index].english)
                            } else {
                                PlatformSpeechSynthesizer().stop()
                            }
                        },
                        translatedText = if (index == pagerState.currentPage) translatedText else null,
                        translationLanguage = translationLanguage,
                        isTranslating = isTranslating && index == pagerState.currentPage,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Hadith ${currentHadith.id} of ${hadiths.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One hadith page: tags, listen button, Arabic, translation, explanation. */
@Composable
private fun HadithDetailPage(
    hadith: SharedHadith,
    bookTag: String?,
    arabicFont: String,
    listening: Boolean,
    onListeningChange: (Boolean) -> Unit,
    translatedText: String? = null,
    translationLanguage: String = "en",
    isTranslating: Boolean = false,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column {
                Text("Hadith ${hadith.id}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTag("HADITH ${hadith.id}")
                    if (bookTag != null) {
                        ReaderTag(bookTag, selected = false)
                    }
                }
            }
        }
        item {
            HadithListenButton(hadith.english) { enabled ->
                onListeningChange(enabled)
            }
        }
        if (hadith.arabic.isNotBlank()) {
            item {
                ReaderSection("Arabic", MaterialTheme.colorScheme.primary) {
                    Text(
                        hadith.arabic,
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                        fontSize = 26.sp,
                        lineHeight = 44.sp,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        if (hadith.english.isNotBlank()) {
            item {
                ReaderSection("English translation", MaterialTheme.colorScheme.secondary) {
                    Text(hadith.english, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                }
            }
        }
        if (isTranslating) {
            item {
                Text(
                    "Translating…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!translatedText.isNullOrBlank() && translatedText != hadith.english) {
            item {
                ReaderSection(
                    "Translation (${SharedTranslationService.displayName(translationLanguage)})",
                    MaterialTheme.colorScheme.tertiary,
                ) {
                    Text(translatedText, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                }
            }
        }
        if (hadith.explanation.isNotBlank()) {
            item {
                ReaderSection("Explanation", MaterialTheme.colorScheme.tertiary) {
                    Text(hadith.explanation, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                }
            }
        }
    }
}

@Composable
internal fun ShamayelBookDetailScreen(
    id: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
    onOpenHadith: (Int) -> Unit,
) {
    val book = com.starception.submission.core.model.data.ShamayelBooks.find(id)
    if (book == null) {
        SharedDetailScaffold(title = "Shama'il At-Tirmidhi", onBack = onBack) { Text("Book not found") }
        return
    }
    var query by remember(id) { mutableStateOf("") }
    var loadAttempt by remember(id) { mutableStateOf(0) }
    var state by remember(id) { mutableStateOf<HadithsState>(HadithsState.Loading) }
    val repository = remember { createSharedHadithRepository() }
    LaunchedEffect(id, loadAttempt) {
        state = try {
            val hadiths = repository.getShamayelHadiths(book.firstHadithId, book.lastHadithId)
            if (hadiths.isEmpty()) {
                HadithsState.Error("No narrations were found for this book.")
            } else {
                HadithsState.Loaded(hadiths)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            HadithsState.Error(error.message ?: "The Shama'il At-Tirmidhi database could not be read.")
        }
    }
    SharedDetailScaffold(title = book.nameEnglish, onBack = onBack, maxContentWidth = 900.dp) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Book ${book.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    book.nameBengali,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${book.lastHadithId - book.firstHadithId + 1} narrations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (val current = state) {
            HadithsState.Loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is HadithsState.Error -> SupportingCard("Unable to load narrations", current.message)
            is HadithsState.Loaded -> {
                val filtered = remember(current.hadiths, query) {
                    val term = query.trim().lowercase()
                    if (term.isEmpty()) {
                        current.hadiths
                    } else {
                        current.hadiths.filter {
                            it.english.lowercase().contains(term) || it.explanation.lowercase().contains(term)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filtered, key = { it.id }) { hadith ->
                        BukhariHadithTile(hadith = hadith, onClick = { onOpenHadith(hadith.id) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShamayelHadithDetailScreen(
    hadithId: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    val book = com.starception.submission.core.model.data.ShamayelBooks.findByHadithId(hadithId)
    val repository = remember { createSharedHadithRepository() }
    var state by remember { mutableStateOf<HadithsState>(HadithsState.Loading) }
    LaunchedEffect(hadithId) {
        state = HadithsState.Loading
        state = try {
            // The whole book loads so the pager can swipe between chapters,
            // matching the Android prev/next hadith navigation.
            val hadiths = if (book != null) {
                repository.getShamayelHadiths(book.firstHadithId, book.lastHadithId)
            } else {
                listOfNotNull(repository.getShamayelHadith(hadithId))
            }
            if (hadiths.isEmpty()) {
                HadithsState.Error("Hadith $hadithId was not found.")
            } else {
                HadithsState.Loaded(hadiths)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            HadithsState.Error(error.message ?: "Unable to read this hadith.")
        }
    }
    var listening by remember { mutableStateOf(false) }
    var selectedArabicFont by remember { mutableStateOf(store.quranArabicFont()) }
    var translationLanguage by remember { mutableStateOf(store.hadithTranslationLanguage()) }
    var translationProvider by remember { mutableStateOf(store.hadithTranslationProvider()) }
    when (val current = state) {
        HadithsState.Loading -> SharedDetailScaffold(title = "Shama'il At-Tirmidhi", onBack = onBack) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HadithsState.Error -> SharedDetailScaffold(title = "Shama'il At-Tirmidhi", onBack = onBack) {
            SupportingCard("Unable to load hadith", current.message)
        }
        is HadithsState.Loaded -> {
            val hadiths = current.hadiths
            val initialIndex = hadiths.indexOfFirst { it.id == hadithId }.coerceAtLeast(0)
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = initialIndex,
            ) { hadiths.size }
            // Play-all queue — same mechanism as the Bukhari detail screen.
            var queueIndex by remember { mutableStateOf<Int?>(null) }
            val queueSynthesizer = remember { PlatformSpeechSynthesizer() }
            LaunchedEffect(queueIndex) {
                val index = queueIndex ?: return@LaunchedEffect
                if (index >= hadiths.size) {
                    queueIndex = null
                    return@LaunchedEffect
                }
                pagerState.animateScrollToPage(index)
                listening = true
                val utterance = hadiths[index].english
                if (utterance.isBlank()) {
                    queueIndex = index + 1
                    return@LaunchedEffect
                }
                queueSynthesizer.speak(text = utterance) { error ->
                    if (error == null && queueIndex == index) {
                        queueIndex = index + 1
                    } else if (error != null) {
                        queueIndex = null
                        listening = false
                    }
                }
            }
            LaunchedEffect(pagerState.currentPage) {
                if (queueIndex == null) {
                    PlatformSpeechSynthesizer().stop()
                    listening = false
                }
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    queueSynthesizer.stop()
                    PlatformSpeechSynthesizer().stop()
                }
            }
            val currentHadith = hadiths.getOrNull(pagerState.currentPage) ?: hadiths.first()
            var translatedText by remember { mutableStateOf<String?>(null) }
            var isTranslating by remember { mutableStateOf(false) }
            LaunchedEffect(currentHadith.id, translationLanguage, translationProvider) {
                if (translationLanguage == "en") {
                    translatedText = null
                } else {
                    isTranslating = true
                    translatedText = SharedTranslationService.translateFromEnglish(
                        text = currentHadith.english,
                        targetLang = translationLanguage,
                        provider = translationProvider,
                    )
                    isTranslating = false
                }
            }
            val translationLanguageAction = DetailAction(
                id = "translation_language",
                label = "Translation language",
                trailingText = SharedTranslationService.displayName(translationLanguage),
            ) {
                val codes = SharedTranslationService.languages.map { it.first }
                translationLanguage = codes[(codes.indexOf(translationLanguage) + 1) % codes.size]
                store.saveHadithTranslationLanguage(translationLanguage)
            }
            val translationProviderAction = DetailAction(
                id = "translation_provider",
                label = "Translation provider",
                trailingText = translationProvider.replaceFirstChar { it.uppercase() },
            ) {
                val providers = listOf(
                    SharedTranslationService.PROVIDER_AUTO,
                    SharedTranslationService.PROVIDER_GOOGLE,
                    SharedTranslationService.PROVIDER_REVERSO,
                )
                translationProvider = providers[(providers.indexOf(translationProvider) + 1) % providers.size]
                store.saveHadithTranslationProvider(translationProvider)
            }
            val arabicFontAction = DetailAction(
                id = "arabic_font",
                label = "Arabic font",
                trailingText = QuranArabicFonts.displayName(selectedArabicFont),
            ) {
                val order = QuranArabicFonts.selectionOrder
                val currentIndex = order.indexOf(selectedArabicFont).coerceAtLeast(0)
                val next = order[(currentIndex + 1) % order.size]
                selectedArabicFont = next
                store.saveQuranArabicFont(next)
            }
            val listenAction = DetailAction(
                id = "listen",
                label = if (listening && queueIndex == null) "Stop narration" else "Listen (TTS)",
                selected = listening && queueIndex == null,
            ) {
                queueIndex = null
                queueSynthesizer.stop()
                listening = !listening
                if (listening) {
                    PlatformSpeechSynthesizer().speak(text = currentHadith.english)
                } else {
                    PlatformSpeechSynthesizer().stop()
                }
            }
            val playAllAction = DetailAction(
                id = "play_all",
                label = if (queueIndex != null) "Stop queue" else "Play all from here",
                selected = queueIndex != null,
                trailingText = if (queueIndex != null) {
                    "${(queueIndex ?: 0) + 1}/${hadiths.size}"
                } else {
                    "${hadiths.size - pagerState.currentPage} left"
                },
            ) {
                if (queueIndex != null) {
                    queueSynthesizer.stop()
                    queueIndex = null
                    listening = false
                } else {
                    queueIndex = pagerState.currentPage
                }
            }
            ImmersiveDetailScaffold(onBack = onBack, header = {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxSize())
                    Column {
                        DetailToolbar(
                            onBack = onBack,
                            sheetActions = listOf(
                                listenAction,
                                playAllAction,
                                translationLanguageAction,
                                translationProviderAction,
                                arabicFontAction,
                            ),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            toolbarTitle = "Shama'il At-Tirmidhi · Hadith ${currentHadith.id}",
                        )
                    }
                    ImmersiveDetailHeaderScrim(
                        title = "Shama'il At-Tirmidhi",
                        supportingText = "Hadith ${currentHadith.id} of ${hadiths.size}",
                        arabicTitle = "شمائل الترمذي",
                    )
                }
            }) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { index ->
                    HadithDetailPage(
                        hadith = hadiths[index],
                        bookTag = book?.let { "BOOK ${it.id} · ${it.nameEnglish.uppercase()}" },
                        arabicFont = selectedArabicFont,
                        listening = listening && index == pagerState.currentPage,
                        onListeningChange = { enabled ->
                            listening = enabled
                            if (enabled) {
                                PlatformSpeechSynthesizer().speak(text = hadiths[index].english)
                            } else {
                                PlatformSpeechSynthesizer().stop()
                            }
                        },
                        translatedText = if (index == pagerState.currentPage) translatedText else null,
                        translationLanguage = translationLanguage,
                        isTranslating = isTranslating && index == pagerState.currentPage,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Hadith ${currentHadith.id} of ${hadiths.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HadithListenButton(
    englishText: String,
    onToggle: (enabled: Boolean) -> Unit,
) {
    var listening by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (listening) {
                PlatformSpeechSynthesizer().stop()
            }
        }
    }
    if (englishText.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                listening = !listening
                onToggle(listening)
            }) {
                Icon(
                    if (listening) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.size(6.dp))
                Text(if (listening) "Stop" else "Listen")
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column {
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
        }
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
    onOpenNews: (Int) -> Unit = {},
    onOpenTopic: (Int) -> Unit = {},
    searchController: SharedSearchController? = null,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    prayerAlert: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState? = null,
    globalRefreshing: Boolean = false,
    globalSyncResultText: String? = null,
) {
    // Pull-to-refresh reloads the topic + news databases. The strip HOLDS
    // until the reload lands (plus a small dwell) so it reads as a real sync
    // instead of a flash — matching how the home's refresh behaves.
    var refreshAttempt by remember { mutableStateOf(0) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val topicRepository = remember { createSharedTopicRepository() }
    val newsRepository = remember { createSharedNewsRepository() }
    var topics by remember { mutableStateOf(emptyList<SharedTopic>()) }
    var topicsLoading by remember { mutableStateOf(true) }
    var topicsError by remember { mutableStateOf<String?>(null) }
    var followedTopicIds by remember { mutableStateOf(store.followedTopicIds()) }
    var bookmarkedNewsIds by remember { mutableStateOf(store.bookmarkedNewsIds()) }
    var viewedNewsIds by remember { mutableStateOf(store.viewedNewsIds()) }
    var onboardingHidden by remember { mutableStateOf(store.isOnboardingHidden()) }
    var newsState by remember { mutableStateOf<SharedNewsState>(SharedNewsState.Loading) }

    LaunchedEffect(topicRepository, refreshAttempt) {
        topicsLoading = true
        try {
            topics = topicRepository.topics()
            topicsError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            topicsError = error.message ?: "Unable to read topics"
        }
        topicsLoading = false
    }
    LaunchedEffect(newsRepository, followedTopicIds) {
        newsState = if (followedTopicIds.isEmpty()) {
            SharedNewsState.Loaded(emptyList())
        } else {
            try {
                SharedNewsState.Loaded(newsRepository.newsForTopics(followedTopicIds, limit = 100))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                SharedNewsState.Error(error.message ?: "Unable to read news")
            }
        }
    }
    val topicsById = remember(topics) { topics.associateBy(SharedTopic::id) }

    LaunchedEffect(topicsLoading, pullRefreshing) {
        if (pullRefreshing && !topicsLoading) {
            kotlinx.coroutines.delay(700)
            pullRefreshing = false
        }
    }
    TopLevelScaffold(
        title = "For you",
        selectedIndex = 1,
        onSelectBottom = onSelectBottom,
        searchController = searchController,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        isRefreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            refreshAttempt += 1
        },
        adaptiveGrid = true,
    ) { expanded ->
        if (!onboardingHidden) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "What are you interested in?",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Updates from topics you follow will appear here. Follow some things to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when {
                topicsLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                topicsError != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    SupportingCard("Unable to load topics", topicsError.orEmpty())
                }
                else -> gridItems(
                    items = topics,
                    key = { "onboarding:${it.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { topic ->
                    OnboardingTopicRow(
                        topic = topic,
                        followed = topic.id in followedTopicIds,
                        onFollowChanged = { followed ->
                            store.setTopicFollowed(topic.id, followed)
                            followedTopicIds = if (followed) {
                                followedTopicIds + topic.id
                            } else {
                                followedTopicIds - topic.id
                            }
                        },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = {
                        store.setOnboardingHidden(true)
                        onboardingHidden = true
                    },
                    enabled = followedTopicIds.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics { contentDescription = "Finish choosing topics" },
                ) { Text("Done") }
            }
        }

        when (val state = newsState) {
            SharedNewsState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SharedNewsState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                SupportingCard("Unable to load your feed", state.message)
            }
            is SharedNewsState.Loaded -> if (state.news.isEmpty() && onboardingHidden) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SupportingCard(
                        title = "Your feed is empty",
                        body = "Follow a few topics and related content will appear here.",
                        action = "Browse topics",
                        onAction = { onSelectBottom(4) },
                    )
                }
            } else {
                gridItems(state.news, key = { "news:${it.id}" }) { news ->
                    SharedNewsResourceCard(
                        news = news,
                        topicsById = topicsById,
                        bookmarked = news.id in bookmarkedNewsIds,
                        viewed = news.id in viewedNewsIds,
                        onToggleBookmark = {
                            val bookmarked = news.id !in bookmarkedNewsIds
                            store.setNewsBookmarked(news.id, bookmarked)
                            bookmarkedNewsIds = if (bookmarked) bookmarkedNewsIds + news.id else bookmarkedNewsIds - news.id
                        },
                        onClick = {
                            if (news.id !in viewedNewsIds) {
                                store.markNewsViewed(news.id)
                                viewedNewsIds = viewedNewsIds + news.id
                            }
                            onOpenNews(news.id)
                        },
                        onTopicClick = onOpenTopic,
                        compact = expanded,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopicRow(
    topic: SharedTopic,
    followed: Boolean,
    onFollowChanged: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicArtwork(topic.name, Modifier.size(48.dp).padding(8.dp))
            Text(topic.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp).weight(1f))
            FilledIconToggleButton(checked = followed, onCheckedChange = onFollowChanged) {
                Icon(
                    if (followed) NiaIcons.Check else NiaIcons.Add,
                    contentDescription = if (followed) "Unfollow ${topic.name}" else "Follow ${topic.name}",
                )
            }
        }
    }
}

@Composable
internal fun SavedScreen(
    store: SharedContentStore,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
    onSelectBottom: (Int) -> Unit,
    onOpenNews: (Int) -> Unit = {},
    onOpenTopic: (Int) -> Unit = {},
    searchController: SharedSearchController? = null,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    prayerAlert: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState? = null,
    globalRefreshing: Boolean = false,
    globalSyncResultText: String? = null,
) {
    val newsRepository = remember { createSharedNewsRepository() }
    val topicRepository = remember { createSharedTopicRepository() }
    var bookmarkedIds by remember { mutableStateOf(store.bookmarkedNewsIds()) }
    var viewedIds by remember { mutableStateOf(store.viewedNewsIds()) }
    var state by remember { mutableStateOf<SharedNewsState>(SharedNewsState.Loading) }
    var topics by remember { mutableStateOf(emptyList<SharedTopic>()) }
    var removedForUndo by remember { mutableStateOf<SharedNewsResource?>(null) }
    var loadAttempt by remember { mutableStateOf(0) }
    val savedBukhariBooks = BukhariBooks.all.filter { it.id in store.savedBukhariBooks() }

    LaunchedEffect(topicRepository) {
        topics = try {
            topicRepository.topics()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyList()
        }
    }
    LaunchedEffect(newsRepository, loadAttempt) {
        state = if (bookmarkedIds.isEmpty()) {
            SharedNewsState.Loaded(emptyList())
        } else {
            try {
                SharedNewsState.Loaded(newsRepository.newsByIds(bookmarkedIds))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                SharedNewsState.Error(error.message ?: "Unable to read saved news")
            }
        }
    }
    val topicsById = remember(topics) { topics.associateBy(SharedTopic::id) }

    TopLevelScaffold(
        title = "Saved",
        selectedIndex = 2,
        onSelectBottom = onSelectBottom,
        searchController = searchController,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onRefresh = { },
        prayerAlert = prayerAlert,
        adaptiveGrid = true,
    ) { expanded ->
        removedForUndo?.let { removed ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                SupportingCard(
                    title = "Removed from Saved",
                    body = removed.title,
                    action = "Undo",
                    onAction = {
                        store.setNewsBookmarked(removed.id, true)
                        bookmarkedIds = bookmarkedIds + removed.id
                        state = when (val current = state) {
                            is SharedNewsState.Loaded -> current.copy(news = listOf(removed) + current.news)
                            else -> SharedNewsState.Loaded(listOf(removed))
                        }
                        removedForUndo = null
                    },
                )
            }
        }
        when (val current = state) {
            SharedNewsState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SharedNewsState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                SupportingCard(
                    title = "Unable to load saved news",
                    body = current.message,
                    action = "Try again",
                    onAction = { loadAttempt++ },
                )
            }
            is SharedNewsState.Loaded -> if (
                current.news.isEmpty() && savedBukhariBooks.isEmpty() && removedForUndo == null
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SupportingCard(
                        title = "Nothing saved yet",
                        body = "News you bookmark will appear here across launches.",
                    )
                }
            } else {
                gridItems(current.news, key = { "saved:${it.id}" }) { news ->
                    SharedNewsResourceCard(
                        news = news,
                        topicsById = topicsById,
                        bookmarked = true,
                        viewed = news.id in viewedIds,
                        onToggleBookmark = {
                            store.setNewsBookmarked(news.id, false)
                            bookmarkedIds = bookmarkedIds - news.id
                            removedForUndo = news
                            state = current.copy(news = current.news.filterNot { it.id == news.id })
                        },
                        onClick = {
                            if (news.id !in viewedIds) {
                                store.markNewsViewed(news.id)
                                viewedIds = viewedIds + news.id
                            }
                            onOpenNews(news.id)
                        },
                        onTopicClick = onOpenTopic,
                        compact = expanded,
                    )
                }
            }
        }
        gridItems(savedBukhariBooks, key = { "saved-bukhari:${it.id}" }) { book ->
            BukhariBookRow(
                book = book,
                saved = true,
                onClick = { onOpenBukhariBook(book.id) },
            )
        }
    }
}

@Composable
internal fun CourseScreen(
    store: SharedContentStore,
    onSelectBottom: (Int) -> Unit,
    searchController: SharedSearchController? = null,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    prayerAlert: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState? = null,
) {
    var completed by remember { mutableStateOf(store.completedLessons()) }
    TopLevelScaffold(
        title = "Course",
        selectedIndex = 3,
        onSelectBottom = onSelectBottom,
        searchController = searchController,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onRefresh = { },
        prayerAlert = prayerAlert,
    ) { _ ->
        item {
            Text(
                "Foundations · ${completed.size}/${SharedCourseLessons.size} complete",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        gridItems(SharedCourseLessons, key = { it.number }) { lesson ->
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
    searchController: SharedSearchController? = null,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    prayerAlert: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState? = null,
    globalRefreshing: Boolean = false,
    globalSyncResultText: String? = null,
) {
    var refreshAttempt by remember { mutableStateOf(0) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val repository = remember { createSharedTopicRepository() }
    var topics by remember { mutableStateOf(emptyList<SharedTopic>()) }
    var followedTopicIds by remember { mutableStateOf(store.followedTopicIds()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTopicId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(repository, refreshAttempt) {
        loading = true
        try {
            val loadedTopics = repository.topics()
            val order = store.topicOrder()
            val topicsById = loadedTopics.associateBy(SharedTopic::id)
            topics = order.mapNotNull(topicsById::get) +
                loadedTopics.filterNot { it.id in order }
            if (selectedTopicId == null || loadedTopics.none { it.id == selectedTopicId }) {
                selectedTopicId = topics.firstOrNull()?.id
            }
            error = null
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            error = failure.message ?: "Unable to read topics"
        }
        loading = false
    }
    LaunchedEffect(loading, pullRefreshing) {
        if (pullRefreshing && !loading) {
            kotlinx.coroutines.delay(700)
            pullRefreshing = false
        }
    }
    TopLevelScaffold(
        title = "Interests",
        selectedIndex = 4,
        onSelectBottom = onSelectBottom,
        searchController = searchController,
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        isRefreshing = pullRefreshing || globalRefreshing,
        syncResultText = globalSyncResultText,
        onRefresh = {
            pullRefreshing = true
            refreshAttempt += 1
        },
        prayerAlert = prayerAlert,
        itemSpacing = 0.dp,
        expandedPane = { modifier ->
            val selectedTopic = topics.firstOrNull { it.id == selectedTopicId }
            if (selectedTopic == null) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text("Select a topic", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                TopicInterestDetailPane(
                    topic = selectedTopic,
                    following = selectedTopic.id in followedTopicIds,
                    onToggle = { followed ->
                        store.setTopicFollowed(selectedTopic.id, followed)
                        followedTopicIds = if (followed) {
                            followedTopicIds + selectedTopic.id
                        } else {
                            followedTopicIds - selectedTopic.id
                        }
                    },
                    onOpen = { onOpenTopic(selectedTopic.id) },
                    modifier = modifier,
                )
            }
        },
    ) { expanded ->
        when {
            loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                SupportingCard("Unable to load topics", error.orEmpty())
            }
            topics.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                SupportingCard("No topics", "The topic database is empty.")
            }
            else -> gridItems(topics, key = { it.id }) { topic ->
                val index = topics.indexOfFirst { it.id == topic.id }
                TopicInterestRow(
                    topic = topic,
                    following = topic.id in followedTopicIds,
                    selected = expanded && topic.id == selectedTopicId,
                    canMoveEarlier = index > 0,
                    canMoveLater = index in 0 until topics.lastIndex,
                    onOpen = {
                        if (expanded) selectedTopicId = topic.id else onOpenTopic(topic.id)
                    },
                    onToggle = { followed ->
                        store.setTopicFollowed(topic.id, followed)
                        followedTopicIds = if (followed) followedTopicIds + topic.id else followedTopicIds - topic.id
                    },
                    onMove = { offset ->
                        val target = index + offset
                        if (index >= 0 && target in topics.indices) {
                            topics = topics.toMutableList().apply {
                                add(target, removeAt(index))
                            }
                            store.saveTopicOrder(topics.map(SharedTopic::id))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TopicInterestRow(
    topic: SharedTopic,
    following: Boolean,
    selected: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
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
            Column(horizontalAlignment = Alignment.End) {
                FilledIconToggleButton(
                    checked = following,
                    onCheckedChange = onToggle,
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
                Row {
                    TextButton(
                        onClick = { onMove(-1) },
                        enabled = canMoveEarlier,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "Move ${topic.name} earlier"
                        },
                    ) { Text("Up", style = MaterialTheme.typography.labelSmall) }
                    TextButton(
                        onClick = { onMove(1) },
                        enabled = canMoveLater,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "Move ${topic.name} later"
                        },
                    ) { Text("Down", style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                Color.Transparent
            },
        ),
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
private fun TopicInterestDetailPane(
    topic: SharedTopic,
    following: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TopicArtwork(
                    topicName = topic.name,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
            item {
                Text(topic.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Text(
                    topic.longDescription.ifBlank { topic.shortDescription },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                FilterChip(
                    selected = following,
                    onClick = { onToggle(!following) },
                    label = { Text(if (following) "FOLLOWING" else "FOLLOW") },
                    leadingIcon = {
                        Icon(
                            if (following) NiaIcons.Check else NiaIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
            item {
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Open topic")
                }
            }
        }
    }
}

@Composable
internal fun TopicNewsScreen(
    topicId: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
    onOpenShamayelBook: (Int) -> Unit = {},
    onOpenArticle: (Int, Int) -> Unit,
    onOpenNews: (Int) -> Unit = {},
    onOpenTopic: (Int) -> Unit = {},
) {
    val topicRepository = remember { createSharedTopicRepository() }
    val newsRepository = remember { createSharedNewsRepository() }
    var topic by remember(topicId) { mutableStateOf(sharedTopic(topicId)) }
    var topics by remember { mutableStateOf(emptyList<SharedTopic>()) }
    var articles by remember(topicId) { mutableStateOf(emptyList<SharedTopicArticle>()) }
    var state by remember(topicId) { mutableStateOf<TopicNewsState>(TopicNewsState.Loading) }
    var followedTopics by remember { mutableStateOf(store.followedTopicIds()) }
    var bookmarkedNewsIds by remember { mutableStateOf(store.bookmarkedNewsIds()) }
    var viewedNewsIds by remember { mutableStateOf(store.viewedNewsIds()) }
    var requestedOffset by remember(topicId) { mutableStateOf(0) }
    var loadAttempt by remember(topicId) { mutableStateOf(0) }

    LaunchedEffect(topicId, topicRepository) {
        println("[TopicScreen] loading topics for topicId=$topicId")
        try {
            val loadedTopics = topicRepository.topics()
            println("[TopicScreen] loaded ${loadedTopics.size} topics, finding id=$topicId")
            topics = loadedTopics
            topic = loadedTopics.firstOrNull { it.id == topicId } ?: sharedTopic(topicId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            topic = sharedTopic(topicId)
        }
    }
    // Articles fallback: Shama'il books, Quranic duas, Fortress duas —
    // loaded for topics whose news list is empty.
    LaunchedEffect(topicId, topicRepository) {
        articles = runCatching { topicRepository.articles(topicId) }.getOrDefault(emptyList())
    }
    LaunchedEffect(topicId, newsRepository, requestedOffset, loadAttempt) {
        println("[TopicScreen] LaunchedEffect FIRED topicId=$topicId offset=$requestedOffset attempt=$loadAttempt")
        val existing = state as? TopicNewsState.Loaded
        if (requestedOffset > 0 && existing == null) return@LaunchedEffect
        state = if (requestedOffset == 0) {
            TopicNewsState.Loading
        } else {
            requireNotNull(existing).copy(loadingMore = true, loadMoreError = null)
        }
        try {
            println("[TopicScreen] calling newsForTopic...")
            val page = newsRepository.newsForTopic(
                topicId = topicId,
                limit = TOPIC_NEWS_PAGE_SIZE,
                offset = requestedOffset,
            )
            val combined = if (requestedOffset == 0) {
                page
            } else {
                requireNotNull(existing).news + page
            }
            println("[TopicScreen] got ${page.size} news items")
            state = TopicNewsState.Loaded(
                news = combined.distinctBy(SharedNewsResource::id),
                nextOffset = requestedOffset + page.size,
                hasMore = page.size == TOPIC_NEWS_PAGE_SIZE,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "Unable to read topic news"
            println("[TopicScreen] ERROR: $message")
            state = existing?.copy(loadingMore = false, loadMoreError = message)
                ?: TopicNewsState.Error(message)
        }
    }

    val currentTopic = topic
    if (currentTopic == null) {
        SharedDetailScaffold(title = "Topic", onBack = onBack) {
            SupportingCard("Topic unavailable", "This topic is not part of the current catalog.")
        }
        return
    }

    // The topic page: a direct LazyColumn — the LazyVerticalGrid inside
    // TopicPageScaffold silently composed nothing (pixel analysis: 98% white
    // below the header on every topic page).
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
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
                    selected = currentTopic.id in followedTopics,
                    onClick = {
                        val followed = currentTopic.id !in followedTopics
                        store.setTopicFollowed(currentTopic.id, followed)
                        followedTopics = if (followed) followedTopics + currentTopic.id else followedTopics - currentTopic.id
                    },
                    label = { Text(if (currentTopic.id in followedTopics) "FOLLOWING" else "NOT FOLLOWING") },
                    leadingIcon = if (currentTopic.id in followedTopics) {
                        { Icon(NiaIcons.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else {
                        { Icon(NiaIcons.Add, contentDescription = null, Modifier.size(18.dp)) }
                    },
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item { TopicPageHeader(currentTopic) }
                when (val current = state) {
                    TopicNewsState.Loading -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    is TopicNewsState.Error -> item {
                        Box(Modifier.padding(horizontal = 24.dp)) {
                            SupportingCard(
                                title = "Unable to load news",
                                body = current.message,
                                action = "Try again",
                                onAction = { loadAttempt++ },
                            )
                        }
                    }
                    is TopicNewsState.Loaded -> if (current.news.isEmpty() && articles.isEmpty()) {
                        item {
                            Box(Modifier.padding(horizontal = 24.dp)) {
                                SupportingCard(
                                    title = "Content not downloaded",
                                    body = "This topic's content needs to be downloaded once. " +
                                        "Go to Settings → Content & Storage to download it, " +
                                        "or pull down to refresh.",
                                )
                            }
                        }
                    } else if (current.news.isEmpty()) {
                        items(articles, key = { "article:${it.id}" }) { article ->
                            TopicArticleRow(
                                article = article,
                                topic = currentTopic,
                                bookmarked = false,
                                onToggleBookmark = { },
                                onClick = { onOpenArticle(topicId, article.id) },
                            )
                        }
                    } else {
                        val topicsById = topics.associateBy(SharedTopic::id)
                        items(current.news, key = { it.id }) { news ->
                            SharedNewsResourceCard(
                                news = news,
                                topicsById = topicsById,
                                bookmarked = news.id in bookmarkedNewsIds,
                                viewed = news.id in viewedNewsIds,
                                onToggleBookmark = {
                                    if (news.id in bookmarkedNewsIds) {
                                        store.setNewsBookmarked(news.id, false)
                                        bookmarkedNewsIds = bookmarkedNewsIds - news.id
                                    } else {
                                        store.setNewsBookmarked(news.id, true)
                                        bookmarkedNewsIds = bookmarkedNewsIds + news.id
                                    }
                                },
                                onClick = {
                                    if (news.id !in viewedNewsIds) {
                                        store.markNewsViewed(news.id)
                                        viewedNewsIds = viewedNewsIds + news.id
                                    }
                                    onOpenNews(news.id)
                                },
                                onTopicClick = onOpenTopic,
                                currentTopicId = topicId,
                                compact = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewsDetailScreen(
    id: Int,
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    val repository = remember { createSharedNewsRepository() }
    var state by remember(id) { mutableStateOf<SharedNewsState>(SharedNewsState.Loading) }
    var bookmarked by remember(id) { mutableStateOf(id in store.bookmarkedNewsIds()) }
    var loadAttempt by remember(id) { mutableStateOf(0) }

    LaunchedEffect(id, repository, loadAttempt) {
        store.markNewsViewed(id)
        state = try {
            repository.newsById(id)?.let { SharedNewsState.Loaded(listOf(it)) }
                ?: SharedNewsState.Error("News $id was not found.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SharedNewsState.Error(error.message ?: "Unable to read this news item")
        }
    }

    val bookmarkAct = DetailAction(
        id = "bookmark",
        label = if (bookmarked) "Remove bookmark" else "Add bookmark",
        icon = NiaIcons.Bookmark.takeIf { bookmarked } ?: NiaIcons.BookmarkBorder,
        selected = bookmarked,
    ) {
        store.setNewsBookmarked(id, !bookmarked)
        bookmarked = !bookmarked
    }
    val shareAct = DetailAction(
        id = "share",
        label = "Share",
    ) { onBack() } // host-level share integration point
    ImmersiveDetailScaffold(onBack = onBack, header = {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            NewsHeaderArtwork("masjid_al_haram", Modifier.fillMaxSize())
            Column {
                DetailToolbar(
                    onBack = onBack,
                    inlineActions = listOf(bookmarkAct),
                    sheetActions = listOf(shareAct),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    toolbarTitle = "Invocations",
                )
            }
            ImmersiveDetailHeaderScrim(
                title = "Invocations",
                supportingText = "From the Noble Quran",
                arabicTitle = "دعاء",
            )
        }
    }) {
        when (val current = state) {
            SharedNewsState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is SharedNewsState.Error -> Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SupportingCard("Unable to load this item", current.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadAttempt++ }) { Text("Try again") }
            }
            is SharedNewsState.Loaded -> {
                val news = current.news.first()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            FilledIconToggleButton(
                                checked = bookmarked,
                                onCheckedChange = { checked ->
                                    store.setNewsBookmarked(id, checked)
                                    bookmarked = checked
                                },
                            ) {
                                Icon(
                                    if (bookmarked) NiaIcons.Bookmark else NiaIcons.BookmarkBorder,
                                    if (bookmarked) {
                                        "Remove bookmark for ${news.title}"
                                    } else {
                                        "Bookmark ${news.title}"
                                    },
                                )
                            }
                        }
                    }
                    item { SharedNewsDetailContent(news) }
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
    content: LazyGridScope.(expanded: Boolean) -> Unit,
) {
    val page: @Composable () -> Unit = {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= EXPANDED_WIDTH
                Column(
                    modifier = Modifier
                        .widthIn(max = 900.dp)
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (expanded) 2 else 1),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        content(expanded)
                    }
                }
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
        state = try {
            TopicArticlesState.Loaded(repository.articles(topicId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            TopicArticlesState.Error(error.message ?: "Unable to read this item")
        }
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
                        if (article.context.isNotBlank()) {
                            item {
                                ReaderSection("Context", MaterialTheme.colorScheme.secondary) {
                                    Text(article.context, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp))
                                }
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

private const val TOPIC_NEWS_PAGE_SIZE = 100
private val EXPANDED_WIDTH = 700.dp

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
    maxContentWidth: Dp = 720.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .imePadding()
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
    searchController: SharedSearchController? = null,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    isRefreshing: Boolean = false,
    syncResultText: String? = null,
    onRefresh: (() -> Unit)? = null,
    prayerAlert: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState? = null,
    itemSpacing: Dp = 10.dp,
    adaptiveGrid: Boolean = false,
    expandedPane: (@Composable (Modifier) -> Unit)? = null,
    content: LazyGridScope.(expanded: Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        val page: @Composable () -> Unit = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= EXPANDED_WIDTH
                // Match Android and the home screen: the side rail appears only in
                // landscape tablet windows; portrait keeps the bottom pill.
                val useSideRail = maxWidth >= 600.dp && maxHeight >= 600.dp && maxWidth > maxHeight
                Column(
                    modifier = Modifier
                        .widthIn(max = if (expandedPane != null) 1200.dp else 1100.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(
                            start = if (useSideRail) 80.dp else 16.dp,
                            end = 16.dp,
                        ),
                ) {
                    // The SAME top bar as the home screen on every page — Android's
                    // AppTopSearchBar: avatar + search pill with voice + settings.
                    if (searchController != null) {
                        SharedUnifiedHeaderRow(
                            searchController = searchController,
                            onOpenSettings = onOpenSettings,
                            onOpenProfile = onOpenProfile,
                        )
                    } else {
                        ScreenHeader(title)
                    }
                    val grid: @Composable (Modifier) -> Unit = { modifier ->
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (adaptiveGrid && expanded) 2 else 1),
                            modifier = modifier,
                            verticalArrangement = Arrangement.spacedBy(itemSpacing),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(
                                top = 12.dp,
                                bottom = if (useSideRail) 24.dp else 88.dp,
                            ),
                        ) {
                            content(expanded)
                        }
                    }
                    if (expanded && expandedPane != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            grid(Modifier.weight(0.55f).fillMaxHeight())
                            expandedPane(Modifier.weight(0.45f).fillMaxHeight())
                        }
                    } else {
                        grid(Modifier.fillMaxWidth().weight(1f))
                    }
                }
                if (useSideRail) {
                    FloatingSideBar(
                        items = SharedBottomBarItems,
                        selectedIndex = selectedIndex,
                        onSelect = onSelectBottom,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                } else {
                    FloatingBottomBar(
                        items = SharedBottomBarItems,
                        selectedIndex = selectedIndex,
                        onSelect = onSelectBottom,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        // Pull-to-refresh on every tab page — the app-level container Android
        // gives all top-level destinations; pages without async data pass a
        // refresh that completes immediately so the gesture springs back.
        if (onRefresh != null) {
            PullToSyncContainer(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                syncResultText = syncResultText,
                prayerAlertState = prayerAlert
                    ?: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState(),
                modifier = Modifier.fillMaxSize(),
            ) { _ -> page() }
        } else {
            page()
        }
    }
}

/** The shared top bar — identical to the home header on every tab page. */
@Composable
private fun SharedUnifiedHeaderRow(
    searchController: SharedSearchController,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTapTarget(
            icon = androidx.compose.material.icons.Icons.Filled.AccountCircle,
            contentDescription = "Open local profile",
            tint = MaterialTheme.colorScheme.onBackground,
            visualSize = 34.dp,
            iconSize = 34.dp,
            showBackground = false,
            onClick = onOpenProfile,
        )
        Surface(
            onClick = { searchController.open() },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp),
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
                    modifier = Modifier.weight(1f),
                )
                IconTapTarget(
                    icon = androidx.compose.material.icons.Icons.Filled.Mic,
                    contentDescription = "Voice search",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    visualSize = 36.dp,
                    iconSize = 20.dp,
                    showBackground = false,
                    onClick = { searchController.openVoice() },
                )
            }
        }
        IconTapTarget(
            icon = androidx.compose.material.icons.Icons.Outlined.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.onBackground,
            visualSize = 36.dp,
            iconSize = 26.dp,
            showBackground = false,
            onClick = onOpenSettings,
        )
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
