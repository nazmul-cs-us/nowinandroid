package com.starception.submission.feature.surah

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.R
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.designsystem.theme.QuranFonts
import com.starception.submission.core.designsystem.component.scrollbar.DraggableScrollbar
import com.starception.submission.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.starception.submission.core.designsystem.component.scrollbar.scrollbarState
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.core.qurandatabase.QuranRepository
import com.starception.submission.core.qurandatabase.Surah
import com.starception.submission.feature.quran.QuranPlayerViewModel
import com.starception.submission.feature.quran.QuranPlaybackService
import com.starception.submission.feature.quran.AudioLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.Manifest
import android.os.Build
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.core.designsystem.component.NiaTopicTag
import java.util.Locale

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
    viewModel: SurahDetailViewModel = hiltViewModel(),
    quranRepository: QuranRepository = hiltViewModel<QuranRepositoryHolder>().repository,
    userDataRepository: UserDataRepository = hiltViewModel<UserDataRepositoryHolder>().repository
) {
    val context = LocalContext.current

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

    val playerViewModel = remember { QuranPlayerViewModel(context) }

    // Properly clean up the ViewModel when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            // Manually call cleanup to unbind service connection
            playerViewModel.cleanup()
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val currentTranslation by viewModel.currentTranslation.collectAsState()
    val scrollState = rememberLazyListState()

    var showMusicPlayer by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackService by remember { mutableStateOf<QuranPlaybackService?>(null) }
    var currentProgress by remember { mutableStateOf(0f) }

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

    // Bismillah display state from ViewModel (based on database content)
    val showBismillahRow by viewModel.showBismillahRow.collectAsState()

    // Load audio language from ViewModel (persisted in SharedPreferences)
    val savedAudioLanguage by viewModel.currentAudioLanguage.collectAsState()
    var currentAudioLanguage by remember {
        mutableStateOf(
            when (savedAudioLanguage) {
                "ARABIC_ONLY" -> AudioLanguage.ARABIC_ONLY
                "ENGLISH_TRANSLATION" -> AudioLanguage.ENGLISH_TRANSLATION
                "BENGALI_TRANSLATION" -> AudioLanguage.BENGALI_TRANSLATION
                else -> AudioLanguage.ARABIC_ONLY
            }
        )
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

    // Track bookmark state using UserDataRepository (the correct repository for news bookmarks)
    var isBookmarked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Sync bookmark state from UserDataRepository if we have a news resource ID
    LaunchedEffect(newsResourceId) {
        if (newsResourceId != null) {
            val userData = userDataRepository.userData.first()
            val bookmarkState = newsResourceId in userData.bookmarkedNewsResources
            android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "🔄 SYNC | surah=$surahNumber | newsResourceId=$newsResourceId | bookmarked=$bookmarkState")
            isBookmarked = bookmarkState
        } else {
            android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "⚠️ NO_NEWS_ID | surah=$surahNumber | bookmark disabled")
        }
    }

    // Load topics for this news resource
    val topics by viewModel.topics.collectAsState()
    LaunchedEffect(newsResourceId) {
        viewModel.loadTopicsForNewsResource(newsResourceId)
    }

    val availableTranslations = remember { viewModel.getAvailableTranslations() }

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
                }

                // Update current playing surah number when next/previous is pressed
                playbackService?.onSurahChanged = { surahIndex ->
                    val newSurahNumber = surahIndex + 1 // Convert 0-based index to 1-based surah number
                    android.util.Log.d("QuranAlbumPlayer", "🔄 SURAH_CHANGED | index=$surahIndex | surahNumber=$newSurahNumber")
                    currentPlayingSurahNumber = newSurahNumber
                }

                playbackService?.setAudioLanguage(currentAudioLanguage)
                isPlaying = playbackService?.isPlaying() ?: false
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
                scrollState.animateScrollToItem(ayahIndex)
            }
        }
    }

    // Calculate toolbar collapse state with smooth single-stage transition:
    // At top (album art visible) → Transparent
    // Scrolled down (past album art) → Solid theme color
    val collapseProgress = remember {
        derivedStateOf {
            val itemIndex = scrollState.firstVisibleItemIndex
            val offset = scrollState.firstVisibleItemScrollOffset.toFloat()

            when {
                // Past the header - fully solid
                itemIndex >= 1 -> 1f

                // Within AlbumHeader + InfoCard (item 0)
                // Smooth transition as album art scrolls off
                itemIndex == 0 -> {
                    // Transition zone: 600px to 1000px of scroll
                    val transitionStart = 600f
                    val transitionEnd = 1000f

                    when {
                        offset <= transitionStart -> 0f  // Transparent
                        offset >= transitionEnd -> 1f    // Solid
                        else -> (offset - transitionStart) / (transitionEnd - transitionStart)
                    }
                }

                else -> 0f
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
    var isFloatingToolbarExpanded by remember { mutableStateOf(false) } // Left-side floating toolbar starts collapsed (hint only)
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

                // Only collapse floating toolbar when scrolling up AND it's currently expanded
                if (!isScrollingDown && isFloatingToolbarExpanded) {
                    isFloatingToolbarExpanded = false
                }
            }

            // Update previous scroll position
            previousScrollOffset = currentOffset
            previousItemIndex = currentItemIndex
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Scaffold(
            topBar = {}
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
                AlbumPlayerContent(
                    surah = state.surah,
                    ayahs = state.ayahs,
                    scrollState = scrollState,
                    collapseProgress = collapseProgress.value,
                    showMusicPlayer = showMusicPlayer,
                    isPlaying = isPlaying,
                    currentProgress = currentProgress,
                    currentVolume = currentVolume,
                    currentPlayingSurahNumber = currentPlayingSurahNumber,
                    currentPlayingSurah = currentPlayingSurah,
                    currentPlayingAyahs = currentPlayingAyahs,
                    showFabVisible = showFabVisible,
                    selectedArabicFont = selectedArabicFont,
                    arabicFontSize = arabicFontSize,
                    textAlignment = textAlignment,
                    showTranslationInText = showTranslationInText,
                    showBismillahRow = showBismillahRow,
                    showTajweed = showTajweed,
                    tajweedAnnotations = tajweedAnnotations,
                    onToggleTajweed = { viewModel.changeTajweed(!showTajweed) },
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
                        if (service != null) {
                            if (service.isPlaying()) {
                                service.togglePlayPause()
                            } else {
                                playWithPermissionCheck {
                                    showMusicPlayer = true
                                    service.setAudioLanguage(currentAudioLanguage)
                                    service.playSurah(surahNumber - 1, true)
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
                        if (service != null) {
                            if (service.isPlaying()) {
                                service.togglePlayPause()
                            } else {
                                playWithPermissionCheck {
                                    showMusicPlayer = true
                                    service.setAudioLanguage(currentAudioLanguage)
                                    service.playSurah(surahNumber - 1, true)
                                }
                            }
                        }
                    },
                    onCollapseMusicPlayer = { showMusicPlayer = false },
                    onWordStudyClick = { ayahNumber ->
                        viewModel.loadWordStudy(surahNumber, ayahNumber)
                        showWordStudyDialog = true
                    },
                    onTafseerClick = { ayahNumber ->
                        viewModel.loadTafseer(surahNumber, ayahNumber)
                        showTafseerDialog = true
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
                    modifier = Modifier
                )
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
        }
    }

        // Always visible toolbar with collapsing effect based on scroll position
        AlbumPlayerTopBar(
            collapseProgress = collapseProgress.value,
            isCollapsed = isCollapsed.value,
            surahName = when (uiState) {
                is SurahDetailUiState.Success -> (uiState as SurahDetailUiState.Success).surah.nameEnglish
                else -> ""
            },
            surahNameArabic = when (uiState) {
                is SurahDetailUiState.Success -> (uiState as SurahDetailUiState.Success).surah.nameArabic
                else -> ""
            },
            currentTranslation = currentTranslation,
            isBookmarked = isBookmarked,
            selectedArabicFont = selectedArabicFont,
            showTajweed = showTajweed,
            onBackClick = onBackClick,
            onTranslationClick = { showTranslationDialog = true },
            onFontClick = { showFontDialog = true },
            onTajweedClick = { viewModel.changeTajweed(!showTajweed) },
            onBookmarkClick = {
                // Only toggle bookmark if we have a valid news resource ID
                if (newsResourceId != null) {
                    val oldState = isBookmarked
                    val newState = !oldState
                    isBookmarked = newState
                    android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "👆 CLICK | surah=$surahNumber | newsResourceId=$newsResourceId | old_state=$oldState | new_state=$newState")

                    // Update bookmark in UserDataRepository (correct repository for news bookmarks)
                    coroutineScope.launch {
                        userDataRepository.setNewsResourceBookmarked(newsResourceId, newState)
                        android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "✅ CLICK_COMPLETE | surah=$surahNumber | newsResourceId=$newsResourceId | state=$newState")
                    }
                } else {
                    android.util.Log.d("QuranAlbumPlayer_BOOKMARK", "⚠️ CLICK_IGNORED | surah=$surahNumber | no newsResourceId")
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Floating Surah name that moves from info card to toolbar when scrolling
        // Uses scroll offset for smooth translation (like DuaDetailScreen)
        if (uiState is SurahDetailUiState.Success) {
            val surah = (uiState as SurahDetailUiState.Success).surah
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current

            // Calculate positions once (stable values)
            val albumHeaderHeight = configuration.screenWidthDp
            val headerYPx = with(density) { (albumHeaderHeight + 24).dp.toPx() }
            val toolbarYPx = with(density) { 16.dp.toPx() }  // Moved down for better vertical centering in toolbar
            val startXPx = with(density) { 24.dp.toPx() }
            val endXPx = with(density) { 56.dp.toPx() }

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
            val scale = 1f - (progress * 0.4f)
            val contentColor = MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .graphicsLayer {
                        translationX = xOffsetPx
                        translationY = namesYPx
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
            ) {
                Column {
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
                    // Change the text display
                    viewModel.changeTranslation(translationCode, surahNumber)

                    // Try to map translation to audio language
                    val mappedAudioLanguage = mapTranslationCodeToAudioLanguage(translationCode)

                    if (mappedAudioLanguage != null) {
                        // Translation has audio support
                        currentAudioLanguage = mappedAudioLanguage
                        viewModel.changeAudioLanguage(mappedAudioLanguage.name)
                        val service = playbackService
                        if (service != null) {
                            service.setAudioLanguage(mappedAudioLanguage)
                            val surahIndex = surahNumber - 1
                            val shouldAutoPlay = service.isPlaying()
                            service.playSurah(surahIndex, shouldAutoPlay)
                        }
                        Toast.makeText(
                            context,
                            "Translation applied with ${getAudioLanguageDisplayName(mappedAudioLanguage)} audio",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Translation has no audio support, keep playing Arabic
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

        // Floating action toolbar - draggable hint icon (vertical only, sticks to edges)
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenWidth = configuration.screenWidthDp.dp
        val albumHeaderHeight = screenWidth // AlbumHeader is square (aspectRatio 1f)
        val baseVerticalOffset = albumHeaderHeight + 40.dp // Position closer to bottom of album image
        val baseVerticalOffsetPx = with(density) { baseVerticalOffset.toPx() }

        var toolbarOffsetY by remember { mutableStateOf(0f) }
        var isOnRightSide by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .align(if (isOnRightSide) Alignment.TopEnd else Alignment.TopStart)
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            0,
                            (baseVerticalOffsetPx + toolbarOffsetY).toInt()
                        )
                    }
            ) {
                FloatingActionToolbar(
                    isExpanded = isFloatingToolbarExpanded,
                    onExpandedChange = { expanded ->
                        isFloatingToolbarExpanded = expanded
                    },
                    currentFontSize = arabicFontSize,
                    onIncreaseFontSize = {
                        if (arabicFontSize < maxFontSize) {
                            viewModel.changeArabicFontSize(arabicFontSize + 2f)
                        }
                    },
                    onDecreaseFontSize = {
                        if (arabicFontSize > minFontSize) {
                            viewModel.changeArabicFontSize(arabicFontSize - 2f)
                        }
                    },
                    isOnRightSide = isOnRightSide,
                    onDrag = { dragAmount ->
                        // Only allow vertical movement
                        toolbarOffsetY += dragAmount.y
                    },
                    onSideSwap = {
                        // Swap between left and right sides
                        isOnRightSide = !isOnRightSide
                    },
                    textAlignment = textAlignment,
                    onSetAlignment = { alignment ->
                        viewModel.changeTextAlignment(alignment)
                    },
                    showTranslation = showTranslationInText,
                    onToggleTranslation = { viewModel.changeShowTranslation(!showTranslationInText) }
                )
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

    Surface(
        color = backgroundColor,
        tonalElevation = (4 * collapseProgress).dp, // Smooth elevation transition
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
                    tint = contentColor
                )
            }

            // Spacer for title area (actual floating title is an overlay)
            Spacer(Modifier.weight(1f))

            // Translation button with language indicator
            Surface(
                onClick = onTranslationClick,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Translation",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = translationDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Font selection button with font hint
            val fontDisplay = when (selectedArabicFont) {
                "pdms_saleem" -> "PS"
                "noor_e_hidayat" -> "NH"
                "thabit" -> "TH"
                "uthmani_script" -> "US"
                "indopak_script" -> "IP"
                else -> "F"
            }
            Surface(
                onClick = onFontClick,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFormat,
                        contentDescription = "Font selection",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = fontDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Tajweed toggle button - uses Check icons like bookmark
            IconButton(onClick = onTajweedClick) {
                Icon(
                    imageVector = if (showTajweed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                    contentDescription = if (showTajweed) "Disable Tajweed colors" else "Enable Tajweed colors",
                    tint = contentColor
                )
            }

            IconButton(onClick = onBookmarkClick) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = contentColor
                )
            }

            // More options menu
            IconButton(onClick = { /* TODO: More */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    // Note state - integrated into bottom sheet (no separate dialog)
    var bottomSheetNoteMode by remember { mutableStateOf(false) } // true = show notes, false = show menu
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
        // Album Header with either FAB+Info Card OR Music Player Controls
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column {
                    AlbumHeader(
                        surah = surah,
                        topics = topics,
                        onTopicClick = onTopicClick
                    )

                    // Fixed-height container to prevent FAB position jump during transitions
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(196.dp) // Fixed height matches original AlbumInfoCard
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
                                    collapseProgress = collapseProgress
                                )
                            }
                        }
                    }
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

        // Bismillah row - shown only if first ayah contains Bismillah in database
        // This is determined by checking the actual ayah text in the database
        if (showBismillahRow) {
            item(key = "bismillah") {
                BismillahRow(
                    arabicFont = selectedArabicFont,
                    arabicFontSize = arabicFontSize,
                    textAlignment = textAlignment
                )
            }
        }

        // Ayah List (Track List) - use displayAyahs for current playing surah
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
                onClick = { onAyahClick(ayah) },
                onLongPress = {
                    selectedAyahForOptions = ayah.numberInSurah
                    showBottomSheet = true
                },
                onDoubleTap = {
                    val ayahNumber = ayah.numberInSurah
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
            )
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
                bottomSheetNoteMode = false
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

                        // Content with padding - switches between menu and notes mode
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            // Animated content switch between menu and notes
                            AnimatedContent(
                                targetState = bottomSheetNoteMode,
                                transitionSpec = {
                                    if (targetState) {
                                        // Entering notes mode
                                        slideInHorizontally { it } + fadeIn() togetherWith
                                            slideOutHorizontally { -it } + fadeOut()
                                    } else {
                                        // Returning to menu
                                        slideInHorizontally { -it } + fadeIn() togetherWith
                                            slideOutHorizontally { it } + fadeOut()
                                    }
                                },
                                label = "BottomSheetContent"
                            ) { isNoteMode ->
                                if (isNoteMode) {
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
                                            bottomSheetNoteMode = false
                                            noteText = ""
                                            editingNote = null
                                        }
                                    )
                                } else {
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
                                                bottomSheetNoteMode = true
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
                                                onTafseerClick(selectedAyahForOptions!!)
                                                showBottomSheet = false
                                            }
                                        )

                                        BottomSheetOption(
                                            icon = Icons.Default.Book,
                                            title = "Word Study",
                                            description = "",
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Transparent,
                                            onClick = {
                                                onWordStudyClick(selectedAyahForOptions!!)
                                                showBottomSheet = false
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

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
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
                onClick = onSaveNote,
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

@Composable
private fun AlbumHeader(
    surah: Surah,
    topics: List<com.starception.submission.core.topicsdatabase.Topic> = emptyList(),
    onTopicClick: (String) -> Unit = {}
) {
    // Album cover images (using cover resources from Fragment)
    val coverImages = remember {
        listOf(
            R.drawable.album_ellen_qin_unsplash,
            R.drawable.album_jean_philippe_delberghe_unsplash,
            R.drawable.album_karina_vorozheeva_unsplash,
            R.drawable.album_amy_shamblen_unsplash,
            R.drawable.album_pawel_czerwinski_unsplash,
            R.drawable.album_david_clode_unsplash
        )
    }

    val coverIndex = (surah.number - 1) % coverImages.size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Square album cover
    ) {
        // Album cover image
        Image(
            painter = painterResource(coverImages[coverIndex]),
            contentDescription = "Album cover for ${surah.nameEnglish}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay at bottom with topic chips
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        )
                    )
                )
        ) {
            // Topic chips on the gradient overlay - using NiaTopicTag for consistency with news cards
            if (topics.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    topics.forEach { topic ->
                        NiaTopicTag(
                            followed = false,
                            onClick = { onTopicClick(topic.id) },
                            text = {
                                Text(
                                    text = topic.name.uppercase(Locale.getDefault())
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumInfoCard(
    surah: Surah,
    selectedArabicFont: String,
    collapseProgress: Float = 0f
) {
    // Use MaterialTheme.colorScheme for automatic theme support
    // NOTE: Surah names are now handled by the floating overlay for smooth scroll transition
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight() // Fill parent Box container (196dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Spacer for the floating Surah name overlay (names are now positioned as overlay)
            Spacer(Modifier.height(56.dp))

            // Surah meaning (translation of the name)
            if (surah.nameTranslation.isNotBlank()) {
                Text(
                    text = "\"${surah.nameTranslation}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
            }

            // Surah info chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoChip(text = "${surah.ayahCount} Ayahs")
                InfoChip(text = surah.revelationType)
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
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
    // Dark player controls matching reference design
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
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            // Progress bar at top
            LinearProgressIndicator(
                progress = { currentProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White,
                trackColor = Color.Gray.copy(alpha = 0.3f),
            )

            // Spacer for the floating Surah name overlay (names are handled by the overlay)
            Spacer(Modifier.height(72.dp))

            // Playback controls - minimal style matching reference
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
                        tint = Color.White,
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
                        tint = Color.White,
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
                        tint = Color.White,
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
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )

                Slider(
                    value = currentVolume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )

                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volume up",
                    tint = Color.White,
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
    // Bismillah text in Arabic
    val bismillahText = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

    // Convert alignment string to TextAlign enum
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

    Surface(
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            // Bismillah text with user-selected font and size - keep in single line
            val arabicTextStyle = getArabicFontStyle(arabicFont, arabicFontSize)
            Text(
                text = bismillahText,
                style = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Divider
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
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
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {}
) {
    // Use MaterialTheme.colorScheme for automatic theme support
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
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
    onIncreaseFontSize: () -> Unit = {},
    onDecreaseFontSize: () -> Unit = {},
    isOnRightSide: Boolean = false,
    onDrag: (Offset) -> Unit = {},
    onSideSwap: () -> Unit = {},
    textAlignment: String = "start",
    onSetAlignment: (String) -> Unit = {},
    showTranslation: Boolean = true,
    onToggleTranslation: () -> Unit = {},
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
                        enabled = currentFontSize < 60f
                    )

                    // Decrease font size button
                    FloatingToolbarButton(
                        icon = Icons.Default.TextDecrease,
                        contentDescription = "Decrease font size",
                        selected = false,
                        onClick = onDecreaseFontSize,
                        enabled = currentFontSize > 14f
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
