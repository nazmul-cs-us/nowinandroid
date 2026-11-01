package com.starception.submission.feature.quran

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * App-scoped coordinator for externally hosted reciter recordings.
 * MP3Quran recordings are not CDN-manifest assets: keep them in a separate namespace
 * while exposing the same progress/cancel/delete lifecycle as content downloads.
 */
class ReciterDownloadManager(private val context: Context) {
    data class DownloadStatus(
        val downloadedSurahs: Int = 0,
        val totalSurahs: Int = 0,
        val downloadedBytes: Long = 0,
        val downloading: Boolean = false,
        val error: String? = null,
    ) {
        val progress: Float get() = if (totalSurahs == 0) 0f else
            downloadedSurahs.toFloat() / totalSurahs
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val states = mutableMapOf<String, MutableStateFlow<DownloadStatus>>()

    @Synchronized
    fun observe(reciter: ReciterCatalog.Reciter): StateFlow<DownloadStatus> =
        states.getOrPut(reciter.id) { MutableStateFlow(snapshot(reciter)) }.asStateFlow()

    fun cachedFile(reciter: ReciterCatalog.Reciter, surah: Int): File? {
        if (surah !in reciter.surahs) return null
        val file = File(context.filesDir, "quran/reciters/${reciter.id}/%03d.mp3".format(java.util.Locale.US, surah))
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    fun snapshot(reciter: ReciterCatalog.Reciter): DownloadStatus {
        val files = reciter.surahs.mapNotNull { cachedFile(reciter, it) }
        return DownloadStatus(
            downloadedSurahs = files.size,
            totalSurahs = reciter.surahs.size,
            downloadedBytes = files.sumOf { it.length() },
        )
    }

    /** One reciter has one active job; repeated taps do not start duplicate transfers. */
    @Synchronized
    fun download(reciter: ReciterCatalog.Reciter, surahs: Set<Int> = reciter.surahs) {
        if (jobs[reciter.id]?.isActive == true) return
        require(surahs.all { it in reciter.surahs })
        val state = states.getOrPut(reciter.id) { MutableStateFlow(snapshot(reciter)) }
        val job = scope.launch {
            state.value = snapshot(reciter).copy(downloading = true)
            try {
                for (surah in surahs.sorted()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    ReciterCatalog.download(context, reciter, surah)
                    state.value = snapshot(reciter).copy(downloading = true)
                }
                state.value = snapshot(reciter)
            } catch (cancelled: CancellationException) {
                state.value = snapshot(reciter)
                throw cancelled
            } catch (error: Exception) {
                state.value = snapshot(reciter).copy(error = error.message ?: "Download failed")
            }
        }
        jobs[reciter.id] = job
    }

    @Synchronized
    fun cancel(reciterId: String) {
        jobs.remove(reciterId)?.cancel()
    }

    @Synchronized
    fun delete(reciter: ReciterCatalog.Reciter) {
        cancel(reciter.id)
        val directory = File(context.filesDir, "quran/reciters/${reciter.id}")
        directory.deleteRecursively()
        states[reciter.id]?.value = snapshot(reciter)
    }
}
