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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.starception.submission.R
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.core.qurandatabase.Surah
import com.starception.submission.feature.quran.QuranPlayerViewModel
import com.starception.submission.feature.quran.QuranPlaybackService
import com.starception.submission.feature.quran.AudioLanguage

/**
 * Quran Album Player Screen - Compose version
 * Replicates the album-style design from MusicPlayerAlbumDemoFragment
 * but uses MaterialTheme.colorScheme directly for automatic theme support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranAlbumPlayerScreen(
    surahNumber: Int,
    onBackClick: () -> Unit,
    viewModel: SurahDetailViewModel = hiltViewModel()
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
    var currentVolume by remember { mutableStateOf(0.7f) }
    var showTranslationDialog by remember { mutableStateOf(false) }
    var currentAudioLanguage by remember { mutableStateOf(AudioLanguage.ARABIC_ONLY) }
    var isBookmarked by remember { mutableStateOf(false) }

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

    // Calculate toolbar collapse state
    val isCollapsed = remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 ||
            scrollState.firstVisibleItemScrollOffset > 100
        }
    }

    // Track scroll direction for floating toolbar animation
    var previousScrollOffset by remember { mutableStateOf(0) }
    val isScrollingDown = remember {
        derivedStateOf {
            val currentOffset = scrollState.firstVisibleItemScrollOffset
            val scrollingDown = currentOffset > previousScrollOffset
            previousScrollOffset = currentOffset
            scrollingDown && currentOffset > 50 // Only hide after scrolling past 50dp
        }
    }
    val showFloatingToolbar = !isScrollingDown.value

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {}
        ) { paddingValues ->
        when (val state = uiState) {
            is SurahDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
                    showMusicPlayer = showMusicPlayer,
                    isPlaying = isPlaying,
                    currentProgress = currentProgress,
                    currentVolume = currentVolume,
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
                        currentVolume = volume
                        playbackService?.setVolume(volume)
                    },
                    onAyahClick = { /* TODO */ },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is SurahDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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

    // Overlay toolbar on top of content
    AlbumPlayerTopBar(
        isCollapsed = isCollapsed.value,
        surahName = when (uiState) {
            is SurahDetailUiState.Success -> (uiState as SurahDetailUiState.Success).surah.nameEnglish
            else -> ""
        },
        currentTranslation = currentTranslation,
        isBookmarked = isBookmarked,
        onBackClick = onBackClick,
        onTranslationClick = { showTranslationDialog = true },
        onBookmarkClick = { isBookmarked = !isBookmarked },
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

    // Floating action toolbar on the left side with scroll-based animation
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .align(Alignment.CenterStart)
            .padding(start = 16.dp, top = 80.dp), // Move left and down
        contentAlignment = Alignment.CenterStart
    ) {
        FloatingActionToolbar(
            visible = showFloatingToolbar
        )
    }
}
}

