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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
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
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.R
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
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
import com.starception.submission.feature.quran.surahArtworkRes
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

private const val SURAH_ARTWORK_PORTRAIT_ASPECT_RATIO = 3f / 2f
private val SurahArtworkLandscapeHeight = 180.dp

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
    var surahCacheTranslation by remember { mutableStateOf(currentTranslation) }

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
        // A Surah swipe must retain the already-preloaded incoming pane. Clearing
        // here on every number change made AnimatedContent briefly fall back to
        // the route's original Surah, then destroy/recreate the pane when its real
        // data arrived. That disposal also cleared the app-level Mushaf strip.
        // Only a translation change invalidates the cached text.
        if (surahCacheTranslation != currentTranslation) {
            surahCache.clear()
            surahCacheTranslation = currentTranslation
        }
        val retainedSurahs = listOf(
            currentPlayingSurahNumber - 1,
            currentPlayingSurahNumber,
            currentPlayingSurahNumber + 1,
        ).filter { it in 1..114 }
        retainedSurahs
            .filter { it !in surahCache }
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
        // Keep the cache bounded without removing the outgoing transition pane.
        surahCache.keys.toList().forEach { num ->
            if (num !in retainedSurahs) surahCache.remove(num)
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

    // Keep the toolbar transparent over the artwork, then smoothly add its
    // surface as the header scrolls away.
    val toolbarCollapseDistancePx = with(LocalDensity.current) { 112.dp.toPx() }
    val collapseProgress = remember(scrollState, toolbarCollapseDistancePx) {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (scrollState.firstVisibleItemScrollOffset / toolbarCollapseDistancePx)
                    .coerceIn(0f, 1f)
            }
        }
    }

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
                    // Tajweed offsets are authored against the Uthmani text, so they have
                    // to travel with it: swapping the edition underneath them left every
                    // rule pointing at whatever sat at that index in the other spelling —
                    // the silent-lam colour landed on the following rāʾ, and other rules
                    // on bare vowels, which reads as colour bleeding across a ligature.
                    val pageTajweed = remember(num, baseAyahs, pageAyahs, tajweedAnnotations) {
                        if (pageAyahs === baseAyahs) {
                            tajweedAnnotations
                        } else {
                            val indoPakByNumber = pageAyahs.associate {
                                it.numberInSurah to it.text.substringBefore("\n\n")
                            }
                            baseAyahs.mapNotNull { ayah ->
                                val rules = tajweedAnnotations[ayah.numberInSurah]
                                    ?: return@mapNotNull null
                                val target = indoPakByNumber[ayah.numberInSurah]
                                    ?: return@mapNotNull null
                                ayah.numberInSurah to
                                    com.starception.submission.feature.surah.tajweed
                                        .TajweedEditionMapper.remap(
                                            sourceText = ayah.text.substringBefore("\n\n"),
                                            targetText = target,
                                            annotations = rules,
                                        )
                            }.toMap()
                        }
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
                    tajweedAnnotations = pageTajweed,
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
                            //
                            // Reset the database the state says is missing, NOT the selected
                            // translation — every layout is built on the Arabic text, so
                            // reading English can be blocked by the Arabic database. Resetting
                            // the selected one instead left the broken database cached and
                            // reloaded straight back into this same download prompt.
                            com.starception.submission.core.qurandatabase.QuranTranslationHelper
                                .resetTranslationDatabase(context, state.translationCode)
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

            // Keep the floating Surah name anchored to the exact artwork edge.
            // This must stay in sync with AlbumHeader's dimensions so enlarging
            // the photograph never creates an empty strip above the info card.
            val albumHeaderHeight = if (localIsLandscape) {
                SurahArtworkLandscapeHeight.value.toInt()
            } else {
                (localConfig.screenWidthDp / SURAH_ARTWORK_PORTRAIT_ASPECT_RATIO).toInt()
            }
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
            shape = NiaBottomSheetDefaults.FloatingShape,
            containerColor = Color.Transparent,
            contentColor = NiaBottomSheetDefaults.contentColor(),
            scrimColor = NiaBottomSheetDefaults.scrimColor(),
            tonalElevation = 0.dp,
            dragHandle = null,
        ) {
            NiaBottomSheetTheme {
                NiaBottomSheetFrame {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                    NiaOutlinedButton(
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

            NiaOutlinedButton(
                onClick = {
                    keyboardController?.hide()
                    onSaveNote()
                },
                enabled = noteText.isNotBlank(),
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
    // Reuse the exact chapter-specific artwork shown by the Quran Grid widget.
    val artwork = remember(surah.number) { surahArtworkRes(surah.number) }

    // Parallax factor - image moves at 0.4x the scroll speed for depth effect
    val parallaxOffset = scrollOffset * 0.4f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLandscape) {
                    Modifier.height(SurahArtworkLandscapeHeight)
                } else {
                    // A 3:2 photographic canvas gives the chapter artwork more
                    // presence than the previous shallow 16:9 banner while
                    // preserving room for the Surah information below.
                    Modifier.aspectRatio(SURAH_ARTWORK_PORTRAIT_ASPECT_RATIO)
                }
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh) // Match info card background
            .clipToBounds() // Clip the image so parallax doesn't overflow
    ) {
        // Album cover image with parallax effect
        Image(
            painter = painterResource(artwork),
            contentDescription = "Symbolic artwork for Surah ${surah.nameEnglish}",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Overscan provides enough room for a clearly visible
                    // slower-moving image layer without revealing an edge.
                    scaleX = 1.20f
                    scaleY = 1.20f
                    translationY = parallaxOffset
                        .coerceAtMost(size.height * 0.085f)
                },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )

        // The toolbar begins over this image, so preserve contrast across both
        // bright dawn scenes and darker night artwork without altering the asset.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.20f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.08f),
                    )
                )
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
// font size) so the ring AND the gaps around it scale together when the
// reader changes the font size.
// ---------------------------------------------------------------------------
/**
 * Compact end-of-ayah slot used by the continuous Mushaf. Quran page typography
 * treats the marker as punctuation, not as a separate badge; keeping it below
 * one em lets another Quran word fit on many otherwise sparse justified lines.
 */
private const val MARKER_HEIGHT_EM = 0.92f
/** Translation text is intentionally quieter than Arabic, but its terminal
 * rosette should remain the same physical size as every Arabic ayah rosette. */
private const val MUSHAF_TRANSLATION_FONT_SCALE = 0.65f
private const val TRANSLATION_MARKER_EM_SCALE = 1f / MUSHAF_TRANSLATION_FONT_SCALE
/** Ring height as a fraction of the slot height. */
private const val MARKER_ORNAMENT_FILL = 0.74f

/**
 * Pause-mark height as a fraction of the ayah ring it sits over.
 *
 * Print sets these signs small — they are an annotation on the verse, not part of it —
 * and a mark much larger than this reads as a second marker stacked on the first.
 */
private const val PAUSE_MARK_SIZE_FRACTION = 0.52f

/**
 * Clearance between the lowest ink of the pause sign and the ayah ring's top edge, as a
 * fraction of the ring's height.
 *
 * This is what [MARKER_ORNAMENT_FILL] was reduced for: the medallion no longer fills its
 * slot, and the room that frees inside the line is where the sign goes, the way the
 * printed page stacks them.
 */
private const val PAUSE_MARK_GAP = 0.06f

/**
 * The letters a pause sign is printed as.
 *
 * The source encodes these as combining marks (SMALL HIGH TAH, SMALL HIGH LAM ALEF, and
 * so on), which exist to sit on a base letter. Standing alone they will not shape at all
 * — neither Canvas.drawText nor a Compose Text draws anything for them, verified on
 * device with the glyph reaching the painter and its box measuring zero width. What the
 * printed Mushaf actually sets above the medallion is the ordinary letter each sign
 * abbreviates, so that is what is drawn.
 *
 * The edition's private-use signs are compound forms with no letter equivalent; they are
 * passed through, since those slots do hold real spacing glyphs in this font.
 */
private fun pauseMarkGlyph(mark: String): String = buildString {
    mark.forEach { character ->
        append(
            when (character) {
                'ؕ' -> "ط"
                'ۖ' -> "صلے"
                'ۗ' -> "قلے"
                'ۘ' -> "م"
                'ۙ' -> "لا"
                'ۚ' -> "ج"
                'ۛ' -> "∴"
                'ۜ' -> "س"
                '۩' -> "۩"
                else -> character.toString()
            },
        )
    }
}
/** Quran.com-style end-of-ayah rings are circular. */
private const val MARKER_ASPECT = 1f
/** Gap reserved in the text flow on each side of the ornament. This sets how
 *  much ink-free room the slot contributes; the drawing pass then CENTRES the
 *  ornament in the true ink gap (see computeInkMarkerGeometries), so the space
 *  the eye sees is (ink-free width - ornament width) / 2 — the reserved gap
 *  plus whatever justification stretches into it. Shrink these to tighten the
 *  ornament against its neighbouring words; a justified line redistributes part
 *  of the reclaimed width across its other spaces, so the on-screen gap drops
 *  by somewhat less than the amount removed here. */
private const val MARKER_GAP_BEFORE_EM = 0.08f
private const val MARKER_GAP_AFTER_EM = 0.08f
/** Visual gap between the ornament and the MEASURED ink edge of the ayah's
 *  last word. Placement is ink-accurate (see computeInkMarkerGeometries): the
 *  page text is rendered once to an offscreen bitmap and the true glyph edges
 *  are scanned, so this is the gap the eye actually sees — no per-glyph
 *  overhang guessing. */
private const val MARKER_INK_GAP_EM = 0.08f

/**
 * A continuous Quran page is intentionally denser than the ayah-card reader.
 * The user's selected size still controls zoom; this scale only gives Mushaf
 * mode the page-like word count and balanced line lengths it needs.
 */
private const val MUSHAF_TYPESETTING_SCALE = 0.68f
/** Offscreen ink-scan bitmap scale (half resolution is ample for edges). */
private const val MARKER_INK_SCAN_SCALE = 0.5f

// Selected ayah: a soft cloud around the actual shaped words. Basing the path
// on word ink keeps justified RTL whitespace from becoming a page-wide card.
private val MUSHAF_WORD_CLOUD_HPAD = 6.dp
private val MUSHAF_WORD_CLOUD_VPAD = 5.dp
private val MUSHAF_WORD_CLOUD_RADIUS = 16.dp

/** Joins neighbouring selected words on the same visual line into one cloud.
 * Distant justified words remain separate, while normal word spacing no longer
 * produces a row of overlapping pill/scallop shapes. */
private fun List<androidx.compose.ui.geometry.Rect>.toWordCloudClusters():
    List<androidx.compose.ui.geometry.Rect> {
    if (isEmpty()) return emptyList()

    fun union(
        first: androidx.compose.ui.geometry.Rect,
        second: androidx.compose.ui.geometry.Rect,
    ) = androidx.compose.ui.geometry.Rect(
        left = minOf(first.left, second.left),
        top = minOf(first.top, second.top),
        right = maxOf(first.right, second.right),
        bottom = maxOf(first.bottom, second.bottom),
    )

    val visualLines = mutableListOf<MutableList<androidx.compose.ui.geometry.Rect>>()
    sortedBy { it.center.y }.forEach { rect ->
        val line = visualLines.lastOrNull()
        val lineBounds = line?.reduce(::union)
        val sameLine = lineBounds != null &&
            kotlin.math.abs(rect.center.y - lineBounds.center.y) <=
            maxOf(rect.height, lineBounds.height) * 0.58f
        if (sameLine) line.add(rect) else visualLines.add(mutableListOf(rect))
    }

    return visualLines.flatMap { line ->
        val clusters = mutableListOf<androidx.compose.ui.geometry.Rect>()
        line.sortedBy { it.left }.forEach { rect ->
            val previous = clusters.lastOrNull()
            val joinsPrevious = previous != null &&
                rect.left - previous.right <= maxOf(rect.height, previous.height) * 0.72f
            if (joinsPrevious) {
                clusters[clusters.lastIndex] = union(previous, rect)
            } else {
                clusters += rect
            }
        }
        clusters
    }
}

