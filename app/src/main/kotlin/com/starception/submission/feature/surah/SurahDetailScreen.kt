package com.starception.submission.feature.surah

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.core.qurandatabase.Surah
import com.starception.submission.core.qurandatabase.QuranTranslationHelper
import com.starception.submission.core.qurandatabase.QuranTranslationRepository
import com.starception.submission.feature.quran.QuranPlayerViewModel
import com.starception.submission.feature.quran.QuranData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SurahDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow<SurahDetailUiState>(SurahDetailUiState.Loading)
    val uiState: StateFlow<SurahDetailUiState> = _uiState.asStateFlow()
    
    private val _currentTranslation = MutableStateFlow(
        prefs.getString("quran_translation", "ar") ?: "ar"
    )
    val currentTranslation: StateFlow<String> = _currentTranslation.asStateFlow()
    
    private val translations = QuranTranslationHelper.getAvailableTranslations()
    
    fun getRepository(translationCode: String): QuranTranslationRepository {
        return QuranTranslationRepository(context, translationCode)
    }
    
    fun loadSurah(surahNumber: Int) {
        loadSurah(surahNumber, _currentTranslation.value)
    }
    
    fun loadSurah(surahNumber: Int, translationCode: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SurahDetail", "🔍 Loading Surah $surahNumber in translation: $translationCode")
                
                val repository = try {
                    getRepository(translationCode)
                } catch (e: Exception) {
                    android.util.Log.e("SurahDetail", "❌ Failed to create repository for translation: $translationCode", e)
                    _uiState.value = SurahDetailUiState.Error("Failed to load translation database: ${e.message}")
                    return@launch
                }
                
                val surah = repository.getSurahByNumber(surahNumber)
                
                if (surah == null) {
                    android.util.Log.e("SurahDetail", "❌ Surah $surahNumber not found in translation: $translationCode")
                    _uiState.value = SurahDetailUiState.Error("Surah not found in $translationCode translation")
                    return@launch
                }
                
                android.util.Log.d("SurahDetail", "✅ Surah found: ${surah.nameEnglish} (ID: ${surah.id}, Number: ${surah.number})")
                
                val ayahs = repository.getAyahsBySurahOnce(surah.id)
                
                android.util.Log.d("SurahDetail", "✅ Loaded ${ayahs.size} Ayahs from $translationCode")
                _uiState.value = SurahDetailUiState.Success(surah, ayahs)
            } catch (e: Exception) {
                android.util.Log.e("SurahDetail", "❌ Error loading Surah $surahNumber in translation: $translationCode", e)
                e.printStackTrace()
                _uiState.value = SurahDetailUiState.Error("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    fun changeTranslation(translationCode: String, surahNumber: Int) {
        viewModelScope.launch {
            _currentTranslation.value = translationCode
            prefs.edit().putString("quran_translation", translationCode).apply()
            loadSurah(surahNumber, translationCode)
        }
    }
    
    fun getAvailableTranslations(): List<String> = translations
    
    fun getTranslationName(code: String): String = QuranTranslationHelper.getTranslationName(code)
}

