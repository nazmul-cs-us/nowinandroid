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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
import com.starception.submission.shared.content.SharedCourses
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
import com.starception.submission.shared.quran.hasLeadingBismillah
import com.starception.submission.shared.quran.QURAN_BISMILLAH
import com.starception.submission.shared.quran.removeLeadingBismillah
import com.starception.submission.shared.quran.createSharedTajweedRepository
import com.starception.submission.shared.quran.metadataLabel
import com.starception.submission.shared.quran.tajweedAnnotatedString
import com.starception.submission.shared.translation.SharedTranslationService
import com.starception.submission.shared.voice.PlatformSpeechSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import io.github.alexzhirkevich.cupertino.CupertinoNavigateBackButton
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveSurface
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveWidget
import io.github.alexzhirkevich.cupertino.adaptive.ExperimentalAdaptiveApi
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme

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
    data class Loaded(
        val verses: List<QuranVerse>,
        val nameTranslation: String,
    ) : QuranAyahState
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
    var playing by remember(number) { mutableStateOf(false) }
    var showAudioPlayer by remember(number) { mutableStateOf(false) }
    LaunchedEffect(number) {
        if (store.quranAutoplayPending()) {
            store.saveQuranAutoplayPending(false)
            playing = player.play(quranAudioUrl(number))
            showAudioPlayer = playing
        }
    }
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
                QuranAyahState.Loaded(
                    verses = verses,
                    nameTranslation = repository.getSurahMetadata(number)?.nameTranslation.orEmpty(),
                )
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
    var mushafCurrentPage by remember(number) { mutableIntStateOf(1) }
    var mushafTotalPages by remember(number) { mutableIntStateOf(1) }
    var requestedMushafPage by remember(number) { mutableIntStateOf(1) }
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
    var showReadingSettings by remember { mutableStateOf(false) }
    // Android's toolbar translation chip — the two-letter code (EN/BN/…) that
    // opens the reading settings' translation picker.
    val translationChipAction = DetailAction(
        id = "translation_chip",
        label = "Translation",
        trailingText = translationLanguage.code.uppercase(),
    ) {
        showReadingSettings = true
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
            if (playing) showAudioPlayer = true
        }
    }
    val readingSettingsAction = DetailAction(
        id = "reading_settings",
        label = "Reading settings",
        trailingText = QuranArabicFonts.displayName(selectedArabicFont),
    ) {
        showReadingSettings = true
    }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    ImmersiveDetailScaffold(
        onBack = onBack,
        collapsibleHeader = true,
        contentHorizontalPadding = 0.dp,
        header = {
            Column {
                if (mushafMode) {
                    SurahMushafMiniBar(
                        surah = surah,
                        currentPage = mushafCurrentPage,
                        totalPages = mushafTotalPages,
                        onPrevious = {
                            if (mushafCurrentPage > 1) {
                                requestedMushafPage = mushafCurrentPage - 1
                            } else if (number > 1) {
                                onOpenSurah(number - 1)
                            }
                        },
                        onNext = {
                            if (mushafCurrentPage < mushafTotalPages) {
                                requestedMushafPage = mushafCurrentPage + 1
                            } else if (number < 114) {
                                onOpenSurah(number + 1)
                            }
                        },
                    )
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 2f)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                ) {
                    com.starception.submission.shared.quran.SurahArtworkHeader(
                        surahNumber = number,
                        contentDescription = "Symbolic artwork for Surah ${surah.nameEnglish}",
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.30f),
                                    0.4f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.08f),
                                ),
                            ),
                    )
                    Column {
                        DetailToolbar(
                            onBack = onBack,
                            inlineActions = listOf(
                                translationChipAction,
                                bookmarkAction,
                            ),
                            sheetActions = listOf(
                                playAudioAction,
                                readingSettingsAction,
                            ),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            toolbarTitle = "Surah ${surah.number} · ${surah.nameEnglish}",
                            includeStatusBarInset = !mushafMode,
                        )
                    }
                }
            }
        },
    ) {
        // The Arabic font picker — Android's FontSelectionDialog as a sheet:
        // one row per font, the active one checked, tap to apply.
        // Surah-to-surah swipe, matching Android's SurahSwipeContainer. In
        // mushaf mode the pager owns horizontal gestures, so the detector
        // only runs in the ayah-list mode.
        var swipeTotalX by remember(number) { mutableStateOf(0f) }
        val swipeModifier = if (!mushafMode) {
            Modifier.pointerInput(number) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeTotalX = 0f },
                    onDragEnd = {
                        if (swipeTotalX < -240f && number < 114) {
                            onOpenSurah(number + 1)
                        } else if (swipeTotalX > 240f && number > 1) {
                            onOpenSurah(number - 1)
                        }
                        swipeTotalX = 0f
                    },
                    onDragCancel = { swipeTotalX = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    swipeTotalX += dragAmount
                }
            }
        } else {
            Modifier
        }
        Column(swipeModifier.weight(1f)) {
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
            if (showAudioPlayer) Surface(
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
                                    if (playing) showAudioPlayer = true
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
                        IconButton(onClick = { isBookmarked = number in store.toggleSurah(number) }) {
                            Icon(
                                if (isBookmarked) NiaIcons.Bookmark else NiaIcons.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark surah",
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
            if (showAudioPlayer) Spacer(Modifier.height(10.dp))
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
                    val showBismillah = remember(number, state.verses) {
                        number != 1 &&
                            number != 9 &&
                            state.verses.firstOrNull()?.arabicText?.let(::hasLeadingBismillah) == true
                    }
                    val displayVerses = remember(state.verses, showBismillah) {
                        if (!showBismillah) {
                            state.verses
                        } else {
                            state.verses.mapIndexed { index, verse ->
                                if (index == 0) {
                                    verse.copy(arabicText = removeLeadingBismillah(verse.arabicText))
                                } else {
                                    verse
                                }
                            }
                        }
                    }
                    if (mushafMode) {
                        MushafPagerView(
                            verses = displayVerses,
                            arabicFont = selectedArabicFont,
                            arabicFontSize = arabicFontSize * 0.68f,
                            showTranslation = showTranslation,
                            textAlignment = textAlignment,
                            showBismillah = showBismillah,
                            tajweedAnnotations = if (tajweedEnabled) tajweedAnnotations else null,
                            openingContent = {
                                SurahAlbumInfoCard(
                                    surah = surah,
                                    ayahCount = displayVerses.size,
                                    nameTranslation = state.nameTranslation,
                                    arabicFont = selectedArabicFont,
                                    playing = playing,
                                    onPlayClick = {
                                        if (playing) {
                                            player.pause()
                                            playing = false
                                        } else {
                                            playing = player.play(quranAudioUrl(number))
                                            if (playing) showAudioPlayer = true
                                        }
                                    },
                                )
                            },
                            requestedPage = requestedMushafPage,
                            onPageChanged = { current, total ->
                                mushafCurrentPage = current
                                mushafTotalPages = total
                                requestedMushafPage = current
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            // Android's AlbumInfoCard: the surah panel between
                            // the artwork and the verses — name translation
                            // quote, then the info chips row.
                            item(key = "surah_info_panel") {
                                SurahAlbumInfoCard(
                                    surah = surah,
                                    ayahCount = displayVerses.size,
                                    nameTranslation = state.nameTranslation,
                                    arabicFont = selectedArabicFont,
                                    playing = playing,
                                    onPlayClick = {
                                        if (playing) {
                                            player.pause()
                                            playing = false
                                        } else {
                                            playing = player.play(quranAudioUrl(number))
                                            if (playing) showAudioPlayer = true
                                        }
                                    },
                                )
                            }
                            if (showBismillah) {
                                item(key = "bismillah") {
                                    Text(
                                        text = QURAN_BISMILLAH,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontFamily = QuranArabicFonts.fontFamily(selectedArabicFont),
                                        fontSize = (arabicFontSize * 0.78f).sp,
                                        lineHeight = (arabicFontSize * 1.15f).sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            items(displayVerses, key = { it.id }) { verse ->
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
                                    onToggleTranslation = {
                                        showTranslation = !showTranslation
                                        store.saveQuranShowTranslation(showTranslation)
                                    },
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
        if (showReadingSettings) {
            SurahReadingSettingsSheet(
                previewText = (ayahState as? QuranAyahState.Loaded)?.verses
                    ?.firstOrNull()?.arabicText
                    ?: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                selectedFont = selectedArabicFont,
                fontSize = arabicFontSize,
                textAlignment = textAlignment,
                mushafMode = mushafMode,
                showTranslation = showTranslation,
                tajweed = tajweedEnabled,
                tajweedAvailable = !tajweedUnavailable,
                translationLanguage = translationLanguage,
                onFontChange = { font ->
                    selectedArabicFont = font
                    store.saveQuranArabicFont(font)
                },
                onFontSizeChange = { size ->
                    arabicFontSize = size
                    store.saveQuranArabicFontSize(size)
                },
                onAlignmentChange = { alignment ->
                    textAlignment = alignment
                    store.saveQuranTextAlignment(alignment)
                },
                onMushafChange = { enabled ->
                    mushafMode = enabled
                    store.saveQuranMushafMode(enabled)
                },
                onTranslationChange = { enabled ->
                    showTranslation = enabled
                    store.saveQuranShowTranslation(enabled)
                },
                onTajweedChange = { enabled ->
                    tajweedEnabled = enabled
                    store.saveQuranTajweedEnabled(enabled)
                },
                onTranslationLanguageChange = { language ->
                    translationLanguage = language
                    store.saveQuranTranslationLanguage(language.code)
                },
                onDismiss = { showReadingSettings = false },
            )
        }
    }
}

@Composable
private fun SurahAlbumInfoCard(
    surah: com.starception.submission.feature.quran.Surah,
    ayahCount: Int,
    nameTranslation: String,
    arabicFont: String,
    playing: Boolean,
    onPlayClick: () -> Unit,
) {
    // Lazy layouts clip children drawn beyond an item's bounds. Reserve room
    // for both overlapping controls so neither is cut at the item boundary.
    Box(modifier = Modifier.fillMaxWidth().height(223.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
                .offset(y = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = surah.nameEnglish,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "سُورَةُ ${surah.nameArabic}",
                        fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (nameTranslation.isNotBlank()) {
                        Text(
                            text = "\"$nameTranslation\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InfoChip(text = "$ayahCount Ayahs")
                        InfoChip(text = surah.revelationType)
                        InfoChip(text = "Holy Quran")
                    }
                }
            }
        }
        Surface(
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-24).dp)
                .size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause recitation" else "Play surah",
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(46.dp),
        )
    }
}

@Composable
private fun SurahMushafMiniBar(
    surah: com.starception.submission.feature.quran.Surah,
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "سُورَةُ ${surah.nameArabic}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = " · ${surah.nameEnglish} · ${surah.number}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Show Surah information",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                modifier = Modifier.size(18.dp),
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
            ) {
                Text(
                    text = "$currentPage/$totalPages",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = if (currentPage == 1) "Previous Surah" else "Previous page",
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (currentPage == totalPages) "Next Surah" else "Next page",
                )
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
                textAlign = when (textAlignment) {
                    "start" -> TextAlign.Start
                    "center" -> TextAlign.Center
                    "end" -> TextAlign.End
                    else -> TextAlign.Justify
                },
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
                verse.translation,
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
            ImmersiveDetailHeaderScrim(
                title = "Fortress of the Muslim",
                supportingText = chapter?.title ?: "Chapter $chapterId",
                arabicTitle = "حصن المسلم",
            )
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

/**
 * The reading-settings bottom sheet — Android's floating reading toolbar
 * layout: drag handle, a live preview of the actual first ayah in the chosen
 * typeface, a Play-Books-style size slider, segmented alignment icons,
 * Mushaf/translation/tajweed switch rows, and inline expandable Arabic font
 * and translation-language pickers with tinted pills. Pull down (or tap the
 * scrim) to dismiss.
 */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun SurahReadingSettingsSheet(
    previewText: String,
    selectedFont: String,
    fontSize: Float,
    textAlignment: String,
    mushafMode: Boolean,
    showTranslation: Boolean,
    tajweed: Boolean,
    tajweedAvailable: Boolean,
    translationLanguage: com.starception.submission.shared.quran.QuranTranslationLanguage,
    onFontChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onAlignmentChange: (String) -> Unit,
    onMushafChange: (Boolean) -> Unit,
    onTranslationChange: (Boolean) -> Unit,
    onTajweedChange: (Boolean) -> Unit,
    onTranslationLanguageChange: (com.starception.submission.shared.quran.QuranTranslationLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Entrance: the sheet slides up from below the finger's side of the screen.
    val slideIn = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        slideIn.animateTo(
            1f,
            androidx.compose.animation.core.tween(durationMillis = 280),
        )
    }
    // Whole-sheet drag-to-dismiss past a threshold, springing back otherwise.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var expandedSection by remember { mutableStateOf<String?>(null) }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        // No scrim behind the sheet: the ayah text stays visible above it so
        // the preview and the page read together — Android keeps the page up.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = ((1f - slideIn.value) * size.height) + dragOffset
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0f) dragOffset += dragAmount
                        },
                        onDragEnd = {
                            if (dragOffset > 150f) {
                                onDismiss()
                            } else {
                                dragOffset = 0f
                            }
                        },
                        onDragCancel = { dragOffset = 0f },
                    )
                },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Drag handle — the whole sheet is draggable.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }

                // Food Truck's grouped-form layout: titled sections on cards.
                SettingsSection(title = "Preview") {
                    Text(
                        text = previewText,
                        fontFamily = com.starception.submission.shared.quran.QuranArabicFonts.fontFamily(selectedFont),
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.7f).sp,
                        textAlign = when (textAlignment) {
                            "start" -> TextAlign.Start
                            "center" -> TextAlign.Center
                            "end" -> TextAlign.End
                            else -> TextAlign.Justify
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }

                SettingsSection(title = "Text") {
                    // Menu-picker rows — the value with a trailing chevron.
                    SettingsPickerRow(
                        label = "Arabic font",
                        value = com.starception.submission.shared.quran.QuranArabicFonts.displayName(selectedFont),
                        expanded = expandedSection == "font",
                        onToggle = {
                            expandedSection = if (expandedSection == "font") null else "font"
                        },
                    )
                    if (expandedSection == "font") {
                        com.starception.submission.shared.quran.QuranArabicFonts.selectionOrder.forEach { font ->
                            SettingsOptionRow(
                                label = com.starception.submission.shared.quran.QuranArabicFonts.displayName(font),
                                selected = font == selectedFont,
                                onClick = {
                                    onFontChange(font)
                                    expandedSection = null
                                },
                            )
                        }
                    }
                    SettingsPickerRow(
                        label = "Translation",
                        value = translationLanguage.displayName,
                        expanded = expandedSection == "language",
                        onToggle = {
                            expandedSection = if (expandedSection == "language") null else "language"
                        },
                    )
                    if (expandedSection == "language") {
                        com.starception.submission.shared.quran.QuranTranslationLanguage.entries.forEach { language ->
                            SettingsOptionRow(
                                label = language.displayName,
                                selected = language == translationLanguage,
                                onClick = {
                                    onTranslationLanguageChange(language)
                                    expandedSection = null
                                },
                            )
                        }
                    }
                    SettingsRowDivider()
                    // Food Truck's quantity stepper: −, value, +.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Text size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                                )
                                onFontSizeChange((fontSize - 1f).coerceIn(28f, 60f))
                            },
                            enabled = fontSize > 28f,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Text(
                                "−",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(0.dp),
                            )
                        }
                        Text(
                            fontSize.toInt().toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                                )
                                onFontSizeChange((fontSize + 1f).coerceIn(28f, 60f))
                            },
                            enabled = fontSize < 60f,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Text(
                                "+",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }

                SettingsSection(title = "Layout") {
                    val options = listOf(
                        "Left" to "start",
                        "Center" to "center",
                        "Right" to "end",
                        "Justified" to "justify",
                    )
                    io.github.alexzhirkevich.cupertino.adaptive.AdaptiveWidget(
                        material = {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                options.forEachIndexed { index, (label, value) ->
                                    SegmentedButton(
                                        selected = textAlignment == value,
                                        onClick = { onAlignmentChange(value) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = options.size,
                                        ),
                                        icon = {},
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        },
                        cupertino = {
                            // The system's segmented control, like Settings.app.
                            io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl(
                                selectedTabIndex = options
                                    .indexOfFirst { it.second == textAlignment }
                                    .coerceAtLeast(0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                options.forEach { (label, value) ->
                                    io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab(
                                        onClick = { onAlignmentChange(value) },
                                        isSelected = textAlignment == value,
                                    ) {
                                        Text(label)
                                    }
                                }
                            }
                        },
                    )
                }

                SettingsSection(title = "Display") {
                    SettingsSwitchRow(
                        label = "Mushaf page",
                        checked = mushafMode,
                        onCheckedChange = onMushafChange,
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        label = "Show translation",
                        checked = showTranslation,
                        onCheckedChange = onTranslationChange,
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        label = "Tajweed",
                        checked = tajweed,
                        available = tajweedAvailable,
                        onCheckedChange = onTajweedChange,
                    )
                }
            }
        }
    }
}

/** Reading controls shared by Dua and Hadith details, without Quran-only options. */
@androidx.compose.runtime.Composable
private fun ReaderReadingSettingsSheet(
    previewText: String,
    selectedFont: String,
    fontSize: Float,
    textAlignment: String,
    onFontChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onAlignmentChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var expandedFontPicker by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = dragOffset }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0f) dragOffset += dragAmount
                        },
                        onDragEnd = {
                            if (dragOffset > 150f) onDismiss() else dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    )
                },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Text(
                    text = "Reading settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                SettingsSection(title = "Preview") {
                    Text(
                        text = previewText,
                        fontFamily = QuranArabicFonts.fontFamily(selectedFont),
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.65f).sp,
                        textAlign = readerTextAlignment(textAlignment),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                SettingsSection(title = "Text") {
                    SettingsPickerRow(
                        label = "Arabic font",
                        value = QuranArabicFonts.displayName(selectedFont),
                        expanded = expandedFontPicker,
                        onToggle = { expandedFontPicker = !expandedFontPicker },
                    )
                    if (expandedFontPicker) {
                        QuranArabicFonts.selectionOrder.forEach { font ->
                            SettingsOptionRow(
                                label = QuranArabicFonts.displayName(font),
                                selected = font == selectedFont,
                                onClick = {
                                    onFontChange(font)
                                    expandedFontPicker = false
                                },
                            )
                        }
                    }
                    SettingsRowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Text size", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                                )
                                onFontSizeChange((fontSize - 1f).coerceIn(28f, 60f))
                            },
                            enabled = fontSize > 28f,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(30.dp),
                        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                        Text(
                            fontSize.toInt().toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                                )
                                onFontSizeChange((fontSize + 1f).coerceIn(28f, 60f))
                            },
                            enabled = fontSize < 60f,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(30.dp),
                        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                    }
                }
                SettingsSection(title = "Alignment") {
                    val options = listOf(
                        "Left" to "start",
                        "Center" to "center",
                        "Right" to "end",
                        "Justified" to "justify",
                    )
                    io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl(
                        selectedTabIndex = options.indexOfFirst { it.second == textAlignment }.coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        options.forEach { (label, value) ->
                            io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab(
                                onClick = { onAlignmentChange(value) },
                                isSelected = textAlignment == value,
                            ) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}

private fun readerTextAlignment(value: String): TextAlign = when (value) {
    "start" -> TextAlign.Start
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    else -> TextAlign.Justify
}

/** One Food Truck-style grouped form section: title above a rounded card. */
@androidx.compose.runtime.Composable
private fun SettingsSection(
    title: String,
    content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** A value row that expands its picker inline — SwiftUI's menu-picker look. */
@androidx.compose.runtime.Composable
private fun SettingsPickerRow(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = NiaIcons.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(12.dp)
                .rotate(if (expanded) 90f else -90f),
        )
    }
}

/** One picker option — the selected one gets a soft tinted pill. */
@androidx.compose.runtime.Composable
private fun SettingsOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = NiaIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The hairline between rows inside a section card. */
@androidx.compose.runtime.Composable
private fun SettingsRowDivider() {
    Box(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

/** A label + switch row — SwiftUI's toggle row, Cupertino's switch on iOS. */
@androidx.compose.runtime.Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    available: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = available) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (available) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        io.github.alexzhirkevich.cupertino.adaptive.AdaptiveSwitch(
            checked = checked,
            onCheckedChange = { enabled ->
                if (available || !enabled) onCheckedChange(enabled)
            },
        )
    }
}