private fun List<androidx.compose.ui.geometry.Rect>.toWordCloudPath(
    radiusPx: Float,
): androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path().apply {
    this@toWordCloudPath.forEach { rect ->
        if (rect.width > 0f && rect.height > 0f) {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect,
                    androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                ),
            )
            // A width-aware row of overlapping puffs gives short words and long
            // justified lines the same cloud silhouette. The round-rect body is
            // retained underneath so no glyph or Quranic sign can fall through
            // the gaps between puffs.
            val puffRadius = minOf(rect.height * 0.25f, radiusPx * 0.72f)
            if (puffRadius > 0f) {
                val puffCount = kotlin.math.ceil(
                    rect.width / (puffRadius * 1.48f),
                ).toInt().coerceIn(3, 24)
                val radiusPattern = floatArrayOf(0.84f, 1.08f, 0.92f, 1.16f, 0.88f)
                repeat(puffCount) { index ->
                    val fraction = (index + 0.5f) / puffCount
                    val radius = puffRadius * radiusPattern[index % radiusPattern.size]
                    val center = androidx.compose.ui.geometry.Offset(
                        x = rect.left + rect.width * fraction,
                        y = rect.top + puffRadius * if (index % 2 == 0) 0.22f else 0.32f,
                    )
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = center,
                            radius = radius,
                        ),
                    )
                }
            }
        }
    }
}

