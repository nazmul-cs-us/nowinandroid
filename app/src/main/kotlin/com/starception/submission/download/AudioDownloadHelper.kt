package com.starception.submission.download

import android.content.Context
import android.util.Log
import com.starception.submission.feature.quran.AudioLanguage
import com.starception.submission.feature.quran.QuranData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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

    // App-scoped: on-demand downloads must outlive the (cancellable) UI coroutine that
    // requested playback, so a re-tap or navigating away doesn't abort a download in progress.
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Coalesces concurrent requests for the same file into a single shared download.
    private val inFlight = ConcurrentHashMap<String, Deferred<AssetDownloadManager.DownloadState>>()

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
                // Look up by numeric prefix — several CDN spellings drift from
                // QuranData.fileName (051 adh-dhariyat, 073 al-muzammil,
                // 074 al-muddaththir, 098 al-baiyyinah, 108 al-kauthar), and a
                // reconstructed key that misses the manifest simply never plays.
                // Fall back to the fileName form when no manifest is cached.
                findCdnKeyByNumericPrefix("audio/quran/arabic/", surah.number)
                    ?: "audio/quran/arabic/${surah.fileName}"
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
                // Manifest naming is inconsistent across English files:
                //   1-30:  "001 surah_al_fatihah.ogg"  (space + lowercase 's')
                //   31-114:"031Surah_luqman.ogg"      (no space + capital 'S')
                // Plus spelling variants (at_tawbah, as_safat, adh_dhariyat, al_muddaththir,
                // al_layl, quraysh) that don't match QuranData.nameEnglish.
                // So look up by numeric prefix rather than trying to reconstruct the filename.
                findCdnKeyByNumericPrefix("audio/quran/english/", surah.number)
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
     * Find a manifest CDN key under [folderPrefix] whose filename starts with the
     * zero-padded surah number. Used when filename suffix conventions vary across
     * entries (e.g. English Quran audio).
     */
    private fun findCdnKeyByNumericPrefix(folderPrefix: String, surahNumber: Int): String? {
        return try {
            val manifest = downloadManager.getCachedManifest() ?: return null
            val numPrefix = folderPrefix + String.format("%03d", surahNumber)
            manifest.assets.keys.firstOrNull { key ->
                if (!key.startsWith(numPrefix)) return@firstOrNull false
                // Ensure the digits don't bleed into a higher number (e.g. "003" matching "0030").
                val next = key.getOrNull(numPrefix.length)
                next == null || !next.isDigit()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to lookup CDN key by numeric prefix: $folderPrefix #$surahNumber", e)
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
                // Match by number like the English path — on-disk spellings can
                // differ from QuranData.fileName (e.g. 074 muddathir vs muddaththir).
                val pattern = String.format("%03d", surah.number)
                File(SD_QURAN_ARABIC).listFiles()?.find { it.name.startsWith(pattern) }
                    ?: File(SD_QURAN_ARABIC, surah.fileName)
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

    // ======================== Fortress (chapter dua) Audio ========================

    /**
     * Resolve a Fortress chapter audio URL to a LOCAL playable file path, downloading and
     * caching it on demand if it isn't present yet. Returns null when the URL isn't a fortress
     * CDN asset or the download fails — the caller should then stream the URL directly.
     *
     * Backs the play-local-else-download-then-cache behaviour of the news-card chapter play
     * button (ChapterAudioController.localAudioResolver).
     */
    suspend fun resolveFortressAudioUrlToLocalPath(audioUrl: String): String? {
        val cdnKey = cdnKeyFromUrl(audioUrl) ?: return null

        // 1. Already downloaded → play the local copy.
        downloadManager.getAssetFile(cdnKey)?.let {
            Log.d(TAG, "Fortress audio found in cdn_assets: $cdnKey")
            return it.absolutePath
        }

        // 2. Download on demand on the app scope (survives caller cancellation), then return
        //    the local path. Concurrent requests for the same file share one download.
        Log.i(TAG, "Fortress audio not local, downloading on demand: $cdnKey")
        val deferred = inFlight.getOrPut(cdnKey) {
            downloadScope.async {
                try {
                    downloadAudio(cdnKey)
                } finally {
                    inFlight.remove(cdnKey)
                }
            }
        }
        val state = deferred.await()
        return if (state is AssetDownloadManager.DownloadState.Completed) {
            downloadManager.getAssetFile(cdnKey)?.absolutePath
        } else {
            Log.w(TAG, "Fortress audio download did not complete for $cdnKey: $state")
            null
        }
    }

    /**
     * Derive the manifest/CDN key from a fortress audio URL, e.g.
     * "https://pub-xxx.r2.dev/audio/fortress/arabic/001.mp3" -> "audio/fortress/arabic/001.mp3".
     * Returns null for anything that isn't a fortress CDN audio URL (so the caller streams it).
     */
    private fun cdnKeyFromUrl(audioUrl: String): String? {
        val marker = "audio/fortress/"
        val idx = audioUrl.indexOf(marker)
        if (idx < 0) return null
        return audioUrl.substring(idx).substringBefore('?').substringBefore('#')
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
