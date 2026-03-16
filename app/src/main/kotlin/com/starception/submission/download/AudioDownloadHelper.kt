package com.starception.submission.download

import android.content.Context
import android.util.Log
import com.starception.submission.feature.quran.AudioLanguage
import com.starception.submission.feature.quran.QuranData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper that bridges audio player code with AssetDownloadManager for on-demand downloads.
 *
 * Resolution order for audio files:
 * 1. Check cdn_assets/ (downloaded from CDN)
 * 2. Check SD card (legacy location, backward compat)
 * 3. Return null → caller triggers on-demand download
 */
@Singleton
class AudioDownloadHelper @Inject constructor(
    private val downloadManager: AssetDownloadManager,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AudioDownloadHelper"

        // Legacy SD card paths
        private const val SD_QURAN_ARABIC = "/sdcard/Quran/Arabic"
        private const val SD_QURAN_BENGALI = "/sdcard/Quran/Bengali"
        private const val SD_QURAN_ENGLISH = "/sdcard/Quran/English"
        private const val SD_BUKHARI_BN = "/sdcard/Bukhari/bukhari_audio_bn"
    }

    // ======================== Quran Audio ========================

    /**
     * Resolve a Quran audio file: checks cdn_assets first, then SD card.
     * Returns null if the file isn't available locally (needs download).
     */
    fun resolveQuranAudioFile(surahIndex: Int, language: AudioLanguage): File? {
        val surah = QuranData.surahs.getOrNull(surahIndex) ?: return null

        // 1. Check CDN download location
        val cdnKey = getQuranCdnKey(surahIndex, language)
        if (cdnKey != null) {
            val cdnFile = downloadManager.getAssetFile(cdnKey)
            if (cdnFile != null) {
                Log.d(TAG, "Quran audio found in cdn_assets: $cdnKey")
                return cdnFile
            }
        }

        // 2. Check legacy SD card location
        val sdFile = resolveQuranFromSdCard(surahIndex, language)
        if (sdFile != null && sdFile.exists() && sdFile.length() > 0) {
            Log.d(TAG, "Quran audio found on SD card: ${sdFile.absolutePath}")
            return sdFile
        }

        Log.d(TAG, "Quran audio not found for surah ${surah.number} ($language)")
        return null
    }

    /**
     * Get the CDN key for a Quran surah audio file.
     * Returns null if the CDN key can't be determined.
     */
    fun getQuranCdnKey(surahIndex: Int, language: AudioLanguage): String? {
        val surah = QuranData.surahs.getOrNull(surahIndex) ?: return null
        return when (language) {
            AudioLanguage.ARABIC_ONLY -> {
                // CDN key matches the fileName field: "001-al-fatihah.ogg"
                "audio/quran/arabic/${surah.fileName}"
            }
            AudioLanguage.BENGALI_TRANSLATION -> {
                // Bengali uses fileNameBengali: "001 - Al-Fatihah ( The Opening ) - سورة الفاتحة.ogg"
                if (surah.fileNameBengali.isNotEmpty()) {
                    "audio/quran/bengali/${surah.fileNameBengali}"
                } else {
                    // Fallback: try manifest lookup by prefix
                    findCdnKeyByPrefix("audio/quran/bengali/", surahIndex)
                }
            }
            AudioLanguage.ENGLISH_TRANSLATION -> {
                // English: "001 surah_al_fatihah.ogg"
                val num = String.format("%03d", surah.number)
                val name = "surah_${surah.nameEnglish.lowercase().replace("-", "_").replace(" ", "_")}"
                "audio/quran/english/$num $name.ogg"
            }
        }
    }

    /**
     * Lookup a CDN key from the manifest by prefix and sorted index.
     * Used for Bengali audio where filenames contain Arabic characters.
     */
    private fun findCdnKeyByPrefix(prefix: String, index: Int): String? {
        return try {
            val manifest = downloadManager.getCachedManifest() ?: return null
            manifest.assets.keys
                .filter { it.startsWith(prefix) }
                .sorted()
                .getOrNull(index)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to lookup CDN key by prefix: $prefix, index=$index", e)
            null
        }
    }

    /**
     * Resolve Quran audio from legacy SD card paths.
     */
    private fun resolveQuranFromSdCard(surahIndex: Int, language: AudioLanguage): File? {
        val surah = QuranData.surahs.getOrNull(surahIndex) ?: return null
        return when (language) {
            AudioLanguage.ARABIC_ONLY -> {
                File(SD_QURAN_ARABIC, surah.fileName)
            }
            AudioLanguage.BENGALI_TRANSLATION -> {
                val bengaliDir = File(SD_QURAN_BENGALI)
                val allFiles = bengaliDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                allFiles.getOrNull(surahIndex)
            }
            AudioLanguage.ENGLISH_TRANSLATION -> {
                val englishDir = File(SD_QURAN_ENGLISH)
                val pattern = String.format("%03d", surah.number)
                englishDir.listFiles()?.find { it.name.startsWith(pattern) }
            }
        }
    }

    // ======================== Hadith Audio ========================

    /**
     * Resolve a Hadith audio file: checks cdn_assets first, then SD card.
     * Returns null if the file isn't available locally (needs download).
     */
    fun resolveHadithAudioFile(hadithNumber: Int): File? {
        // 1. Check CDN download location
        val cdnKey = getHadithCdnKey(hadithNumber)
        val cdnFile = downloadManager.getAssetFile(cdnKey)
        if (cdnFile != null) {
            Log.d(TAG, "Hadith audio found in cdn_assets: $cdnKey")
            return cdnFile
        }

        // 2. Check legacy SD card location
        val sdFileName = "bukhari_${String.format("%04d", hadithNumber)}.ogg"
        val sdFile = File(SD_BUKHARI_BN, sdFileName)
        if (sdFile.exists() && sdFile.length() > 0) {
            Log.d(TAG, "Hadith audio found on SD card: ${sdFile.absolutePath}")
            return sdFile
        }

        Log.d(TAG, "Hadith audio not found for hadith #$hadithNumber")
        return null
    }

    /**
     * Get the CDN key for a Hadith audio file.
     */
    fun getHadithCdnKey(hadithNumber: Int): String {
        return "audio/bukhari/bn/bukhari_${String.format("%04d", hadithNumber)}.ogg"
    }

    // ======================== Download Helpers ========================

    /**
     * Download a single audio file on-demand.
     * Returns the download result.
     */
    suspend fun downloadAudio(cdnKey: String): AssetDownloadManager.DownloadState {
        val manifest = downloadManager.loadManifest()
            ?: return AssetDownloadManager.DownloadState.Failed("Manifest not available")

        if (manifest.assets[cdnKey] == null) {
            return AssetDownloadManager.DownloadState.Failed("Audio not found in manifest: $cdnKey")
        }

        Log.i(TAG, "Starting on-demand download: $cdnKey")
        return downloadManager.downloadAsset(cdnKey, manifest)
    }

    /**
     * Get the file size for a CDN key from the manifest.
     * Returns null if the key isn't in the manifest.
     */
    suspend fun getAudioFileSize(cdnKey: String): Long? {
        val manifest = downloadManager.loadManifest() ?: return null
        return manifest.assets[cdnKey]?.size
    }

    /**
     * Check if audio is available locally (either cdn_assets or SD card).
     */
    fun isQuranAudioAvailable(surahIndex: Int, language: AudioLanguage): Boolean {
        return resolveQuranAudioFile(surahIndex, language) != null
    }

    /**
     * Check if hadith audio is available locally.
     */
    fun isHadithAudioAvailable(hadithNumber: Int): Boolean {
        return resolveHadithAudioFile(hadithNumber) != null
    }

    /**
     * Get download progress flow for a CDN key.
     */
    fun getDownloadProgress(cdnKey: String) = downloadManager.getDownloadState(cdnKey)
}
