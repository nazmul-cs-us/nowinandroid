package com.starception.submission.feature.hadith

import com.starception.submission.media.GlobalMediaViewModel
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.Hadith
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.core.translation.TranslationService
import com.starception.submission.feature.surah.QuranFonts
import com.starception.submission.util.toLocalizedDigits
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.designsystem.component.NiaVerifiedTag
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import com.starception.submission.core.ui.ImmersiveFullScreenEffect
import com.starception.submission.feature.course.CourseCompletionBadgeCompact
import com.starception.submission.feature.course.CourseProgressTracker
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.runtime.toMutableStateList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.starception.submission.R
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import com.starception.submission.voice.SherpaOnnxTtsService
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import com.starception.submission.voice.EnglishTtsTextNormalizer
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.settings.components.TtsVoiceSelectionSheet
import com.starception.submission.settings.components.isTtsVoiceModelAvailable
import com.starception.submission.download.AudioDownloadHelper
import com.starception.submission.download.AssetDownloadManager
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

private const val HADITH_SECTION_ORDER_PREFS = "hadith_section_order_prefs"
private const val HADITH_SECTION_ORDER_KEY = "section_order"

/**
 * Save hadith section order to SharedPreferences
 */
private fun saveHadithSectionOrder(context: android.content.Context, order: List<HadithSection>) {
    val prefs = context.getSharedPreferences(HADITH_SECTION_ORDER_PREFS, android.content.Context.MODE_PRIVATE)
    prefs.edit().putString(HADITH_SECTION_ORDER_KEY, order.joinToString(",") { it.name }).apply()
}

/**
 * Load hadith section order from SharedPreferences
 */
