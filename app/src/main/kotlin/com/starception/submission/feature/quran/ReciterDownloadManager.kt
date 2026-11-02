package com.starception.submission.feature.quran

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * App-scoped coordinator for externally hosted reciter recordings.
 * MP3Quran recordings are not CDN-manifest assets: keep them in a separate namespace
 * while exposing the same progress/cancel/delete lifecycle as content downloads.
 */
class ReciterDownloadManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: ReciterDownloadManager? = null

        fun get(context: Context): ReciterDownloadManager =
            instance ?: synchronized(this) {
                instance ?: ReciterDownloadManager(context.applicationContext).also { instance = it }
            }
    }

    data class DownloadStatus(
        val downloadedSurahs: Int = 0,
        val totalSurahs: Int = 0,
        val downloadedBytes: Long = 0,
        val downloading: Boolean = false,
        val error: String? = null,
        val currentFileProgress: Float = 0f,
    ) {
        val progress: Float get() = if (totalSurahs == 0) 0f else
            ((downloadedSurahs + currentFileProgress) / totalSurahs).coerceIn(0f, 1f)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    data class TransferProgress(val label: String, val progress: Float)
    private val transfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val activeTransfer: StateFlow<TransferProgress?> = transfers.map { active ->
        when (active.size) {
            0 -> null
            1 -> active.values.first()
            else -> TransferProgress("Downloading recitations (${active.size})", active.values.map { it.progress }.average().toFloat())
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private fun publish(key: String, label: String, progress: Float) {
        transfers.update { it + (key to TransferProgress(label, progress.coerceIn(0.001f, 1f))) }
    }
    private fun clear(key: String) { transfers.update { it - key } }

    suspend fun downloadSurah(reciter: ReciterCatalog.Reciter, surah: Int): File {
        cachedFile(reciter, surah)?.let { return it }
        val key = "playback:${reciter.id}:$surah:${java.util.UUID.randomUUID()}"
        publish(key, "Downloading ${reciter.name}", 0f)
        return try {
            val file = ReciterCatalog.download(context, reciter, surah) { progress ->
                publish(key, "Downloading ${reciter.name}", progress)
            }
            // Single-surah playback downloads must refresh an open Storage
            // panel just like a bulk download does.
            withContext(Dispatchers.IO) {
                val saved = snapshot(reciter)
                states[reciter.id]?.update { current -> current.copy(
                    downloadedSurahs = saved.downloadedSurahs,
                    totalSurahs = saved.totalSurahs,
                    downloadedBytes = saved.downloadedBytes,
                ) }
            }
            file
        } finally { clear(key) }
    }

    private val jobs = mutableMapOf<String, Job>()
    private val generation = mutableMapOf<String, Int>()
    private val cancelled = mutableSetOf<String>()
    private val states = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<DownloadStatus>>()

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
        cancelled.remove(reciter.id)
        val token = (generation[reciter.id] ?: 0) + 1
        generation[reciter.id] = token
        publish(reciter.id, "Downloading ${reciter.name}", 0f)
        val job = scope.launch {
            if (generation[reciter.id] == token) state.value = snapshot(reciter).copy(downloading = true)
            try {
                for ((index, surah) in surahs.sorted().withIndex()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (reciter.id in cancelled) throw CancellationException("Cancelled")
                    ReciterCatalog.download(context, reciter, surah) { fraction ->
                        if (generation[reciter.id] == token) {
                            state.update { it.copy(currentFileProgress = fraction) }
                            publish(reciter.id,
                                "Downloading ${reciter.name} · ${index + 1}/${surahs.size}",
                                (index + fraction) / surahs.size)
                        }
                    }
                    if (generation[reciter.id] == token) state.value = snapshot(reciter).copy(downloading = true)
                }
                if (generation[reciter.id] == token) state.value = snapshot(reciter)
            } catch (cancelled: CancellationException) {
                if (generation[reciter.id] == token) state.value = snapshot(reciter)
                throw cancelled
            } catch (error: Exception) {
                if (generation[reciter.id] == token) state.value = snapshot(reciter).copy(error = error.message ?: "Download failed")
            } finally {
                if (generation[reciter.id] == token) clear(reciter.id)
            }
        }
        jobs[reciter.id] = job
    }

    @Synchronized
    fun cancel(reciterId: String) {
        clear(reciterId)
        cancelled.add(reciterId)
        generation[reciterId] = (generation[reciterId] ?: 0) + 1
        jobs.remove(reciterId)?.cancel()
        states[reciterId]?.let { it.value = it.value.copy(downloading = false, currentFileProgress = 0f) }
    }

    @Synchronized
    fun delete(reciter: ReciterCatalog.Reciter) {
        cancel(reciter.id)
        val directory = File(context.filesDir, "quran/reciters/${reciter.id}")
        directory.deleteRecursively()
        states[reciter.id]?.value = snapshot(reciter)
    }
}
