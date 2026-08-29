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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.feature.surah.QuranFonts
import com.starception.submission.util.toLocalizedDigits
import kotlinx.coroutines.delay
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
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import java.util.Locale
import com.starception.submission.core.topicsdatabase.Topic
import com.starception.submission.core.topicsdatabase.TopicsDatabase
import com.starception.submission.core.topicsdatabase.toTopic
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.core.duadatabase.DuaDatabase
import com.starception.submission.core.duadatabase.HadithReference
import com.starception.submission.core.duadatabase.toHadithReference
import com.starception.submission.core.qurandatabase.QuranDatabase
import com.starception.submission.core.ui.ChapterAudioController
import com.starception.submission.core.ui.DynamicSkyHeader
import com.starception.submission.core.ui.ImmersiveFullScreenEffect
import com.starception.submission.core.ui.getCurrentSkyPeriodForTheme
import com.starception.submission.core.ui.getSkyColors
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.settings.components.TtsVoiceSelectionSheet
import com.starception.submission.settings.components.isTtsVoiceModelAvailable
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalDensity
import com.starception.submission.R

private const val DUA_SECTION_ORDER_PREFS = "dua_section_order_prefs"
private const val DUA_SECTION_ORDER_KEY = "section_order"

private enum class DuaToolbarPicker {
    Translation,
    ArabicFont,
}

/**
 * Save dua section order to SharedPreferences
 */
private fun saveDuaSectionOrder(context: android.content.Context, order: List<DuaSection>) {
    val prefs = context.getSharedPreferences(DUA_SECTION_ORDER_PREFS, android.content.Context.MODE_PRIVATE)
    prefs.edit().putString(DUA_SECTION_ORDER_KEY, order.joinToString(",") { it.name }).apply()
}

/**
 * Load dua section order from SharedPreferences
 */
private fun loadDuaSectionOrder(context: android.content.Context): List<DuaSection>? {
    val prefs = context.getSharedPreferences(DUA_SECTION_ORDER_PREFS, android.content.Context.MODE_PRIVATE)
    val orderString = prefs.getString(DUA_SECTION_ORDER_KEY, null) ?: return null
    return try {
        orderString.split(",").mapNotNull { name ->
            try { DuaSection.valueOf(name) } catch (e: Exception) { null }
        }
    } catch (e: Exception) {
        null
    }
}

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

                    // Use Room-managed news.db (populated via regenerateFromSources at startup)
                    val roomDbPath = context.getDatabasePath("news.db")
                    if (!roomDbPath.exists()) {
                        android.util.Log.w("DuaDetailViewModel", "Room news.db not found, triggering regeneration")
                        com.starception.submission.core.contentdatabase.NewsDatabase.regenerateFromSources(context)
                    }

                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        roomDbPath.absolutePath,
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

                    // Sort by news-resource id, NOT duaNumber. For Fortress duas duaNumber is the
                    // position WITHIN a chapter (parsed from "{Chapter}: Dua N"), so sorting a
                    // multi-chapter list by it interleaves chapters ([every ch's Dua 1, every ch's
                    // Dua 2, …]) — which made auto-advance hop between chapters mid-way. News ids
                    // are assigned in (chapter, position) order by NewsDbGenerator, so id order is
                    // the correct reading order that keeps each chapter's duas contiguous.
                    duas.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
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

                    // Use Room-managed news.db (populated via regenerateFromSources at startup)
                    val roomDbPath = context.getDatabasePath("news.db")
                    if (!roomDbPath.exists()) {
                        android.util.Log.w("DuaDetailViewModel", "Room news.db not found, triggering regeneration")
                        com.starception.submission.core.contentdatabase.NewsDatabase.regenerateFromSources(context)
                    }

                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        roomDbPath.absolutePath,
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

                    // Sort by news-resource id (chapter→position order), not duaNumber — see the
                    // note in loadDuasByTopic. duaNumber is a per-chapter position, so sorting the
                    // whole book by it interleaves chapters and scrambles auto-advance order.
                    duas.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
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
 * Dense RTL paragraph styling for long Arabic duas. The paragraph line breaker keeps
 * justification even, and trimming the font metrics removes oversized top/bottom gaps.
 */