private data class MushafSelectionCloud(
    val path: androidx.compose.ui.graphics.Path,
    val sparkleCenters: List<androidx.compose.ui.geometry.Offset>,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMushafSparkle(
    center: androidx.compose.ui.geometry.Offset,
    radius: Float,
    color: Color,
) {
    val inner = radius * 0.28f
    val sparkle = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(sparkle, color)
}

/** Inline-content tag for the invisible line filler appended to non-final page
 *  slices. Android never justifies a paragraph's last line, so a sliced page's
 *  bottom line collapses to natural width even though the full-text layout had
 *  it justified. The filler is wider than any line, wraps alone onto a clipped
 *  zero-ink line, and thereby keeps the real last content line justified. */
private const val MUSHAF_LINE_FILLER_TAG = "mushafLineFiller"

/** Annotation around the inline translation. Besides keeping its character
 * range exact after page slicing, this lets the page draw a reveal mask over
 * only the translation without rebuilding/re-measuring the Quran text on every
 * animation frame. */
private const val MUSHAF_TRANSLATION_TAG = "mushafTranslation"

/** Full-size ayah ornament that follows an inline translation. Its Center
 * alignment uses the final line's expanded metrics (rather than the preceding
 * glyph metrics), keeping the complete frame vertically aligned with the text
 * without letting it rise into the preceding wrapped line. */
private const val MUSHAF_TRANSLATION_ORNAMENT_TAG = "translationAyahOrnament"

/** Exact Arabic-script range for one ayah. Page-local copies of this annotation
 * are the sole source of highlight geometry, so a translation continuation can
 * never be mistaken for Arabic belonging to the selected ayah. */
private const val MUSHAF_ARABIC_AYAH_TAG = "mushafArabicAyah"

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

/**
 * Per-line rectangles covering [start, end) — one clean box per visual line.
 * `getPathForRange` returns the same coverage as an opaque Path whose sub-rects
 * can't be reached, so it can only be drawn square; going through the line
 * metrics instead lets the selection be rounded and inset per line. min/max on
 * the two horizontal positions keeps it correct in RTL.
 */
private fun androidx.compose.ui.text.TextLayoutResult.rangeLineRects(
    start: Int,
    end: Int,
): List<androidx.compose.ui.geometry.Rect> {
    if (end <= start) return emptyList()
    val rawText = layoutInput.text.text
    data class HorizontalBounds(
        var left: Float = Float.POSITIVE_INFINITY,
        var right: Float = Float.NEGATIVE_INFINITY,
    )

    // Logical offsets are unreliable at a mixed-ayah RTL line boundary:
    // getLineForOffset(start) can report the following line even though the
    // selected glyph itself is painted on the preceding visual line. Assign
    // every selected glyph by its actual vertical box instead.
    val boundsByVisualLine = sortedMapOf<Int, HorizontalBounds>()

    fun addVisualBounds(box: androidx.compose.ui.geometry.Rect) {
        if (box.width <= 0f || box.height <= 0f) return
        val visualLine = getLineForVerticalPosition(box.center.y)
        val bounds = boundsByVisualLine.getOrPut(visualLine) { HorizontalBounds() }
        bounds.left = minOf(bounds.left, box.left)
        bounds.right = maxOf(bounds.right, box.right)
    }

    var wordStart = -1
    fun appendWord(wordEnd: Int) {
        if (wordStart < 0 || wordEnd <= wordStart) return
        for (offset in wordStart until wordEnd) {
            val box = getBoundingBox(offset)
            if (box.width > 0f && box.height > 0f) {
                addVisualBounds(box)
            }
        }
        // Character boxes can be only PARTIALLY missing for a shaped Arabic
        // word. In 6:137, Compose exposed boxes for part of the line-final
        // "وَمَا" but omitted its joined edge, so the old all-or-nothing
        // fallback never ran and the complete word sat outside the highlight.
        // Always include the shaped range for this ONE word. Keeping the range
        // word-local prevents it from absorbing a neighbouring ayah's RTL run.
        val shapedBounds = getPathForRange(wordStart, wordEnd).getBounds()
        if (shapedBounds.width > 0f && shapedBounds.height > 0f) {
            addVisualBounds(shapedBounds)
        }
        wordStart = -1
    }

    for (offset in start until end) {
        val char = rawText.getOrNull(offset) ?: continue
        if (char.isWhitespace() || char in MARKER_TRAILING_INVISIBLES) {
            appendWord(offset)
        } else if (wordStart < 0) {
            wordStart = offset
        }
    }
    appendWord(end)

    // Every visual line strictly between the range's first and last lines is
    // selected in full. Trust that invariant instead of Android's per-offset
    // boxes: StaticLayout can omit the final shaped RTL word at a soft wrap
    // (6:137 "وَمَا", 2:161 "اللَّهِ") even when both character boxes and the
    // word-local selection path are queried. The first/last lines remain
    // glyph-bounded because they may share space with a neighbouring ayah.
    val firstSelectedLine = boundsByVisualLine.keys.firstOrNull()
    val lastSelectedLine = boundsByVisualLine.keys.lastOrNull()
    if (firstSelectedLine != null && lastSelectedLine != null &&
        lastSelectedLine - firstSelectedLine > 1
    ) {
        for (line in (firstSelectedLine + 1) until lastSelectedLine) {
            val lineLeft = getLineLeft(line)
            val lineRight = getLineRight(line)
            if (lineRight > lineLeft) {
                val bounds = boundsByVisualLine.getOrPut(line) { HorizontalBounds() }
                bounds.left = minOf(bounds.left, lineLeft)
                bounds.right = maxOf(bounds.right, lineRight)
            }
        }
    }

    return boundsByVisualLine.mapNotNull { (line, bounds) ->
        if (!bounds.left.isFinite() || !bounds.right.isFinite() || bounds.right <= bounds.left) {
            null
        } else {
            androidx.compose.ui.geometry.Rect(
                bounds.left,
                getLineTop(line),
                bounds.right,
                getLineBottom(line),
            )
        }
    }
}

/** Tight shaped-word bounds for a selected logical range. Unlike
 * [rangeLineRects], this deliberately preserves the whitespace between words so
 * the visual treatment reads as a cloud around the Arabic rather than a card. */
private fun androidx.compose.ui.text.TextLayoutResult.rangeWordRects(
    start: Int,
    end: Int,
): List<androidx.compose.ui.geometry.Rect> {
    if (end <= start) return emptyList()
    val rawText = layoutInput.text.text
    val result = mutableListOf<androidx.compose.ui.geometry.Rect>()
    var wordStart = -1

    fun appendWord(wordEnd: Int) {
        if (wordStart < 0 || wordEnd <= wordStart) return
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var inkTop = Float.POSITIVE_INFINITY
        var inkBottom = Float.NEGATIVE_INFINITY
        var visualLine = -1
        for (offset in wordStart until wordEnd) {
            val box = getBoundingBox(offset)
            if (box.width > 0f && box.height > 0f) {
                left = minOf(left, box.left)
                right = maxOf(right, box.right)
                if (visualLine < 0) visualLine = getLineForVerticalPosition(box.center.y)
            }
        }
        // Joined Arabic can expose zero-width boxes for part of a cluster. The
        // shaped word path restores those visible edges without absorbing the
        // justified space or a neighbouring ayah.
        val shaped = getPathForRange(wordStart, wordEnd).getBounds()
        if (shaped.width > 0f && shaped.height > 0f) {
            left = minOf(left, shaped.left)
            right = maxOf(right, shaped.right)
            inkTop = minOf(inkTop, shaped.top)
            inkBottom = maxOf(inkBottom, shaped.bottom)
            if (visualLine < 0) visualLine = getLineForVerticalPosition(shaped.center.y)
        }

        // StaticLayout can return neither boxes nor a shaped path for a complete
        // joined word at an RTL soft-wrap edge. Cursor advances are independent
        // of those APIs, so use the tighter primary/secondary endpoint pair as a
        // final word-local fallback (never a whole-line fallback).
        val cursorLine = getLineForOffset((wordEnd - 1).coerceAtLeast(wordStart))
        val lineLeft = getLineLeft(cursorLine)
        val lineRight = getLineRight(cursorLine)
        val lineWidth = (lineRight - lineLeft).coerceAtLeast(1f)
        // At a bidi boundary the useful start and end cursors are sometimes on
        // opposite affinities. Trying only primary-primary/secondary-secondary
        // therefore collapses a complete Arabic word to zero width. Consider all
        // four local pairs and keep the tightest sane one when glyph geometry is
        // unavailable.
        val startCursors = listOf(
            getHorizontalPosition(wordStart, usePrimaryDirection = true),
            getHorizontalPosition(wordStart, usePrimaryDirection = false),
        )
        val endCursors = listOf(
            getHorizontalPosition(wordEnd, usePrimaryDirection = true),
            getHorizontalPosition(wordEnd, usePrimaryDirection = false),
        )
        val cursorCandidates = startCursors.flatMap { startX ->
            endCursors.mapNotNull { endX ->
                val candidateLeft = minOf(startX, endX).coerceIn(lineLeft, lineRight)
                val candidateRight = maxOf(startX, endX).coerceIn(lineLeft, lineRight)
                androidx.compose.ui.geometry.Rect(
                    candidateLeft,
                    getLineTop(cursorLine),
                    candidateRight,
                    getLineBottom(cursorLine),
                ).takeIf { it.width > 0.5f && it.width < lineWidth * 0.92f }
            }
        }.distinctBy { it.left to it.right }
        val cursorBounds = if (left.isFinite() && right.isFinite()) {
            cursorCandidates
                .filter { it.right >= left - 1f && it.left <= right + 1f }
                .maxByOrNull { it.width }
        } else {
            cursorCandidates.minByOrNull { it.width }
        }
        if (cursorBounds != null) {
            left = minOf(left, cursorBounds.left)
            right = maxOf(right, cursorBounds.right)
            if (visualLine < 0) visualLine = cursorLine
        }
        if (visualLine >= 0 && left.isFinite() && right.isFinite() && right > left) {
            val top = inkTop.takeIf { it.isFinite() } ?: getLineTop(visualLine)
            val bottom = inkBottom.takeIf { it.isFinite() && it > top }
                ?: getLineBottom(visualLine)
            // At an RTL soft wrap, StaticLayout can report the final visual
            // word's left edge several glyphs too far to the right (notably
            // Quranic final forms with combining signs). Protect only that
            // trailing edge with a line-height-relative allowance; applying
            // this to every word would make otherwise-correct clouds too wide.
            val visibleLineEnd = getLineEnd(visualLine, visibleEnd = true)
            val isRtlSoftWrapTail = visualLine < lineCount - 1 && wordEnd >= visibleLineEnd
            val protectedLeft = if (isRtlSoftWrapTail) {
                (left - (getLineBottom(visualLine) - getLineTop(visualLine)) * 0.38f)
                    .coerceAtLeast(getLineLeft(visualLine))
            } else {
                left
            }
            result += androidx.compose.ui.geometry.Rect(
                protectedLeft,
                top,
                right,
                bottom,
            )
        }
        wordStart = -1
    }

    for (offset in start until end) {
        val char = rawText.getOrNull(offset) ?: continue
        if (char.isWhitespace() || char in MARKER_TRAILING_INVISIBLES) {
            appendWord(offset)
        } else if (wordStart < 0) {
            wordStart = offset
        }
    }
    appendWord(end)
    return result
}

/**
 * One Tajweed rule's colour and the characters it covers, in the coordinates of whichever
 * string carries it — the master string while paginating, page-local once sliced.
 *
 * Kept out of the [AnnotatedString] on purpose: a colour span that begins inside a word
 * splits the shaping run, and Arabic letters either side of that split stop joining.
 */
private data class TajweedSpan(val start: Int, val end: Int, val color: androidx.compose.ui.graphics.Color)

/** The paginated Mushaf's master string and everything indexed against it. */
private data class MushafMaster(
    val text: AnnotatedString,
    val placeholders: List<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.Placeholder>>,
    val ayahRanges: List<Pair<Int, IntRange>>,
    val tajweed: List<TajweedSpan>,
)

private data class MarkerGeometry(
    val digits: String,
    val isTranslation: Boolean,
    val centerX: Float,
    val centerY: Float,
    val left: Float,
    val top: Float,
    val w: Float,
    val h: Float,
    /** Waqf sign printed above this ayah's medallion; empty when the ayah has none. */
    val pauseMark: String = "",
)

/**
 * Separates the ayah digits from the pause mark inside one marker slot's text.
 *
 * INVISIBLE SEPARATOR carries no width and no joining behaviour, and the slot's
 * characters are replaced by the medallion anyway — the string exists only so the
 * geometry pass and the painter can read back what the builder put in.
 */
private const val PAUSE_MARK_SEPARATOR = '\u2063'

/**
 * A sign the IndoPak edition prints above the line rather than in it.
 *
 * The waqf letters (U+0615 ط, U+06D6-U+06DC صلے ج لا م ۛ, U+06E9 sajdah) are combining
 * marks, and the private-use range is where this edition's font keeps its compound stop
 * signs. Both are printed above their word — or, at an ayah's end, above its medallion.
 */
private fun Char.isPauseMark(): Boolean =
    this == '\u0615' || this in '\u06D6'..'\u06DC' || this == '\u06E9' ||
        this in '\uE000'..'\uF8FF'

/**
 * Splits an ayah into the text the line sets and the pause mark that belongs above its
 * end medallion.
 *
 * The source text ends these ayahs with SPACE + mark + RIGHT-TO-LEFT MARK. A combining
 * mark preceded by a space has no base letter to sit on, so the shaper gives it a box of
 * its own: it floated mid-line as a stray glyph, and its two spaces became justification
 * expansion points that pulled the surrounding words apart. Taking it out of the flow
 * fixes the spacing as well as the placement — the printed Mushaf sets it over the
 * medallion, which is what [MarkerGeometry.pauseMark] is for.
 */
private fun splitTrailingPauseMark(raw: String): Pair<String, String> {
    var end = raw.length
    val marks = StringBuilder()
    while (end > 0) {
        val c = raw[end - 1]
        when {
            // Direction and width controls sit between the mark and the word.
            c == '\u200F' || c == '\u200E' || c == '\u200B' || c == '\uFEFF' ||
                c.isWhitespace() ->
                end--

            c.isPauseMark() -> {
                marks.append(c)
                end--
            }

            else -> return raw.substring(0, end) to marks.reverse().toString()
        }
    }
    return "" to marks.reverse().toString()
}


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
    isActive: () -> Boolean = { true },
): List<MarkerGeometry> {
    val markerAnnotations = pageText
        .getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, pageText.length)
        .sortedBy { it.start }
    if (markerAnnotations.isEmpty()) return emptyList()

    val ornamentPlaceholder = androidx.compose.ui.text.Placeholder(
        MARKER_SLOT_WIDTH_EM.em, MARKER_HEIGHT_EM.em,
        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
    )
    val translationOrnamentPlaceholder = androidx.compose.ui.text.Placeholder(
        (MARKER_SLOT_WIDTH_EM * TRANSLATION_MARKER_EM_SCALE).em,
        (MARKER_HEIGHT_EM * TRANSLATION_MARKER_EM_SCALE).em,
        androidx.compose.ui.text.PlaceholderVerticalAlign.Center,
    )
    val layout = textMeasurer.measure(
        text = pageText,
        style = style,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidthPx),
        placeholders = markerAnnotations.map {
            AnnotatedString.Range(
                if (it.item == MUSHAF_LINE_FILLER_TAG) {
                    mushafLineFillerPlaceholder(maxWidthPx, emPx)
                } else if (it.item == MUSHAF_TRANSLATION_ORNAMENT_TAG) {
                    translationOrnamentPlaceholder
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
    try {
        if (!isActive()) throw kotlinx.coroutines.CancellationException()
        val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
        canvas.save()
        canvas.scale(scale, scale)
        androidx.compose.ui.text.TextPainter.paint(canvas, layout)
        canvas.restore()
        val pixels = bitmap.toPixelMap()

        fun columnHasInk(x: Int, top: Int, bottom: Int): Boolean {
            if (!isActive()) throw kotlinx.coroutines.CancellationException()
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
            if (!isActive()) throw kotlinx.coroutines.CancellationException()
        if (rect == null) return@forEachIndexed
        val annotation = markerAnnotations.getOrNull(i) ?: return@forEachIndexed
        if (annotation.item == MUSHAF_LINE_FILLER_TAG) return@forEachIndexed
        // The slot holds the digits and, after the separator, the ayah's pause mark.
        val slotText = pageText.text.substring(annotation.start, annotation.end)
        val digits = slotText.substringBefore(PAUSE_MARK_SEPARATOR)
        val pauseMark = slotText.substringAfter(PAUSE_MARK_SEPARATOR, "")
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
        val lineRight = layout.getLineRight(markerLine)
        val slotCenter = (rect.left + rect.right) / 2f
        val belongsToTranslation = pageText
            .getStringAnnotations(
                tag = MUSHAF_TRANSLATION_TAG,
                start = annotation.start,
                end = annotation.end,
            )
            .isNotEmpty()
        val window = 2f * emPx
        // Line extent in scan-bitmap columns — the outward ink walks stop here so
        // a marker can never drift into a neighbouring line's margin.
        val lineLeftPx = (lineLeft * scale).toInt().coerceAtLeast(0)
        val lineRightPx = (lineRight * scale).toInt().coerceAtMost(pixels.width - 1)

        var detectedGapLeft: Float? = null
        var detectedGapRight: Float? = null
        val centerX: Float
        if (belongsToTranslation) {
            // Translation markers already sit in an explicit LTR placeholder
            // immediately after the translated text. Arabic ink-gap scanning
            // is direction-specific and can drag that slot back across Bengali
            // glyphs, so preserve the layout engine's authoritative position.
            centerX = slotCenter
        } else if (nextOnSameLine) {
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
            // A run can only host the medallion if it is at least as wide as the
            // ornament, so width gates candidacy BEFORE overlap ranks it. Without
            // that gate a hairline stroke splitting the true gap lets a sliver win
            // on overlap alone: Ar-Rahman marker 3 centred a 98px medallion in a
            // 34px fragment — an 11px swash tip had cut the real 394px gap in two,
            // and the fragment sat dead-centre in the slot so it out-scored it —
            // planting the ornament on top of the final noon of ٱلْإِنسَٰنَ.
            // Fitting runs are ranked by slot overlap exactly as before; only when
            // NO run fits do we fall back to the original overlap-only choice, so
            // lines whose gap is genuinely narrower than the ornament (Al-A'raf
            // 49/52, Al-Muddaththir 43) keep their tuned placement untouched.
            val slotL = (rect.left * scale).toInt()
            val slotR = (rect.right * scale).toInt()
            val minFitW = w * scale
            var bestGapL = -1
            var bestGapR = -1
            var bestOverlap = -1
            var bestWidth = -1
            var bestFits = false
            var runStart = -1
            var x = winL
            while (x <= winR + 1) {
                val ink = x <= winR && columnHasInk(x, bandTop, bandBottom)
                if (!ink && runStart < 0) runStart = x
                if ((ink || x == winR + 1) && runStart >= 0) {
                    val runEnd = x
                    val overlap = (minOf(runEnd, slotR) - maxOf(runStart, slotL)).coerceAtLeast(0)
                    val width = runEnd - runStart
                    val fits = width >= minFitW
                    val better = when {
                        fits != bestFits -> fits
                        overlap != bestOverlap -> overlap > bestOverlap
                        else -> width > bestWidth
                    }
                    if (better) {
                        bestFits = fits
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
                // The scan window is derived from the slot and the next word's
                // layout position, so it can CLIP the chosen run; centring in a
                // clipped run biases the medallion toward the clipped side —
                // Ar-Rahman 55:3 ended up 69px from its own verse but 228px from
                // the next word. Walk both edges out to the real ink (bounded by
                // the line's own extent) and centre in the TRUE gap, so the space
                // reads even on both sides. Runs already bounded by ink don't move.
                var trueL = bestGapL
                while (trueL - 1 >= lineLeftPx && !columnHasInk(trueL - 1, bandTop, bandBottom)) trueL--
                var trueR = bestGapR
                while (trueR + 1 <= lineRightPx && !columnHasInk(trueR + 1, bandTop, bandBottom)) trueR++
                detectedGapLeft = trueL / scale
                detectedGapRight = trueR / scale
                ((trueL + trueR) / 2f) / scale
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
            // No text follows on this line, so the space after the ornament is
            // free — sense both sides and centre between the line's end and the
            // verse's ink edge rather than hugging at a fixed gap. Hugging gave
            // line-end markers ~0.3em of breathing room while mid-line ones sit
            // at 0.6-1.5em, which read as cramped against the verse. Still never
            // closer than MARKER_INK_GAP_EM, and never outside the line.
            centerX = ((lineLeft + rightInk) / 2f)
                .coerceAtMost(rightInk - MARKER_INK_GAP_EM * emPx - w / 2f)
                .coerceAtLeast(lineLeft + w / 2f)
        }
        // The default font-metric center sits slightly below the visible center
        // of Bengali/Latin translation glyphs. Nudge only the compact gloss
        // ornament upward; Arabic markers retain their measured placement.
        val translationOpticalOffsetY = if (belongsToTranslation) -h * 0.10f else 0f
        // Prefer keeping a mid-line ornament inside its placeholder, but never
        // enforce that preference by pushing it out of the ink-safe interval.
        // Arabic final forms can overhang deeply into the nominal slot (most
        // visibly in short Surahs such as Al-Kafirun); the old slot-only clamp
        // placed the ornament back on top of that measured ink.
        val safeCenterX = if (nextOnSameLine && !belongsToTranslation) {
            val slotMin = rect.left + w / 2f
            val slotMax = rect.right - w / 2f
            val gapLeft = detectedGapLeft
            val gapRight = detectedGapRight
            if (gapLeft != null && gapRight != null) {
                val minimumInkGap = MARKER_INK_GAP_EM * emPx
                val inkMin = gapLeft + minimumInkGap + w / 2f
                val inkMax = gapRight - minimumInkGap - w / 2f
                val intersectionMin = maxOf(slotMin, inkMin)
                val intersectionMax = minOf(slotMax, inkMax)
                when {
                    // Ideal: both the reserved slot and measured ink clearance agree.
                    intersectionMin <= intersectionMax ->
                        centerX.coerceIn(intersectionMin, intersectionMax)
                    // Deep glyph overhang: prioritize the real ink-safe interval.
                    inkMin <= inkMax -> centerX.coerceIn(inkMin, inkMax)
                    // The gap cannot provide the preferred clearance, but can at
                    // least contain the complete ornament without touching ink.
                    gapRight - gapLeft >= w -> centerX.coerceIn(
                        gapLeft + w / 2f,
                        gapRight - w / 2f,
                    )
                    else -> centerX.coerceIn(slotMin, slotMax)
                }
            } else {
                centerX.coerceIn(slotMin, slotMax)
            }
        } else {
            centerX
        }
        result.add(
            MarkerGeometry(
                digits = digits,
                isTranslation = belongsToTranslation,
                centerX = safeCenterX,
                centerY = rect.center.y + translationOpticalOffsetY,
                left = safeCenterX - w / 2f,
                top = rect.top + (rect.height - h) / 2f + translationOpticalOffsetY,
                w = w,
                h = h,
                pauseMark = pauseMark,
            ),
        )
        }
        return result
    } finally {
        // ImageBitmap owns an Android Bitmap allocation. Waiting for finalization
        // retained roughly one page-sized scan buffer per turn; moving quickly
        // through a long surah could therefore hit the 256 MB app heap before GC
        // reclaimed them. Geometry is plain floats now, so release the scan surface
        // deterministically even when a newer page cancels this scan.
        bitmap.asAndroidBitmap().recycle()
    }
}

// ---------------------------------------------------------------------------
// Mushaf page — renders pre-paginated justified text
// ---------------------------------------------------------------------------
@Composable
private fun MushafPageWithFrame(
    pageText: AnnotatedString,
    /**
     * Tajweed rules for this page, painted over the finished layout rather than carried
     * as colour spans — a span boundary inside a word breaks the cursive join.
     */
    tajweed: List<TajweedSpan> = emptyList(),
    arabicFont: String,
    arabicFontSize: Float,
    /** Page-specific leading used to fill complete pages vertically. */
    lineHeightSp: Float = arabicFontSize * 1.45f,
    showBismillah: Boolean,
    onAyahLongPress: (Int) -> Unit,
    ayahRanges: List<Pair<Int, IntRange>>,
    highlightedAyahNumber: Int? = null,
    /** Double-tap on an ayah — toggles its translation inline in the page text. */
    onAyahDoubleTap: (Int) -> Unit = {},
    /** Short out-and-back horizontal scrub over one ayah — same action as double-tap. */
    onAyahRub: (Int) -> Unit = {},
    /**
     * True only for the page the reader is on. Lazy pagers may keep neighbouring
     * pages composed for prefetch, so off-pages install no pointer input and can
     * never answer a gesture intended for the visible page.
     */
    interactive: Boolean = true,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = emptyMap(),
    /** Precomputed ink-accurate marker geometry for THIS page (null while pending). */
    inkGeometries: List<MarkerGeometry>? = null,
    /** Shared show/hide progress so removal waits for the fade-out to finish. */
    translationVisibility: Float = 1f,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val glowColor = MaterialTheme.colorScheme.primary
    val markerColor = onSurfaceColor.copy(alpha = 0.90f)
    // PageCurl snapshots its first page draw. If precise marker geometry is still
    // being prepared during that draw, use the reserved inline slots themselves
    // to render the rosettes so a cached page can never lose its ayah endings.
    // Once geometry is ready, the two ink-aware Canvas passes below take over.
    val markersRenderedInline = inkGeometries == null
    val renderedInlineContent = remember(
        inlineContent,
        markersRenderedInline,
        arabicFontSize,
    ) {
        if (!markersRenderedInline) {
            inlineContent
        } else {
            buildMap {
                putAll(inlineContent)
                inlineContent["ayahOrnament"]?.placeholder?.let { placeholder ->
                    put(
                        "ayahOrnament",
                        androidx.compose.foundation.text.InlineTextContent(placeholder) { digits ->
                            MushafInlineAyahOrnament(
                                digits = digits,
                                arabicFontSize = arabicFontSize,
                            )
                        },
                    )
                }
                inlineContent[MUSHAF_TRANSLATION_ORNAMENT_TAG]?.placeholder?.let { placeholder ->
                    put(
                        MUSHAF_TRANSLATION_ORNAMENT_TAG,
                        androidx.compose.foundation.text.InlineTextContent(placeholder) { digits ->
                            MushafInlineAyahOrnament(
                                digits = digits,
                                arabicFontSize = arabicFontSize,
                            )
                        },
                    )
                }
            }
        }
    }

    // Rosette is now part of the masterString text flow (U+06DD + digits
    // styled with Amiri Quran). No inline content needed here.

    // The selected ayah is drawn as a GLOW behind the text (pass-1 canvas below).
    // Use the master-string ayah range for the START: unlike a page-local string
    // annotation it survives an RTL line shared with the preceding ayah without
    // Compose moving its start to the next visual line. Keep the Arabic annotation
    // for the END so neither the ornament nor inline translation is highlighted.
    val highlightRange = remember(highlightedAyahNumber, pageText, ayahRanges) {
        val target = highlightedAyahNumber ?: return@remember null
        val hitRange = ayahRanges.firstOrNull { it.first == target }?.second
            ?: return@remember null
        val arabicRange = pageText
            .getStringAnnotations(MUSHAF_ARABIC_AYAH_TAG, 0, pageText.length)
            .firstOrNull { it.item == target.toString() }
            ?: return@remember null
        val exactStart = hitRange.first.coerceIn(0, pageText.length)
        val exactEnd = arabicRange.end.coerceIn(exactStart, pageText.length)
        if (exactEnd > exactStart) exactStart to exactEnd else null
    }
    // Captured page layout — turns that char range into a path for the glow.
    // Null until the first layout pass, so the glow appears one frame late.
    val pageLayout = remember(pageText) {
        androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
    }
    val cloudDensity = LocalDensity.current
    val selectionCloud = remember(pageLayout.value, highlightRange, inkGeometries) {
        val laid = pageLayout.value ?: return@remember null
        val range = highlightRange ?: return@remember null
        val hPad = with(cloudDensity) { MUSHAF_WORD_CLOUD_HPAD.toPx() }
        val vPad = with(cloudDensity) { MUSHAF_WORD_CLOUD_VPAD.toPx() }
        val radius = with(cloudDensity) { MUSHAF_WORD_CLOUD_RADIUS.toPx() }
        val layoutWidth = laid.size.width.toFloat()
        val layoutHeight = laid.size.height.toFloat()
        val wordClouds = laid.rangeWordRects(range.first, range.second).mapNotNull { rect ->
            val padded = androidx.compose.ui.geometry.Rect(
                (rect.left - hPad).coerceAtLeast(0f),
                (rect.top - vPad).coerceAtLeast(0f),
                (rect.right + hPad).coerceAtMost(layoutWidth),
                (rect.bottom + vPad).coerceAtMost(layoutHeight),
            )
            padded.takeIf { it.width > 0f && it.height > 0f }
        }
        if (wordClouds.isEmpty()) return@remember null
        val cloudClusters = wordClouds.toWordCloudClusters()
        val sparkles = buildList {
            cloudClusters.forEachIndexed { index, rect ->
                add(androidx.compose.ui.geometry.Offset(rect.right - hPad * 0.3f, rect.top))
                if (rect.width > rect.height * 2.1f) {
                    add(androidx.compose.ui.geometry.Offset(rect.center.x, rect.top - vPad * 0.35f))
                }
                if (rect.width > rect.height * 4.2f) {
                    add(androidx.compose.ui.geometry.Offset(rect.left + rect.width * 0.22f, rect.top))
                }
                if (index % 2 == 1) {
                    add(androidx.compose.ui.geometry.Offset(rect.left + hPad * 0.3f, rect.bottom))
                }
            }
        }.take(16)
        val bodyPath = cloudClusters.toWordCloudPath(radius)
        // Ayah ornaments are semantic boundaries, not selected text. Remove a
        // small halo from the cloud anywhere it crosses a marker so an adjacent
        // wrapped word can never make the preceding verse number look selected.
        val markerExclusions = androidx.compose.ui.graphics.Path().apply {
            inkGeometries.orEmpty().forEach { marker ->
                val clearanceRadius = maxOf(marker.w, marker.h) * 0.59f
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = androidx.compose.ui.geometry.Offset(
                            marker.centerX,
                            marker.centerY,
                        ),
                        radius = clearanceRadius,
                    ),
                )
            }
        }
        val cloudPath = if (inkGeometries.isNullOrEmpty()) {
            bodyPath
        } else {
            androidx.compose.ui.graphics.Path.combine(
                operation = androidx.compose.ui.graphics.PathOperation.Difference,
                path1 = bodyPath,
                path2 = markerExclusions,
            )
        }
        MushafSelectionCloud(
            path = cloudPath,
            sparkleCenters = sparkles,
        )
    }
    val sparkleTransition = rememberInfiniteTransition(label = "mushafSparkleTransition")
    val sparklePhase by sparkleTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mushafSparklePhase",
    )

    // A colour mask fades away over only the newly inserted translation. This
    // gives the gloss a calm reveal while keeping the AnnotatedString stable for
    // the entire animation; animating its SpanStyle would re-paginate the full
    // surah on every frame and quickly churn native StaticLayout allocations.
    val translationAnnotation = remember(pageText) {
        pageText.getStringAnnotations(MUSHAF_TRANSLATION_TAG, 0, pageText.length).firstOrNull()
    }
    val translationRevealRects = remember(pageLayout.value, translationAnnotation) {
        val laid = pageLayout.value ?: return@remember emptyList()
        val annotation = translationAnnotation ?: return@remember emptyList()
        laid.rangeLineRects(annotation.start, annotation.end)
    }

    // Which ayah sits under a touch point. Reads pageLayout.value at call time,
    // so it stays correct as the layout settles.
    val ayahAt: (androidx.compose.ui.geometry.Offset) -> Int? = { pos ->
        pageLayout.value?.let { laid ->
            val off = laid.getOffsetForPosition(pos)
            ayahRanges.firstOrNull { off in it.second }?.first
        }
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
    // Keep the render bounds identical to the paginator's bounds. The page is
    // edge-to-edge, so the navigation inset is part of maxHeight and must be
    // excluded here as well as during page splitting.
    val bottomPadding = navBarHeight + 8.dp
    val bismillahHeightDp = if (showBismillah) 36.dp else 0.dp

    // The ayah ornaments are painted BEHIND the text at the exact placeholder
    // rects the layout produced, so overhanging swashes render on top of the
    // ornament's flourishes (printed-mushaf behaviour) instead of colliding.

    // Inline marker annotations in layout order — alt text carries the
    // localized digits, and the char range locates each marker in the page
    // text so the drawing pass can find the neighbouring words.
    val arabicTextStyle = getArabicFontStyle(arabicFont, arabicFontSize)

    // The page's own Quran face as a platform Typeface, for the pause marks the marker
    // pass paints straight onto the canvas. Resolved through the same resolver the Text
    // uses, so a font switch in reading settings carries to the marks with it.
    val fontResolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val arabicTypeface: android.graphics.Typeface? = remember(fontResolver, arabicTextStyle.fontFamily) {
        runCatching {
            fontResolver.resolve(arabicTextStyle.fontFamily).value as? android.graphics.Typeface
        }.getOrNull()
    }

    // PageCurl caches its page draw. Starting this value at zero and animating it
    // after the first draw left the initial page permanently marker-less; returning
    // from another page happened to redraw it at the animation's final value.
    // Geometry is prepared before the curl is created now, so paint it immediately.
    val markerAlpha = if (inkGeometries != null) 1f else 0f

    // Deliberately a Box painted with the surface colour rather than a Surface.
    // Surface installs a `pointerInput {}` whose only job is to stop touches
    // reaching whatever is behind it. A Box draws the same background without
    // adding an unnecessary input target on prefetched pages (see [interactive]).
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
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

            selectionCloud?.let { cloud ->
                // Soft cloud body: two restrained blur passes plus a translucent
                // core. Each lobe comes from one shaped Arabic word, so the glow
                // follows the selected text instead of justified line whitespace.
                listOf(10.dp to 0.045f, 5.dp to 0.06f).forEach { (blurRadius, alpha) ->
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(
                                blurRadius,
                                edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded,
                            ),
                    ) {
                        translate(
                            left = horizontalPadding.toPx(),
                            top = (topPadding + bismillahHeightDp).toPx(),
                        ) {
                            drawPath(cloud.path, glowColor.copy(alpha = alpha))
                        }
                    }
                }
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    translate(
                        left = horizontalPadding.toPx(),
                        top = (topPadding + bismillahHeightDp).toPx(),
                    ) {
                        drawPath(cloud.path, glowColor.copy(alpha = 0.095f))
                        drawPath(
                            path = cloud.path,
                            color = glowColor.copy(alpha = 0.13f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 0.75.dp.toPx(),
                            ),
                        )
                        cloud.sparkleCenters.forEachIndexed { index, center ->
                            val alternatingPhase = if (index % 2 == 0) {
                                sparklePhase
                            } else {
                                1.2f - sparklePhase
                            }.coerceIn(0.2f, 1f)
                            val radius = if (index % 3 == 0) 3.2.dp.toPx() else 2.2.dp.toPx()
                            drawMushafSparkle(
                                center = center,
                                radius = radius,
                                color = glowColor.copy(alpha = 0.25f + alternatingPhase * 0.65f),
                            )
                        }
                    }
                }
            }

            // Markers are drawn in two passes around the Text:
            //   1) BEFORE it (this Canvas): the compact end-of-ayah ring.
            //   2) AFTER it (Canvas below the Text): the digits, always crisp. With
            //      ink-accurate placement nothing should reach the digit zone; when
            //      a tail does cross the ring it slides under the digits untouched.
            if (!markersRenderedInline) {
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
                            val radius = minOf(g.w, g.h) * 0.43f
                            drawCircle(
                                color = markerColor.copy(alpha = markerColor.alpha * markerAlpha),
                                radius = radius,
                                center = Offset(g.w / 2f, g.h / 2f),
                                style = Stroke(width = maxOf(1.dp.toPx(), g.h * 0.045f)),
                            )
                        }
                    }
                }
            }



            Text(
                text = pageText,
                onTextLayout = { pageLayout.value = it },
                inlineContent = renderedInlineContent,
                color = onSurfaceColor,
                style = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle).copy(
                    fontSize = arabicFontSize.sp,
                    textAlign = TextAlign.Justify,
                    // High-quality (optimizing) line breaking packs words far more
                    // evenly than the greedy default, so Justify stretches gaps much
                    // less. MUST match the paginator's measureStyle exactly.
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                    textDirection = androidx.compose.ui.text.style.TextDirection.Rtl,
                    letterSpacing = 0.sp,
                    lineHeight = lineHeightSp.sp,
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
                    // Attached only on the live page — an off-page detector is still
                    // a hit-test target, and the topmost one wins, so leaving it on
                    // every page let the curled-away previous page resolve the tap
                    // against ITS text (double-tapping 18:28 revealed 18:24).
                    .then(
                        if (interactive) {
                            Modifier
                                // Observe at the Initial pass because the pager also watches
                                // horizontal movement. A rub is deliberately an out-and-back
                                // motion, so its signed distance cancels and cannot turn a page.
                                .pointerInput(ayahRanges, pageText, onAyahRub) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                        val touchedAyah = ayahAt(down.position)
                                            ?: return@awaitEachGesture
                                        val minimumLeg = 24.dp.toPx()
                                        val minimumTravel = 56.dp.toPx()
                                        var legDirection = 0
                                        var legDistance = 0f
                                        var reversed = false
                                        var horizontalTravel = 0f
                                        var verticalTravel = 0f
                                        var fired = false

                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: break
                                            val delta = change.position - change.previousPosition
                                            horizontalTravel += kotlin.math.abs(delta.x)
                                            verticalTravel += kotlin.math.abs(delta.y)

                                            if (kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y) * 1.15f) {
                                                val direction = if (delta.x >= 0f) 1 else -1
                                                when {
                                                    legDirection == 0 -> {
                                                        legDirection = direction
                                                        legDistance = kotlin.math.abs(delta.x)
                                                    }
                                                    direction == legDirection -> {
                                                        legDistance += kotlin.math.abs(delta.x)
                                                    }
                                                    legDistance >= minimumLeg -> {
                                                        // A real reversal after a deliberate first
                                                        // stroke; tiny direction jitter is ignored.
                                                        reversed = true
                                                        legDirection = direction
                                                        legDistance = kotlin.math.abs(delta.x)
                                                    }
                                                }
                                            }

                                            val stillOnAyah = ayahAt(change.position) == touchedAyah
                                            if (!fired && reversed && legDistance >= minimumLeg &&
                                                horizontalTravel >= minimumTravel &&
                                                verticalTravel < horizontalTravel * 0.65f && stillOnAyah
                                            ) {
                                                fired = true
                                                onAyahRub(touchedAyah)
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                                .pointerInput(ayahRanges, pageText, onAyahDoubleTap, onAyahLongPress) {
                                    detectTapGestures(
                                        onDoubleTap = { pos -> ayahAt(pos)?.let(onAyahDoubleTap) },
                                        // Fall back to the page's first ayah only when the point
                                        // maps nowhere. This USED to be unconditional — the old
                                        // handler ignored the offset entirely, so a long-press
                                        // anywhere on the page selected the first ayah on it.
                                        onLongPress = { pos ->
                                            (ayahAt(pos) ?: ayahRanges.firstOrNull()?.first)
                                                ?.let(onAyahLongPress)
                                        },
                                    )
                                }
                        } else {
                            Modifier
                        }
                    )
            )

            // Tajweed, painted over the finished layout.
            //
            // The letters are drawn once, uncoloured, by the Text below; each rule then
            // redraws that same layout in its own colour, clipped to the characters it
            // covers. Because both passes share one TextLayoutResult, the shaping is
            // identical — a rule can colour a nūn alone without the mīm before it losing
            // its join, which is exactly what colour spans could not do.
            pageLayout.value?.let { layout ->
                if (tajweed.isNotEmpty()) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                top = topPadding + bismillahHeightDp,
                                bottom = bottomPadding,
                            ),
                    ) {
                        tajweed.forEach { span ->
                            val start = span.start.coerceIn(0, layout.layoutInput.text.length)
                            val end = span.end.coerceIn(start, layout.layoutInput.text.length)
                            if (end <= start) return@forEach
                            clipPath(layout.getPathForRange(start, end)) {
                                drawText(textLayoutResult = layout, color = span.color)
                            }
                        }
                    }
                }
            }


            // Reveal the inserted gloss without changing the text being measured.
            // The mask shares the Text's exact content bounds, and its annotated
            // range survives AnnotatedString.subSequence when an ayah crosses pages.
            if (translationAnnotation != null && translationVisibility < 0.999f) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = topPadding + bismillahHeightDp,
                            bottom = bottomPadding,
                        ),
                ) {
                    translationRevealRects.forEach { rect ->
                        drawRect(
                            color = surfaceColor.copy(alpha = 1f - translationVisibility),
                            topLeft = rect.topLeft,
                            size = rect.size,
                        )
                    }
                }
            }

            // Pass 2: digits + shield above the text (see comment on pass 1).
            if (!markersRenderedInline) {
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
                    android.util.Log.d(
                        "WaqfDebug",
                        "geoms=${geoms.size} withMark=${geoms.count { it.pauseMark.isNotEmpty() }} " +
                            "typeface=${arabicTypeface != null} " +
                            "marks=${geoms.joinToString { "${it.digits}:${it.pauseMark.map { c -> "U+%04X".format(c.code) }}" }}",
                    )
                    for (g in geoms) {
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = markerColor.toArgb()
                                alpha = (255 * markerAlpha).toInt()
                                textAlign = android.graphics.Paint.Align.CENTER
                                // Derive the digit size from its measured ornament.
                                // Translation ornaments use a smaller em than the
                                // Arabic markers, so the global Arabic size clipped
                                // their otherwise correctly scaled number.
                                textSize = g.h * if (g.digits.length >= 3) 0.40f else 0.58f
                                isFakeBoldText = false
                            }
                            val baselineY = g.centerY - (paint.descent() + paint.ascent()) / 2f
                            canvas.nativeCanvas.drawText(g.digits, g.centerX, baselineY, paint)

                            // The pause sign, set over the medallion as print sets it.
                            // Drawn as the letter it abbreviates: the source encodes these
                            // as combining marks, which shape to nothing on their own —
                            // neither this canvas nor a Compose Text would draw them,
                            // verified on device. The letter is ordinary text and renders.
                            val pauseGlyph = pauseMarkGlyph(g.pauseMark)
                            if (pauseGlyph.isNotEmpty() && !g.isTranslation) {
                                val markPaint = android.graphics.Paint(
                                    android.graphics.Paint.ANTI_ALIAS_FLAG,
                                ).apply {
                                    color = markerColor.toArgb()
                                    alpha = (255 * markerAlpha).toInt()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    textSize = g.h * PAUSE_MARK_SIZE_FRACTION
                                    typeface = arabicTypeface
                                }
                                // Positioned by the glyph's own ink, not by a shared
                                // baseline: ج and م carry tails below the baseline while
                                // لا and ط do not, so one baseline for all of them left
                                // the tailed signs resting on the ornament. Measuring puts
                                // the lowest ink of every sign the same distance clear.
                                val inkBounds = android.graphics.Rect()
                                markPaint.getTextBounds(pauseGlyph, 0, pauseGlyph.length, inkBounds)
                                canvas.nativeCanvas.drawText(
                                    pauseGlyph,
                                    g.centerX,
                                    g.top - g.h * PAUSE_MARK_GAP - inkBounds.bottom,
                                    markPaint,
                                )
                            }
                        }
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
@OptIn(ExperimentalPageCurlApi::class)
@Composable
private fun MushafPagerView(
    ayahs: List<com.starception.submission.core.qurandatabase.Ayah>,
    arabicFont: String,
    arabicFontSize: Float,
    showTajweed: Boolean,
    tajweedAnnotations: Map<Int, List<com.starception.submission.feature.surah.tajweed.TajweedAnnotation>>,
    showBismillah: Boolean = false,
    textAlignment: String = "start",
    /** Selected translation controls the numeral system used by ayah markers. */
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
    // PageCurl 1.5.1 composes only current-1/current/current+1. Keep its state as
    // the single source of truth so the full 131-page Al-Baqarah list is never
    // represented by 131 graphics layers.
    var pagerPageCount by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    val state = rememberPageCurlState(
        initialCurrent = initialPage.coerceAtLeast(0),
    )
    val pageCurlConfig = rememberPageCurlConfig(
        backPageColor = surfaceColor,
        backPageContentAlpha = 0.10f,
        shadowColor = Color.Black,
        shadowAlpha = 0.18f,
        shadowRadius = 12.dp,
        dragForwardEnabled = true,
        dragBackwardEnabled = true,
        // A single tap must stay available to the reader, and a double tap must
        // reveal translation rather than advancing a page.
        tapForwardEnabled = false,
        tapBackwardEnabled = false,
        tapCustomEnabled = false,
        dragInteraction = PageCurlConfig.StartEndDragInteraction(
            pointerBehavior = PageCurlConfig.DragInteraction.PointerBehavior.PageEdge,
        ),
    )

    // Pinch streams a new font size every frame; re-measuring the whole surah at
    // that rate janks the gesture and dumps the reader onto a different page.
    // Pagination therefore follows a DEBOUNCED copy of the size (committed once
    // the value stops changing), while the live pager scales visually as a
    // preview. After repagination the pager re-anchors to the ayah that was at
    // the top of the page being read.
    var committedFontSize by remember { mutableStateOf(arabicFontSize) }
    val typesetFontSize = committedFontSize * MUSHAF_TYPESETTING_SCALE
    var pendingAnchorAyah by remember { mutableStateOf(0) }
    // Per-page measures (marker ink geometry) — small layouts, worth caching.
    val textMeasurer = rememberTextMeasurer(cacheSize = 3)
    // The paginator measures the WHOLE surah in one layout: ~300 lines of Arabic,
    // each backed by a native StaticLayout. Every revealed translation produces a
    // new master string and therefore a new one of these, and the default 8-entry
    // cache kept all of them alive — native heap ran to ~180MB and the process was
    // killed after enough double-taps. Two entries is exactly what the toggle needs
    // (gloss on / gloss off) and bounds what we hold.
    val pageMeasurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current

    // Wire physical volume keys to Mushaf page navigation while this composable
    // is on screen. Activity.onKeyDown checks MushafKeyBus.handle{Next,Prev}().
    val mushafScope = rememberCoroutineScope()
    val miniBarOwner = remember { Any() }
    DisposableEffect(state, pagerPageCount) {
        MushafKeyBus.bind(
            next = {
                mushafScope.launch {
                    if (state.current < pagerPageCount - 1) state.next()
                }
            },
            prev = {
                mushafScope.launch {
                    if (state.current > 0) state.prev()
                }
            },
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
    val lineSpacingMultiplier = 1.55f
    val horizontalPadding = 12.dp
    // 16dp strip at the top hosts the morphing pull-down hint drawn by the
    // screen overlay (pill ↔ grabber); MUST match MushafPageWithFrame's
    // topPadding so rendered pages fit exactly.
    val topPadding = 16.dp
    val bottomPadding = navBarHeight + 8.dp
    val bismillahHeightDp = 36.dp

    // Keep the marker itself Mushaf-like while displaying its number in the
    // numeral system readers expect from the selected translation.
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
    val translationOrnamentPlaceholder = androidx.compose.ui.text.Placeholder(
        (MARKER_SLOT_WIDTH_EM * TRANSLATION_MARKER_EM_SCALE).em,
        (MARKER_HEIGHT_EM * TRANSLATION_MARKER_EM_SCALE).em,
        androidx.compose.ui.text.PlaceholderVerticalAlign.Center,
    )
    val markerInlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> =
        remember {
            mapOf(
                "ayahOrnament" to androidx.compose.foundation.text.InlineTextContent(ornamentPlaceholder) { _ -> },
                MUSHAF_TRANSLATION_ORNAMENT_TAG to androidx.compose.foundation.text.InlineTextContent(
                    translationOrnamentPlaceholder,
                ) { _ -> },
            )
        }

    // Double-tap reveal. `ayah.text` arrives as arabic + "\n\n" + translation from the
    // translation-aware loader, so part 1 is the translation; ayahs whose translation is
    // missing have nothing to reveal. Declared before the master string because the
    // translation is now part of that string rather than drawn over it.
    var revealedAyah by remember(ayahs) { mutableStateOf<Int?>(null) }
    val translationVisibility = remember(ayahs) {
        androidx.compose.animation.core.Animatable(1f)
    }
    var translationTransitionJob by remember(ayahs) {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
    val translationByAyah = remember(ayahs) {
        ayahs.associate { a ->
            a.numberInSurah to a.text.split("\n\n").getOrNull(1)?.trim().orEmpty()
        }
    }
    /** Translation actually inlined this pass — blank ones must not alter the layout. */
    val inlinedAyah: Int? = revealedAyah?.takeIf { !translationByAyah[it].isNullOrBlank() }
    val inlinedText: String = inlinedAyah?.let { translationByAyah[it] }.orEmpty()

    val translationSpanStyle = SpanStyle(
        // Book-like typography distinguishes the translation gently without
        // making it feel like secondary UI chrome. The marker shares this span,
        // so both retain identical row metrics.
        fontSize = (typesetFontSize * MUSHAF_TRANSLATION_FONT_SCALE).sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val translationParagraphStyle = ParagraphStyle(
        textDirection = androidx.compose.ui.text.style.TextDirection.Content,
        textAlign = TextAlign.Start,
        lineHeight = (typesetFontSize * MUSHAF_TRANSLATION_FONT_SCALE * 1.28f).sp,
    )
    val translationSeparatorSpanStyle = SpanStyle(
        fontSize = (typesetFontSize * 0.12f).sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    )
    val translationSeparatorParagraphStyle = ParagraphStyle(
        textDirection = androidx.compose.ui.text.style.TextDirection.Content,
        textAlign = TextAlign.Start,
        // A line separator is still a visual line in StaticLayout. Give that
        // line deliberately compact metrics instead of inheriting the Quran
        // paragraph's 1.45x leading, which left a conspicuous blank Arabic line
        // between the Arabic and its much smaller translation.
        lineHeight = (typesetFontSize * 0.12f).sp,
    )

    val markerData = remember(
        ayahs, showTajweed, tajweedAnnotations, typesetFontSize, translationCode,
        inlinedAyah, inlinedText,
    ) {
        val placeholderRanges = mutableListOf<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.Placeholder>>()
        val tajweedSpans = mutableListOf<TajweedSpan>()
        val builtRanges = mutableListOf<Pair<Int, IntRange>>()
        val built = buildAnnotatedString {
            ayahs.forEach { ayah ->
                // Real offset in the string being built, recorded instead of recomputed.
                // The old ranges re-derived positions arithmetically from arabicText, which
                // disagreed with the text whenever an append changed its length (Tajweed's
                // applyWithOverlap does), so every later ayah drifted and a tap resolved early.
                val ayahStart = length
                // The pause mark is lifted out of the text and set above the medallion
                // instead — see splitTrailingPauseMark. Only trailing characters are
                // removed, so the Tajweed annotation offsets below still line up.
                val (arabicText, pauseMark) = splitTrailingPauseMark(
                    ayah.text.split("\n\n").getOrNull(0) ?: ayah.text,
                )
                pushStringAnnotation(
                    tag = MUSHAF_ARABIC_AYAH_TAG,
                    annotation = ayah.numberInSurah.toString(),
                )
                // Tajweed colour is deliberately NOT a span on this string. A colour
                // change inside a word splits the shaping run, and cursive Arabic then
                // renders the two halves in isolated forms — the mīm of "مِنْ" stopped
                // joining its nūn wherever a rule coloured only the nūn. The rules are
                // carried alongside instead and painted over the finished layout, which
                // cannot disturb shaping because the shaping has already happened.
                val arabicStart = length
                append(arabicText)
                if (showTajweed) {
                    tajweedAnnotations[ayah.numberInSurah]?.forEach { rule ->
                        val start = (arabicStart + rule.startIndex).coerceIn(arabicStart, length)
                        val end = (arabicStart + rule.endIndex).coerceIn(start, length)
                        if (end > start) tajweedSpans.add(TajweedSpan(start, end, rule.rule.color))
                    }
                }
                pop()

                // For the selected ayah the gloss is part of the reading flow
                // BEFORE its end marker: Arabic → translation → ornament. The
                // Arabic annotation was already closed above, so the cloud can
                // never include the translation. Unicode isolate controls keep
                // Bengali/English order stable inside the surrounding RTL text.
                if (ayah.numberInSurah == inlinedAyah && inlinedText.isNotEmpty()) {
                    val digits = markerDigitsFor(ayah.numberInSurah)
                    pushStringAnnotation(
                        tag = MUSHAF_TRANSLATION_TAG,
                        annotation = ayah.numberInSurah.toString(),
                    )
                    // A compact leading break makes the translation readable
                    // without inserting a full Arabic line of empty space.
                    withStyle(translationSeparatorParagraphStyle) {
                        withStyle(translationSeparatorSpanStyle) { append('\u2028') }
                    }
                    withStyle(translationParagraphStyle) {
                        withStyle(translationSpanStyle) {
                            append('\u2066') // LEFT-TO-RIGHT ISOLATE
                            append(inlinedText)
                            // This dedicated Center-aligned slot uses the
                            // translation row metrics. Its separate painter
                            // avoids reusing the Arabic marker's cached size.
                            // WORD JOINER attaches it to the final translated
                            // word; the placeholder already reserves its own
                            // horizontal breathing room.
                            append('\u2060')
                            val markerStart = length
                            appendInlineContent(MUSHAF_TRANSLATION_ORNAMENT_TAG, digits)
                            placeholderRanges.add(
                                androidx.compose.ui.text.AnnotatedString.Range(
                                    translationOrnamentPlaceholder,
                                    markerStart,
                                    markerStart + digits.length,
                                ),
                            )
                            // Close the LTR isolate after the ornament so the
                            // final translated word and marker wrap as one unit.
                            append('\u2069') // POP DIRECTIONAL ISOLATE
                        }
                    }
                    // End the selected ayah cleanly after its marker; the next
                    // Arabic ayah resumes on a fresh RTL line.
                    withStyle(translationSeparatorParagraphStyle) {
                        withStyle(translationSeparatorSpanStyle) { append('\u2028') }
                    }
                    pop()
                    append(' ')
                } else {
                    val digits = markerDigitsFor(ayah.numberInSurah)
                    // The slot's own characters are never drawn — the medallion covers
                    // them — so the pause mark rides along inside it. That keeps the mark
                    // tied to its ayah through pagination, where a page slice would lose
                    // any side table keyed by position.
                    val slotText = if (pauseMark.isEmpty()) {
                        digits
                    } else {
                        digits + PAUSE_MARK_SEPARATOR + pauseMark
                    }
                    // Normally the marker stays attached to the Arabic ending.
                    append('⁠')
                    val markerStart = length
                    appendInlineContent("ayahOrnament", slotText)
                    placeholderRanges.add(
                        androidx.compose.ui.text.AnnotatedString.Range(
                            ornamentPlaceholder,
                            markerStart,
                            markerStart + slotText.length,
                        ),
                    )
                    // A regular space becomes a justification expansion point.
                    // That is especially visible in short Surahs, whose sparse
                    // lines can pour most of their unused width immediately after
                    // an ayah marker and destroy its symmetric breathing room.
                    // Keep the visual separator fixed-width, then provide a
                    // zero-width wrapping opportunity between ayahs.
                    append('\u202F') // NARROW NO-BREAK SPACE: fixed visual gap
                    append('\u200B') // ZERO WIDTH SPACE: line may still wrap here
                }

                // Closed after the gloss and marker so tapping either still
                // resolves to the ayah that opened the translation.
                builtRanges.add(ayah.numberInSurah to (ayahStart until length))
            }
        }
        MushafMaster(built, placeholderRanges, builtRanges.toList(), tajweedSpans.toList())
    }
    val masterString = markerData.text
    val markerPlaceholders = markerData.placeholders

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

    // Taken straight from the master string's construction, so the ranges that drive page
    // slicing, tap hit-testing and highlighting cannot disagree with the text they index.
    val ayahCharRanges = markerData.ayahRanges


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
                        val startedAtMushaf =
                            (parentScrollState?.firstVisibleItemIndex ?: 1) >= 1
                        var totalDx = 0f
                        var totalDy = 0f
                        var directionDecided = false
                        var isVerticalScroll = false
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
                                    // PageCurl owns horizontal movement. Do not
                                    // consume here or its child detector cannot
                                    // anchor the paper edge to the reader's finger.
                                } else if (directionDecided && isVerticalScroll) {
                                    // PageCurl treats an unrestricted Y drag as a left/right
                                    // page turn, so claim the vertical stream before it reaches
                                    // the child and route it into the parent Surah list instead.
                                    change.consume()
                                    verticalDragTotal += delta.y
                                    parentScrollState?.dispatchRawDelta(-delta.y)
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (directionDecided && isVerticalScroll) {
                            val verticalThreshold = size.height * 0.12f
                            scope.launch {
                                when {
                                    // Pulling down from Mushaf reveals the album header and
                                    // Surah information, then settles cleanly at its top.
                                    verticalDragTotal > verticalThreshold -> {
                                        parentScrollState?.animateScrollToItem(
                                            index = 0,
                                            scrollOffset = 0,
                                        )
                                    }

                                    // A swipe up only advances the Quran page when the gesture
                                    // began in the full Mushaf position. Swiping up from Surah
                                    // info returns to Mushaf without skipping a page.
                                    verticalDragTotal < -verticalThreshold -> {
                                        if (startedAtMushaf) {
                                            if (state.current < pagerPageCount - 1) state.next()
                                        } else {
                                            parentScrollState?.animateScrollToItem(
                                                index = 1,
                                                scrollOffset = 0,
                                            )
                                        }
                                    }

                                    // Cancelled/short pulls return to the section where the
                                    // gesture started instead of leaving a partial header.
                                    else -> {
                                        parentScrollState?.animateScrollToItem(
                                            index = if (startedAtMushaf) 1 else 0,
                                            scrollOffset = 0,
                                        )
                                    }
                                }
                            }
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

            val arabicTextStyle = getArabicFontStyle(arabicFont, typesetFontSize)
            val measureStyle = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle).copy(
                fontSize = typesetFontSize.sp,
                textAlign = TextAlign.Justify,
                // Keep in lockstep with MushafPageWithFrame's render style — the
                // paginator slices pages at these line breaks AND the ink-geometry
                // pass positions medallions from this layout. textDirection is part
                // of that contract: without it the measured paragraph can resolve
                // LTR (left-aligned last lines), shifting every X on those lines
                // ~half a page from what is displayed.
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                textDirection = androidx.compose.ui.text.style.TextDirection.Rtl,
                letterSpacing = 0.sp,
                lineHeight = (typesetFontSize * lineSpacingMultiplier).sp,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            )

            data class PaginatedPage(
                val text: AnnotatedString,
                val pageNumber: Int,
                val showBismillah: Boolean,
                val ayahRanges: List<Pair<Int, IntRange>>,
                /** Rules for this page's characters, painted over the finished layout. */
                val tajweed: List<TajweedSpan> = emptyList(),
                /**
                 * Full Mushaf pages distribute the otherwise-unused fraction of
                 * a final line between their existing lines. TextAlign.Justify
                 * only fills horizontally; without this, every page can retain
                 * almost one complete line of dead space above the navigation
                 * inset.
                 */
                val lineHeightSp: Float = typesetFontSize * lineSpacingMultiplier,
            )

            val paginatedPages = remember(
                masterString, typesetFontSize, arabicFont,
                availableWidthPx, fullPageHeightPx, firstPageHeightPx
            ) {
                if (masterString.text.isEmpty() || availableWidthPx <= 0f || fullPageHeightPx <= 0f) {
                    return@remember listOf(
                        PaginatedPage(masterString, 1, showBismillah, ayahCharRanges, markerData.tajweed)
                    )
                }

                val fullLayout = pageMeasurer.measure(
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
                        PaginatedPage(masterString, 1, showBismillah, ayahCharRanges, markerData.tajweed)
                    )
                }

                // Inline translations deliberately flow across page boundaries.
                // Treating the entire gloss as an atomic block moved a long
                // translation to the following page and left most of the tapped
                // page empty, even though many measured lines still fitted.
                val arabicLineBlocks = masterString
                    .getStringAnnotations(MUSHAF_ARABIC_AYAH_TAG, 0, masterString.length)
                    .filter { it.item == inlinedAyah?.toString() }
                    .mapNotNull { annotation ->
                        if (annotation.end <= annotation.start) return@mapNotNull null
                        fullLayout.getLineForOffset(annotation.start) to
                            fullLayout.getLineForOffset(annotation.end - 1)
                    }

                val pages = mutableListOf<PaginatedPage>()
                var currentLine = 0
                var pageNum = 1

                while (currentLine < fullLayout.lineCount) {
                    val pageHeightPx = if (pageNum == 1) firstPageHeightPx else fullPageHeightPx
                    var linesOnPage = 0

                    for (line in currentLine until fullLayout.lineCount) {
                        val lineTop = fullLayout.getLineTop(line) - fullLayout.getLineTop(currentLine)
                        val lineBottom = fullLayout.getLineBottom(line) - fullLayout.getLineTop(currentLine)
                        if (lineBottom > pageHeightPx && linesOnPage > 0) break
                        linesOnPage++
                    }

                    if (linesOnPage == 0) linesOnPage = 1

                    val startCharIndex = fullLayout.getLineStart(currentLine)
                    var endLine = currentLine + linesOnPage - 1

                    // Do not leave a single final Arabic line (often only one
                    // word plus its medallion) alone at the top of the next page.
                    // Pull one more line forward with it. Besides reading more
                    // naturally, this keeps a selected ayah's terminal highlight
                    // from looking like a detached or missed fragment.
                    val oneLineWidow = arabicLineBlocks.firstOrNull { (blockStart, blockEnd) ->
                        blockStart <= endLine && blockEnd == endLine + 1 && linesOnPage > 1
                    }
                    if (oneLineWidow != null) {
                        linesOnPage--
                        endLine--
                    }
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

                    val usedHeightPx =
                        fullLayout.getLineBottom(endLine) - fullLayout.getLineTop(currentLine)
                    // Same slice the text took: a rule that straddles the page break is
                    // clipped to the part that landed here, and one that fell entirely on
                    // another page is dropped.
                    val pageTajweed = markerData.tajweed.mapNotNull { span ->
                        val start = maxOf(span.start, startCharIndex)
                        val end = minOf(span.end, endCharIndex)
                        if (end <= start) null
                        else TajweedSpan(start - startCharIndex, end - startCharIndex, span.color)
                    }

                    val isFinalPage = endCharIndex >= masterString.length
                    val lineGapCount = (linesOnPage - 1).coerceAtLeast(0)
                    // Leave one physical dp for float/rounding differences between
                    // the master layout and the independently rendered page slice.
                    // Cap the expansion so a widow-protection decision can never
                    // produce visibly loose lines.
                    val stretchPerGapPx = if (!isFinalPage && lineGapCount > 0) {
                        val roundingSafetyPx = with(density) { 1.dp.toPx() }
                        val maxStretchPx = with(density) { 3.dp.toPx() }
                        ((pageHeightPx - usedHeightPx - roundingSafetyPx) / lineGapCount)
                            .coerceIn(0f, maxStretchPx)
                    } else {
                        0f
                    }
                    val stretchedLineHeightSp =
                        typesetFontSize * lineSpacingMultiplier +
                            stretchPerGapPx / (density.density * density.fontScale)

                    pages.add(PaginatedPage(
                        text = pageString,
                        pageNumber = pageNum,
                        showBismillah = showBismillah && pageNum == 1,
                        ayahRanges = pageAyahRanges,
                        tajweed = pageTajweed,
                        lineHeightSp = stretchedLineHeightSp,
                    ))

                    currentLine += linesOnPage
                    pageNum++
                }

                pages
            }

            LaunchedEffect(paginatedPages.size) {
                pagerPageCount = paginatedPages.size.coerceAtLeast(1)
                if (paginatedPages.isNotEmpty() && state.current > paginatedPages.lastIndex) {
                    state.snapTo(paginatedPages.lastIndex)
                }
            }

            LaunchedEffect(state.current, paginatedPages.size) {
                onPageChange(state.current, paginatedPages.size)
            }

            // Publish current Mushaf page to PullToSyncContainer's mini-bar.
            // Cleared on dispose so leaving Mushaf mode hides the strip.
            DisposableEffect(surahNameArabic, surahNameEnglish, state.current, paginatedPages.size) {
                if (paginatedPages.isNotEmpty()) {
                    MushafMiniBarBus.bind(
                        owner = miniBarOwner,
                        next = {
                            mushafScope.launch {
                                if (state.current < pagerPageCount - 1) state.next()
                            }
                        },
                        previous = {
                            mushafScope.launch {
                                if (state.current > 0) state.prev()
                            }
                        },
                        openInfo = {
                            mushafScope.launch {
                                parentScrollState?.animateScrollToItem(
                                    index = 0,
                                    scrollOffset = 0,
                                )
                            }
                        },
                    )
                    MushafMiniBarBus.publish(
                        owner = miniBarOwner,
                        newState = MushafMiniBarState(
                            surahNumber = ayahs.first().surahNumber,
                            surahNameArabic = surahNameArabic,
                            surahNameEnglish = surahNameEnglish,
                            currentPage = state.current + 1,
                            totalPages = paginatedPages.size,
                        ),
                    )
                }
                onDispose { MushafMiniBarBus.unbind(miniBarOwner) }
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
            val pagerEmPxForFiller = with(density) { typesetFontSize.sp.toPx() }
            val pageInlineContent = remember(markerInlineContent, availableWidthPx, typesetFontSize) {
                markerInlineContent + (
                    MUSHAF_LINE_FILLER_TAG to androidx.compose.foundation.text.InlineTextContent(
                        mushafLineFillerPlaceholder(availableWidthPx.toInt(), pagerEmPxForFiller),
                    ) { _ -> }
                    )
            }

            // Ink-accurate marker geometry per page, prefetched for the pages the
            // reader can reach next so page turns never show markers moving.
            //
            // Keyed by the page's TEXT, not its index. Revealing a translation
            // re-paginates the whole surah, and an index-keyed cache was discarded
            // wholesale each time even though every page before the gloss came back
            // byte-identical — so each double-tap re-measured pages that had not
            // changed and made every marker fade out and back in. Font, face,
            // column width, and page height can invalidate the geometry; page
            // height also controls slicing and stretched leading.
            val inkGeomCache = remember(
                typesetFontSize,
                arabicFont,
                availableWidthPx,
                fullPageHeightPx,
                firstPageHeightPx,
            ) {
                androidx.compose.runtime.mutableStateMapOf<AnnotatedString, List<MarkerGeometry>>()
            }
            val pagerEmPx = with(density) { typesetFontSize.sp.toPx() }
            LaunchedEffect(state.current, paginatedPages, inkGeomCache) {
                if (paginatedPages.isEmpty()) return@LaunchedEffect
                // A page turn can arrive much faster than a bitmap ink scan.
                // Debounce just long enough for rapid swipes to settle; a newer
                // state cancels this effect before it allocates another surface.
                kotlinx.coroutines.delay(120)
                for (idx in listOf(state.current, state.current + 1, state.current - 1)) {
                    val page = paginatedPages.getOrNull(idx) ?: continue
                    val pageText = page.text
                    if (pageText !in inkGeomCache) {
                        val geoms = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            val scanJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                            runCatching {
                                computeInkMarkerGeometries(
                                    textMeasurer = textMeasurer,
                                    pageText = pageText,
                                    style = measureStyle.copy(lineHeight = page.lineHeightSp.sp),
                                    maxWidthPx = availableWidthPx.toInt(),
                                    density = density,
                                    emPx = pagerEmPx,
                                    isActive = { scanJob?.isActive == true },
                                )
                            }.onFailure { error ->
                                if (error !is kotlinx.coroutines.CancellationException) {
                                    android.util.Log.e(
                                        "MushafPager",
                                        "Unable to prepare ayah-marker geometry; using inline marker fallback",
                                        error,
                                    )
                                }
                            }.getOrNull()
                        }
                        if (geoms != null) {
                            inkGeomCache[pageText] = geoms
                        }
                    }
                }
                // Geometry values are tiny, but AnnotatedString keys retain all
                // page styling. Keep only the active curl neighbourhood so long
                // reading sessions have a hard upper memory bound.
                val retainedTexts = (state.current - 1..state.current + 1)
                    .mapNotNull { paginatedPages.getOrNull(it)?.text }
                    .toSet()
                inkGeomCache.keys.toList().forEach { key ->
                    if (key !in retainedTexts) inkGeomCache.remove(key)
                }
            }

            // Consumed once per search jump. The effect below is keyed on
            // paginatedPages because the target page only exists after the first
            // measure — but the surah also re-paginates whenever a double-tapped
            // translation is inlined or dismissed, and without this latch every
            // reveal re-ran the jump and hauled the reader back to the searched
            // ayah's page (open 18:1 from search, read to page 7, double-tap an
            // ayah → thrown back to page 1).
            var consumedScrollToAyah by remember(ayahs) { mutableStateOf(0) }
            LaunchedEffect(paginatedPages, scrollToAyah) {
                if (scrollToAyah > 0 && scrollToAyah != consumedScrollToAyah &&
                    paginatedPages.isNotEmpty()
                ) {
                    val targetIndex = paginatedPages.indexOfFirst { page ->
                        page.ayahRanges.any { it.first == scrollToAyah }
                    }
                    if (targetIndex >= 0) {
                        consumedScrollToAyah = scrollToAyah
                        if (targetIndex != state.current) state.snapTo(targetIndex)
                    }
                }
            }

            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val toggleInlineTranslation: (Int) -> Unit = { ayahNumber ->
                // Arabic-only or incomplete databases have no gloss to reveal.
                // In that case the gesture should be a no-op rather than leaving
                // a misleading highlight with no text beneath it.
                if (!translationByAyah[ayahNumber].isNullOrBlank()) {
                    val currentAyah = revealedAyah
                    val isHidingCurrent = currentAyah == ayahNumber
                    translationTransitionJob?.cancel()
                    translationTransitionJob = mushafScope.launch {
                        if (currentAyah != null) {
                            // Keep the translation in the measured page while
                            // the surface-colour mask softly fades it away.
                            translationVisibility.animateTo(
                                targetValue = 0f,
                                animationSpec = NiaMotion.exitTween(
                                    NiaMotion.Duration.MEDIUM_2,
                                ),
                            )
                        } else {
                            translationVisibility.snapTo(0f)
                        }

                        if (isHidingCurrent) {
                            revealedAyah = null
                            translationVisibility.snapTo(1f)
                        } else {
                            revealedAyah = ayahNumber
                            // Let the newly paginated text install its opaque
                            // mask before beginning the reveal, avoiding a flash.
                            androidx.compose.runtime.withFrameNanos { }
                            translationVisibility.animateTo(
                                targetValue = 1f,
                                animationSpec = NiaMotion.enterTween(
                                    NiaMotion.Duration.MEDIUM_4,
                                ),
                            )
                        }
                    }
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                    )
                }
            }
            // PageCurl composes its page before the asynchronous ink scan finishes,
            // and that initial captured draw does not reliably refresh when only the
            // geometry map changes. Keep the geometry in the curl's identity so the
            // prepared markers are installed when ready, but render the Quran text
            // immediately. Geometry is decorative; blocking the entire reader on it
            // left a permanently blank page whenever an ink scan failed. It also meant
            // PageCurl still had a zero page count while search/offline restoration
            // effects attempted to snap, which could crash inside PageCurlState.
            val currentPageText = paginatedPages.getOrNull(state.current)?.text
            val currentPageGeometry = currentPageText?.let(inkGeomCache::get)
            // Make the prepared geometry part of the curl's identity. This is
            // important because PageCurl owns a subcomposition and otherwise can
            // retain the page it created while the async geometry was absent.
            androidx.compose.runtime.key(currentPageGeometry) {
                PageCurl(
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
                    val page = paginatedPages.getOrNull(pageIndex) ?: return@PageCurl
                    MushafPageWithFrame(
                        pageText = page.text,
                        tajweed = page.tajweed,
                        inlineContent = pageInlineContent,
                        inkGeometries = if (pageIndex == state.current) {
                            currentPageGeometry
                        } else {
                            inkGeomCache[page.text]
                        },
                        translationVisibility = translationVisibility.value,
                        arabicFont = arabicFont,
                        arabicFontSize = typesetFontSize,
                        lineHeightSp = page.lineHeightSp,
                        showBismillah = page.showBismillah,
                        onAyahLongPress = onAyahLongPress,
                        ayahRanges = page.ayahRanges,
                        // A revealed ayah is also the selected one, so the verse the
                        // gloss belongs to lights up under the words — otherwise, on a
                        // dense page, nothing tells the reader which of the ayahs above
                        // the translation it is explaining. Outranks the search
                        // highlight, which is stale once the reader starts tapping.
                        highlightedAyahNumber = revealedAyah ?: highlightedAyahNumber,
                        interactive = pageIndex == state.current,
                        onAyahDoubleTap = { ayahNumber ->
                            // No re-anchoring here on purpose. The gloss is inserted AFTER
                            // the tapped ayah, which sits on this page, so every line before
                            // this page's first character is untouched and repagination
                            // reproduces the same page boundaries up to it — state.current
                            // still points at the page being read. The anchor that used to
                            // be set here is what dragged the reader backwards, because it
                            // re-snapped to the first page that merely *contains* the ayah.
                            // Double-tap the revealed ayah again to dismiss.
                            toggleInlineTranslation(ayahNumber)
                        },
                        onAyahRub = toggleInlineTranslation,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun getArabicFontFamily(arabicFont: String): androidx.compose.ui.text.font.FontFamily {
    return getArabicFontFamilyForSelection(arabicFont)
}

/** Ayah number rendered with the numeral system of the selected translation. */
private fun Int.toAyahDigits(translationCode: String): String {
    val zero = when (translationCode) {
        "ar" -> '\u0660' // ٠ Arabic-Indic
        "ur" -> '\u06F0' // ۰ Extended Arabic-Indic
        "bn" -> '\u09E6' // ০ Bengali
        else -> return toString() // Western digits for the remaining translations
    }
    return toString().map { character ->
        if (character in '0'..'9') zero + (character - '0') else character
    }.joinToString("")
}

private fun Int.toArabicIndic(): String = this.toString().map { c ->
    when (c) {
        '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
        '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
        else -> c
    }
}.joinToString("")

/**
 * Draws a compact end-of-ayah ring inside the inline placeholder reserved by
 * the text layout. Keeping the ring and digits in that slot prevents a later Canvas
 * pass from drifting onto Arabic glyphs on justified RTL lines.
 */
@Composable
private fun MushafInlineAyahOrnament(
    digits: String,
    arabicFontSize: Float,
) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(MARKER_ORNAMENT_FILL)
                .aspectRatio(MARKER_ASPECT)
                .border(0.8.dp, tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = digits,
                color = tint,
                fontFamily = ubuntuInspiredFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = (
                    arabicFontSize * if (digits.length >= 3) 0.32f else 0.42f
                    ).sp,
                lineHeight = (
                    arabicFontSize * if (digits.length >= 3) 0.32f else 0.42f
                    ).sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * Ayah number rendered inside the same compact ring used by continuous Mushaf
 * reading, so list and page modes share one end-of-ayah visual language.
 */
@Composable
private fun AyahOrnamentMarker(
    ayahNumber: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
) {
    val arabicDigits = ayahNumber.toArabicIndic()
    Box(
        modifier = modifier.border(1.dp, tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = arabicDigits,
            color = tint,
            fontWeight = FontWeight.Medium,
            fontSize = if (arabicDigits.length >= 3) 9.sp else 12.sp,
            lineHeight = 13.sp,
        )
    }
}

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
                // Ayah number stamped inside the Mushaf ayah-ending ornament
                // (same marker as continuous reading). Note indicator stacks
                // on the top-right when the ayah has a note.
                Box {
                    AyahOrnamentMarker(
                        ayahNumber = ayah.numberInSurah,
                        modifier = Modifier.size(40.dp)
                    )
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