private fun loadHadithSectionOrder(context: android.content.Context): List<HadithSection>? {
    val prefs = context.getSharedPreferences(HADITH_SECTION_ORDER_PREFS, android.content.Context.MODE_PRIVATE)
    val orderString = prefs.getString(HADITH_SECTION_ORDER_KEY, null) ?: return null
    return try {
        orderString.split(",").mapNotNull { name ->
            try { HadithSection.valueOf(name) } catch (e: Exception) { null }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Hadith Detail Screen - displays a single hadith from a collection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithDetailScreen(
    collectionName: String,
    hadithNumber: Int,
    databaseFile: String,
    onBackClick: () -> Unit,
    onNavigateToPreviousHadith: () -> Unit = {},
    onNavigateToNextHadith: () -> Unit = {},
    initialAutoPlay: Boolean = false,
    initialAutoAdvance: Boolean = false,
    playbackRangeStart: Int? = null,
    playbackRangeEnd: Int? = null,
    modifier: Modifier = Modifier
) {
    // Capture the route-provided value, then shadow with mutable state so navigation
    // between hadiths (swipe or mini-bar prev/next) stays within this composable
    // instance — the same pattern SurahDetailScreen uses to keep the global mini-bar
    // visible across track changes instead of dismissing during a remount.
    val routeHadithNumber = hadithNumber
    @Suppress("NAME_SHADOWING")
    var hadithNumber by remember(routeHadithNumber) { mutableStateOf(routeHadithNumber) }
    // Enable immersive full-screen mode (hides status bar)
    // Don't restore on dispose to prevent status bar flash when swiping between hadiths
    ImmersiveFullScreenEffect(restoreOnDispose = false)

    // Create wrapped back click that restores status bar first
    val view = LocalView.current
    val wrappedOnBackClick: () -> Unit = {
        val window = (view.context as? android.app.Activity)?.window
        val insetsController = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view)
        }
        insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        onBackClick()
    }

    val context = LocalContext.current
    val repository = remember { HadithRepository.getInstance(context) }
    val translationService = remember { TranslationService.getInstance(context) }
    val bukhariTranslationRepo = remember { BukhariLocalTranslationRepository.getInstance(context) }

    // Get Sherpa-ONNX TTS service and download manager via Hilt entry point
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SherpaOnnxTtsEntryPoint::class.java
        )
    }
    val sherpaOnnxTts = remember { entryPoint.sherpaOnnxTtsService() }
    val downloadManager = remember { entryPoint.assetDownloadManager() }
    val audioDownloadHelper = remember { entryPoint.audioDownloadHelper() }
    val userDataRepository = remember { entryPoint.userDataRepository() }

    // Resolve to the real news-resource ID when this hadith exists in the feed,
    // so the shared Bookmarks screen can render it. Direct book playback still
    // has a deterministic fallback ID if no feed entry exists.
    var bookmarkId by remember(databaseFile, hadithNumber) {
        mutableStateOf("hadith-${databaseFile.removeSuffix(".db")}-$hadithNumber")
    }
    var isBookmarked by remember(databaseFile, hadithNumber) { mutableStateOf(false) }
    val bookmarkScope = rememberCoroutineScope()

    LaunchedEffect(databaseFile, hadithNumber) {
        val fallbackId = "hadith-${databaseFile.removeSuffix(".db")}-$hadithNumber"
        val hadithUrl = "hadith://${databaseFile.removeSuffix(".db")}/$hadithNumber"
        val resolvedId = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                NewsDatabase.getInstance(context).newsDao()
                    .getNewsIdByExactUrl(hadithUrl)
                    ?.toString()
            }.getOrNull()
        }
        bookmarkId = resolvedId ?: fallbackId
        isBookmarked = bookmarkId in userDataRepository.userData.first().bookmarkedNewsResources
    }

    // Load user's TTS preferences from SharedPreferences
    val ttsPrefs = remember {
        context.getSharedPreferences("tts_settings", android.content.Context.MODE_PRIVATE)
    }
    // Reactive so the voice-selection bottom sheet (toolbar ⋮) takes effect
    // immediately; every change is persisted to the same prefs Settings uses.
    var selectedVoiceName by remember {
        mutableStateOf(ttsPrefs.getString("selected_voice", TtsVoice.KOKORO_EN.name) ?: TtsVoice.KOKORO_EN.name)
    }
    var selectedSpeakerId by remember { mutableStateOf(ttsPrefs.getInt("selected_speaker_id", 0)) }
    val selectedVoice = remember(selectedVoiceName) {
        try {
            TtsVoice.valueOf(selectedVoiceName)
        } catch (e: Exception) {
            TtsVoice.KOKORO_EN
        }
    }

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var hadith by remember { mutableStateOf<Hadith?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDownloadPrompt by remember { mutableStateOf(false) }
    var downloadCategory by remember { mutableStateOf("") }
    var reloadTrigger by remember { mutableStateOf(0) }
    // Play tapped in English but the selected Sherpa TTS voice model isn't on
    // disk: show the standard missing-content download page for that voice.
    var showTtsModelDownload by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(showTtsModelDownload) { showTtsModelDownload = false }
    val selectedArabicFont by rememberHadithArabicFont(context)
    // Toolbar ⋮ opens reading settings; font and narration each have their own picker.
    var showReadingSettingsSheet by remember { mutableStateOf(false) }
    var showArabicFontSheet by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    val readingSettingsScope = rememberCoroutineScope()

    // Per-hadith cache so the AnimatedContent swipe transition can render the
    // exiting page with its original data while the new page slides in with its
    // own (preloaded) data — matching DuaDetailScreen's HorizontalPager feel.
    val hadithCache = remember(databaseFile) { androidx.compose.runtime.mutableStateMapOf<Int, Hadith>() }

    // Translation state
    var translatedArabic by remember { mutableStateOf<String?>(null) }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var translatedElaboration by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(translationService.getSelectedLanguage()) }
    var selectedProvider by remember { mutableStateOf(translationService.getSelectedProvider()) }
    var showTranslationSheet by remember { mutableStateOf(false) }

    // Available translations
    val availableTranslations = listOf("en", "ar", "bn", "zh", "es", "fr", "id", "ru", "sv", "tr", "ur")

    // Available providers
    val availableProviders = listOf(
        "auto" to "Auto (Reverso → Google)",
        "google" to "Google Translate",
        "reverso" to "Reverso"
    )

    // Audio playback state
    var isPlaying by remember { mutableStateOf(false) }
    // True when Sherpa/Android TTS renders the audio while
    // ChapterRecitationService supplies the system MediaSession notification.
    var isTtsBackedPlayback by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsInitialized by remember { mutableStateOf(false) }
    // Invalidates late TTS completion callbacks after stop or manual navigation.
    var playbackGeneration by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // When on, hitting the end of a hadith auto-advances to the next one.
    var autoAdvance by remember(initialAutoAdvance) { mutableStateOf(initialAutoAdvance) }
    // "Play all" is a continuous coroutine instead of a chain of recompositions.
    // That distinction matters when the display turns off: Compose rendering pauses,
    // but this coroutine and the foreground playback service keep advancing tracks.
    var bookPlaylistEnabled by remember(initialAutoPlay, initialAutoAdvance) {
        mutableStateOf(initialAutoPlay && initialAutoAdvance)
    }
    var isBookPlaylistPlayback by remember { mutableStateOf(false) }
    var isBookPlaylistPaused by remember { mutableStateOf(false) }
    var bookPlaylistJumpTarget by remember { mutableStateOf<Int?>(null) }

    // On-demand audio download state
    var isDownloadingAudio by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Audio handoff state — during mini-bar/swipe navigation we swap hadith inside
    // this composable instance (no remount). To prevent the mini-bar from briefly
    // dismissing during the audio swap, we suppress the "stopped" notification and
    // auto-resume playback on the new hadith.
    var prevHadithNumberRef by remember { mutableStateOf(hadithNumber) }
    var shouldAutoPlayAfterLoad by remember(initialAutoPlay, initialAutoAdvance, selectedLanguage) {
        mutableStateOf(initialAutoPlay && !initialAutoAdvance)
    }
    var suppressStopNotification by remember { mutableStateOf(false) }

    // Notify global media controller when hadith playback state OR hadith changes
    // (so the mini-bar title updates when navigating between hadiths while playing).
    androidx.compose.runtime.LaunchedEffect(isPlaying, hadithNumber) {
        if (!isPlaying && suppressStopNotification) {
            // Skip the (false) notification while a navigation handoff is in progress
            return@LaunchedEffect
        }
        suppressStopNotification = false
        val title = "Hadith #$hadithNumber"
        GlobalMediaViewModel.onHadithPlaybackChanged?.invoke(isPlaying, hadithNumber, collectionName, title)

        if (isPlaying) {
            while (true) {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    try {
                        GlobalMediaViewModel.onHadithProgressChanged?.invoke(
                            mp.currentPosition,
                            mp.duration,
                        )
                    } catch (_: Exception) { }
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    // When the user navigates between hadiths (mini-bar prev/next or swipe), tear
    // down the old audio silently, then auto-resume on the freshly loaded hadith.
    androidx.compose.runtime.LaunchedEffect(hadithNumber) {
        if (prevHadithNumberRef != hadithNumber) {
            if (isBookPlaylistPlayback) {
                prevHadithNumberRef = hadithNumber
                return@LaunchedEffect
            }
            playbackGeneration += 1
            // Auto-resume if the user was playing OR if continuous-play toggle is on.
            // Continuous-play covers the natural-end case where isPlaying is already
            // false by the time onCompletionListener bumps the hadith number.
            shouldAutoPlayAfterLoad = isPlaying || autoAdvance
            if (isPlaying || autoAdvance) suppressStopNotification = true
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            textToSpeech?.stop()
            sherpaOnnxTts.stopSpeaking()
            isPlaying = false
            prevHadithNumberRef = hadithNumber
        }
    }

    // Cleanup on dispose
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            // Sherpa TTS is app-scoped and keeps reading after this screen is
            // gone — keep the mini-bar alive in that case so the user can see
            // and stop the playback; only clear it when nothing is speaking.
            if (!sherpaOnnxTts.isSpeaking()) {
                GlobalMediaViewModel.onHadithPlaybackChanged?.invoke(false, hadithNumber, collectionName, "")
            }
        }
    }

    // Load hadith (reloadTrigger forces re-execution after CDN download completes)
    LaunchedEffect(databaseFile, hadithNumber, reloadTrigger) {
        isLoading = true
        error = null
        translatedText = null
        translatedElaboration = null
        try {
            val cached = hadithCache[hadithNumber]
            val current = cached ?: repository.getHadith(databaseFile, hadithNumber)
            if (current != null) {
                hadithCache[hadithNumber] = current
                hadith = current
            } else {
                hadith = null
                error = "Hadith not found"
            }
        } catch (e: Exception) {
            android.util.Log.e("HadithDetailScreen", "Error loading hadith", e)
            // Check if it's a database-not-found error (asset or file missing)
            val isDbMissing = e.message?.contains("Cannot copy asset", ignoreCase = true) == true ||
                e.message?.contains("Unable to copy database", ignoreCase = true) == true ||
                e.message?.contains("not found", ignoreCase = true) == true ||
                e.message?.contains("doesn't exist", ignoreCase = true) == true ||
                e.cause is java.io.FileNotFoundException ||
                e is IllegalStateException
            if (isDbMissing) {
                // Map database filename to CDN category
                val dbName = databaseFile.removeSuffix(".db").replace("_", " ")
                downloadCategory = "hadith_${databaseFile.removeSuffix(".db")}"
                showDownloadPrompt = true
                error = "Database not available"
            } else {
                error = e.message ?: "Error loading hadith"
            }
        }
        isLoading = false
    }

    // Preload neighbours so the swipe transition has correct per-page data instantly.
    LaunchedEffect(databaseFile, hadithNumber) {
        listOf(hadithNumber - 1, hadithNumber + 1)
            .filter { candidate ->
                candidate > 0 &&
                    candidate !in hadithCache &&
                    (playbackRangeStart == null || candidate >= playbackRangeStart) &&
                    (playbackRangeEnd == null || candidate <= playbackRangeEnd)
            }
            .forEach { num ->
                try {
                    val h = repository.getHadith(databaseFile, num)
                    if (h != null) hadithCache[num] = h
                } catch (_: Exception) {
                    // Neighbour preload failure is non-fatal — silent.
                }
            }
    }

    // Translate content when hadith is loaded or settings change
    // Uses local Bukhari translations for English, online API for other languages
    LaunchedEffect(hadith, selectedLanguage, selectedProvider) {
        val currentHadith = hadith ?: return@LaunchedEffect

        // Skip translation only for transliteration (no API support)
        if (selectedLanguage == "transliteration") {
            translatedArabic = null  // Show original Arabic
            translatedText = currentHadith.textPlain
            translatedElaboration = currentHadith.elaboration
            return@LaunchedEffect
        }

        // If Arabic is selected, show original Arabic text without translation
        if (selectedLanguage == "ar") {
            translatedArabic = null  // Show original Arabic
            translatedText = currentHadith.textPlain
            translatedElaboration = currentHadith.elaboration
            return@LaunchedEffect
        }

        isTranslating = true
        try {
            // Check if this is Bukhari - use local English as source for translations
            val isBukhari = databaseFile.contains("bukhari", ignoreCase = true)

            if (isBukhari) {
                // Load local Bukhari English translations
                bukhariTranslationRepo.loadTranslations()
                val localEnglish = bukhariTranslationRepo.getEnglishText(hadithNumber)

                if (localEnglish != null) {
                    if (selectedLanguage == "en") {
                        // English selected - show local English directly
                        translatedArabic = null
                        translatedText = localEnglish
                        translatedElaboration = currentHadith.elaboration
                        android.util.Log.d("HadithDetailScreen", "✅ Using local Bukhari English for hadith #$hadithNumber")
                    } else {
                        // Other language - translate FROM English TO target language
                        translatedArabic = null
                        translatedText = translationService.translateFromEnglish(localEnglish, selectedLanguage)
                        if (!currentHadith.elaboration.isNullOrEmpty()) {
                            translatedElaboration = translationService.translate(currentHadith.elaboration!!, selectedLanguage)
                        }
                        android.util.Log.d("HadithDetailScreen", "✅ Translated Bukhari from English to $selectedLanguage for hadith #$hadithNumber")
                    }
                } else {
                    // Fallback to online if local not found
                    android.util.Log.w("HadithDetailScreen", "⚠️ Local Bukhari translation not found for #$hadithNumber, using online")
                    if (!currentHadith.textPlain.isNullOrEmpty()) {
                        translatedText = translationService.translate(currentHadith.textPlain!!, selectedLanguage)
                    }
                }
            } else {
                // Use online translation service for other collections (translate from Arabic)
                // Translate Arabic hadith text (main hadith)
                if (!currentHadith.textArabic.isNullOrEmpty()) {
                    translatedArabic = translationService.translate(currentHadith.textArabic!!, selectedLanguage)
                    android.util.Log.d("HadithDetailScreen", "Translated textArabic to $selectedLanguage")
                } else {
                    translatedArabic = null
                }

                // Translate textPlain (Translation section)
                if (!currentHadith.textPlain.isNullOrEmpty()) {
                    translatedText = translationService.translate(currentHadith.textPlain!!, selectedLanguage)
                    android.util.Log.d("HadithDetailScreen", "Translated textPlain to $selectedLanguage")
                } else {
                    translatedText = null
                }

                // Translate elaboration (Explanation section)
                if (!currentHadith.elaboration.isNullOrEmpty()) {
                    translatedElaboration = translationService.translate(currentHadith.elaboration!!, selectedLanguage)
                    android.util.Log.d("HadithDetailScreen", "Translated elaboration to $selectedLanguage")
                } else {
                    translatedElaboration = null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HadithDetailScreen", "Translation error", e)
            // Fall back to original text
            translatedArabic = null
            translatedText = currentHadith.textPlain
            translatedElaboration = currentHadith.elaboration
        }
        isTranslating = false
    }

    // In-place navigation handlers — update internal hadithNumber state instead of
    // popping/pushing the back stack, so the composable stays mounted and the mini-bar
    // doesn't get a dispose/remount cycle.
    val handleSkipNext: () -> Unit = {
        if (isBookPlaylistPlayback) {
            val rangeEnd = playbackRangeEnd
            if (rangeEnd != null && hadithNumber < rangeEnd) {
                bookPlaylistJumpTarget = hadithNumber + 1
                isBookPlaylistPaused = false
                sherpaOnnxTts.stopSpeaking()
            }
        } else if (playbackRangeEnd == null || hadithNumber < playbackRangeEnd) {
            hadithNumber += 1
        }
    }
    val handleSkipPrev: () -> Unit = {
        if (isBookPlaylistPlayback) {
            val firstAllowed = playbackRangeStart ?: 1
            if (hadithNumber > firstAllowed) {
                bookPlaylistJumpTarget = hadithNumber - 1
                isBookPlaylistPaused = false
                sherpaOnnxTts.stopSpeaking()
            }
        } else {
            val firstAllowed = playbackRangeStart ?: 1
            if (hadithNumber > firstAllowed) hadithNumber -= 1
        }
    }
    val currentAutoAdvance by androidx.compose.runtime.rememberUpdatedState(autoAdvance)
    val handlePlaybackCompleted: () -> Unit = {
        val shouldAdvance = currentAutoAdvance &&
            (playbackRangeEnd == null || hadithNumber < playbackRangeEnd)
        if (shouldAdvance) {
            // Keep the mini-player visible while the next hadith is loading.
            suppressStopNotification = true
            shouldAutoPlayAfterLoad = true
        }
        if (isTtsBackedPlayback) {
            com.starception.submission.services.ChapterRecitationService.stop(context)
            isTtsBackedPlayback = false
        }
        isPlaying = false
        if (shouldAdvance) {
            android.util.Log.d(
                "HadithAutoAdvance",
                "Hadith #$hadithNumber completed; advancing to #${hadithNumber + 1}",
            )
            handleSkipNext()
        }
    }
    val currentPlaybackCompleted by androidx.compose.runtime.rememberUpdatedState(
        handlePlaybackCompleted,
    )

    // Downloaded Bengali recordings play in the foreground recitation service,
    // so their natural-completion event must be bridged back to this screen.
    androidx.compose.runtime.DisposableEffect(Unit) {
        val completionCallback: () -> Unit = { currentPlaybackCompleted() }
        com.starception.submission.services.ChapterRecitationState.onHadithCompletion =
            completionCallback
        onDispose {
            if (
                com.starception.submission.services.ChapterRecitationState.onHadithCompletion ===
                completionCallback
            ) {
                com.starception.submission.services.ChapterRecitationState.onHadithCompletion = null
            }
        }
    }

    // Keep book playback independent from recomposition. The former implementation
    // finished one hadith, changed Compose state, and waited for a LaunchedEffect to
    // start the next. With the display off that handoff can be deferred indefinitely.
    // This loop awaits each TTS item directly and therefore advances in the background.
    androidx.compose.runtime.LaunchedEffect(
        bookPlaylistEnabled,
        playbackRangeStart,
        playbackRangeEnd,
        selectedLanguage,
        selectedVoice,
        selectedSpeakerId,
    ) {
        if (!bookPlaylistEnabled) return@LaunchedEffect
        val rangeStart = playbackRangeStart ?: return@LaunchedEffect
        val rangeEnd = playbackRangeEnd ?: return@LaunchedEffect
        // Every Bukhari item now begins with its numbered English Sherpa intro, including
        // Bengali recordings, so the selected voice model is required for Play All.
        if (!isTtsVoiceModelAvailable(context, selectedVoice)) {
            bookPlaylistEnabled = false
            showTtsModelDownload = true
            return@LaunchedEffect
        }

        isBookPlaylistPlayback = true
        isBookPlaylistPaused = false
        bookPlaylistJumpTarget = null
        shouldAutoPlayAfterLoad = false
        bukhariTranslationRepo.loadTranslations()
        sherpaOnnxTts.setVoice(selectedVoice)

        try {
            var playlistNumber = rangeStart
            while (playlistNumber <= rangeEnd) {
                if (!bookPlaylistEnabled) break
                val number = playlistNumber
                val nextHadith = repository.getHadith(databaseFile, number)
                if (nextHadith == null) {
                    playlistNumber += 1
                    continue
                }
                val englishText = bukhariTranslationRepo.getEnglishText(number)
                    ?: nextHadith.textPlain
                if (englishText == null) {
                    playlistNumber += 1
                    continue
                }
                val spokenText = if (selectedLanguage == "en") {
                    englishText
                } else {
                    runCatching {
                        translationService.translateFromEnglish(englishText, selectedLanguage)
                    }.getOrNull() ?: englishText
                }

                playbackGeneration += 1
                prevHadithNumberRef = number
                hadithNumber = number
                hadithCache[number] = nextHadith
                hadith = nextHadith
                translatedText = spokenText
                isLoading = false
                isPlaying = true
                isTtsBackedPlayback = true
                com.starception.submission.services.ChapterRecitationService
                    .showExternalPlayback(
                        context = context,
                        title = "Hadith #$number",
                        subtitle = "Sahih Bukhari",
                    )

                // Once this hadith starts playing, Sherpa's native engine is idle. Use
                // that playback window to prepare the next English fallback as one clip,
                // so Play All normally pays the preparation cost only for the first item.
                val nextSherpaText = if (number < rangeEnd) {
                    val nextNumber = number + 1
                    val nextHasBengaliRecording = selectedLanguage == "bn" &&
                        audioDownloadHelper.resolveHadithAudioFile(nextNumber) != null
                    if (nextHasBengaliRecording) {
                        EnglishTtsTextNormalizer.bukhariIntro(nextNumber)
                    } else {
                        val nextEnglishText = bukhariTranslationRepo.getEnglishText(nextNumber)
                            ?: repository.getHadith(databaseFile, nextNumber)?.textPlain
                        nextEnglishText?.let {
                            "${EnglishTtsTextNormalizer.bukhariIntro(nextNumber)} $it"
                        }
                    }
                } else {
                    null
                }

                val preGenerateNext: () -> Unit = {
                    nextSherpaText?.let { nextText ->
                        sherpaOnnxTts.preGenerateAsync(
                            text = nextText,
                            speakerId = selectedSpeakerId,
                        )
                    }
                }

                suspend fun playExactEnglishWithSherpa(): Boolean {
                    if (!isTtsVoiceModelAvailable(context, selectedVoice)) {
                        bookPlaylistEnabled = false
                        showTtsModelDownload = true
                        return false
                    }
                    sherpaOnnxTts.setVoice(selectedVoice)
                    return sherpaOnnxTts.speakCachedOrGenerate(
                        text = "${EnglishTtsTextNormalizer.bukhariIntro(number)} $englishText",
                        speakerId = selectedSpeakerId,
                        onPlaybackStart = preGenerateNext,
                    )
                }

                suspend fun playNumberIntroWithSherpa(): Boolean =
                    sherpaOnnxTts.speakCachedOrGenerate(
                        text = EnglishTtsTextNormalizer.bukhariIntro(number),
                        speakerId = selectedSpeakerId,
                        onPlaybackStart = preGenerateNext,
                    )

                val completed = if (selectedLanguage == "bn") {
                    var audioFile = audioDownloadHelper.resolveHadithAudioFile(number)
                    if (audioFile == null) {
                        val cdnKey = audioDownloadHelper.getHadithCdnKey(number)
                        isDownloadingAudio = true
                        audioFile = when (audioDownloadHelper.downloadAudio(cdnKey)) {
                            is AssetDownloadManager.DownloadState.Completed ->
                                audioDownloadHelper.resolveHadithAudioFile(number)
                            else -> null
                        }
                        isDownloadingAudio = false
                    }
                    if (audioFile != null) {
                        val introCompleted = playNumberIntroWithSherpa()
                        val recordingCompleted = introCompleted && bookPlaylistEnabled &&
                            playHadithRecordingAndAwait(
                                context = context,
                                source = audioFile.absolutePath,
                                hadithNumber = number,
                            )
                        if (recordingCompleted) {
                            true
                        } else if (bookPlaylistEnabled) {
                            playExactEnglishWithSherpa()
                        } else {
                            false
                        }
                    } else {
                        playExactEnglishWithSherpa()
                    }
                } else {
                    playExactEnglishWithSherpa()
                }
                val requestedHadith = bookPlaylistJumpTarget
                if (requestedHadith != null) {
                    bookPlaylistJumpTarget = null
                    playlistNumber = requestedHadith
                    continue
                }
                if (!completed || !bookPlaylistEnabled) break
                playlistNumber += 1
            }
        } finally {
            isBookPlaylistPlayback = false
            isBookPlaylistPaused = false
            bookPlaylistJumpTarget = null
            isTtsBackedPlayback = false
            isPlaying = false
            com.starception.submission.services.ChapterRecitationService.stop(context)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,  // Solid background to prevent sky showing through
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // No padding for status bar in immersive mode
    ) { _ ->
        HadithSwipeContainer(
            hadithNumber = hadithNumber,
            onNavigateToPreviousHadith = handleSkipPrev,
            onNavigateToNextHadith = handleSkipNext
        ) {
            when {
                // Play tapped in English with the selected TTS voice model missing —
                // same missing-content layout as the hadith-DB download prompt below.
                showTtsModelDownload -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            com.starception.submission.download.MissingContentCard(
                                resourceName = "${selectedVoice.displayName} Voice",
                                category = ttsVoiceDownloadCategory(selectedVoice),
                                description = "The ${selectedVoice.displayName} text-to-speech voice needs to be downloaded to read this hadith aloud.",
                                downloadManager = downloadManager,
                                onDownloadComplete = {
                                    showTtsModelDownload = false
                                    android.widget.Toast.makeText(
                                        context,
                                        "Voice ready — tap play to listen",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                },
                            )
                        }
                        // Back button
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            IconButton(onClick = { showTtsModelDownload = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                // Show shimmer only on first load (no hadith yet). During swipe/mini-bar
                // navigation, keep the previous hadith visible while the new one loads.
                isLoading && hadith == null -> {
                    HadithShimmerLoading(onBackClick = wrappedOnBackClick, isLandscape = isLandscape)
                }
                error != null && showDownloadPrompt -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            com.starception.submission.download.MissingContentCard(
                                resourceName = "$collectionName Hadith Collection",
                                category = downloadCategory,
                                description = "This hadith collection database needs to be downloaded.",
                                downloadManager = downloadManager,
                                onDownloadComplete = {
                                    // Clear cached broken DB instance and Room's corrupted DB file
                                    com.starception.submission.core.hadithdatabase.HadithDatabase.clearInstance(context, databaseFile)
                                    android.util.Log.i("HadithDetailScreen", "Download complete, cleared DB cache for: $databaseFile")
                                    showDownloadPrompt = false
                                    error = null
                                    isLoading = true
                                    hadith = null
                                    // Increment trigger to force LaunchedEffect re-execution
                                    reloadTrigger++
                                },
                            )
                        }
                        // Back button
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            IconButton(onClick = wrappedOnBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                error != null -> {
                    HadithErrorContent(
                        error = error!!,
                        onBackClick = wrappedOnBackClick
                    )
                }
                hadith != null -> {
                    val updateTtsPlaybackState: (Boolean) -> Unit = { playing ->
                        isPlaying = playing
                        if (playing) {
                            isTtsBackedPlayback = true
                            com.starception.submission.services.ChapterRecitationService
                                .showExternalPlayback(
                                    context = context,
                                    title = "Hadith #$hadithNumber",
                                    subtitle = "Sahih Bukhari",
                                )
                        } else if (isTtsBackedPlayback) {
                            com.starception.submission.services.ChapterRecitationService.stop(context)
                            isTtsBackedPlayback = false
                        }
                    }
                    val playExactEnglishBukhariWithSherpa:
                        (onPlaybackCompleted: () -> Unit) -> Unit = { onPlaybackCompleted ->
                            val englishText = bukhariTranslationRepo.getEnglishText(hadithNumber)
                                ?: hadith?.textPlain
                                ?: hadith?.elaboration
                                ?: ""
                            if (!isTtsVoiceModelAvailable(context, selectedVoice)) {
                                android.util.Log.i(
                                    "HadithDetailScreen",
                                    "${selectedVoice.displayName} model missing for English Bukhari fallback",
                                )
                                isPlaying = false
                                showTtsModelDownload = true
                            } else if (englishText.isBlank()) {
                                android.util.Log.e(
                                    "HadithDetailScreen",
                                    "No English text available for Bukhari hadith #$hadithNumber",
                                )
                                isPlaying = false
                            } else {
                                android.util.Log.i(
                                    "HadithDetailScreen",
                                    "Playing exact English Bukhari fallback with ${selectedVoice.displayName}",
                                )
                                playWithSherpaOnnxTts(
                                    sherpaOnnxTts = sherpaOnnxTts,
                                    text = englishText,
                                    hadithNumber = hadithNumber,
                                    selectedVoice = selectedVoice,
                                    speakerId = selectedSpeakerId,
                                    onPlayingChanged = updateTtsPlaybackState,
                                    onPlaybackCompleted = onPlaybackCompleted,
                                )
                            }
                        }
                    val handlePlayClick: () -> Unit = {
                            if (isBookPlaylistPlayback) {
                                // MediaSession owns pause/resume so the same behavior is used
                                // by the in-app button, lock screen, Bluetooth and Android Auto.
                                com.starception.submission.services.ChapterRecitationService
                                    .toggle(context)
                            } else if (isPlaying) {
                                playbackGeneration += 1
                                // Stop playback (local player, TTS, and the recitation service)
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                                textToSpeech?.stop()
                                sherpaOnnxTts.stopSpeaking()
                                com.starception.submission.services.ChapterRecitationService.stop(context)
                                isTtsBackedPlayback = false
                                isPlaying = false
                            } else {
                                // Start playback
                                playbackGeneration += 1
                                val playbackId = playbackGeneration
                                val completeCurrentPlayback: () -> Unit = {
                                    if (playbackGeneration == playbackId) {
                                        handlePlaybackCompleted()
                                    }
                                }
                                val isBukhari = databaseFile.contains("bukhari", ignoreCase = true)
                                val playBengaliRecordingWithIntro: (source: String) -> Unit =
                                    playRecording@{ source ->
                                        val recordingHadithNumber = hadithNumber
                                        if (!isTtsVoiceModelAvailable(context, selectedVoice)) {
                                            showTtsModelDownload = true
                                            isPlaying = false
                                            return@playRecording
                                        }
                                        CoroutineScope(Dispatchers.Main).launch {
                                            isPlaying = true
                                            isTtsBackedPlayback = true
                                            com.starception.submission.services.ChapterRecitationService
                                                .showExternalPlayback(
                                                    context = context,
                                                    title = "Hadith #$recordingHadithNumber",
                                                    subtitle = "Sahih Bukhari",
                                                )
                                            sherpaOnnxTts.setVoice(selectedVoice)
                                            val introCompleted = sherpaOnnxTts.speakCachedOrGenerate(
                                                text = EnglishTtsTextNormalizer.bukhariIntro(
                                                    recordingHadithNumber,
                                                ),
                                                speakerId = selectedSpeakerId,
                                            )
                                            if (
                                                introCompleted &&
                                                playbackGeneration == playbackId
                                            ) {
                                                isTtsBackedPlayback = false
                                                try {
                                                    com.starception.submission.services.ChapterRecitationService.play(
                                                        context = context,
                                                        source = source,
                                                        title = "Hadith #$recordingHadithNumber",
                                                        subtitle = "Sahih Bukhari",
                                                    )
                                                    android.util.Log.i(
                                                        "HadithDetailScreen",
                                                        "Numbered English intro completed; playing Bengali recording: $source",
                                                    )
                                                } catch (e: Exception) {
                                                    android.util.Log.e(
                                                        "HadithDetailScreen",
                                                        "Unable to play Bengali recording after intro",
                                                        e,
                                                    )
                                                    playExactEnglishBukhariWithSherpa(
                                                        completeCurrentPlayback,
                                                    )
                                                }
                                            } else if (playbackGeneration == playbackId) {
                                                isTtsBackedPlayback = false
                                                isPlaying = false
                                                com.starception.submission.services.ChapterRecitationService
                                                    .stop(context)
                                            }
                                        }
                                    }

                                // Bengali first uses its matching recording. If that exact
                                // track is unavailable, Bukhari always falls back to the
                                // exact English entry in the user's selected Sherpa voice.
                                if (selectedLanguage == "bn" && isBukhari) {
                                    // Resolve audio: check cdn_assets first, then SD card
                                    val audioFile = audioDownloadHelper.resolveHadithAudioFile(hadithNumber)

                                    if (audioFile != null) {
                                        playBengaliRecordingWithIntro(audioFile.absolutePath)
                                    } else {
                                        // File not available locally - attempt on-demand download
                                        val cdnKey = audioDownloadHelper.getHadithCdnKey(hadithNumber)
                                        android.util.Log.i("HadithDetailScreen", "Bengali audio not found for hadith #$hadithNumber, downloading: $cdnKey")
                                        isDownloadingAudio = true
                                        downloadProgress = 0f

                                        CoroutineScope(Dispatchers.Main).launch {
                                            try {
                                                // Collect download progress in background
                                                val progressJob = launch {
                                                    audioDownloadHelper.getDownloadProgress(cdnKey).collect { state ->
                                                        when (state) {
                                                            is AssetDownloadManager.DownloadState.Downloading -> {
                                                                downloadProgress = state.progress
                                                            }
                                                            is AssetDownloadManager.DownloadState.Completed -> {
                                                                downloadProgress = 1f
                                                            }
                                                            else -> {}
                                                        }
                                                    }
                                                }

                                                val result = audioDownloadHelper.downloadAudio(cdnKey)
                                                progressJob.cancel()
                                                isDownloadingAudio = false
                                                downloadProgress = 0f

                                                when (result) {
                                                    is AssetDownloadManager.DownloadState.Completed -> {
                                                        // Download successful - resolve and play
                                                        val downloadedFile = audioDownloadHelper.resolveHadithAudioFile(hadithNumber)
                                                        if (downloadedFile != null) {
                                                            playBengaliRecordingWithIntro(
                                                                downloadedFile.absolutePath,
                                                            )
                                                        } else {
                                                            playExactEnglishBukhariWithSherpa(
                                                                completeCurrentPlayback,
                                                            )
                                                        }
                                                    }
                                                    is AssetDownloadManager.DownloadState.Failed -> {
                                                        android.util.Log.w("HadithDetailScreen", "Download failed: ${result.error}, using English Sherpa fallback")
                                                        playExactEnglishBukhariWithSherpa(
                                                            completeCurrentPlayback,
                                                        )
                                                    }
                                                    else -> {
                                                        playExactEnglishBukhariWithSherpa(
                                                            completeCurrentPlayback,
                                                        )
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("HadithDetailScreen", "Download error", e)
                                                isDownloadingAudio = false
                                                downloadProgress = 0f
                                                playExactEnglishBukhariWithSherpa(
                                                    completeCurrentPlayback,
                                                )
                                            }
                                        }
                                    }
                                } else if (isBukhari) {
                                    // Bukhari has no matching recording for this selected
                                    // language. Do not route it through a system TTS voice.
                                    playExactEnglishBukhariWithSherpa(
                                        completeCurrentPlayback,
                                    )
                                } else {
                                    // Use Sherpa-ONNX TTS (user-selected voice) for English
                                    // For non-English languages, fall back to Android TTS
                                    val textToSpeak = translatedText ?: hadith!!.textPlain ?: ""
                                    if (selectedLanguage == "en") {
                                        if (!isTtsVoiceModelAvailable(context, selectedVoice)) {
                                            // Selected voice model not downloaded — show the
                                            // asset download page instead of failing silently.
                                            android.util.Log.i("HadithDetailScreen", "🔊 ${selectedVoice.displayName} model missing — showing download page")
                                            showTtsModelDownload = true
                                        } else {
                                            android.util.Log.i("HadithDetailScreen", "🔊 Using Sherpa-ONNX TTS with ${selectedVoice.displayName}, speaker $selectedSpeakerId")
                                            playWithSherpaOnnxTts(
                                                sherpaOnnxTts = sherpaOnnxTts,
                                                text = textToSpeak,
                                                hadithNumber = hadithNumber,
                                                selectedVoice = selectedVoice,
                                                speakerId = selectedSpeakerId,
                                                onPlayingChanged = updateTtsPlaybackState,
                                                onPlaybackCompleted = completeCurrentPlayback,
                                            )
                                        }
                                    } else {
                                        // Non-English languages use Android TTS (has more language support)
                                        android.util.Log.i("HadithDetailScreen", "🔊 Using Android TTS for $selectedLanguage")
                                        playWithTts(
                                            context = context,
                                            text = textToSpeak,
                                            language = selectedLanguage,
                                            tts = textToSpeech,
                                            onTtsCreated = { textToSpeech = it; isTtsInitialized = true },
                                            onPlayingChanged = updateTtsPlaybackState,
                                            onPlaybackCompleted = completeCurrentPlayback,
                                        )
                                    }
                                }
                            }
                        }

                    val currentHandlePlayClick by androidx.compose.runtime.rememberUpdatedState(handlePlayClick)
                    val currentSkipNext by androidx.compose.runtime.rememberUpdatedState(handleSkipNext)
                    val currentSkipPrev by androidx.compose.runtime.rememberUpdatedState(handleSkipPrev)

                    // Auto-resume playback on the freshly loaded hadith when navigation
                    // was triggered while audio was playing (mini-bar prev/next or swipe).
                    androidx.compose.runtime.LaunchedEffect(hadith) {
                        if (shouldAutoPlayAfterLoad && !isPlaying && !isBookPlaylistPlayback) {
                            shouldAutoPlayAfterLoad = false
                            kotlinx.coroutines.delay(50)
                            handlePlayClick()
                        }
                    }
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        // Capture the exact lambdas we install so we can check identity on dispose
                        // and avoid clobbering a successor screen that mounted before we tore down.
                        val playCb: () -> Unit = {
                            android.util.Log.d("HadithMiniBar", "🎯 PLAY/PAUSE invoked | hadith=$hadithNumber | currentlyPlaying=$isPlaying")
                            currentHandlePlayClick()
                        }
                        val nextCb: () -> Unit = {
                            android.util.Log.d("HadithMiniBar", "🎯 SKIP_NEXT invoked | hadith=$hadithNumber → ${hadithNumber + 1}")
                            currentSkipNext()
                        }
                        val prevCb: () -> Unit = {
                            android.util.Log.d("HadithMiniBar", "🎯 SKIP_PREV invoked | hadith=$hadithNumber → ${hadithNumber - 1}")
                            currentSkipPrev()
                        }
                        val pauseCb: () -> Unit = {
                            if (isBookPlaylistPlayback && !isBookPlaylistPaused) {
                                isBookPlaylistPaused = true
                                sherpaOnnxTts.pauseSpeaking()
                                isPlaying = false
                            } else if (!isBookPlaylistPlayback && isPlaying) {
                                currentHandlePlayClick()
                            }
                        }
                        val resumeCb: () -> Unit = {
                            if (isBookPlaylistPlayback && isBookPlaylistPaused) {
                                isBookPlaylistPaused = false
                                sherpaOnnxTts.resumeSpeaking()
                                isPlaying = true
                            } else if (!isBookPlaylistPlayback && !isPlaying) {
                                currentHandlePlayClick()
                            }
                        }
                        val carNextCb: () -> Boolean = {
                            val canSkip = isBookPlaylistPlayback &&
                                playbackRangeEnd?.let { hadithNumber < it } == true
                            if (canSkip) currentSkipNext()
                            canSkip
                        }
                        val carPrevCb: () -> Boolean = {
                            val canSkip = isBookPlaylistPlayback &&
                                hadithNumber > (playbackRangeStart ?: 1)
                            if (canSkip) currentSkipPrev()
                            canSkip
                        }
                        android.util.Log.d("HadithMiniBar", "📌 REGISTER | hadith=$hadithNumber | callbacks installed")
                        GlobalMediaViewModel.onHadithPlayPauseRequested = playCb
                        GlobalMediaViewModel.onHadithSkipNextRequested = nextCb
                        GlobalMediaViewModel.onHadithSkipPreviousRequested = prevCb
                        com.starception.submission.services.ChapterRecitationState.onExternalToggle = playCb
                        com.starception.submission.services.ChapterRecitationState.onExternalPause = pauseCb
                        com.starception.submission.services.ChapterRecitationState.onExternalPlay = resumeCb
                        com.starception.submission.services.ChapterRecitationState.onSkipNext = carNextCb
                        com.starception.submission.services.ChapterRecitationState.onSkipPrevious = carPrevCb
                        onDispose {
                            android.util.Log.d("HadithMiniBar", "🧹 UNREGISTER | hadith=$hadithNumber | clearing only own callbacks")
                            if (GlobalMediaViewModel.onHadithPlayPauseRequested === playCb) {
                                GlobalMediaViewModel.onHadithPlayPauseRequested = null
                            }
                            if (GlobalMediaViewModel.onHadithSkipNextRequested === nextCb) {
                                GlobalMediaViewModel.onHadithSkipNextRequested = null
                            }
                            if (GlobalMediaViewModel.onHadithSkipPreviousRequested === prevCb) {
                                GlobalMediaViewModel.onHadithSkipPreviousRequested = null
                            }
                            if (
                                com.starception.submission.services.ChapterRecitationState
                                    .onExternalToggle === playCb
                            ) {
                                com.starception.submission.services.ChapterRecitationState
                                    .onExternalToggle = null
                            }
                            if (
                                com.starception.submission.services.ChapterRecitationState
                                    .onExternalPause === pauseCb
                            ) {
                                com.starception.submission.services.ChapterRecitationState
                                    .onExternalPause = null
                            }
                            if (
                                com.starception.submission.services.ChapterRecitationState
                                    .onExternalPlay === resumeCb
                            ) {
                                com.starception.submission.services.ChapterRecitationState
                                    .onExternalPlay = null
                            }
                            if (
                                com.starception.submission.services.ChapterRecitationState
                                    .onSkipNext === carNextCb
                            ) {
                                com.starception.submission.services.ChapterRecitationState
                                    .onSkipNext = null
                            }
                            if (
                                com.starception.submission.services.ChapterRecitationState
                                    .onSkipPrevious === carPrevCb
                            ) {
                                com.starception.submission.services.ChapterRecitationState
                                    .onSkipPrevious = null
                            }
                        }
                    }

                    // Horizontal slide between hadiths to match DuaDetailScreen's
                    // HorizontalPager swipe feel. Each pane reads its hadith from the
                    // cache so the exiting page keeps its original data and the entering
                    // page shows the new hadith immediately (preloaded as a neighbour).
                    androidx.compose.animation.AnimatedContent(
                        targetState = hadithNumber,
                        transitionSpec = {
                            val direction = if (targetState > initialState) {
                                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            slideIntoContainer(direction, animationSpec = tween(350, easing = FastOutSlowInEasing)) togetherWith
                                slideOutOfContainer(direction, animationSpec = tween(350, easing = FastOutSlowInEasing))
                        },
                        label = "hadithPageSwipe",
                        modifier = Modifier.fillMaxSize(),
                    ) { num ->
                        val pageHadith = hadithCache[num] ?: hadith
                        if (pageHadith != null) {
                            HadithContent(
                                hadith = pageHadith,
                                collectionName = collectionName,
                                hadithNumber = num,
                                databaseFile = databaseFile,
                                onBackClick = wrappedOnBackClick,
                                translatedArabic = if (num == hadithNumber) translatedArabic else null,
                                translatedText = if (num == hadithNumber) translatedText else null,
                                translatedElaboration = if (num == hadithNumber) translatedElaboration else null,
                                isTranslating = if (num == hadithNumber) isTranslating else false,
                                selectedLanguage = selectedLanguage,
                                isBookmarked = isBookmarked,
                                onBookmarkClick = {
                                    val newState = !isBookmarked
                                    isBookmarked = newState
                                    bookmarkScope.launch {
                                        userDataRepository.setNewsResourceBookmarked(
                                            bookmarkId,
                                            newState,
                                        )
                                    }
                                },
                                onMoreClick = { showReadingSettingsSheet = true },
                                arabicFontFamily = hadithArabicFontFamily(selectedArabicFont),
                                isLandscape = isLandscape,
                                isPlaying = if (num == hadithNumber) isPlaying else false,
                                onPlayClick = handlePlayClick,
                                autoAdvance = autoAdvance,
                                onToggleAutoAdvance = { autoAdvance = !autoAdvance },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReadingSettingsSheet) {
        HadithReadingSettingsSheet(
            selectedFont = selectedArabicFont,
            selectedVoice = selectedVoice.displayName,
            selectedTranslation = getLanguageName(selectedLanguage),
            onTranslationClick = {
                showReadingSettingsSheet = false
                readingSettingsScope.launch {
                    kotlinx.coroutines.delay(300)
                    showTranslationSheet = true
                }
            },
            onFontClick = {
                showReadingSettingsSheet = false
                // Let the first modal finish leaving before presenting the next one.
                // Showing two ModalBottomSheets in the same frame is unreliable on
                // Samsung/Android 16 and can leave both sheets dismissed.
                readingSettingsScope.launch {
                    kotlinx.coroutines.delay(300)
                    showArabicFontSheet = true
                }
            },
            onVoiceClick = {
                showReadingSettingsSheet = false
                readingSettingsScope.launch {
                    kotlinx.coroutines.delay(300)
                    showVoiceSheet = true
                }
            },
            onDismiss = { showReadingSettingsSheet = false },
        )
    }

    if (showArabicFontSheet) {
        HadithArabicFontSheet(
            selectedFont = selectedArabicFont,
            onFontSelected = { font -> saveHadithArabicFont(context, font) },
            onDismiss = { showArabicFontSheet = false },
        )
    }

    if (showTranslationSheet) {
        TranslationSettingsSheet(
            selectedProvider = selectedProvider,
            selectedLanguage = selectedLanguage,
            availableProviders = availableProviders,
            availableTranslations = availableTranslations,
            onProviderSelected = { providerCode ->
                selectedProvider = providerCode
                translationService.setSelectedProvider(providerCode)
                translationService.clearCache()
                translatedText = null
                translatedElaboration = null
            },
            onLanguageSelected = { languageCode ->
                if (languageCode != selectedLanguage) {
                    translationService.clearCache()
                    translatedText = null
                    translatedElaboration = null
                    selectedLanguage = languageCode
                    translationService.setSelectedLanguage(languageCode)
                }
            },
            onDismiss = { showTranslationSheet = false },
        )
    }

    // Voice-selection bottom sheet (toolbar ⋮) — same slide-up minimal style
    // as the Surah page's options sheet. Persists to the prefs Settings uses.
    if (showVoiceSheet) {
        TtsVoiceSelectionSheet(
            selectedVoice = selectedVoice,
            selectedSpeakerId = selectedSpeakerId,
            supportingText = "Used when reading this hadith aloud in English",
            ttsService = sherpaOnnxTts,
            isVoiceAvailable = { isTtsVoiceModelAvailable(context, it) },
            onVoiceSelected = { voice ->
                if (voice.name != selectedVoiceName) {
                    selectedVoiceName = voice.name
                    selectedSpeakerId = 0
                    ttsPrefs.edit()
                        .putString("selected_voice", voice.name)
                        .putInt("selected_speaker_id", 0)
                        .apply()
                    // Cached audio was generated with the previous voice — the
                    // cache key is text-only, so without this the old voice keeps
                    // playing and the change appears not to stick.
                    sherpaOnnxTts.clearCache()
                }
            },
            onSpeakerChanged = { speakerId ->
                selectedSpeakerId = speakerId
                ttsPrefs.edit().putInt("selected_speaker_id", speakerId).apply()
                // Same reason as voice change: drop text-keyed cache entries
                // generated with the previous speaker.
                sherpaOnnxTts.clearCache()
            },
            onDismiss = { showVoiceSheet = false },
        )
    }
}

@Composable
private fun HadithContent(
    hadith: Hadith,
    collectionName: String,
    hadithNumber: Int,
    databaseFile: String,
    onBackClick: () -> Unit,
    translatedArabic: String? = null,
    translatedText: String? = null,
    translatedElaboration: String? = null,
    isTranslating: Boolean = false,
    selectedLanguage: String = "en",
    isBookmarked: Boolean = false,
    onBookmarkClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    arabicFontFamily: FontFamily = QuranFonts.PDMSSaleem,
    isLandscape: Boolean = false,
    isPlaying: Boolean = false,
    onPlayClick: () -> Unit = {},
    autoAdvance: Boolean = false,
    onToggleAutoAdvance: () -> Unit = {},
) {
    val context = LocalContext.current
    val bukhariBook = remember(databaseFile, hadithNumber) {
        if (databaseFile.contains("bukhari", ignoreCase = true)) {
            BukhariBooks.findByHadithId(hadithNumber)
        } else {
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // No status bar padding - immersive mode hides status bar
        val lazyListState = rememberLazyListState()

        // Toolbar always shows solid surface background regardless of scroll position
        val collapseProgress = remember { androidx.compose.runtime.derivedStateOf { 1f } }

        // Player UI swap (mirrors SurahDetailScreen): info card transforms into player controls when audio starts.
        var showMusicPlayer by remember { mutableStateOf(false) }

        // Track scroll direction for FAB animation
        var previousScrollOffset by remember { mutableStateOf(0) }
        var previousItemIndex by remember { mutableStateOf(0) }
        var showFabVisible by remember { mutableStateOf(true) }

        // Track scroll changes for FAB visibility
        LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
            val currentItemIndex = lazyListState.firstVisibleItemIndex
            val currentOffset = lazyListState.firstVisibleItemScrollOffset

            // Calculate total scroll position for accurate direction detection
            val currentTotalScroll = (currentItemIndex * 1000) + currentOffset
            val previousTotalScroll = (previousItemIndex * 1000) + previousScrollOffset
            val scrollDelta = currentTotalScroll - previousTotalScroll

            // Only respond to significant scroll changes
            if (kotlin.math.abs(scrollDelta) > 20) {
                val isScrollingDown = scrollDelta > 0

                // At top: always show FAB
                val atTop = currentItemIndex == 0 && currentOffset < 100

                if (atTop) {
                    showFabVisible = true
                } else {
                    // Scrolling up → show FAB
                    // Scrolling down → hide FAB
                    showFabVisible = !isScrollingDown
                }

                // Update previous scroll position
                previousScrollOffset = currentOffset
                previousItemIndex = currentItemIndex
            }
        }

        // Build list of available sections based on hadith data
        val availableSections = remember(hadith) {
            mutableListOf<HadithSection>().apply {
                if (hadith.textArabic.isNotEmpty()) add(HadithSection.ARABIC)
                if (!hadith.textPlain.isNullOrEmpty()) add(HadithSection.TRANSLATION)
                if (!hadith.elaboration.isNullOrEmpty()) add(HadithSection.EXPLANATION)
            }.toList()
        }

        // Load saved order and apply it to available sections
        val initialSections = remember(availableSections) {
            val savedOrder = loadHadithSectionOrder(context)
            if (savedOrder != null) {
                // Reorder available sections based on saved order
                val ordered = mutableListOf<HadithSection>()
                savedOrder.forEach { section ->
                    if (section in availableSections) ordered.add(section)
                }
                // Add any new sections not in saved order
                availableSections.forEach { section ->
                    if (section !in ordered) ordered.add(section)
                }
                ordered
            } else {
                availableSections
            }
        }

        // Local mutable list for smooth drag reordering
        val localSections = remember(initialSections) { initialSections.toMutableStateList() }

        // Track if reordering happened to save on drag end
        var wasReordered by remember { mutableStateOf(false) }

        // Reorderable state for the LazyColumn
        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            // Only reorder if both indices are in the sections range (after header)
            val headerOffset = 1 // 1 header item before sections
            val fromSectionIndex = from.index - headerOffset
            val toSectionIndex = to.index - headerOffset
            if (fromSectionIndex >= 0 && toSectionIndex >= 0 &&
                fromSectionIndex < localSections.size && toSectionIndex < localSections.size) {
                localSections.apply {
                    add(toSectionIndex, removeAt(fromSectionIndex))
                }
                wasReordered = true
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with Masjid al-Nawabi image - with parallax effect
            item {
                // Calculate parallax progress based on scroll
                val scrollOffset = lazyListState.firstVisibleItemScrollOffset.toFloat()
                val headerHeightPx = with(LocalDensity.current) {
                    if (isLandscape) 200.dp.toPx() else 400.dp.toPx()
                }
                val parallaxProgress = (scrollOffset / headerHeightPx).coerceIn(0f, 1f)

                // Easing function for smooth parallax
                fun easeOutCubic(x: Float): Float = 1f - (1f - x).let { it * it * it }
                val easedProgress = easeOutCubic(parallaxProgress)
                val centeredProgress = (easedProgress - 0.5f) * 2f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column {
                        // Album-style header image with parallax
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isLandscape) {
                                        Modifier.height(160.dp)
                                    } else {
                                        Modifier.aspectRatio(4f / 3f)
                                    }
                                )
                        ) {
                            Image(
                                painter = painterResource(R.drawable.masjid_al_nawabi),
                                contentDescription = "Masjid al-Nawabi",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // Scale effect: 1.0 -> 1.08 zoom
                                        val scaleValue = 1f + (1f - kotlin.math.abs(centeredProgress)) * 0.08f
                                        scaleX = scaleValue
                                        scaleY = scaleValue

                                        // Subtle vertical translation
                                        val maxTranslation = 15.dp.toPx()
                                        translationY = -easedProgress * maxTranslation

                                        // Alpha variation
                                        alpha = 0.95f + (1f - kotlin.math.abs(centeredProgress)) * 0.05f
                                    },
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center
                            )
                        }

                        // Fixed-height container for info card to match SurahDetailScreen FAB positioning.
                        // Swaps between info card and music player controls when playback starts.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isLandscape) 130.dp else 170.dp)
                        ) {
                            AnimatedContent(
                                targetState = showMusicPlayer,
                                transitionSpec = {
                                    if (targetState) {
                                        slideInVertically(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                                            initialOffsetY = { it / 3 }
                                        ) + fadeIn(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                                        ) togetherWith slideOutVertically(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                                            targetOffsetY = { -it / 3 }
                                        ) + fadeOut(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                                        )
                                    } else {
                                        slideInVertically(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                                            initialOffsetY = { -it / 3 }
                                        ) + fadeIn(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                                        ) togetherWith slideOutVertically(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                                            targetOffsetY = { it / 3 }
                                        ) + fadeOut(
                                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                                        )
                                    }
                                },
                                label = "Hadith Player Controls Transition",
                                modifier = Modifier.fillMaxSize()
                            ) { showPlayer ->
                                if (showPlayer) {
                                    HadithPlayerControls(
                                        isPlaying = isPlaying,
                                        onPlayPauseClick = onPlayClick,
                                        onCollapse = { showMusicPlayer = false },
                                        autoAdvance = autoAdvance,
                                        onToggleAutoAdvance = onToggleAutoAdvance,
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp, vertical = 16.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = hadith.collectionNameEnglish.ifEmpty { collectionName },
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            if (hadith.author.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Compiled by ${hadith.author}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontStyle = FontStyle.Italic
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            val courseCompletionInfo = CourseProgressTracker.getHadithCourseCompletion(
                                                context,
                                                hadithNumber,
                                                databaseFile
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                NiaTopicTag(
                                                    followed = true,
                                                    onClick = { },
                                                    enabled = true,
                                                    text = {
                                                        Text(
                                                            text = "Hadith #${hadithNumber.toLocalizedDigits(selectedLanguage)}"
                                                                .uppercase(Locale.getDefault())
                                                        )
                                                    }
                                                )

                                                if (bukhariBook != null) {
                                                    NiaTopicTag(
                                                        modifier = Modifier.widthIn(
                                                            max = if (courseCompletionInfo == null) 220.dp else 150.dp,
                                                        ),
                                                        followed = false,
                                                        onClick = { },
                                                        enabled = true,
                                                        text = {
                                                            Text(
                                                                text = "Book ${bukhariBook.id} · ${bukhariBook.nameEnglish}"
                                                                    .uppercase(Locale.getDefault()),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        },
                                                    )
                                                }

                                                if (courseCompletionInfo != null) {
                                                    NiaVerifiedTag(
                                                        onClick = { },
                                                        enabled = true,
                                                        text = {
                                                            Text(
                                                                text = courseCompletionInfo.courseName.uppercase(Locale.getDefault())
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Play FAB positioned at boundary between header image and info card
                    // Same position as SurahDetailScreen - hides on scroll down, shows on scroll up
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showFabVisible,
                        enter = scaleIn(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                        exit = scaleOut(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(durationMillis = 300)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(y = (-142).dp)
                            .padding(end = 12.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                if (showMusicPlayer) {
                                    showMusicPlayer = false
                                } else {
                                    if (!isPlaying) showMusicPlayer = true
                                    onPlayClick()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                imageVector = when {
                                    showMusicPlayer -> Icons.Default.CallReceived
                                    isPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = when {
                                    showMusicPlayer -> "Minimize player"
                                    isPlaying -> "Pause"
                                    else -> "Play"
                                }
                            )
                        }
                    }
                }
            }

            // Reorderable content sections - each section is a separate item
            items(localSections, key = { "section_${it.name}" }) { section ->
                ReorderableItem(reorderableLazyListState, key = "section_${section.name}") { isDragging ->
                    // Shared callback to save order when drag ends
                    val onDragStopped: () -> Unit = {
                        if (wasReordered) {
                            saveHadithSectionOrder(context, localSections.toList())
                            wasReordered = false
                        }
                    }

                    when (section) {
                        HadithSection.ARABIC -> {
                            HadithSectionCardWithContent(
                                title = "Arabic (Original)",
                                accentColor = Color(0xFF4CAF50),
                                isExpanded = true,
                                showDragHandle = true,
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides LayoutDirection.Rtl,
                                    ) {
                                        Text(
                                            text = normalizeHadithParagraphs(hadith.textArabic),
                                            modifier = Modifier.fillMaxWidth(),
                                            style = hadithArabicReadingStyle(
                                                fontFamily = arabicFontFamily,
                                                fontSize = 26f,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }

                                    if (translatedArabic != null && selectedLanguage != "ar") {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Hadith (${getLanguageName(selectedLanguage)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val translationDirection = hadithLayoutDirection(selectedLanguage)
                                        CompositionLocalProvider(
                                            LocalLayoutDirection provides translationDirection,
                                        ) {
                                            Text(
                                                text = normalizeHadithParagraphs(translatedArabic),
                                                modifier = Modifier.fillMaxWidth(),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 16.sp,
                                                    lineHeight = 26.sp,
                                                ),
                                                textAlign = hadithTextAlignment(selectedLanguage),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HadithSection.TRANSLATION -> {
                            val displayText = normalizeHadithParagraphs(
                                translatedText ?: hadith.textPlain.orEmpty(),
                            )
                            HadithSectionCard(
                                title = "Translation (${getLanguageName(selectedLanguage)})",
                                accentColor = MaterialTheme.colorScheme.primary,
                                content = displayText,
                                contentLanguage = selectedLanguage,
                                isLoading = isTranslating && translatedText == null,
                                showDragHandle = true,
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
                            )
                        }
                        HadithSection.EXPLANATION -> {
                            val displayText = normalizeHadithParagraphs(
                                translatedElaboration ?: hadith.elaboration.orEmpty(),
                            )
                            HadithSectionCard(
                                title = "Explanation (${getLanguageName(selectedLanguage)})",
                                accentColor = MaterialTheme.colorScheme.secondary,
                                content = displayText,
                                contentLanguage = selectedLanguage,
                                isExpanded = false,
                                isLoading = isTranslating && translatedElaboration == null,
                                showDragHandle = true,
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Fixed toolbar at top with color transition based on scroll
        // Smooth transition: transparent (over album art) → solid (scrolled down)
        val toolbarBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = collapseProgress.value)
        val surfaceColor = MaterialTheme.colorScheme.onSurface
        val toolbarContentColor = Color(
            red = 1f + (surfaceColor.red - 1f) * collapseProgress.value,
            green = 1f + (surfaceColor.green - 1f) * collapseProgress.value,
            blue = 1f + (surfaceColor.blue - 1f) * collapseProgress.value,
            alpha = 1f
        )

        // Get center camera cutout bounds to avoid overlapping icons
        val view = LocalView.current
        val density = LocalDensity.current

        // Calculate the right edge of the camera cutout
        // Dynamic toolbar layout based on camera cutout
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp

        // Get camera cutout bounds
        val cutoutRightDp = remember(view) {
            val displayCutout = view.rootWindowInsets?.displayCutout
            if (displayCutout != null && displayCutout.boundingRects.isNotEmpty()) {
                val maxRight = displayCutout.boundingRects.maxOfOrNull { it.right } ?: 0
                with(density) { maxRight.toDp() }
            } else {
                0.dp
            }
        }

        // Calculate available width for icons on the right side
        val availableWidthDp = screenWidthDp - cutoutRightDp - 16.dp

        // Icon sizes - Bookmark (48dp) + Badge (~100dp) + More (48dp) = 196dp needed
        val iconButtonSize = 44.dp
        val badgeWidth = 90.dp // Compact badge width
        val moreButtonSize = 44.dp

        // Total needed for all elements
        val totalNeeded = iconButtonSize + badgeWidth + moreButtonSize // ~178dp

        // Show elements based on available space
        // Priority: More (always) > Bookmark > Badge
        val showBookmarkButton = availableWidthDp >= (iconButtonSize + moreButtonSize) // ~88dp
        val showBadge = availableWidthDp >= totalNeeded // ~178dp

        Surface(
            color = toolbarBackgroundColor,
            tonalElevation = (4 * collapseProgress.value).dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 8.dp) // Minimal top padding since status bar is hidden by immersive mode
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT SIDE - Back button
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = toolbarContentColor.copy(alpha = 0.15f)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = toolbarContentColor
                        )
                    }
                }

                // RIGHT SIDE - Dynamic icons based on available space
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Bookmark button - same filled/outlined treatment as Surah Details.
                    if (showBookmarkButton) {
                        IconButton(
                            onClick = onBookmarkClick,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) {
                                    Icons.Rounded.Bookmark
                                } else {
                                    Icons.Rounded.BookmarkBorder
                                },
                                contentDescription = if (isBookmarked) {
                                    "Remove bookmark"
                                } else {
                                    "Add bookmark"
                                },
                                tint = toolbarContentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Collection badge - shown if space allows (priority 2)
                    if (showBadge) {
                        NiaTopicTag(
                            followed = false,
                            onClick = { },
                            enabled = true,
                            text = {
                                Text(
                                    text = collectionName.uppercase(Locale.getDefault()),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        )
                    }

                    // More options — opens the voice-selection bottom sheet
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = toolbarContentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Get display name for a language code
 */
private fun getLanguageName(code: String): String {
    return when (code) {
        "ar" -> "Arabic"
        "bn" -> "Bengali"
        "zh" -> "Chinese"
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "id" -> "Indonesian"
        "ru" -> "Russian"
        "sv" -> "Swedish"
        "tr" -> "Turkish"
        "ur" -> "Urdu"
        else -> code.uppercase()
    }
}

/**
 * Play text using TextToSpeech
 */
private suspend fun playHadithRecordingAndAwait(
    context: android.content.Context,
    source: String,
    hadithNumber: Int,
): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    val state = com.starception.submission.services.ChapterRecitationState
    val previousCallback = state.onHadithCompletion
    lateinit var completionCallback: () -> Unit
    fun restoreCallback() {
        if (state.onHadithCompletion === completionCallback) {
            state.onHadithCompletion = previousCallback
        }
    }
    completionCallback = {
        restoreCallback()
        if (continuation.isActive) continuation.resume(true)
    }
    state.onHadithCompletion = completionCallback
    continuation.invokeOnCancellation {
        restoreCallback()
        com.starception.submission.services.ChapterRecitationService.stop(context)
    }
    com.starception.submission.services.ChapterRecitationService.play(
        context = context,
        source = source,
        title = "Hadith #$hadithNumber",
        subtitle = "Sahih Bukhari",
        continuousHandoff = true,
    )
}

private suspend fun speakWithAndroidTtsAndAwait(
    context: android.content.Context,
    text: String,
    language: String,
    existingTts: TextToSpeech?,
    onTtsCreated: (TextToSpeech) -> Unit,
): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    var activeTts = existingTts
    val utteranceId = "bukhari_playlist_${System.nanoTime()}"
    val locale = when (language) {
        "ar" -> java.util.Locale.forLanguageTag("ar")
        "bn" -> java.util.Locale.forLanguageTag("bn-BD")
        "es" -> java.util.Locale.forLanguageTag("es-ES")
        "fr" -> java.util.Locale.FRANCE
        "id" -> java.util.Locale.forLanguageTag("id-ID")
        "ru" -> java.util.Locale.forLanguageTag("ru-RU")
        "sv" -> java.util.Locale.forLanguageTag("sv-SE")
        "tr" -> java.util.Locale.forLanguageTag("tr-TR")
        "ur" -> java.util.Locale.forLanguageTag("ur-PK")
        "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
        else -> java.util.Locale.US
    }
    val intro = when (language) {
        "bn" -> "সহীহ আল-বুখারী থেকে।"
        "ar" -> "من صحيح البخاري."
        "es" -> "De Sahih Al-Bujari."
        "fr" -> "De Sahih Al-Boukhari."
        "id" -> "Dari Sahih Al-Bukhari."
        "ru" -> "Из Сахих аль-Бухари."
        "tr" -> "Sahih-i Buhari'den."
        "ur" -> "صحیح البخاری سے۔"
        "zh" -> "来自《布哈里圣训》。"
        else -> "From Sahih Al-Bukhari."
    }
    val speechText = if (language == "en") {
        EnglishTtsTextNormalizer.normalize("$intro $text")
    } else {
        "$intro $text"
    }

    fun finish(result: Boolean) {
        if (continuation.isActive) continuation.resume(result)
    }

    fun start(tts: TextToSpeech) {
        activeTts = tts
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(true)
            override fun onError(utteranceId: String?) = finish(false)
        })
        tts.language = locale
        val result = tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) finish(false)
    }

    continuation.invokeOnCancellation { activeTts?.stop() }
    if (existingTts != null) {
        start(existingTts)
    } else {
        lateinit var createdTts: TextToSpeech
        createdTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                onTtsCreated(createdTts)
                start(createdTts)
            } else {
                createdTts.shutdown()
                finish(false)
            }
        }
        activeTts = createdTts
    }
}

private fun playWithTts(
    context: android.content.Context,
    text: String,
    language: String,
    tts: TextToSpeech?,
    onTtsCreated: (TextToSpeech) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onPlaybackCompleted: () -> Unit,
) {
    val locale = when (language) {
        "en" -> java.util.Locale.US
        "ar" -> java.util.Locale("ar")
        "bn" -> java.util.Locale("bn", "BD")
        "es" -> java.util.Locale("es", "ES")
        "fr" -> java.util.Locale.FRANCE
        "id" -> java.util.Locale("id", "ID")
        "ru" -> java.util.Locale("ru", "RU")
        "sv" -> java.util.Locale("sv", "SE")
        "tr" -> java.util.Locale("tr", "TR")
        "ur" -> java.util.Locale("ur", "PK")
        "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
        else -> java.util.Locale.US
    }

    // Get intro for hadith
    val intro = when (language) {
        "bn" -> "সহীহ আল-বুখারী থেকে।"
        "ar" -> "من صحيح البخاري."
        "es" -> "De Sahih Al-Bujari."
        "fr" -> "De Sahih Al-Boukhari."
        "id" -> "Dari Sahih Al-Bukhari."
        "ru" -> "Из Сахих аль-Бухари."
        "tr" -> "Sahih-i Buhari'den."
        "ur" -> "صحیح البخاری سے۔"
        "zh" -> "来自《布哈里圣训》。"
        else -> "From Sahih Al-Bukhari."
    }

    val fullText = if (language == "en") {
        EnglishTtsTextNormalizer.normalize("$intro $text")
    } else {
        "$intro $text"
    }

    if (tts != null) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onPlayingChanged(true)
            }
            override fun onDone(utteranceId: String?) {
                onPlaybackCompleted()
            }
            override fun onError(utteranceId: String?) {
                onPlayingChanged(false)
            }
        })
        tts.language = locale
        tts.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "hadith_tts")
        onPlayingChanged(true)
    } else {
        // Use a holder to reference TTS in the callback
        var ttsHolder: TextToSpeech? = null
        ttsHolder = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsHolder?.let { createdTts ->
                    onTtsCreated(createdTts)
                    createdTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            onPlayingChanged(true)
                        }
                        override fun onDone(utteranceId: String?) {
                            onPlaybackCompleted()
                        }
                        override fun onError(utteranceId: String?) {
                            onPlayingChanged(false)
                        }
                    })
                    createdTts.language = locale
                    createdTts.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "hadith_tts")
                    onPlayingChanged(true)
                }
            }
        }
    }
}

/**
 * Play text using Sherpa-ONNX offline TTS with user-selected voice settings.
 * Uses the voice and speaker ID selected in Settings > TTS Settings.
 *
 * Uses speakCachedOrGenerate() to leverage pre-generated audio from the
 * DrivingAudioService cache (3-hadith rolling cache).
 */
private fun playWithSherpaOnnxTts(
    sherpaOnnxTts: SherpaOnnxTtsService,
    text: String,
    hadithNumber: Int,
    selectedVoice: TtsVoice,
    speakerId: Int,
    onPlayingChanged: (Boolean) -> Unit,
    onPlaybackCompleted: () -> Unit,
) {
    // IMPORTANT: Use same intro format as DrivingAudioService for cache compatibility
    val introText = EnglishTtsTextNormalizer.bukhariIntro(hadithNumber)
    val fullText = "$introText $text"

    // Set the voice from user settings
    sherpaOnnxTts.setVoice(selectedVoice)

    // Check if cached (from DrivingAudioService pre-generation)
    val isCached = sherpaOnnxTts.isCached(fullText)
    android.util.Log.i("HadithDetailScreen", "🔊 Playing hadith #$hadithNumber - cached: $isCached")

    // Launch coroutine for TTS playback
    CoroutineScope(Dispatchers.Main).launch {
        try {
            onPlayingChanged(true)

            // Use speakCachedOrGenerate to leverage pre-generated cache
            val success = sherpaOnnxTts.speakCachedOrGenerate(
                text = fullText,
                speakerId = speakerId,
                onComplete = {
                    onPlaybackCompleted()
                }
            )

            if (!success) {
                android.util.Log.e("HadithDetailScreen", "Sherpa-ONNX TTS failed to speak")
                onPlayingChanged(false)
            }
        } catch (e: Exception) {
            android.util.Log.e("HadithDetailScreen", "Error playing with Sherpa-ONNX TTS", e)
            onPlayingChanged(false)
        }
    }
}

@Composable
private fun HadithSectionCard(
    title: String,
    accentColor: Color,
    content: String,
    contentLanguage: String,
    isExpanded: Boolean = true,
    isLoading: Boolean = false,
    showDragHandle: Boolean = false,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(isExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    // Background color for the section
    val sectionColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "sectionColor"
    )
    val sectionShape = RoundedCornerShape(12.dp)

    // Outer container - use Modifier.shadow with shape for proper rounded shadow
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(
                elevation = if (isDragging) 8.dp else 0.dp,
                shape = sectionShape,
                clip = false
            )
            .clip(sectionShape)
            .background(sectionColor)
    ) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = sectionShape,
        color = sectionColor,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent border - spans full height including drag handle
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(accentColor)
                    .then(
                        if (expanded) {
                            Modifier.height(androidx.compose.ui.unit.Dp.Unspecified)
                        } else {
                            Modifier.height(if (showDragHandle) 84.dp else 56.dp)
                        }
                    )
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Drag handle at top center - this is the only draggable area
                if (showDragHandle) {
                    val dragIconTint by animateColorAsState(
                        targetValue = if (isDragging)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "dragIconTint"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = dragIconTint,
                            modifier = dragHandleModifier.size(24.dp)
                        )
                    }
                }

                // Header row - clickable to expand/collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 16.dp, vertical = if (showDragHandle) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = rotationAngle }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isLoading) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = rotationAngle }
                        )
                    }

                // Expandable content with animation
                androidx.compose.animation.AnimatedVisibility(
                    visible = expanded,
                    enter = androidx.compose.animation.expandVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + androidx.compose.animation.fadeIn(
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    ),
                    exit = androidx.compose.animation.shrinkVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        if (isLoading) {
                            // Shimmer loading effect for text
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                repeat(3) { index ->
                                    val widthFraction = when (index) {
                                        0 -> 1f
                                        1 -> 0.85f
                                        else -> 0.6f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(widthFraction)
                                            .height(14.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                            }
                        } else {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides hadithLayoutDirection(contentLanguage),
                            ) {
                                Text(
                                    text = content,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                    ),
                                    textAlign = hadithTextAlignment(contentLanguage),
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
}

/**
 * Collapsible section card that accepts composable content
 */
@Composable
private fun HadithSectionCardWithContent(
    title: String,
    accentColor: Color,
    isExpanded: Boolean = true,
    showDragHandle: Boolean = false,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(isExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    // Background color for the section
    val sectionColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "sectionColor"
    )
    val sectionShape = RoundedCornerShape(12.dp)

    // Outer container - use Modifier.shadow with shape for proper rounded shadow
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(
                elevation = if (isDragging) 8.dp else 0.dp,
                shape = sectionShape,
                clip = false
            )
            .clip(sectionShape)
            .background(sectionColor)
    ) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = sectionShape,
        color = sectionColor,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent border - spans full height including drag handle
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(accentColor)
                    .then(
                        if (expanded) {
                            Modifier.height(androidx.compose.ui.unit.Dp.Unspecified)
                        } else {
                            Modifier.height(if (showDragHandle) 84.dp else 56.dp)
                        }
                    )
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Drag handle at top center - this is the only draggable area
                if (showDragHandle) {
                    val dragIconTint by animateColorAsState(
                        targetValue = if (isDragging)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "dragIconTint"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = dragIconTint,
                            modifier = dragHandleModifier.size(24.dp)
                        )
                    }
                }

                // Header row - clickable to expand/collapse
                Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 16.dp, vertical = if (showDragHandle) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = rotationAngle }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = rotationAngle }
                        )
                    }

                    // Expandable content with animation
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expanded,
                        enter = androidx.compose.animation.expandVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ),
                        exit = androidx.compose.animation.shrinkVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + androidx.compose.animation.fadeOut(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section types for reorderable hadith content
 */
private enum class HadithSection {
    ARABIC,
    TRANSLATION,
    EXPLANATION
}

/** Keeps paragraph direction and its visual edge consistent for every Hadith language. */
private fun hadithLayoutDirection(language: String): LayoutDirection =
    if (language == "ar" || language == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr

private fun hadithTextAlignment(language: String): TextAlign =
    if (hadithLayoutDirection(language) == LayoutDirection.Rtl) TextAlign.End else TextAlign.Start

/**
 * Book-like Arabic paragraph shaping shared with the Mushaf reader: optimized line breaks
 * reduce stretched gaps, while RTL justification lets long lines use the card width.
 */
private fun hadithArabicReadingStyle(
    fontFamily: FontFamily,
    fontSize: Float,
): androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle(
    fontFamily = fontFamily,
    fontSize = fontSize.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = (fontSize * 1.55f).sp,
    letterSpacing = 0.sp,
    textAlign = TextAlign.Justify,
    textDirection = androidx.compose.ui.text.style.TextDirection.Rtl,
    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
    ),
)

/**
 * Bukhari source rows are wrapped at a fixed character width and often indent continuation
 * lines. Those are storage-format breaks, not authored paragraphs. Remove the wrapping and
 * indentation while retaining real blank-line paragraph boundaries.
 */
private fun normalizeHadithParagraphs(text: String): String = text
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .trim()
    .split(Regex("\\n\\s*\\n+"))
    .joinToString("\n\n") { paragraph ->
        paragraph.replace(Regex("\\s+"), " ").trim()
    }


@Composable
private fun HadithErrorContent(
    error: String,
    onBackClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error loading hadith",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Back button
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun HadithShimmerLoading(
    onBackClick: () -> Unit,
    isLandscape: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header shimmer matching the new Masjid al-Nawabi design
            Column {
                // Mosque image placeholder matching the loaded state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLandscape) {
                                Modifier.height(160.dp)
                            } else {
                                Modifier.aspectRatio(4f / 3f)
                            }
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.masjid_al_nawabi),
                        contentDescription = "Masjid al-Nawabi",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = 0.6f // Slightly dimmed for shimmer effect
                    )
                }

                // Info card placeholder matching the loaded state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 130.dp else 170.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Column {
                            // Collection name shimmer
                            Box(
                                modifier = Modifier
                                    .size(width = 200.dp, height = 28.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Author shimmer
                            Box(
                                modifier = Modifier
                                    .size(width = 260.dp, height = 16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(6.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Hadith number chip shimmer
                            Box(
                                modifier = Modifier
                                    .size(width = 100.dp, height = 32.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(16.dp)
                                    )
                            )
                        }
                    }
                }
            }

            // Content shimmer
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                }
            }
        }

        // Back button toolbar - transparent to show sky through
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp) // Minimal top padding since status bar is hidden by immersive mode
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)) // Honor camera cutout horizontally only
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Container for hadith navigation with drag gesture anywhere on screen.
 * Shows pill-shaped edge indicator (like system back gesture style).
 */
@Composable
private fun HadithSwipeContainer(
    hadithNumber: Int,
    onNavigateToPreviousHadith: () -> Unit,
    onNavigateToNextHadith: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var swipeOffsetX by remember { mutableStateOf(0f) }
    var touchY by remember { mutableStateOf(0f) }
    val swipeThreshold = 300f

    val canSwipeRight = hadithNumber > 1
    val swipeProgress = (kotlin.math.abs(swipeOffsetX) / swipeThreshold).coerceIn(0f, 1f)
    val isSwipingRight = swipeOffsetX > 0f
    val isSwipingLeft = swipeOffsetX < 0f

    val showLeftIndicator = isSwipingRight && canSwipeRight
    val showRightIndicator = isSwipingLeft
    val targetProgress = if (showLeftIndicator || showRightIndicator) swipeProgress else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (targetProgress == 0f) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        },
        label = "hadithSwipeArrowProgress",
    )

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(hadithNumber) {
                detectDragGestures(
                    onDragStart = { offset ->
                        swipeOffsetX = 0f
                        touchY = offset.y
                    },
                    onDragEnd = {
                        when {
                            swipeOffsetX > swipeThreshold && canSwipeRight -> onNavigateToPreviousHadith()
                            swipeOffsetX < -swipeThreshold -> onNavigateToNextHadith()
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        swipeOffsetX += dragAmount.x
                        touchY = change.position.y
                    }
                )
            },
    ) {
        content()

        val touchYDp = with(density) { touchY.toDp() }
        val baseHeight = 72f
        val targetSize = 46f
        val indicatorHeight = (baseHeight - (baseHeight - targetSize) * animatedProgress).dp
        val verticalOffset = touchYDp - (indicatorHeight / 2)

        val thresholdReachedLeft = swipeProgress >= 1f && showLeftIndicator
        val thresholdReachedRight = swipeProgress >= 1f && showRightIndicator
        val detachOffset = 8.dp

        if (animatedProgress > 0.01f && showLeftIndicator) {
            HadithSwipeEdgeIndicator(
                progress = animatedProgress,
                thresholdReached = thresholdReachedLeft,
                isLeftEdge = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = if (thresholdReachedLeft) detachOffset else 0.dp,
                        y = verticalOffset
                    ),
            )
        }

        if (animatedProgress > 0.01f && showRightIndicator) {
            HadithSwipeEdgeIndicator(
                progress = animatedProgress,
                thresholdReached = thresholdReachedRight,
                isLeftEdge = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = if (thresholdReachedRight) -detachOffset else 0.dp,
                        y = verticalOffset
                    ),
            )
        }
    }
}

/**
 * Android system back gesture style edge indicator.
 * Starts as tall pill, becomes circular when threshold reached.
 */
@Composable
private fun HadithSwipeEdgeIndicator(
    progress: Float,
    thresholdReached: Boolean,
    isLeftEdge: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (thresholdReached) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(0xFF424242)
    }

    val alpha = (progress * 2.5f).coerceIn(0f, 1f)

    val baseWidth = 28f
    val baseHeight = 72f
    val targetSize = 46f

    val pillWidth = baseWidth + (targetSize - baseWidth) * progress
    val pillHeight = baseHeight - (baseHeight - targetSize) * progress
    val cornerRadius = pillWidth / 2f

    val view = LocalView.current
    LaunchedEffect(thresholdReached) {
        if (thresholdReached) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .width(pillWidth.dp)
            .height(pillHeight.dp)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isLeftEdge) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((28f + progress * 8f).dp),
        )
    }
}

