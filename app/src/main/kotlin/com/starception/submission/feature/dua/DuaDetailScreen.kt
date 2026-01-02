package com.starception.submission.feature.dua

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
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

            // Pager for duas - swipeable, with scrollable header inside each page
            if (duasList.isNotEmpty()) {
                android.util.Log.d("DuaScreen", "📖 SHOWING PAGER with ${duasList.size} duas")
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
                        // No status bar padding - immersive mode hides status bar
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 72.dp) // Space for bottom navigation
                        ) {
                            // Header with dynamic sky - scrollable, extends behind toolbar
                            item {
                                val skyPeriod = getCurrentSkyPeriodForTheme()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp) // Taller header for full-screen immersive experience
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
                                    android.util.Log.d("DuaHeader", "🎨 GRADIENT BOX RENDERING - dua.title='${dua.title}' arabicText.length=${dua.arabicText.length}")

                                    // Header content positioned at bottom to show more sky artwork
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp)
                                            .padding(top = 100.dp, bottom = 4.dp), // Position content at very bottom
                                        verticalArrangement = Arrangement.Bottom,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        android.util.Log.d("DuaHeader", "📦 HEADER COLUMN RENDERING")

                                        // Dynamic header: "Quranic Dua" for Quran-based duas, otherwise show category or "Dua"
                                        val isQuranicDua = dua.surahNumber > 0 && dua.ayahNumber > 0
                                        val headerText = if (isQuranicDua) {
                                            "Quranic Dua"
                                        } else {
                                            "Dua" // For fortress_of_the_muslim and other duas
                                        }

                                        Text(
                                            text = headerText,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Dua theme/title - clean up various title formats
                                        // Filter out Arabic characters to prevent Arabic text from appearing in header
                                        val duaTheme = dua.title
                                            .replace(Regex("Quranic Dua \\d+:\\s*"), "")
                                            .replace(Regex("Dua #\\d+:\\s*"), "")
                                            .replace(Regex("Dua \\d+:\\s*"), "")
                                            .replace(Regex("\\s*\\(\\d+:\\d+\\)\\s*$"), "")
                                            .replace(Regex("\\s*\\(\\d+/\\d+\\)\\s*$"), "") // Handle (1/3), (2/3) format
                                            .trim()
                                            .let { theme ->
                                                // Remove any Arabic characters (Unicode range 0600-06FF and 0750-077F)
                                                if (theme.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }) {
                                                    "" // Don't show Arabic in header - it should be in the Arabic card below
                                                } else {
                                                    theme
                                                }
                                            }

                                        // Show theme for all duas that have a meaningful English title
                                        if (duaTheme.isNotEmpty()) {
                                            Text(
                                                text = duaTheme,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                        }

                                        // Topic chips and Surah pill in a single row - using NiaTopicTag for consistency
                                        val hasTopics = topics.isNotEmpty()
                                        val hasSurahRef = dua.surahNumber > 0 && dua.ayahNumber > 0

                                        if (hasTopics || hasSurahRef) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Topic chips
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

                                                // Surah/Ayah navigation chip - lookup surah name from database
                                                if (hasSurahRef) {
                                                    // Look up surah name from database
                                                    var surahName by remember { mutableStateOf(dua.surahName.ifEmpty { "Surah ${dua.surahNumber}" }) }

                                                    LaunchedEffect(dua.surahNumber) {
                                                        if (dua.surahName.isEmpty()) {
                                                            surahName = viewModel.getSurahName(dua.surahNumber)
                                                        }
                                                    }

                                                    val surahDisplayName = "$surahName #${dua.ayahNumber}"

                                                    NiaTopicTag(
                                                        followed = false,
                                                        onClick = { onNavigateToSurah?.invoke(dua.surahNumber, dua.ayahNumber) },
                                                        text = {
                                                            Text(
                                                                text = surahDisplayName.uppercase(Locale.getDefault())
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                }
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

                        // Arabic Text Card - Cream/beige background, centered text
                        if (dua.arabicText.isNotEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFF8F5F0), // Cream/beige background
                                    tonalElevation = 0.dp,
                                    shadowElevation = 1.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 40.dp),
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
                                                    color = Color(0xFF3D3D3D) // Dark gray text
                                                )
                                            )
                                            Text(
                                                text = annotatedText,
                                                fontFamily = arabicFontFamily,
                                                fontSize = 32.sp,
                                                lineHeight = 54.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            Text(
                                                text = dua.arabicText,
                                                fontFamily = arabicFontFamily,
                                                fontSize = 32.sp,
                                                lineHeight = 54.sp,
                                                textAlign = TextAlign.Center,
                                                color = Color(0xFF3D3D3D) // Dark gray text
                                            )
                                        }
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
                                        color = Color(0xFF5D5D5D) // Medium gray
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
                                        color = Color(0xFF5D5D5D) // Medium gray
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

            // Bottom indicator - Clean "Dua X of Y" design matching screenshot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFFFAF8F5)) // Light cream background
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left line
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0xFFD0D0D0))
                    )

                    // Center text: "Dua X of Y"
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dua ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF9E9E9E)
                        )
                        Text(
                            text = "${currentPage + 1}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3D3D3D)
                        )
                        Text(
                            text = " of $totalDuas",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF9E9E9E)
                        )
                    }

                    // Right line
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0xFFD0D0D0))
                    )
                }
            }

            // Fixed toolbar at top - transparent to show sky through
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding() // Account for status bar/punch hole
                    .padding(top = 56.dp) // Extra top padding to clear camera punch hole and show more sky
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
            // Arabic Text Card - Cream background
            if (parsedContent.arabicText.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8F5F0), // Cream/beige background
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = parsedContent.arabicText,
                            fontFamily = arabicFontFamily,
                            fontSize = 32.sp,
                            lineHeight = 54.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF3D3D3D)
                        )
                    }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding() // Account for status bar/punch hole
                .padding(top = 56.dp) // Extra top padding to match main toolbar
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
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
