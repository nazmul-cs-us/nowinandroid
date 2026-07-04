package com.starception.submission.feature.surah

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.core.qurandatabase.Surah
import com.starception.submission.core.qurandatabase.QuranTranslationHelper
import com.starception.submission.core.qurandatabase.QuranTranslationRepository
import com.starception.submission.core.topicsdatabase.Topic
import com.starception.submission.core.topicsdatabase.TopicsDatabase
import com.starception.submission.core.topicsdatabase.toTopic
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.core.translation.TranslationService
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SurahDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quranEnhancedRepository: com.starception.submission.core.qurandatabase.QuranEnhancedRepository,
    private val tajweedRepository: com.starception.submission.feature.surah.tajweed.TajweedRepository,
    private val assetRepository: AssetRepository,
    val downloadManager: AssetDownloadManager,
) : ViewModel() {

    // Topics for the current news resource
    private val _topics = MutableStateFlow<List<Topic>>(emptyList())
    val topics: StateFlow<List<Topic>> = _topics.asStateFlow()

    private val prefs: SharedPreferences = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<SurahDetailUiState>(SurahDetailUiState.Loading)
    val uiState: StateFlow<SurahDetailUiState> = _uiState.asStateFlow()

    private val _showBismillahRow = MutableStateFlow(false)
    val showBismillahRow: StateFlow<Boolean> = _showBismillahRow.asStateFlow()

    private val _currentTranslation = MutableStateFlow(
        prefs.getString("quran_translation", "ar") ?: "ar"
    )
    val currentTranslation: StateFlow<String> = _currentTranslation.asStateFlow()

    private val _selectedArabicFont = MutableStateFlow(
        prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
    )
    val selectedArabicFont: StateFlow<String> = _selectedArabicFont.asStateFlow()

    private val _arabicFontSize = MutableStateFlow(
        prefs.getFloat("arabic_font_size", 41f)
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

    // Tajweed settings
    private val _showTajweed = MutableStateFlow(
        prefs.getBoolean("show_tajweed", false)
    )
    val showTajweed: StateFlow<Boolean> = _showTajweed.asStateFlow()

    // Continuous (Mushaf) reading mode
    private val _continuousReadingMode = MutableStateFlow(
        prefs.getBoolean("continuous_reading_mode", true)
    )
    val continuousReadingMode: StateFlow<Boolean> = _continuousReadingMode.asStateFlow()

    private val _tajweedAnnotations = MutableStateFlow<Map<Int, List<com.starception.submission.feature.surah.tajweed.TajweedAnnotation>>>(emptyMap())
    val tajweedAnnotations: StateFlow<Map<Int, List<com.starception.submission.feature.surah.tajweed.TajweedAnnotation>>> = _tajweedAnnotations.asStateFlow()

    private val translations = QuranTranslationHelper.getAvailableTranslations()

    fun getRepository(translationCode: String): QuranTranslationRepository {
        return QuranTranslationRepository(context, translationCode, assetRepository)
    }

    fun loadSurah(surahNumber: Int) {
        loadSurah(surahNumber, _currentTranslation.value)
        // Load Tajweed annotations for this surah
        loadTajweedForSurah(surahNumber)
    }

    /**
     * Helper function to check if Bismillah exists in the ayah text
     */
    private fun hasBismillah(ayahText: String): Boolean {
        val bismillahRegex = Regex(
            "^\\s*ب[ِ]*س[ْۡ]*م[ِ]*\\s*[اٱ]لل[َّ]*ه[ِ]*\\s*[اٱ]لر[َّ]*ح[ْۡ]*م[َٰ]*ن[ِ]*\\s*[اٱ]لر[َّ]*ح[ِ]*ي[ۡ]*م[ِ]*\\s*",
            RegexOption.IGNORE_CASE
        )

        if (bismillahRegex.containsMatchIn(ayahText)) {
            return true
        }

        val bismillahPatterns = listOf(
            "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            "بسم الله الرحمن الرحيم",
            "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
            "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
        )

        return bismillahPatterns.any { ayahText.trim().startsWith(it) }
    }

    /**
     * Helper function to remove Bismillah from ayah text
     */
    private fun removeBismillahIfNeeded(ayahText: String, surahNumber: Int, ayahNumberInSurah: Int): String {
        // Only process first ayah of surahs 2-8 and 10-114
        if (ayahNumberInSurah != 1 || surahNumber == 1 || surahNumber == 9) {
            return ayahText
        }

        // Use regex to match any Bismillah pattern with flexible diacritics and spacing
        // Pattern matches: بسم الله الرحمن الرحيم (with any combination of diacritics)
        val bismillahRegex = Regex(
            "^\\s*ب[ِ]*س[ْۡ]*م[ِ]*\\s*[اٱ]لل[َّ]*ه[ِ]*\\s*[اٱ]لر[َّ]*ح[ْۡ]*م[َٰ]*ن[ِ]*\\s*[اٱ]لر[َّ]*ح[ِ]*ي[ۡ]*م[ِ]*\\s*",
            RegexOption.IGNORE_CASE
        )

        var cleanedText = ayahText

        // Try regex first (most flexible)
        cleanedText = bismillahRegex.replace(cleanedText, "").trim()

        // If regex didn't match (ayah text unchanged), try exact pattern matching
        if (cleanedText == ayahText) {
            val bismillahPatterns = listOf(
                "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",  // With Quranic diacritics
                "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",  // Standard diacritics
                "بسم الله الرحمن الرحيم",                  // Without diacritics
                "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"   // Another variant
            )

            for (pattern in bismillahPatterns) {
                if (cleanedText.startsWith(pattern)) {
                    cleanedText = cleanedText.removePrefix(pattern).trim()
                    break
                }
            }
        }

        android.util.Log.d("SurahDetail", "🔄 Bismillah removal | Surah $surahNumber | Original length: ${ayahText.length} | Cleaned length: ${cleanedText.length}")

        return cleanedText
    }

    fun loadSurah(surahNumber: Int, translationCode: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SurahDetail", "🔍 Loading Surah $surahNumber in translation: $translationCode")

                val repository = try {
                    getRepository(translationCode)
                } catch (e: Exception) {
                    android.util.Log.e("SurahDetail", "❌ Failed to create repository for translation: $translationCode", e)
                    val translationName = QuranTranslationHelper.getTranslationName(translationCode)
                    val category = if (translationCode == "ar") "quran_core" else "quran_translation"
                    _uiState.value = SurahDetailUiState.NeedsDownload(
                        category = category,
                        resourceName = "$translationName Quran Translation",
                        description = "This translation database needs to be downloaded to view the Quran in $translationName."
                    )
                    return@launch
                }

                val surah = repository.getSurahByNumber(surahNumber)

                if (surah == null) {
                    android.util.Log.e("SurahDetail", "❌ Surah $surahNumber not found in translation: $translationCode (database may be empty/corrupt)")
                    val translationName = QuranTranslationHelper.getTranslationName(translationCode)
                    val category = if (translationCode == "ar") "quran_core" else "quran_translation"
                    _uiState.value = SurahDetailUiState.NeedsDownload(
                        category = category,
                        resourceName = "$translationName Quran Translation",
                        description = "The $translationName translation database appears to be empty or incomplete. Please download it to view the Quran."
                    )
                    return@launch
                }

                android.util.Log.d("SurahDetail", "✅ Surah found: ${surah.nameEnglish} (ID: ${surah.id}, Number: ${surah.number})")

                // Load ayahs first to check for Bismillah in first ayah
                val rawAyahs = if (translationCode != "ar") {
                    val arabicRepository = getRepository("ar")
                    arabicRepository.getAyahsBySurahOnce(surah.id)
                } else {
                    repository.getAyahsBySurahOnce(surah.id)
                }

                // Check if first ayah has Bismillah (only for surahs 2-8, 10-114)
                val shouldShowBismillah = if (surahNumber != 1 && surahNumber != 9 && rawAyahs.isNotEmpty()) {
                    hasBismillah(rawAyahs.first().text)
                } else {
                    false
                }

                // Update state to show/hide Bismillah row
                _showBismillahRow.value = shouldShowBismillah
                android.util.Log.d("SurahDetail", "🔍 Bismillah check | Surah $surahNumber | Show Bismillah: $shouldShowBismillah")

                // If non-Arabic translation is selected, load both Arabic and translation
                val ayahs = if (translationCode != "ar") {
                    android.util.Log.d("SurahDetail", "📖 Loading dual language: Arabic + $translationCode")

                    // Load Arabic ayahs
                    val arabicRepository = getRepository("ar")
                    val arabicAyahs = arabicRepository.getAyahsBySurahOnce(surah.id)

                    // Load translation ayahs
                    val translationAyahs = repository.getAyahsBySurahOnce(surah.id)

                    // Combine them - each Ayah will show both Arabic and translation
                    // Remove Bismillah from first ayah if needed
                    arabicAyahs.mapIndexed { index, arabicAyah ->
                        val translationText = translationAyahs.getOrNull(index)?.text ?: ""
                        val cleanedArabicText = removeBismillahIfNeeded(
                            arabicAyah.text,
                            surahNumber,
                            arabicAyah.numberInSurah
                        )
                        val cleanedTranslationText = removeBismillahIfNeeded(
                            translationText,
                            surahNumber,
                            arabicAyah.numberInSurah
                        )
                        // Create a combined ayah with both texts separated by newlines
                        arabicAyah.copy(
                            text = "$cleanedArabicText\n\n$cleanedTranslationText"
                        )
                    }
                } else {
                    // Arabic only - remove Bismillah from first ayah if needed
                    repository.getAyahsBySurahOnce(surah.id).map { ayah ->
                        ayah.copy(
                            text = removeBismillahIfNeeded(
                                ayah.text,
                                surahNumber,
                                ayah.numberInSurah
                            )
                        )
                    }
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

    /**
     * Load a surah's ayahs with the current translation applied (Arabic + translation
     * joined by "\n\n" when a non-Arabic translation is selected). Used by the surah
     * cache preloader so neighbouring surahs render with translation, not just Arabic.
     */
    suspend fun loadSurahWithTranslation(surahNumber: Int): Pair<Surah, List<Ayah>>? {
        val translationCode = _currentTranslation.value
        val repo = try {
            getRepository(translationCode)
        } catch (_: Exception) {
            return null
        }
        val surah = repo.getSurahByNumber(surahNumber) ?: return null

        val rawAyahs = if (translationCode != "ar") {
            getRepository("ar").getAyahsBySurahOnce(surah.id)
        } else {
            repo.getAyahsBySurahOnce(surah.id)
        }
        // An empty result here is a transient failure (DB mid-download, Room busy),
        // not a surah with no ayahs — return null so callers don't cache it and
        // permanently render an empty page for this surah.
        if (rawAyahs.isEmpty()) return null

        val ayahs = if (translationCode != "ar") {
            val arabicAyahs = rawAyahs
            val translationAyahs = repo.getAyahsBySurahOnce(surah.id)
            arabicAyahs.mapIndexed { index, arabicAyah ->
                val translationText = translationAyahs.getOrNull(index)?.text ?: ""
                val cleanedArabic = removeBismillahIfNeeded(arabicAyah.text, surahNumber, arabicAyah.numberInSurah)
                val cleanedTr = removeBismillahIfNeeded(translationText, surahNumber, arabicAyah.numberInSurah)
                arabicAyah.copy(text = "$cleanedArabic\n\n$cleanedTr")
            }
        } else {
            rawAyahs.map { it.copy(text = removeBismillahIfNeeded(it.text, surahNumber, it.numberInSurah)) }
        }
        return surah to ayahs
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

    fun saveLastMushafPage(surahNumber: Int, page: Int) {
        prefs.edit().putInt("last_mushaf_page_$surahNumber", page).apply()
    }

    fun getLastMushafPage(surahNumber: Int): Int {
        return prefs.getInt("last_mushaf_page_$surahNumber", 0)
    }

    fun toggleContinuousReadingMode() {
        viewModelScope.launch {
            val newValue = !_continuousReadingMode.value
            _continuousReadingMode.value = newValue
            prefs.edit().putBoolean("continuous_reading_mode", newValue).apply()
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

    // ============= Enhanced Database Features (Word Study & Tafseer) =============

    private val translationService = TranslationService.getInstance(context)

    private val _wordStudyData = MutableStateFlow<com.starception.submission.core.qurandatabase.AyahMeaningsItem?>(null)
    val wordStudyData: StateFlow<com.starception.submission.core.qurandatabase.AyahMeaningsItem?> = _wordStudyData.asStateFlow()

    private val _tafseerData = MutableStateFlow<com.starception.submission.core.qurandatabase.QuranAyahTafseer?>(null)
    val tafseerData: StateFlow<com.starception.submission.core.qurandatabase.QuranAyahTafseer?> = _tafseerData.asStateFlow()

    private val _selectedTafseerBook = MutableStateFlow("saadi")
    val selectedTafseerBook: StateFlow<String> = _selectedTafseerBook.asStateFlow()

    // Tafseer translation states
    private val _tafseerTranslationLanguage = MutableStateFlow(
        prefs.getString("tafseer_translation_lang", "ar") ?: "ar"
    )
    val tafseerTranslationLanguage: StateFlow<String> = _tafseerTranslationLanguage.asStateFlow()

    private val _tafseerTranslationProvider = MutableStateFlow(
        translationService.getSelectedProvider()
    )
    val tafseerTranslationProvider: StateFlow<String> = _tafseerTranslationProvider.asStateFlow()

    private val _translatedTafseerSaadi = MutableStateFlow<String?>(null)
    val translatedTafseerSaadi: StateFlow<String?> = _translatedTafseerSaadi.asStateFlow()

    private val _translatedTafseerMoysar = MutableStateFlow<String?>(null)
    val translatedTafseerMoysar: StateFlow<String?> = _translatedTafseerMoysar.asStateFlow()

    private val _translatedTafseerBaghawi = MutableStateFlow<String?>(null)
    val translatedTafseerBaghawi: StateFlow<String?> = _translatedTafseerBaghawi.asStateFlow()

    private val _translatedWordMeanings = MutableStateFlow<String?>(null)
    val translatedWordMeanings: StateFlow<String?> = _translatedWordMeanings.asStateFlow()

    private val _isTafseerTranslating = MutableStateFlow(false)
    val isTafseerTranslating: StateFlow<Boolean> = _isTafseerTranslating.asStateFlow()

    fun loadWordStudy(surahNumber: Int, ayahNumber: Int) {
        viewModelScope.launch {
            val meanings = quranEnhancedRepository.getAyahMeanings(surahNumber, ayahNumber)
            _wordStudyData.value = meanings
        }
    }

    fun loadTafseer(surahNumber: Int, ayahNumber: Int) {
        viewModelScope.launch {
            val tafseer = quranEnhancedRepository.getTafseerForAyah(surahNumber, ayahNumber)
            _tafseerData.value = tafseer
            // Clear previous translations
            _translatedTafseerSaadi.value = null
            _translatedTafseerMoysar.value = null
            _translatedTafseerBaghawi.value = null
            _translatedWordMeanings.value = null
            // Translate if language is not Arabic
            if (_tafseerTranslationLanguage.value != "ar" && tafseer != null) {
                translateTafseer(tafseer, _tafseerTranslationLanguage.value)
            }
        }
    }

    fun selectTafseerBook(book: String) {
        _selectedTafseerBook.value = book
    }

    fun changeTafseerTranslationLanguage(languageCode: String) {
        viewModelScope.launch {
            _tafseerTranslationLanguage.value = languageCode
            prefs.edit().putString("tafseer_translation_lang", languageCode).apply()

            // If Arabic, clear translations
            if (languageCode == "ar") {
                _translatedTafseerSaadi.value = null
                _translatedTafseerMoysar.value = null
                _translatedTafseerBaghawi.value = null
                _translatedWordMeanings.value = null
                return@launch
            }

            // Translate current tafseer data
            _tafseerData.value?.let { tafseer ->
                translateTafseer(tafseer, languageCode)
            }
        }
    }

    private fun translateTafseer(tafseer: com.starception.submission.core.qurandatabase.QuranAyahTafseer, targetLang: String) {
        viewModelScope.launch {
            _isTafseerTranslating.value = true
            try {
                // Translate all three tafseer texts and word meanings
                if (tafseer.tafseerSaadi.isNotEmpty()) {
                    _translatedTafseerSaadi.value = translationService.translate(tafseer.tafseerSaadi, targetLang)
                }
                if (tafseer.tafseerMoysar.isNotEmpty()) {
                    _translatedTafseerMoysar.value = translationService.translate(tafseer.tafseerMoysar, targetLang)
                }
                if (tafseer.tafseerBaghawi.isNotEmpty()) {
                    _translatedTafseerBaghawi.value = translationService.translate(tafseer.tafseerBaghawi, targetLang)
                }
                if (tafseer.ayahMeanings.isNotEmpty()) {
                    _translatedWordMeanings.value = translationService.translate(tafseer.ayahMeanings, targetLang)
                }
            } catch (e: Exception) {
                android.util.Log.e("SurahDetailVM", "Tafseer translation error", e)
            }
            _isTafseerTranslating.value = false
        }
    }

    fun clearWordStudy() {
        _wordStudyData.value = null
    }

    fun clearTafseer() {
        _tafseerData.value = null
        _translatedTafseerSaadi.value = null
        _translatedTafseerMoysar.value = null
        _translatedTafseerBaghawi.value = null
        _translatedWordMeanings.value = null
    }

    fun getAvailableTafseerTranslations(): List<String> = listOf(
        "ar", "en", "bn", "zh", "es", "fr", "id", "ru", "sv", "tr", "ur"
    )

    fun getTafseerTranslationName(code: String): String = when (code) {
        "ar" -> "Arabic (Original)"
        "en" -> "English"
        "bn" -> "Bengali"
        "zh" -> "Chinese"
        "es" -> "Spanish"
        "fr" -> "French"
        "id" -> "Indonesian"
        "ru" -> "Russian"
        "sv" -> "Swedish"
        "tr" -> "Turkish"
        "ur" -> "Urdu"
        else -> code.uppercase()
    }

    fun getAvailableTafseerProviders(): List<Pair<String, String>> = listOf(
        "auto" to "Auto (Reverso → Google)",
        "google" to "Google Translate",
        "reverso" to "Reverso"
    )

    fun changeTafseerTranslationProvider(providerCode: String) {
        viewModelScope.launch {
            _tafseerTranslationProvider.value = providerCode
            translationService.setSelectedProvider(providerCode)
            // Clear cache to force re-translation with new provider
            translationService.clearCache()
            _translatedTafseerSaadi.value = null
            _translatedTafseerMoysar.value = null
            _translatedTafseerBaghawi.value = null
            _translatedWordMeanings.value = null

            // Re-translate if not Arabic
            if (_tafseerTranslationLanguage.value != "ar") {
                _tafseerData.value?.let { tafseer ->
                    translateTafseer(tafseer, _tafseerTranslationLanguage.value)
                }
            }
        }
    }

    // Tajweed methods
    val isTajweedAvailable: Boolean
        get() = assetRepository.isAvailable("json/tajweed.json")

    fun changeTajweed(enabled: Boolean) {
        if (enabled && !isTajweedAvailable) {
            android.util.Log.w("SurahDetailVM", "⚠️ Tajweed data not downloaded yet")
            return
        }
        _showTajweed.value = enabled
        prefs.edit().putBoolean("show_tajweed", enabled).apply()
        android.util.Log.d("SurahDetailVM", "🎨 Tajweed changed to: $enabled")
    }

    fun loadTajweedForSurah(surahNumber: Int) {
        if (!isTajweedAvailable) {
            android.util.Log.w("SurahDetailVM", "⚠️ Tajweed data not available, skipping load for surah $surahNumber")
            return
        }
        viewModelScope.launch {
            try {
                val annotations = tajweedRepository.getAnnotationsForSurah(surahNumber)
                _tajweedAnnotations.value = annotations
                android.util.Log.d("SurahDetailVM", "📗 Loaded Tajweed for surah $surahNumber: ${annotations.size} ayahs")
            } catch (e: Exception) {
                android.util.Log.e("SurahDetailVM", "❌ Error loading Tajweed: ${e.message}", e)
            }
        }
    }

    /**
     * Load topics for a news resource
     * @param newsResourceId The ID of the news resource (can be string from navigation)
     */
    fun loadTopicsForNewsResource(newsResourceId: String?) {
        if (newsResourceId == null) {
            _topics.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val newsId = newsResourceId.toIntOrNull() ?: return@launch

                // Get topic IDs for this news resource
                val newsDao = NewsDatabase.getInstance(context).newsDao()
                val topicIds = newsDao.getTopicIdsForNews(newsId)

                android.util.Log.d("SurahDetailVM", "📚 Found ${topicIds.size} topics for news resource $newsId")

                if (topicIds.isEmpty()) {
                    _topics.value = emptyList()
                    return@launch
                }

                // Get topic details
                val topicsDao = TopicsDatabase.getInstance(context).topicsDao()
                val topicEntities = topicsDao.getTopicsByIds(topicIds)

                _topics.value = topicEntities.map { it.toTopic() }
                android.util.Log.d("SurahDetailVM", "📚 Loaded topics: ${_topics.value.map { it.name }}")

            } catch (e: Exception) {
                android.util.Log.e("SurahDetailVM", "❌ Error loading topics: ${e.message}", e)
                _topics.value = emptyList()
            }
        }
    }

    /**
     * Loads topics for a surah - all surahs belong to "Holy Quran" topic
     * This is used when navigating via swipe where newsResourceId is not available
     * @param surahNumber The surah number (1-114)
     */
    fun loadTopicsForSurah(surahNumber: Int) {
        viewModelScope.launch {
            try {
                // All surahs belong to "Holy Quran" topic
                val topicsDao = TopicsDatabase.getInstance(context).topicsDao()
                val quranTopic = topicsDao.getTopicByName("Holy Quran")
                if (quranTopic != null) {
                    _topics.value = listOf(quranTopic.toTopic())
                    android.util.Log.d("SurahDetailVM", "📚 Using 'Holy Quran' topic for surah $surahNumber")
                } else {
                    _topics.value = emptyList()
                    android.util.Log.d("SurahDetailVM", "📚 'Holy Quran' topic not found for surah $surahNumber")
                }
            } catch (e: Exception) {
                android.util.Log.e("SurahDetailVM", "❌ Error loading topics for surah: ${e.message}", e)
                _topics.value = emptyList()
            }
        }
    }
}

sealed interface SurahDetailUiState {
    data object Loading : SurahDetailUiState
    data class Success(val surah: Surah, val ayahs: List<Ayah>) : SurahDetailUiState
    data class Error(val message: String) : SurahDetailUiState
    data class NeedsDownload(
        val category: String,
        val resourceName: String,
        val description: String,
    ) : SurahDetailUiState
}