@Composable
private fun HadithPlayerControls(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onCollapse: () -> Unit,
    autoAdvance: Boolean,
    onToggleAutoAdvance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var currentVolume by remember {
        mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCollapse() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val contentColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            if (isPlaying) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = contentColor,
                        modifier = Modifier.size(44.dp)
                    )
                }

                IconButton(
                    onClick = onToggleAutoAdvance,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = if (autoAdvance) "Auto-advance on" else "Auto-advance off",
                        tint = if (autoAdvance) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (isPlaying) onPlayPauseClick()
                        onCollapse()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = "Volume down",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )

                Slider(
                    value = currentVolume,
                    onValueChange = { v ->
                        currentVolume = v
                        audioManager.setStreamVolume(
                            android.media.AudioManager.STREAM_MUSIC,
                            (v * maxVolume).toInt(),
                            0
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volume up",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationSettingsSheet(
    selectedProvider: String,
    selectedLanguage: String,
    availableProviders: List<Pair<String, String>>,
    availableTranslations: List<String>,
    onProviderSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 650.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Translation settings",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Personalize how you read this hadith",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TRANSLATION SERVICE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    items(
                        items = availableProviders,
                        key = { (providerCode, _) -> providerCode },
                    ) { (providerCode, providerName) ->
                        TranslationProviderCard(
                            providerCode = providerCode,
                            providerName = providerName,
                            selected = providerCode == selectedProvider,
                            onClick = { onProviderSelected(providerCode) },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "TRANSLATE TO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    items(
                        items = availableTranslations.chunked(2),
                        key = { languages -> languages.joinToString(separator = "-") },
                    ) { languages ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            languages.forEach { languageCode ->
                                TranslationLanguageCard(
                                    languageCode = languageCode,
                                    selected = languageCode == selectedLanguage,
                                    onClick = { onLanguageSelected(languageCode) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (languages.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your selection is saved automatically and refreshes the current translation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                text = "Done",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationProviderCard(
    providerCode: String,
    providerName: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = when (providerCode) {
        "auto" -> "Automatic"
        else -> providerName
    }
    val description = when (providerCode) {
        "auto" -> "Reverso first, with Google as fallback"
        "google" -> "Reliable coverage across all available languages"
        "reverso" -> "Natural phrasing when a translation is available"
        else -> "Translation service"
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun TranslationLanguageCard(
    languageCode: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = languageCode.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = getLanguageName(languageCode),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** CDN category for a TTS voice — keep in sync with UnifiedSettingsViewModel. */
private fun ttsVoiceDownloadCategory(voice: TtsVoice): String = when (voice) {
    TtsVoice.KOKORO_EN -> "model_tts_kokoro"
    TtsVoice.VITS_VCTK -> "model_tts_vits"
}
