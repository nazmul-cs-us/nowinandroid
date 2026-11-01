package com.starception.submission.shared.audio

import com.starception.submission.core.assets.AssetPlatform
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.Foundation.NSURL

/**
 * iOS reciter audio storage, isolated from the Deenly CDN manifest.
 * Uses the existing URLSession download pipeline (resume data, cancellation,
 * atomic file promotion) rather than loading whole MP3s into memory.
 */
object IosReciterAudioStore {
    private fun key(reciterId: String, surah: Int): String {
        require(reciterId.matches(Regex("[0-9]+-[0-9]+")))
        require(surah in 1..114)
        return "quran/reciters/$reciterId/${surah.toString().padStart(3, '0')}.mp3"
    }

    fun cachedUrl(reciterId: String, surah: Int): String? =
        AssetPlatform.cachedPath(key(reciterId, surah))
            ?.let { NSURL.fileURLWithPath(it).absoluteString }

    suspend fun download(
        source: QuranReciterSource,
        surah: Int,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String? {
        val url = source.urlForSurah(surah) ?: return null
        val cdnKey = key(source.id, surah)
        cachedUrl(source.id, surah)?.let { return it }
        val path = AssetPlatform.download(
            url = url,
            cdnKey = cdnKey,
            onProgress = onProgress,
        ) ?: return null
        return NSURL.fileURLWithPath(path).absoluteString
    }

    suspend fun downloadAll(
        source: QuranReciterSource,
        onCompleted: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        val ordered = source.availableSurahs.filter { it in 1..114 }.sorted()
        var completed = 0
        for (surah in ordered) {
            currentCoroutineContext().ensureActive()
            if (download(source, surah) != null) completed++
            onCompleted(completed, ordered.size)
        }
        return completed
    }

    fun delete(source: QuranReciterSource, surah: Int): Boolean =
        AssetPlatform.delete(key(source.id, surah))

    fun deleteAll(source: QuranReciterSource): Int =
        source.availableSurahs.count { surah ->
            surah in 1..114 && delete(source, surah)
        }
}
