package com.starception.submission.shared.audio

import com.starception.submission.core.assets.AssetPlatform
import com.starception.submission.shared.assets.ContentDownloadBus
import com.starception.submission.shared.assets.ContentDownloadStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSURL

/**
 * iOS reciter audio storage, isolated from the Deenly CDN manifest.
 * Uses the existing URLSession download pipeline (resume data, cancellation,
 * atomic file promotion) rather than loading whole MP3s into memory.
 */
object IosReciterAudioStore {
    private val transferMutex = Mutex()
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
        cachedUrl(source.id, surah)?.let { return it }
        val bannerKey = "track:${source.id}:$surah:${platform.Foundation.NSUUID.UUID().UUIDString}"
        ContentDownloadBus.publishReciter(bannerKey, ContentDownloadStatus("Downloading ${source.displayName}", 0.001f))
        val throttle = com.starception.submission.shared.assets.ContentDownloadProgressThrottle()
        return try {
            downloadFile(source, surah) { done, total ->
                onProgress(done, total)
                val fraction = if (total > 0) done.toFloat() / total else 0.001f
                if (throttle.shouldPublish(fraction)) ContentDownloadBus.publishReciter(bannerKey,
                    ContentDownloadStatus("Downloading ${source.displayName}", fraction))
            }
        } finally { ContentDownloadBus.publishReciter(bannerKey, null) }
    }

    private suspend fun downloadFile(
        source: QuranReciterSource,
        surah: Int,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String? {
        val url = source.urlForSurah(surah) ?: return null
        require(source.id.matches(Regex("[0-9]+-[0-9]+"))) { "Invalid reciter identifier" }
        val cdnKey = key(source.id, surah)
        cachedUrl(source.id, surah)?.let { return it }
        return transferMutex.withLock {
            cachedUrl(source.id, surah)?.let { return@withLock it }
            val path = AssetPlatform.download(
                url = url,
                cdnKey = cdnKey,
                onProgress = onProgress,
            ) ?: return@withLock null
            NSURL.fileURLWithPath(path).absoluteString
        }
    }

    suspend fun downloadAll(
        source: QuranReciterSource,
        onCompleted: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        val ordered = source.availableSurahs.filter { it in 1..114 }.sorted()
        val pending = ordered.filter { cachedUrl(source.id, it) == null }
        var completed = ordered.size - pending.size
        onCompleted(completed, ordered.size)
        if (pending.isEmpty()) return completed
        val bannerKey = "collection:${source.id}"
        ContentDownloadBus.publishReciter(bannerKey, ContentDownloadStatus(
            "Downloading ${source.displayName} · $completed/${ordered.size}",
            (completed.toFloat() / ordered.size).coerceAtLeast(0.001f), completed, ordered.size))
        val throttle = com.starception.submission.shared.assets.ContentDownloadProgressThrottle()
        try {
            for (surah in pending) {
                currentCoroutineContext().ensureActive()
                val result = downloadFile(source, surah) { done, total ->
                    val fileFraction = if (total > 0) done.toFloat() / total else 0f
                    val fraction = (completed + fileFraction) / ordered.size.coerceAtLeast(1)
                    if (throttle.shouldPublish(fraction)) ContentDownloadBus.publishReciter(bannerKey,
                        ContentDownloadStatus("Downloading ${source.displayName} · ${completed + 1}/${ordered.size}", fraction,
                            completedFiles = completed, totalFiles = ordered.size))
                }
                if (result == null) return completed
                completed++
                onCompleted(completed, ordered.size)
                ContentDownloadBus.publishReciter(bannerKey,
                    ContentDownloadStatus("Downloading ${source.displayName} · $completed/${ordered.size}",
                        completed.toFloat() / ordered.size, completed, ordered.size))
            }
            return completed
        } finally { ContentDownloadBus.publishReciter(bannerKey, null) }
    }

    fun downloadedCount(source: QuranReciterSource): Int =
        source.availableSurahs.count { it in 1..114 && cachedUrl(source.id, it) != null }

    fun downloadedBytes(source: QuranReciterSource): Long =
        source.availableSurahs.filter { it in 1..114 }
            .sumOf { surah ->
                val url = cachedUrl(source.id, surah) ?: return@sumOf 0L
                val path = NSURL.URLWithString(url)?.path ?: return@sumOf 0L
                AssetPlatform.fileSize(path)
            }

    fun delete(source: QuranReciterSource, surah: Int): Boolean =
        AssetPlatform.delete(key(source.id, surah))

    fun deleteAll(source: QuranReciterSource): Int =
        source.availableSurahs.count { surah ->
            surah in 1..114 && delete(source, surah)
        }
}
