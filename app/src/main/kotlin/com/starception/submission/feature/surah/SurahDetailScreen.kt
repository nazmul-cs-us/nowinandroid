package com.starception.submission.feature.surah

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.compose.animation.*
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
import kotlin.math.sqrt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.R
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.designsystem.animation.NiaMotion
import com.starception.submission.core.designsystem.theme.QuranFonts
import com.starception.submission.core.designsystem.theme.ubuntuInspiredFontFamily
import com.starception.submission.core.designsystem.component.scrollbar.DraggableScrollbar
import com.starception.submission.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.starception.submission.core.designsystem.component.scrollbar.scrollbarState
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.core.qurandatabase.QuranRepository
import com.starception.submission.core.qurandatabase.Surah
import com.starception.submission.feature.quran.QuranPlaybackService
import com.starception.submission.feature.quran.AudioLanguage
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import com.starception.submission.download.AssetDownloadManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.starception.submission.core.ui.ImmersiveFullScreenEffect
import javax.inject.Inject
import android.Manifest
import android.os.Build
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.designsystem.component.NiaVerifiedTag
import com.starception.submission.feature.course.CourseCompletionInfo
import com.starception.submission.feature.course.CourseProgressTracker
import java.util.Locale

/**
 * Container for surah navigation with drag gesture anywhere on screen.
 * Shows system-style edge indicator on the side being swiped from.
 */
