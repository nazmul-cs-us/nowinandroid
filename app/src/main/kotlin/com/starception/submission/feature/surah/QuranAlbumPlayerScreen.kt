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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
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

/**
 * Quran Album Player Screen - Compose version
 * Replicates the album-style design from MusicPlayerAlbumDemoFragment
 * but uses MaterialTheme.colorScheme directly for automatic theme support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranAlbumPlayerScreen(
    surahNumber: Int,
    newsResourceId: String? = null, // News resource ID for bookmark tracking
    onBackClick: () -> Unit,
    viewModel: SurahDetailViewModel = hiltViewModel(),
    quranRepository: QuranRepository = hiltViewModel<QuranRepositoryHolder>().repository,
    userDataRepository: UserDataRepository = hiltViewModel<UserDataRepositoryHolder>().repository
) {
    val context = LocalContext.current
    val playerViewModel = remember { QuranPlayerViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val currentTranslation by viewModel.currentTranslation.collectAsState()
    val scrollState = rememberLazyListState()

    var showMusicPlayer by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackService by remember { mutableStateOf<QuranPlaybackService?>(null) }
    var currentProgress by remember { mutableStateOf(0f) }

    // Load volume from ViewModel (persisted in SharedPreferences)
    val currentVolume by viewModel.currentVolume.collectAsState()

    var showTranslationDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

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

    // Font size state - loaded from ViewModel (which reads from SharedPreferences)
    val arabicFontSize by viewModel.arabicFontSize.collectAsState()
    val minFontSize = 14f
    val maxFontSize = 60f  // Increased from 40f to 60f for much larger text

    // Translation visibility toggle state - loaded from ViewModel (persisted in SharedPreferences)
    val showTranslationInText by viewModel.showTranslation.collectAsState()

    // Text alignment state - loaded from ViewModel (persisted in SharedPreferences)
    val textAlignment by viewModel.textAlignment.collectAsState()

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

    // Calculate toolbar collapse state with smooth transition
    // Track when AlbumInfoCard (item 1) is scrolling up toward the toolbar
    val collapseProgress = remember {
        derivedStateOf {
            when {
                scrollState.firstVisibleItemIndex > 1 -> 1f
                scrollState.firstVisibleItemIndex == 1 -> {
                    // Smooth transition as item 1 (AlbumInfoCard) scrolls up
                    val offset = scrollState.firstVisibleItemScrollOffset
                    (offset / 200f).coerceIn(0f, 1f)
                }
                scrollState.firstVisibleItemIndex == 0 -> {
                    // Check if we're near the end of item 0 (AlbumHeader)
                    // Assuming AlbumHeader height is around screen width (square)
                    val offset = scrollState.firstVisibleItemScrollOffset
                    ((offset - 800) / 200f).coerceIn(0f, 1f)
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
                    showFabVisible = showFabVisible,
                    selectedArabicFont = selectedArabicFont,
                    arabicFontSize = arabicFontSize,
                    textAlignment = textAlignment,
                    showTranslationInText = showTranslationInText,
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
                                showMusicPlayer = true
                                service.setAudioLanguage(currentAudioLanguage)
                                service.playSurah(surahNumber - 1, true)
                            }
                        }
                    },
                    onRewindClick = {
                        playbackService?.playPrevious()
                    },
                    onForwardClick = {
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
                                showMusicPlayer = true
                                service.setAudioLanguage(currentAudioLanguage)
                                service.playSurah(surahNumber - 1, true)
                            }
                        }
                    },
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
            onBackClick = onBackClick,
            onTranslationClick = { showTranslationDialog = true },
            onFontClick = { showFontDialog = true },
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
        var toolbarOffsetY by remember { mutableStateOf(0f) }
        var isOnRightSide by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .align(if (isOnRightSide) Alignment.CenterEnd else Alignment.CenterStart)
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            0,
                            toolbarOffsetY.toInt()
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
    onBackClick: () -> Unit,
    onTranslationClick: () -> Unit = {},
    onFontClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Use MaterialTheme.colorScheme for automatic theme support
    val backgroundColor = if (isCollapsed) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.Transparent
    }

    val contentColor = if (isCollapsed) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }

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
        tonalElevation = if (isCollapsed) 4.dp else 0.dp,
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

            // Smooth blending title that appears to rise from AlbumInfoCard
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .graphicsLayer {
                        alpha = collapseProgress
                        scaleX = 0.9f + (collapseProgress * 0.1f)
                        scaleY = 0.9f + (collapseProgress * 0.1f)
                    }
            ) {
                if (collapseProgress > 0f) {
                    // Surah name in English - matches AlbumInfoCard styling
                    Text(
                        text = surahName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Surah name in Arabic - matches AlbumInfoCard styling
                    Text(
                        text = surahNameArabic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Translation button with indicator
            Box(
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onTranslationClick) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translation",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = translationDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Font selection button with icon in rounded box
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
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = "Font selection",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    showFabVisible: Boolean,
    selectedArabicFont: String,
    arabicFontSize: Float,
    textAlignment: String,
    showTranslationInText: Boolean,
    onToggleTranslation: () -> Unit,
    onCycleAlignment: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onAyahClick: (Ayah) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate total items for scrollbar state
    val totalItems = remember(ayahs, showMusicPlayer) {
        1 + // AlbumHeader
        (if (showMusicPlayer) 1 else 0) + // MusicPlayerControls
        (if (!showMusicPlayer) 1 else 0) + // AlbumInfoCard
        ayahs.size // Ayah items
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
        // Album Header with FAB and Info Card combined for proper FAB overlap
        if (!showMusicPlayer) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Column {
                        AlbumHeader(surah = surah)
                        AlbumInfoCard(
                            surah = surah,
                            collapseProgress = collapseProgress
                        )
                    }

                    // FAB positioned with more overlap on the info card
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
                            .padding(end = 24.dp)
                    ) {
                        FloatingActionButton(
                            onClick = onFabClick,
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
        }

        // Music Player Controls (expanded view)
        if (showMusicPlayer) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Column {
                        AlbumHeader(surah = surah)
                        MusicPlayerControls(
                            isPlaying = isPlaying,
                            currentProgress = currentProgress,
                            currentVolume = currentVolume,
                            surahName = surah.nameEnglish,
                            surahNameArabic = surah.nameArabic,
                            onPlayPauseClick = onPlayPauseClick,
                            onRewindClick = onRewindClick,
                            onForwardClick = onForwardClick,
                            onVolumeChange = onVolumeChange
                        )
                    }
                }
            }
        }

        // Ayah List (Track List)
        items(
            items = ayahs,
            key = { it.numberInSurah }
        ) { ayah ->
            AyahTrackItem(
                ayah = ayah,
                arabicFont = selectedArabicFont,
                arabicFontSize = arabicFontSize,
                textAlignment = textAlignment,
                showTranslation = showTranslationInText,
                onClick = { onAyahClick(ayah) }
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
    }
}

@Composable
private fun AlbumHeader(surah: Surah) {
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

        // Gradient overlay at bottom
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
        )
    }
}

@Composable
private fun AlbumInfoCard(
    surah: Surah,
    collapseProgress: Float = 0f
) {
    // Use MaterialTheme.colorScheme for automatic theme support
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Surah name in English - fades out as it blends into toolbar
            Text(
                text = surah.nameEnglish,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - collapseProgress
                }
            )

            Spacer(Modifier.height(8.dp))

            // Surah name in Arabic - fades out as it blends into toolbar
            Text(
                text = surah.nameArabic,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - collapseProgress
                }
            )

            Spacer(Modifier.height(16.dp))

            // Surah info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dark player controls matching reference design
    Surface(
        modifier = modifier.fillMaxWidth(),
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

            Spacer(Modifier.height(12.dp))

            // Title and Artist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = surahName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = surahNameArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button
                IconButton(
                    onClick = onRewindClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play/Pause button
                FilledIconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = onForwardClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
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
private fun AyahTrackItem(
    ayah: Ayah,
    arabicFont: String = "default",
    arabicFontSize: Float = 22f,
    textAlignment: String = "start",
    showTranslation: Boolean = true,
    onClick: () -> Unit
) {
    // Use MaterialTheme.colorScheme for automatic theme support
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number
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
                Text(
                    text = arabicText,
                    style = MaterialTheme.typography.bodyLarge.merge(arabicTextStyle),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )

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
        contentAlignment = if (isOnRightSide) Alignment.CenterEnd else Alignment.CenterStart
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
