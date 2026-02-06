package com.starception.submission.feature.dua

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.LibraryBooks
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import com.starception.submission.core.designsystem.component.NiaTopicTag
import java.util.Locale
import com.starception.submission.core.topicsdatabase.Topic
import com.starception.submission.core.topicsdatabase.TopicsDatabase
import com.starception.submission.core.topicsdatabase.toTopic
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.core.duadatabase.DuaDatabase
import com.starception.submission.core.duadatabase.HadithReference
import com.starception.submission.core.duadatabase.toHadithReference
import com.starception.submission.core.qurandatabase.QuranDatabase
import com.starception.submission.core.ui.DynamicSkyHeader
import com.starception.submission.core.ui.ImmersiveFullScreenEffect
import com.starception.submission.core.ui.getCurrentSkyPeriodForTheme
import com.starception.submission.core.ui.getSkyColors
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalDensity
import com.starception.submission.R

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
    val surahName: String = "", // Surah name fetched from database (e.g., "Al-Baqarah")
    val context: String = "",  // When/why to recite (before the dua)
    val instruction: String = "",  // Special instructions (e.g., "Recite 3 times")
    val note: String = "",  // Additional scholarly notes
    val postContext: String = "",  // Context after the dua text
    val reference: String = ""  // Hadith references
)

/**
 * Data class representing parsed Dua content
 */