sealed interface SurahDetailUiState {
    data object Loading : SurahDetailUiState
    data class Success(val surah: Surah, val ayahs: List<Ayah>) : SurahDetailUiState
    data class Error(val message: String) : SurahDetailUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahDetailScreen(
    surahNumber: Int,
    onBackClick: () -> Unit,
    viewModel: SurahDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playerViewModel = remember { QuranPlayerViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val currentTranslation by viewModel.currentTranslation.collectAsState()
    var showTranslationDialog by remember { mutableStateOf(false) }
    var showMusicPlayer by remember { mutableStateOf(false) }
    var currentSurahNumber by remember { mutableStateOf(surahNumber) }
    
    val scrollState = rememberLazyListState()
    
    // Observe player state using LaunchedEffect (since ViewModel uses mutableStateOf)
    var isPlaying by remember { mutableStateOf(false) }
    var currentSurahIndex by remember { mutableStateOf(0) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = playerViewModel.isPlaying
            currentSurahIndex = playerViewModel.currentSurahIndex
            currentPosition = playerViewModel.currentPosition
            duration = playerViewModel.duration
            kotlinx.coroutines.delay(500)
        }
    }
    
    // Map surah number to index (surah numbers are 1-based, indices are 0-based)
    val surahIndex = remember(surahNumber) {
        QuranData.surahs.indexOfFirst { it.number == surahNumber }
    }
    
    val isCurrentSurahPlaying = surahIndex >= 0 && currentSurahIndex == surahIndex
    
    LaunchedEffect(currentSurahNumber) {
        viewModel.loadSurah(currentSurahNumber)
    }
    
    Scaffold(
        containerColor = Color.Transparent, // Make Scaffold background transparent
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Remove window insets so content fills screen
        topBar = {
                    when (val state = uiState) {
                is SurahDetailUiState.Success -> {
                    // Transparent header that overlays the gradient background
                    LargeTopAppBar(
                        title = { 
                            Text(
                                text = state.surah.nameEnglish,
                                color = Color.White.copy(alpha = 0.9f) // White text over gradient
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White // White arrow over gradient
                                )
                            }
                        },
                        actions = {
                            // Heart icon (faint outline like in image)
                            IconButton(onClick = { /* TODO: Add bookmark functionality */ }) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = "Bookmark",
                                    tint = Color.White.copy(alpha = 0.7f) // Faint white outline
                                )
                            }
                            // Three dots menu (hidden in image, but keep for functionality)
                            IconButton(onClick = { showTranslationDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent, // Transparent to show gradient
                            titleContentColor = Color.White, // White text
                            navigationIconContentColor = Color.White, // White icons
                            actionIconContentColor = Color.White // White action icons
                        ),
                        scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                    )
                }
                else -> {
                    TopAppBar(
                        title = { Text("Loading...") },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White // White like reference
                                )
                            }
                        },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
                }
            }
        }
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
            
            is SurahDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            is SurahDetailUiState.Success -> {
                // No padding - gradient should extend to top including under status bar
                SurahContentWithMusicPlayer(
                    surah = state.surah,
                    ayahs = state.ayahs,
                    scrollState = scrollState,
                    isPlaying = isCurrentSurahPlaying && isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    showMusicPlayer = showMusicPlayer,
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onPrevious = { playerViewModel.playPrevious() },
                    onNext = { playerViewModel.playNext() },
                    onSeek = { position -> playerViewModel.seekTo(position) },
                    onToggleMusicPlayer = { 
                        showMusicPlayer = !showMusicPlayer
                        if (!showMusicPlayer && surahIndex >= 0 && !isCurrentSurahPlaying) {
                            playerViewModel.playSurah(surahIndex)
                        }
                    }
                    // Removed modifier.padding(paddingValues) - gradient should fill entire screen
                )
            }
        }
    }
    
    // Translation selection dialog
    if (showTranslationDialog) {
        TranslationSelectorDialog(
            currentTranslation = currentTranslation,
            availableTranslations = viewModel.getAvailableTranslations(),
            getTranslationName = { viewModel.getTranslationName(it) },
            onTranslationSelected = { translationCode ->
                viewModel.changeTranslation(translationCode, currentSurahNumber)
                showTranslationDialog = false
            },
            onDismiss = { showTranslationDialog = false }
        )
    }
}

@Composable
fun SurahContentWithMusicPlayer(
    surah: Surah,
    ayahs: List<Ayah>,
    scrollState: LazyListState,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    showMusicPlayer: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleMusicPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Fixed full-screen header (album image + bottom bar) - stays in place
        SurahHeaderSection(
            surah = surah,
            ayahsCount = ayahs.size,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            showMusicPlayer = showMusicPlayer,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeek = onSeek,
            onToggleMusicPlayer = onToggleMusicPlayer,
            modifier = Modifier.fillMaxSize()
        )
        
        // Scrollable track list (RecyclerView equivalent) - scrolls over the image
        // Matches: RecyclerView with appbar_scrolling_view_behavior
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(
                top = 200.dp, // Account for TopAppBar height + spacing
                bottom = 8.dp
            ),
            // Add padding for status bar so content doesn't overlap it
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp) // Account for bottom info bar
                .windowInsetsPadding(WindowInsets.statusBars),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Large spacer to push content below the visible image area
            // This allows scrolling while keeping header visible initially
            item {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                Spacer(modifier = Modifier.height(screenHeight - 120.dp - 200.dp))
            }
            
            // Track List (Ayahs) - appears as you scroll
            items(
                items = ayahs,
                key = { it.id }
            ) { ayah ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    AyahTrackItem(
                        ayah = ayah,
                        isPlaying = false, // TODO: Track current ayah
                        onClick = { }
                    )
                }
            }
        }
    }
}