/** Android's InfoChip (NiaTopicTag look): a tinted uppercase pill. */
@androidx.compose.runtime.Composable
private fun InfoChip(text: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
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
    store: SharedContentStore,
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
    val speechSynthesizer = remember { PlatformSpeechSynthesizer() }
    var bookmarked by remember(number) {
        mutableStateOf(QURANIC_DUA_NEWS_ID_OFFSET + number in store.bookmarkedNewsIds())
    }
    var showReadingSettings by remember { mutableStateOf(false) }
    var selectedArabicFont by remember { mutableStateOf(store.quranArabicFont()) }
    var arabicFontSize by remember { mutableStateOf(store.quranArabicFontSize()) }
    var textAlignment by remember { mutableStateOf(store.quranTextAlignment()) }
    DisposableEffect(speechSynthesizer) {
        onDispose { speechSynthesizer.stop() }
    }
    val bookmarkAction = DetailAction(
        id = "bookmark",
        label = if (bookmarked) "Remove bookmark" else "Bookmark dua",
        icon = NiaIcons.Bookmark.takeIf { bookmarked } ?: NiaIcons.BookmarkBorder,
        selected = bookmarked,
    ) {
        val newsId = QURANIC_DUA_NEWS_ID_OFFSET + number
        store.setNewsBookmarked(newsId, !bookmarked)
        bookmarked = !bookmarked
    }
    val readingSettingsAction = DetailAction(
        id = "reading_settings",
        label = "Reading settings",
        trailingText = QuranArabicFonts.displayName(selectedArabicFont),
    ) { showReadingSettings = true }
    Box(Modifier.fillMaxSize()) {
        ImmersiveDetailScaffold(onBack = onBack, header = {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.42f),
                                0.4f to Color.Black.copy(alpha = 0.08f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
                Column {
                    DetailToolbar(
                        onBack = onBack,
                        inlineActions = listOf(bookmarkAction),
                        sheetActions = listOf(
                            readingSettingsAction,
                            DetailAction(
                                id = "listen",
                                label = if (listening) "Stop narration" else "Listen (TTS)",
                                selected = listening,
                            ) {
                                listening = !listening
                                val dua = (state as? DuaDetailState.Loaded)?.dua
                                if (listening && dua != null) {
                                    val started = speechSynthesizer.speak(text = dua.translation) {
                                        listening = false
                                    }
                                    if (!started) listening = false
                                } else {
                                    speechSynthesizer.stop()
                                    listening = false
                                }
                            },
                        ),
                        contentColor = androidx.compose.ui.graphics.Color.White,
                        toolbarTitle = "Quranic Dua $number",
                    )
                }
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
                val arabicFontFamily = QuranArabicFonts.fontFamily(selectedArabicFont)
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
                    if (dua.arabic.isNotBlank()) {
                        item {
                            ReaderSection("Arabic", MaterialTheme.colorScheme.primary) {
                                Text(
                                    dua.arabic,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        textDirection = TextDirection.Rtl,
                                    ),
                                    fontFamily = arabicFontFamily,
                                    fontSize = arabicFontSize.sp,
                                    lineHeight = (arabicFontSize * 1.65f).sp,
                                    textAlign = readerTextAlignment(textAlignment),
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
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 26.sp,
                                        fontStyle = FontStyle.Italic,
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 26.sp,
                                        fontStyle = FontStyle.Italic,
                                    ),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    if (dua.explanation.isNotBlank()) {
                        item {
                            ReaderSection(
                                title = "Explanation",
                                accent = MaterialTheme.colorScheme.primary,
                                collapsible = true,
                                initiallyExpanded = false,
                            ) {
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
        if (showReadingSettings) {
            ReaderReadingSettingsSheet(
                previewText = (state as? DuaDetailState.Loaded)?.dua?.arabic
                    ?: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                selectedFont = selectedArabicFont,
                fontSize = arabicFontSize,
                textAlignment = textAlignment,
                onFontChange = { font ->
                    selectedArabicFont = font
                    store.saveQuranArabicFont(font)
                },
                onFontSizeChange = { size ->
                    arabicFontSize = size
                    store.saveQuranArabicFontSize(size)
                },
                onAlignmentChange = { alignment ->
                    textAlignment = alignment
                    store.saveQuranTextAlignment(alignment)
                },
                onDismiss = { showReadingSettings = false },
            )
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

/** Matches Android's DatabaseSyncHelper mapping for Quranic duas in news. */
private const val QURANIC_DUA_NEWS_ID_OFFSET = 100

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
    var arabicFontSize by remember { mutableStateOf(store.quranArabicFontSize()) }
    var textAlignment by remember { mutableStateOf(store.quranTextAlignment()) }
    var showReadingSettings by remember { mutableStateOf(false) }
    var translationLanguage by remember { mutableStateOf(store.hadithTranslationLanguage()) }
    var translationProvider by remember { mutableStateOf(store.hadithTranslationProvider()) }
    val readingSettingsAction = DetailAction(
        id = "reading_settings",
        label = "Reading settings",
        trailingText = QuranArabicFonts.displayName(selectedArabicFont),
    ) { showReadingSettings = true }
    // Play-all queue: narrates from the current hadith to the end of the
    // book, auto-advancing the pager as each narration finishes — the shared
    // counterpart of the Android hadith playlist.
    var queueIndex by remember { mutableStateOf<Int?>(null) }
    val speechSynthesizer = remember { PlatformSpeechSynthesizer() }
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
            // Drives the queue: scrolls to the queued hadith, narrates it,
            // then enqueues the next one. A null index or a stop cancels.
            LaunchedEffect(queueIndex) {
                val index = queueIndex ?: return@LaunchedEffect
                if (index >= hadiths.size) {
                    queueIndex = null
                    listening = false
                    return@LaunchedEffect
                }
                pagerState.animateScrollToPage(index)
                listening = true
                val sourceText = hadiths[index].english
                val utterance = if (translationLanguage == "en") {
                    sourceText
                } else {
                    SharedTranslationService.translateFromEnglish(
                        text = sourceText,
                        targetLang = translationLanguage,
                        provider = translationProvider,
                    )
                }
                if (utterance.isBlank()) {
                    queueIndex = index + 1
                    return@LaunchedEffect
                }
                val started = speechSynthesizer.speak(
                    text = utterance,
                    language = hadithNarrationLanguage(translationLanguage),
                ) { error ->
                    if (error == null && queueIndex == index) {
                        queueIndex = index + 1
                    } else if (error != null) {
                        queueIndex = null
                        listening = false
                    }
                }
                if (!started) {
                    queueIndex = null
                    listening = false
                }
            }
            // Stop narration when the user swipes to another hadith manually.
            LaunchedEffect(pagerState.currentPage) {
                if (queueIndex == null) {
                    speechSynthesizer.stop()
                    listening = false
                }
            }
            androidx.compose.runtime.DisposableEffect(speechSynthesizer) {
                onDispose {
                    speechSynthesizer.stop()
                }
            }
            val currentHadith = hadiths.getOrNull(pagerState.currentPage) ?: hadiths.first()
            var translatedText by remember { mutableStateOf<String?>(null) }
            var isTranslating by remember { mutableStateOf(false) }
            LaunchedEffect(currentHadith.id, translationLanguage, translationProvider) {
                if (translationLanguage == "en") {
                    translatedText = null
                    isTranslating = false
                } else {
                    isTranslating = true
                    try {
                        translatedText = SharedTranslationService.translateFromEnglish(
                            text = currentHadith.english,
                            targetLang = translationLanguage,
                            provider = translationProvider,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        translatedText = null
                    } finally {
                        isTranslating = false
                    }
                }
            }
            val narrationText = translatedText?.takeIf { it.isNotBlank() } ?: currentHadith.english
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
                if (listening && queueIndex == null) {
                    speechSynthesizer.stop()
                    listening = false
                } else {
                    queueIndex = null
                    speechSynthesizer.stop()
                    listening = speechSynthesizer.speak(
                        text = narrationText,
                        language = hadithNarrationLanguage(translationLanguage),
                    ) { listening = false }
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
                    speechSynthesizer.stop()
                    queueIndex = null
                    listening = false
                } else {
                    speechSynthesizer.stop()
                    queueIndex = pagerState.currentPage
                }
            }
            Box(Modifier.fillMaxSize()) {
                ImmersiveDetailScaffold(onBack = onBack, header = {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxSize())
                    ImmersiveDetailHeaderScrim(
                        title = "Sahih al-Bukhari",
                        supportingText = "Hadith #${currentHadith.id} · ${pagerState.currentPage + 1} of ${hadiths.size}",
                        arabicTitle = "صحيح البخاري",
                    )
                    Column {
                        DetailToolbar(
                            onBack = onBack,
                            sheetActions = listOf(
                                listenAction,
                                playAllAction,
                                translationLanguageAction,
                                translationProviderAction,
                                readingSettingsAction,
                            ),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            toolbarTitle = "Sahih al-Bukhari · Hadith ${currentHadith.id}",
                        )
                    }
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
                        arabicFontSize = arabicFontSize,
                        textAlignment = textAlignment,
                        listening = listening && index == pagerState.currentPage,
                        onListeningChange = { enabled ->
                            if (enabled) {
                                queueIndex = null
                                speechSynthesizer.stop()
                                listening = speechSynthesizer.speak(
                                    text = narrationText,
                                    language = hadithNarrationLanguage(translationLanguage),
                                ) { listening = false }
                            } else {
                                speechSynthesizer.stop()
                                listening = false
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
                        "Hadith #${currentHadith.id} · ${pagerState.currentPage + 1} of ${hadiths.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
                if (showReadingSettings) {
                    ReaderReadingSettingsSheet(
                        previewText = currentHadith.arabic.ifBlank {
                            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
                        },
                        selectedFont = selectedArabicFont,
                        fontSize = arabicFontSize,
                        textAlignment = textAlignment,
                        onFontChange = { font ->
                            selectedArabicFont = font
                            store.saveQuranArabicFont(font)
                        },
                        onFontSizeChange = { size ->
                            arabicFontSize = size
                            store.saveQuranArabicFontSize(size)
                        },
                        onAlignmentChange = { alignment ->
                            textAlignment = alignment
                            store.saveQuranTextAlignment(alignment)
                        },
                        onDismiss = { showReadingSettings = false },
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
    arabicFontSize: Float,
    textAlignment: String,
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
            HadithListenButton(hadith.english, listening) { enabled ->
                onListeningChange(enabled)
            }
        }
        if (hadith.arabic.isNotBlank()) {
            item {
                ReaderSection("Arabic", MaterialTheme.colorScheme.primary) {
                    Text(
                        hadith.arabic,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl,
                        ),
                        fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                        fontSize = arabicFontSize.sp,
                        lineHeight = (arabicFontSize * 1.65f).sp,
                        textAlign = readerTextAlignment(textAlignment),
                    )
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
        } else if (hadith.english.isNotBlank()) {
            item {
                val hasSelectedTranslation = !translatedText.isNullOrBlank() && translatedText != hadith.english
                ReaderSection(
                    if (hasSelectedTranslation) {
                        "Translation (${SharedTranslationService.displayName(translationLanguage)})"
                    } else {
                        "English translation"
                    },
                    MaterialTheme.colorScheme.secondary,
                ) {
                    Text(
                        translatedText.takeIf { hasSelectedTranslation } ?: hadith.english,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 27.sp,
                            textDirection = if (translationLanguage == "ar" || translationLanguage == "ur") {
                                TextDirection.Rtl
                            } else {
                                TextDirection.Content
                            },
                        ),
                        textAlign = if (translationLanguage == "ar" || translationLanguage == "ur") {
                            TextAlign.End
                        } else {
                            TextAlign.Start
                        },
                    )
                }
            }
        }
        if (hadith.explanation.isNotBlank()) {
            item {
                ReaderSection(
                    title = "Explanation",
                    accent = MaterialTheme.colorScheme.tertiary,
                    collapsible = true,
                    initiallyExpanded = false,
                ) {
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
    var arabicFontSize by remember { mutableStateOf(store.quranArabicFontSize()) }
    var textAlignment by remember { mutableStateOf(store.quranTextAlignment()) }
    var showReadingSettings by remember { mutableStateOf(false) }
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
            val speechSynthesizer = remember { PlatformSpeechSynthesizer() }
            LaunchedEffect(queueIndex) {
                val index = queueIndex ?: return@LaunchedEffect
                if (index >= hadiths.size) {
                    queueIndex = null
                    listening = false
                    return@LaunchedEffect
                }
                pagerState.animateScrollToPage(index)
                listening = true
                val sourceText = hadiths[index].english
                val utterance = if (translationLanguage == "en") {
                    sourceText
                } else {
                    SharedTranslationService.translateFromEnglish(
                        text = sourceText,
                        targetLang = translationLanguage,
                        provider = translationProvider,
                    )
                }
                if (utterance.isBlank()) {
                    queueIndex = index + 1
                    return@LaunchedEffect
                }
                val started = speechSynthesizer.speak(
                    text = utterance,
                    language = hadithNarrationLanguage(translationLanguage),
                ) { error ->
                    if (error == null && queueIndex == index) {
                        queueIndex = index + 1
                    } else if (error != null) {
                        queueIndex = null
                        listening = false
                    }
                }
                if (!started) {
                    queueIndex = null
                    listening = false
                }
            }
            LaunchedEffect(pagerState.currentPage) {
                if (queueIndex == null) {
                    speechSynthesizer.stop()
                    listening = false
                }
            }
            androidx.compose.runtime.DisposableEffect(speechSynthesizer) {
                onDispose {
                    speechSynthesizer.stop()
                }
            }
            val currentHadith = hadiths.getOrNull(pagerState.currentPage) ?: hadiths.first()
            var translatedText by remember { mutableStateOf<String?>(null) }
            var isTranslating by remember { mutableStateOf(false) }
            LaunchedEffect(currentHadith.id, translationLanguage, translationProvider) {
                if (translationLanguage == "en") {
                    translatedText = null
                    isTranslating = false
                } else {
                    isTranslating = true
                    try {
                        translatedText = SharedTranslationService.translateFromEnglish(
                            text = currentHadith.english,
                            targetLang = translationLanguage,
                            provider = translationProvider,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        translatedText = null
                    } finally {
                        isTranslating = false
                    }
                }
            }
            val narrationText = translatedText?.takeIf { it.isNotBlank() } ?: currentHadith.english
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
            val readingSettingsAction = DetailAction(
                id = "reading_settings",
                label = "Reading settings",
                trailingText = QuranArabicFonts.displayName(selectedArabicFont),
            ) { showReadingSettings = true }
            val listenAction = DetailAction(
                id = "listen",
                label = if (listening && queueIndex == null) "Stop narration" else "Listen (TTS)",
                selected = listening && queueIndex == null,
            ) {
                if (listening && queueIndex == null) {
                    speechSynthesizer.stop()
                    listening = false
                } else {
                    queueIndex = null
                    speechSynthesizer.stop()
                    listening = speechSynthesizer.speak(
                        text = narrationText,
                        language = hadithNarrationLanguage(translationLanguage),
                    ) { listening = false }
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
                    speechSynthesizer.stop()
                    queueIndex = null
                    listening = false
                } else {
                    speechSynthesizer.stop()
                    queueIndex = pagerState.currentPage
                }
            }
            Box(Modifier.fillMaxSize()) {
                ImmersiveDetailScaffold(onBack = onBack, header = {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    NewsHeaderArtwork("masjid_al_nawabi", Modifier.fillMaxSize())
                    ImmersiveDetailHeaderScrim(
                        title = "Shama'il At-Tirmidhi",
                        supportingText = "Hadith #${currentHadith.id} · ${pagerState.currentPage + 1} of ${hadiths.size}",
                        arabicTitle = "شمائل الترمذي",
                    )
                    Column {
                        DetailToolbar(
                            onBack = onBack,
                            sheetActions = listOf(
                                listenAction,
                                playAllAction,
                                translationLanguageAction,
                                translationProviderAction,
                                readingSettingsAction,
                            ),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            toolbarTitle = "Shama'il At-Tirmidhi · Hadith ${currentHadith.id}",
                        )
                    }
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
                        arabicFontSize = arabicFontSize,
                        textAlignment = textAlignment,
                        listening = listening && index == pagerState.currentPage,
                        onListeningChange = { enabled ->
                            if (enabled) {
                                queueIndex = null
                                speechSynthesizer.stop()
                                listening = speechSynthesizer.speak(
                                    text = narrationText,
                                    language = hadithNarrationLanguage(translationLanguage),
                                ) { listening = false }
                            } else {
                                speechSynthesizer.stop()
                                listening = false
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
                        "Hadith #${currentHadith.id} · ${pagerState.currentPage + 1} of ${hadiths.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
                if (showReadingSettings) {
                    ReaderReadingSettingsSheet(
                        previewText = currentHadith.arabic.ifBlank {
                            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
                        },
                        selectedFont = selectedArabicFont,
                        fontSize = arabicFontSize,
                        textAlignment = textAlignment,
                        onFontChange = { font ->
                            selectedArabicFont = font
                            store.saveQuranArabicFont(font)
                        },
                        onFontSizeChange = { size ->
                            arabicFontSize = size
                            store.saveQuranArabicFontSize(size)
                        },
                        onAlignmentChange = { alignment ->
                            textAlignment = alignment
                            store.saveQuranTextAlignment(alignment)
                        },
                        onDismiss = { showReadingSettings = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun HadithListenButton(
    englishText: String,
    listening: Boolean,
    onToggle: (enabled: Boolean) -> Unit,
) {
    if (englishText.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onToggle(!listening)
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

private fun hadithNarrationLanguage(language: String): String = when (language) {
    "ar" -> "ar-SA"
    "bn" -> "bn-BD"
    "zh" -> "zh-CN"
    "en" -> "en-US"
    "es" -> "es-ES"
    "fr" -> "fr-FR"
    "id" -> "id-ID"
    "ru" -> "ru-RU"
    "sv" -> "sv-SE"
    "tr" -> "tr-TR"
    "ur" -> "ur-PK"
    else -> language
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
            val loadedTopics = topicRepository.topics()
            val order = store.topicOrder()
            val topicsById = loadedTopics.associateBy(SharedTopic::id)
            topics = order.mapNotNull(topicsById::get) +
                loadedTopics.filterNot { it.id in order }
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
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromIndex = topics.indexOfFirst { "onboarding:${it.id}" == from.key }
        val toIndex = topics.indexOfFirst { "onboarding:${it.id}" == to.key }
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            topics = topics.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
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
        gridState = gridState,
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
                    ReorderableItem(reorderState, key = "onboarding:${topic.id}") { isDragging ->
                        OnboardingTopicRow(
                            topic = topic,
                            followed = topic.id in followedTopicIds,
                            isDragging = isDragging,
                            onFollowChanged = { followed ->
                                store.setTopicFollowed(topic.id, followed)
                                followedTopicIds = if (followed) {
                                    followedTopicIds + topic.id
                                } else {
                                    followedTopicIds - topic.id
                                }
                            },
                            dragHandleModifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    store.saveTopicOrder(topics.map(SharedTopic::id))
                                },
                            ),
                        )
                    }
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
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(dragHandleModifier)
            .graphicsLayer {
                val scale = if (isDragging) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            },
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
    onOpenSurah: (Int) -> Unit = {},
    onOpenBukhariBook: (Int) -> Unit = {},
    onOpenCourseDetail: (String) -> Unit = {},
) {
    val allCourses = remember { com.starception.submission.shared.content.SharedCourses }
    var enrolled by remember { mutableStateOf(store.enrolledCourses()) }
    var progress by remember {
        mutableStateOf(allCourses.associate { it.id to store.courseProgress(it.id) })
    }
    var selectedFilter by remember { mutableStateOf("MY_COURSES") }

    val myCourses = allCourses.filter { it.id in enrolled }
    val featuredCourses = when (selectedFilter) {
        "QURAN" -> allCourses.filter { it.category == "Quran" }
        "HADITH" -> allCourses.filter { it.category == "Hadith" }
        "MEMORIZATION" -> allCourses.filter { it.category == "Memorization" }
        else -> allCourses
    }
    val filteredMyCourses = when (selectedFilter) {
        "QURAN" -> myCourses.filter { it.category == "Quran" }
        "HADITH" -> myCourses.filter { it.category == "Hadith" }
        "MEMORIZATION" -> myCourses.filter { it.category == "Memorization" }
        else -> myCourses
    }

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
        // Editorial header — Android's CourseEditorialHeader
        item {
            Text(
                "Small lessons. Meaningful progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
            )
        }

        // Filter chips — Android's CourseHeader
        item {
            val primaryAccent = MaterialTheme.colorScheme.primary
            data class Filter(val key: String, val label: String, val accent: androidx.compose.ui.graphics.Color)
            val filters = listOf(
                Filter("MY_COURSES", "Recent", primaryAccent),
                Filter("QURAN", "Quran", androidx.compose.ui.graphics.Color(0xFF4F779D)),
                Filter("HADITH", "Hadith", androidx.compose.ui.graphics.Color(0xFF99593C)),
                Filter("MEMORIZATION", "Memorize", androidx.compose.ui.graphics.Color(0xFFCEC3A1)),
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(filters, key = { it.key }) { filter ->
                    val key = filter.key
                    val label = filter.label
                    val accent = filter.accent
                    val selected = selectedFilter == key
                    Surface(
                        onClick = { selectedFilter = key },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Learning overview — Android's CourseLearningOverview
        item {
            val completedLessons = myCourses.sumOf { progress[it.id] ?: 0 }
            val totalLessons = myCourses.sumOf { it.totalLessons }
            val overallProgress = if (totalLessons > 0) completedLessons.toFloat() / totalLessons else 0f
            val barHeights = listOf(14, 19, 25, 20, 31, 37, 29, 43, 34, 49, 39, 54)
            val accent = MaterialTheme.colorScheme.primary

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Header row: icon + title/lessons + chevron
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    NiaIcons.Upcoming,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    if (myCourses.isEmpty()) "Start learning" else "Learning progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (myCourses.isEmpty()) {
                                        "${allCourses.size} courses ready to explore"
                                    } else {
                                        "$completedLessons of $totalLessons lessons"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Browse all courses",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    // Percentage + active badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${(overallProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
                            )
                            Text(
                                if (myCourses.isEmpty()) "Ready when you are" else "Overall completion",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                if (myCourses.isEmpty()) "${allCourses.size} available" else "${myCourses.size} active",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    // Mini histogram — Android's 12-bar progress bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        barHeights.forEachIndexed { index, barHeight ->
                            val reached = overallProgress > 0f &&
                                (index + 1).toFloat() / barHeights.size <= overallProgress
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (reached) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                    ),
                            )
                        }
                    }
                }
            }
        }

        // Featured courses — Android's CourseSwipeableTiles (horizontal carousel)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Featured courses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(featuredCourses, key = { it.id }) { course ->
                    val isEnrolled = course.id in enrolled
                    val courseProgress = progress[course.id] ?: 0
                    val percent = if (course.totalLessons > 0) courseProgress * 100 / course.totalLessons else 0
                    val accent = when (course.category) {
                        "Quran" -> androidx.compose.ui.graphics.Color(0xFF4F779D)
                        "Hadith" -> androidx.compose.ui.graphics.Color(0xFF99593C)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        onClick = { onOpenCourseDetail(course.id) },
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.width(300.dp).height(224.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(accent, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            course.category.take(1),
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 23.sp,
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            course.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 20.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            course.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text(
                                    "${course.totalLessons} lessons · ${course.difficulty}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isEnrolled) {
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = accent,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        when {
                                            percent >= 100 -> "Course complete"
                                            isEnrolled -> "$percent% complete"
                                            else -> course.category
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Surface(
                                        onClick = {
                                            if (isEnrolled && percent < 100) {
                                                store.incrementCourseProgress(course.id)
                                                progress = progress + (course.id to store.courseProgress(course.id))
                                            } else if (!isEnrolled) {
                                                enrolled = store.toggleEnrolledCourse(course.id)
                                            }
                                        },
                                        shape = CircleShape,
                                        color = accent,
                                    ) {
                                        Box(
                                            modifier = Modifier.size(42.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                if (isEnrolled && percent < 100) "▶" else "→",
                                                color = androidx.compose.ui.graphics.Color.White,
                                                fontSize = 18.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Continue learning — Android's OngoingCourseList
        if (filteredMyCourses.isNotEmpty()) {
            item {
                Text(
                    "Continue learning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            gridItems(filteredMyCourses, key = { it.id }) { course ->
                val courseProgress = progress[course.id] ?: 0
                val percent = if (course.totalLessons > 0) courseProgress * 100 / course.totalLessons else 0
                val accent = when (course.category) {
                    "Quran" -> androidx.compose.ui.graphics.Color(0xFF4F779D)
                    "Hadith" -> androidx.compose.ui.graphics.Color(0xFF99593C)
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    onClick = { onOpenCourseDetail(course.id) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(course.category.take(1), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(course.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$percent% · ${course.totalLessons} lessons",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            onClick = {
                                store.incrementCourseProgress(course.id)
                                progress = progress + (course.id to store.courseProgress(course.id))
                            },
                            shape = CircleShape,
                            color = accent,
                        ) {
                            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                Text("▶", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
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
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromIndex = topics.indexOfFirst { it.id == from.key }
        val toIndex = topics.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            topics = topics.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
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
        gridState = gridState,
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
                ReorderableItem(reorderState, key = topic.id) { isDragging ->
                    TopicInterestRow(
                        topic = topic,
                        following = topic.id in followedTopicIds,
                        selected = expanded && topic.id == selectedTopicId,
                        isDragging = isDragging,
                        onOpen = {
                            if (expanded) selectedTopicId = topic.id else onOpenTopic(topic.id)
                        },
                        onToggle = { followed ->
                            store.setTopicFollowed(topic.id, followed)
                            followedTopicIds = if (followed) followedTopicIds + topic.id else followedTopicIds - topic.id
                        },
                        dragHandleModifier = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                store.saveTopicOrder(topics.map(SharedTopic::id))
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicInterestRow(
    topic: SharedTopic,
    following: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
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
        },
        colors = ListItemDefaults.colors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.surfaceContainerLow
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else -> Color.Transparent
            },
        ),
        modifier = Modifier
            // TopLevelScaffold already supplies 16 dp; Android's list supplies 24 dp.
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .then(dragHandleModifier)
            .graphicsLayer {
                val scale = if (isDragging) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
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
    val headerNews = (state as? SharedNewsState.Loaded)?.news?.firstOrNull()
    val rawHeaderType = headerNews?.type?.takeIf(String::isNotBlank) ?: "Invocations"
    val isHadith = rawHeaderType.contains("hadith", ignoreCase = true)
    val headerType = displayNewsType(rawHeaderType)
    ImmersiveDetailScaffold(onBack = onBack, header = {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            NewsHeaderArtwork(
                resourceName = headerNews?.let(::newsHeaderResource) ?: "masjid_al_haram",
                modifier = Modifier.fillMaxSize(),
            )
            ImmersiveDetailHeaderScrim(
                title = headerType,
                supportingText = headerNews?.source?.takeIf(String::isNotBlank)
                    ?: if (isHadith) "Prophetic narration" else "From the Noble Quran",
                arabicTitle = if (isHadith) "حديث" else "دعاء",
            )
            Column {
                DetailToolbar(
                    onBack = onBack,
                    inlineActions = listOf(bookmarkAct),
                    sheetActions = listOf(shareAct),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    toolbarTitle = headerType,
                )
            }
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
    AdaptiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = RoundedCornerShape(16.dp),
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
    AdaptiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = RoundedCornerShape(16.dp),
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
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    if (collapsible) {
                        Text(
                            if (expanded) "Hide" else "Show",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    content()
                }
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

@OptIn(ExperimentalAdaptiveApi::class)
@Composable
internal fun SharedDetailScaffold(
    title: String,
    onBack: () -> Unit,
    maxContentWidth: Dp = 720.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveSurface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
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

@OptIn(ExperimentalAdaptiveApi::class)
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
    gridState: LazyGridState? = null,
    expandedPane: (@Composable (Modifier) -> Unit)? = null,
    content: LazyGridScope.(expanded: Boolean) -> Unit,
) {
    AdaptiveSurface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxSize().background(screenCanvasBrush()),
    ) {
        val page: @Composable () -> Unit = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= EXPANDED_WIDTH
                // Match Android and the home screen: the side rail appears only in
                // landscape tablet windows; portrait keeps the bottom pill.
                val useSideRail = maxWidth >= 600.dp && maxHeight >= 600.dp && maxWidth > maxHeight
                val effectiveGridState = gridState ?: rememberLazyGridState()
                Column(
                    modifier = Modifier
                        .widthIn(max = if (expandedPane != null) 1200.dp else 1100.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(
                            start = if (useSideRail) 80.dp else 16.dp,
                            end = 16.dp,
                        )
                        // Match the home column's breathing room above the header so
                        // the pulled sync strip holds the search bar at the same
                        // clearance on every tab page.
                        .padding(top = 8.dp),
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
                            state = effectiveGridState,
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
                if (LocalShowBottomNavigation.current) {
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
                            onVoiceTap = searchController?.let { controller ->
                                { controller.openVoice() }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        // Pull-to-refresh on every tab page — the app-level container Android
        // gives all top-level destinations; pages without async data pass a
        // refresh that completes immediately so the gesture springs back.
        // The shared connectivity feed drives the persistent offline row.
        if (onRefresh != null) {
            val isOnline by com.starception.submission.shared.connectivity.appConnectivity.isOnline
                .collectAsState()
            PullToSyncContainer(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                syncResultText = syncResultText,
                isOffline = !isOnline,
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
@OptIn(ExperimentalAdaptiveApi::class)
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
        AdaptiveWidget(
            material = {
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
            },
            cupertino = {
                io.github.alexzhirkevich.cupertino.CupertinoSearchBarNative(
                    placeholder = "Search",
                    onSearchClick = searchController::open,
                    onVoiceClick = { searchController.openVoice() },
                    modifier = Modifier.weight(1f),
                )
            },
        )
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

@OptIn(ExperimentalAdaptiveApi::class)
@Composable
private fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    AdaptiveWidget(
        material = {
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
        },
        cupertino = {
            if (onBack != null) {
                CupertinoNavigateBackButton(
                    onClick = onBack,
                    modifier = Modifier.height(44.dp),
                ) {
                    CupertinoText(
                        title,
                        style = CupertinoTheme.typography.title3,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                CupertinoText(
                    title,
                    style = CupertinoTheme.typography.title2,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(44.dp),
                )
            }
        },
    )
}

@OptIn(ExperimentalAdaptiveApi::class)
@Composable
internal fun AdaptiveCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveWidget(
        material = {
            if (onClick != null) {
                androidx.compose.material3.Card(
                    onClick = onClick,
                    modifier = modifier,
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    content = content,
                )
            } else {
                androidx.compose.material3.Card(
                    modifier = modifier,
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    content = content,
                )
            }
        },
        cupertino = {
            val surfaceModifier = if (onClick != null) {
                modifier.clickable(onClick = onClick)
            } else {
                modifier
            }
            CupertinoSurface(
                modifier = surfaceModifier,
                shape = shape,
                color = CupertinoTheme.colorScheme.secondarySystemGroupedBackground,
            ) {
                Column(Modifier.fillMaxWidth(), content = content)
            }
        },
    )
}

@Composable
private fun SurahRow(
    surah: Surah,
    saved: Boolean,
    onClick: () -> Unit,
    onToggleSaved: (() -> Unit)? = null,
) {
    AdaptiveCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
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
    AdaptiveCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
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
    AdaptiveCard(
        shape = RoundedCornerShape(16.dp),
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