private fun duaArabicReadingStyle(
    fontFamily: FontFamily,
    fontSize: Float,
): androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle(
    fontFamily = fontFamily,
    fontSize = fontSize.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = (fontSize * 1.55f).sp,
    letterSpacing = 0.sp,
    textAlign = TextAlign.Justify,
    textDirection = androidx.compose.ui.text.style.TextDirection.Rtl,
    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
    ),
)

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
    // Don't restore on dispose to prevent status bar flash when navigating to surah detail
    ImmersiveFullScreenEffect(restoreOnDispose = false)

    // Create wrapped back click that restores status bar first
    val view = androidx.compose.ui.platform.LocalView.current
    val wrappedOnBackClick: () -> Unit = {
        val window = (view.context as? android.app.Activity)?.window
        val insetsController = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view)
        }
        // Restore status bar before navigating back
        insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        onBackClick()
    }

    val context = LocalContext.current
    val viewModel = remember { DuaDetailViewModel(context) }
    val ttsEntryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SherpaOnnxTtsEntryPoint::class.java,
        )
    }
    val sherpaOnnxTts = remember { ttsEntryPoint.sherpaOnnxTtsService() }
    val ttsPrefs = remember {
        context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
    }
    var selectedVoiceName by remember {
        mutableStateOf(
            ttsPrefs.getString("selected_voice", TtsVoice.KOKORO_EN.name)
                ?: TtsVoice.KOKORO_EN.name,
        )
    }
    var selectedSpeakerId by remember {
        mutableStateOf(ttsPrefs.getInt("selected_speaker_id", 0))
    }
    val selectedVoice = remember(selectedVoiceName) {
        runCatching { TtsVoice.valueOf(selectedVoiceName) }
            .getOrDefault(TtsVoice.KOKORO_EN)
    }
    val selectedFont by viewModel.selectedArabicFont.collectAsState()
    val selectedTranslation by viewModel.selectedTranslation.collectAsState()
    val showTajweed by viewModel.showTajweed.collectAsState()
    val arabicFontFamily = getArabicFontFamilyForDua(selectedFont)
    val scope = rememberCoroutineScope()

    // Per-dua recitation audio, via the shared process-wide ChapterAudioController — same player
    // the news cards use, so it downloads-and-caches from the CDN and drives the global media
    // mini-bar/notification. The route title is "{Chapter}: Dua N", so we parse the chapter title
    // (before the colon) and the dua position (after "Dua "). Prefer the DUA's own clip; fall back
    // to the whole-chapter recitation only if a dua has no per-dua audio.
    var chapterAudioUrl by remember(title) { mutableStateOf<String?>(null) }
    LaunchedEffect(title) {
        // Chapter titles can contain colons — split on the ": Dua N" suffix instead.
        val chTitle = if (title.contains(": Dua ")) {
            title.substringBeforeLast(": Dua ").trim()
        } else {
            title.substringBefore(":").trim()
        }
        val position = title.substringAfterLast(": Dua ", "").trim().toIntOrNull()
        chapterAudioUrl = withContext(Dispatchers.IO) {
            runCatching {
                val dao = DuaDatabase.getInstance(context).duaDao()
                val perDua = if (position != null) dao.getDuaAudioByTitleAndPosition(chTitle, position) else null
                perDua ?: dao.getChapterAudioByTitle(chTitle)
            }.getOrNull()
        }
    }

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Topics state for displaying in header
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }

    // Topics are loaded further down, keyed on the dua actually shown rather than on the
    // route's newsResourceId, which is empty for any caller that has no real news id.
    // Keying on the visible dua also keeps the tag correct while paging.

    // Toolbar language/font icons open focused modal sheets rather than popups.
    var toolbarPicker by remember { mutableStateOf<DuaToolbarPicker?>(null) }
    var showFloatingToolbar by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }

    // Font size state for toolbar controls
    var arabicFontSize by remember { mutableStateOf(32f) }
    val minFontSize = 20f
    val maxFontSize = 48f

    // Toolbar collapse progress state (0 = transparent, 1 = solid background)
    // This is updated from inside the pager based on scroll position
    var toolbarCollapseProgress by remember { mutableFloatStateOf(0f) }

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

    // Fortress routes pass title = "{Chapter}: Dua N" and no news id, so this exact match
    // is what selects the right page. It is tried before id-match because a Fortress
    // position and a Quranic dua id occupy the same small number range.
    val isFortressTitle = title.contains(": Dua ")

    // Find page index by news resource ID first (works for fortress_of_the_muslim duas)
    // and Quranic Duas; Fortress routes match by title.
    val initialPageIndex = remember(duasList, initialDuaNumber, initialNewsResourceId, title) {
        if (duasList.isNotEmpty()) {
            // Fortress: match by exact title (e.g. "Before sleeping: Dua 3")
            if (isFortressTitle) {
                val indexByTitle = duasList.indexOfFirst { it.title == title }
                if (indexByTitle >= 0) {
                    return@remember indexByTitle
                }
            }
            // Otherwise try news resource ID (Quranic Duas + topic-filtered lists)
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

    // The pager is constructed before duasList arrives, so its initial page is a guess from
    // the dua number. Until the real index has been applied, the "current" dua is whatever
    // that guess landed on — reading a tag or bookmark from it shows another dua's data for
    // a frame or two. Gate anything derived from the visible page on this.
    var initialPageResolved by remember(title, duasList) { mutableStateOf(false) }

    // Also scroll when duas load (in case index needs adjustment based on ID or duaNumber field)
    LaunchedEffect(duasList, initialNewsResourceId, title) {
        if (duasList.isNotEmpty()) {
            // Fortress: title match wins so we don't fall into a Quranic Dua id collision
            val indexByTitle = if (isFortressTitle) {
                duasList.indexOfFirst { it.title == title }.takeIf { it >= 0 }
            } else null
            // Otherwise: news resource ID (Quranic Duas + topic-filtered lists)
            val indexById = if (initialNewsResourceId.isNotEmpty()) {
                duasList.indexOfFirst { it.id == initialNewsResourceId }.takeIf { it >= 0 }
            } else null
            // Fall back to dua number matching
            val indexByNumber = duasList.indexOfFirst { it.duaNumber == initialDuaNumber }.takeIf { it >= 0 }
            val targetIndex = indexByTitle ?: indexById ?: indexByNumber
                ?: targetPageIndex.coerceIn(0, duasList.size - 1)
            // A Fortress request that resolves by number instead of title has landed on
            // whichever dua shares that number — in practice a Quranic one, because those
            // sort first. Record which rule won so a wrong page is traceable.
            android.util.Log.d(
                "DuaResolve",
                "title='$title' fortress=$isFortressTitle byTitle=$indexByTitle byId=$indexById " +
                    "byNumber=$indexByNumber -> index=$targetIndex ('${duasList.getOrNull(targetIndex)?.title}')",
            )
            if (pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
            initialPageResolved = true
        }
    }

    // Tag the page with the topics of the dua on screen. The id comes from the list entry
    // itself, so it is the real news id for both Quranic and Fortress duas.
    val visibleDuaId = if (initialPageResolved) {
        duasList.getOrNull(pagerState.currentPage)?.id
    } else {
        null
    }
    LaunchedEffect(visibleDuaId) {
        val newsId = visibleDuaId?.toIntOrNull()
        if (newsId == null) {
            topics = emptyList()
            return@LaunchedEffect
        }
        try {
            val newsDao = NewsDatabase.getInstance(context).newsDao()
            val topicIds = newsDao.getTopicIdsForNews(newsId)
            // Clear rather than keep the previous dua's tags when this one has none.
            topics = if (topicIds.isEmpty()) {
                emptyList()
            } else {
                TopicsDatabase.getInstance(context).topicsDao()
                    .getTopicsByIds(topicIds)
                    .map { it.toTopic() }
            }
            android.util.Log.d(
                "DuaDetailScreen",
                "📚 Topics for news $newsId ('${duasList.getOrNull(pagerState.currentPage)?.title}'): " +
                    topics.joinToString { it.name },
            )
        } catch (e: Exception) {
            android.util.Log.e("DuaDetailScreen", "❌ Error loading topics: ${e.message}")
        }
    }

    // Publish this list's ordered dua titles so the app-level continuous playback can keep
    // advancing through the topic in the background (and even after leaving this screen).
    LaunchedEffect(duasList) {
        ChapterAudioController.playlistTitles = duasList.map { it.title }
        android.util.Log.d("DuaAutoPlay", "playlist set | size=${duasList.size} first='${duasList.firstOrNull()?.title}'")
    }

    // Follow app-level continuous playback visually: when the background auto-advance
    // moves to the next dua (updating the controller's observable current title), animate
    // the pager to that dua so an open screen stays in sync. Playback itself — and its
    // background continuation — is owned by the app, not this screen.
    LaunchedEffect(ChapterAudioController.currentTitle, duasList) {
        val playingTitle = ChapterAudioController.currentTitle
        if (playingTitle != null && duasList.isNotEmpty()) {
            val idx = duasList.indexOfFirst { it.title == playingTitle }
            if (idx >= 0 && idx != pagerState.currentPage) {
                pagerState.animateScrollToPage(idx)
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

    // Keyed on the dua being shown, not the route's id: a route reached without a news id
    // resolves by title, so the visible dua is the only reliable source of one. Falls back
    // to the route id for callers that did pass a real one.
    LaunchedEffect(visibleDuaId, initialNewsResourceId) {
        val id = visibleDuaId ?: initialNewsResourceId
        if (id.isNotEmpty()) {
            isBookmarked = isNiaBookmarked(id)
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,  // Solid background to prevent sky showing through
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // No padding for status bar in immersive mode
    ) { _ ->
        // Don't apply paddingValues - let content scroll under transparent toolbar like SurahDetailScreen
        Box(modifier = Modifier.fillMaxSize()) {
            android.util.Log.d("DuaScreen", "📦 BOX: duasList.isEmpty=${duasList.isEmpty()}")

            // Show simple loading state while data loads
            val hasContent = content.isNotBlank()
            if (duasList.isEmpty() && !hasContent) {
                android.util.Log.d("DuaScreen", "⏳ SHOWING LOADING (no content)")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Pager for duas - with custom drag gesture and edge indicators (no sliding)
            if (duasList.isNotEmpty()) {
                android.util.Log.d("DuaScreen", "📖 SHOWING PAGER with ${duasList.size} duas")

                // Custom swipe state
                var swipeOffsetX by remember { mutableStateOf(0f) }
                var touchY by remember { mutableStateOf(0f) }
                val swipeThreshold = 300f
                val coroutineScope = rememberCoroutineScope()

                val swipeProgress = (kotlin.math.abs(swipeOffsetX) / swipeThreshold).coerceIn(0f, 1f)
                val isSwipingRight = swipeOffsetX > 0f
                val isSwipingLeft = swipeOffsetX < 0f

                val showLeftIndicator = isSwipingRight && hasPrevious
                val showRightIndicator = isSwipingLeft && hasNext
                val targetProgress = if (showLeftIndicator || showRightIndicator) swipeProgress else 0f

                val duaAnimatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh),
                    label = "duaSwipeProgress",
                )

                val density = LocalDensity.current

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentPage) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    swipeOffsetX = 0f
                                    touchY = offset.y
                                },
                                onDragEnd = {
                                    when {
                                        swipeOffsetX > swipeThreshold && hasPrevious -> {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(currentPage - 1)
                                            }
                                        }
                                        swipeOffsetX < -swipeThreshold && hasNext -> {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(currentPage + 1)
                                            }
                                        }
                                    }
                                    swipeOffsetX = 0f
                                },
                                onDragCancel = { swipeOffsetX = 0f },
                                onDrag = { change, dragAmount ->
                                    swipeOffsetX += dragAmount.x
                                    touchY = change.position.y
                                    if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) * 1.5f) {
                                        change.consume()
                                    }
                                }
                            )
                        }
                ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false
                ) { page ->
                    val dua = duasList[page]

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Track scroll state for collapsing toolbar effect
                        val lazyListState = rememberLazyListState()

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

                        // Toolbar always shows solid surface background regardless of scroll position
                        LaunchedEffect(Unit) {
                            toolbarCollapseProgress = 1f
                        }

                        // Build list of available sections based on dua data
                        val availableSections = remember(dua) {
                            mutableListOf<DuaSection>().apply {
                                if (dua.context.isNotEmpty()) add(DuaSection.CONTEXT)
                                if (dua.arabicText.isNotEmpty()) add(DuaSection.ARABIC)
                                if (dua.instruction.isNotEmpty()) add(DuaSection.INSTRUCTION)
                                if (dua.translation.isNotEmpty()) add(DuaSection.TRANSLATION)
                                if (dua.transliteration.isNotEmpty()) add(DuaSection.TRANSLITERATION)
                                if (dua.explanation.isNotEmpty()) add(DuaSection.EXPLANATION)
                                if (dua.note.isNotEmpty()) add(DuaSection.NOTE)
                                if (dua.postContext.isNotEmpty()) add(DuaSection.POST_CONTEXT)
                                if (dua.reference.isNotEmpty()) add(DuaSection.REFERENCE)
                            }.toList()
                        }

                        // Load saved order and apply it to available sections
                        val initialSections = remember(availableSections) {
                            val savedOrder = loadDuaSectionOrder(context)
                            if (savedOrder != null) {
                                // Reorder available sections based on saved order
                                val ordered = mutableListOf<DuaSection>()
                                savedOrder.forEach { section ->
                                    if (section in availableSections) ordered.add(section)
                                }
                                // Add any new sections not in saved order
                                availableSections.forEach { section ->
                                    if (section !in ordered) ordered.add(section)
                                }
                                ordered
                            } else {
                                availableSections
                            }
                        }

                        // Local mutable list for smooth drag reordering
                        val localSections = remember(initialSections) { initialSections.toMutableStateList() }

                        // Track if reordering happened to save on drag end
                        var wasReordered by remember { mutableStateOf(false) }

                        // State for loaded hadith references (needed for REFERENCE section)
                        var hadithReferences by remember { mutableStateOf<List<HadithReference>>(emptyList()) }

                        // Load hadith references by parsing title to get chapter and position
                        LaunchedEffect(dua.title) {
                            try {
                                val titleParts = dua.title.split(": Dua ")
                                if (titleParts.size == 2) {
                                    val chapterTitle = titleParts[0].trim()
                                    val position = titleParts[1].trim().toIntOrNull() ?: 1
                                    val duaDb = DuaDatabase.getInstance(context)
                                    val refs = duaDb.duaDao().getHadithReferencesByChapterAndPosition(chapterTitle, position)
                                    hadithReferences = refs.map { it.toHadithReference() }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DuaDetailScreen", "Error loading hadith references", e)
                            }
                        }

                        // Reorderable state for the LazyColumn
                        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                            // Only reorder if both indices are in the sections range (after header + spacer)
                            val headerOffset = 2 // 1 header item + 1 spacer before sections
                            val fromSectionIndex = from.index - headerOffset
                            val toSectionIndex = to.index - headerOffset
                            if (fromSectionIndex >= 0 && toSectionIndex >= 0 &&
                                fromSectionIndex < localSections.size && toSectionIndex < localSections.size) {
                                localSections.apply {
                                    add(toSectionIndex, removeAt(fromSectionIndex))
                                }
                                wasReordered = true
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
                                                        Modifier.height(160.dp)
                                                    } else {
                                                        Modifier.aspectRatio(4f / 3f)
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
                                                                    Text(
                                                                        text = "$surahName:${dua.ayahNumber.toLocalizedDigits(selectedTranslation)}"
                                                                            .uppercase(Locale.getDefault()),
                                                                    )
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

                        // Reorderable content sections - each section is a separate item
                        items(localSections, key = { "section_${it.name}" }) { section ->
                            ReorderableItem(reorderableLazyListState, key = "section_${section.name}") { isDragging ->
                                // Shared callback to save order when drag ends
                                val onDragStopped: () -> Unit = {
                                    if (wasReordered) {
                                        saveDuaSectionOrder(context, localSections.toList())
                                        wasReordered = false
                                    }
                                }

                                when (section) {
                                    DuaSection.CONTEXT -> {
                                        CollapsibleDuaSection(
                                            title = "Context",
                                            accentColor = Color(0xFF4CAF50),
                                            initiallyExpanded = true,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                    DuaSection.ARABIC -> {
                                        CollapsibleDuaSection(
                                            title = "Arabic (Original)",
                                            accentColor = MaterialTheme.colorScheme.primary,
                                            initiallyExpanded = true,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                                    style = duaArabicReadingStyle(
                                                        fontFamily = arabicFontFamily,
                                                        fontSize = 32f,
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                Text(
                                                    text = dua.arabicText,
                                                    style = duaArabicReadingStyle(
                                                        fontFamily = arabicFontFamily,
                                                        fontSize = 32f,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                    DuaSection.INSTRUCTION -> {
                                        CollapsibleDuaSection(
                                            title = "Instruction",
                                            accentColor = Color(0xFFE91E63),
                                            initiallyExpanded = true,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                    DuaSection.TRANSLATION -> {
                                        CollapsibleDuaSection(
                                            title = "Translation ($translationDisplayName)",
                                            accentColor = Color(0xFF9E9E9E),
                                            initiallyExpanded = true,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                    DuaSection.TRANSLITERATION -> {
                                        CollapsibleDuaSection(
                                            title = "Transliteration",
                                            accentColor = Color(0xFF9E9E9E),
                                            initiallyExpanded = true,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                    DuaSection.EXPLANATION -> {
                                        CollapsibleDuaSection(
                                            title = "Explanation",
                                            accentColor = Color(0xFF8BC34A),
                                            initiallyExpanded = false,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                    DuaSection.NOTE -> {
                                        CollapsibleDuaSection(
                                            title = "Note",
                                            accentColor = Color(0xFFFF9800),
                                            initiallyExpanded = false,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                    DuaSection.POST_CONTEXT -> {
                                        CollapsibleDuaSection(
                                            title = "Additional Context",
                                            accentColor = Color(0xFF2196F3),
                                            initiallyExpanded = false,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
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
                                    DuaSection.REFERENCE -> {
                                        CollapsibleDuaSection(
                                            title = "Reference",
                                            accentColor = Color(0xFF9C27B0),
                                            initiallyExpanded = false,
                                            showDragHandle = true,
                                            isDragging = isDragging,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                                            modifier = Modifier.longPressDraggableHandle(onDragStopped = onDragStopped)
                                        ) {
                                            Column {
                                                if (dua.reference.isNotEmpty()) {
                                                    Text(
                                                        text = dua.reference,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontSize = 13.sp,
                                                            lineHeight = 20.sp
                                                        ),
                                                        color = Color(0xFF757575)
                                                    )
                                                }

                                                if (hadithReferences.isNotEmpty() && onHadithClick != null) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        hadithReferences.forEach { ref ->
                                                            val refText = buildString {
                                                                append(ref.collectionName ?: "Hadith")
                                                                if (ref.hadithNumber != null) {
                                                                    append(":${ref.hadithNumber}")
                                                                }
                                                            }
                                                            NiaTopicTag(
                                                                followed = false,
                                                                onClick = {
                                                                    if (ref.databaseFile != null && ref.hadithNumber != null) {
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

                                                if (topics.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        topics.take(2).forEach { topic ->
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
                        }

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }


                        // Floating play button at the banner/content boundary — same
                        // affordance as the Surah and Hadith detail pages. Rides up
                        // with the parallax header and hides once the header scrolls
                        // away. Uses this PAGE's dua audio (per-dua clip, falling back
                        // to the whole-chapter recitation).
                        var pageAudioUrl by remember(dua.title) { mutableStateOf<String?>(null) }
                        LaunchedEffect(dua.title) {
                            val chTitle = if (dua.title.contains(": Dua ")) {
                                dua.title.substringBeforeLast(": Dua ").trim()
                            } else {
                                dua.title.substringBefore(":").trim()
                            }
                            val position = dua.title.substringAfterLast(": Dua ", "").trim().toIntOrNull()
                            pageAudioUrl = withContext(Dispatchers.IO) {
                                runCatching {
                                    val dao = DuaDatabase.getInstance(context).duaDao()
                                    val perDua = if (position != null) {
                                        dao.getDuaAudioByTitleAndPosition(chTitle, position)
                                    } else {
                                        null
                                    }
                                    perDua ?: dao.getChapterAudioByTitle(chTitle)
                                }.getOrNull()
                            }
                        }
                        pageAudioUrl?.let { audioUrl ->
                            // Anchor to the REAL banner-image height: the header is
                            // aspectRatio(4:3) in portrait (width * 3/4) and 160dp in
                            // landscape — see the parallax header item above. The FAB
                            // stays PINNED at the boundary (like Surah/Hadith) and
                            // show/hide follows scroll direction with a gentle
                            // scale+fade, matching the Surah page exactly.
                            val headerHeight = if (isLandscape) {
                                160.dp
                            } else {
                                configuration.screenWidthDp.dp * 3f / 4f
                            }
                            var fabVisible by remember { mutableStateOf(true) }
                            var prevIndex by remember { mutableStateOf(0) }
                            var prevOffset by remember { mutableStateOf(0) }
                            LaunchedEffect(
                                lazyListState.firstVisibleItemIndex,
                                lazyListState.firstVisibleItemScrollOffset,
                            ) {
                                val index = lazyListState.firstVisibleItemIndex
                                val offset = lazyListState.firstVisibleItemScrollOffset
                                val delta = (index * 1000 + offset) - (prevIndex * 1000 + prevOffset)
                                if (kotlin.math.abs(delta) > 10) {
                                    fabVisible = when {
                                        // At the very top: always show.
                                        index == 0 && offset < 100 -> true
                                        // Header fully scrolled off: keep hidden (the FAB
                                        // rides off with the banner; don't flash it back in
                                        // on an upward flick deep in the list).
                                        index > 0 -> false
                                        // Within the header: scrolling up shows, down hides.
                                        else -> delta < 0
                                    }
                                    prevIndex = index
                                    prevOffset = offset
                                }
                            }
                            AnimatedVisibility(
                                visible = fabVisible,
                                enter = scaleIn(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                                exit = scaleOut(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                ) + fadeOut(animationSpec = tween(durationMillis = 300)),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    // Ride up 1:1 with the scroll like the Surah page's FAB
                                    // (which lives inside the scrolling content): stay pinned
                                    // to the banner's bottom edge and track how far the header
                                    // (item 0) has scrolled up, so it moves with the page and
                                    // then scrolls away — instead of just vanishing in place.
                                    // Read in the layout phase (offset lambda) so scrolling
                                    // never triggers recomposition.
                                    .offset {
                                        val scrolled = if (lazyListState.firstVisibleItemIndex == 0) {
                                            lazyListState.firstVisibleItemScrollOffset
                                        } else {
                                            // Header fully scrolled off — carry the FAB off
                                            // the top edge with it.
                                            headerHeight.toPx().toInt()
                                        }
                                        IntOffset(
                                            x = (-12).dp.toPx().roundToInt(),
                                            y = (headerHeight.toPx() - 28.dp.toPx() - scrolled).roundToInt(),
                                        )
                                    },
                            ) {
                                val isThisPlaying = ChapterAudioController.currentUrl == audioUrl &&
                                    ChapterAudioController.isPlaying
                                val isThisLoading = ChapterAudioController.loadingUrl == audioUrl
                                FloatingActionButton(
                                    onClick = {
                                        ChapterAudioController.currentTitle = dua.title
                                        // Feed the media bar's subtitle the Interests topic
                                        // this dua belongs to (first chip on the page).
                                        ChapterAudioController.currentTopic = topics.firstOrNull()?.name
                                        ChapterAudioController.toggle(audioUrl)
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    if (isThisLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isThisPlaying) "Pause recitation" else "Play recitation",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Edge indicators using custom swipe state
                val touchYDp = with(density) { touchY.toDp() }
                val baseHeight = 72f
                val targetSize = 46f
                val indicatorHeight = (baseHeight - (baseHeight - targetSize) * duaAnimatedProgress).dp
                val verticalOffset = touchYDp - (indicatorHeight / 2)

                val thresholdReachedLeft = swipeProgress >= 1f && showLeftIndicator
                val thresholdReachedRight = swipeProgress >= 1f && showRightIndicator
                val detachOffset = 8.dp

                if (duaAnimatedProgress > 0.01f && showLeftIndicator) {
                    DuaSwipeArrowIndicator(
                        progress = duaAnimatedProgress,
                        thresholdReached = thresholdReachedLeft,
                        isLeftEdge = true,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = if (thresholdReachedLeft) detachOffset else 0.dp,
                                y = verticalOffset
                            ),
                    )
                }

                if (duaAnimatedProgress > 0.01f && showRightIndicator) {
                    DuaSwipeArrowIndicator(
                        progress = duaAnimatedProgress,
                        thresholdReached = thresholdReachedRight,
                        isLeftEdge = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(
                                x = if (thresholdReachedRight) -detachOffset else 0.dp,
                                y = verticalOffset
                            ),
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
                    enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
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
                                                    text = (page + 1).toLocalizedDigits(selectedTranslation),
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
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
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
                                            text = (pageIndex + 1).toLocalizedDigits(selectedTranslation),
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

            // Reading settings uses the same floating, theme-aware modal frame as
            // Course and Hadith sheets. The system modal owns motion, scrim, back,
            // and swipe dismissal; the visible frame intentionally has no handle
            // or close icon.
            if (showFloatingToolbar) {
                val haptics = LocalHapticFeedback.current
                // Which inline picker is expanded: "font", "language", or null.
                var expandedSection by remember { mutableStateOf<String?>(null) }
                ModalBottomSheet(
                    onDismissRequest = { showFloatingToolbar = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = NiaBottomSheetDefaults.FloatingShape,
                    containerColor = Color.Transparent,
                    contentColor = NiaBottomSheetDefaults.contentColor(),
                    scrimColor = NiaBottomSheetDefaults.scrimColor(),
                    tonalElevation = 0.dp,
                    dragHandle = null,
                    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                ) {
                    NiaBottomSheetTheme {
                        NiaBottomSheetFrame {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(top = 18.dp, bottom = 8.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = "Reading settings",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                                Text(
                                    text = "Preview and tune your dua reading experience",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                                )

                        // Live preview — the actual dua text, directly on the sheet.
                        Text(
                            text = duasList.getOrNull(currentPage)?.arabicText
                                ?: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                            fontFamily = arabicFontFamily,
                            fontSize = arabicFontSize.sp,
                            lineHeight = (arabicFontSize * 1.7f).sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        )

                        // Text size — small A, slider, big A (Play Books style).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "A",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = arabicFontSize,
                                onValueChange = { newValue ->
                                    val stepped = newValue.toInt()
                                    if (stepped != arabicFontSize.toInt()) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    arabicFontSize = newValue.coerceIn(minFontSize, maxFontSize)
                                },
                                valueRange = minFontSize..maxFontSize,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "A",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        )

                        // Tajweed — plain label + switch row.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleTajweed() }
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tajweed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = showTajweed,
                                onCheckedChange = { viewModel.toggleTajweed() },
                            )
                        }

                        // TTS narration — opens the same model/speaker picker used by
                        // Hadith details while keeping the reading controls in this sheet.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFloatingToolbar = false
                                    showVoiceSheet = true
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Narration voice",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${selectedVoice.displayName} · $selectedSpeakerId",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(12.dp),
                            )
                        }

                        // Arabic font — value + chevron row.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSection = if (expandedSection == "font") null else "font" }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Arabic font",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = when (selectedFont) {
                                    "pdms_saleem" -> "Saleem"
                                    "noor_e_hidayat" -> "Noor"
                                    "thabit" -> "Thabit"
                                    "uthmani_script" -> "Uthmani"
                                    "indopak_script" -> "IndoPak"
                                    else -> selectedFont.replaceFirstChar { it.uppercase() }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(12.dp),
                            )
                        }

                        AnimatedVisibility(
                            visible = expandedSection == "font",
                            enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                            exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
                        ) {
                            Column {
                                availableFonts.forEach { font ->
                                    val fontSelected = selectedFont == font
                                    // Selected row gets a soft tinted pill — no radio circles.
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (fontSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                viewModel.changeArabicFont(font)
                                                expandedSection = null
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = when (font) {
                                                "pdms_saleem" -> "Saleem"
                                                "noor_e_hidayat" -> "Noor"
                                                "thabit" -> "Thabit"
                                                "uthmani_script" -> "Uthmani"
                                                "indopak_script" -> "IndoPak"
                                                else -> font.replaceFirstChar { it.uppercase() }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (fontSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        // Live sample so the reader can see the script style.
                                        Text(
                                            text = "بِسْمِ اللَّهِ",
                                            fontFamily = getArabicFontFamilyForDua(font),
                                            fontSize = 18.sp,
                                            maxLines = 1,
                                            color = if (fontSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // Translation language — value + chevron row.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSection = if (expandedSection == "language") null else "language" }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Translation language",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = translationDisplayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(12.dp),
                            )
                        }

                        AnimatedVisibility(
                            visible = expandedSection == "language",
                            enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                            exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
                        ) {
                            Column {
                                availableTranslations.forEach { code ->
                                    val langSelected = selectedTranslation == code
                                    // Selected row gets a soft tinted pill — no radio circles.
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (langSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                viewModel.changeTranslation(code)
                                                expandedSection = null
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = viewModel.getTranslationName(code),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (langSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        // Endonym so native speakers recognize their language.
                                        Text(
                                            text = when (code) {
                                                "ar" -> "العربية"
                                                "transliteration" -> "Bismillāh"
                                                "bn" -> "বাংলা"
                                                "zh" -> "中文"
                                                "en" -> "English"
                                                "es" -> "Español"
                                                "fr" -> "Français"
                                                "id" -> "Bahasa Indonesia"
                                                "ru" -> "Русский"
                                                "sv" -> "Svenska"
                                                "tr" -> "Türkçe"
                                                "ur" -> "اردو"
                                                else -> ""
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            color = if (langSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Fixed toolbar at top - background transitions from transparent to solid on scroll
            // Smooth transition based on toolbarCollapseProgress (0 = transparent, 1 = solid)
            val toolbarBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = toolbarCollapseProgress)

            // Hide toolbar until status bar is gone (ImmersiveFullScreenEffect fires after first frame,
            // causing a brief window where both the status bar and toolbar are visible). Once the status
            // bar insets drop to zero (bar is hidden), fade the toolbar in.
            val statusBarVisible = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() > 0.dp
            val toolbarAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (statusBarVisible) 0f else 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "toolbarAlpha"
            )

            // Content color transitions from white (over image) to onSurface (over solid background)
            val surfaceColor = MaterialTheme.colorScheme.onSurface
            val toolbarContentColor = Color(
                red = 1f + (surfaceColor.red - 1f) * toolbarCollapseProgress,
                green = 1f + (surfaceColor.green - 1f) * toolbarCollapseProgress,
                blue = 1f + (surfaceColor.blue - 1f) * toolbarCollapseProgress,
                alpha = 1f
            )

            // Bookmark state hoisted above Box so it's accessible in both icon groups
                    val currentDuaNewsResourceId = if (duasList.isNotEmpty() && currentPage < duasList.size) {
                        duasList[currentPage].id
                    } else {
                        initialNewsResourceId
                    }
                    var localBookmarkState by remember(currentDuaNewsResourceId) {
                        mutableStateOf(isNiaBookmarked(currentDuaNewsResourceId))
                    }
                    val parentBookmarkState = isNiaBookmarked(currentDuaNewsResourceId)
                    LaunchedEffect(parentBookmarkState) {
                        localBookmarkState = parentBookmarkState
                    }

                    // Detect camera punch hole horizontal bounds to arrange icons around it
                    val toolbarView = LocalView.current
                    val toolbarDensity = LocalDensity.current
                    val cutoutLeft = remember(toolbarView) {
                        val cutout = toolbarView.rootWindowInsets?.displayCutout
                        if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                            with(toolbarDensity) { cutout.boundingRects.minOf { it.left }.toDp() }
                        } else 0.dp
                    }
                    val cutoutRight = remember(toolbarView) {
                        val cutout = toolbarView.rootWindowInsets?.displayCutout
                        if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                            with(toolbarDensity) { cutout.boundingRects.maxOf { it.right }.toDp() }
                        } else 0.dp
                    }
                    val hasCutout = cutoutLeft > 0.dp && cutoutRight > cutoutLeft

                    // Back button ends at 4dp(padding) + 40dp(button) + 8dp(gap) = 52dp
                    val backButtonEndDp = 52.dp
                    // Available width between back button and punch hole left edge
                    val leftZoneWidth = if (hasCutout && cutoutLeft > backButtonEndDp)
                        cutoutLeft - backButtonEndDp - 4.dp else 0.dp
                    // How many action icons fit left of the punch hole:
                    //   Language=40dp, +spacer8=48dp, +Font=40dp, +spacer4=92dp
                    val iconsInLeftZone = when {
                        leftZoneWidth >= 92.dp -> 2   // Language + Font both fit
                        leftZoneWidth >= 48.dp -> 1   // Only Language fits
                        else -> 0                      // All icons go to right group
                    }

            Surface(
                color = toolbarBackgroundColor,
                tonalElevation = (4 * toolbarCollapseProgress).dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .graphicsLayer { alpha = toolbarAlpha }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    // Back button — always at the far left
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = toolbarContentColor.copy(alpha = 0.15f)
                        ) {
                            IconButton(onClick = wrappedOnBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = toolbarContentColor
                                )
                            }
                        }
                    }

                    // Left icon group — icons that fit before the punch hole (after back button)
                    if (iconsInLeftZone > 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = backButtonEndDp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Language icon always first in left group
                            Surface(
                                onClick = { toolbarPicker = DuaToolbarPicker.Translation },
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = toolbarContentColor.copy(alpha = 0.12f),
                                contentColor = toolbarContentColor
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Translation",
                                        tint = toolbarContentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = translationCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = toolbarContentColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            // Font icon also in left group when space allows
                            if (iconsInLeftZone >= 2) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    onClick = { toolbarPicker = DuaToolbarPicker.ArabicFont },
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = toolbarContentColor.copy(alpha = 0.12f),
                                    contentColor = toolbarContentColor
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TextFormat,
                                            contentDescription = "Font selection",
                                            tint = toolbarContentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = fontDisplay,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = toolbarContentColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right icon group — always at screen right edge (safely past the punch hole)
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Language icon goes here when it doesn't fit left of punch hole
                        if (iconsInLeftZone == 0) {
                            Surface(
                                onClick = { toolbarPicker = DuaToolbarPicker.Translation },
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = toolbarContentColor.copy(alpha = 0.12f),
                                contentColor = toolbarContentColor
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Translation",
                                        tint = toolbarContentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = translationCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = toolbarContentColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        // Font icon goes here when only Language fit in the left zone
                        if (iconsInLeftZone <= 1) {
                            Surface(
                                onClick = { toolbarPicker = DuaToolbarPicker.ArabicFont },
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = toolbarContentColor.copy(alpha = 0.12f),
                                contentColor = toolbarContentColor
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFormat,
                                        contentDescription = "Font selection",
                                        tint = toolbarContentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = fontDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = toolbarContentColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        // Chapter recitation play/pause when audio is available. Uses the shared
                        // ChapterAudioController (same as news cards) so it downloads-and-caches
                        // from the CDN AND surfaces the global media mini-bar with progress.
                        chapterAudioUrl?.let { audioUrl ->
                            val isThisPlaying = ChapterAudioController.currentUrl == audioUrl &&
                                ChapterAudioController.isPlaying
                            val isThisLoading = ChapterAudioController.loadingUrl == audioUrl
                            IconButton(onClick = {
                                ChapterAudioController.currentTitle = title
                                ChapterAudioController.currentTopic = topics.firstOrNull()?.name
                                ChapterAudioController.toggle(audioUrl)
                            }) {
                                if (isThisLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = toolbarContentColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isThisPlaying) "Pause recitation" else "Play chapter recitation",
                                        tint = toolbarContentColor
                                    )
                                }
                            }
                        }
                        // Tajweed, Bookmark, MoreVert always in the right group
                        IconButton(onClick = { viewModel.toggleTajweed() }) {
                            Icon(
                                imageVector = if (showTajweed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                                contentDescription = if (showTajweed) "Disable Tajweed" else "Enable Tajweed",
                                tint = toolbarContentColor
                            )
                        }
                        IconButton(onClick = {
                            onToggleNiaBookmark(currentDuaNewsResourceId)
                            localBookmarkState = !localBookmarkState
                        }) {
                            Icon(
                                imageVector = if (localBookmarkState) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = if (localBookmarkState) "Remove Bookmark" else "Add Bookmark",
                                tint = toolbarContentColor
                            )
                        }
                        IconButton(
                            onClick = { showFloatingToolbar = !showFloatingToolbar },
                            modifier = Modifier.offset(x = (-8).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = toolbarContentColor
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVoiceSheet) {
        TtsVoiceSelectionSheet(
            selectedVoice = selectedVoice,
            selectedSpeakerId = selectedSpeakerId,
            supportingText = "Used for the spoken introduction before each dua recitation",
            ttsService = sherpaOnnxTts,
            isVoiceAvailable = { isTtsVoiceModelAvailable(context, it) },
            onVoiceSelected = { voice ->
                if (voice.name != selectedVoiceName) {
                    selectedVoiceName = voice.name
                    selectedSpeakerId = 0
                    ttsPrefs.edit()
                        .putString("selected_voice", voice.name)
                        .putInt("selected_speaker_id", 0)
                        .apply()
                    sherpaOnnxTts.clearCache()
                    sherpaOnnxTts.setVoice(voice)
                }
            },
            onSpeakerChanged = { speakerId ->
                selectedSpeakerId = speakerId
                ttsPrefs.edit().putInt("selected_speaker_id", speakerId).apply()
                sherpaOnnxTts.clearCache()
            },
            onDismiss = { showVoiceSheet = false },
        )
    }

    toolbarPicker?.let { picker ->
        DuaToolbarPickerSheet(
            picker = picker,
            currentTranslation = selectedTranslation,
            currentFont = selectedFont,
            availableTranslations = availableTranslations,
            availableFonts = availableFonts,
            getTranslationName = viewModel::getTranslationName,
            getFontName = viewModel::getArabicFontDisplayName,
            onTranslationSelected = { translation ->
                viewModel.changeTranslation(translation)
                toolbarPicker = null
            },
            onFontSelected = { font ->
                viewModel.changeArabicFont(font)
                toolbarPicker = null
            },
            onDismiss = { toolbarPicker = null },
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
                            style = duaArabicReadingStyle(
                                fontFamily = arabicFontFamily,
                                fontSize = 34f,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = dua.arabicText,
                            style = duaArabicReadingStyle(
                                fontFamily = arabicFontFamily,
                                fontSize = 34f,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth(),
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
 * Uses sh.calvin.reorderable library for smooth drag-and-drop UX
 */
@Composable
private fun CollapsibleDuaSection(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    showDragHandle: Boolean = false,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    // Background color for the section
    val sectionColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val sectionShape = RoundedCornerShape(12.dp)

    // Outer container - use Modifier.shadow with shape for proper rounded shadow
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(
                elevation = if (isDragging) 8.dp else 0.dp,
                shape = sectionShape,
                clip = false
            )
            .clip(sectionShape)
            .background(sectionColor)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            color = sectionColor,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent border - spans full height including drag handle
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(accentColor)
                    .then(
                        if (isExpanded) {
                            Modifier.height(androidx.compose.ui.unit.Dp.Unspecified)
                        } else {
                            Modifier.height(if (showDragHandle) 84.dp else 56.dp)
                        }
                    )
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Drag handle at top center - modifier applies reorderable drag behavior
                if (showDragHandle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragging)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = dragHandleModifier.size(24.dp)
                        )
                    }
                }

                // Header row - clickable to expand/collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = if (showDragHandle) 12.dp else 16.dp),
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
                        enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                        exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
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
}

/**
 * Section types for reorderable dua content
 */
private enum class DuaSection {
    CONTEXT,
    ARABIC,
    INSTRUCTION,
    TRANSLATION,
    TRANSLITERATION,
    EXPLANATION,
    NOTE,
    POST_CONTEXT,
    REFERENCE
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
        // Header with mosque image - matches the pager header style
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp) // Shorter for fallback (no toolbar overlap needed)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // Mosque image background only - no overlay text
            Image(
                painter = painterResource(R.drawable.masjid_al_nawabi),
                contentDescription = "Masjid al-Nawabi",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
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
                        style = duaArabicReadingStyle(
                            fontFamily = arabicFontFamily,
                            fontSize = 32f,
                        ),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuaToolbarPickerSheet(
    picker: DuaToolbarPicker,
    currentTranslation: String,
    currentFont: String,
    availableTranslations: List<String>,
    availableFonts: List<String>,
    getTranslationName: (String) -> String,
    getFontName: (String) -> String,
    onTranslationSelected: (String) -> Unit,
    onFontSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val validTranslations = remember(availableTranslations) {
        availableTranslations.filter { code ->
            getTranslationName(code).isNotBlank()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 18.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = if (picker == DuaToolbarPicker.Translation) {
                            "Translation language"
                        } else {
                            "Arabic font"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Text(
                        text = if (picker == DuaToolbarPicker.Translation) {
                            "Choose the language used for this dua"
                        } else {
                            "Choose the script used for Arabic text"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (picker == DuaToolbarPicker.Translation) {
                            items(validTranslations, key = { it }) { code ->
                                DuaToolbarPickerRow(
                                    title = getTranslationName(code),
                                    detail = translationEndonym(code),
                                    selected = code == currentTranslation,
                                    onClick = { onTranslationSelected(code) },
                                )
                            }
                        } else {
                            items(availableFonts, key = { it }) { font ->
                                DuaToolbarPickerRow(
                                    title = getFontName(font),
                                    detail = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                    detailFontFamily = getArabicFontFamilyForDua(font),
                                    selected = font == currentFont,
                                    onClick = { onFontSelected(font) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuaToolbarPickerRow(
    title: String,
    detail: String,
    selected: Boolean,
    detailFontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = detailFontFamily,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(22.dp),
            )
        }
    }
}

private fun translationEndonym(code: String): String = when (code) {
    "ar" -> "العربية"
    "transliteration" -> "Bismillāh"
    "bn" -> "বাংলা"
    "zh" -> "中文"
    "en" -> "English"
    "es" -> "Español"
    "fr" -> "Français"
    "id" -> "Bahasa Indonesia"
    "ru" -> "Русский"
    "sv" -> "Svenska"
    "tr" -> "Türkçe"
    "ur" -> "اردو"
    else -> ""
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
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
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

    val baseWidth = 28f
    val baseHeight = 72f
    val targetSize = 46f

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
            imageVector = if (isLeftEdge) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((28f + progress * 8f).dp),
        )
    }
}

/**
 * Font chip for the tuning-mode dock — renders "الله" in the actual font so the
 * user picks by seeing the glyphs, not by reading a name.
 */
@Composable
private fun DuaFontGlyphChip(
    fontKey: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "الله",
            fontFamily = getArabicFontFamilyForDua(fontKey),
            fontSize = 24.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(18.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

/** Pill toggle/action chip for the tuning-mode dock. */
@Composable
private fun DuaTuneChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            maxLines = 1,
        )
    }
}

/** Tesla-style monochrome Switch — neutral grays only, no primary green. */
@Composable
private fun duaTeslaSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.surface,
    checkedTrackColor = MaterialTheme.colorScheme.onSurface,
    checkedBorderColor = MaterialTheme.colorScheme.onSurface,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun duaTeslaSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.onSurface,
    activeTrackColor = MaterialTheme.colorScheme.onSurface,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

/**
 * Lightweight streaming player for a chapter's recitation audio. Streams the URL via
 * MediaPlayer (no caching); only one chapter plays at a time. Created via remember and
 * released when the screen leaves composition.
 */
// The chapter recitation player now uses the shared core/ui ChapterAudioController (a
// process-wide singleton with CDN download-and-cache + global media mini-bar integration),
// replacing the former private ChapterAudioPlayer duplicate that streamed the raw URL and
// bypassed the media bar.
