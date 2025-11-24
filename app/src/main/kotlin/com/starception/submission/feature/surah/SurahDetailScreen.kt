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

    private val _selectedArabicFont = MutableStateFlow(
        prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
    )
    val selectedArabicFont: StateFlow<String> = _selectedArabicFont.asStateFlow()

    private val _arabicFontSize = MutableStateFlow(
        prefs.getFloat("arabic_font_size", 22f)
    )
    val arabicFontSize: StateFlow<Float> = _arabicFontSize.asStateFlow()

    private val _currentVolume = MutableStateFlow(
        prefs.getFloat("audio_volume", 0.7f)
    )
    val currentVolume: StateFlow<Float> = _currentVolume.asStateFlow()

    private val _currentAudioLanguage = MutableStateFlow(
        prefs.getString("audio_language", "ARABIC_ONLY") ?: "ARABIC_ONLY"
    )
    val currentAudioLanguage: StateFlow<String> = _currentAudioLanguage.asStateFlow()

    private val _showTranslation = MutableStateFlow(
        prefs.getBoolean("show_translation", true)
    )
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _textAlignment = MutableStateFlow(
        prefs.getString("text_alignment", "start") ?: "start"
    )
    val textAlignment: StateFlow<String> = _textAlignment.asStateFlow()

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

                // If non-Arabic translation is selected, load both Arabic and translation
                val ayahs = if (translationCode != "ar") {
                    android.util.Log.d("SurahDetail", "📖 Loading dual language: Arabic + $translationCode")

                    // Load Arabic ayahs
                    val arabicRepository = getRepository("ar")
                    val arabicAyahs = arabicRepository.getAyahsBySurahOnce(surah.id)

                    // Load translation ayahs
                    val translationAyahs = repository.getAyahsBySurahOnce(surah.id)

                    // Combine them - each Ayah will show both Arabic and translation
                    arabicAyahs.mapIndexed { index, arabicAyah ->
                        val translationText = translationAyahs.getOrNull(index)?.text ?: ""
                        // Create a combined ayah with both texts separated by newlines
                        arabicAyah.copy(
                            text = "${arabicAyah.text}\n\n$translationText"
                        )
                    }
                } else {
                    // Arabic only
                    repository.getAyahsBySurahOnce(surah.id)
                }

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

    fun changeArabicFont(fontName: String) {
        viewModelScope.launch {
            _selectedArabicFont.value = fontName
            prefs.edit().putString("arabic_font", fontName).apply()
        }
    }

    fun changeArabicFontSize(fontSize: Float) {
        viewModelScope.launch {
            _arabicFontSize.value = fontSize
            prefs.edit().putFloat("arabic_font_size", fontSize).apply()
        }
    }

    fun changeVolume(volume: Float) {
        viewModelScope.launch {
            _currentVolume.value = volume
            prefs.edit().putFloat("audio_volume", volume).apply()
        }
    }

    fun changeAudioLanguage(languageCode: String) {
        viewModelScope.launch {
            _currentAudioLanguage.value = languageCode
            prefs.edit().putString("audio_language", languageCode).apply()
        }
    }

    fun changeShowTranslation(show: Boolean) {
        viewModelScope.launch {
            _showTranslation.value = show
            prefs.edit().putBoolean("show_translation", show).apply()
        }
    }

    fun changeTextAlignment(alignment: String) {
        viewModelScope.launch {
            _textAlignment.value = alignment
            prefs.edit().putString("text_alignment", alignment).apply()
        }
    }

    fun getAvailableTranslations(): List<String> = translations

    fun getTranslationName(code: String): String = QuranTranslationHelper.getTranslationName(code)

    fun getAvailableArabicFonts(): List<String> = listOf(
        "pdms_saleem",
        "noor_e_hidayat",
        "thabit",
        "uthmani_script",
        "indopak_script"
    )

    fun getArabicFontDisplayName(font: String): String = when (font) {
        "pdms_saleem" -> "PDMS Saleem"
        "noor_e_hidayat" -> "Noor-e-Hidayat"
        "thabit" -> "Thabit"
        "uthmani_script" -> "Uthmani Script"
        "indopak_script" -> "IndoPak Script"
        else -> "PDMS Saleem"
    }
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
    
    // Match XML: CoordinatorLayout equivalent
        when (val state = uiState) {
            is SurahDetailUiState.Loading -> {
                Box(
                modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is SurahDetailUiState.Error -> {
                Box(
                modifier = Modifier.fillMaxSize(),
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
                },
                onVolumeChange = { volume ->
                    playerViewModel.setVolume(volume)
                },
                onBackClick = onBackClick,
                onTranslationClick = { showTranslationDialog = true },
                onBookmarkClick = { /* TODO */ }
            )
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onVolumeChange: ((Float) -> Unit)? = null,
    onBackClick: () -> Unit,
    onTranslationClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val albumImageHeight = screenWidth // 1:1 aspect ratio (XML: app:layout_constraintDimensionRatio="H,1:1")
    val containerHeight = 196.dp // XML: android:layout_height="196dp"
    
    // Match XML: CoordinatorLayout with scrollBehavior
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Match XML: AppBarLayout with CollapsingToolbarLayout
            LargeTopAppBar(
                title = { }, // Title handled in collapsing content
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBookmarkClick) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Bookmark",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onTranslationClick) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Translation",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        // Match XML: CoordinatorLayout structure - header content is separate from scrollable content
        Box(modifier = Modifier.fillMaxSize()) {
            // Match XML: RecyclerView (song_recycler_view) with appbar_scrolling_view_behavior
            // XML: android:paddingTop="8dp", app:layout_behavior="@string/appbar_scrolling_view_behavior"
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(
                    top = 8.dp + paddingValues.calculateTopPadding(),
                    bottom = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Spacer to push content below collapsing header
                item {
                    Spacer(modifier = Modifier.height(albumImageHeight + containerHeight))
                }
                
                // Match XML: Track list items (ayahs)
                items(
                    items = ayahs,
                    key = { it.id }
                ) { ayah ->
                    AyahTrackItem(
                        ayah = ayah,
                        isPlaying = false, // TODO: Track current playing ayah
                        onClick = { }
                    )
                }
            }
            
            // Match XML: ConstraintLayout content inside CollapsingToolbarLayout
            // This is the collapsing header content that overlays the scrollable content
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Match XML: ImageView (album_image) - 1:1 aspect ratio
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(albumImageHeight)
                            .background(
                            brush = Brush.verticalGradient(
                                    colors = listOf(
                                    Color(0xFF00BCD4),
                                    Color(0xFF7B1FA2),
                                    Color(0xFF9C27B0),
                                    Color(0xFFE91E63),
                                    Color(0xFFFF5722),
                                    Color(0xFFFF9800),
                                    Color(0xFFFF6F00)
                                )
                            )
                        )
                )

                // Match XML: MaterialCardView (album_info_container) OR ConstraintLayout (music_player_container)
                // Both 196dp height, same position (layout_constraintTop_toBottomOf="@id/album_image")
                Box(
                        modifier = Modifier
                            .fillMaxWidth()
                        .height(containerHeight)
                ) {
                    // Album Info Container (XML: album_info_container) - shown when player is hidden
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !showMusicPlayer,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
                    ) {
                        // Match XML: MaterialCardView with cardBackgroundColor="?attr/colorPrimarySurface"
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(0.dp) // XML: app:cardCornerRadius="0dp"
                        ) {
                            // Match XML: LinearLayout (album_details) with paddingStart="56dp", paddingEnd="16dp"
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 56.dp, end = 16.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Match XML: TextView (album_title) - textAppearanceHeadline3
                                Text(
                                    text = surah.nameEnglish,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Match XML: TextView (album_artist) - textAppearanceSubtitle1
                                Text(
                                    text = surah.nameArabic,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Music Player Container (XML: music_player_container) - shown when player is visible
                    // XML: android:background="@android:color/black", android:visibility="gone"
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showMusicPlayer,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
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
                            onClose = onToggleMusicPlayer,
                            onVolumeChange = onVolumeChange,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Left FAB - Hint/Info button
            FloatingActionButton(
                onClick = {
                    // TODO: Add hint/info functionality
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 24.dp, y = (albumImageHeight - 28.dp)), // Position at bottom of album image with offset from edge
                containerColor = Color(0xFFB0BEC5), // Match reference color
                contentColor = Color(0xFF263238)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Right FAB - Play/Pause button
            FloatingActionButton(
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
                    .align(Alignment.TopEnd)
                    .offset(x = (-24).dp, y = (albumImageHeight - 28.dp)), // Position at bottom of album image with offset from edge
                containerColor = Color(0xFFB0BEC5), // Match reference color
                contentColor = Color(0xFF263238)
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
                    modifier = Modifier.size(24.dp)
                        )
                    }
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
    onVolumeChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var volume by remember { mutableStateOf(0.35f) } // XML: android:value="3.5" out of 0-10, so 0.35f
    
    val progressValue = remember(currentPosition, duration) {
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0.1f // XML: android:progress="10"
    }
    
    // Match XML: ConstraintLayout (music_player_container) - 196dp height, black background
    // XML: android:background="@android:color/black"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp)
            .background(Color.Black)
    ) {
        // Match XML: LinearProgressIndicator (music_progress_indicator) at top
        // XML: app:indicatorSize="16dp", app:layout_constraintTop_toTopOf="parent"
        LinearProgressIndicator(
            progress = { progressValue },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            trackColor = Color.White.copy(alpha = 0.2f),
            color = MaterialTheme.colorScheme.primary
        )
        
        // Match XML: MaterialButton (rewind) - positioned below progress indicator, above volume slider
        // XML: app:layout_constraintBottom_toTopOf="@id/volume_slider"
        //      app:layout_constraintTop_toBottomOf="@id/music_progress_indicator"
        //      app:layout_constraintStart_toStartOf="parent"
        //      android:layout_marginStart="@dimen/cat_music_player_control_button_margin"
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = 20.dp) // Position between progress and volume
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(56.dp) // XML: @dimen/cat_music_player_button_size
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Match XML: MaterialButton (music_play_button) - center, below progress, above volume
        // XML: app:layout_constraintBottom_toTopOf="@id/volume_slider"
        //      app:layout_constraintEnd_toEndOf="parent"
        //      app:layout_constraintStart_toStartOf="parent"
        //      app:layout_constraintTop_toBottomOf="@id/music_progress_indicator"
                        Box(
                            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 20.dp)
        ) {
            FloatingActionButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp), // XML: @dimen/cat_music_player_button_size
                containerColor = Color.White,
                contentColor = Color.Black
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Match XML: MaterialButton (fast_forward) - end, below progress, above volume
        // XML: app:layout_constraintBottom_toTopOf="@id/volume_slider"
        //      app:layout_constraintEnd_toEndOf="parent"
        //      app:layout_constraintTop_toBottomOf="@id/music_progress_indicator"
        //      android:layout_marginEnd="@dimen/cat_music_player_control_button_margin"
        Box(
                            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = 20.dp)
        ) {
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Match XML: Volume controls at bottom - ConstraintLayout positioning
        // XML: volume_down_button, volume_slider, volume_up_button
        // Volume slider: app:layout_constraintWidth_percent="0.55", app:layout_constraintWidth_min="200dp"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Match XML: MaterialButton (volume_down_button)
            // XML: app:layout_constraintEnd_toStartOf="@id/volume_slider"
            //      app:layout_constraintBottom_toBottomOf="@id/volume_slider"
            //      app:layout_constraintTop_toTopOf="@id/volume_slider"
            //      android:layout_marginStart="@dimen/cat_music_player_volume_button_margin"
            IconButton(
                onClick = {
                    volume = (volume - 0.1f).coerceAtLeast(0f)
                    onVolumeChange?.invoke(volume)
                },
                modifier = Modifier.size(48.dp) // XML: @dimen/cat_music_player_button_size
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = "Volume Down",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Match XML: Slider (volume_slider) - 55% width, min 200dp
            // XML: app:layout_constraintWidth_percent="0.55"
            //      app:layout_constraintWidth_min="200dp"
            //      app:thumbRadius="8dp", app:trackHeight="3dp"
            //      android:value="3.5", android:valueFrom="0", android:valueTo="10"
            Slider(
                value = volume * 10f, // Convert 0-1 to 0-10 range
                onValueChange = { newVolume ->
                    volume = (newVolume / 10f).coerceIn(0f, 1f)
                    onVolumeChange?.invoke(volume)
                },
                    modifier = Modifier
                    .widthIn(min = 200.dp)
                    .weight(0.55f) // Match XML: 55% width
                    .padding(horizontal = 8.dp),
                valueRange = 0f..10f, // XML: android:valueTo="10"
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            
            // Match XML: MaterialButton (volume_up_button)
            // XML: app:layout_constraintStart_toEndOf="@id/volume_slider"
            //      app:layout_constraintBottom_toBottomOf="@id/volume_slider"
            //      app:layout_constraintTop_toTopOf="@id/volume_slider"
            //      android:layout_marginEnd="@dimen/cat_music_player_volume_button_margin"
            IconButton(
                onClick = {
                    volume = (volume + 0.1f).coerceAtMost(1f)
                    onVolumeChange?.invoke(volume)
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volume Up",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
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
        verticalAlignment = Alignment.Top // Align to top to allow text wrapping
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
        
        // Ayah text - allow full text to display without truncation
                Text(
                    text = ayah.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = QuranFonts.Amiri,
                fontSize = 20.sp,
                lineHeight = 32.sp
            ),
                    color = MaterialTheme.colorScheme.onSurface,
            // Removed maxLines restriction - allow text to wrap naturally
            modifier = Modifier.weight(1f)
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
        icon = {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Translation",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Select Translation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(availableTranslations) { translationCode ->
                    val isSelected = translationCode == currentTranslation
                    Surface(
                        onClick = {
                            onTranslationSelected(translationCode)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = if (isSelected) {
                            BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            )
                        } else {
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        },
                        shadowElevation = if (isSelected) 4.dp else 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Language icon indicator
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            Text(
                                text = getTranslationName(translationCode),
                                style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                // Invisible placeholder to maintain alignment
                                Spacer(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