data class ParsedDuaContent(
    val arabicText: String,
    val transliteration: String,
    val translation: String,
    val explanation: String,
    val quranReference: String?,
    val context: String = "",  // When/why to recite (before the dua)
    val instruction: String = "",  // Special instructions (e.g., "Recite 3 times")
    val note: String = "",  // Additional scholarly notes
    val postContext: String = "",  // Context after the dua text
    val reference: String = ""  // Hadith references
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
        // Don't load duas here - let the composable control loading based on topicId
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

    // Cache for surah names to avoid repeated database lookups
    private val surahNameCache = mutableMapOf<Int, String>()

    /**
     * Get surah name by number from database
     * Returns the English name (e.g., "Al-Baqara") or fallback to "Surah X"
     */
    suspend fun getSurahName(surahNumber: Int): String {
        // Check cache first
        surahNameCache[surahNumber]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val db = QuranDatabase.getInstance(context)
                val surah = db.quranDao().getSurahByNumber(surahNumber)
                val name: String = if (surah?.nameEnglish?.isNotEmpty() == true) {
                    surah.nameEnglish
                } else {
                    "Surah $surahNumber"
                }
                surahNameCache[surahNumber] = name
                name
            } catch (e: Exception) {
                android.util.Log.e("DuaDetailViewModel", "Error looking up surah name for $surahNumber", e)
                "Surah $surahNumber"
            }
        }
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

    /**
     * Load duas filtered by topic ID
     */
    fun loadDuasByTopic(context: Context, topicId: String) {
        android.util.Log.d("DuaDetailViewModel", "🔍 loadDuasByTopic called with topicId='$topicId'")
        if (topicId.isEmpty()) {
            android.util.Log.d("DuaDetailViewModel", "⚠️ topicId is empty, loading all duas")
            loadAllDuas(context)
            return
        }
        viewModelScope.launch {
            try {
                val sortedDuas = withContext(Dispatchers.IO) {
                    val duas = mutableListOf<DuaItem>()

                    val dbPath = context.getDatabasePath("news_resources_topic_temp.db")
                    if (dbPath.exists()) dbPath.delete()

                    context.assets.open("databases/news.db").use { inputStream ->
                        dbPath.parentFile?.mkdirs()
                        java.io.FileOutputStream(dbPath).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbPath.absolutePath,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                    )

                    // Query duas filtered by topic
                    val cursor = db.rawQuery(
                        """SELECT nr.id, nr.title, nr.content, nr.type
                           FROM news_resources nr
                           INNER JOIN news_topics nt ON nr.id = nt.news_id
                           WHERE nt.topic_id = ? AND nr.type LIKE '%Dua%'""",
                        arrayOf(topicId)
                    )

                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(0).toString()
                        val title = cursor.getString(1) ?: ""
                        val content = cursor.getString(2) ?: ""

                        val parsed = parseDuaContent(content, null)
                        val duaNumber = Regex("#(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("Dua (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("\\((\\d+)/\\d+\\)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                            ?: (duas.size + 1)

                        // Parse Quran reference from title (e.g., "(2:127)" -> surah=2, ayah=127)
                        val quranRefMatch = Regex("\\((\\d+):(\\d+)\\)").find(title)
                        val surahNum = quranRefMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val ayahNum = quranRefMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                        val quranRef = if (surahNum > 0 && ayahNum > 0) "$surahNum:$ayahNum" else null

                        duas.add(
                            DuaItem(
                                id = id,
                                title = title,
                                arabicText = parsed.arabicText,
                                transliteration = parsed.transliteration,
                                translation = parsed.translation,
                                explanation = parsed.explanation,
                                quranReference = quranRef,
                                duaNumber = duaNumber,
                                surahNumber = surahNum,
                                ayahNumber = ayahNum,
                                surahName = "",
                                context = parsed.context,
                                instruction = parsed.instruction,
                                note = parsed.note,
                                postContext = parsed.postContext,
                                reference = parsed.reference
                            )
                        )
                    }

                    cursor.close()
                    db.close()
                    dbPath.delete()

                    duas.sortedBy { it.duaNumber }
                }

                android.util.Log.d("DuaDetailViewModel", "✅ Loaded ${sortedDuas.size} duas for topic")
                baseDuas = sortedDuas
                _allDuas.value = sortedDuas
            } catch (e: Exception) {
                android.util.Log.e("DuaDetailViewModel", "❌ Error loading duas by topic", e)
                e.printStackTrace()
                // Fall back to loading all duas
                loadAllDuas(context)
            }
        }
    }

    /**
     * Public method to load all duas (called from composable when no topicId)
     */
    fun loadAllDuasPublic(context: Context) {
        loadAllDuas(context)
    }

    private fun loadAllDuas(context: Context) {
        viewModelScope.launch {
            try {
                // Phase 1: Load from news_resources.db on IO thread
                val sortedDuas = withContext(Dispatchers.IO) {
                    val duas = mutableListOf<DuaItem>()

                    // Open news_resources.db from assets
                    val dbPath = context.getDatabasePath("news_resources_temp.db")
                    if (dbPath.exists()) dbPath.delete()

                    context.assets.open("databases/news.db").use { inputStream ->
                        dbPath.parentFile?.mkdirs()
                        java.io.FileOutputStream(dbPath).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbPath.absolutePath,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                    )

                    val cursor = db.rawQuery(
                        "SELECT id, title, content, type FROM news_resources WHERE type LIKE '%Dua%'",
                        null
                    )

                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(0).toString()
                        val title = cursor.getString(1) ?: ""
                        val content = cursor.getString(2) ?: ""
                        val type = cursor.getString(3) ?: ""

                        val parsed = parseDuaContent(content, null)
                        val duaNumber = Regex("#(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("Dua (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("\\((\\d+)/\\d+\\)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                            ?: (duas.size + 1)

                        // Parse Quran reference from title (e.g., "(2:127)" -> surah=2, ayah=127)
                        val quranRefMatch = Regex("\\((\\d+):(\\d+)\\)").find(title)
                        val surahNum = quranRefMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val ayahNum = quranRefMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                        val quranRef = if (surahNum > 0 && ayahNum > 0) "$surahNum:$ayahNum" else null

                        duas.add(
                            DuaItem(
                                id = id,
                                title = title,
                                arabicText = parsed.arabicText,
                                transliteration = parsed.transliteration,
                                translation = parsed.translation,
                                explanation = parsed.explanation,
                                quranReference = quranRef,
                                duaNumber = duaNumber,
                                surahNumber = surahNum,
                                ayahNumber = ayahNum,
                                surahName = "",
                                context = parsed.context,
                                instruction = parsed.instruction,
                                note = parsed.note,
                                postContext = parsed.postContext,
                                reference = parsed.reference
                            )
                        )
                    }

                    cursor.close()
                    db.close()
                    dbPath.delete()

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
    var context = ""
    var instruction = ""
    var note = ""
    var postContext = ""
    var reference = ""

    for (part in parts) {
        val trimmedPart = part.trim()

        when {
            // Context - when/why to recite
            trimmedPart.startsWith("**Context:**") -> {
                context = trimmedPart.removePrefix("**Context:**").trim()
            }
            // Instruction - special instructions
            trimmedPart.startsWith("**Instruction:**") -> {
                instruction = trimmedPart.removePrefix("**Instruction:**").trim()
            }
            // Arabic text
            trimmedPart.startsWith("**Arabic:**") -> {
                val inlineArabic = trimmedPart.removePrefix("**Arabic:**").trim()
                if (inlineArabic.isNotEmpty()) {
                    arabicText = inlineArabic
                }
            }
            // Transliteration
            trimmedPart.startsWith("**Transliteration:**") -> {
                transliteration = trimmedPart.removePrefix("**Transliteration:**").trim()
            }
            trimmedPart.startsWith("Transliteration:") -> {
                transliteration = trimmedPart.removePrefix("Transliteration:").trim()
            }
            // Translation
            trimmedPart.startsWith("**Translation:**") -> {
                translation = trimmedPart.removePrefix("**Translation:**").trim()
            }
            trimmedPart.startsWith("Translation:") -> {
                translation = trimmedPart.removePrefix("Translation:").trim()
            }
            // Note - scholarly notes
            trimmedPart.startsWith("**Note:**") -> {
                note = trimmedPart.removePrefix("**Note:**").trim()
            }
            // Additional Context / Post Context
            trimmedPart.startsWith("**Additional Context:**") -> {
                postContext = trimmedPart.removePrefix("**Additional Context:**").trim()
            }
            // Reference - hadith sources
            trimmedPart.startsWith("**Reference:**") -> {
                reference = trimmedPart.removePrefix("**Reference:**").trim()
            }
            // Detect Arabic text by Unicode range (fallback)
            trimmedPart.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' } && arabicText.isEmpty() -> {
                arabicText = trimmedPart
            }
            // Collect explanation (text not matching any marker)
            translation.isNotEmpty() && !trimmedPart.startsWith("**") && trimmedPart.isNotEmpty() -> {
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
        quranReference = quranReference,
        context = context,
        instruction = instruction,
        note = note,
        postContext = postContext,
        reference = reference
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
    onToggleNiaBookmark: (newsResourceId: String) -> Unit = {},
    topicId: String = "",
    onTopicClick: (String) -> Unit = {},
    onHadithClick: ((collectionName: String, hadithNumber: Int, databaseFile: String) -> Unit)? = null
) {
    // Enable immersive full-screen mode (hides status bar)
    ImmersiveFullScreenEffect()

    val context = LocalContext.current
    val viewModel = remember { DuaDetailViewModel(context) }
    val selectedFont by viewModel.selectedArabicFont.collectAsState()
    val selectedTranslation by viewModel.selectedTranslation.collectAsState()
    val showTajweed by viewModel.showTajweed.collectAsState()
    val arabicFontFamily = getArabicFontFamilyForDua(selectedFont)
    val scope = rememberCoroutineScope()

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Topics state for displaying in header
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }

    // Load topics when newsResourceId changes
    LaunchedEffect(initialNewsResourceId) {
        if (initialNewsResourceId.isNotEmpty()) {
            val newsId = initialNewsResourceId.toIntOrNull()
            if (newsId != null) {
                try {
                    val newsDao = NewsDatabase.getInstance(context).newsDao()
                    val topicIds = newsDao.getTopicIdsForNews(newsId)
                    if (topicIds.isNotEmpty()) {
                        val topicsDao = TopicsDatabase.getInstance(context).topicsDao()
                        val topicEntities = topicsDao.getTopicsByIds(topicIds)
                        topics = topicEntities.map { it.toTopic() }
                        android.util.Log.d("DuaDetailScreen", "📚 Loaded ${topics.size} topics for dua")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DuaDetailScreen", "❌ Error loading topics: ${e.message}")
                }
            }
        }
    }

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

    // Load duas - either filtered by topic or all duas
    LaunchedEffect(topicId) {
        android.util.Log.d("DuaDetailScreen", "🔍 LaunchedEffect topicId='$topicId'")
        if (topicId.isNotEmpty()) {
            android.util.Log.d("DuaDetailScreen", "📂 Loading duas for topic: $topicId")
            viewModel.loadDuasByTopic(context, topicId)
        } else {
            android.util.Log.d("DuaDetailScreen", "📂 Loading ALL duas (no topicId)")
            viewModel.loadAllDuasPublic(context)
        }
    }

    // Use the passed allDuas list if provided (e.g., from a topic), otherwise load all duas from ViewModel
    // This ensures "Dua X of Y" shows the correct count for the topic, not all 322 duas
    val loadedDuas by viewModel.allDuas.collectAsState()
    val duasList = if (allDuas.isNotEmpty()) allDuas else loadedDuas

    // Find initial page index based on dua number from title
    // Handle both formats: "Dua #2" and "Quranic Dua 2: Make us Muslims"
    val initialDuaNumber = remember(title) {
        Regex("#(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("Dua (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: 1
    }

    // Find page index by news resource ID first (works for fortress_of_the_muslim duas)
    // Fall back to dua number matching if ID not found
    val initialPageIndex = remember(duasList, initialDuaNumber, initialNewsResourceId) {
        if (duasList.isNotEmpty()) {
            // First try to find by news resource ID (for fortress_of_the_muslim and all duas)
            if (initialNewsResourceId.isNotEmpty()) {
                val indexById = duasList.indexOfFirst { it.id == initialNewsResourceId }
                if (indexById >= 0) {
                    return@remember indexById
                }
            }
            // Fall back to dua number matching
            duasList.indexOfFirst { it.duaNumber == initialDuaNumber }.takeIf { it >= 0 } ?: 0
        } else {
            initialDuaNumber - 1
        }
    }

    // Calculate initial page index - prefer ID-based lookup, fall back to number-based
    val targetPageIndex = initialPageIndex.coerceAtLeast(0)

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

    // Also scroll when duas load (in case index needs adjustment based on ID or duaNumber field)
    LaunchedEffect(duasList, initialNewsResourceId) {
        if (duasList.isNotEmpty()) {
            // First try to find by news resource ID
            val indexById = if (initialNewsResourceId.isNotEmpty()) {
                duasList.indexOfFirst { it.id == initialNewsResourceId }.takeIf { it >= 0 }
            } else null
            // Fall back to dua number matching
            val indexByNumber = duasList.indexOfFirst { it.duaNumber == initialDuaNumber }.takeIf { it >= 0 }
            val targetIndex = indexById ?: indexByNumber ?: targetPageIndex.coerceIn(0, duasList.size - 1)
            if (pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }

    // When duasList is empty but we have fallback content, show 1 (single dua from navigation params)
    val totalDuas = when {
        duasList.isNotEmpty() -> duasList.size
        content.isNotBlank() -> 1  // Fallback: single dua from navigation params
        else -> 1  // Default to 1, not 40
    }
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

    android.util.Log.d("DuaScreen", "🔍 RENDER: duasList.size=${duasList.size}, allDuas.size=${allDuas.size}, loadedDuas collected")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent  // Transparent to let sky extend to top
    ) { _ ->
        // Don't apply paddingValues - let content scroll under transparent toolbar like SurahDetailScreen
        Box(modifier = Modifier.fillMaxSize()) {
            android.util.Log.d("DuaScreen", "📦 BOX: duasList.isEmpty=${duasList.isEmpty()}")

            // Show shimmer loading state while data loads
            // Show shimmer only when no data is available at all (neither from pager nor fallback)
            val hasContent = content.isNotBlank()
            if (duasList.isEmpty() && !hasContent) {
                android.util.Log.d("DuaScreen", "⏳ SHOWING SHIMMER (no content)")
                DuaShimmerLoadingContent(
                    onBackClick = onBackClick
                )
            }

            // Pager for duas - with pager swipe and edge indicators
            if (duasList.isNotEmpty()) {
                android.util.Log.d("DuaScreen", "📖 SHOWING PAGER with ${duasList.size} duas")

                Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
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
                        // No status bar padding - immersive mode hides status bar
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 56.dp) // Space for Vuesax pagination pill
                        ) {
                            // Header with Masjid al-Nawabi image - with parallax effect
                            item {
                                // Calculate parallax progress based on scroll
                                val scrollOffset = lazyListState.firstVisibleItemScrollOffset.toFloat()
                                val headerHeightPx = with(LocalDensity.current) {
                                    if (isLandscape) 200.dp.toPx() else 400.dp.toPx()
                                }
                                val parallaxProgress = (scrollOffset / headerHeightPx).coerceIn(0f, 1f)

                                // Easing function for smooth parallax
                                fun easeOutCubic(x: Float): Float = 1f - (1f - x).let { it * it * it }
                                val easedProgress = easeOutCubic(parallaxProgress)
                                val centeredProgress = (easedProgress - 0.5f) * 2f

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Column {
                                        // Album-style header image with parallax
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (isLandscape) {
                                                        Modifier.height(200.dp)
                                                    } else {
                                                        Modifier.aspectRatio(1f)
                                                    }
                                                )
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        ) {
                                            Image(
                                                painter = painterResource(R.drawable.masjid_al_nawabi),
                                                contentDescription = "Masjid al-Nawabi",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer {
                                                        // Scale effect: 1.0 -> 1.08 zoom
                                                        val scaleValue = 1f + (1f - kotlin.math.abs(centeredProgress)) * 0.08f
                                                        scaleX = scaleValue
                                                        scaleY = scaleValue

                                                        // Subtle vertical translation
                                                        val maxTranslation = 15.dp.toPx()
                                                        translationY = -easedProgress * maxTranslation

                                                        // Alpha variation
                                                        alpha = 0.95f + (1f - kotlin.math.abs(centeredProgress)) * 0.05f
                                                    },
                                                contentScale = ContentScale.Crop,
                                                alignment = Alignment.Center
                                            )
                                        }

                                        // Info card below header
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp)
                                                .padding(top = 12.dp, bottom = 8.dp)
                                        ) {
                                            // Load hadith references for header display
                                            var headerHadithRefs by remember { mutableStateOf<List<HadithReference>>(emptyList()) }
                                            LaunchedEffect(dua.title) {
                                                if (dua.surahNumber <= 0 || dua.ayahNumber <= 0) {
                                                    // Only load for non-Quranic duas
                                                    try {
                                                        // Parse title to extract chapter name and dua number
                                                        // Titles follow pattern: "When waking up: Dua 1"
                                                        val titleParts = dua.title.split(": Dua ")
                                                        if (titleParts.size == 2) {
                                                            val chapterTitle = titleParts[0].trim()
                                                            val position = titleParts[1].trim().toIntOrNull() ?: 1
                                                            val duaDb = DuaDatabase.getInstance(context)
                                                            val refs = duaDb.duaDao().getHadithReferencesByChapterAndPosition(chapterTitle, position)
                                                            headerHadithRefs = refs.map { it.toHadithReference() }
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("DuaDetailScreen", "Error loading header hadith refs", e)
                                                    }
                                                }
                                            }

                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Dua title - cleaned and formatted
                                                val duaTitle = dua.title
                                                    .replace(Regex("Quranic Dua \\d+:\\s*"), "")
                                                    .replace(Regex("Dua #\\d+:\\s*"), "")
                                                    .replace(Regex("Dua \\d+:\\s*"), "")
                                                    .replace(Regex("\\s*\\(\\d+:\\d+\\)\\s*$"), "")
                                                    .replace(Regex("\\s*\\(\\d+/\\d+\\)\\s*$"), "")
                                                    .trim()
                                                    .let { title ->
                                                        if (title.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }) {
                                                            "Dua"
                                                        } else {
                                                            title.ifEmpty { "Dua" }
                                                        }
                                                    }

                                                // Group 1: Title + Arabic subtitle
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = duaTitle,
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 2,
                                                        lineHeight = 28.sp
                                                    )

                                                    // Arabic subtitle using selected font
                                                    if (dua.arabicText.isNotBlank()) {
                                                        val arabicPreview = dua.arabicText.split("\n").firstOrNull()?.take(60) ?: ""
                                                        if (arabicPreview.isNotBlank()) {
                                                            Text(
                                                                text = arabicPreview + if (arabicPreview.length >= 60) "..." else "",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                fontFamily = arabicFontFamily,
                                                                fontSize = 16.sp
                                                            )
                                                        }
                                                    }
                                                }

                                                // Group 2: Hadith references (scrollable left) + Topic tag (fixed right)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Left side - Surah reference OR Hadith reference chips (scrollable, takes remaining space)
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .horizontalScroll(rememberScrollState())
                                                    ) {
                                                        if (dua.surahNumber > 0 && dua.ayahNumber > 0) {
                                                            // Quranic dua - show Surah reference
                                                            var surahName by remember { mutableStateOf(dua.surahName.ifEmpty { "Surah ${dua.surahNumber}" }) }
                                                            LaunchedEffect(dua.surahNumber) {
                                                                if (dua.surahName.isEmpty()) {
                                                                    surahName = viewModel.getSurahName(dua.surahNumber)
                                                                }
                                                            }
                                                            NiaTopicTag(
                                                                followed = true,
                                                                onClick = { onNavigateToSurah?.invoke(dua.surahNumber, dua.ayahNumber) },
                                                                text = {
                                                                    Text(text = "$surahName:${dua.ayahNumber}".uppercase(Locale.getDefault()))
                                                                }
                                                            )
                                                        } else if (headerHadithRefs.isNotEmpty()) {
                                                            // Non-Quranic dua - show Hadith references
                                                            headerHadithRefs.forEach { ref ->
                                                                val refText = buildString {
                                                                    append(ref.collectionName ?: "Hadith")
                                                                    if (ref.hadithNumber != null) {
                                                                        append(":${ref.hadithNumber}")
                                                                    }
                                                                }
                                                                NiaTopicTag(
                                                                    followed = false,
                                                                    onClick = {
                                                                        if (ref.databaseFile != null && ref.hadithNumber != null && onHadithClick != null) {
                                                                            onHadithClick(
                                                                                ref.collectionName ?: "Hadith",
                                                                                ref.hadithNumber,
                                                                                ref.databaseFile
                                                                            )
                                                                        }
                                                                    },
                                                                    text = {
                                                                        Text(text = refText.uppercase(Locale.getDefault()))
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Right side - Topic tag (fixed, single line, never wraps)
                                                    if (topics.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        topics.take(1).forEach { topic -> // Show only first topic to ensure single line
                                                            NiaTopicTag(
                                                                followed = true,
                                                                onClick = { onTopicClick(topic.id) },
                                                                text = {
                                                                    Text(
                                                                        text = topic.name.uppercase(Locale.getDefault()),
                                                                        maxLines = 1
                                                                    )
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

                        // Spacer between header and content cards
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Context section - When/why to recite (before the dua)
                        if (dua.context.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Context",
                                    accentColor = Color(0xFF4CAF50), // Green accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = true
                                ) {
                                    Text(
                                        text = dua.context,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            lineHeight = 24.sp
                                        ),
                                        color = Color(0xFF5D5D5D)
                                    )
                                }
                            }
                        }

                        // Arabic Text Card - Using CollapsibleDuaSection for consistent styling
                        if (dua.arabicText.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Arabic",
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = true
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
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            text = annotatedText,
                                            fontFamily = arabicFontFamily,
                                            fontSize = 32.sp,
                                            lineHeight = 54.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Text(
                                            text = dua.arabicText,
                                            fontFamily = arabicFontFamily,
                                            fontSize = 32.sp,
                                            lineHeight = 54.sp,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        // Instruction section - Special instructions (e.g., "Recite 3 times")
                        if (dua.instruction.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Instruction",
                                    accentColor = Color(0xFFE91E63), // Pink/red accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = true
                                ) {
                                    Text(
                                        text = dua.instruction,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            lineHeight = 24.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = Color(0xFF5D5D5D)
                                    )
                                }
                            }
                        }

                        // Translation - Collapsible section with left accent
                        if (dua.translation.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Translation",
                                    accentColor = Color(0xFF9E9E9E), // Gray accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = true
                                ) {
                                    Text(
                                        text = dua.translation,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 17.sp,
                                            lineHeight = 28.sp,
                                            fontStyle = FontStyle.Italic
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Transliteration - Collapsible section with left accent
                        if (dua.transliteration.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Transliteration",
                                    accentColor = Color(0xFF9E9E9E), // Gray accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = true
                                ) {
                                    Text(
                                        text = dua.transliteration,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 17.sp,
                                            lineHeight = 28.sp,
                                            fontStyle = FontStyle.Italic
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Explanation - Collapsible section
                        if (dua.explanation.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Explanation",
                                    accentColor = Color(0xFF8BC34A), // Light green accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = false
                                ) {
                                    Text(
                                        text = dua.explanation,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            lineHeight = 24.sp
                                        ),
                                        color = Color(0xFF5D5D5D)
                                    )
                                }
                            }
                        }

                        // Note section - Additional scholarly notes
                        if (dua.note.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Note",
                                    accentColor = Color(0xFFFF9800), // Orange accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = false
                                ) {
                                    Text(
                                        text = dua.note,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            fontStyle = FontStyle.Italic
                                        ),
                                        color = Color(0xFF5D5D5D)
                                    )
                                }
                            }
                        }

                        // Post Context section - Additional context after the dua
                        if (dua.postContext.isNotEmpty()) {
                            item {
                                CollapsibleDuaSection(
                                    title = "Additional Context",
                                    accentColor = Color(0xFF2196F3), // Blue accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = false
                                ) {
                                    Text(
                                        text = dua.postContext,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            lineHeight = 24.sp
                                        ),
                                        color = Color(0xFF5D5D5D)
                                    )
                                }
                            }
                        }

                        // Reference section - Hadith sources with clickable chips
                        // Always try to load hadith references for fortress_of_the_muslim duas
                        item {
                            // State for loaded hadith references
                            var hadithReferences by remember { mutableStateOf<List<HadithReference>>(emptyList()) }

                            // Load hadith references by parsing title to get chapter and position
                            // Titles follow pattern: "When waking up: Dua 1", "In the morning and evening: Dua 2"
                            LaunchedEffect(dua.title) {
                                try {
                                    // Try to parse title to extract chapter name and dua number
                                    val titleParts = dua.title.split(": Dua ")
                                    if (titleParts.size == 2) {
                                        val chapterTitle = titleParts[0].trim()
                                        val position = titleParts[1].trim().toIntOrNull() ?: 1
                                        val duaDb = DuaDatabase.getInstance(context)
                                        val refs = duaDb.duaDao().getHadithReferencesByChapterAndPosition(chapterTitle, position)
                                        hadithReferences = refs.map { it.toHadithReference() }
                                        android.util.Log.d("DuaDetailScreen", "📖 Loaded ${hadithReferences.size} hadith references for '$chapterTitle' position $position")
                                    } else {
                                        android.util.Log.d("DuaDetailScreen", "📖 Title format not matching: ${dua.title}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DuaDetailScreen", "❌ Error loading hadith references", e)
                                }
                            }

                            // Show Reference section if we have reference text OR hadith references
                            if (dua.reference.isNotEmpty() || hadithReferences.isNotEmpty()) {
                                CollapsibleDuaSection(
                                    title = "Reference",
                                    accentColor = Color(0xFF9C27B0), // Purple accent
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    initiallyExpanded = false
                                ) {
                                    Column {
                                        // Show original reference text
                                        Text(
                                            text = dua.reference,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp
                                            ),
                                            color = Color(0xFF757575)
                                        )

                                        // Show clickable hadith reference chips if available - using NiaTopicTag for consistency
                                        if (hadithReferences.isNotEmpty() && onHadithClick != null) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                hadithReferences.forEach { ref ->
                                                    if (ref.databaseFile != null && ref.hadithNumber != null) {
                                                        NiaTopicTag(
                                                            followed = false,
                                                            onClick = {
                                                                onHadithClick(
                                                                    ref.collectionName ?: "Hadith",
                                                                    ref.hadithNumber,
                                                                    ref.databaseFile
                                                                )
                                                            },
                                                            text = {
                                                                Text(
                                                                    text = ref.displayName.uppercase(Locale.getDefault())
                                                                )
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

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    }
                }

                // Edge indicators based on pager offset
                val pageOffset = pagerState.currentPageOffsetFraction
                val absOffset = kotlin.math.abs(pageOffset)
                val pagerSwipeProgress = (absOffset / 0.2f).coerceIn(0f, 1f)
                val isSwipingToNext = pageOffset > 0.005f
                val isSwipingToPrev = pageOffset < -0.005f

                val showLeftArrow = isSwipingToPrev && hasPrevious
                val showRightArrow = isSwipingToNext && hasNext
                val targetProgress = if (showLeftArrow || showRightArrow) pagerSwipeProgress else 0f

                val duaAnimatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = if (targetProgress == 0f) {
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
                    } else {
                        spring(stiffness = Spring.StiffnessHigh)
                    },
                    label = "duaSwipeProgress",
                )

                if (duaAnimatedProgress > 0.01f && hasPrevious && (showLeftArrow || duaAnimatedProgress > 0.01f)) {
                    DuaSwipeArrowIndicator(
                        progress = duaAnimatedProgress,
                        thresholdReached = pagerSwipeProgress >= 1f && isSwipingToPrev,
                        isLeftEdge = true,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }

                if (duaAnimatedProgress > 0.01f && hasNext && (showRightArrow || duaAnimatedProgress > 0.01f)) {
                    DuaSwipeArrowIndicator(
                        progress = duaAnimatedProgress,
                        thresholdReached = pagerSwipeProgress >= 1f && isSwipingToNext,
                        isLeftEdge = false,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                } // end wrapping Box
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

            // Bottom indicator - Vuesax-style pagination with numbered buttons
            val coroutineScope = rememberCoroutineScope()

            // State for expanded ellipsis (-1 = left ellipsis, -2 = right ellipsis, 0 = none)
            var expandedEllipsis by remember { mutableStateOf(0) }

            // Calculate which page numbers to show (max 5 numbers + ellipsis)
            val maxVisible = 5
            val pageNumbers = remember(currentPage, totalDuas) {
                buildList {
                    when {
                        totalDuas <= maxVisible + 2 -> {
                            // Show all pages if total is small
                            for (i in 0 until totalDuas) add(i)
                        }
                        currentPage < maxVisible - 1 -> {
                            // Near start: show first pages + ellipsis + last
                            for (i in 0 until maxVisible) add(i)
                            add(-1) // ellipsis
                            add(totalDuas - 1)
                        }
                        currentPage > totalDuas - maxVisible -> {
                            // Near end: show first + ellipsis + last pages
                            add(0)
                            add(-1) // ellipsis
                            for (i in totalDuas - maxVisible until totalDuas) add(i)
                        }
                        else -> {
                            // Middle: show first + ellipsis + current context + ellipsis + last
                            add(0)
                            add(-1) // ellipsis (left)
                            add(currentPage - 1)
                            add(currentPage)
                            add(currentPage + 1)
                            add(-2) // ellipsis (right)
                            add(totalDuas - 1)
                        }
                    }
                }
            }

            // Calculate hidden page ranges for each ellipsis
            // Find the index of ellipsis in pageNumbers to determine what pages it hides
            val leftEllipsisRange = remember(currentPage, totalDuas, pageNumbers) {
                val ellipsisIndex = pageNumbers.indexOf(-1)
                if (ellipsisIndex >= 0) {
                    // Get the page before ellipsis and after ellipsis
                    val pageBefore = if (ellipsisIndex > 0) pageNumbers[ellipsisIndex - 1] else -1
                    val pageAfter = if (ellipsisIndex < pageNumbers.size - 1) pageNumbers[ellipsisIndex + 1] else -1

                    if (pageBefore >= 0 && pageAfter >= 0) {
                        // Hidden pages are between pageBefore and pageAfter (exclusive)
                        ((pageBefore + 1) until pageAfter).toList()
                    } else emptyList()
                } else emptyList()
            }

            val rightEllipsisRange = remember(currentPage, totalDuas, pageNumbers) {
                val ellipsisIndex = pageNumbers.indexOf(-2)
                if (ellipsisIndex >= 0) {
                    // Get the page before ellipsis and after ellipsis
                    val pageBefore = if (ellipsisIndex > 0) pageNumbers[ellipsisIndex - 1] else -1
                    val pageAfter = if (ellipsisIndex < pageNumbers.size - 1) pageNumbers[ellipsisIndex + 1] else -1

                    if (pageBefore >= 0 && pageAfter >= 0) {
                        // Hidden pages are between pageBefore and pageAfter (exclusive)
                        ((pageBefore + 1) until pageAfter).toList()
                    } else emptyList()
                } else emptyList()
            }

            // Vuesax Circle Pagination - Clean circular buttons with inline grid expansion
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp), // Equal 12dp gap above and below pagination
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expanded grid view for hidden pages (appears above pagination bar)
                val activeHiddenPages = when (expandedEllipsis) {
                    -1 -> leftEllipsisRange
                    -2 -> rightEllipsisRange
                    else -> emptyList()
                }

                AnimatedVisibility(
                    visible = expandedEllipsis != 0 && activeHiddenPages.isNotEmpty(),
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = Color.Black.copy(alpha = 0.1f),
                                spotColor = Color.Black.copy(alpha = 0.15f)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Split pages into rows of 4
                            activeHiddenPages.chunked(4).forEach { rowPages ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowPages.forEach { page ->
                                        val isActive = page == currentPage
                                        Surface(
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(page)
                                                }
                                                expandedEllipsis = 0
                                            },
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(50),
                                            color = if (isActive) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Text(
                                                    text = "${page + 1}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Main pagination bar
                Surface(
                    modifier = Modifier
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = Color.Black.copy(alpha = 0.1f),
                            spotColor = Color.Black.copy(alpha = 0.15f)
                        ),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous arrow - circular
                        Surface(
                            onClick = {
                                if (hasPrevious) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentPage - 1)
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(50),
                            color = if (hasPrevious) MaterialTheme.colorScheme.primaryContainer
                                   else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous",
                                    tint = if (hasPrevious) MaterialTheme.colorScheme.onPrimaryContainer
                                          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Circular page buttons
                        pageNumbers.forEach { pageIndex ->
                            if (pageIndex < 0) {
                                // Ellipsis - clickable to expand inline grid
                                val ellipsisId = pageIndex // -1 or -2
                                val isExpanded = expandedEllipsis == ellipsisId

                                Surface(
                                    onClick = {
                                        expandedEllipsis = if (isExpanded) 0 else ellipsisId
                                    },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(50),
                                    color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer
                                           else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = "•••",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isExpanded) MaterialTheme.colorScheme.onPrimaryContainer
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            } else {
                                val isActive = pageIndex == currentPage
                                // Animated scale for active
                                val scale by animateFloatAsState(
                                    targetValue = if (isActive) 1.15f else 1f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "scale"
                                )
                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIndex)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .then(
                                            if (isActive) Modifier.shadow(
                                                elevation = 6.dp,
                                                shape = RoundedCornerShape(50),
                                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            ) else Modifier
                                        ),
                                    shape = RoundedCornerShape(50),
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = "${pageIndex + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Next arrow - circular
                        Surface(
                            onClick = {
                                if (hasNext) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentPage + 1)
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(50),
                            color = if (hasNext) MaterialTheme.colorScheme.primaryContainer
                                   else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next",
                                    tint = if (hasNext) MaterialTheme.colorScheme.onPrimaryContainer
                                          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Fixed toolbar at top - transparent to show sky through
            // Use fixed padding instead of statusBarsPadding() to prevent jump when immersive mode activates
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp) // Fixed padding to clear camera punch hole area
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 4.dp),
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

                    Spacer(modifier = Modifier.weight(1f))

                    // Translation button with language indicator (matches SurahDetailScreen)
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // Font selection button with font hint (matches SurahDetailScreen)
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

                    Spacer(modifier = Modifier.width(4.dp))

                    // Tajweed toggle button
                    IconButton(onClick = { viewModel.toggleTajweed() }) {
                        Icon(
                            imageVector = if (showTajweed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                            contentDescription = if (showTajweed) "Disable Tajweed" else "Enable Tajweed",
                            tint = Color.White
                        )
                    }

                    // Bookmark button - uses current page's news resource ID
                    val currentDuaNewsResourceId = if (duasList.isNotEmpty() && currentPage < duasList.size) {
                        duasList[currentPage].id
                    } else {
                        initialNewsResourceId
                    }

                    // Use remembered state that syncs with parent's bookmark status
                    var localBookmarkState by remember(currentDuaNewsResourceId) {
                        mutableStateOf(isNiaBookmarked(currentDuaNewsResourceId))
                    }

                    // Sync with parent when it changes
                    val parentBookmarkState = isNiaBookmarked(currentDuaNewsResourceId)
                    LaunchedEffect(parentBookmarkState) {
                        localBookmarkState = parentBookmarkState
                    }

                    IconButton(onClick = {
                        onToggleNiaBookmark(currentDuaNewsResourceId)
                        // Optimistically update local state for immediate UI feedback
                        localBookmarkState = !localBookmarkState
                    }) {
                        Icon(
                            imageVector = if (localBookmarkState) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (localBookmarkState) "Remove Bookmark" else "Add Bookmark",
                            tint = Color.White
                        )
                    }

                    // More options button (vertical dots)
                    IconButton(
                        onClick = { /* Menu action */ },
                        modifier = Modifier.offset(x = (-8).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White
                        )
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
 * Collapsible section card component with left accent border
 * Matches the clean, minimal design with expand/collapse functionality
 */
@Composable
private fun CollapsibleDuaSection(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(accentColor)
                    .then(
                        if (isExpanded) {
                            Modifier.height(androidx.compose.ui.unit.Dp.Unspecified)
                        } else {
                            Modifier.height(56.dp)
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 0.dp)
            ) {
                // Header row - clickable to expand/collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotationAngle)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationAngle)
                    )
                }

                // Expandable content
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Reusable section card component with icon header (legacy - for backwards compatibility)
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
    CollapsibleDuaSection(
        title = title,
        accentColor = iconTint,
        modifier = modifier,
        initiallyExpanded = true
    ) {
        content()
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
    ) {
        // Header with dynamic sky - matches the pager header style
        val skyPeriod = getCurrentSkyPeriodForTheme()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp) // Shorter for fallback (no toolbar overlap needed)
        ) {
            // Dynamic sky background based on time of day
            DynamicSkyHeader(
                modifier = Modifier.fillMaxSize(),
                height = 220.dp,
                period = skyPeriod
            )
            // Semi-transparent overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 80.dp, bottom = 16.dp), // 80dp for toolbar
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = "Dua",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Dua",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Content cards with padding
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Arabic Text Card - Using CollapsibleDuaSection for consistent styling
            if (parsedContent.arabicText.isNotEmpty()) {
                CollapsibleDuaSection(
                    title = "Arabic",
                    accentColor = MaterialTheme.colorScheme.primary,
                    initiallyExpanded = true
                ) {
                    Text(
                        text = parsedContent.arabicText,
                        fontFamily = arabicFontFamily,
                        fontSize = 32.sp,
                        lineHeight = 54.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Transliteration - Collapsible section
            if (parsedContent.transliteration.isNotEmpty()) {
                CollapsibleDuaSection(
                    title = "Transliteration",
                    accentColor = Color(0xFF9E9E9E),
                    initiallyExpanded = true
                ) {
                    Text(
                        text = parsedContent.transliteration,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = Color(0xFF5D5D5D)
                    )
                }
            }

            // Translation - Collapsible section
            if (parsedContent.translation.isNotEmpty()) {
                CollapsibleDuaSection(
                    title = "Translation",
                    accentColor = Color(0xFF9E9E9E),
                    initiallyExpanded = true
                ) {
                    Text(
                        text = parsedContent.translation,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = Color(0xFF5D5D5D)
                    )
                }
            }

            // Explanation - Collapsible section
            if (parsedContent.explanation.isNotEmpty()) {
                CollapsibleDuaSection(
                    title = "Explanation",
                    accentColor = Color(0xFF8BC34A),
                    initiallyExpanded = false
                ) {
                    Text(
                        text = parsedContent.explanation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        ),
                        color = Color(0xFF5D5D5D)
                    )
                }
            }
        } // End of content Column
    } // End of outer Column
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
            // Header with dynamic sky - shown immediately with shimmer placeholders
            val skyPeriod = getCurrentSkyPeriodForTheme()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp) // Match taller header
            ) {
                // Dynamic sky background based on time of day
                DynamicSkyHeader(
                    modifier = Modifier.fillMaxSize(),
                    height = 420.dp,
                    period = skyPeriod
                )
                // Semi-transparent overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
                // Header content positioned at bottom to show more sky artwork
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 100.dp, bottom = 4.dp), // Position content at very bottom
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Dua", // Generic text while loading
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Shimmer placeholder for topic/Surah reference pill
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(36.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(18.dp)
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

        // Toolbar overlay - transparent to show sky through
        // Use fixed padding instead of statusBarsPadding() to prevent jump when immersive mode activates
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp) // Fixed padding to clear camera punch hole area
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { /* Menu action */ },
                    modifier = Modifier.offset(x = (-8).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
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

/**
 * Android system back gesture style edge indicator.
 * Starts as tall pill, becomes circular when threshold reached.
 */
@Composable
private fun DuaSwipeArrowIndicator(
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

    val baseWidth = 24f
    val baseHeight = 64f
    val targetSize = 40f

    val pillWidth = baseWidth + (targetSize - baseWidth) * progress
    val pillHeight = baseHeight - (baseHeight - targetSize) * progress
    val cornerRadius = pillWidth / 2f

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
            imageVector = if (isLeftEdge) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((18f + progress * 4f).dp),
        )
    }
}
