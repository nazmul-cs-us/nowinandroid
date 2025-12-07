package com.starception.submission.feature.dua

import android.content.Context
import android.content.SharedPreferences
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
                }
            }
        }
        loadAllDuas(context)
    }

    /**
     * Get translation name for display
     */
    fun getTranslationName(code: String): String {
        return QuranTranslationHelper.getTranslationName(code)
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

                // Sort by dua number
                val sortedDuas = duas.sortedBy { it.duaNumber }

                // Load Surah names and translations from database
                val translationCode = _selectedTranslation.value
                val duasWithDetails = sortedDuas.map { dua ->
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
    onNavigateToSurah: ((surahNumber: Int, ayahNumber: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel = remember { DuaDetailViewModel(context) }
    val selectedFont by viewModel.selectedArabicFont.collectAsState()
    val selectedTranslation by viewModel.selectedTranslation.collectAsState()
    val arabicFontFamily = getArabicFontFamilyForDua(selectedFont)
    val scope = rememberCoroutineScope()

    // Get translation display name
    val translationDisplayName = remember(selectedTranslation) {
        viewModel.getTranslationName(selectedTranslation)
    }

    // Load all duas from ViewModel
    val loadedDuas by viewModel.allDuas.collectAsState()
    val duasList = if (loadedDuas.isNotEmpty()) loadedDuas else allDuas

    // Find initial page index based on dua number from title
    val initialDuaNumber = remember(title) {
        Regex("#(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    val initialPageIndex = remember(duasList, initialDuaNumber) {
        if (duasList.isNotEmpty()) {
            duasList.indexOfFirst { it.duaNumber == initialDuaNumber }.takeIf { it >= 0 } ?: 0
        } else {
            initialDuaNumber - 1
        }
    }

    // Pager state
    val pagerState = rememberPagerState(
        initialPage = if (duasList.isNotEmpty()) initialPageIndex else 0,
        pageCount = { if (duasList.isNotEmpty()) duasList.size else 1 }
    )

    // Update pager when duas load
    LaunchedEffect(duasList, initialDuaNumber) {
        if (duasList.isNotEmpty()) {
            val targetIndex = duasList.indexOfFirst { it.duaNumber == initialDuaNumber }.takeIf { it >= 0 } ?: 0
            if (pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }

    val totalDuas = if (duasList.isNotEmpty()) duasList.size else 40
    val currentPage = pagerState.currentPage
    val hasPrevious = currentPage > 0
    val hasNext = currentPage < totalDuas - 1

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Gradient Header - Material 3 Expressive
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            brush = Brush.linearGradient(colors = DuaGradientColors)
                        )
                ) {
                    // Back button
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Header content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .padding(top = 44.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Dua icon - glassmorphism style
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title
                        Text(
                            text = "Quranic Dua",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Pills row with dua number and translation
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dua number pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${currentPage + 1} / $totalDuas",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }

                            // Translation language pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = translationDisplayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Pager for duas - swipeable
                if (duasList.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        val dua = duasList[page]
                        DuaPageContent(
                            dua = dua,
                            arabicFontFamily = arabicFontFamily,
                            onNavigateToSurah = onNavigateToSurah
                        )
                    }
                } else {
                    // Fallback to single dua from navigation params
                    val parsedContent = remember(content, quranReference) {
                        parseDuaContent(content, quranReference)
                    }
                    SingleDuaContent(
                        parsedContent = parsedContent,
                        arabicFontFamily = arabicFontFamily,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Bottom navigation bar - Material 3 Expressive
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    FilledIconButton(
                        onClick = {
                            if (hasPrevious) {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage - 1)
                                }
                            }
                        },
                        enabled = hasPrevious,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Dua",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Center indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${currentPage + 1}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "of $totalDuas Duas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Next button
                    FilledIconButton(
                        onClick = {
                            if (hasNext) {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            }
                        },
                        enabled = hasNext,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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

/**
 * Content for a single dua page in the pager
 */
@Composable
private fun DuaPageContent(
    dua: DuaItem,
    arabicFontFamily: FontFamily,
    modifier: Modifier = Modifier,
    onNavigateToSurah: ((surahNumber: Int, ayahNumber: Int) -> Unit)? = null
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
        if (dua.arabicText.isNotEmpty()) {
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
                        text = dua.arabicText,
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
        if (dua.transliteration.isNotEmpty()) {
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

        // Quran reference badge - show Surah name and Ayah number (clickable link)
        if (!dua.quranReference.isNullOrEmpty() && dua.surahNumber > 0 && dua.ayahNumber > 0) {
            val referenceText = if (dua.surahName.isNotEmpty()) {
                "Surah ${dua.surahName}, Ayah ${dua.ayahNumber}"
            } else {
                "Surah ${dua.quranReference}"
            }
            val isClickable = onNavigateToSurah != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isClickable) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    },
                    modifier = if (isClickable) {
                        Modifier.clickable {
                            onNavigateToSurah?.invoke(dua.surahNumber, dua.ayahNumber)
                        }
                    } else {
                        Modifier
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = referenceText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        if (isClickable) {
                            Spacer(modifier = Modifier.size(6.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Go to Surah",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