@Composable
fun SurahHeaderSection(
    surah: Surah,
    ayahsCount: Int,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    showMusicPlayer: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleMusicPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Full-screen album image with vibrant gradient - extends behind status bar
        Box(
            modifier = Modifier
                .fillMaxSize() // Fill entire screen including behind status bar
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF00BCD4), // Teal/Cyan at top
                            Color(0xFF7B1FA2), // Deep Purple
                            Color(0xFF9C27B0), // Purple
                            Color(0xFFE91E63), // Magenta/Pink
                            Color(0xFFFF5722), // Deep Orange
                            Color(0xFFFF9800), // Orange (bright light source)
                            Color(0xFFFF6F00) // Darker orange at bottom
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Optional: Could add surah number or decorative elements here
            // Keeping it clean like the reference
        }
        
        // Dark bottom bar with surah info (exactly like reference - dark blue-grey)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(120.dp),
            color = Color(0xFF2C3E50), // Exact dark blue-grey from reference
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 100.dp, top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Album title - large white text (like "Metamorphosis")
                Text(
                    text = surah.nameEnglish,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Artist name - smaller white text (like "Sandra Adams")
                Text(
                    text = surah.nameArabic,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Play button - rounded square overlapping bottom-right (exactly like reference)
        Surface(
            onClick = {
                if (showMusicPlayer) {
                    onToggleMusicPlayer()
                } else {
                    onToggleMusicPlayer()
                    if (!isPlaying) {
                        onPlayPause()
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-16).dp, y = (-16).dp)
                .size(64.dp),
            shape = RoundedCornerShape(16.dp), // Rounded square like reference
            color = Color(0xFFB0BEC5), // Light blue-grey like reference
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showMusicPlayer) {
                        Icons.Default.Close
                    } else if (isPlaying) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (showMusicPlayer) "Close" else if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFF263238), // Dark blue-grey icon like reference
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Music Player Container (shown when showMusicPlayer is true)
        androidx.compose.animation.AnimatedVisibility(
            visible = showMusicPlayer,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(400)) + slideInVertically(
                animationSpec = tween(400),
                initialOffsetY = { it }
            ),
            exit = fadeOut(tween(300)) + slideOutVertically(
                animationSpec = tween(300),
                targetOffsetY = { it }
            )
        ) {
            MusicPlayerContainerIntegrated(
                surah = surah,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                onClose = onToggleMusicPlayer
            )
        }
    }
}
@Composable
fun MusicPlayerContainerIntegrated(
    surah: Surah,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var volume by remember { mutableStateOf(3.5f) }
    
    val progressValue = remember(currentPosition, duration) {
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f
    }
    
    // Format time helper
    fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color.Black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Enhanced progress indicator with time display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    trackColor = Color.White.copy(alpha = 0.2f),
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Time labels
                Row(
            modifier = Modifier
                .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Enhanced control buttons with better spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button with enhanced styling
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Enhanced play/pause button with larger size
                FloatingActionButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp),
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Next button with enhanced styling
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Enhanced volume controls with better layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { volume = (volume - 0.5f).coerceAtLeast(0f) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = "Volume Down",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Slider(
                        value = volume / 10f,
                        onValueChange = { volume = (it * 10f).coerceIn(0f, 10f) },
                            modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    
                    IconButton(
                        onClick = { volume = (volume + 0.5f).coerceAtMost(10f) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Volume Up",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Surah name display
                Text(
                    text = surah.nameEnglish,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AyahTrackItem(
    ayah: Ayah,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Playing icon (hidden when not playing)
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Playing",
            modifier = Modifier
                .size(24.dp)
                .alpha(if (isPlaying) 1f else 0f),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Ayah number (Track number)
        Text(
            text = "${ayah.numberInSurah}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(32.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Ayah text (Track title)
        Text(
            text = ayah.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Duration placeholder
        Text(
            text = "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun TranslationSelectorDialog(
    currentTranslation: String,
    availableTranslations: List<String>,
    getTranslationName: (String) -> String,
    onTranslationSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Translation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableTranslations) { translationCode ->
                    val isSelected = translationCode == currentTranslation
                    Surface(
                        onClick = { onTranslationSelected(translationCode) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = if (isSelected) {
                            BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            )
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = getTranslationName(translationCode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
