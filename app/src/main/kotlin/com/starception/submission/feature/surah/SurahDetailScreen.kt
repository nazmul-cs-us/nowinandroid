package com.starception.submission.feature.surah

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.core.qurandatabase.Surah
import com.starception.submission.core.qurandatabase.QuranTranslationHelper
import com.starception.submission.core.qurandatabase.QuranTranslationRepository
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
                
                // Create repository - this will trigger database initialization
                val repository = try {
                    getRepository(translationCode)
                } catch (e: Exception) {
                    android.util.Log.e("SurahDetail", "❌ Failed to create repository for translation: $translationCode", e)
                    _uiState.value = SurahDetailUiState.Error("Failed to load translation database: ${e.message}")
                    return@launch
                }
                
                // Query for surah - this should work for all translation databases
                val surah = repository.getSurahByNumber(surahNumber)
                
                if (surah == null) {
                    android.util.Log.e("SurahDetail", "❌ Surah $surahNumber not found in translation: $translationCode")
                    _uiState.value = SurahDetailUiState.Error("Surah not found in $translationCode translation")
                    return@launch
                }
                
                android.util.Log.d("SurahDetail", "✅ Surah found: ${surah.nameEnglish} (ID: ${surah.id}, Number: ${surah.number})")
                
                // Get ayahs from the translation database using the surah ID
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
    val uiState by viewModel.uiState.collectAsState()
    val currentTranslation by viewModel.currentTranslation.collectAsState()
    var showTranslationDialog by remember { mutableStateOf(false) }
    val availableTranslations = remember { viewModel.getAvailableTranslations() }
    var currentSurahNumber by remember { mutableStateOf(surahNumber) }
    
    LaunchedEffect(currentSurahNumber) {
        viewModel.loadSurah(currentSurahNumber)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    when (val state = uiState) {
                        is SurahDetailUiState.Success -> Text(state.surah.nameEnglish)
                        else -> Text("Loading...")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTranslationDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Change Translation"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
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
                SurahContent(
                    surah = state.surah,
                    ayahs = state.ayahs,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
    
    // Translation selection dialog
    if (showTranslationDialog) {
        TranslationSelectorDialog(
            currentTranslation = currentTranslation,
            availableTranslations = availableTranslations,
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
fun SurahContent(
    surah: Surah,
    ayahs: List<Ayah>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Surah Header with Expressive Material 3 Design
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Gradient background with shadow
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    // Decorative gradient accent
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Surah number indicator
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(64.dp)
                                .border(3.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "${surah.number}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Arabic Name - Prominent
                        Text(
                            text = surah.nameArabic,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = MaterialTheme.typography.displayMedium.lineHeight * 1.1
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // English transliteration with decorative divider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Divider(
                                modifier = Modifier.width(24.dp),
                                thickness = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            Text(
                                text = surah.nameEnglish,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Divider(
                                modifier = Modifier.width(24.dp),
                                thickness = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Translation
                        Text(
                            text = surah.nameTranslation,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Info badges in row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Verse count badge
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp)),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "📖",
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${ayahs.size} Verses",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            // Revelation type badge
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp)),
                                color = if (surah.revelationType == "Meccan") {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    if (surah.revelationType == "Meccan") {
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (surah.revelationType == "Meccan") "🕌" else "🌙",
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = surah.revelationType,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (surah.revelationType == "Meccan") {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Bismillah (except for Surah 9 and 1)
        if (surah.number != 9 && surah.number != 1) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(20.dp)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Decorative Islamic pattern indicator
                            Box(
                                modifier = Modifier
                                    .height(1.dp)
                                    .width(40.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                                            )
                                        )
                                    )
                            )
                            
                            Text(
                                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = 48.sp
                            )
                            
                            // English transliteration below
                            Text(
                                text = "In the name of Allah, the Most Gracious, the Most Merciful",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Normal
                            )
                            
                            Box(
                                modifier = Modifier
                                    .height(1.dp)
                                    .width(40.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
        
        // Ayahs
        items(
            items = ayahs,
            key = { it.id }
        ) { ayah ->
            AyahCard(ayah = ayah)
        }
    }
}

@Composable
fun AyahCard(ayah: Ayah) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Verse number badge with modern styling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "•",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "${ayah.numberInSurah}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    // Sajda indicator if applicable (top right)
                    if (ayah.sajda) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(
                                text = "۩ Sajda",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Arabic text with beautiful typography
                Text(
                    text = ayah.text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        lineHeight = 40.sp
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Decorative bottom accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                                )
                            )
                        )
                )
            }
        }
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
                            androidx.compose.foundation.BorderStroke(
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