@Composable
private fun SurahSwipeContainer(
    surahNumber: Int,
    onNavigateToPreviousSurah: () -> Unit,
    onNavigateToNextSurah: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var swipeOffsetX by remember { mutableStateOf(0f) }
    var touchY by remember { mutableStateOf(0f) }
    val swipeThreshold = 300f

    val canSwipeRight = surahNumber > 1
    val canSwipeLeft = surahNumber < 114
    val swipeProgress = (kotlin.math.abs(swipeOffsetX) / swipeThreshold).coerceIn(0f, 1f)
    val isSwipingRight = swipeOffsetX > 0f
    val isSwipingLeft = swipeOffsetX < 0f

    // Show indicator on the side where finger started (same side as swipe direction)
    val showLeftIndicator = isSwipingRight && canSwipeRight
    val showRightIndicator = isSwipingLeft && canSwipeLeft
    val targetProgress = if (showLeftIndicator || showRightIndicator) swipeProgress else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (targetProgress == 0f) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        },
        label = "swipeArrowProgress",
    )

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(surahNumber) {
                detectDragGestures(
                    onDragStart = { offset ->
                        swipeOffsetX = 0f
                        touchY = offset.y
                    },
                    onDragEnd = {
                        when {
                            swipeOffsetX > swipeThreshold && canSwipeRight -> onNavigateToPreviousSurah()
                            swipeOffsetX < -swipeThreshold && canSwipeLeft -> onNavigateToNextSurah()
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f },
                    onDrag = { change, dragAmount ->
                        swipeOffsetX += dragAmount.x
                        touchY = change.position.y
                        if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) * 1.5f) {
                            change.consume()
                        }
                    }
                )
            },
    ) {
        content()

        // Convert touchY to dp for offset
        val touchYDp = with(density) { touchY.toDp() }
        val baseHeight = 72f
        val targetSize = 46f
        val indicatorHeight = (baseHeight - (baseHeight - targetSize) * animatedProgress).dp
        val verticalOffset = touchYDp - (indicatorHeight / 2)

        // When threshold reached, detach from edge (move inward)
        val thresholdReachedLeft = swipeProgress >= 1f && showLeftIndicator
        val thresholdReachedRight = swipeProgress >= 1f && showRightIndicator
        val detachOffset = 8.dp // How far to move away from edge when threshold reached

        // Left indicator (when swiping right to go to previous)
        if (animatedProgress > 0.01f && showLeftIndicator) {
            SwipeEdgeIndicator(
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

        // Right indicator (when swiping left to go to next)
        if (animatedProgress > 0.01f && showRightIndicator) {
            SwipeEdgeIndicator(
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
private fun SwipeEdgeIndicator(
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

    // Start as tall pill (height >> width), become circular (height = width) as progress increases
    val baseWidth = 28f
    val baseHeight = 72f
    val targetSize = 46f // Final circular size

    // Interpolate: width grows, height shrinks toward target
    val pillWidth = baseWidth + (targetSize - baseWidth) * progress
    val pillHeight = baseHeight - (baseHeight - targetSize) * progress
    val cornerRadius = pillWidth / 2f

    // Haptic feedback when threshold reached
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

/**
 * Helper function to convert font name to FontFamily
 */
internal fun getArabicFontFamilyForSelection(selectedFont: String): androidx.compose.ui.text.font.FontFamily {
    val fontFamily = when (selectedFont) {
        "pdms_saleem" -> QuranFonts.PDMSSaleem
        "noor_e_hidayat" -> QuranFonts.NoorEHidayat
        "thabit" -> QuranFonts.Thabit
        "uthmani_script" -> QuranFonts.UthmanicScript
        "indopak_script" -> QuranFonts.IndoPakScript
        else -> {
            android.util.Log.w("FontSelection", "⚠️ Unknown font: $selectedFont, using default PDMSSaleem")
            QuranFonts.PDMSSaleem
        }
    }
    android.util.Log.d("FontSelection", "🔤 Mapping '$selectedFont' -> ${fontFamily::class.simpleName}")
    return fontFamily
}

/** Normal-weight font resource for [selectedFont] — must stay in sync with
 *  [getArabicFontFamilyForSelection]; used to measure glyph ink bounds with
 *  android.graphics.Paint (Compose text layout only exposes advance widths). */
internal fun getArabicFontResId(selectedFont: String): Int = when (selectedFont) {
    "pdms_saleem" -> R.font.pdms_saleem_quran
    "noor_e_hidayat" -> R.font.noor_hidayat_quran
    "thabit" -> R.font.thabit_quran
    "uthmani_script" -> R.font.amiri_quran
    "indopak_script" -> R.font.indopak_quran
    else -> R.font.pdms_saleem_quran
}

/**
 * Surah Detail Screen - Compose version
 * Displays Surah content with album-style design inspired by MusicPlayerAlbumDemoFragment
 * Uses MaterialTheme.colorScheme directly for automatic theme support
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SurahDetailScreen(
    surahNumber: Int,
    newsResourceId: String? = null, // News resource ID for bookmark tracking
    scrollToAyah: Int = 0, // Optional: scroll to specific ayah number (0 = no scroll)
    onBackClick: () -> Unit,
    onTopicClick: (String) -> Unit = {}, // Navigate to topic detail screen
    onNavigateToPreviousSurah: () -> Unit = {}, // Navigate to previous surah (swipe right)
    onNavigateToNextSurah: () -> Unit = {}, // Navigate to next surah (swipe left)
    viewModel: SurahDetailViewModel = hiltViewModel()
) {
    // Enable immersive full-screen mode (hides status bar)
    // Don't restore on dispose to prevent status bar flash when swiping between surahs
    ImmersiveFullScreenEffect(restoreOnDispose = false)

    // Create wrapped back click that restores status bar first
    val view = LocalView.current
    val wrappedOnBackClick: () -> Unit = {
        val window = (view.context as? android.app.Activity)?.window
        val insetsController = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view)
        }
        // Restore status bar before navigating back
        insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        onBackClick()
    }

    // Get repositories from ViewModel holders (moved from parameters to reduce bytecode complexity)
    val quranRepository: QuranRepository = hiltViewModel<QuranRepositoryHolder>().repository
    val userDataRepository: UserDataRepository = hiltViewModel<UserDataRepositoryHolder>().repository
    val context = LocalContext.current

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Audio permission state for runtime permission request
    val audioPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(permission = Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // Helper to check permission before playing audio
    val playWithPermissionCheck: (() -> Unit) -> Unit = { playAction ->
        if (audioPermissionState.status is PermissionStatus.Granted) {
            playAction()
        } else {
            audioPermissionState.launchPermissionRequest()
            // Show toast explaining permission is needed
            Toast.makeText(
                context,
                "Audio permission is required to play Quran recitation",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val currentTranslation by viewModel.currentTranslation.collectAsState()
    val scrollState = rememberLazyListState()

    var showMusicPlayer by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackService by remember { mutableStateOf<QuranPlaybackService?>(null) }
    var currentProgress by remember { mutableStateOf(0f) }

    // Audio recitation tracking — fetch per-ayah timings on surah change and
    // compute the ayah currently being recited from playback position.
    var ayahTimings by remember { mutableStateOf<List<com.starception.submission.core.qurandatabase.AyahTiming>>(emptyList()) }
    var currentRecitingAyah by remember { mutableStateOf<Int?>(null) }

    // Track current playing surah number for updating AlbumInfoCard when next/previous is pressed
    var currentPlayingSurahNumber by remember { mutableStateOf(surahNumber) }

    // Fetch current playing surah AND ayahs when it changes (at parent level for proper recomposition)
    var currentPlayingSurah by remember { mutableStateOf<Surah?>(null) }
    var currentPlayingAyahs by remember { mutableStateOf<List<Ayah>?>(null) }
    LaunchedEffect(currentPlayingSurahNumber) {
        android.util.Log.d("QuranAlbumPlayer", "📥 PARENT_FETCH | surahNumber=$currentPlayingSurahNumber | original=$surahNumber")
        if (currentPlayingSurahNumber != surahNumber) {
            val fetchedSurah = quranRepository.getSurahByNumber(currentPlayingSurahNumber)
            val fetchedAyahs = quranRepository.getAyahsBySurahOnce(currentPlayingSurahNumber)
            android.util.Log.d("QuranAlbumPlayer", "✅ PARENT_FETCHED | surah=${fetchedSurah?.nameEnglish} | ayahs=${fetchedAyahs.size}")
            currentPlayingSurah = fetchedSurah
            currentPlayingAyahs = fetchedAyahs
        } else {
            currentPlayingSurah = null
            currentPlayingAyahs = null
        }
    }

    // Per-surah cache so the AnimatedContent swipe transition can render the
    // exiting page with its original data while the new page slides in with its
    // own (preloaded) data — same pattern as DuaDetailScreen's HorizontalPager
    // and HadithDetailScreen's swipe transition.
    val surahCache = remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Surah, List<Ayah>>>() }

    // Fetch per-ayah timings for the currently playing surah so we can highlight
    // the ayah being recited as audio plays.
    LaunchedEffect(currentPlayingSurahNumber) {
        ayahTimings = com.starception.submission.core.qurandatabase
            .AyahTimingRepository.getTimings(currentPlayingSurahNumber)
        currentRecitingAyah = null
    }

    // Preload the current and neighbouring surahs (clamped to 1..114).
    // Uses the translation-aware loader so cached ayahs carry both Arabic and the
    // selected translation joined by "\n\n" — otherwise the renderer's split would
    // produce one-part text and the translation row would silently disappear.
    LaunchedEffect(currentPlayingSurahNumber, currentTranslation) {
        // Translation changed — invalidate the cache so neighbours reload in the
        // new language instead of serving stale Arabic-only entries.
        surahCache.clear()
        listOf(currentPlayingSurahNumber - 1, currentPlayingSurahNumber, currentPlayingSurahNumber + 1)
            .filter { it in 1..114 && it !in surahCache }
            .forEach { num ->
                try {
                    val pair = viewModel.loadSurahWithTranslation(num)
                    if (pair != null && pair.second.isNotEmpty()) {
                        surahCache[num] = pair
                    }
                } catch (_: Exception) {
                    // Neighbour preload failure is non-fatal — silent.
                }
            }
    }

    // Load volume from ViewModel (persisted in SharedPreferences)
    val currentVolume by viewModel.currentVolume.collectAsState()

    var showTranslationDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showTajweedLegendDialog by remember { mutableStateOf(false) }

    // Enhanced database features
    val wordStudyData by viewModel.wordStudyData.collectAsState()
    val tafseerData by viewModel.tafseerData.collectAsState()
    val selectedTafseerBook by viewModel.selectedTafseerBook.collectAsState()
    var showWordStudyDialog by remember { mutableStateOf(false) }
    var showTafseerDialog by remember { mutableStateOf(false) }

    // Tafseer translation states
    val tafseerTranslationLanguage by viewModel.tafseerTranslationLanguage.collectAsState()
    val tafseerTranslationProvider by viewModel.tafseerTranslationProvider.collectAsState()
    val translatedTafseerSaadi by viewModel.translatedTafseerSaadi.collectAsState()
    val translatedTafseerMoysar by viewModel.translatedTafseerMoysar.collectAsState()
    val translatedTafseerBaghawi by viewModel.translatedTafseerBaghawi.collectAsState()
    val translatedWordMeanings by viewModel.translatedWordMeanings.collectAsState()
    val isTafseerTranslating by viewModel.isTafseerTranslating.collectAsState()

    // Bismillah display state from ViewModel (based on database content)
    val showBismillahRow by viewModel.showBismillahRow.collectAsState()

    // Playback audio language always follows the selected translation text, so
    // Bengali text gets Bengali recitation, English gets English, etc. (translations
    // without audio fall back to Arabic). The translation key is shared app-wide and
    // can be changed from other screens, so deriving it here keeps audio in sync
    // instead of relying on a separate, easily-stale audio_language preference.
    var currentAudioLanguage by remember {
        mutableStateOf(
            mapTranslationCodeToAudioLanguage(currentTranslation) ?: AudioLanguage.ARABIC_ONLY
        )
    }

    // Keep the audio language in lock-step with the translation whenever it changes
    // (from this screen's dialog or any other surface that writes quran_translation).
    LaunchedEffect(currentTranslation) {
        val derived = mapTranslationCodeToAudioLanguage(currentTranslation) ?: AudioLanguage.ARABIC_ONLY
        if (derived != currentAudioLanguage) {
            currentAudioLanguage = derived
        }
        viewModel.changeAudioLanguage(derived.name)
        playbackService?.let { service ->
            service.setAudioLanguage(derived)
            // If audio is already playing, restart the current surah so the new
            // language takes effect immediately rather than on the next track.
            if (service.isPlaying()) {
                service.playSurah(service.getCurrentSurahIndex(), true)
            }
        }
    }

    val selectedArabicFont by viewModel.selectedArabicFont.collectAsState()
    val availableArabicFonts = remember { viewModel.getAvailableArabicFonts() }

    // Debug: Log when font selection changes
    LaunchedEffect(selectedArabicFont) {
        android.util.Log.d("QuranAlbumPlayer_FONT", "🎨 MAIN SCREEN - Font changed to: $selectedArabicFont")
    }

    // Font size state - loaded from ViewModel (which reads from SharedPreferences)
    val arabicFontSize by viewModel.arabicFontSize.collectAsState()
    val minFontSize = 14f
    val maxFontSize = 60f  // Increased from 40f to 60f for much larger text

    // Translation visibility toggle state - loaded from ViewModel (persisted in SharedPreferences)
    val showTranslationInText by viewModel.showTranslation.collectAsState()

    // Text alignment state - loaded from ViewModel (persisted in SharedPreferences)
    val textAlignment by viewModel.textAlignment.collectAsState()

    // Tajweed state - loaded from ViewModel (persisted in SharedPreferences)
    val showTajweed by viewModel.showTajweed.collectAsState()
    val tajweedAnnotations by viewModel.tajweedAnnotations.collectAsState()

    // Continuous (Mushaf) reading mode
    val continuousReadingMode by viewModel.continuousReadingMode.collectAsState()

    // Track bookmark state using UserDataRepository
    // When opened from the news feed we use the real news resource ID; otherwise fall back to a
    // synthetic per-surah ID so bookmarking still works from Prayer Times, Course, prev/next nav, etc.
    val bookmarkId = newsResourceId ?: "quran-surah-$surahNumber"
    var isBookmarked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(bookmarkId) {
        val userData = userDataRepository.userData.first()
        val bookmarkState = bookmarkId in userData.bookmarkedNewsResources
        android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "🔄 SYNC | surah=$surahNumber | bookmarkId=$bookmarkId | bookmarked=$bookmarkState")
        isBookmarked = bookmarkState
    }

    // Load topics for this news resource or by surah number
    val topics by viewModel.topics.collectAsState()
    LaunchedEffect(newsResourceId, surahNumber) {
        if (newsResourceId != null) {
            viewModel.loadTopicsForNewsResource(newsResourceId)
        } else {
            // When swiping, newsResourceId is null - load topics by surah number
            viewModel.loadTopicsForSurah(surahNumber)
        }
    }

    val availableTranslations = remember { viewModel.getAvailableTranslations() }

    // Hilt entry point for AudioDownloadHelper — needed so the service can fetch
    // missing translation audio (e.g. Bengali / English) on demand. Without
    // wiring this here, playSurah() silently fails for any audio not already
    // on disk because the service's onAudioNeedsDownload callback is unbound.
    val audioDownloadHelper = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SherpaOnnxTtsEntryPoint::class.java,
        ).audioDownloadHelper()
    }

    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? QuranPlaybackService.QuranBinder
                playbackService = binder?.getService()

                playbackService?.onPlaybackStateChanged = { playing ->
                    isPlaying = playing
                }

                playbackService?.onProgressChanged = { position, duration ->
                    if (duration > 0) {
                        currentProgress = position.toFloat() / duration.toFloat()
                    }
                    // Map current ms → ayah number using cached timings.
                    val nowAyah = com.starception.submission.core.qurandatabase
                        .AyahTimingRepository.findAyahAt(ayahTimings, position.toLong())
                    if (nowAyah != currentRecitingAyah) {
                        currentRecitingAyah = nowAyah
                    }
                }

                // Update current playing surah number when next/previous is pressed
                playbackService?.onSurahChanged = { surahIndex ->
                    val newSurahNumber = surahIndex + 1 // Convert 0-based index to 1-based surah number
                    android.util.Log.d("QuranAlbumPlayer", "🔄 SURAH_CHANGED | index=$surahIndex | surahNumber=$newSurahNumber")
                    currentPlayingSurahNumber = newSurahNumber
                }

                // Wire the on-demand download trigger so missing translation audio gets
                // fetched and auto-played when ready. The helper itself is injected into
                // the service directly via Hilt.
                playbackService?.onAudioNeedsDownload = { cdnKey, _ ->
                    android.util.Log.i("PlaybackTrace", "⬇️ download requested: $cdnKey")
                    coroutineScope.launch {
                        try {
                            val result = audioDownloadHelper.downloadAudio(cdnKey)
                            android.util.Log.i("PlaybackTrace", "⬇️ download result: $result")
                            if (result is AssetDownloadManager.DownloadState.Completed) {
                                val idx = playbackService?.getCurrentSurahIndex() ?: return@launch
                                playbackService?.playAfterDownload(idx)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PlaybackTrace", "⬇️ download failed", e)
                        }
                    }
                }

                playbackService?.setAudioLanguage(currentAudioLanguage)
                isPlaying = playbackService?.isPlaying() ?: false

                // The page always shows the surah from the route argument. We must NOT
                // snap it to whatever the service is currently playing — otherwise
                // opening a different surah (e.g. from For You) while audio plays would
                // jump to the playing surah. onSurahChanged keeps it in sync only when
                // playback itself advances while this screen is open.
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playbackService = null
            }
        }
    }

    // Bind to playback service
    DisposableEffect(context) {
        val intent = Intent(context, QuranPlaybackService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    // Load the surah
    LaunchedEffect(surahNumber) {
        viewModel.loadSurah(surahNumber)
    }

    // First-launch peek hint: once per install, after the surah loads, briefly
    // peek toward the "other" view and spring back. In Mushaf mode that means
    // peek UP (the ayah header is above); in ayah-list mode that means peek
    // DOWN (more content is below). Mirrors the TikTok / Reels / YouTube
    // Shorts content-tease pattern. Skipped when scrollToAyah is set.
    run {
        val hintDensity = LocalDensity.current
        LaunchedEffect(uiState, continuousReadingMode) {
            if (uiState !is SurahDetailUiState.Success) return@LaunchedEffect
            if (scrollToAyah > 0) return@LaunchedEffect
            val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("mushaf_view_hint_shown", false)) return@LaunchedEffect
            // Wait for auto-scroll-to-Mushaf (80ms delay + ~350ms animation) to
            // fully settle before we peek.
            kotlinx.coroutines.delay(900L)
            if (scrollState.isScrollInProgress) return@LaunchedEffect
            val peekPx = with(hintDensity) { 140.dp.toPx() }
            // Peek away from the current view to reveal the other view briefly.
            val peekDelta = if (continuousReadingMode) -peekPx else peekPx
            scrollState.animateScrollBy(
                value = peekDelta,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 380,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
            kotlinx.coroutines.delay(260L)
            scrollState.animateScrollBy(
                value = -peekDelta,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                ),
            )
            prefs.edit().putBoolean("mushaf_view_hint_shown", true).apply()
        }
    }

    // Highlighted ayah: when the user arrives via search (e.g. tapping
    // "Ayatul Kursi" jumps to 2:255), tint that ayah so they can see which
    // ayah the search picked instead of just landing in the middle of the
    // surah. Keyed on surahNumber + scrollToAyah so revisiting clears it.
    var highlightedAyahNumber by remember(surahNumber, scrollToAyah) {
        mutableStateOf<Int?>(if (scrollToAyah > 0) scrollToAyah else null)
    }

    // Scroll to specific ayah when content is loaded (if scrollToAyah > 0)
    LaunchedEffect(uiState, scrollToAyah, showBismillahRow) {
        if (scrollToAyah > 0 && uiState is SurahDetailUiState.Success) {
            val state = uiState as SurahDetailUiState.Success
            // Calculate the index in LazyColumn:
            // Index 0: AlbumHeader + InfoCard/Controls container
            // Index 1 (optional): Bismillah row (if showBismillahRow is true)
            // Index 2+: Ayah items (starting from ayah 1)
            val bismillahOffset = if (showBismillahRow) 1 else 0
            val ayahIndex = 1 + bismillahOffset + (scrollToAyah - 1)

            // Ensure index is valid
            val totalItems = 1 + bismillahOffset + state.ayahs.size
            if (ayahIndex in 0 until totalItems) {
                android.util.Log.d("QuranAlbumPlayer", "📜 Scrolling to Ayah $scrollToAyah at index $ayahIndex")
                scrollState.animateScrollToItem(
                    ayahIndex
                )
            }
        }
    }

    // Toolbar always shows solid surface background regardless of scroll position
    val collapseProgress = remember { derivedStateOf { 1f } }

    val isCollapsed = remember {
        derivedStateOf {
            collapseProgress.value > 0.5f
        }
    }

    // Track scroll direction for floating toolbar and FAB animation with stable detection
    var previousScrollOffset by remember { mutableStateOf(0) }
    var previousItemIndex by remember { mutableStateOf(0) }
    var showFloatingToolbar by remember { mutableStateOf(false) } // Floating toolbar hidden initially, shown via More menu
    var isFloatingToolbarExpanded by remember { mutableStateOf(true) } // When shown, starts expanded
    var showFabVisible by remember { mutableStateOf(true) } // FAB visible by default

    // Use LaunchedEffect to track scroll changes with proper thresholds
    LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
        val currentItemIndex = scrollState.firstVisibleItemIndex
        val currentOffset = scrollState.firstVisibleItemScrollOffset

        // Calculate total scroll position for accurate direction detection
        val currentTotalScroll = (currentItemIndex * 1000) + currentOffset
        val previousTotalScroll = (previousItemIndex * 1000) + previousScrollOffset
        val scrollDelta = currentTotalScroll - previousTotalScroll

        // Only update if scroll delta is significant (prevents jitter during small movements)
        if (kotlin.math.abs(scrollDelta) > 50) {
            val isScrollingDown = scrollDelta > 0

            // At top: always show FAB
            val atTop = currentItemIndex == 0 && currentOffset < 100

            if (atTop) {
                showFabVisible = true
                // Don't auto-expand floating toolbar - user must click hint manually
            } else {
                // Scrolling up → show FAB, and collapse floating toolbar if it's currently expanded
                // Scrolling down → hide FAB, but don't change floating toolbar state
                showFabVisible = !isScrollingDown

                // Hide floating toolbar when scrolling up
                if (!isScrollingDown && showFloatingToolbar) {
                    showFloatingToolbar = false
                }
            }

            // Update previous scroll position
            previousScrollOffset = currentOffset
            previousItemIndex = currentItemIndex
        }
    }

    // In-place swipe handlers — match the mini-bar prev/next behavior. Bumping
    // currentPlayingSurahNumber drives the AnimatedContent slide; if audio is
    // playing we also advance the service so its onSurahChanged echo lines up.
    SurahSwipeContainer(
        surahNumber = currentPlayingSurahNumber,
        onNavigateToPreviousSurah = {
            if (currentPlayingSurahNumber > 1) {
                if (isPlaying) {
                    playbackService?.playPrevious()
                } else {
                    currentPlayingSurahNumber -= 1
                }
            }
        },
        onNavigateToNextSurah = {
            if (currentPlayingSurahNumber < 114) {
                if (isPlaying) {
                    playbackService?.playNext()
                } else {
                    currentPlayingSurahNumber += 1
                }
            }
        },
    ) {
        Scaffold(
            topBar = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentWindowInsets = WindowInsets(0, 0, 0, 0) // No padding for status bar in immersive mode
        ) { paddingValues ->
        when (val state = uiState) {
            is SurahDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SurahDetailUiState.Success -> {
                androidx.compose.animation.AnimatedContent(
                    targetState = currentPlayingSurahNumber,
                    transitionSpec = {
                        val direction = if (targetState > initialState) {
                            androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        slideIntoContainer(direction, animationSpec = tween(350, easing = FastOutSlowInEasing)) togetherWith
                            slideOutOfContainer(direction, animationSpec = tween(350, easing = FastOutSlowInEasing))
                    },
                    label = "surahPageSwipe",
                    modifier = Modifier.fillMaxSize(),
                ) { num ->
                    // Each pane reads its surah+ayahs from the preloaded cache so the
                    // exiting page keeps its original content and the entering page
                    // immediately shows the new surah while sliding in.
                    // Ignore poisoned cache entries with no ayahs (a transient DB
                    // failure during preload) — fall back to the loaded state so the
                    // page never renders empty.
                    val cached = surahCache[num]?.takeIf { it.second.isNotEmpty() }
                    val pageSurah = cached?.first ?: state.surah
                    val baseAyahs = cached?.second ?: state.ayahs
                    // Swap Arabic text to the IndoPak edition when an IndoPak font is
                    // selected, so the "extra alif" issue from Uthmani text rendered in
                    // an IndoPak font goes away (matches quran.com IndoPak reading mode).
                    val pageAyahs = remember(num, baseAyahs, selectedArabicFont) {
                        if (selectedArabicFont == "pdms_saleem" || selectedArabicFont == "indopak_script") {
                            val indoPakTexts = com.starception.submission.core.qurandatabase
                                .IndoPakTextRepository.getInstance(context)
                                .getSurahTexts(num)
                            if (indoPakTexts.isEmpty()) baseAyahs
                            else baseAyahs.map { ayah ->
                                val ip = indoPakTexts[ayah.numberInSurah] ?: return@map ayah
                                // Swap Arabic for IndoPak text but keep any translation
                                // suffix joined with "\n\n" (renderer splits on this to
                                // show Arabic on top and translation under it).
                                val translationSuffix = ayah.text.substringAfter("\n\n", missingDelimiterValue = "")
                                val newText = if (translationSuffix.isEmpty()) ip else "$ip\n\n$translationSuffix"
                                ayah.copy(text = newText)
                            }
                        } else baseAyahs
                    }
                AlbumPlayerContent(
                    surah = pageSurah,
                    ayahs = pageAyahs,
                    scrollState = scrollState,
                    collapseProgress = collapseProgress.value,
                    showMusicPlayer = showMusicPlayer,
                    isPlaying = isPlaying,
                    currentProgress = currentProgress,
                    currentVolume = currentVolume,
                    currentPlayingSurahNumber = num,
                    currentPlayingSurah = null,
                    currentPlayingAyahs = null,
                    showFabVisible = showFabVisible,
                    selectedArabicFont = selectedArabicFont,
                    arabicFontSize = arabicFontSize,
                    textAlignment = textAlignment,
                    currentTranslation = currentTranslation,
                    showTranslationInText = showTranslationInText,
                    showBismillahRow = showBismillahRow,
                    showTajweed = showTajweed,
                    tajweedAnnotations = tajweedAnnotations,
                    onToggleTajweed = {
                        if (!showTajweed && !viewModel.isTajweedAvailable) {
                            Toast.makeText(context, "Tajweed data not downloaded yet. Please download it from Settings.", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.changeTajweed(!showTajweed)
                        }
                    },
                    onToggleTranslation = { viewModel.changeShowTranslation(!showTranslationInText) },
                    onCycleAlignment = {
                        // Cycle through: start -> center -> end -> start
                        val nextAlignment = when (textAlignment) {
                            "start" -> "center"
                            "center" -> "end"
                            else -> "start"
                        }
                        viewModel.changeTextAlignment(nextAlignment)
                    },
                    onPlayPauseClick = {
                        val service = playbackService
                        android.util.Log.d("PlaybackTrace", "▶️ onPlayPauseClick | route=$surahNumber | num=$num | service=${service != null} | isPlaying=${service?.isPlaying()}")
                        if (service != null) {
                            if (service.isPlaying()) {
                                service.togglePlayPause()
                            } else {
                                playWithPermissionCheck {
                                    showMusicPlayer = true
                                    service.setAudioLanguage(currentAudioLanguage)
                                    android.util.Log.d("PlaybackTrace", "▶️ calling playSurah(index=${num - 1}) for num=$num")
                                    service.playSurah(num - 1, true)
                                }
                            }
                        }
                    },
                    onRewindClick = {
                        // Immediately update the current surah number for UI sync
                        val prevSurahNumber = if (currentPlayingSurahNumber > 1) currentPlayingSurahNumber - 1 else 114
                        currentPlayingSurahNumber = prevSurahNumber
                        playbackService?.playPrevious()
                    },
                    onForwardClick = {
                        // Immediately update the current surah number for UI sync
                        val nextSurahNumber = if (currentPlayingSurahNumber < 114) currentPlayingSurahNumber + 1 else 1
                        currentPlayingSurahNumber = nextSurahNumber
                        playbackService?.playNext()
                    },
                    onVolumeChange = { volume ->
                        viewModel.changeVolume(volume)
                        playbackService?.setVolume(volume)
                    },
                    onAyahClick = { /* TODO */ },
                    onFabClick = {
                        val service = playbackService
                        android.util.Log.d("PlaybackTrace", "▶️ onFabClick | route=$surahNumber | num=$num | service=${service != null} | isPlaying=${service?.isPlaying()} | currentPlayingSurahNumber=$currentPlayingSurahNumber")
                        if (service != null) {
                            if (service.isPlaying()) {
                                service.togglePlayPause()
                            } else {
                                playWithPermissionCheck {
                                    showMusicPlayer = true
                                    service.setAudioLanguage(currentAudioLanguage)
                                    android.util.Log.d("PlaybackTrace", "▶️ FAB calling playSurah(index=${num - 1}) for num=$num")
                                    service.playSurah(num - 1, true)
                                }
                            }
                        }
                    },
                    onCollapseMusicPlayer = { showMusicPlayer = false },
                    onWordStudyClick = { ayahNumber ->
                        viewModel.loadWordStudy(surahNumber, ayahNumber)
                        // Don't show dialog - data is shown in bottom sheet
                    },
                    onTafseerClick = { ayahNumber ->
                        viewModel.loadTafseer(surahNumber, ayahNumber)
                        // Don't show dialog - data is shown in bottom sheet
                    },
                    onPlayAyahClick = { ayahNumber ->
                        val service = playbackService
                        if (service != null) {
                            playWithPermissionCheck {
                                // Generate audio URL for this specific ayah
                                val audioUrl = com.starception.submission.core.qurandatabase.getAyahAudioUrl(
                                    surahNumber = surahNumber,
                                    ayahNumber = ayahNumber,
                                    reciter = com.starception.submission.core.qurandatabase.QuranReciters.ALAFASY_128
                                )

                                // Show music player
                                showMusicPlayer = true

                                // Play the specific ayah using URL
                                service.playAyahByUrl(
                                    audioUrl = audioUrl,
                                    surahName = state.surah.nameEnglish,
                                    ayahNumber = ayahNumber,
                                    shouldAutoPlay = true
                                )

                                // Show toast with ayah info
                                android.widget.Toast.makeText(
                                    context,
                                    "Playing ${state.surah.nameEnglish} - Ayah $ayahNumber",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Audio player not ready",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    topics = topics,
                    onTopicClick = onTopicClick,
                    tafseerData = tafseerData,
                    wordStudyData = wordStudyData,
                    selectedTafseerBook = selectedTafseerBook,
                    onTafseerBookSelected = { book -> viewModel.selectTafseerBook(book) },
                    // Tafseer translation
                    tafseerTranslationLanguage = tafseerTranslationLanguage,
                    tafseerTranslationProvider = tafseerTranslationProvider,
                    translatedTafseerSaadi = translatedTafseerSaadi,
                    translatedTafseerMoysar = translatedTafseerMoysar,
                    translatedTafseerBaghawi = translatedTafseerBaghawi,
                    translatedWordMeanings = translatedWordMeanings,
                    isTafseerTranslating = isTafseerTranslating,
                    availableTafseerTranslations = viewModel.getAvailableTafseerTranslations(),
                    availableTafseerProviders = viewModel.getAvailableTafseerProviders(),
                    onTafseerLanguageChange = { lang -> viewModel.changeTafseerTranslationLanguage(lang) },
                    onTafseerProviderChange = { provider -> viewModel.changeTafseerTranslationProvider(provider) },
                    getTafseerTranslationName = { code -> viewModel.getTafseerTranslationName(code) },
                    isLandscape = isLandscape,
                    onFontSizeChange = { newSize -> viewModel.changeArabicFontSize(newSize) },
                    minFontSize = minFontSize,
                    maxFontSize = maxFontSize,
                    onToggleContinuousReadingMode = { viewModel.toggleContinuousReadingMode() },
                    continuousReadingMode = continuousReadingMode,
                    initialMushafPage = viewModel.getLastMushafPage(surahNumber),
                    onMushafPageChange = { page -> viewModel.saveLastMushafPage(surahNumber, page) },
                    currentRecitingAyah = currentRecitingAyah,
                    // Highlight + Mushaf snap only on the originating surah —
                    // swiping to a neighbour surah shouldn't drag the tint or
                    // page-jump to its same-numbered ayah.
                    highlightedAyahNumber = if (num == surahNumber) highlightedAyahNumber else null,
                    scrollToAyahForMushafJump = if (num == surahNumber) scrollToAyah else 0,
                    modifier = Modifier
                )
                }
            }
            is SurahDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is SurahDetailUiState.NeedsDownload -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    com.starception.submission.download.MissingContentCard(
                        resourceName = state.resourceName,
                        category = state.category,
                        description = state.description,
                        downloadManager = viewModel.downloadManager,
                        onDownloadComplete = {
                            // Close cached Room instance AND delete its managed DB file
                            // so Room re-copies from the freshly downloaded source.
                            // (Plain clearCache() would reuse the stale empty file Room
                            // created on the previous open.)
                            com.starception.submission.core.qurandatabase.QuranTranslationHelper
                                .resetTranslationDatabase(context, currentTranslation)
                            viewModel.loadSurah(surahNumber, currentTranslation)
                        },
                    )
                }
            }
        }
    }

        // In Mushaf mode the toolbar only shows when the user has scrolled up to the album
        // header (item 0) and at least half of it is in view. Keying off the header's
        // scroll fraction (not firstVisibleItemIndex) is robust to landscape, where the
        // short header + the PullToSync banner's viewport push leave a small bottom sliver
        // of item 0 peeking at the top of the page — a plain `index >= 1` or "any header
        // pixels visible" check would wrongly keep the toolbar up during reading.
        val showTopBar = remember {
            derivedStateOf {
                if (!continuousReadingMode) return@derivedStateOf true
                if (scrollState.firstVisibleItemIndex != 0) return@derivedStateOf false
                val header = scrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                    ?: return@derivedStateOf false
                scrollState.firstVisibleItemScrollOffset < header.size / 2
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showTopBar.value,
            enter = androidx.compose.animation.slideInVertically(
                animationSpec = NiaMotion.standardTween(NiaMotion.Duration.MEDIUM_3),
                initialOffsetY = { -it }
            ) + androidx.compose.animation.fadeIn(animationSpec = NiaMotion.standardTween(NiaMotion.Duration.MEDIUM_3)),
            exit = androidx.compose.animation.slideOutVertically(
                animationSpec = NiaMotion.standardTween(NiaMotion.Duration.MEDIUM_3),
                targetOffsetY = { -it }
            ) + androidx.compose.animation.fadeOut(animationSpec = NiaMotion.standardTween(NiaMotion.Duration.MEDIUM_3)),
        ) {
        // Always visible toolbar with collapsing effect based on scroll position
        AlbumPlayerTopBar(
            collapseProgress = collapseProgress.value,
            isCollapsed = isCollapsed.value,
            surahName = when (uiState) {
                is SurahDetailUiState.Success -> (surahCache[currentPlayingSurahNumber]?.first
                    ?: (uiState as SurahDetailUiState.Success).surah).nameEnglish
                else -> ""
            },
            surahNameArabic = when (uiState) {
                is SurahDetailUiState.Success -> (surahCache[currentPlayingSurahNumber]?.first
                    ?: (uiState as SurahDetailUiState.Success).surah).nameArabic
                else -> ""
            },
            currentTranslation = currentTranslation,
            isBookmarked = isBookmarked,
            selectedArabicFont = selectedArabicFont,
            showTajweed = showTajweed,
            onBackClick = wrappedOnBackClick,
            onTranslationClick = { showTranslationDialog = true },
            onFontClick = { showFontDialog = true },
            onTajweedClick = {
                if (!showTajweed && !viewModel.isTajweedAvailable) {
                    Toast.makeText(context, "Tajweed data not downloaded yet. Please download it from Settings.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.changeTajweed(!showTajweed)
                }
            },
            onBookmarkClick = {
                val oldState = isBookmarked
                val newState = !oldState
                isBookmarked = newState
                android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "👆 CLICK | surah=$surahNumber | bookmarkId=$bookmarkId | old_state=$oldState | new_state=$newState")

                coroutineScope.launch {
                    userDataRepository.setNewsResourceBookmarked(bookmarkId, newState)
                    android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "✅ CLICK_COMPLETE | surah=$surahNumber | bookmarkId=$bookmarkId | state=$newState")
                }
            },
            onMoreClick = {
                // Toggle floating toolbar visibility
                showFloatingToolbar = !showFloatingToolbar
                // When showing, ensure it's expanded
                if (showFloatingToolbar) {
                    isFloatingToolbarExpanded = true
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }

        // Floating Surah name that moves from info card to toolbar when scrolling.
        if (uiState is SurahDetailUiState.Success && showTopBar.value) {
            // Prefer the currently-playing surah from the cache so the floating
            // name matches the AlbumInfoCard / mini-bar when audio advances to a
            // different surah than the route param.
            val surah = surahCache[currentPlayingSurahNumber]?.first
                ?: (uiState as SurahDetailUiState.Success).surah
            val density = LocalDensity.current
            val localConfig = LocalConfiguration.current
            val localIsLandscape = localConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            // Calculate positions once (stable values)
            // Album art is height(160) in landscape, 4/3 (width/height) ratio portrait — height = width * 3/4.
            val albumHeaderHeight = if (localIsLandscape) 160 else (localConfig.screenWidthDp * 3 / 4)
            // Start floating names higher (12dp) to ensure good separation from translation text below
            val headerYPx = with(density) { (albumHeaderHeight + 12).dp.toPx() }
            val toolbarYPx = with(density) { 21.dp.toPx() }  // Stop at toolbar level (locked position)
            val startXPx = with(density) { 24.dp.toPx() }
            val endXPx = with(density) { 56.dp.toPx() }  // Position after back button

            // Use derivedStateOf for stable, optimized updates
            val floatingState by remember {
                derivedStateOf {
                    val scrollOffset = if (scrollState.firstVisibleItemIndex == 0) {
                        scrollState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        headerYPx
                    }
                    val namesY = (headerYPx - scrollOffset).coerceAtLeast(toolbarYPx)
                    val progress = ((headerYPx - namesY) / (headerYPx - toolbarYPx)).coerceIn(0f, 1f)
                    Triple(namesY, progress, startXPx + (progress * (endXPx - startXPx)))
                }
            }

            val (namesYPx, progress, xOffsetPx) = floatingState
            val scale = 1f - (progress * 0.4f)  // Scale down as it moves up
            val contentColor = MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = xOffsetPx
                        translationY = namesYPx
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = surah.nameEnglish,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = surah.nameArabic,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                            fontWeight = FontWeight.Normal
                        ),
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Translation selection dialog
        if (showTranslationDialog) {
            TranslationSelectionDialog(
                availableTranslations = availableTranslations,
                currentTranslation = currentTranslation,
                onDismiss = { showTranslationDialog = false },
                onTranslationSelected = { translationCode ->
                    // Change the text display. The LaunchedEffect on currentTranslation
                    // takes care of switching (and restarting) playback audio to match.
                    viewModel.changeTranslation(translationCode, surahNumber)

                    val mappedAudioLanguage = mapTranslationCodeToAudioLanguage(translationCode)
                    if (mappedAudioLanguage != null) {
                        Toast.makeText(
                            context,
                            "Translation applied with ${getAudioLanguageDisplayName(mappedAudioLanguage)} audio",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Translation applied. Audio not available for ${viewModel.getTranslationName(translationCode)}, playing Arabic audio.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    showTranslationDialog = false
                },
                getTranslationDisplayName = { code -> viewModel.getTranslationName(code) }
            )
        }

        // Font selection dialog
        if (showFontDialog) {
            FontSelectionDialog(
                availableFonts = availableArabicFonts,
                currentFont = selectedArabicFont,
                onDismiss = { showFontDialog = false },
                onFontSelected = { fontName ->
                    viewModel.changeArabicFont(fontName)
                    Toast.makeText(
                        context,
                        "Arabic font changed to ${viewModel.getArabicFontDisplayName(fontName)}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showFontDialog = false
                },
                getFontDisplayName = { font -> viewModel.getArabicFontDisplayName(font) }
            )
        }

        // Reading settings — bottom sheet with a live preview strip. No scrim:
        // the ayah text stays visible above the sheet, and the preview row
        // re-renders the actual Arabic as the font and size change.
        androidx.compose.animation.AnimatedVisibility(
            visible = showFloatingToolbar,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(160)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(160)),
            modifier = Modifier.zIndex(10f),
        ) {
            // Tap-outside-to-dismiss layer with a light dim, so the sheet reads
            // as focused while the page behind stays visible for live preview.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showFloatingToolbar = false }
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showFloatingToolbar,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) + androidx.compose.animation.fadeIn(animationSpec = tween(200)),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            ) + androidx.compose.animation.fadeOut(animationSpec = tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f),
        ) {
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            androidx.activity.compose.BackHandler { showFloatingToolbar = false }
            // Which inline picker is expanded: "font", "language", or null.
            var expandedSection by remember { mutableStateOf<String?>(null) }
            // Sheet follows the finger while dragging the handle down; past a
            // threshold the drag dismisses the sheet.
            var sheetDragOffset by remember { mutableStateOf(0f) }
            // The content Column is scrollable, so raw drag events never reach a
            // parent gesture detector — the scroll gesture claims them. Instead we
            // join the nested-scroll chain: downward drag the content can't consume
            // pulls the sheet; release past the threshold dismisses it.
            val sheetDismissConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < 0f && sheetDragOffset > 0f) {
                            val consumed = kotlin.math.max(available.y, -sheetDragOffset)
                            sheetDragOffset += consumed
                            return Offset(0f, consumed)
                        }
                        return Offset.Zero
                    }
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        // Sheet follows the finger for the whole drag; the dismiss
                        // decision happens on release (onPreFling) so nothing pops
                        // out from under the finger.
                        if (available.y > 0f) {
                            sheetDragOffset += available.y
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        val offset = sheetDragOffset
                        if (offset > 150f) {
                            // Keep the offset — the exit animation continues the slide
                            // from where the finger released instead of jumping back up.
                            showFloatingToolbar = false
                            return available
                        }
                        if (offset > 0f) {
                            // Below threshold: settle back into place smoothly.
                            androidx.compose.animation.core.animate(
                                offset,
                                0f,
                                animationSpec = NiaMotion.standardTween(NiaMotion.Duration.MEDIUM_1)
                            ) { value, _ ->
                                sheetDragOffset = value
                            }
                            return available
                        }
                        return Velocity.Zero
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(sheetDismissConnection)
                    .graphicsLayer { translationY = sheetDragOffset.coerceAtLeast(0f) }
                    // Whole-sheet drag-to-dismiss. Runs after children in the Main
                    // pass, so it only sees drags the inner scroll/slider didn't
                    // consume — scrolling still scrolls; pulling down when the
                    // content is at its top drags the sheet away.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                sheetDragOffset = (sheetDragOffset + dragAmount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                // Keep the offset on dismiss so the exit animation
                                // continues from the finger's release point.
                                if (sheetDragOffset > 150f) showFloatingToolbar = false
                                else sheetDragOffset = 0f
                            },
                            onDragCancel = { sheetDragOffset = 0f },
                        )
                    },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Drag handle (whole sheet is draggable — see the Surface modifier).
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
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }

                    // Live preview — the actual first ayah, directly on the sheet.
                    Text(
                        text = (uiState as? SurahDetailUiState.Success)
                            ?.ayahs?.firstOrNull()?.text?.split("\n\n")?.getOrNull(0)
                            ?: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                        fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                        fontSize = arabicFontSize.sp,
                        lineHeight = (arabicFontSize * 1.7f).sp,
                        textAlign = when (textAlignment) {
                            "start" -> TextAlign.Start
                            "center" -> TextAlign.Center
                            "end" -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    // Text size — small A, slider, big A (Play Books style).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "A",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = arabicFontSize,
                            onValueChange = { newValue ->
                                val stepped = newValue.toInt()
                                if (stepped != arabicFontSize.toInt()) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                                viewModel.changeArabicFontSize(newValue.coerceIn(minFontSize, maxFontSize))
                            },
                            valueRange = minFontSize..maxFontSize,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "A",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    // Alignment — compact segmented icons.
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        val opts = listOf(
                            Triple(Icons.Default.FormatAlignLeft, "Left", "start"),
                            Triple(Icons.Default.FormatAlignCenter, "Center", "center"),
                            Triple(Icons.Default.FormatAlignRight, "Right", "end"),
                        )
                        opts.forEachIndexed { i, (icon, _, value) ->
                            SegmentedButton(
                                selected = textAlignment == value,
                                onClick = { viewModel.changeTextAlignment(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = opts.size),
                                icon = {},
                            ) {
                                Icon(icon, contentDescription = value, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Mushaf page — plain label + switch row.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleContinuousReadingMode() }
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Mushaf page",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = continuousReadingMode,
                            onCheckedChange = { viewModel.toggleContinuousReadingMode() },
                        )
                    }

                    // Inline translation — plain label + switch row.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.changeShowTranslation(!showTranslationInText) }
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Show translation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = showTranslationInText,
                            onCheckedChange = { viewModel.changeShowTranslation(it) },
                        )
                    }

                    // Tajweed — plain label + switch row.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!showTajweed && !viewModel.isTajweedAvailable) {
                                    Toast.makeText(context, "Tajweed data not downloaded yet. Please download it from Settings.", Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.changeTajweed(!showTajweed)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Tajweed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = showTajweed,
                            onCheckedChange = {
                                if (!showTajweed && !viewModel.isTajweedAvailable) {
                                    Toast.makeText(context, "Tajweed data not downloaded yet. Please download it from Settings.", Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.changeTajweed(it)
                                }
                            },
                        )
                    }

                    // Arabic font — value + chevron row.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedSection = if (expandedSection == "font") null else "font" }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Arabic font",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when (selectedArabicFont) {
                                "pdms_saleem" -> "Saleem"
                                "noor_e_hidayat" -> "Noor"
                                "thabit" -> "Thabit"
                                "uthmani_script" -> "Uthmani"
                                "indopak_script" -> "IndoPak"
                                else -> selectedArabicFont.replaceFirstChar { it.uppercase() }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(12.dp),
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = expandedSection == "font") {
                        Column {
                            availableArabicFonts.forEach { font ->
                                val fontSelected = selectedArabicFont == font
                                // Selected row gets a soft tinted pill — no radio circles.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (fontSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.changeArabicFont(font)
                                            expandedSection = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = when (font) {
                                            "pdms_saleem" -> "Saleem"
                                            "noor_e_hidayat" -> "Noor"
                                            "thabit" -> "Thabit"
                                            "uthmani_script" -> "Uthmani"
                                            "indopak_script" -> "IndoPak"
                                            else -> font.replaceFirstChar { it.uppercase() }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (fontSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    // Live sample so the reader can see the script style.
                                    Text(
                                        text = "بِسْمِ اللَّهِ",
                                        fontFamily = getArabicFontFamilyForSelection(font),
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        color = if (fontSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Translation language — value + chevron row.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedSection = if (expandedSection == "language") null else "language" }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Translation language",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when (currentTranslation) {
                                "ar" -> "Arabic"
                                "transliteration" -> "Transliteration"
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
                                else -> currentTranslation.uppercase(Locale.getDefault())
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(12.dp),
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = expandedSection == "language") {
                        Column {
                            availableTranslations.forEach { code ->
                                val langSelected = currentTranslation == code
                                // Selected row gets a soft tinted pill — no radio circles.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (langSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.changeTranslation(code, surahNumber)
                                            expandedSection = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = viewModel.getTranslationName(code),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (langSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    // Endonym so native speakers recognize their language.
                                    Text(
                                        text = when (code) {
                                            "ar" -> "العربية"
                                            "transliteration" -> "Bismillāh"
                                            "bn" -> "বাংলা"
                                            "zh" -> "中文"
                                            "en" -> "English"
                                            "es" -> "Español"
                                            "fr" -> "Français"
                                            "id" -> "Bahasa Indonesia"
                                            "ru" -> "Русский"
                                            "sv" -> "Svenska"
                                            "tr" -> "Türkçe"
                                            "ur" -> "اردو"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        color = if (langSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Word Study dialog
        if (showWordStudyDialog && wordStudyData != null) {
            com.starception.submission.feature.surah.WordStudyDialog(
                wordStudyData = wordStudyData!!,
                selectedArabicFont = selectedArabicFont,
                onDismiss = {
                    showWordStudyDialog = false
                    viewModel.clearWordStudy()
                }
            )
        }

        // Tafseer dialog
        if (showTafseerDialog && tafseerData != null) {
            com.starception.submission.feature.surah.TafseerDialog(
                tafseerData = tafseerData!!,
                selectedTafseerBook = selectedTafseerBook,
                selectedArabicFont = selectedArabicFont,
                onTafseerBookSelected = { book -> viewModel.selectTafseerBook(book) },
                onDismiss = {
                    showTafseerDialog = false
                    viewModel.clearTafseer()
                }
            )
        }

        // Tajweed Legend dialog
        if (showTajweedLegendDialog) {
            com.starception.submission.feature.surah.tajweed.TajweedLegendDialog(
                onDismiss = { showTajweedLegendDialog = false }
            )
        }

        // Floating animated chevron anchored at the bottom edge of the album
        // header (i.e., the boundary where the ayah view begins). When the
        // header is scrolled off-screen, the chevron sticks to the top of the
        // viewport. Hints that swiping up reveals more (surah info / full
        // Mushaf).
        // Swipe hints. Header visible: bouncing up-chevron at the header's
        // bottom edge (swipe up → Mushaf). Fullscreen Mushaf: a transient
        // "Swipe down" pill drops in from the top for a few seconds, and the
        // first entry also plays a scroll "peek" that physically reveals the
        // header's edge and settles back — demonstrating the gesture itself.
        // Nothing is anchored at the bottom: the page text runs to the nav
        // inset, so a bottom hint would overlap the last line or the system
        // gesture bar. Hidden while the reading-settings sheet is open.
        val peekDistancePx = with(LocalDensity.current) { 40.dp.toPx() }
        // How many times the labelled pill has been shown this visit — after
        // the second showing the hint starts life already collapsed to the
        // grabber so re-entries stop nagging.
        var swipeDownHintShows by remember { mutableStateOf(0) }
        LaunchedEffect(scrollState) {
            // One peek per screen visit, on the first entry into fullscreen
            // Mushaf. Deliberately NOT keyed on the fullscreen flag: the peek
            // itself makes the header visible mid-animation, and a keyed
            // effect would be cancelled right there, leaving the page stuck
            // half-scrolled.
            snapshotFlow {
                scrollState.layoutInfo.visibleItemsInfo.none { it.index == 0 }
            }.first { it }
            delay(600)
            if (!scrollState.isScrollInProgress) {
                scrollState.animateScrollBy(
                    -peekDistancePx,
                    NiaMotion.emphasizedTween(NiaMotion.Duration.MEDIUM_4),
                )
                delay(80)
                scrollState.animateScrollBy(
                    peekDistancePx,
                    NiaMotion.emphasizedTween(NiaMotion.Duration.LONG_2),
                )
            }
        }
        if (uiState is SurahDetailUiState.Success && !showFloatingToolbar) {
            val item0BottomPx = scrollState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == 0 }
                ?.let { it.offset + it.size }
            val headerVisible = item0BottomPx != null

            // One continuous hint object with three states. Fullscreen
            // Mushaf: a labelled "Swipe down" pill that collapses into the
            // 32x4dp grabber. When the user scrolls down and the header
            // appears, the SAME object rides the header boundary and the
            // up-chevron morphs OUT of the pill (container fades away, bare
            // chevron fades in) — not a separate hint popping in elsewhere.
            var hintExpanded by remember { mutableStateOf(false) }
            LaunchedEffect(headerVisible) {
                if (headerVisible) {
                    hintExpanded = false
                } else if (swipeDownHintShows < 2) {
                    hintExpanded = true
                    // Count a "showing" only after the pill has survived on
                    // screen for 1.5s — scroll bounces and the peek animation
                    // flip fullscreen on/off within milliseconds, and each
                    // flip would otherwise burn one of the two allowed
                    // showings unseen.
                    delay(1500)
                    swipeDownHintShows++
                    delay(2000)
                    hintExpanded = false
                }
            }
            // Touching the Quran text collapses the pill straight into the
            // grabber — the reader has started interacting, the hint's job
            // is done. Observed on the Initial pass without consuming, so
            // page turns, long-presses and scrolls behave exactly as before.
            if (hintExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.any { it.pressed }) {
                                        hintExpanded = false
                                        break
                                    }
                                }
                            }
                        },
                )
            }
            val isLabel = hintExpanded && !headerVisible
            val hintShape = RoundedCornerShape(percent = 50)
            val hintContainerColor by animateColorAsState(
                // Only the labelled pill has a container; the grabber and the
                // chevron are the SAME drawn stroke (see bend canvas below),
                // so the surface behind them stays transparent and the shape
                // itself carries the transform.
                targetValue = if (isLabel) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    Color.Transparent
                },
                animationSpec = NiaMotion.effectsSlow(),
                label = "mushafHintColor",
            )
            val hintBorderColor by animateColorAsState(
                targetValue = if (isLabel) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    Color.Transparent
                },
                animationSpec = NiaMotion.effectsSlow(),
                label = "mushafHintBorder",
            )
            val hintElevation by animateDpAsState(
                targetValue = if (isLabel) 8.dp else 0.dp,
                animationSpec = NiaMotion.effectsSlow(),
                label = "mushafHintElevation",
            )
            // One continuously-animated vertical position: docked 6dp into
            // the page's top strip in fullscreen, riding the header boundary
            // while the header is visible. A spring chases the target so the
            // flip between the two anchors never teleports — the same object
            // visibly travels while it folds/unfolds.
            val hintTargetOffset = if (headerVisible) {
                (with(LocalDensity.current) { item0BottomPx!!.toDp() } - 22.dp)
                    .coerceAtLeast(6.dp)
            } else {
                6.dp
            }
            val hintOffsetY by animateDpAsState(
                targetValue = hintTargetOffset,
                animationSpec = NiaMotion.spatialDefault(),
                label = "mushafHintOffset",
            )
            val positionModifier = Modifier.offset(y = hintOffsetY)
            // Fold fraction: 0 = flat grabber bar, 1 = up-chevron. Drives the
            // stroke geometry below AND the whole-object bounce/pulse. Hoisted
            // to the Surface level because Surface clips its content to the
            // stadium shape — bouncing the canvas INSIDE the surface sheared
            // the chevron's top off on every upward bounce.
            val bend by animateFloatAsState(
                targetValue = if (headerVisible) 1f else 0f,
                animationSpec = NiaMotion.spatialDefault(),
                label = "mushafHintBend",
            )
            val handleTransition = rememberInfiniteTransition(label = "mushafHandleHint")
            val bounceDp by handleTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "mushafHandleBounce",
            )
            val pulse by handleTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "mushafHandlePulse",
            )
            Surface(
                shape = hintShape,
                color = hintContainerColor,
                border = BorderStroke(1.dp, hintBorderColor),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(positionModifier)
                    .zIndex(10f)
                    .graphicsLayer {
                        // Bounce + pulse the WHOLE object (only once folded
                        // into the chevron) so the Surface's shape clip moves
                        // with the stroke instead of shearing it off.
                        translationY = bounceDp.dp.toPx() * bend
                        val pulseGate = ((bend - 0.85f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - (1f - pulse) * pulseGate
                    }
                    .shadow(
                        elevation = hintElevation,
                        shape = hintShape,
                        clip = false,
                        // Primary-tinted shadow — the default black-at-low-
                        // alpha shadow disappears on this cream palette.
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ),
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isLabel,
                    transitionSpec = {
                        (fadeIn(NiaMotion.effectsDefault()) togetherWith fadeOut(NiaMotion.effectsDefault()))
                            .using(
                                SizeTransform(clip = false) { _, _ ->
                                    NiaMotion.spatialDefault()
                                },
                            )
                    },
                    label = "mushafHintMorph",
                ) { label ->
                    if (label) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            // The chevron dips gently — same motion language
                            // as the bent-chevron handle on the header boundary.
                            val chevronTransition = rememberInfiniteTransition(label = "pillChevron")
                            val chevronDip by chevronTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "pillChevronDip",
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { translationY = chevronDip.dp.toPx() },
                            )
                            Text(
                                text = "Swipe down for surah info",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        // Grabber ↔ chevron as ONE drawn stroke: a horizontal
                        // 32dp bar whose midpoint lifts as the header comes
                        // into view, folding the pill into a ^ (and unfolding
                        // back into the flat pill on the way up) — a real
                        // geometric transform, not a crossfade. Fold fraction,
                        // bounce and pulse are hoisted to the Surface level
                        // (see graphicsLayer above) so the shape clip can't
                        // shear the bouncing chevron.
                        // Same theme color in both shapes — the flat grabber
                        // bar and the folded chevron are one object, so they
                        // share the brand primary rather than swapping to a
                        // neutral gray when flattened.
                        val strokeColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(width = 36.dp, height = 20.dp),
                        ) {
                            val cx = size.width / 2f
                            val halfWidth = lerp(16.dp.toPx(), 11.dp.toPx(), bend)
                            val peak = 9.dp.toPx() * bend
                            val baseY = size.height / 2f + peak / 2f
                            val stroke = lerp(4.dp.toPx(), 3.5.dp.toPx(), bend)
                            val path = Path().apply {
                                moveTo(cx - halfWidth, baseY)
                                lineTo(cx, baseY - peak)
                                lineTo(cx + halfWidth, baseY)
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = stroke,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPlayerTopBar(
    collapseProgress: Float,
    isCollapsed: Boolean,
    surahName: String,
    surahNameArabic: String,
    currentTranslation: String,
    isBookmarked: Boolean,
    selectedArabicFont: String,
    showTajweed: Boolean,
    onBackClick: () -> Unit,
    onTranslationClick: () -> Unit = {},
    onFontClick: () -> Unit = {},
    onTajweedClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAllSurahsClick: () -> Unit = onBackClick,
    modifier: Modifier = Modifier
) {
    // Smooth transition based on collapseProgress (0 = transparent, 1 = solid)
    // Toolbar becomes transparent as user scrolls up, solid as user scrolls down
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = collapseProgress)

    // Content color transitions from white (over album art) to onSurface (over solid background)
    val surfaceColor = MaterialTheme.colorScheme.onSurface
    val contentColor = Color(
        red = 1f + (surfaceColor.red - 1f) * collapseProgress,
        green = 1f + (surfaceColor.green - 1f) * collapseProgress,
        blue = 1f + (surfaceColor.blue - 1f) * collapseProgress,
        alpha = 1f
    )

    // Get short translation code for display
    val translationDisplay = when (currentTranslation) {
        "ar" -> "AR"
        "transliteration" -> "TR"
        "bn" -> "BN"
        "zh" -> "ZH"
        "en" -> "EN"
        "es" -> "ES"
        "fr" -> "FR"
        "id" -> "ID"
        "ru" -> "RU"
        "sv" -> "SV"
        "tr" -> "TR"
        "ur" -> "UR"
        else -> "??"
    }

    // Dynamic toolbar layout based on camera cutout
    // Measure available space after camera cutout and fit as many icons as possible

    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

    // Get camera cutout bounds
    val cutoutRightDp = remember(view) {
        val displayCutout = view.rootWindowInsets?.displayCutout
        if (displayCutout != null && displayCutout.boundingRects.isNotEmpty()) {
            // Find the rightmost edge of any cutout
            val maxRight = displayCutout.boundingRects.maxOfOrNull { it.right } ?: 0
            with(density) { maxRight.toDp() }
        } else {
            0.dp // No cutout
        }
    }

    // Calculate available width for icons on the right side
    // Screen width - cutout right edge - some padding
    val availableWidthDp = screenWidthDp - cutoutRightDp - 16.dp

    // Define icon sizes
    val iconButtonSize = 44.dp
    val translationButtonSize = 44.dp

    // Calculate how many icons can fit (More button is always shown)
    // Icons: Translation (44dp), Bookmark (44dp), Font (44dp), Tajweed (44dp), More (44dp)
    val moreButtonSize = 44.dp
    val remainingWidth = availableWidthDp - moreButtonSize
    val maxIconsThatFit = (remainingWidth / iconButtonSize).toInt().coerceAtLeast(0)

    // Translation and font are accessible via the Reading Settings side sheet
    // (More menu); keep them off the toolbar so it stays uncluttered.
    val showTranslation = false
    val showBookmark = maxIconsThatFit >= 2
    val showFont = false
    val showTajweedButton = maxIconsThatFit >= 4

    Surface(
        color = backgroundColor,
        tonalElevation = (4 * collapseProgress).dp, // Smooth elevation transition
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp) // Minimal top padding since status bar is hidden by immersive mode
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT SIDE - Back button only (floating surah name will animate here)
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.15f)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = contentColor
                    )
                }
            }

            // RIGHT SIDE - Dynamic icons based on available space after camera cutout
            // Shows as many icons as can fit, rest accessible via More menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (true) {
                // Translation button - shown if space allows (priority 1)
                if (showTranslation) {
                    Surface(
                        onClick = onTranslationClick,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = contentColor.copy(alpha = 0.12f),
                        contentColor = contentColor
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = translationDisplay,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bookmark button - shown if space allows (priority 2)
                if (showBookmark) {
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Font button - shown if space allows (priority 3)
                if (showFont) {
                    val fontDisplay = when (selectedArabicFont) {
                        "pdms_saleem" -> "ص"
                        "noor_e_hidayat" -> "ن"
                        "thabit" -> "ث"
                        "uthmani_script" -> "ع"
                        "indopak_script" -> "پ"
                        else -> "F"
                    }
                    Surface(
                        onClick = onFontClick,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = contentColor.copy(alpha = 0.12f),
                        contentColor = contentColor
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = fontDisplay,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tajweed toggle - shown if space allows (priority 4)
                if (showTajweedButton) {
                    IconButton(
                        onClick = onTajweedClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (showTajweed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                            contentDescription = if (showTajweed) "Disable Tajweed" else "Enable Tajweed",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                } // end hidden actions block

                // More options menu - always shown (floating toolbar has all options)
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun AlbumPlayerContent(
    surah: Surah,
    ayahs: List<Ayah>,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    collapseProgress: Float,
    showMusicPlayer: Boolean,
    isPlaying: Boolean,
    currentProgress: Float,
    currentVolume: Float,
    currentPlayingSurahNumber: Int,
    currentPlayingSurah: Surah?, // Passed from parent for proper recomposition
    currentPlayingAyahs: List<Ayah>?, // Passed from parent for proper recomposition
    showFabVisible: Boolean,
    selectedArabicFont: String,
    arabicFontSize: Float,
    textAlignment: String,
    currentTranslation: String = "ar",
    showTranslationInText: Boolean,
    showBismillahRow: Boolean,
    showTajweed: Boolean,
    tajweedAnnotations: Map<Int, List<com.starception.submission.feature.surah.tajweed.TajweedAnnotation>>,
    onToggleTajweed: () -> Unit,
    onToggleTranslation: () -> Unit,
    onCycleAlignment: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onAyahClick: (Ayah) -> Unit,
    onFabClick: () -> Unit,
    onCollapseMusicPlayer: () -> Unit = {},
    onWordStudyClick: (Int) -> Unit = {},
    onTafseerClick: (Int) -> Unit = {},
    onPlayAyahClick: (Int) -> Unit = {},
    topics: List<com.starception.submission.core.topicsdatabase.Topic> = emptyList(),
    onTopicClick: (String) -> Unit = {},
    tafseerData: com.starception.submission.core.qurandatabase.QuranAyahTafseer? = null,
    wordStudyData: com.starception.submission.core.qurandatabase.AyahMeaningsItem? = null,
    selectedTafseerBook: String = "saadi",
    onTafseerBookSelected: (String) -> Unit = {},
    // Tafseer translation parameters
    tafseerTranslationLanguage: String = "ar",
    tafseerTranslationProvider: String = "auto",
    translatedTafseerSaadi: String? = null,
    translatedTafseerMoysar: String? = null,
    translatedTafseerBaghawi: String? = null,
    translatedWordMeanings: String? = null,
    isTafseerTranslating: Boolean = false,
    availableTafseerTranslations: List<String> = emptyList(),
    availableTafseerProviders: List<Pair<String, String>> = emptyList(),
    onTafseerLanguageChange: (String) -> Unit = {},
    onTafseerProviderChange: (String) -> Unit = {},
    getTafseerTranslationName: (String) -> String = { it },
    isLandscape: Boolean = false,
    onFontSizeChange: (Float) -> Unit = {},
    minFontSize: Float = 14f,
    maxFontSize: Float = 60f,
    onToggleContinuousReadingMode: () -> Unit = {},
    continuousReadingMode: Boolean = false,
    initialMushafPage: Int = 0,
    onMushafPageChange: (Int) -> Unit = {},
    /** numberInSurah of the ayah currently being recited (audio sync), or null. */
    currentRecitingAyah: Int? = null,
    /** numberInSurah to softly tint after a search-driven scrollToAyah jump,
     *  so the user can see which verse the link picked. */
    highlightedAyahNumber: Int? = null,
    /** Search-driven jump target — forwarded to the Mushaf pager so it can
     *  snap to the page containing this ayah. 0 = no jump. */
    scrollToAyahForMushafJump: Int = 0,
    modifier: Modifier = Modifier
) {
    // Use current playing surah/ayahs if available, otherwise use original
    val displaySurah = currentPlayingSurah ?: surah
    val displayAyahs = currentPlayingAyahs ?: ayahs

    val quranRepository = hiltViewModel<QuranRepositoryHolder>().repository
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Bottom sheet state for ayah options
    var selectedAyahForOptions by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    // Track favourite ayahs - loaded from repository (persisted in database)
    var favouriteAyahs by remember { mutableStateOf(setOf<Int>()) }

    // Load favourite ayahs for this surah from repository
    LaunchedEffect(surah.number) {
        favouriteAyahs = quranRepository.getFavouriteAyahsForSurah(surah.number)
        android.util.Log.d("QuranAlbumPlayer_FAVOURITE", "📥 LOADED | surah=${surah.number} | count=${favouriteAyahs.size} | ayahs=$favouriteAyahs")
    }

    // Track ayahs with notes - for showing note indicator
    var ayahsWithNotes by remember { mutableStateOf(setOf<Int>()) }

    // Bottom sheet mode - supports menu, notes, tafseer, word study
    var bottomSheetMode by remember { mutableStateOf("menu") } // "menu", "notes", "tafseer", "wordstudy"
    var noteText by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<com.starception.submission.core.qurandatabase.AyahNoteEntity?>(null) }
    var existingNotes by remember { mutableStateOf<List<com.starception.submission.core.qurandatabase.AyahNoteEntity>>(emptyList()) }
    var showDeleteNoteConfirmation by remember { mutableStateOf<com.starception.submission.core.qurandatabase.AyahNoteEntity?>(null) }

    // Load ayahs with notes for this surah
    LaunchedEffect(surah.number) {
        quranRepository.getAyahsWithNotesInSurah(surah.number).collect { notes ->
            ayahsWithNotes = notes
            android.util.Log.d("QuranAlbumPlayer_NOTE", "📥 LOADED | surah=${surah.number} | count=${notes.size} | ayahs=$notes")
        }
    }

    // Calculate total items for scrollbar state
    val totalItems = remember(displayAyahs, showMusicPlayer) {
        1 + // AlbumHeader
        (if (showMusicPlayer) 1 else 0) + // MusicPlayerControls
        (if (!showMusicPlayer) 1 else 0) + // AlbumInfoCard
        displayAyahs.size // Ayah items
    }

    val scrollbarState = scrollState.scrollbarState(
        itemsAvailable = totalItems,
    )

    // For pinch-to-zoom font size adjustment
    val view = LocalView.current
    var currentFontSizeState by remember { mutableStateOf(arabicFontSize) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    // Wobble offset: when the app-level PullToSyncContainer pushes content
    // down (media playing, prayer alert, sync), shrink the Mushaf height so
    // it fits the reduced viewport instead of overflowing — which previously
    // pulled the page-number footer up into the visible area and left a big
    // gap at the top.
    val wobbleIntensity = com.starception.submission.ui.LocalWobbleIntensity.current
    // Must match PullToSyncContainer's banner reveal height (smaller in landscape), else
    // the page is sized for a taller push than actually shows and ends up ~156px short of
    // filling the viewport — which pins the scroll and leaves the album header's bottom
    // (its InfoCard chips) peeking above the page.
    val mushafBannerRevealDp = if (isLandscape) 130f else 220f
    val mushafHeight = (screenHeightDp - (wobbleIntensity * mushafBannerRevealDp).dp)
        .coerceAtLeast(200.dp)

    // Update local state when arabicFontSize changes externally (e.g. +/- buttons)
    LaunchedEffect(arabicFontSize) {
        currentFontSizeState = arabicFontSize
    }

    // Auto-scroll to Mushaf item whenever Mushaf mode is active and content is loaded.
    // Keys on mode + ayah count so it fires on first open (default=true) once ayahs
    // arrive, and again if the user toggles the mode on manually. Also keyed on
    // [isLandscape]: a rotation re-lays-out the list and drops it back to item 0, which
    // would re-show the toolbar — re-snapping to the page keeps the immersive reading
    // view (toolbar hidden until the user scrolls up) consistent in both orientations.
    LaunchedEffect(continuousReadingMode, displayAyahs.size, isLandscape) {
        if (continuousReadingMode && displayAyahs.isNotEmpty()) {
            // Item 0 = AlbumHeader+InfoCard, item 1 = MushafPagerView.
            // Small delay lets Compose finish layout before scrolling.
            kotlinx.coroutines.delay(80)
            scrollState.animateScrollToItem(
                index = 1,
                scrollOffset = 0
            )
        }
    }

    // Persist per-Surah reading progress: which ayah is currently first-visible.
    // Skipped while in Mushaf mode (HorizontalPager — first-visible-ayah doesn't
    // make sense). Ayah items use the key `"${displaySurah.number}_<ayahNumber>"`
    // (defined where items() is called), so we parse that to find the ayah.
    LaunchedEffect(displaySurah.number, displayAyahs.size, continuousReadingMode) {
        if (continuousReadingMode || displayAyahs.isEmpty()) return@LaunchedEffect
        val keyPrefix = "${displaySurah.number}_"
        val total = displayAyahs.size
        androidx.compose.runtime.snapshotFlow {
            scrollState.layoutInfo.visibleItemsInfo
                .firstNotNullOfOrNull { info ->
                    (info.key as? String)?.takeIf { it.startsWith(keyPrefix) }
                        ?.substringAfter(keyPrefix)?.toIntOrNull()
                }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .debounce(800L)
            .collect { ayahNumber ->
                com.starception.submission.core.ui.SurahReadingProgressRepository.update(
                    context = context,
                    surahNumber = displaySurah.number,
                    currentAyahNumber = ayahNumber,
                    totalAyahs = total,
                )
            }
    }

    // Strong magnetic snap: any scroll immediately snaps to either top or Mushaf.
    // Threshold is just 1px — the snap fling behavior handles the rest.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress && continuousReadingMode && displayAyahs.isNotEmpty()) {
            val firstItem = scrollState.firstVisibleItemIndex
            val offset = scrollState.firstVisibleItemScrollOffset

            when {
                firstItem == 0 && offset > 0 -> {
                    scrollState.animateScrollToItem(
                        index = 1,
                        scrollOffset = 0
                    )
                }
                firstItem >= 1 && offset > 0 -> {
                    scrollState.animateScrollToItem(
                        index = 1,
                        scrollOffset = 0
                    )
                }
                firstItem >= 2 -> {
                    scrollState.animateScrollToItem(
                        index = 1,
                        scrollOffset = 0
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(minFontSize, maxFontSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    var previousDistance = 0f
                    var isPinching = false
                    // Snapshot of font size at pinch start — scale is relative to this
                    var pinchStartFontSize = currentFontSizeState

                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }

                        if (pointers.size >= 2) {
                            val pointer1 = pointers[0]
                            val pointer2 = pointers[1]

                            val dx = pointer1.position.x - pointer2.position.x
                            val dy = pointer1.position.y - pointer2.position.y
                            val currentDistance = sqrt(dx * dx + dy * dy)

                            if (!isPinching) {
                                pinchStartFontSize = currentFontSizeState
                                isPinching = true
                            } else if (previousDistance > 0f) {
                                val scale = currentDistance / previousDistance
                                val newFontSize = (currentFontSizeState * scale).coerceIn(minFontSize, maxFontSize)

                                if (newFontSize != currentFontSizeState) {
                                    currentFontSizeState = newFontSize
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                                    // Both modes: LazyColumn only re-layouts visible chunks — cheap
                                    onFontSizeChange(newFontSize)
                                }
                            }

                            previousDistance = currentDistance
                            pointers.forEach { it.consume() }
                        } else {
                            if (isPinching) onFontSizeChange(currentFontSizeState)
                            isPinching = false
                            previousDistance = 0f
                        }
                    } while (event.changes.any { it.pressed })
                    if (isPinching) onFontSizeChange(currentFontSizeState)
                }
            }
    ) {
        // Snap fling behavior for Mushaf mode - snaps to item boundaries
        val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = scrollState)

        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(top = 0.dp), // No padding needed (status bar is hidden)
            flingBehavior = if (continuousReadingMode) snapFlingBehavior else androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
            modifier = Modifier.fillMaxSize()
        ) {
        // Album Header with either FAB+Info Card OR Music Player Controls
        item {
            // Calculate scroll offset for parallax effect
            val parallaxScrollOffset = if (scrollState.firstVisibleItemIndex == 0) {
                scrollState.firstVisibleItemScrollOffset
            } else {
                0
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh) // Eliminate white gap
            ) {
                Column {
                    AlbumHeader(
                        surah = surah,
                        isLandscape = isLandscape,
                        scrollOffset = parallaxScrollOffset
                    )

                    // Fixed-height container to prevent FAB position jump during transitions
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 140.dp else 196.dp) // Reduced height in landscape
                    ) {
                        // Professional animated transition between AlbumInfoCard and MusicPlayerControls
                        // Using AnimatedContent for smooth fade + slide transitions
                        androidx.compose.animation.AnimatedContent(
                            targetState = showMusicPlayer,
                            transitionSpec = {
                                if (targetState) {
                                    // Expanding to Music Player: slide up + fade in
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
                                    // Collapsing to Info Card: slide down + fade in
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
                            label = "Player Controls Transition",
                            modifier = Modifier.fillMaxSize()
                        ) { showPlayer ->
                            if (showPlayer) {
                                // Music Player Controls - show current playing surah name
                                MusicPlayerControls(
                                    isPlaying = isPlaying,
                                    currentProgress = currentProgress,
                                    currentVolume = currentVolume,
                                    surahName = displaySurah.nameEnglish,
                                    surahNameArabic = displaySurah.nameArabic,
                                    selectedArabicFont = selectedArabicFont,
                                    onPlayPauseClick = onPlayPauseClick,
                                    onRewindClick = onRewindClick,
                                    onForwardClick = onForwardClick,
                                    onVolumeChange = onVolumeChange,
                                    onCollapse = onCollapseMusicPlayer
                                )
                            } else {
                                // Album Info Card - show current playing surah info
                                AlbumInfoCard(
                                    surah = displaySurah,
                                    selectedArabicFont = selectedArabicFont,
                                    collapseProgress = collapseProgress,
                                    topics = topics,
                                    onTopicClick = onTopicClick,
                                    courseCompletionInfo = CourseProgressTracker.getSurahCourseCompletion(context, surah.number),
                                )
                            }
                        }
                    }
                }

                // Floating Ayahs/Mushaf reading-mode toggle — hidden; mode is
                // accessible via the More menu's floating bottom toolbar.
                androidx.compose.animation.AnimatedVisibility(
                    visible = false,
                    enter = scaleIn(
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit = scaleOut(
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 300)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = (-168.dp))
                        .padding(start = 12.dp)
                ) {
                    ReadingModeToggle(
                        isMushafMode = continuousReadingMode,
                        onToggle = onToggleContinuousReadingMode
                    )
                }

                // FAB positioned with more overlap on the info card
                // Shows minimize icon when player controls are visible, play/pause when info card is showing
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
                        .offset(y = (-168.dp)) // Position FAB lower with more overlap on info card (75% on info card, 25% on artwork)
                        .padding(end = 12.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (showMusicPlayer) {
                                // When player controls are showing, minimize/collapse the player
                                onCollapseMusicPlayer()
                            } else {
                                // When info card is showing, control play/pause
                                onFabClick()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = when {
                                showMusicPlayer -> Icons.Default.CallReceived // Minimize icon (arrow into box) when player controls are visible
                                isPlaying -> Icons.Default.Pause // Pause when playing
                                else -> Icons.Default.PlayArrow // Play when paused
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

        // Ayah content — Mushaf pager in Mushaf mode, individual ayah items otherwise
        if (continuousReadingMode) {
            item(key = "mushaf_pager_${displaySurah.number}") {
                MushafPagerView(
                    ayahs = displayAyahs,
                    arabicFont = selectedArabicFont,
                    arabicFontSize = arabicFontSize,
                    showTajweed = showTajweed,
                    tajweedAnnotations = tajweedAnnotations,
                    showBismillah = showBismillahRow,
                    textAlignment = textAlignment,
                    translationCode = currentTranslation,
                    parentScrollState = scrollState,
                    initialPage = initialMushafPage,
                    surahNameArabic = displaySurah.nameArabic,
                    surahNameEnglish = displaySurah.nameEnglish,
                    scrollToAyah = scrollToAyahForMushafJump,
                    highlightedAyahNumber = highlightedAyahNumber,
                    onAyahLongPress = { ayahNumber ->
                        selectedAyahForOptions = ayahNumber
                        showBottomSheet = true
                    },
                    onPageChange = { current, _ ->
                        onMushafPageChange(current)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mushafHeight)
                )
            }
        } else {
            if (showBismillahRow) {
                item(key = "bismillah") {
                    BismillahRow(
                        arabicFont = selectedArabicFont,
                        arabicFontSize = arabicFontSize,
                        textAlignment = textAlignment
                    )
                }
            }
            items(
                items = displayAyahs,
                key = { "${displaySurah.number}_${it.numberInSurah}" }
            ) { ayah ->
                AyahTrackItem(
                    ayah = ayah,
                    arabicFont = selectedArabicFont,
                    arabicFontSize = arabicFontSize,
                    textAlignment = textAlignment,
                    showTranslation = showTranslationInText,
                    showTajweed = showTajweed,
                    tajweedAnnotations = tajweedAnnotations[ayah.numberInSurah],
                    isFavourite = ayah.numberInSurah in favouriteAyahs,
                    hasNote = ayah.numberInSurah in ayahsWithNotes,
                    isReciting = currentRecitingAyah == ayah.numberInSurah,
                    isHighlighted = highlightedAyahNumber == ayah.numberInSurah,
                    onClick = { onAyahClick(ayah) },
                    onLongPress = {
                        selectedAyahForOptions = ayah.numberInSurah
                        showBottomSheet = true
                    },
                    onDoubleTap = {
                        val ayahNumber = ayah.numberInSurah
                        val isFavourite = ayahNumber in favouriteAyahs
                        val newFavouriteStatus = !isFavourite

                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            quranRepository.setAyahFavourite(surah.number, ayahNumber, newFavouriteStatus)
                            favouriteAyahs = if (newFavouriteStatus) {
                                favouriteAyahs + ayahNumber
                            } else {
                                favouriteAyahs - ayahNumber
                            }
                            val action = if (newFavouriteStatus) "added to" else "removed from"
                            android.widget.Toast.makeText(context, "Ayah $ayahNumber $action favourites", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

        // Draggable scrollbar - matches ForYou tab and other pages
        scrollState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMoved = scrollState.rememberDraggableScroller(
                itemsAvailable = totalItems,
            ),
        )

    // Material Design 3 Expressive Bottom Sheet for Ayah options (with integrated notes)
    if (showBottomSheet && selectedAyahForOptions != null) {
        val selectedAyah = ayahs.find { it.numberInSurah == selectedAyahForOptions }
        val context = LocalContext.current

        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                selectedAyahForOptions = null
                bottomSheetMode = "menu"
                noteText = ""
                editingNote = null
                existingNotes = emptyList()
            },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            dragHandle = null
        ) {
            // Wrap everything in a Box with padding to create margins from screen edges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Drag handle inside the surface
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Content with padding - switches between menu, notes, tafseer, wordstudy modes
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            // Animated content switch between modes
                            AnimatedContent(
                                targetState = bottomSheetMode,
                                transitionSpec = {
                                    if (targetState != "menu") {
                                        // Entering sub-mode
                                        slideInHorizontally { it } + fadeIn() togetherWith
                                            slideOutHorizontally { -it } + fadeOut()
                                    } else {
                                        // Returning to menu
                                        slideInHorizontally { -it } + fadeIn() togetherWith
                                            slideOutHorizontally { it } + fadeOut()
                                    }
                                },
                                label = "BottomSheetContent"
                            ) { mode ->
                                when (mode) {
                                    "notes" -> {
                                    // Notes UI integrated into bottom sheet
                                    BottomSheetNotesContent(
                                        surahNumber = surah.number,
                                        ayahNumber = selectedAyahForOptions!!,
                                        surahName = surah.nameEnglish,
                                        existingNotes = existingNotes,
                                        noteText = noteText,
                                        editingNote = editingNote,
                                        onNoteTextChange = { noteText = it },
                                        onEditNote = { note ->
                                            editingNote = note
                                            noteText = note.noteText
                                        },
                                        onCancelEdit = {
                                            editingNote = null
                                            noteText = ""
                                        },
                                        onSaveNote = {
                                            if (noteText.isNotBlank()) {
                                                scope.launch {
                                                    if (editingNote != null) {
                                                        quranRepository.updateAyahNote(
                                                            editingNote!!.copy(
                                                                noteText = noteText.trim(),
                                                                updatedAt = System.currentTimeMillis()
                                                            )
                                                        )
                                                    } else {
                                                        quranRepository.addAyahNote(surah.number, selectedAyahForOptions!!, noteText.trim())
                                                    }
                                                    // Refresh notes list
                                                    existingNotes = quranRepository.getNotesForAyah(surah.number, selectedAyahForOptions!!)
                                                    noteText = ""
                                                    editingNote = null
                                                }
                                            }
                                        },
                                        onDeleteNote = { note ->
                                            showDeleteNoteConfirmation = note
                                        },
                                        onBack = {
                                            bottomSheetMode = "menu"
                                            noteText = ""
                                            editingNote = null
                                        }
                                    )
                                    }
                                    "tafseer" -> {
                                        // Tafseer content in bottom sheet - use passed-in data
                                        tafseerData?.let { data ->
                                            BottomSheetTafseerContent(
                                                tafseerData = data,
                                                selectedTafseerBook = selectedTafseerBook,
                                                selectedArabicFont = selectedArabicFont,
                                                onTafseerBookSelected = onTafseerBookSelected,
                                                // Translation parameters
                                                selectedLanguage = tafseerTranslationLanguage,
                                                selectedProvider = tafseerTranslationProvider,
                                                translatedSaadi = translatedTafseerSaadi,
                                                translatedMoysar = translatedTafseerMoysar,
                                                translatedBaghawi = translatedTafseerBaghawi,
                                                translatedWordMeanings = translatedWordMeanings,
                                                isTranslating = isTafseerTranslating,
                                                availableLanguages = availableTafseerTranslations,
                                                availableProviders = availableTafseerProviders,
                                                onLanguageChange = onTafseerLanguageChange,
                                                onProviderChange = onTafseerProviderChange,
                                                getLanguageName = getTafseerTranslationName,
                                                onBack = {
                                                    bottomSheetMode = "menu"
                                                }
                                            )
                                        } ?: run {
                                            // Show loading indicator while data is being fetched
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }
                                    }
                                    "wordstudy" -> {
                                        // Word Study content in bottom sheet - use passed-in data
                                        wordStudyData?.let { data ->
                                            BottomSheetWordStudyContent(
                                                wordStudyData = data,
                                                selectedArabicFont = selectedArabicFont,
                                                onBack = {
                                                    bottomSheetMode = "menu"
                                                }
                                            )
                                        } ?: run {
                                            // Show loading indicator while data is being fetched
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }
                                    }
                                    else -> {
                                    // Actions list (menu mode)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        BottomSheetOption(
                                            icon = Icons.Default.PlayArrow,
                                            title = "Play Ayah",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                selectedAyahForOptions?.let { ayahNumber ->
                                                    onPlayAyahClick(ayahNumber)
                                                }
                                                showBottomSheet = false
                                            }
                                        )

                                        BottomSheetOption(
                                            icon = if (selectedAyahForOptions in favouriteAyahs) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            title = if (selectedAyahForOptions in favouriteAyahs) "Remove Favourite" else "Add Favourite",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                selectedAyahForOptions?.let { ayahNumber ->
                                                    val isFavourite = ayahNumber in favouriteAyahs
                                                    val newFavouriteStatus = !isFavourite

                                                    // Update database
                                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                        quranRepository.setAyahFavourite(surah.number, ayahNumber, newFavouriteStatus)

                                                        // Update UI state
                                                        favouriteAyahs = if (newFavouriteStatus) {
                                                            favouriteAyahs + ayahNumber
                                                        } else {
                                                            favouriteAyahs - ayahNumber
                                                        }

                                                        val action = if (newFavouriteStatus) "added to" else "removed from"
                                                        android.widget.Toast.makeText(context, "Ayah $ayahNumber $action favourites", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                showBottomSheet = false
                                            }
                                        )

                                        // Add Note option - now switches to notes mode in same sheet
                                        val hasExistingNote = selectedAyahForOptions?.let { it in ayahsWithNotes } == true
                                        BottomSheetOption(
                                            icon = if (hasExistingNote) Icons.Default.Edit else Icons.Default.NoteAdd,
                                            title = if (hasExistingNote) "View Note" else "Add Note",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                // Load existing notes for this ayah
                                                scope.launch {
                                                    selectedAyahForOptions?.let { ayahNum ->
                                                        existingNotes = quranRepository.getNotesForAyah(surah.number, ayahNum)
                                                    }
                                                }
                                                // Switch to notes mode in same sheet
                                                bottomSheetMode = "notes"
                                            }
                                        )

                                        BottomSheetOption(
                                            icon = Icons.Default.ContentCopy,
                                            title = "Copy",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                selectedAyah?.let { ayah ->
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Ayah", ayah.text)
                                                    clipboard.setPrimaryClip(clip)
                                                    android.widget.Toast.makeText(context, "Ayah copied", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                showBottomSheet = false
                                            }
                                        )

                                        BottomSheetOption(
                                            icon = Icons.Default.Share,
                                            title = "Share",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                selectedAyah?.let { ayah ->
                                                    val shareText = "${surah.nameEnglish} ${selectedAyahForOptions}\n\n${ayah.text}"
                                                    val shareIntent = android.content.Intent().apply {
                                                        action = android.content.Intent.ACTION_SEND
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Ayah"))
                                                }
                                                showBottomSheet = false
                                            }
                                        )

                                        BottomSheetOption(
                                            icon = Icons.Default.MenuBook,
                                            title = "Tafseer",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                // Load tafseer via callback and switch to tafseer mode
                                                selectedAyahForOptions?.let { ayahNum ->
                                                    onTafseerClick(ayahNum)
                                                }
                                                bottomSheetMode = "tafseer"
                                            }
                                        )

                                        BottomSheetOption(
                                            icon = Icons.Default.Book,
                                            title = "Word Study",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                // Load word study via callback and switch to wordstudy mode
                                                selectedAyahForOptions?.let { ayahNum ->
                                                    onWordStudyClick(ayahNum)
                                                }
                                                bottomSheetMode = "wordstudy"
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
        }

        // Delete confirmation dialog for notes
        if (showDeleteNoteConfirmation != null) {
            AlertDialog(
                onDismissRequest = { showDeleteNoteConfirmation = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Delete Note?") },
                text = { Text("This note will be permanently removed.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteNoteConfirmation?.let { note ->
                                scope.launch {
                                    quranRepository.deleteAyahNote(note)
                                    existingNotes = quranRepository.getNotesForAyah(surah.number, selectedAyahForOptions!!)
                                    if (editingNote?.id == note.id) {
                                        editingNote = null
                                        noteText = ""
                                    }
                                }
                            }
                            showDeleteNoteConfirmation = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteNoteConfirmation = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun BottomSheetOption(
    icon: ImageVector,
    title: String,
    description: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // YouTube-style clean list item
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simple icon (no background container)
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            // Text only (no description)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Notes content integrated into bottom sheet
 * Shows note input field and existing notes list
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BottomSheetNotesContent(
    surahNumber: Int,
    ayahNumber: Int,
    surahName: String,
    existingNotes: List<com.starception.submission.core.qurandatabase.AyahNoteEntity>,
    noteText: String,
    editingNote: com.starception.submission.core.qurandatabase.AyahNoteEntity?,
    onNoteTextChange: (String) -> Unit,
    onEditNote: (com.starception.submission.core.qurandatabase.AyahNoteEntity) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveNote: () -> Unit,
    onDeleteNote: (com.starception.submission.core.qurandatabase.AyahNoteEntity) -> Unit,
    onBack: () -> Unit
) {
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (editingNote != null) "Edit Note" else "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$surahName - Ayah $ayahNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Note input field
        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 120.dp),
            placeholder = {
                Text(
                    "Write your thoughts...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editingNote != null) {
                TextButton(onClick = onCancelEdit) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            FilledTonalButton(
                onClick = {
                    keyboardController?.hide()
                    onSaveNote()
                },
                enabled = noteText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (editingNote != null) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (editingNote != null) "Update" else "Save",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Existing notes list
        if (existingNotes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            // Section header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notes,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Your Notes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${existingNotes.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                existingNotes.forEach { note ->
                    val isEditing = editingNote?.id == note.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isEditing)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = if (isEditing) 4.dp else 1.dp,
                        shadowElevation = if (isEditing) 2.dp else 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isEditing)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Note icon on the left
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isEditing)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isEditing)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            // Note content
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = note.noteText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isEditing)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Timestamp with icon
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = dateFormat.format(java.util.Date(note.updatedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            // Action buttons - tonal style
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { onEditNote(note) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (isEditing)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isEditing)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = { onDeleteNote(note) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
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
 * Tafseer content for bottom sheet - Professional design with translation support
 */
@Composable
private fun BottomSheetTafseerContent(
    tafseerData: com.starception.submission.core.qurandatabase.QuranAyahTafseer,
    selectedTafseerBook: String,
    selectedArabicFont: String,
    onTafseerBookSelected: (String) -> Unit,
    // Translation parameters
    selectedLanguage: String = "ar",
    selectedProvider: String = "auto",
    translatedSaadi: String? = null,
    translatedMoysar: String? = null,
    translatedBaghawi: String? = null,
    translatedWordMeanings: String? = null,
    isTranslating: Boolean = false,
    availableLanguages: List<String> = emptyList(),
    availableProviders: List<Pair<String, String>> = emptyList(),
    onLanguageChange: (String) -> Unit = {},
    onProviderChange: (String) -> Unit = {},
    getLanguageName: (String) -> String = { it },
    onBack: () -> Unit
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Professional header with icon badge and language selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Tafseer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${tafseerData.surahNameArabic} · Ayah ${tafseerData.ayahNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = getArabicFontFamilyForSelection(selectedArabicFont)
                    )
                }
            }

            // Language selector button
            Surface(
                onClick = { showLanguageDialog = true },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Select Language",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = selectedLanguage.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Elegant Arabic Ayah card with subtle gradient border
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tafseerData.ayahText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                        fontSize = 22.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Modern segmented button style tabs
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "saadi" to "As-Sa'di",
                    "moysar" to "Al-Moyassar",
                    "baghawi" to "Al-Baghawi"
                ).forEach { (code, name) ->
                    val isSelected = selectedTafseerBook == code
                    Surface(
                        onClick = { onTafseerBookSelected(code) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Get the appropriate text - translated or original
        val originalText = when (selectedTafseerBook) {
            "saadi" -> tafseerData.tafseerSaadi
            "moysar" -> tafseerData.tafseerMoysar
            "baghawi" -> tafseerData.tafseerBaghawi
            else -> ""
        }

        val displayText = if (selectedLanguage != "ar") {
            when (selectedTafseerBook) {
                "saadi" -> translatedSaadi ?: originalText
                "moysar" -> translatedMoysar ?: originalText
                "baghawi" -> translatedBaghawi ?: originalText
                else -> originalText
            }
        } else {
            originalText
        }

        val displayWordMeanings = if (selectedLanguage != "ar" && translatedWordMeanings != null) {
            translatedWordMeanings
        } else {
            tafseerData.ayahMeanings
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (displayText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Show language indicator when translated
                        if (selectedLanguage != "ar") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = getLanguageName(selectedLanguage),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = if (selectedLanguage == "ar") getArabicFontFamilyForSelection(selectedArabicFont) else null,
                                fontSize = 15.sp,
                                lineHeight = 26.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (displayWordMeanings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Word Meanings",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = displayWordMeanings,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = if (selectedLanguage == "ar") getArabicFontFamilyForSelection(selectedArabicFont) else null,
                                lineHeight = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }

    // Language and Provider selection dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Translation Settings") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
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
                    items(
                        items = availableProviders,
                        key = { it.first }
                    ) { (providerCode, providerName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onProviderChange(providerCode)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = providerCode == selectedProvider,
                                onClick = { onProviderChange(providerCode) }
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
                    items(
                        items = availableLanguages,
                        key = { it }
                    ) { langCode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageChange(langCode)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = langCode == selectedLanguage,
                                onClick = {
                                    onLanguageChange(langCode)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
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

/**
 * Word Study content for bottom sheet - Professional design
 */
@Composable
private fun BottomSheetWordStudyContent(
    wordStudyData: com.starception.submission.core.qurandatabase.AyahMeaningsItem,
    selectedArabicFont: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Professional header with icon badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Word Study",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ayah ${wordStudyData.ayahNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Elegant Arabic Ayah card with subtle gradient border
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = wordStudyData.ayahText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                        fontSize = 22.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Word meanings in styled card
        if (wordStudyData.meanings.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Word Meanings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = wordStudyData.meanings,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                                fontSize = 15.sp,
                                lineHeight = 26.sp
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    surah: Surah,
    isLandscape: Boolean = false,
    scrollOffset: Int = 0 // Scroll offset for parallax effect
) {
    // Use mosque image based on revelation type
    val mosqueImage = remember(surah.revelationType) {
        when (surah.revelationType) {
            "Meccan" -> R.drawable.masjid_al_haram
            "Medinan" -> R.drawable.masjid_al_nawabi
            else -> R.drawable.masjid_al_haram // Default to Makkah
        }
    }

    // Parallax factor - image moves at 0.4x the scroll speed for depth effect
    val parallaxOffset = scrollOffset * 0.4f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLandscape) {
                    Modifier.height(160.dp) // Limited height in landscape
                } else {
                    Modifier.aspectRatio(4f / 3f) // Shortened portrait album cover (was square)
                }
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh) // Match info card background
            .clipToBounds() // Clip the image so parallax doesn't overflow
    ) {
        // Album cover image with parallax effect
        Image(
            painter = painterResource(mosqueImage),
            contentDescription = "Mosque cover for ${surah.nameEnglish} (${surah.revelationType})",
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1.3f) // Make image taller to allow parallax movement
                .graphicsLayer {
                    translationY = parallaxOffset
                },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
    }
}

@Composable
private fun AlbumInfoCard(
    surah: Surah,
    selectedArabicFont: String,
    collapseProgress: Float = 0f,
    topics: List<com.starception.submission.core.topicsdatabase.Topic> = emptyList(),
    onTopicClick: (String) -> Unit = {},
    courseCompletionInfo: CourseCompletionInfo? = null,
    // Inline player + reading-mode controls (new design): title/subtitle on the
    // left, segmented Ayahs/Mushaf toggle in the middle, square play button on
    // the right, with a thin progress bar below.
    isMushafMode: Boolean = false,
    onToggleMode: () -> Unit = {},
    isPlaying: Boolean = false,
    onPlayClick: () -> Unit = {},
    currentProgress: Float = 0f,
) {
    // Original layout: spacer for floating surah-name overlay at top, then
    // translation text + optional course badge + info chips at the bottom.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val floatingNameSpacerHeight = if (courseCompletionInfo != null) 72.dp else 44.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(floatingNameSpacerHeight))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (surah.nameTranslation.isNotBlank()) {
                    Text(
                        text = "\"${surah.nameTranslation}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (courseCompletionInfo != null) {
                    NiaVerifiedTag(
                        onClick = { },
                        enabled = true,
                        text = {
                            Text(text = courseCompletionInfo.courseName.uppercase(Locale.getDefault()))
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InfoChip(text = "${surah.ayahCount} Ayahs")
                        InfoChip(text = surah.revelationType)
                    }
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (topics.isNotEmpty()) {
                            topics.forEach { topic ->
                                NiaTopicTag(
                                    followed = true,
                                    onClick = { onTopicClick(topic.id) },
                                    text = { Text(text = topic.name.uppercase(Locale.getDefault())) },
                                )
                            }
                        } else {
                            InfoChip(text = "Holy Quran")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    NiaTopicTag(
        followed = true,
        onClick = {},
        enabled = true  // Changed from false to true for better visibility
    ) {
        Text(text = text.uppercase(Locale.getDefault()))
    }
}

/**
 * Compact Ayahs/Mushaf segmented control for inline use inside AlbumInfoCard.
 * Same animated sliding indicator as ReadingModeToggle but sized to fit a row.
 */
@Composable
private fun InlineReadingModeToggle(
    isMushafMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(percent = 50)
    val selectionFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isMushafMode) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "inlineSelectionSlide",
    )

    Surface(
        modifier = modifier
            .height(40.dp)
            .width(168.dp),
        shape = pillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        BoxWithConstraints(modifier = Modifier.padding(4.dp)) {
            val segmentWidth = maxWidth / 2f
            val indicatorOffset = segmentWidth * selectionFraction
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, pillShape),
            )
            Row(modifier = Modifier.fillMaxSize()) {
                InlineReadingModeSegment(
                    label = "Ayahs",
                    selectionFraction = 1f - selectionFraction,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isMushafMode) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onToggle()
                        }
                    },
                )
                InlineReadingModeSegment(
                    label = "Mushaf",
                    selectionFraction = selectionFraction,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!isMushafMode) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onToggle()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun InlineReadingModeSegment(
    label: String,
    selectionFraction: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = androidx.compose.ui.graphics.lerp(onSurfaceVariant, onPrimary, selectionFraction)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Rounded-square play/pause button used in the inline AlbumInfoCard layout. */
@Composable
private fun SquarePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun MusicPlayerControls(
    isPlaying: Boolean,
    currentProgress: Float,
    currentVolume: Float,
    surahName: String,
    surahNameArabic: String,
    selectedArabicFont: String,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Player controls using theme colors for visibility of floating surah name overlay
    // Tap anywhere to collapse back to AlbumInfoCard with FAB
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight() // Fill parent Box container (196dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap anywhere collapses to AlbumInfoCard
                onCollapse()
            },
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val contentColor = MaterialTheme.colorScheme.onSurface
        Column(
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            // Progress bar at top
            LinearProgressIndicator(
                progress = { currentProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Spacer for the floating Surah name overlay (names are handled by the overlay)
            Spacer(Modifier.height(72.dp))

            // Playback controls - using theme colors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button - simple icon
                IconButton(
                    onClick = onRewindClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play/Pause button - simple triangle/pause icon
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = contentColor,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Next button - simple icon
                IconButton(
                    onClick = onForwardClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Volume controls
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
                    modifier = Modifier.size(24.dp)
                )

                Slider(
                    value = currentVolume,
                    onValueChange = onVolumeChange,
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BismillahRow(
    arabicFont: String = "default",
    arabicFontSize: Float = 22f,
    textAlignment: String = "start",
    modifier: Modifier = Modifier
) {
    val bismillahText = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
    val textColor = MaterialTheme.colorScheme.onSurface
    val calligraphicStyle = getArabicFontStyle(arabicFont, arabicFontSize * 1.3f)

    Text(
        text = bismillahText,
        style = calligraphicStyle,
        color = textColor,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

@Composable
private fun ContinuousAyahsContent(
    ayahs: List<com.starception.submission.core.qurandatabase.Ayah>,
    arabicFont: String = "default",
    arabicFontSize: Float = 22f,
    showTajweed: Boolean = false,
    tajweedAnnotations: Map<Int, List<com.starception.submission.feature.surah.tajweed.TajweedAnnotation>> = emptyMap(),
    showBismillah: Boolean = false,
    onAyahLongPress: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val markerColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val arabicTextStyle = getArabicFontStyle(arabicFont, arabicFontSize)

    // Build the annotated string only when content changes — NOT on font size changes.
    // Font size is applied via the parent Text style, so it scales automatically.
    // The marker uses 0.85.em (relative) so it always scales with the parent font size
    // without requiring a rebuild of the entire string on every pinch frame.
    val currentMarkerColor = markerColor
    val annotatedString = remember(ayahs, showTajweed, tajweedAnnotations, showBismillah) {
        buildAnnotatedString {
            if (showBismillah) {
                withStyle(SpanStyle(fontSize = 1.3.em)) {
                    append("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ")
                }
                append("\n")
            }
            ayahs.forEachIndexed { index, ayah ->
                val arabicText = ayah.text.split("\n\n").getOrNull(0) ?: ayah.text

                if (showTajweed) {
                    val annotations = tajweedAnnotations[ayah.numberInSurah]
                    if (annotations != null && annotations.isNotEmpty()) {
                        val annotated = com.starception.submission.feature.surah.tajweed.TajweedTextApplier.applyWithOverlap(
                            text = arabicText,
                            annotations = annotations,
                            defaultStyle = SpanStyle()
                        )
                        append(annotated)
                    } else {
                        append(arabicText)
                    }
                } else {
                    append(arabicText)
                }

                // Append U+06DD end-of-ayah marker followed by Arabic-Indic verse number.
                // Use 0.85.em so size scales with parent Text fontSize — no rebuild needed.
                withStyle(SpanStyle(
                    color = currentMarkerColor,
                    fontSize = 0.85.em,
                    fontFamily = ubuntuInspiredFontFamily
                )) {
                    append(" \u06DD${ayah.numberInSurah.toArabicIndic()}")
                }

                if (index < ayahs.size - 1) append(" ")
            }
        }
    }

    val lineSpacingMultiplier = 1.5f
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(ayahs) {
                detectTapGestures(
                    onLongPress = {
                        if (ayahs.isNotEmpty()) onAyahLongPress(ayahs.first().numberInSurah)
                    }
                )
            }
    ) {
        val density = LocalDensity.current
        val availableHeightPx = with(density) { maxHeight.toPx() }
        val horizontalPaddingDp = 12.dp
        val verticalPaddingDp = 8.dp
        val availableWidthPx = with(density) { (maxWidth - horizontalPaddingDp * 2).toPx() }
        val verticalPaddingPx = with(density) { (verticalPaddingDp * 2).toPx() }
        val contentHeightPx = availableHeightPx - verticalPaddingPx

        val baseStyle = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle)

        fun measureHeight(fontSizeSp: Float): Int {
            val style = baseStyle.copy(
                fontSize = fontSizeSp.sp,
                textAlign = TextAlign.End,
                lineHeight = (fontSizeSp * lineSpacingMultiplier).sp
            )
            val result = textMeasurer.measure(
                text = annotatedString,
                style = style,
                constraints = androidx.compose.ui.unit.Constraints(
                    maxWidth = availableWidthPx.toInt()
                ),
                density = density
            )
            return result.size.height
        }

        val scaledFontSize = remember(annotatedString, arabicFontSize, availableHeightPx, availableWidthPx, arabicFont) {
            if (availableHeightPx <= 0f || availableWidthPx <= 0f || annotatedString.text.isEmpty()) {
                return@remember arabicFontSize
            }
            if (measureHeight(arabicFontSize) >= contentHeightPx) {
                return@remember arabicFontSize
            }
            var lo = arabicFontSize
            var hi = 150f
            repeat(15) {
                val mid = (lo + hi) / 2f
                if (measureHeight(mid) <= contentHeightPx) lo = mid else hi = mid
            }
            lo
        }

        Text(
            text = annotatedString,
            color = onSurfaceColor,
            style = baseStyle.copy(
                fontSize = scaledFontSize.sp,
                textAlign = TextAlign.End,
                lineHeight = (scaledFontSize * lineSpacingMultiplier).sp
            ),
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPaddingDp, vertical = verticalPaddingDp)
        )
    }
}

// ---------------------------------------------------------------------------
// Ayah-marker slot geometry. Everything is in em (a fraction of the Arabic
// font size) so the ornament AND the gaps around it scale together when the
// reader changes the font size.
// ---------------------------------------------------------------------------
/** Slot height — must stay under the 1.45x line height or the ornament bleeds into neighbouring lines. */
private const val MARKER_HEIGHT_EM = 1.2f
/** Ornament height as a fraction of the slot height. */
private const val MARKER_ORNAMENT_FILL = 0.92f
/** Aspect ratio (w/h) of R.drawable.ayah_ornament_frame. */
private const val MARKER_ASPECT = 1332f / 1418f
/** Gap reserved in the text flow on each side of the ornament. This IS the
 *  breathing room the user sees: the drawing pass centers the ornament in its
 *  reserved slot (which Compose already positions correctly in the justified
 *  RTL flow), so these gaps directly determine the symmetric spacing between
 *  the ornament and its neighbouring words — no per-letter ink measurement. */
private const val MARKER_GAP_BEFORE_EM = 0.55f
private const val MARKER_GAP_AFTER_EM = 0.55f
/** Visual gap between the ornament and the MEASURED ink edge of the ayah's
 *  last word. Placement is ink-accurate (see computeInkMarkerGeometries): the
 *  page text is rendered once to an offscreen bitmap and the true glyph edges
 *  are scanned, so this is the gap the eye actually sees — no per-glyph
 *  overhang guessing. */
private const val MARKER_INK_GAP_EM = 0.3f
/** Offscreen ink-scan bitmap scale (half resolution is ample for edges). */
private const val MARKER_INK_SCAN_SCALE = 0.5f

/** Inline-content tag for the invisible line filler appended to non-final page
 *  slices. Android never justifies a paragraph's last line, so a sliced page's
 *  bottom line collapses to natural width even though the full-text layout had
 *  it justified. The filler is wider than any line, wraps alone onto a clipped
 *  zero-ink line, and thereby keeps the real last content line justified. */
private const val MUSHAF_LINE_FILLER_TAG = "mushafLineFiller"

/** Filler width as a fraction of the page line width. It must NOT fit in any
 *  line's leftover space (so it wraps, keeping the content line justified) but
 *  MUST fit on a line of its own (an over-wide placeholder cannot wrap at all
 *  and just runs off the edge of the last line — measured, not guessed). */
private const val MUSHAF_LINE_FILLER_WIDTH_FRACTION = 0.98f

private fun mushafLineFillerPlaceholder(maxWidthPx: Int, emPx: Float): androidx.compose.ui.text.Placeholder =
    androidx.compose.ui.text.Placeholder(
        androidx.compose.ui.unit.TextUnit(
            (maxWidthPx / emPx) * MUSHAF_LINE_FILLER_WIDTH_FRACTION,
            androidx.compose.ui.unit.TextUnitType.Em,
        ),
        androidx.compose.ui.unit.TextUnit(0.1f, androidx.compose.ui.unit.TextUnitType.Em),
        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
    )
/** Drawn ornament width in em. */
private const val MARKER_ORNAMENT_WIDTH_EM = MARKER_HEIGHT_EM * MARKER_ORNAMENT_FILL * MARKER_ASPECT
/** Total slot width reserved in the text flow. */
private const val MARKER_SLOT_WIDTH_EM =
    MARKER_GAP_BEFORE_EM + MARKER_ORNAMENT_WIDTH_EM + MARKER_GAP_AFTER_EM
/** Invisible formatting chars that may trail an ayah's last word before its
 *  marker slot (RLM, LRM, ZWSP, BOM, WORD JOINER) — skipped when locating the
 *  final base letter for overhang compensation. */
private val MARKER_TRAILING_INVISIBLES =
    charArrayOf('‏', '‎', '​', '﻿', '⁠')

private data class MarkerGeometry(
    val digits: String,
    val centerX: Float,
    val centerY: Float,
    val left: Float,
    val top: Float,
    val w: Float,
    val h: Float,
)


/**
 * Ink-accurate marker placement for ONE page, self-contained: measures the page
 * text itself (same style/width the paginator used, so positions match the
 * displayed layout exactly), renders it to a half-scale bitmap, and scans each
 * marker's medallion band for the true neighbouring ink edges:
 *  - centered case: ornament at the INK midpoint — visually symmetric;
 *  - line-end case: ornament hugs the verse at [MARKER_INK_GAP_EM] from ink.
 * Pure function of (pageText, style, width) — computed off-main and CACHED per
 * page by the pager, with the neighbouring pages prefetched, so page turns show
 * markers in their final position with no post-render jump.
 */
private fun computeInkMarkerGeometries(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    pageText: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    maxWidthPx: Int,
    density: androidx.compose.ui.unit.Density,
    emPx: Float,
): List<MarkerGeometry> {
    val markerAnnotations = pageText
        .getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, pageText.length)
        .sortedBy { it.start }
    if (markerAnnotations.isEmpty()) return emptyList()

    val ornamentPlaceholder = androidx.compose.ui.text.Placeholder(
        MARKER_SLOT_WIDTH_EM.em, MARKER_HEIGHT_EM.em,
        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
    )
    val layout = textMeasurer.measure(
        text = pageText,
        style = style,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidthPx),
        placeholders = markerAnnotations.map {
            AnnotatedString.Range(
                if (it.item == MUSHAF_LINE_FILLER_TAG) {
                    mushafLineFillerPlaceholder(maxWidthPx, emPx)
                } else {
                    ornamentPlaceholder
                },
                it.start,
                it.end,
            )
        },
        density = density,
    )

    val scale = MARKER_INK_SCAN_SCALE
    val bmpW = (layout.size.width * scale).toInt().coerceAtLeast(1)
    val bmpH = (layout.size.height * scale).toInt().coerceAtLeast(1)
    val bitmap = androidx.compose.ui.graphics.ImageBitmap(bmpW, bmpH)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    canvas.save()
    canvas.scale(scale, scale)
    androidx.compose.ui.text.TextPainter.paint(canvas, layout)
    canvas.restore()
    val pixels = bitmap.toPixelMap()

    fun columnHasInk(x: Int, top: Int, bottom: Int): Boolean {
        if (x < 0 || x >= pixels.width) return false
        // Require a couple of ink rows so anti-aliasing specks and hairline
        // swash tips don't register as a word's edge.
        var inkRows = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(pixels.height)) {
            if (pixels[x, y].alpha > 0.15f && ++inkRows >= 2) return true
        }
        return false
    }

    val result = mutableListOf<MarkerGeometry>()
    layout.placeholderRects.forEachIndexed { i, rect ->
        if (rect == null) return@forEachIndexed
        val annotation = markerAnnotations.getOrNull(i) ?: return@forEachIndexed
        if (annotation.item == MUSHAF_LINE_FILLER_TAG) return@forEachIndexed
        val digits = pageText.text.substring(annotation.start, annotation.end)
        val h = rect.height * MARKER_ORNAMENT_FILL
        val w = h * MARKER_ASPECT

        val markerLine = layout.getLineForOffset(annotation.start)
        // Scan only the CENTRAL span of the ornament. The medallion is a
        // circle, so ink at its extreme top/bottom rows can only touch the
        // sparse outer flourish — while those rows are exactly where glyphs
        // of NEIGHBOURING lines overshoot their boxes (marker 52 of Al-A'raf:
        // the hamza+damma stack of the line below poked 6px into the band's
        // bottom edge mid-gap and split the true gap in two). Trimming to the
        // solid ring's collision zone excludes both maddas above and
        // next-line stack tips below without missing real tails, which sweep
        // through the middle.
        val ornTop = rect.top + (rect.height - h) / 2f
        val bandTop = ((ornTop + 0.12f * h) * scale).toInt()
        val bandBottom = ((ornTop + 0.88f * h) * scale).toInt()
        var nextCharIdx = annotation.end
        while (nextCharIdx < pageText.length &&
            (pageText.text[nextCharIdx].isWhitespace() ||
                pageText.text[nextCharIdx] in MARKER_TRAILING_INVISIBLES)
        ) {
            nextCharIdx++
        }
        val nextOnSameLine = nextCharIdx < pageText.length &&
            layout.getLineForOffset(nextCharIdx) == markerLine

        val lineLeft = layout.getLineLeft(markerLine)
        val slotCenter = (rect.left + rect.right) / 2f
        val window = 2f * emPx

        val centerX: Float
        if (nextOnSameLine) {
            // Both neighbours share this line. Naive edge scans fail when a deep
            // tail sweeps across the slot (both scans land inside the SAME glyph
            // run — marker 43 of Al-Muddaththir measured a 2px "gap"). Instead,
            // find the LARGEST ink-free run of columns in the window and center
            // the medallion in it: that is the true white gap between the two
            // words regardless of how far any tail intrudes.
            //
            // The window MUST reach into both words' ink or the gap gets cut at
            // the window edge and the midpoint biases toward it (markers 49/52 of
            // Al-A'raf sat left-of-center on justify-stretched lines). The next
            // word's layout position bounds the left side exactly.
            val nextWordX = layout.getHorizontalPosition(nextCharIdx, usePrimaryDirection = true)
            val winLPx = minOf(rect.left - window, nextWordX - 1.5f * emPx).coerceAtLeast(lineLeft)
            val winL = (winLPx * scale).toInt().coerceAtLeast(0)
            val winR = ((rect.right + window) * scale).toInt().coerceAtMost(pixels.width - 1)
            // Among all ink-free runs, pick the one that OVERLAPS THE SLOT most
            // (ties broken by width) — the slot is where layout reserved space
            // between the two words, so that run IS the inter-word gap. Picking
            // the globally widest run instead can select white space beyond the
            // NEXT word (marker 52 of Al-A'raf landed on top of it that way).
            val slotL = (rect.left * scale).toInt()
            val slotR = (rect.right * scale).toInt()
            var bestGapL = -1
            var bestGapR = -1
            var bestOverlap = -1
            var bestWidth = -1
            var runStart = -1
            var x = winL
            while (x <= winR + 1) {
                val ink = x <= winR && columnHasInk(x, bandTop, bandBottom)
                if (!ink && runStart < 0) runStart = x
                if ((ink || x == winR + 1) && runStart >= 0) {
                    val runEnd = x
                    val overlap = (minOf(runEnd, slotR) - maxOf(runStart, slotL)).coerceAtLeast(0)
                    val width = runEnd - runStart
                    if (overlap > bestOverlap || (overlap == bestOverlap && width > bestWidth)) {
                        bestOverlap = overlap
                        bestWidth = width
                        bestGapL = runStart
                        bestGapR = runEnd
                    }
                    runStart = -1
                }
                x++
            }
            centerX = if (bestGapL >= 0) {
                ((bestGapL + bestGapR) / 2f) / scale
            } else {
                slotCenter // window solid with ink — degenerate; keep the slot center
            }
        } else {
            // Line-end marker: hug the verse at the measured ink edge. Scanning
            // from the line's empty left region cannot start inside a glyph.
            var rightInk = rect.right
            var x = (lineLeft * scale).toInt().coerceAtLeast(0)
            val maxX = ((rect.right + window) * scale).toInt().coerceAtMost(pixels.width - 1)
            while (x <= maxX) {
                if (columnHasInk(x, bandTop, bandBottom)) { rightInk = x / scale; break }
                x++
            }
            centerX = maxOf(
                rightInk - MARKER_INK_GAP_EM * emPx - w / 2f,
                lineLeft + w / 2f,
            )
        }
        result.add(
            MarkerGeometry(
                digits = digits,
                centerX = centerX,
                centerY = rect.center.y,
                left = centerX - w / 2f,
                top = rect.top + (rect.height - h) / 2f,
                w = w,
                h = h,
            ),
        )
    }
    return result
}

// ---------------------------------------------------------------------------
// Mushaf page — renders pre-paginated justified text
// ---------------------------------------------------------------------------
@Composable
private fun MushafPageWithFrame(
    pageText: AnnotatedString,
    arabicFont: String,
    arabicFontSize: Float,
    showBismillah: Boolean,
    onAyahLongPress: (Int) -> Unit,
    ayahRanges: List<Pair<Int, IntRange>>,
    highlightedAyahNumber: Int? = null,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = emptyMap(),
    /** Precomputed ink-accurate marker geometry for THIS page (null while pending). */
    inkGeometries: List<MarkerGeometry>? = null,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
    val markerColor = MaterialTheme.colorScheme.primary

    // Rosette is now part of the masterString text flow (U+06DD + digits
    // styled with Amiri Quran). No inline content needed here.

    // Overlay a background tint over the highlighted ayah's char range. The
    // range is page-local (already remapped in MushafPagerView's paginator),
    // so we can apply SpanStyle directly without re-mapping.
    val renderedText = remember(pageText, highlightedAyahNumber, ayahRanges, highlightBg) {
        val target = highlightedAyahNumber ?: return@remember pageText
        val range = ayahRanges.firstOrNull { it.first == target }?.second
            ?: return@remember pageText
        androidx.compose.ui.text.AnnotatedString.Builder(pageText).apply {
            addStyle(
                style = androidx.compose.ui.text.SpanStyle(background = highlightBg),
                start = range.first,
                end = (range.last + 1).coerceAtMost(pageText.length),
            )
        }.toAnnotatedString()
    }

    val statusBarHeight = with(LocalDensity.current) {
        val cutout = WindowInsets.displayCutout.getTop(this)
        val statusBar = WindowInsets.statusBars.getTop(this)
        maxOf(cutout, statusBar).toDp()
    }
    val navBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val horizontalPadding = 12.dp
    // 16dp strip at the top hosts the morphing pull-down hint drawn by the
    // screen overlay (pill ↔ grabber); MUST match the paginator's topPadding
    // so measured pages fit exactly.
    val topPadding = 16.dp
    val bottomPadding = 16.dp
    val bismillahHeightDp = if (showBismillah) 36.dp else 0.dp

    // The ayah ornaments are painted BEHIND the text at the exact placeholder
    // rects the layout produced, so overhanging swashes render on top of the
    // ornament's flourishes (printed-mushaf behaviour) instead of colliding.

    val ornamentPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
        image = ImageVector.vectorResource(R.drawable.ayah_ornament_frame))
    // Inline marker annotations in layout order — alt text carries the
    // localized digits, and the char range locates each marker in the page
    // text so the drawing pass can find the neighbouring words.
    val lineSpacingMultiplier = 1.45f
    val arabicTextStyle = getArabicFontStyle(arabicFont, arabicFontSize)

    // Markers fade in once their (cached/prefetched) ink geometry exists —
    // never drawn at provisional positions, so page turns show no marker jump.
    val markerAlpha by animateFloatAsState(
        targetValue = if (inkGeometries != null) 1f else 0f,
        animationSpec = com.starception.submission.core.designsystem.animation.NiaMotion
            .standardTween(com.starception.submission.core.designsystem.animation.NiaMotion.Duration.SHORT_3),
        label = "markerFade"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = surfaceColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showBismillah) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = topPadding
                        )
                        .height(bismillahHeightDp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 20.sp,
                            fontFamily = getArabicFontFamily(arabicFont)
                        ),
                        color = onSurfaceColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Markers are drawn in two passes around the Text:
            //   1) BEFORE it (this Canvas): the ornament vector — overhanging final
            //      swashes render on top of its flourishes, printed-mushaf style.
            //   2) AFTER it (Canvas below the Text): the digits, always crisp. With
            //      ink-accurate placement nothing should reach the digit zone; when
            //      a tail does cross the ring it slides under the digits untouched.
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topPadding + bismillahHeightDp,
                        bottom = bottomPadding
                    )
            ) {
                val geoms = inkGeometries ?: return@Canvas
                if (markerAlpha <= 0.01f) return@Canvas
                for (g in geoms) {
                    translate(g.left, g.top) {
                        with(ornamentPainter) {
                            draw(
                                androidx.compose.ui.geometry.Size(g.w, g.h),
                                alpha = markerAlpha,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(markerColor),
                            )
                        }
                    }
                }
            }


            Text(
                text = renderedText,
                inlineContent = inlineContent,
                color = onSurfaceColor,
                style = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle).copy(
                    fontSize = arabicFontSize.sp,
                    textAlign = TextAlign.Justify,
                    // High-quality (optimizing) line breaking packs words far more
                    // evenly than the greedy default, so Justify stretches gaps much
                    // less. MUST match the paginator's measureStyle exactly.
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                    textDirection = androidx.compose.ui.text.style.TextDirection.Rtl,
                    lineHeight = (arabicFontSize * lineSpacingMultiplier).sp,
                    // Trim extra font-metric leading/trailing on the first/last
                    // line so pages whose first line contains the Amiri Quran
                    // rosette glyph don't get inflated top padding from the
                    // glyph's tall ascender.
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                    ),
                ),
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topPadding + bismillahHeightDp,
                        bottom = bottomPadding
                    )
                    .pointerInput(ayahRanges) {
                        detectTapGestures(
                            onLongPress = {
                                if (ayahRanges.isNotEmpty()) {
                                    onAyahLongPress(ayahRanges.first().first)
                                }
                            }
                        )
                    }
            )

            // Pass 2: digits + shield above the text (see comment on pass 1).
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topPadding + bismillahHeightDp,
                        bottom = bottomPadding
                    )
            ) {
                val geoms = inkGeometries ?: return@Canvas
                if (markerAlpha <= 0.01f) return@Canvas
                for (g in geoms) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = markerColor.toArgb()
                            alpha = (255 * markerAlpha).toInt()
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = (arabicFontSize * if (g.digits.length >= 3) 0.25f else 0.32f).sp.toPx()
                            isFakeBoldText = true
                        }
                        val baselineY = g.centerY - (paint.descent() + paint.ascent()) / 2f
                        canvas.nativeCanvas.drawText(g.digits, g.centerX, baselineY, paint)
                    }
                }
            }

        }
    }
}

// ---------------------------------------------------------------------------
// Mushaf Pager — page-by-page reading with justified, page-filling text.
// Builds one master AnnotatedString, measures lines, splits at line boundaries.
// Ayahs flow naturally across pages like a real printed Quran.
// ---------------------------------------------------------------------------
@OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)
@Composable
private fun MushafPagerView(
    ayahs: List<com.starception.submission.core.qurandatabase.Ayah>,
    arabicFont: String,
    arabicFontSize: Float,
    showTajweed: Boolean,
    tajweedAnnotations: Map<Int, List<com.starception.submission.feature.surah.tajweed.TajweedAnnotation>>,
    showBismillah: Boolean = false,
    textAlignment: String = "start",
    /** Selected translation code — drives the digit system of ayah markers. */
    translationCode: String = "ar",
    parentScrollState: androidx.compose.foundation.lazy.LazyListState? = null,
    initialPage: Int = 0,
    /** Names used by the app-level Mushaf mini-bar in PullToSyncContainer. */
    surahNameArabic: String = "",
    surahNameEnglish: String = "",
    /** Search-driven jump target; snap once after pagination so the user lands
     *  on the page containing this ayah (e.g. Ayatul Kursi → 2:255). 0 = none. */
    scrollToAyah: Int = 0,
    /** Numerically equal ayah inside [scrollToAyah]'s page gets a background tint
     *  so the user can locate the verse the search link picked. */
    highlightedAyahNumber: Int? = null,
    onAyahLongPress: (Int) -> Unit,
    onPageChange: (current: Int, total: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (ayahs.isEmpty()) return

    val markerColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val state = eu.wewox.pagecurl.page.rememberPageCurlState(initialPage)

    // Pinch streams a new font size every frame; re-measuring the whole surah at
    // that rate janks the gesture and dumps the reader onto a different page.
    // Pagination therefore follows a DEBOUNCED copy of the size (committed once
    // the value stops changing), while the live pager scales visually as a
    // preview. After repagination the pager re-anchors to the ayah that was at
    // the top of the page being read.
    var committedFontSize by remember { mutableStateOf(arabicFontSize) }
    var pendingAnchorAyah by remember { mutableStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Wire physical volume keys to Mushaf page navigation while this composable
    // is on screen. Activity.onKeyDown checks MushafKeyBus.handle{Next,Prev}().
    val mushafScope = rememberCoroutineScope()
    DisposableEffect(state) {
        MushafKeyBus.bind(
            next = { mushafScope.launch { state.next() } },
            prev = { mushafScope.launch { state.prev() } },
        )
        onDispose { MushafKeyBus.unbind() }
    }
    val statusBarHeight = with(density) {
        val cutout = WindowInsets.displayCutout.getTop(this)
        val statusBar = WindowInsets.statusBars.getTop(this)
        maxOf(cutout, statusBar).toDp()
    }
    val navBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val lineSpacingMultiplier = 1.45f
    val horizontalPadding = 12.dp
    // 16dp strip at the top hosts the morphing pull-down hint drawn by the
    // screen overlay (pill ↔ grabber); MUST match MushafPageWithFrame's
    // topPadding so rendered pages fit exactly.
    val topPadding = 16.dp
    val bottomPadding = navBarHeight + 8.dp
    val bismillahHeightDp = 36.dp

    // End-of-ayah marker: the user's ornamental frame drawable with the ayah
    // number centered inside, tinted in the theme color. Drawn as inline
    // content, it looks identical for any digit system (٤ / ۴ / ৪ / 4).
    val markerDigitsFor: (Int) -> String = remember(translationCode) {
        { numberInSurah -> numberInSurah.toAyahDigits(translationCode) }
    }
    // Single-slot placeholder — the drawing pass in MushafPageWithFrame does
    // the real work of positioning the medallion by measuring live ink edges
    // per line and painting an opaque halo of fixed size around it. That
    // guarantees the visible padding around every ornament is the same on
    // both sides regardless of font, size, or justification stretch, so we
    // don't need to reserve variable-width slots up front.
    val ornamentPlaceholder = androidx.compose.ui.text.Placeholder(
        MARKER_SLOT_WIDTH_EM.em, MARKER_HEIGHT_EM.em,
        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
    val markerInlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> =
        remember {
            mapOf(
                "ayahOrnament" to androidx.compose.foundation.text.InlineTextContent(ornamentPlaceholder) { _ -> }
            )
        }

    val markerData = remember(ayahs, showTajweed, tajweedAnnotations, committedFontSize, translationCode) {
        val placeholderRanges = mutableListOf<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.Placeholder>>()
        val built = buildAnnotatedString {
            ayahs.forEach { ayah ->
                val arabicText = ayah.text.split("\n\n").getOrNull(0) ?: ayah.text
                if (showTajweed) {
                    val annotations = tajweedAnnotations[ayah.numberInSurah]
                    if (annotations != null && annotations.isNotEmpty()) {
                        val annotated = com.starception.submission.feature.surah.tajweed.TajweedTextApplier.applyWithOverlap(
                            text = arabicText,
                            annotations = annotations,
                            defaultStyle = SpanStyle()
                        )
                        append(annotated)
                    } else {
                        append(arabicText)
                    }
                } else {
                    append(arabicText)
                }
                val digits = markerDigitsFor(ayah.numberInSurah)
                // U+2060 WORD JOINER: forbids a line break between the ayah's
                // last word and its marker.
                append('⁠')
                val markerStart = length
                appendInlineContent("ayahOrnament", digits)
                placeholderRanges.add(
                    androidx.compose.ui.text.AnnotatedString.Range(
                        ornamentPlaceholder, markerStart, markerStart + digits.length
                    )
                )
                append(' ')
            }
        }
        built to placeholderRanges
    }
    val masterString = markerData.first
    val markerPlaceholders = markerData.second

    // ---- old per-ayah measurement removed; drawing pass handles positioning ----
    /* removed:
        val invisiblesForMeasure = charArrayOf('‏', '‎', '​', '﻿', '⁠')
        val emPx = fontSizePx

        val perAyahBucket = mutableMapOf<Int, Int>()
        val bucketWidthsEm = mutableMapOf<Int, Float>()
        ayahs.forEach { ayah ->
            val arabicText = ayah.text.split("\n\n").getOrNull(0) ?: ayah.text
            var wordEnd = arabicText.length
            while (wordEnd > 0 && arabicText[wordEnd - 1] in invisiblesForMeasure) wordEnd--
            var wordStart = wordEnd
            while (wordStart > 0 && !arabicText[wordStart - 1].isWhitespace()) wordStart--
            // Overhang = ink extending past the advance box. Paint.getTextPath
            // on Android renders LTR by default even for Arabic (its pen goes
            // rightward), so the ink bounds are in a coordinate frame [0..W]
            // where W ≈ advance width. Compare ink_width vs advance and take
            // the excess — this is robust to text direction. Half that excess
            // is (approximately) how much a final swash tail extends past the
            // last-glyph's advance edge, which is what we need to clear.
            val overhangPx = if (wordEnd > wordStart) {
                paint.getTextPath(arabicText, wordStart, wordEnd, 0f, 0f, inkPath)
                inkPath.computeBounds(inkRect, true)
                if (inkRect.isEmpty) {
                    0f
                } else {
                    val advance = paint.measureText(arabicText, wordStart, wordEnd)
                    val inkWidth = inkRect.width()
                    maxOf(0f, inkWidth - advance)
                }
            } else 0f
            val overhangEm = overhangPx / emPx
            val beforeGapEm = overhangEm + MARKER_MIN_BREATHING_EM
            val slotWidthEm = beforeGapEm + MARKER_ORNAMENT_WIDTH_EM + MARKER_GAP_AFTER_EM
            val bucket = kotlin.math.ceil(slotWidthEm * 10f).toInt()
            perAyahBucket[ayah.numberInSurah] = bucket
            bucketWidthsEm[bucket] = bucket / 10f
        }

        val inlineMap: Map<String, androidx.compose.foundation.text.InlineTextContent> =
            bucketWidthsEm.entries.associate { (bucket, widthEm) ->
                "ayahOrnament_$bucket" to androidx.compose.foundation.text.InlineTextContent(
                    androidx.compose.ui.text.Placeholder(
                        widthEm.em, MARKER_HEIGHT_EM.em,
                        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter
                    )
                ) { _ -> }
            }

        val placeholderRanges = mutableListOf<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.Placeholder>>()
        val built = buildAnnotatedString {
            ayahs.forEach { ayah ->
                val arabicText = ayah.text.split("\n\n").getOrNull(0) ?: ayah.text

                if (showTajweed) {
                    val annotations = tajweedAnnotations[ayah.numberInSurah]
                    if (annotations != null && annotations.isNotEmpty()) {
                        val annotated = com.starception.submission.feature.surah.tajweed.TajweedTextApplier.applyWithOverlap(
                            text = arabicText,
                            annotations = annotations,
                            defaultStyle = SpanStyle()
                        )
                        append(annotated)
                    } else {
                        append(arabicText)
                    }
                } else {
                    append(arabicText)
                }

                val digits = markerDigitsFor(ayah.numberInSurah)
                val bucket = perAyahBucket[ayah.numberInSurah] ?: bucketWidthsEm.keys.max()
                val slotTag = "ayahOrnament_$bucket"
                val slotPlaceholder = inlineMap.getValue(slotTag).placeholder
                // U+2060 WORD JOINER: forbids a line break between the ayah's
                // last word and its marker, so an ornament can never be
                // orphaned at the start of a line — it wraps with the word.
                append('\u2060')
                val markerStart = length
                appendInlineContent(slotTag, digits)
                placeholderRanges.add(
                    androidx.compose.ui.text.AnnotatedString.Range(
                        slotPlaceholder, markerStart, markerStart + digits.length
                    )
                )
                // U+202F NARROW NO-BREAK SPACE: still counted as whitespace by
                // Java (so the gap-detection code in MushafPageWithFrame keeps
                // working), but justification does NOT redistribute stretch
                // into it — that would pile all the line's slack on one side
                // of the marker and break the symmetric slot geometry above.
                // Line breaks between ayahs still work because Compose treats
                // the previous ayah's own word boundaries as break candidates.
                append(' ')
            }
        }
        MushafMarkerData(built, placeholderRanges, inlineMap)
    }
    */

    val ayahCharRanges = remember(ayahs, translationCode) {
        val ranges = mutableListOf<Pair<Int, IntRange>>()
        var pos = 0
        ayahs.forEach { ayah ->
            val arabicText = ayah.text.split("\n\n").getOrNull(0) ?: ayah.text
            val startPos = pos
            // arabicText + word-joiner + inline marker (alt text = digits) + " "
            pos += arabicText.length + 1 + markerDigitsFor(ayah.numberInSurah).length + 1
            ranges.add(ayah.numberInSurah to (startPos until pos))
        }
        ranges
    }

    Column(
        modifier = modifier.background(surfaceColor)
    ) {
        val scope = rememberCoroutineScope()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Keep the page text clear of the camera cutout (on a side edge in
                // landscape) — the surface background still fills edge-to-edge, only the
                // measured content area (and pagination) is inset. Matches how full-screen
                // pages like Home honor the cutout via safeDrawing.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .pointerInput(parentScrollState, state) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        var directionDecided = false
                        var isVerticalScroll = false
                        var horizontalDragTotal = 0f
                        var verticalDragTotal = 0f
                        val touchSlop = viewConfiguration.touchSlop

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointers = event.changes.filter { it.pressed }

                            if (pointers.size >= 2) {
                                pointers.forEach { it.consume() }
                            } else if (pointers.size == 1) {
                                val change = pointers[0]
                                val delta = change.position - change.previousPosition

                                if (!directionDecided) {
                                    totalDx += kotlin.math.abs(delta.x)
                                    totalDy += kotlin.math.abs(delta.y)
                                    val totalMove = sqrt(totalDx * totalDx + totalDy * totalDy)
                                    if (totalMove > touchSlop) {
                                        directionDecided = true
                                        isVerticalScroll = totalDy > totalDx
                                        }
                                }

                                if (directionDecided && !isVerticalScroll) {
                                    change.consume()
                                    horizontalDragTotal += delta.x
                                } else if (directionDecided && isVerticalScroll) {
                                    // Don't consume — let parent scroll first.
                                    verticalDragTotal += delta.y
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (directionDecided && !isVerticalScroll) {
                            val swipeThreshold = size.width * 0.15f
                            if (horizontalDragTotal < -swipeThreshold) {
                                scope.launch { state.next() }
                            } else if (horizontalDragTotal > swipeThreshold) {
                                scope.launch { state.prev() }
                            }
                        } else if (directionDecided && isVerticalScroll) {
                            // Only fire swipe-up → next page when the parent
                            // LazyColumn is already at the bottom of its
                            // scroll range (i.e., Mushaf fully takes the
                            // viewport — nothing left to scroll). Otherwise
                            // the parent owns the gesture and scroll up
                            // behaves normally.
                            val parentExhausted = parentScrollState?.canScrollForward == false
                            val upThreshold = size.height * 0.25f
                            if (parentExhausted && verticalDragTotal < -upThreshold) {
                                scope.launch { state.next() }
                            }
                            // Downward swipes never turn the page.
                        }
                    }
                }
        ) {
            val availableWidthPx = with(density) { (maxWidth - horizontalPadding * 2).toPx() }
            val fullPageHeightPx = with(density) {
                (maxHeight - topPadding - bottomPadding).toPx()
            }
            val firstPageHeightPx = if (showBismillah) {
                with(density) { (maxHeight - topPadding - bottomPadding - bismillahHeightDp).toPx() }
            } else {
                fullPageHeightPx
            }

            val arabicTextStyle = getArabicFontStyle(arabicFont, committedFontSize)
            val measureStyle = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle).copy(
                fontSize = committedFontSize.sp,
                textAlign = TextAlign.Justify,
                // Keep in lockstep with MushafPageWithFrame's render style — the
                // paginator slices pages at these line breaks AND the ink-geometry
                // pass positions medallions from this layout. textDirection is part
                // of that contract: without it the measured paragraph can resolve
                // LTR (left-aligned last lines), shifting every X on those lines
                // ~half a page from what is displayed.
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                textDirection = androidx.compose.ui.text.style.TextDirection.Rtl,
                lineHeight = (committedFontSize * lineSpacingMultiplier).sp,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            )

            data class PaginatedPage(
                val text: AnnotatedString,
                val pageNumber: Int,
                val showBismillah: Boolean,
                val ayahRanges: List<Pair<Int, IntRange>>
            )

            val paginatedPages = remember(
                masterString, committedFontSize, arabicFont,
                availableWidthPx, fullPageHeightPx, firstPageHeightPx
            ) {
                if (masterString.text.isEmpty() || availableWidthPx <= 0f || fullPageHeightPx <= 0f) {
                    return@remember listOf(
                        PaginatedPage(masterString, 1, showBismillah, ayahCharRanges)
                    )
                }

                val fullLayout = textMeasurer.measure(
                    text = masterString,
                    style = measureStyle,
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = availableWidthPx.toInt()
                    ),
                    placeholders = markerPlaceholders,
                    density = density
                )

                if (fullLayout.lineCount == 0) {
                    return@remember listOf(
                        PaginatedPage(masterString, 1, showBismillah, ayahCharRanges)
                    )
                }

                val pages = mutableListOf<PaginatedPage>()
                var currentLine = 0
                var pageNum = 1

                while (currentLine < fullLayout.lineCount) {
                    val pageHeightPx = if (pageNum == 1) firstPageHeightPx else fullPageHeightPx
                    var linesOnPage = 0
                    var accumulatedHeight = 0f

                    for (line in currentLine until fullLayout.lineCount) {
                        val lineTop = fullLayout.getLineTop(line) - fullLayout.getLineTop(currentLine)
                        val lineBottom = fullLayout.getLineBottom(line) - fullLayout.getLineTop(currentLine)
                        if (lineBottom > pageHeightPx && linesOnPage > 0) break
                        linesOnPage++
                        accumulatedHeight = lineBottom
                    }

                    if (linesOnPage == 0) linesOnPage = 1

                    val startCharIndex = fullLayout.getLineStart(currentLine)
                    val endLine = currentLine + linesOnPage - 1
                    val endCharIndex = if (endLine >= fullLayout.lineCount - 1) {
                        masterString.length
                    } else {
                        fullLayout.getLineEnd(endLine, visibleEnd = true)
                    }

                    val rawSlice = masterString.subSequence(startCharIndex, endCharIndex)
                    // Non-final pages: append the invisible over-wide filler so the
                    // slice's bottom content line stays justified (it was justified
                    // in the full layout; see MUSHAF_LINE_FILLER_TAG).
                    val pageString = if (endCharIndex < masterString.length) {
                        androidx.compose.ui.text.AnnotatedString.Builder(rawSlice).apply {
                            appendInlineContent(MUSHAF_LINE_FILLER_TAG, "\u200B")
                        }.toAnnotatedString()
                    } else {
                        rawSlice
                    }

                    val pageAyahRanges = ayahCharRanges.mapNotNull { (ayahNum, range) ->
                        val overlapStart = maxOf(range.first, startCharIndex)
                        val overlapEnd = minOf(range.last, endCharIndex - 1)
                        if (overlapStart <= overlapEnd) {
                            ayahNum to ((overlapStart - startCharIndex) until (overlapEnd - startCharIndex + 1))
                        } else null
                    }

                    pages.add(PaginatedPage(
                        text = pageString,
                        pageNumber = pageNum,
                        showBismillah = showBismillah && pageNum == 1,
                        ayahRanges = pageAyahRanges
                    ))

                    currentLine += linesOnPage
                    pageNum++
                }

                pages
            }

            LaunchedEffect(state.current, paginatedPages.size) {
                onPageChange(state.current, paginatedPages.size)
            }

            // Publish current Mushaf page to PullToSyncContainer's mini-bar.
            // Cleared on dispose so leaving Mushaf mode hides the strip.
            DisposableEffect(surahNameArabic, surahNameEnglish, state.current, paginatedPages.size) {
                if (paginatedPages.isNotEmpty()) {
                    MushafMiniBarBus.state.value = MushafMiniBarState(
                        surahNameArabic = surahNameArabic,
                        surahNameEnglish = surahNameEnglish,
                        currentPage = state.current + 1,
                        totalPages = paginatedPages.size,
                    )
                    MushafMiniBarBus.bind(
                        next = { mushafScope.launch { state.next() } },
                        previous = { mushafScope.launch { state.prev() } },
                    )
                }
                onDispose { MushafMiniBarBus.unbind() }
            }

            // Snap to the page containing scrollToAyah after pagination is ready.
            // Keyed on paginatedPages so we re-run once the pages exist; further
            // user swipes stay put because the key doesn't change without a font
            // / size / width edit (which invalidates the layout anyway).
            // Debounced font-size commit: capture the reading anchor first so the
            // repagination effect below can restore the reader's place.
            LaunchedEffect(arabicFontSize) {
                if (arabicFontSize == committedFontSize) return@LaunchedEffect
                kotlinx.coroutines.delay(250)
                pendingAnchorAyah = paginatedPages.getOrNull(state.current)
                    ?.ayahRanges?.firstOrNull()?.first ?: 0
                committedFontSize = arabicFontSize
            }
            LaunchedEffect(paginatedPages) {
                if (pendingAnchorAyah > 0 && paginatedPages.isNotEmpty()) {
                    val idx = paginatedPages.indexOfFirst { page ->
                        page.ayahRanges.any { it.first == pendingAnchorAyah }
                    }
                    if (idx >= 0 && idx != state.current) state.snapTo(idx)
                    pendingAnchorAyah = 0
                }
            }

            // Render-side inline map including the dynamically-sized line filler —
            // must match the widths the paginator and geometry pass used.
            val pagerEmPxForFiller = with(density) { committedFontSize.sp.toPx() }
            val pageInlineContent = remember(markerInlineContent, availableWidthPx, committedFontSize) {
                markerInlineContent + (
                    MUSHAF_LINE_FILLER_TAG to androidx.compose.foundation.text.InlineTextContent(
                        mushafLineFillerPlaceholder(availableWidthPx.toInt(), pagerEmPxForFiller),
                    ) { _ -> }
                    )
            }

            // Ink-accurate marker geometry per page, prefetched for the pages the
            // reader can reach next so page turns never show markers moving.
            val inkGeomCache = remember(paginatedPages) {
                androidx.compose.runtime.mutableStateMapOf<Int, List<MarkerGeometry>>()
            }
            val pagerEmPx = with(density) { committedFontSize.sp.toPx() }
            LaunchedEffect(state.current, paginatedPages) {
                if (paginatedPages.isEmpty()) return@LaunchedEffect
                for (idx in listOf(state.current, state.current + 1, state.current - 1)) {
                    if (idx in paginatedPages.indices && idx !in inkGeomCache) {
                        val geoms = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            runCatching {
                                computeInkMarkerGeometries(
                                    textMeasurer = textMeasurer,
                                    pageText = paginatedPages[idx].text,
                                    style = measureStyle,
                                    maxWidthPx = availableWidthPx.toInt(),
                                    density = density,
                                    emPx = pagerEmPx,
                                )
                            }.getOrNull()
                        }
                        if (geoms != null) inkGeomCache[idx] = geoms
                    }
                }
            }

            LaunchedEffect(paginatedPages, scrollToAyah) {
                if (scrollToAyah > 0 && paginatedPages.isNotEmpty()) {
                    val targetIndex = paginatedPages.indexOfFirst { page ->
                        page.ayahRanges.any { it.first == scrollToAyah }
                    }
                    if (targetIndex >= 0 && targetIndex != state.current) {
                        state.snapTo(targetIndex)
                    }
                }
            }

            val pageCurlConfig = eu.wewox.pagecurl.config.rememberPageCurlConfig(
                dragForwardEnabled = false,
                dragBackwardEnabled = false
            )
            eu.wewox.pagecurl.page.PageCurl(
                count = paginatedPages.size,
                state = state,
                config = pageCurlConfig,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Live pinch preview: scale the committed layout toward the
                        // in-flight size; snaps back to 1x when the commit lands.
                        val preview = if (committedFontSize > 0f) arabicFontSize / committedFontSize else 1f
                        scaleX = preview
                        scaleY = preview
                    }
            ) { pageIndex ->
                val page = paginatedPages[pageIndex]
                MushafPageWithFrame(
                    pageText = page.text,
                    inlineContent = pageInlineContent,
                    inkGeometries = inkGeomCache[pageIndex],
                    arabicFont = arabicFont,
                    arabicFontSize = committedFontSize,
                    showBismillah = page.showBismillah,
                    onAyahLongPress = onAyahLongPress,
                    ayahRanges = page.ayahRanges,
                    highlightedAyahNumber = highlightedAyahNumber,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun getArabicFontFamily(arabicFont: String): androidx.compose.ui.text.font.FontFamily {
    return getArabicFontFamilyForSelection(arabicFont)
}

/** Ayah-number digits in the numbering system of the selected translation. */
private fun Int.toAyahDigits(translationCode: String): String {
    val zero: Char? = when (translationCode) {
        "ar" -> '\u0660'   // ٠ Arabic-Indic
        "ur" -> '\u06F0'   // ۰ Extended Arabic-Indic
        "bn" -> '\u09E6'   // ০ Bengali
        else -> null        // Western digits for Latin/Cyrillic/CJK readers
    }
    if (zero == null) return toString()
    return toString().map { c -> if (c in '0'..'9') zero + (c - '0') else c }.joinToString("")
}

private fun Int.toArabicIndic(): String = this.toString().map { c ->
    when (c) {
        '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
        '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
        else -> c
    }
}.joinToString("")

@Composable
private fun AyahTrackItem(
    ayah: Ayah,
    arabicFont: String = "default",
    arabicFontSize: Float = 22f,
    textAlignment: String = "start",
    showTranslation: Boolean = true,
    showTajweed: Boolean = false,
    tajweedAnnotations: List<com.starception.submission.feature.surah.tajweed.TajweedAnnotation>? = null,
    isFavourite: Boolean = false,
    hasNote: Boolean = false,
    isReciting: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {}
) {
    // Soft tint when this ayah is the one currently being recited, OR was
    // jumped to from search (e.g. "Ayatul Kursi" → 2:255). Reciting wins so a
    // playing ayah is still visually distinct from the search-target tint.
    val highlightColor = when {
        isReciting -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        else -> Color.Transparent
    }
    val highlightBorderColor = if (isHighlighted && !isReciting) {
        MaterialTheme.colorScheme.tertiary
    } else Color.Transparent
    Surface(
        color = highlightColor,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (highlightBorderColor != Color.Transparent) {
                    drawRect(
                        color = highlightBorderColor,
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                    )
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                onDoubleClick = onDoubleTap
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number or heart icon if favourited
            if (isFavourite) {
                // Show heart icon with ayah number inside for favourited ayahs
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp)
                ) {
                    // Heart icon background
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    // Ayah number overlaid on heart
                    Text(
                        text = ayah.numberInSurah.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 2.dp) // Slight adjustment for visual centering
                    )
                }
            } else {
                // Show circular badge with number for non-favourited ayahs
                // Stack note indicator on top-right if ayah has notes
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = ayah.numberInSurah.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    // Note indicator badge
                    if (hasNote) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Has note",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Ayah text (no truncation - shows full text including both Arabic and translation)
            // Split Arabic and translation text for separate styling
            val parts = ayah.text.split("\n\n")
            val arabicText = parts.getOrNull(0) ?: ayah.text
            val translationText = parts.getOrNull(1)

            // Convert alignment string to both TextAlign and Alignment enum
            val textAlign = when (textAlignment) {
                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                "end" -> androidx.compose.ui.text.style.TextAlign.End
                else -> androidx.compose.ui.text.style.TextAlign.Start
            }

            val horizontalAlignment = when (textAlignment) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = horizontalAlignment
            ) {
                // Arabic text with user-selected font and size
                val arabicTextStyle = getArabicFontStyle(arabicFont, arabicFontSize)

                // Apply Tajweed coloring if enabled and annotations are available
                if (showTajweed && tajweedAnnotations != null && tajweedAnnotations.isNotEmpty()) {
                    val annotatedArabicText = com.starception.submission.feature.surah.tajweed.TajweedTextApplier.applyWithOverlap(
                        text = arabicText,
                        annotations = tajweedAnnotations,
                        defaultStyle = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface)
                    )
                    Text(
                        text = annotatedArabicText,
                        style = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle),
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = arabicText,
                        style = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Translation text with 2:1 ratio (translation is 1/2 of Arabic size)
                if (showTranslation && translationText != null && translationText.isNotBlank()) {
                    val translationFontSize = arabicFontSize * 0.5f  // 2:1 ratio (50%)
                    Text(
                        text = translationText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = translationFontSize.sp,
                            lineHeight = (translationFontSize * 1.5f).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Divider
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

// Helper function to get text style for different Arabic fonts
@Composable
private fun getArabicFontStyle(fontName: String, fontSize: Float = 22f): androidx.compose.ui.text.TextStyle {
    val lineHeightMultiplier = 1.6f // Line height is 1.6x font size
    return when (fontName) {
        "pdms_saleem" -> androidx.compose.ui.text.TextStyle(
            fontFamily = QuranFonts.PDMSSaleem,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )
        "noor_e_hidayat" -> androidx.compose.ui.text.TextStyle(
            fontFamily = QuranFonts.NoorEHidayat,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.4.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )
        "thabit" -> androidx.compose.ui.text.TextStyle(
            fontFamily = QuranFonts.Thabit,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.3.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )
        "uthmani_script" -> androidx.compose.ui.text.TextStyle(
            fontFamily = QuranFonts.UthmanicScript,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.6.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )
        "indopak_script" -> androidx.compose.ui.text.TextStyle(
            fontFamily = QuranFonts.IndoPakScript,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.7.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )
        else -> androidx.compose.ui.text.TextStyle(
            fontFamily = QuranFonts.PDMSSaleem,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )
    }
}

@Composable
private fun FloatingActionToolbar(
    isExpanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    currentFontSize: Float = 22f,
    minFontSize: Float = 14f,
    maxFontSize: Float = 60f,
    onIncreaseFontSize: () -> Unit = {},
    onDecreaseFontSize: () -> Unit = {},
    isOnRightSide: Boolean = false,
    onDrag: (Offset) -> Unit = {},
    onSideSwap: () -> Unit = {},
    textAlignment: String = "start",
    onSetAlignment: (String) -> Unit = {},
    showTranslation: Boolean = true,
    onToggleTranslation: () -> Unit = {},
    // Font selection
    selectedArabicFont: String = "pdms_saleem",
    onFontClick: () -> Unit = {},
    // Tajweed toggle
    showTajweed: Boolean = false,
    onTajweedClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    // Use Box with consistent positioning to prevent movement
    Box(
        modifier = modifier,
        contentAlignment = if (isOnRightSide) Alignment.TopEnd else Alignment.TopStart
    ) {
        // Render only the active state to avoid touch interception issues
        if (!isExpanded) {
            // Collapsed state - Hint indicator flush with edge (draggable vertically, double-tap to swap sides)
            Surface(
                shape = if (isOnRightSide) {
                    RoundedCornerShape(
                        topStart = 50.dp,
                        topEnd = 0.dp,
                        bottomEnd = 0.dp,
                        bottomStart = 50.dp
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 50.dp,
                        bottomEnd = 50.dp,
                        bottomStart = 0.dp
                    )
                },
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    }
                    .clickable { onExpandedChange(true) }
            ) {
                Row(
                    modifier = Modifier
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOnRightSide) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                        contentDescription = "Expand toolbar",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            // Expanded state - offset from edge for better look
            Box(modifier = Modifier.padding(start = 16.dp)) {
            Surface(
                shape = RoundedCornerShape(50), // Make container very rounded/pill-shaped
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Collapse button at the top
                    FloatingToolbarButton(
                        icon = Icons.Default.ChevronLeft,
                        contentDescription = "Collapse toolbar",
                        selected = false,
                        onClick = { onExpandedChange(false) }
                    )

                    // Increase font size button
                    FloatingToolbarButton(
                        icon = Icons.Default.TextIncrease,
                        contentDescription = "Increase font size",
                        selected = false,
                        onClick = onIncreaseFontSize,
                        enabled = currentFontSize < maxFontSize
                    )

                    // Decrease font size button
                    FloatingToolbarButton(
                        icon = Icons.Default.TextDecrease,
                        contentDescription = "Decrease font size",
                        selected = false,
                        onClick = onDecreaseFontSize,
                        enabled = currentFontSize > minFontSize
                    )

                    // Text alignment buttons - show all three options
                    FloatingToolbarButton(
                        icon = Icons.Default.FormatAlignLeft,
                        contentDescription = "Align text to start",
                        selected = textAlignment == "start",
                        onClick = { onSetAlignment("start") }
                    )

                    FloatingToolbarButton(
                        icon = Icons.Default.FormatAlignCenter,
                        contentDescription = "Align text to center",
                        selected = textAlignment == "center",
                        onClick = { onSetAlignment("center") }
                    )

                    FloatingToolbarButton(
                        icon = Icons.Default.FormatAlignRight,
                        contentDescription = "Align text to end",
                        selected = textAlignment == "end",
                        onClick = { onSetAlignment("end") }
                    )

                    // Toggle translation visibility
                    FloatingToolbarButton(
                        icon = if (showTranslation) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showTranslation) "Hide translation" else "Show translation",
                        selected = showTranslation,
                        onClick = onToggleTranslation
                    )

                    // Tajweed toggle button
                    FloatingToolbarButton(
                        icon = if (showTajweed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                        contentDescription = if (showTajweed) "Disable Tajweed colors" else "Enable Tajweed colors",
                        selected = showTajweed,
                        onClick = onTajweedClick
                    )

                    // Font selection button
                    FloatingToolbarButton(
                        icon = Icons.Default.FontDownload,
                        contentDescription = "Select Arabic font",
                        selected = false,
                        onClick = onFontClick
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun FloatingToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (selected) {
        // Darker filled circular button when selected
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(48.dp),
            shape = CircleShape, // Make selected button circular
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        // Plain icon button when not selected
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Bottom toolbar item - used in the bottom floating toolbar
 * Displays an icon with a small label below it
 */
@Composable
private fun SettingsSectionLabel(text: String, trailingValue: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Normal,
        )
        if (trailingValue != null) {
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Rounded container that groups related settings rows, M3 grouped-list style. */
@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/** Hairline divider between rows inside a [SettingsGroupCard], inset past the icon tile. */
@Composable
private fun SettingsRowDivider() {
    Box(
        modifier = Modifier
            .padding(start = 56.dp, end = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

@Composable
private fun SettingsListRow(
    leadingIcon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Tesla-style monochrome Switch — neutral grays only, no primary green. */
@Composable
private fun teslaSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.surface,
    checkedTrackColor = MaterialTheme.colorScheme.onSurface,
    checkedBorderColor = MaterialTheme.colorScheme.onSurface,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun teslaSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.onSurface,
    activeTrackColor = MaterialTheme.colorScheme.onSurface,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

/**
 * Font chip for the tuning-mode dock — renders "الله" in the actual font so the
 * user picks by seeing the glyphs, not by reading a name.
 */
@Composable
private fun SurahFontGlyphChip(
    fontKey: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "الله",
            fontFamily = getArabicFontFamilyForSelection(fontKey),
            fontSize = 24.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(18.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

/** Pill toggle/action chip for the tuning-mode dock. */
@Composable
private fun SurahTuneChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSheetDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(1.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun SettingsStepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsIconChoice(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surface
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = modifier.height(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun BottomToolbarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false
) {
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.widthIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TranslationSelectionDialog(
    availableTranslations: List<String>,
    currentTranslation: String,
    onDismiss: () -> Unit,
    onTranslationSelected: (String) -> Unit,
    getTranslationDisplayName: (String) -> String
) {
    // Filter out any translations with empty or blank display names
    val validTranslations = remember(availableTranslations) {
        availableTranslations.filter { code ->
            getTranslationDisplayName(code).isNotBlank()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Translation") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(
                    items = validTranslations,
                    key = { it }
                ) { translationCode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTranslationSelected(translationCode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = translationCode == currentTranslation,
                            onClick = { onTranslationSelected(translationCode) }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = getTranslationDisplayName(translationCode),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FontSelectionDialog(
    availableFonts: List<String>,
    currentFont: String,
    onDismiss: () -> Unit,
    onFontSelected: (String) -> Unit,
    getFontDisplayName: (String) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Arabic Font") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                items(
                    items = availableFonts,
                    key = { it }
                ) { fontName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFontSelected(fontName) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = fontName == currentFont,
                            onClick = { onFontSelected(fontName) }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = getFontDisplayName(fontName),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun mapTranslationCodeToAudioLanguage(code: String): AudioLanguage? {
    return when (code) {
        "ar" -> AudioLanguage.ARABIC_ONLY
        "bn" -> AudioLanguage.BENGALI_TRANSLATION
        "en" -> AudioLanguage.ENGLISH_TRANSLATION
        else -> null // No audio support for this translation
    }
}

private fun getAudioLanguageDisplayName(language: AudioLanguage): String {
    return when (language) {
        AudioLanguage.ARABIC_ONLY -> "Arabic"
        AudioLanguage.ENGLISH_TRANSLATION -> "English"
        AudioLanguage.BENGALI_TRANSLATION -> "Bengali"
    }
}

/**
 * ViewModel holder for QuranRepository to support Hilt injection in Composables
 */
@HiltViewModel
class QuranRepositoryHolder @Inject constructor(
    val repository: QuranRepository
) : ViewModel()

/**
 * ViewModel holder for UserDataRepository to support Hilt injection in Composables
 */
@HiltViewModel
class UserDataRepositoryHolder @Inject constructor(
    val repository: UserDataRepository
) : ViewModel()

/**
 * Word Study Dialog
 * Displays word meanings for an ayah
 */
@Composable
fun WordStudyDialog(
    wordStudyData: com.starception.submission.core.qurandatabase.AyahMeaningsItem,
    selectedArabicFont: String = "pdms_saleem",
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "Word Study",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Word Study",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Ayah ${wordStudyData.ayahNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = getArabicFontFamilyForSelection(selectedArabicFont)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                HorizontalDivider()

                // Content with scrollable column
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Arabic text card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = wordStudyData.ayahText,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                                    fontSize = 26.sp,
                                    lineHeight = 44.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }

                    // Word meanings section
                    if (wordStudyData.meanings.isNotEmpty()) {
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Word Meanings",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Text(
                                        text = wordStudyData.meanings,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                                            fontSize = 19.sp,
                                            lineHeight = 36.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        textAlign = TextAlign.Justify,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Add spacing at bottom for comfortable scrolling
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Tafseer Dialog
 * Displays interpretations from 3 Tafseer books with tabbed navigation
 * Features:
 * - Full-screen professional design (95% x 90%)
 * - Three tabs: As-Sa'di, Al-Moyassar, Al-Baghawi
 * - Smooth swipe navigation with HorizontalPager
 * - Arabic labels under each tab
 * - Automatically removes Bismillah from ayah 1 (except Al-Fatiha)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TafseerDialog(
    tafseerData: com.starception.submission.core.qurandatabase.QuranAyahTafseer,
    selectedTafseerBook: String,
    selectedArabicFont: String = "pdms_saleem",
    onTafseerBookSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTabIndex by remember {
        mutableStateOf(
            when (selectedTafseerBook) {
                "saadi" -> 0
                "moysar" -> 1
                "baghawi" -> 2
                else -> 0
            }
        )
    }

    // Sync tab selection with book selection
    LaunchedEffect(selectedTabIndex) {
        val book = when (selectedTabIndex) {
            0 -> "saadi"
            1 -> "moysar"
            2 -> "baghawi"
            else -> "saadi"
        }
        onTafseerBookSelected(book)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "Tafseer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Tafseer",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${tafseerData.surahNameArabic} - آيَة ${tafseerData.ayahNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = getArabicFontFamilyForSelection(selectedArabicFont)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                HorizontalDivider()

                // Arabic Ayah
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = tafseerData.ayahText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                            fontSize = 26.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }

                // Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    "As-Sa'di",
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    "معاصر",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = getArabicFontFamilyForSelection(selectedArabicFont)
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    "Al-Moyassar",
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    "مُبسّط",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = getArabicFontFamilyForSelection(selectedArabicFont)
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    "Al-Baghawi",
                                    fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    "كلاسيكي",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = getArabicFontFamilyForSelection(selectedArabicFont)
                                )
                            }
                        }
                    )
                }

                // Content with pager
                val pagerState = rememberPagerState(
                    initialPage = selectedTabIndex,
                    pageCount = { 3 }
                )

                LaunchedEffect(selectedTabIndex) {
                    pagerState.animateScrollToPage(selectedTabIndex)
                }

                LaunchedEffect(pagerState.currentPage) {
                    selectedTabIndex = pagerState.currentPage
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val tafseerText = when (page) {
                        0 -> tafseerData.tafseerSaadi
                        1 -> tafseerData.tafseerMoysar
                        2 -> tafseerData.tafseerBaghawi
                        else -> ""
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (tafseerText.isNotEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Text(
                                        text = tafseerText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                                            fontSize = 19.sp,
                                            lineHeight = 36.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        textAlign = TextAlign.Justify,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }
                            }
                        }

                        if (tafseerData.ayahMeanings.isNotEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Lightbulb,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "معاني الكلمات",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Normal,
                                                fontFamily = getArabicFontFamilyForSelection(selectedArabicFont)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = tafseerData.ayahMeanings,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = getArabicFontFamilyForSelection(selectedArabicFont),
                                                fontSize = 17.sp,
                                                lineHeight = 30.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            textAlign = TextAlign.Justify
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
}




/**
 * Routes physical volume key presses from MainActivity.onKeyDown into the
 * MushafPagerView while it is composed. Bound on enter (DisposableEffect),
 * unbound on dispose so volume keys regain their normal function elsewhere.
 */
object MushafKeyBus {
    @Volatile private var next: (() -> Unit)? = null
    @Volatile private var prev: (() -> Unit)? = null
    fun bind(next: () -> Unit, prev: () -> Unit) {
        this.next = next
        this.prev = prev
    }
    fun unbind() {
        next = null
        prev = null
    }
    fun handleNext(): Boolean {
        val handler = next ?: return false
        handler()
        return true
    }
    fun handlePrev(): Boolean {
        val handler = prev ?: return false
        handler()
        return true
    }
}

@Composable
private fun ReadingModeToggle(
    isMushafMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(percent = 50)

    // Selection position: 0f = Ayahs (left), 1f = Mushaf (right)
    val selectionFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isMushafMode) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "selectionSlide"
    )

    Surface(
        modifier = modifier
            .height(52.dp)
            .width(240.dp),
        shape = pillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp
    ) {
        BoxWithConstraints(modifier = Modifier.padding(5.dp)) {
            val segmentWidth = maxWidth / 2f
            val indicatorOffset = segmentWidth * selectionFraction

            // Sliding selection indicator
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, pillShape)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                ReadingModeSegment(
                    icon = Icons.Default.ViewDay,
                    label = "Ayahs",
                    selectionFraction = 1f - selectionFraction,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isMushafMode) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onToggle()
                        }
                    }
                )
                ReadingModeSegment(
                    icon = Icons.Default.MenuBook,
                    label = "Mushaf",
                    selectionFraction = selectionFraction,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!isMushafMode) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onToggle()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ReadingModeSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selectionFraction: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = androidx.compose.ui.graphics.lerp(onSurfaceVariant, onPrimary, selectionFraction)
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}
