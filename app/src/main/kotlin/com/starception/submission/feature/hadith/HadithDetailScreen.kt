package com.starception.submission.feature.hadith

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.Hadith
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.translation.TranslationService
import com.starception.submission.feature.surah.QuranFonts
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.designsystem.component.NiaVerifiedTag
import com.starception.submission.core.ui.DynamicSkyHeader
import com.starception.submission.core.ui.ImmersiveFullScreenEffect
import com.starception.submission.core.ui.getCurrentSkyPeriodForTheme
import com.starception.submission.core.ui.getSkyColors
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

// Gradient colors for hadith header - earthy tones
private val HadithGradientColors = listOf(
    Color(0xFF795548),  // Brown
    Color(0xFF8D6E63),  // Light brown
    Color(0xFF6D4C41),  // Dark brown
    Color(0xFF5D4037),  // Darker brown
    Color(0xFF4E342E)   // Deepest brown
)

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
    modifier: Modifier = Modifier
) {
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

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var hadith by remember { mutableStateOf<Hadith?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Translation state
    var translatedArabic by remember { mutableStateOf<String?>(null) }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var translatedElaboration by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(translationService.getSelectedLanguage()) }
    var selectedProvider by remember { mutableStateOf(translationService.getSelectedProvider()) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsInitialized by remember { mutableStateOf(false) }

    // Cleanup on dispose
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        }
    }

    // Load hadith
    LaunchedEffect(databaseFile, hadithNumber) {
        isLoading = true
        error = null
        translatedText = null
        translatedElaboration = null
        try {
            hadith = repository.getHadith(databaseFile, hadithNumber)
            if (hadith == null) {
                error = "Hadith not found"
            }
        } catch (e: Exception) {
            error = e.message ?: "Error loading hadith"
            android.util.Log.e("HadithDetailScreen", "Error loading hadith", e)
        }
        isLoading = false
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,  // Transparent to let sky extend to top
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // No padding for status bar in immersive mode
    ) { _ ->
        // Don't apply paddingValues - let content scroll under transparent toolbar like SurahDetailScreen
        HadithSwipeContainer(
            hadithNumber = hadithNumber,
            onNavigateToPreviousHadith = onNavigateToPreviousHadith,
            onNavigateToNextHadith = onNavigateToNextHadith
        ) {
            when {
                isLoading -> {
                    HadithShimmerLoading(onBackClick = wrappedOnBackClick, isLandscape = isLandscape)
                }
                error != null -> {
                    HadithErrorContent(
                        error = error!!,
                        onBackClick = wrappedOnBackClick
                    )
                }
                hadith != null -> {
                    HadithContent(
                        hadith = hadith!!,
                        collectionName = collectionName,
                        hadithNumber = hadithNumber,
                        databaseFile = databaseFile,
                        onBackClick = wrappedOnBackClick,
                        translatedArabic = translatedArabic,
                        translatedText = translatedText,
                        translatedElaboration = translatedElaboration,
                        isTranslating = isTranslating,
                        selectedLanguage = selectedLanguage,
                        onLanguageClick = { showLanguageDialog = true },
                        isLandscape = isLandscape,
                        isPlaying = isPlaying,
                        onPlayClick = {
                            if (isPlaying) {
                                // Stop playback
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                                textToSpeech?.stop()
                                isPlaying = false
                            } else {
                                // Start playback
                                val isBukhari = databaseFile.contains("bukhari", ignoreCase = true)

                                // For Bengali language and Bukhari, use audio files from SD card
                                if (selectedLanguage == "bn" && isBukhari) {
                                    // Try to play from SD card
                                    val formattedNumber = String.format("%04d", hadithNumber)
                                    val bukhariPath = "/sdcard/Bukhari/bukhari_audio_bn"
                                    val possibleFiles = listOf(
                                        java.io.File(bukhariPath, "bukhari_$formattedNumber.ogg"),
                                        java.io.File(bukhariPath, "bukhari_$formattedNumber.mp3")
                                    )

                                    var audioPlayed = false
                                    for (audioFile in possibleFiles) {
                                        if (!audioFile.exists()) continue
                                        try {
                                            mediaPlayer = MediaPlayer().apply {
                                                setDataSource(audioFile.absolutePath)
                                                setOnCompletionListener { mp ->
                                                    mp.release()
                                                    mediaPlayer = null
                                                    isPlaying = false
                                                }
                                                setOnErrorListener { mp, _, _ ->
                                                    mp.release()
                                                    mediaPlayer = null
                                                    isPlaying = false
                                                    true
                                                }
                                                prepare()
                                                start()
                                            }
                                            isPlaying = true
                                            audioPlayed = true
                                            android.util.Log.i("HadithDetailScreen", "▶️ Playing Bengali audio: ${audioFile.absolutePath}")
                                            break
                                        } catch (e: Exception) {
                                            android.util.Log.e("HadithDetailScreen", "Error playing audio: ${e.message}")
                                        }
                                    }

                                    // Fall back to TTS if no audio file found
                                    if (!audioPlayed) {
                                        android.util.Log.w("HadithDetailScreen", "No Bengali audio for hadith #$hadithNumber, using TTS")
                                        playWithTts(
                                            context = context,
                                            text = translatedText ?: hadith!!.textPlain ?: "",
                                            language = selectedLanguage,
                                            tts = textToSpeech,
                                            onTtsCreated = { textToSpeech = it; isTtsInitialized = true },
                                            onPlayingChanged = { isPlaying = it }
                                        )
                                    }
                                } else {
                                    // Use TTS for other languages
                                    val textToSpeak = translatedText ?: hadith!!.textPlain ?: ""
                                    playWithTts(
                                        context = context,
                                        text = textToSpeak,
                                        language = selectedLanguage,
                                        tts = textToSpeech,
                                        onTtsCreated = { textToSpeech = it; isTtsInitialized = true },
                                        onPlayingChanged = { isPlaying = it }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Language selection dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Translation Settings") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 450.dp)
                ) {
                    // Provider section
                    item {
                        Text(
                            text = "Translation Provider",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(availableProviders) { (providerCode, providerName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProvider = providerCode
                                    translationService.setSelectedProvider(providerCode)
                                    // Clear cache to force re-translation with new provider
                                    translationService.clearCache()
                                    translatedText = null
                                    translatedElaboration = null
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = providerCode == selectedProvider,
                                onClick = {
                                    selectedProvider = providerCode
                                    translationService.setSelectedProvider(providerCode)
                                    translationService.clearCache()
                                    translatedText = null
                                    translatedElaboration = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = providerName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Divider
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Language section
                    item {
                        Text(
                            text = "Target Language",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(availableTranslations) { langCode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (langCode != selectedLanguage) {
                                        // Clear cache to force re-translation
                                        translationService.clearCache()
                                        translatedText = null
                                        translatedElaboration = null
                                        selectedLanguage = langCode
                                    }
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = langCode == selectedLanguage,
                                onClick = {
                                    if (langCode != selectedLanguage) {
                                        translationService.clearCache()
                                        translatedText = null
                                        translatedElaboration = null
                                        selectedLanguage = langCode
                                    }
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = getLanguageName(langCode),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Done")
                }
            }
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
    onLanguageClick: () -> Unit = {},
    isLandscape: Boolean = false,
    isPlaying: Boolean = false,
    onPlayClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // No status bar padding - immersive mode hides status bar
        val lazyListState = rememberLazyListState()

        // Calculate toolbar collapse state with smooth transition
        // At top (album art visible) → Transparent
        // Scrolled down (past album art) → Solid theme color
        val collapseProgress = remember {
            androidx.compose.runtime.derivedStateOf {
                val itemIndex = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset.toFloat()

                when {
                    // Past the header - fully solid
                    itemIndex >= 1 -> 1f
                    // At header - calculate progress based on scroll offset
                    else -> {
                        val headerHeight = if (isLandscape) 340f else 596f // image + info card height
                        (offset / headerHeight).coerceIn(0f, 1f)
                    }
                }
            }
        }

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
                                        Modifier.height(200.dp)
                                    } else {
                                        Modifier.aspectRatio(1f)
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

                        // Fixed-height container for info card to match SurahDetailScreen FAB positioning
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
                                    // Collection name
                                    Text(
                                        text = hadith.collectionNameEnglish.ifEmpty { collectionName },
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Author
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

                                    // Hadith number chip and course badge in same row
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
                                                    text = "Hadith #$hadithNumber".uppercase(Locale.getDefault())
                                                )
                                            }
                                        )

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
                            onClick = onPlayClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
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
                                    Text(
                                        text = hadith.textArabic,
                                        fontFamily = QuranFonts.PDMSSaleem,
                                        fontSize = 26.sp,
                                        lineHeight = 48.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

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
                                        Text(
                                            text = translatedArabic,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 16.sp,
                                                lineHeight = 26.sp
                                            ),
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        HadithSection.TRANSLATION -> {
                            val displayText = translatedText ?: hadith.textPlain ?: ""
                            HadithSectionCard(
                                title = "Translation (${getLanguageName(selectedLanguage)})",
                                accentColor = MaterialTheme.colorScheme.primary,
                                content = displayText,
                                isLoading = isTranslating && translatedText == null,
                                showDragHandle = true,
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
                            )
                        }
                        HadithSection.EXPLANATION -> {
                            val displayText = translatedElaboration ?: hadith.elaboration ?: ""
                            HadithSectionCard(
                                title = "Explanation (${getLanguageName(selectedLanguage)})",
                                accentColor = MaterialTheme.colorScheme.secondary,
                                content = displayText,
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = toolbarContentColor
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Language selector button
                IconButton(onClick = onLanguageClick) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = "Select Translation Language",
                        tint = toolbarContentColor
                    )
                }

                // Collection badge - using NiaTopicTag for consistency with Dua detail
                NiaTopicTag(
                    followed = false,
                    onClick = { },
                    enabled = true,
                    text = {
                        Text(
                            text = collectionName.uppercase(Locale.getDefault())
                        )
                    }
                )

                // More options menu
                IconButton(onClick = { /* TODO: More options */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = toolbarContentColor
                    )
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
private fun playWithTts(
    context: android.content.Context,
    text: String,
    language: String,
    tts: TextToSpeech?,
    onTtsCreated: (TextToSpeech) -> Unit,
    onPlayingChanged: (Boolean) -> Unit
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

    val fullText = "$intro $text"

    if (tts != null) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onPlayingChanged(true)
            }
            override fun onDone(utteranceId: String?) {
                onPlayingChanged(false)
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
                            onPlayingChanged(false)
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

@Composable
private fun HadithSectionCard(
    title: String,
    accentColor: Color,
    content: String,
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
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    // Background color for the section
    val sectionColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragging)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
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
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    // Background color for the section
    val sectionColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragging)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
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
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}

@Composable
private fun HadithShimmerLoading(
    onBackClick: () -> Unit,
    isLandscape: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val skyPeriod = getCurrentSkyPeriodForTheme()
    val skyColors = getSkyColors(skyPeriod)
    val headerHeight = if (isLandscape) 200.dp else 300.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header shimmer with dynamic sky
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight) // Match taller header
            ) {
                // Dynamic sky background based on time of day
                DynamicSkyHeader(
                    modifier = Modifier.fillMaxSize(),
                    height = headerHeight,
                    period = skyPeriod
                )
                // Semi-transparent overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
                // Header content positioned at bottom to show more sky artwork
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isLandscape) 24.dp else 16.dp)
                        .padding(top = if (isLandscape) 60.dp else 100.dp, bottom = 4.dp), // Position content at very bottom
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Collection name placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 150.dp, height = 24.dp)
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Hadith number placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 36.dp)
                            .background(
                                Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(18.dp)
                            )
                    )
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
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
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