@Composable
private fun AlbumPlayerTopBar(
    isCollapsed: Boolean,
    surahName: String,
    currentTranslation: String,
    isBookmarked: Boolean,
    onBackClick: () -> Unit,
    onTranslationClick: () -> Unit = {},
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

            AnimatedVisibility(
                visible = isCollapsed,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = surahName,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    modifier = Modifier.padding(start = 16.dp)
                )
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

            IconButton(onClick = onBookmarkClick) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = contentColor
                )
            }

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
    showMusicPlayer: Boolean,
    isPlaying: Boolean,
    currentProgress: Float,
    currentVolume: Float,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onAyahClick: (Ayah) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = scrollState,
        modifier = modifier.fillMaxSize()
    ) {
        // Album Header with play FAB
        item {
            Box {
                AlbumHeader(surah = surah)

                // Play FAB positioned half on album artwork, half below
                if (!showMusicPlayer) {
                    FloatingActionButton(
                        onClick = {
                            onPlayPauseClick()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp)
                            .offset(y = 28.dp), // Move down so it's half overlapping
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

        // Music Player Controls (expanded view)
        if (showMusicPlayer) {
            item {
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

        // Album Info Card (shown when player is collapsed)
        if (!showMusicPlayer) {
            item {
                AlbumInfoCard(surah = surah)
            }
        }

        // Ayah List (Track List)
        items(
            items = ayahs,
            key = { it.numberInSurah }
        ) { ayah ->
            AyahTrackItem(
                ayah = ayah,
                onClick = { onAyahClick(ayah) }
            )
        }
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
private fun AlbumInfoCard(surah: Surah) {
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
            // Surah name in English
            Text(
                text = surah.nameEnglish,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            // Surah name in Arabic
            Text(
                text = surah.nameArabic,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.padding(vertical = 24.dp)
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

            Spacer(Modifier.height(24.dp))

            // Title and Artist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = surahName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = surahNameArabic,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(32.dp))

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
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause button
                FilledIconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = onForwardClick,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Volume controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = "Volume down",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
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
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun AyahTrackItem(
    ayah: Ayah,
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

            // Ayah text
            Text(
                text = ayah.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
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
private fun FloatingActionToolbar(
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedButton by remember { mutableStateOf<String?>(null) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally(
            initialOffsetX = { -it }, // Slide in from left
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(durationMillis = 300)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { -it }, // Slide out to left
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(durationMillis = 300)
        )
    ) {
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
            // Bold button
            FloatingToolbarButton(
                icon = Icons.Default.FormatBold,
                contentDescription = "Bold",
                selected = selectedButton == "bold",
                onClick = { selectedButton = if (selectedButton == "bold") null else "bold" }
            )

            // Italic button
            FloatingToolbarButton(
                icon = Icons.Default.FormatItalic,
                contentDescription = "Italic",
                selected = selectedButton == "italic",
                onClick = { selectedButton = if (selectedButton == "italic") null else "italic" }
            )

            // Underline button
            FloatingToolbarButton(
                icon = Icons.Default.FormatUnderlined,
                contentDescription = "Underline",
                selected = selectedButton == "underline",
                onClick = { selectedButton = if (selectedButton == "underline") null else "underline" }
            )

            // Text color button
            FloatingToolbarButton(
                icon = Icons.Default.FormatColorText,
                contentDescription = "Text Color",
                selected = selectedButton == "textColor",
                onClick = { selectedButton = if (selectedButton == "textColor") null else "textColor" }
            )

            // Fill color button
            FloatingToolbarButton(
                icon = Icons.Default.FormatColorFill,
                contentDescription = "Fill Color",
                selected = selectedButton == "fillColor",
                onClick = { selectedButton = if (selectedButton == "fillColor") null else "fillColor" }
            )

            // Strikethrough button
            FloatingToolbarButton(
                icon = Icons.Default.FormatStrikethrough,
                contentDescription = "Strikethrough",
                selected = selectedButton == "strikethrough",
                onClick = { selectedButton = if (selectedButton == "strikethrough") null else "strikethrough" }
            )

            // Left align button
            FloatingToolbarButton(
                icon = Icons.Default.FormatAlignLeft,
                contentDescription = "Align Left",
                selected = selectedButton == "alignLeft",
                onClick = { selectedButton = if (selectedButton == "alignLeft") null else "alignLeft" }
            )

            // Center align button
            FloatingToolbarButton(
                icon = Icons.Default.FormatAlignCenter,
                contentDescription = "Align Center",
                selected = selectedButton == "alignCenter",
                onClick = { selectedButton = if (selectedButton == "alignCenter") null else "alignCenter" }
            )

            // Right align button
            FloatingToolbarButton(
                icon = Icons.Default.FormatAlignRight,
                contentDescription = "Align Right",
                selected = selectedButton == "alignRight",
                onClick = { selectedButton = if (selectedButton == "alignRight") null else "alignRight" }
            )
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
    modifier: Modifier = Modifier
) {
    if (selected) {
        // Darker filled circular button when selected
        FilledIconButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
            shape = CircleShape, // Make selected button circular
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.surface
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
            modifier = modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
