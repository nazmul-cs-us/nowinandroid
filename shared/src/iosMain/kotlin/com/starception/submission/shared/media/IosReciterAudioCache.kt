package com.starception.submission.shared.audio

import com.starception.submission.core.assets.AssetPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.Foundation.NSURL

/**
 * iOS MP3Quran cache, separate from manifest-managed Deenly CDN assets.
 * Uses the existing NSURLSession-backed asset transport with cancellation,
 * resumable transfers, and app-private persistent storage.
 */
object IosReciterAudioCache {
    private fun key(reciterId: String, surah: Int): String {
        require(reciterId.matches(Regex("[0-9]+-[0-9]+")))
        require(surah in 1..114)
        return "quran/reciters/$reciterId/${surah.toString().padStart(3, '0')}.mp3"
    }

    fun cachedUrl(reciterId: String, surah: Int): String? =
        AssetPlatform.cachedPath(key(reciterId, surah))
            ?.let { NSURL.fileURLWithPath(it).absoluteString }

    suspend fun download(
        reciter: QuranReciterSource,
        surah: Int,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String? {
        val url = reciter.urlForSurah(surah) ?: return null
        cachedUrl(reciter.id, surah)?.let { return it }
        val path = AssetPlatform.download(
            url = url,
            cdnKey = key(reciter.id, surah),
            onProgress = onProgress,
        ) ?: return null
        return NSURL.fileURLWithPath(path).absoluteString
    }

    suspend fun downloadAll(
        reciter: QuranReciterSource,
        onSurahComplete: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): Boolean {
        val surahs = reciter.availableSurahs.filter { it in 1..114 }.sorted()
        if (surahs.isEmpty()) return false
        for ((index, surah) in surahs.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (download(reciter, surah) == null) return false
            onSurahComplete(index + 1, surahs.size)
        }
        return true
    }

    fun downloadedCount(reciter: QuranReciterSource): Int =
        reciter.availableSurahs.count { it in 1..114 && cachedUrl(reciter.id, it) != null }

    fun delete(reciter: QuranReciterSource): Boolean =
        reciter.availableSurahs.filter { it in 1..114 }
            .all { AssetPlatform.delete(key(reciter.id, it)) }
}
