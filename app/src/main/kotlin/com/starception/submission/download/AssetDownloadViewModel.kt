package com.starception.submission.download

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.contentdatabase.NewsDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State for a single download category displayed in the UI.
 */
data class CategoryDownloadState(
    val categoryKey: String,
    val displayName: String,
    val description: String,
    val totalSize: Long,
    val downloadedSize: Long,
    val fileCount: Int,
    val required: Boolean,
    val isComplete: Boolean,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
)

/**
 * Overall download screen state.
 */
sealed class DownloadScreenState {
    data object Loading : DownloadScreenState()
    data object AllReady : DownloadScreenState()
    data class NeedsDownload(
        val categories: List<CategoryDownloadState>,
        val totalRequiredSize: Long,
        val totalOptionalSize: Long,
        val requiredComplete: Boolean,
        val overallProgress: Float,
        val isDownloading: Boolean,
        val error: String? = null,
    ) : DownloadScreenState()
    data class Error(val message: String) : DownloadScreenState()
}

@HiltViewModel
class AssetDownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: AssetDownloadManager,
) : ViewModel() {

    private val _screenState = MutableStateFlow<DownloadScreenState>(DownloadScreenState.Loading)
    val screenState: StateFlow<DownloadScreenState> = _screenState.asStateFlow()

    private var manifest: AssetManifest? = null
    private var isCurrentlyDownloading = false

    init {
        loadManifest()
    }

    private fun loadManifest() {
        viewModelScope.launch {
            try {
                val m = downloadManager.loadManifest()
                if (m == null) {
                    _screenState.value = DownloadScreenState.Error("No asset manifest found")
                    return@launch
                }
                manifest = m

                // Check if all required assets are already available (bundled fallback works)
                if (downloadManager.hasEssentialAssets(m)) {
                    _screenState.value = DownloadScreenState.AllReady
                    Log.i(TAG, "All essential assets ready")
                    return@launch
                }

                // Also check if all required categories are individually complete
                val requiredKeys = m.categories.filter { it.value.required }.keys
                if (requiredKeys.all { downloadManager.isCategoryComplete(it, m) }) {
                    _screenState.value = DownloadScreenState.AllReady
                    Log.i(TAG, "All required categories complete, skipping download screen")
                    return@launch
                }

                refreshCategoryStates()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading manifest", e)
                _screenState.value = DownloadScreenState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun refreshCategoryStates() {
        val m = manifest ?: return

        val categories = m.categories.map { (key, info) ->
            val downloadedSize = downloadManager.getCategoryDownloadedSize(key, m)
            val isComplete = downloadManager.isCategoryComplete(key, m)
            CategoryDownloadState(
                categoryKey = key,
                displayName = formatCategoryName(key),
                description = categoryDescription(key),
                totalSize = info.totalSize,
                downloadedSize = downloadedSize,
                fileCount = info.fileCount,
                required = info.required,
                isComplete = isComplete,
                progress = if (info.totalSize > 0) downloadedSize.toFloat() / info.totalSize else 0f,
            )
        }.sortedWith(compareByDescending<CategoryDownloadState> { it.required }.thenBy { it.displayName })

        val totalRequiredSize = categories.filter { it.required }.sumOf { it.totalSize }
        val totalOptionalSize = categories.filter { !it.required }.sumOf { it.totalSize }
        val requiredComplete = categories.filter { it.required }.all { it.isComplete }

        val overallRequired = categories.filter { it.required }
        val overallProgress = if (totalRequiredSize > 0) {
            overallRequired.sumOf { it.downloadedSize }.toFloat() / totalRequiredSize
        } else 1f

        _screenState.value = DownloadScreenState.NeedsDownload(
            categories = categories,
            totalRequiredSize = totalRequiredSize,
            totalOptionalSize = totalOptionalSize,
            requiredComplete = requiredComplete,
            overallProgress = overallProgress,
            isDownloading = isCurrentlyDownloading,
        )
    }

    /**
     * Download all required categories.
     */
    fun downloadRequired() {
        val m = manifest ?: return
        if (isCurrentlyDownloading) return

        val requiredCategories = m.categories.filter { it.value.required }.keys
        val allAlreadyComplete = requiredCategories.all { downloadManager.isCategoryComplete(it, m) }

        if (allAlreadyComplete) {
            Log.i(TAG, "All required categories already complete, skipping download")
            viewModelScope.launch {
                try {
                    Log.i(TAG, "Regenerating news.db from downloaded sources...")
                    val result = withContext(Dispatchers.IO) {
                        NewsDatabase.regenerateFromSources(context)
                    }
                    if (result.success) {
                        Log.i(TAG, "News.db regenerated: ${result.totalNewsResources} resources, ${result.topicMappings} mappings")
                    } else {
                        Log.e(TAG, "News.db regeneration failed: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error regenerating news.db", e)
                }
                _screenState.value = DownloadScreenState.AllReady
            }
            return
        }

        isCurrentlyDownloading = true
        updateDownloadingState(true)

        viewModelScope.launch {
            try {
                for (category in requiredCategories) {
                    if (downloadManager.isCategoryComplete(category, m)) continue

                    downloadManager.downloadCategory(category, m) { progress, downloaded, total ->
                        viewModelScope.launch {
                            refreshCategoryStates()
                        }
                    }
                }
                isCurrentlyDownloading = false
                refreshCategoryStates()
                Log.i(TAG, "Required downloads complete")

                // Regenerate news.db from CDN-downloaded source databases
                // (quran.db, fortress_of_the_muslim.db, quranic_duas.db, sahih_bukhari.json)
                try {
                    Log.i(TAG, "Regenerating news.db from downloaded sources...")
                    val result = withContext(Dispatchers.IO) {
                        NewsDatabase.regenerateFromSources(context)
                    }
                    if (result.success) {
                        Log.i(TAG, "News.db regenerated: ${result.totalNewsResources} resources, ${result.topicMappings} mappings")
                    } else {
                        Log.e(TAG, "News.db regeneration failed: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error regenerating news.db after download", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                isCurrentlyDownloading = false
                val current = _screenState.value
                if (current is DownloadScreenState.NeedsDownload) {
                    _screenState.value = current.copy(
                        isDownloading = false,
                        error = e.message ?: "Download failed",
                    )
                }
            }
        }
    }

    /**
     * Download a specific optional category.
     */
    fun downloadCategory(categoryKey: String) {
        val m = manifest ?: return

        viewModelScope.launch {
            try {
                downloadManager.downloadCategory(categoryKey, m) { progress, downloaded, total ->
                    viewModelScope.launch {
                        refreshCategoryStates()
                    }
                }
                refreshCategoryStates()
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading category $categoryKey", e)
            }
        }
    }

    /**
     * Delete a downloaded category to free storage.
     */
    fun deleteCategory(categoryKey: String) {
        val m = manifest ?: return
        downloadManager.deleteCategory(categoryKey, m)
        refreshCategoryStates()
    }

    /**
     * Skip downloading optional content and proceed with bundled assets.
     */
    fun skipOptionalDownloads() {
        _screenState.value = DownloadScreenState.AllReady
    }

    private fun updateDownloadingState(downloading: Boolean) {
        val current = _screenState.value
        if (current is DownloadScreenState.NeedsDownload) {
            _screenState.value = current.copy(isDownloading = downloading)
        }
    }

    companion object {
        private const val TAG = "AssetDownloadVM"

        fun formatCategoryName(category: String): String = when (category) {
            "quran_core" -> "Quran (Arabic + Tafseer)"
            "quran_translation" -> "Quran Translations"
            "hadith_sahih_bukhari" -> "Sahih Bukhari"
            "hadith_sahih_muslim" -> "Sahih Muslim"
            "hadith_sunan_abu_dawud" -> "Sunan Abu Dawud"
            "hadith_sunan_tirmidhi" -> "Jami at-Tirmidhi"
            "hadith_sunan_nasai" -> "Sunan an-Nasa'i"
            "hadith_sunan_ibn_majah" -> "Sunan Ibn Majah"
            "hadith_musnad_ahmad" -> "Musnad Ahmad"
            "hadith_muwatta_malik" -> "Muwatta Malik"
            "hadith_sunan_darimi" -> "Sunan ad-Darimi"
            "json_data" -> "Islamic Reference Data"
            "news" -> "News Content"
            "model_tts_kokoro" -> "Kokoro TTS Engine"
            "model_tts_vits" -> "VITS TTS Engine"
            "model_tts_ryan" -> "Ryan TTS Voice"
            "model_tts_espeak" -> "eSpeak Phoneme Data"
            "model_asr" -> "Speech Recognition"
            "model_whisper" -> "Whisper STT Engine"
            "model_kws" -> "Keyword Detection"
            "bukhari_audio_bn" -> "Bukhari Audio (Bengali)"
            "quran_audio_arabic" -> "Quran Audio (Arabic)"
            "quran_audio_bengali" -> "Quran Audio (Bengali)"
            "quran_audio_english" -> "Quran Audio (English)"
            "other" -> "Additional Resources"
            else -> category.replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }

        /**
         * Returns a display group for organizing categories in the UI.
         */
        fun categoryGroup(category: String): String = when {
            category == "quran_core" || category == "quran_translation" -> "Holy Quran"
            category.startsWith("hadith_") -> "Hadith Collections"
            category.startsWith("quran_audio_") || category == "bukhari_audio_bn" -> "Audio Recitations"
            category.startsWith("model_tts_") -> "Text-to-Speech"
            category.startsWith("model_") -> "Voice Recognition"
            category == "json_data" || category == "news" || category == "other" -> "App Data"
            else -> "Other"
        }

        /**
         * Returns a short description for a content group.
         */
        fun groupDescription(group: String): String = when (group) {
            "Holy Quran" -> "Arabic text, translations, and Tafseer"
            "Hadith Collections" -> "Prophetic traditions and narrations"
            "Audio Recitations" -> "Quran and Hadith audio playback"
            "Text-to-Speech" -> "On-device voice engines for reading"
            "Voice Recognition" -> "Speech-to-text and voice commands"
            "App Data" -> "Reference data and news content"
            else -> ""
        }

        /**
         * Returns a sort order for groups to ensure consistent display.
         */
        fun groupSortOrder(group: String): Int = when (group) {
            "Holy Quran" -> 0
            "Hadith Collections" -> 1
            "Audio Recitations" -> 2
            "Text-to-Speech" -> 3
            "Voice Recognition" -> 4
            "App Data" -> 5
            else -> 6
        }

        fun categoryDescription(category: String): String = when (category) {
            "quran_core" -> "Arabic text, Tafseer, and word meanings"
            "quran_translation" -> "11 languages including English and Bengali"
            "hadith_sahih_bukhari" -> "Most authentic collection, 7,563 hadith"
            "hadith_sahih_muslim" -> "Second most authentic, 7,453 hadith"
            "hadith_sunan_abu_dawud" -> "Focus on legal rulings, 5,274 hadith"
            "hadith_sunan_tirmidhi" -> "Includes grading of hadith, 3,956 hadith"
            "hadith_sunan_nasai" -> "Known for strict criteria, 5,758 hadith"
            "hadith_sunan_ibn_majah" -> "Covers fiqh topics, 4,341 hadith"
            "hadith_musnad_ahmad" -> "Largest collection, 27,647 hadith"
            "hadith_muwatta_malik" -> "Earliest compiled, Medina school of thought"
            "hadith_sunan_darimi" -> "Companion to the six major books"
            "json_data" -> "Duas, topics, and reference content"
            "news" -> "Islamic news and articles"
            "model_tts_kokoro" -> "High-quality neural voice for reading"
            "model_tts_vits" -> "Multi-speaker voice synthesis"
            "model_tts_ryan" -> "Natural English male voice"
            "model_tts_espeak" -> "Pronunciation data for text-to-speech"
            "model_asr" -> "On-device speech-to-text engine"
            "model_whisper" -> "Accurate English voice transcription"
            "model_kws" -> "Wake word and voice command detection"
            "bukhari_audio_bn" -> "Sahih Bukhari narration in Bengali"
            "quran_audio_arabic" -> "Full Quran recitation by renowned Qari"
            "quran_audio_bengali" -> "Bengali translation audio recitation"
            "quran_audio_english" -> "English translation audio recitation"
            "other" -> "Supplementary app content"
            else -> ""
        }

        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes.toFloat() / (1024 * 1024))
            else -> "%.2f GB".format(bytes.toFloat() / (1024 * 1024 * 1024))
        }
    }
}
