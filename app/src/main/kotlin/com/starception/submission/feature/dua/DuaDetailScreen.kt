package com.starception.submission.feature.dua

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.feature.surah.QuranFonts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.starception.submission.core.qurandatabase.QuranTranslationHelper
import com.starception.submission.core.qurandatabase.QuranTranslationRepository
import com.starception.submission.feature.surah.tajweed.TajweedAnnotation
import com.starception.submission.feature.surah.tajweed.TajweedParser
import com.starception.submission.feature.surah.tajweed.TajweedTextApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Data class representing a complete Dua
 */
data class DuaItem(
    val id: String,
    val title: String,
    val arabicText: String,
    val transliteration: String,
    val translation: String,
    val explanation: String,
    val quranReference: String?,
    val duaNumber: Int,
    val surahNumber: Int = 0,  // Extracted from quranReference for database lookup
    val ayahNumber: Int = 0,   // Extracted from quranReference for database lookup
    val surahName: String = "" // Surah name fetched from database (e.g., "Al-Baqarah")
)

/**
 * Data class representing parsed Dua content
 */
data class ParsedDuaContent(
    val arabicText: String,
    val transliteration: String,
    val translation: String,
    val explanation: String,
    val quranReference: String?
)

/**
 * ViewModel for managing Dua display state and font preferences
 */
class DuaDetailViewModel(private val context: Context) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)

    private val _selectedArabicFont = MutableStateFlow(
        prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
    )
    val selectedArabicFont: StateFlow<String> = _selectedArabicFont.asStateFlow()

    // Translation selection - same as Quran player
    private val _selectedTranslation = MutableStateFlow(
        prefs.getString("quran_translation", "en") ?: "en"
    )
    val selectedTranslation: StateFlow<String> = _selectedTranslation.asStateFlow()

    // Tajweed setting - synced with Surah page
    private val _showTajweed = MutableStateFlow(
        prefs.getBoolean("show_tajweed", false)
    )
    val showTajweed: StateFlow<Boolean> = _showTajweed.asStateFlow()

    // Tajweed data cache
    private var tajweedData: Map<String, List<TajweedAnnotation>>? = null

    private val _allDuas = MutableStateFlow<List<DuaItem>>(emptyList())
    val allDuas: StateFlow<List<DuaItem>> = _allDuas.asStateFlow()

    // Cache for base duas (without dynamic translations)
    private var baseDuas: List<DuaItem> = emptyList()

    init {
        viewModelScope.launch {
            prefs.registerOnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "arabic_font" -> {
                        _selectedArabicFont.value = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
                    }
                    "quran_translation" -> {
                        val newTranslation = prefs.getString("quran_translation", "en") ?: "en"
                        _selectedTranslation.value = newTranslation
                        // Reload duas with new translation
                        reloadDuasWithTranslation(newTranslation)
                    }
                    "show_tajweed" -> {
                        _showTajweed.value = prefs.getBoolean("show_tajweed", false)
                    }
                }
            }
            // Load Tajweed data
            loadTajweedData()
        }
        loadAllDuas(context)
    }

    /**
     * Load Tajweed data from assets
     */
    private suspend fun loadTajweedData() {
        withContext(Dispatchers.IO) {
            try {
                tajweedData = TajweedParser.parse(context)
                android.util.Log.d("DuaDetailViewModel", "📗 Loaded Tajweed data with ${tajweedData?.size ?: 0} entries")
            } catch (e: Exception) {
                android.util.Log.e("DuaDetailViewModel", "❌ Error loading Tajweed data", e)
            }
        }
    }

    /**
     * Get Tajweed annotations for a specific ayah
     */
    fun getTajweedAnnotations(surahNumber: Int, ayahNumber: Int): List<TajweedAnnotation> {
        val key = "$surahNumber:$ayahNumber"
        return tajweedData?.get(key) ?: emptyList()
    }

    /**
     * Get translation name for display
     */
    fun getTranslationName(code: String): String {
        return QuranTranslationHelper.getTranslationName(code)
    }

    /**
     * Get list of available translations
     */
    fun getAvailableTranslations(): List<String> {
        return QuranTranslationHelper.getAvailableTranslations()
    }

    /**
     * Change the current translation
     */
    fun changeTranslation(code: String) {
        prefs.edit().putString("quran_translation", code).apply()
        _selectedTranslation.value = code
        reloadDuasWithTranslation(code)
    }

    /**
     * Get list of available Arabic fonts
     */
    fun getAvailableArabicFonts(): List<String> {
        return listOf(
            "pdms_saleem",
            "noor_e_hidayat",
            "thabit",
            "uthmani_script",
            "indopak_script",
            "amiri",
            "scheherazade"
        )
    }

    /**
     * Get display name for a font
     */
    fun getArabicFontDisplayName(fontName: String): String {
        return when (fontName) {
            "pdms_saleem" -> "PDMS Saleem"
            "noor_e_hidayat" -> "Noor e Hidayat"
            "thabit" -> "Thabit"
            "uthmani_script" -> "Uthmani Script"
            "indopak_script" -> "IndoPak Script"
            "amiri" -> "Amiri"
            "scheherazade" -> "Scheherazade"
            else -> fontName
        }
    }

    /**
     * Change the Arabic font
     */
    fun changeArabicFont(fontName: String) {
        prefs.edit().putString("arabic_font", fontName).apply()
        _selectedArabicFont.value = fontName
    }

    // Bookmark management for Duas
    private val bookmarkedDuas: MutableSet<String> = mutableSetOf()

    init {
        // Load bookmarked duas from SharedPreferences
        val savedBookmarks = prefs.getStringSet("bookmarked_duas", emptySet()) ?: emptySet()
        bookmarkedDuas.addAll(savedBookmarks)
    }

    /**
     * Check if a dua is bookmarked
     */
    fun isDuaBookmarked(duaId: String): Boolean {
        return duaId in bookmarkedDuas
    }

    /**
     * Toggle Tajweed display
     */
    fun toggleTajweed() {
        val newValue = !_showTajweed.value
        prefs.edit().putBoolean("show_tajweed", newValue).apply()
        _showTajweed.value = newValue
    }

    /**
     * Toggle bookmark state for a dua
     */
    fun toggleDuaBookmark(duaId: String): Boolean {
        val newState = if (duaId in bookmarkedDuas) {
            bookmarkedDuas.remove(duaId)
            false
        } else {
            bookmarkedDuas.add(duaId)
            true
        }
        // Persist to SharedPreferences
        prefs.edit().putStringSet("bookmarked_duas", bookmarkedDuas.toSet()).apply()
        return newState
    }

    /**
     * Parse Quran reference (e.g., "2:127") to surah and ayah numbers
     */
    private fun parseQuranReference(reference: String?): Pair<Int, Int> {
        if (reference.isNullOrBlank()) return Pair(0, 0)
        val parts = reference.split(":")
        if (parts.size != 2) return Pair(0, 0)
        val surah = parts[0].toIntOrNull() ?: 0
        val ayah = parts[1].toIntOrNull() ?: 0
        return Pair(surah, ayah)
    }

    /**
     * Reload duas with a new translation from the database
     */
    private fun reloadDuasWithTranslation(translationCode: String) {
        viewModelScope.launch {
            if (baseDuas.isEmpty()) return@launch

            val updatedDuas = baseDuas.map { dua ->
                if (dua.surahNumber > 0 && dua.ayahNumber > 0) {
                    val translatedText = fetchTranslation(translationCode, dua.surahNumber, dua.ayahNumber)
                    if (translatedText != null) {
                        dua.copy(translation = translatedText)
                    } else {
                        dua
                    }
                } else {
                    dua
                }
            }
            _allDuas.value = updatedDuas
        }
    }

    /**
     * Fetch translation for a specific ayah from the database
     */
    private suspend fun fetchTranslation(translationCode: String, surahNumber: Int, ayahNumber: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val repository = QuranTranslationRepository(context, translationCode)
                val ayahs = repository.getAyahsBySurahNumber(surahNumber)
                val ayah = ayahs.find { it.numberInSurah == ayahNumber }
                ayah?.text
            } catch (e: Exception) {
                android.util.Log.e("DuaDetailViewModel", "Failed to fetch translation for $surahNumber:$ayahNumber", e)
                null
            }
        }
    }

    /**
     * Fetch Surah name from the database
     */
    private suspend fun fetchSurahName(surahNumber: Int): String {
        return withContext(Dispatchers.IO) {
            try {
                val repository = QuranTranslationRepository(context, "ar")
                val surah = repository.getSurahByNumber(surahNumber)
                surah?.nameEnglish ?: ""
            } catch (e: Exception) {
                android.util.Log.e("DuaDetailViewModel", "Failed to fetch surah name for $surahNumber", e)
                ""
            }
        }
    }

    private fun loadAllDuas(context: Context) {
        viewModelScope.launch {
            try {
                // Phase 1: Parse JSON on IO thread and emit immediately (fast)
                val sortedDuas = withContext(Dispatchers.IO) {
                    val inputStream = context.assets.open("news.json")
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonString = reader.readText()
                    reader.close()

                    val jsonArray = JSONArray(jsonString)
                    val duas = mutableListOf<DuaItem>()

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val type = item.optString("type", "")

                        if (type.contains("Dua", ignoreCase = true)) {
                            val content = item.optString("content", "")
                            val quranRef = item.optString("quranReference", "")
                            val parsed = parseDuaContent(content, quranRef.ifEmpty { null })
                            val title = item.optString("title", "")
                            val duaNumber = Regex("#(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                                ?: Regex("Dua (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                                ?: (duas.size + 1)

                            // Parse the Quran reference for database lookup
                            val (surahNumber, ayahNumber) = parseQuranReference(quranRef.ifEmpty { null })

                            duas.add(
                                DuaItem(
                                    id = item.optString("id", ""),
                                    title = title,
                                    arabicText = parsed.arabicText,
                                    transliteration = parsed.transliteration,
                                    translation = parsed.translation,
                                    explanation = parsed.explanation,
                                    quranReference = quranRef.ifEmpty { null },
                                    duaNumber = duaNumber,
                                    surahNumber = surahNumber,
                                    ayahNumber = ayahNumber
                                )
                            )
                        }
                    }

                    duas.sortedBy { it.duaNumber }
                }

                // Emit immediately with basic data - UI can show content now
                baseDuas = sortedDuas
                _allDuas.value = sortedDuas

                // Phase 2: Load translations and Surah names in background (slow but non-blocking)
                val translationCode = _selectedTranslation.value
                val duasWithDetails = withContext(Dispatchers.IO) {
                    sortedDuas.map { dua ->
                        if (dua.surahNumber > 0 && dua.ayahNumber > 0) {
                            val translatedText = fetchTranslation(translationCode, dua.surahNumber, dua.ayahNumber)
                            val surahName = fetchSurahName(dua.surahNumber)
                            dua.copy(
                                translation = translatedText ?: dua.translation,
                                surahName = surahName
                            )
                        } else {
                            dua
                        }
                    }
                }

                // Update with full details
                baseDuas = duasWithDetails
                _allDuas.value = duasWithDetails
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/**
 * Helper function to get the appropriate FontFamily based on user selection
 */
fun getArabicFontFamilyForDua(selectedFont: String): FontFamily {
    return when (selectedFont) {
        "pdms_saleem" -> QuranFonts.PDMSSaleem
        "noor_e_hidayat" -> QuranFonts.NoorEHidayat
        "thabit" -> QuranFonts.Thabit
        "uthmani_script" -> QuranFonts.UthmanicScript
        "indopak_script" -> QuranFonts.IndoPakScript
        "amiri" -> QuranFonts.Amiri
        "scheherazade" -> QuranFonts.Scheherazade
        else -> QuranFonts.PDMSSaleem
    }
}

/**
 * Parses the raw dua content into structured components
 */
fun parseDuaContent(content: String, quranReference: String? = null): ParsedDuaContent {
    val parts = content.split("\n\n")

    var arabicText = ""
    var transliteration = ""
    var translation = ""
    var explanation = ""

    for (part in parts) {
        val trimmedPart = part.trim()
        when {
            trimmedPart.startsWith("Transliteration:") -> {
                transliteration = trimmedPart.removePrefix("Transliteration:").trim()
            }
            trimmedPart.startsWith("Translation:") -> {
                translation = trimmedPart.removePrefix("Translation:").trim()
            }
            trimmedPart.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' } && arabicText.isEmpty() -> {
                arabicText = trimmedPart
            }
            transliteration.isNotEmpty() && translation.isNotEmpty() -> {
                if (explanation.isEmpty()) {
                    explanation = trimmedPart
                } else {
                    explanation += "\n\n$trimmedPart"
                }
            }
        }
    }

    return ParsedDuaContent(
        arabicText = arabicText,
        transliteration = transliteration,
        translation = translation,
        explanation = explanation,
        quranReference = quranReference
    )
}

// Gradient colors for dua header - Material 3 Expressive
private val DuaGradientColors = listOf(
    Color(0xFF0D47A1),  // Deep blue
    Color(0xFF1565C0),  // Blue
    Color(0xFF1976D2),  // Medium blue
    Color(0xFF00695C),  // Teal
    Color(0xFF004D40)   // Dark teal
)

/**
 * Main Dua Detail Screen with HorizontalPager for swipe navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DuaDetailScreen(
    title: String,
    content: String,
    quranReference: String? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    allDuas: List<DuaItem> = emptyList(),
    currentDuaIndex: Int = 0,
    onNavigateToDua: ((Int) -> Unit)? = null,
    onNavigateToSurah: ((surahNumber: Int, ayahNumber: Int) -> Unit)? = null,
    initialNewsResourceId: String = "",
    isNiaBookmarked: (newsResourceId: String) -> Boolean = { false },
    onToggleNiaBookmark: (newsResourceId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { DuaDetailViewModel(context) }
    val selectedFont by viewModel.selectedArabicFont.collectAsState()
    val selectedTranslation by viewModel.selectedTranslation.collectAsState()
    val showTajweed by viewModel.showTajweed.collectAsState()
    val arabicFontFamily = getArabicFontFamilyForDua(selectedFont)
    val scope = rememberCoroutineScope()

    // Dialog states
    var showTranslationDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    // Bookmark state - will be updated when page changes
    var isBookmarked by remember { mutableStateOf(false) }

    // Available options
    val availableTranslations = remember { viewModel.getAvailableTranslations() }
    val availableFonts = remember { viewModel.getAvailableArabicFonts() }

    // Get translation display name and short code
    val translationDisplayName = remember(selectedTranslation) {
        viewModel.getTranslationName(selectedTranslation)
    }
    val translationCode = when (selectedTranslation) {
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

    // Load all duas from ViewModel
    val loadedDuas by viewModel.allDuas.collectAsState()
    val duasList = if (loadedDuas.isNotEmpty()) loadedDuas else allDuas

    // Find initial page index based on dua number from title
    // Handle both formats: "Dua #2" and "Quranic Dua 2: Make us Muslims"
    val initialDuaNumber = remember(title) {
        Regex("#(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("Dua (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: 1
    }

    val initialPageIndex = remember(duasList, initialDuaNumber) {
        if (duasList.isNotEmpty()) {
            duasList.indexOfFirst { it.duaNumber == initialDuaNumber }.takeIf { it >= 0 } ?: 0
        } else {
            initialDuaNumber - 1
        }
    }

    // Calculate initial page index - 0-based (dua #2 should be at index 1)
    val targetPageIndex = (initialDuaNumber - 1).coerceAtLeast(0)

    // Pager state
    val pagerState = rememberPagerState(
        initialPage = targetPageIndex,
        pageCount = { if (duasList.isNotEmpty()) duasList.size else 40 }
    )

    // Force scroll to correct page on initial composition
    LaunchedEffect(Unit) {
        // Scroll immediately to the target page
        if (pagerState.currentPage != targetPageIndex) {
            pagerState.scrollToPage(targetPageIndex)
        }
    }

    // Also scroll when duas load (in case index needs adjustment based on duaNumber field)
    LaunchedEffect(duasList) {
        if (duasList.isNotEmpty()) {
            val targetIndex = duasList.indexOfFirst { it.duaNumber == initialDuaNumber }.takeIf { it >= 0 }
                ?: targetPageIndex.coerceIn(0, duasList.size - 1)
            if (pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }

    val totalDuas = if (duasList.isNotEmpty()) duasList.size else 40
    val currentPage = pagerState.currentPage
    // Enable circular navigation - always allow navigation when there are multiple duas
    val hasPrevious = totalDuas > 1
    val hasNext = totalDuas > 1

    // Get current NiA news resource ID for bookmark tracking
    // Dua 1 = news resource ID "128", Dua 2 = "129", etc.
    val currentNewsResourceId = remember(currentPage) {
        (128 + currentPage).toString()  // currentPage is 0-indexed, dua 1 is at page 0
    }

    // Update bookmark state when page changes using NiA's bookmark system
    LaunchedEffect(currentPage, currentNewsResourceId) {
        isBookmarked = isNiaBookmarked(currentNewsResourceId)
    }

    // Also update when the screen first loads with the initial ID
    LaunchedEffect(initialNewsResourceId) {
        if (initialNewsResourceId.isNotEmpty()) {
            isBookmarked = isNiaBookmarked(initialNewsResourceId)
        }
    }

    // Font display code for toolbar
    val fontDisplay = when (selectedFont) {
        "pdms_saleem" -> "PS"
        "noor_e_hidayat" -> "NH"
        "thabit" -> "TH"
        "uthmani_script" -> "US"
        "indopak_script" -> "IP"
        else -> "F"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Show shimmer loading state while data loads
            if (duasList.isEmpty()) {
                DuaShimmerLoadingContent(
                    onBackClick = onBackClick
                )
            }

            // Pager for duas - swipeable, with scrollable header inside each page
            if (duasList.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val dua = duasList[page]

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Track scroll state for collapsing toolbar effect
                        val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

                        // Force scroll to top immediately when this page is displayed
                        LaunchedEffect(page) {
                            lazyListState.scrollToItem(0, 0)
                        }

                        // Calculate if header has scrolled past threshold (when Surah reference should appear in toolbar)
                        val showTitleInToolbar = remember {
                            androidx.compose.runtime.derivedStateOf {
                                lazyListState.firstVisibleItemIndex > 0 ||
                                (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset > 150)
                            }
                        }

                        // Each page has its own LazyColumn with header + content
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 72.dp) // Space for bottom navigation
                        ) {
                            // Gradient Header content - scrollable, extends behind toolbar
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(284.dp) // 64dp toolbar + 220dp content
                                        .background(
                                            brush = Brush.linearGradient(colors = DuaGradientColors)
                                        )
                                ) {
                                    // Header content only (positioned below toolbar area)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp)
                                            .padding(top = 80.dp, bottom = 12.dp), // 80dp = 64dp toolbar + 16dp spacing
                                        verticalArrangement = Arrangement.SpaceEvenly,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Icon and Title group
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Dua icon - glassmorphism style
                                            Box(contentAlignment = Alignment.Center) {
                                                Surface(
                                                    modifier = Modifier.size(52.dp),
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = Color.White.copy(alpha = 0.08f)
                                                ) {}
                                                Surface(
                                                    modifier = Modifier.size(44.dp),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color.White.copy(alpha = 0.18f)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoStories,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = "Quranic Dua",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                        }

                                        // Dua theme/title
                                        val duaTheme = dua.title
                                            .replace(Regex("Quranic Dua \\d+:\\s*"), "")
                                            .replace(Regex("Dua #\\d+:\\s*"), "")
                                            .replace(Regex("Dua \\d+:\\s*"), "")
                                            .replace(Regex("\\s*\\(\\d+:\\d+\\)\\s*$"), "")
                                            .trim()

                                        if (duaTheme.isNotEmpty() && duaTheme != dua.title) {
                                            Text(
                                                text = duaTheme,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                        }

                                        // Space for floating Surah pill overlay
                                        if (dua.surahNumber > 0 && dua.ayahNumber > 0) {
                                            Spacer(modifier = Modifier.height(50.dp))
                                        }
                                    }
                                }
                            }

                        // Arabic Text Card
                        if (dua.arabicText.isNotEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        ),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val tajweedAnnotations = if (showTajweed && dua.surahNumber > 0 && dua.ayahNumber > 0) {
                                            viewModel.getTajweedAnnotations(dua.surahNumber, dua.ayahNumber)
                                        } else {
                                            emptyList()
                                        }

                                        if (showTajweed && tajweedAnnotations.isNotEmpty()) {
                                            val annotatedText = TajweedTextApplier.applyWithOverlap(
                                                text = dua.arabicText,
                                                annotations = tajweedAnnotations,
                                                defaultStyle = androidx.compose.ui.text.SpanStyle(
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                            Text(
                                                text = annotatedText,
                                                fontFamily = arabicFontFamily,
                                                fontSize = 34.sp,
                                                lineHeight = 56.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            Text(
                                                text = dua.arabicText,
                                                fontFamily = arabicFontFamily,
                                                fontSize = 34.sp,
                                                lineHeight = 56.sp,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Transliteration
                        if (dua.transliteration.isNotEmpty()) {
                            item {
                                DuaSectionCard(
                                    icon = Icons.Outlined.RecordVoiceOver,
                                    title = "Transliteration",
                                    iconTint = MaterialTheme.colorScheme.secondary,
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = dua.transliteration,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 16.sp,
                                            lineHeight = 26.sp,
                                            fontStyle = FontStyle.Italic
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // Translation
                        if (dua.translation.isNotEmpty()) {
                            item {
                                DuaSectionCard(
                                    icon = Icons.Outlined.Translate,
                                    title = "Translation",
                                    iconTint = MaterialTheme.colorScheme.tertiary,
                                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = dua.translation,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 15.sp,
                                            lineHeight = 24.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        // Explanation
                        if (dua.explanation.isNotEmpty()) {
                            item {
                                DuaSectionCard(
                                    icon = Icons.Outlined.Lightbulb,
                                    title = "Explanation",
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = dua.explanation,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                        // Fixed toolbar at top - transparent with animated Surah reference
                        val toolbarAlpha by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (showTitleInToolbar.value) 1f else 0f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                            label = "toolbarAlpha"
                        )

                        // Get Surah display info for toolbar
                        val surahDisplayName = if (dua.surahName.isNotEmpty()) {
                            dua.surahName
                        } else if (dua.surahNumber > 0) {
                            "Surah ${dua.surahNumber}"
                        } else {
                            ""
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp) // Standard toolbar height
                                .align(Alignment.TopCenter)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            DuaGradientColors[0],
                                            DuaGradientColors[1]
                                        )
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back button
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                // Spacer for center (Surah pill is a floating overlay that pins next to back button)
                                Spacer(modifier = Modifier.weight(1f))

                                // Right side toolbar buttons - matching SurahDetailScreen spacing
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Translation button
                                    Surface(
                                        onClick = { showTranslationDialog = true },
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        contentColor = Color.White
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = "Translation",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = translationCode,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // Font selection button
                                    Surface(
                                        onClick = { showFontDialog = true },
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        contentColor = Color.White
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TextFormat,
                                                contentDescription = "Font selection",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = fontDisplay,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(4.dp))

                                    // Tajweed toggle button
                                    IconButton(onClick = { viewModel.toggleTajweed() }) {
                                        Icon(
                                            imageVector = if (showTajweed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                                            contentDescription = if (showTajweed) "Disable Tajweed colors" else "Enable Tajweed colors",
                                            tint = Color.White
                                        )
                                    }

                                    // Bookmark button
                                    IconButton(
                                        onClick = {
                                            onToggleNiaBookmark(currentNewsResourceId)
                                            isBookmarked = !isBookmarked
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Floating Surah pill that animates from header to toolbar (pins next to back arrow)
                        if (dua.surahNumber > 0 && dua.ayahNumber > 0 && surahDisplayName.isNotEmpty()) {
                            val density = androidx.compose.ui.platform.LocalDensity.current

                            // Header position (where pill starts - centered) and toolbar position (next to back button)
                            val headerY = with(density) { 230.dp.toPx() }
                            val toolbarY = with(density) { 10.dp.toPx() } // Vertically centered in 64dp toolbar

                            // Get current scroll offset
                            val scrollOffset = if (lazyListState.firstVisibleItemIndex == 0) {
                                lazyListState.firstVisibleItemScrollOffset.toFloat()
                            } else {
                                headerY // Max scroll - pill should be at toolbar position
                            }

                            // Calculate pill Y position: starts at headerY, moves up with scroll, stops at toolbarY
                            val pillY = (headerY - scrollOffset).coerceAtLeast(toolbarY)

                            // Progress: 0 = header position, 1 = toolbar position
                            val progress = ((headerY - pillY) / (headerY - toolbarY)).coerceIn(0f, 1f)

                            // X offset: at progress=0 (header) = 0 (centered), at progress=1 (toolbar) = move left
                            // Move left by half the screen width minus back button position to align next to back
                            val xOffset = with(density) { (-100.dp * progress).toPx() }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(
                                        x = with(density) { xOffset.toDp() },
                                        y = with(density) { pillY.toDp() }
                                    ),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Surface(
                                    onClick = { onNavigateToSurah?.invoke(dua.surahNumber, dua.ayahNumber) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = surahDisplayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Ayah ${dua.ayahNumber}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 10.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Go to Surah",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback to single dua from navigation params
                val parsedContent = remember(content, quranReference) {
                    parseDuaContent(content, quranReference)
                }
                SingleDuaContent(
                    parsedContent = parsedContent,
                    arabicFontFamily = arabicFontFamily,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom navigation bar - Enhanced Material 3 Design
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 12.dp
            ) {
                Column {
                    // Progress indicator at top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (currentPage + 1).toFloat() / totalDuas.toFloat())
                                .height(3.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button - Enhanced with circular navigation
                        FilledIconButton(
                            onClick = {
                                if (hasPrevious) {
                                    scope.launch {
                                        // Wrap to last page when at first page
                                        val targetPage = if (currentPage == 0) totalDuas - 1 else currentPage - 1
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                            },
                            enabled = hasPrevious,
                            modifier = Modifier.size(50.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Dua",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Center indicator - Enhanced with badge style
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${currentPage + 1}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = " / $totalDuas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Next button - Enhanced with circular navigation
                        FilledIconButton(
                            onClick = {
                                if (hasNext) {
                                    scope.launch {
                                        // Wrap to first page when at last page
                                        val targetPage = if (currentPage == totalDuas - 1) 0 else currentPage + 1
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                            },
                            enabled = hasNext,
                            modifier = Modifier.size(50.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Dua",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Translation selection dialog
    if (showTranslationDialog) {
        DuaTranslationSelectorDialog(
            currentTranslation = selectedTranslation,
            availableTranslations = availableTranslations,
            getTranslationName = { viewModel.getTranslationName(it) },
            onTranslationSelected = { translationCode ->
                viewModel.changeTranslation(translationCode)
                showTranslationDialog = false
            },
            onDismiss = { showTranslationDialog = false }
        )
    }

    // Font selection dialog
    if (showFontDialog) {
        DuaFontSelectorDialog(
            currentFont = selectedFont,
            availableFonts = availableFonts,
            getFontName = { viewModel.getArabicFontDisplayName(it) },
            onFontSelected = { fontName ->
                viewModel.changeArabicFont(fontName)
                showFontDialog = false
            },
            onDismiss = { showFontDialog = false }
        )
    }
}

/**
 * Content for a single dua page in the pager
 */
@Composable
private fun DuaPageContent(
    dua: DuaItem,
    arabicFontFamily: FontFamily,
    modifier: Modifier = Modifier,
    onNavigateToSurah: ((surahNumber: Int, ayahNumber: Int) -> Unit)? = null,
    showTajweed: Boolean = false,
    getTajweedAnnotations: ((Int, Int) -> List<TajweedAnnotation>)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Arabic Text Card - Enhanced with shadow and better styling
        if (dua.arabicText.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Quran icon badge at top
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }

                    // Get Tajweed annotations if enabled and dua has Quran reference
                    val tajweedAnnotations = if (showTajweed && dua.surahNumber > 0 && dua.ayahNumber > 0) {
                        getTajweedAnnotations?.invoke(dua.surahNumber, dua.ayahNumber) ?: emptyList()
                    } else {
                        emptyList()
                    }

                    // Apply Tajweed coloring if enabled and annotations are available
                    if (showTajweed && tajweedAnnotations.isNotEmpty()) {
                        val annotatedText = TajweedTextApplier.applyWithOverlap(
                            text = dua.arabicText,
                            annotations = tajweedAnnotations,
                            defaultStyle = androidx.compose.ui.text.SpanStyle(
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        Text(
                            text = annotatedText,
                            fontFamily = arabicFontFamily,
                            fontSize = 34.sp,
                            lineHeight = 56.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = dua.arabicText,
                            fontFamily = arabicFontFamily,
                            fontSize = 34.sp,
                            lineHeight = 56.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Transliteration - Enhanced with icon
        if (dua.transliteration.isNotEmpty()) {
            DuaSectionCard(
                icon = Icons.Outlined.RecordVoiceOver,
                title = "Transliteration",
                iconTint = MaterialTheme.colorScheme.secondary,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = dua.transliteration,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Translation - Enhanced with icon
        if (dua.translation.isNotEmpty()) {
            DuaSectionCard(
                icon = Icons.Outlined.Translate,
                title = "Translation",
                iconTint = MaterialTheme.colorScheme.tertiary,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = dua.translation,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // Explanation - Enhanced with icon
        if (dua.explanation.isNotEmpty()) {
            DuaSectionCard(
                icon = Icons.Outlined.Lightbulb,
                title = "Explanation",
                iconTint = MaterialTheme.colorScheme.primary,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = dua.explanation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Reusable section card component with icon header
 */
@Composable
private fun DuaSectionCard(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
            // Content
            content()
        }
    }
}

/**
 * Fallback single dua content when pager data is not available
 */
@Composable
private fun SingleDuaContent(
    parsedContent: ParsedDuaContent,
    arabicFontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Arabic Text Card
        if (parsedContent.arabicText.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = parsedContent.arabicText,
                        fontFamily = arabicFontFamily,
                        fontSize = 36.sp,
                        lineHeight = 58.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Transliteration
        if (parsedContent.transliteration.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Transliteration",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = parsedContent.transliteration,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Translation
        if (parsedContent.translation.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = parsedContent.translation,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Explanation
        if (parsedContent.explanation.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Explanation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = parsedContent.explanation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Translation selector dialog for Dua screen - matches SurahDetailScreen style
 */
@Composable
fun DuaTranslationSelectorDialog(
    currentTranslation: String,
    availableTranslations: List<String>,
    getTranslationName: (String) -> String,
    onTranslationSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Filter out any translations with empty or blank display names
    val validTranslations = remember(availableTranslations) {
        availableTranslations.filter { code ->
            getTranslationName(code).isNotBlank()
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
                items(validTranslations) { translationCode ->
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
                            text = getTranslationName(translationCode),
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

/**
 * Font selector dialog for Dua screen - matches SurahDetailScreen style
 */
@Composable
fun DuaFontSelectorDialog(
    currentFont: String,
    availableFonts: List<String>,
    getFontName: (String) -> String,
    onFontSelected: (String) -> Unit,
    onDismiss: () -> Unit
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
                items(availableFonts) { fontName ->
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
                            text = getFontName(fontName),
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

/**
 * Shimmer effect brush for loading placeholders
 */
@Composable
private fun shimmerBrush(
    targetValue: Float = 1000f,
    showShimmer: Boolean = true
): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_translate"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = androidx.compose.ui.geometry.Offset(translateAnimation.value - 200f, 0f),
            end = androidx.compose.ui.geometry.Offset(translateAnimation.value, 0f)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent)
        )
    }
}

/**
 * Shimmer placeholder box
 */
@Composable
private fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .background(brush = brush, shape = shape)
    )
}

/**
 * Shimmer loading content that matches the actual Dua layout
 */
@Composable
private fun DuaShimmerLoadingContent(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Gradient header - shown immediately with shimmer placeholders
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(284.dp)
                    .background(
                        brush = Brush.linearGradient(colors = DuaGradientColors)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 80.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Icon - glassmorphism style (actual content, not shimmer)
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.08f)
                            ) {}
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.18f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Quranic Dua",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Shimmer placeholder for dua theme title
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(24.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )

                    // Shimmer placeholder for Surah reference pill
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(44.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(22.dp)
                            )
                    )
                }
            }

            // Content area with shimmer cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Arabic Text Card shimmer
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Arabic text shimmer lines
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Transliteration Card shimmer
                ShimmerSectionCard(
                    iconTint = MaterialTheme.colorScheme.secondary,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )

                // Translation Card shimmer
                ShimmerSectionCard(
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                )

                // Explanation Card shimmer
                ShimmerSectionCard(
                    iconTint = MaterialTheme.colorScheme.primary,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    lineCount = 4
                )
            }
        }

        // Toolbar overlay - always visible
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(DuaGradientColors[0], DuaGradientColors[1])
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
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

        // Bottom navigation bar shimmer
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 12.dp
        ) {
            Column {
                // Progress indicator placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button placeholder
                    ShimmerBox(
                        modifier = Modifier.size(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    )

                    // Center indicator placeholder
                    ShimmerBox(
                        modifier = Modifier
                            .width(80.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Next button placeholder
                    ShimmerBox(
                        modifier = Modifier.size(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    )
                }
            }
        }
    }
}

/**
 * Shimmer placeholder for section cards (Transliteration, Translation, Explanation)
 */
@Composable
private fun ShimmerSectionCard(
    iconTint: Color,
    backgroundColor: Color,
    lineCount: Int = 3,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header with icon shimmer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        ShimmerBox(
                            modifier = Modifier.size(18.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp),
                    shape = RoundedCornerShape(4.dp)
                )
            }

            // Content lines shimmer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(lineCount) { index ->
                    val widthFraction = when (index) {
                        0 -> 1f
                        lineCount - 1 -> 0.6f
                        else -> 0.85f
                    }
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(widthFraction)
                            .height(14.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }
    }
}
