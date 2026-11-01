package com.starception.submission.feature.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** MP3Quran catalog; keeps the bundled/CDN recitation as a separate default option. */
object ReciterCatalog {
    private val downloadLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    data class Reciter(
        val id: String,
        val name: String,
        val server: String,
        val surahs: Set<Int>,
        val riwaya: String
    )

    suspend fun fetch(context: Context? = null): List<Reciter> = withContext(Dispatchers.IO) {
        val preferences = context?.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE)
        val connection = URL("https://www.mp3quran.net/api/v3/reciters?language=eng")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = parse(body)
            preferences?.edit()?.putString("catalog_json", body)?.apply()
            parsed
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val cached = preferences?.getString("catalog_json", null)
            if (cached != null) parse(cached) else throw failure
        } finally { connection.disconnect() }
    }

    internal fun parse(body: String): List<Reciter> {
    val array = JSONObject(body).getJSONArray("reciters")
    return buildList {
        for (i in 0 until array.length()) {
            val reciter = array.getJSONObject(i)
            val moshafs = reciter.optJSONArray("moshaf") ?: continue
            for (j in 0 until moshafs.length()) {
                val moshaf = moshafs.getJSONObject(j)
                val server = moshaf.optString("server")
                if (!server.startsWith("https://")) continue
                val surahs = moshaf.optString("surah_list")
                    .split(',').mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..114 }.toSet()
                if (surahs.isEmpty()) continue
                add(
                    Reciter(
                        id = "${reciter.optInt("id")}-${moshaf.optInt("id")}",
                        name = reciter.optString("name"),
                        server = server.trimEnd('/') + "/",
                        surahs = surahs,
                        riwaya = moshaf.optString("name")
                    )
                )
            }
        }
    }.distinctBy { it.id }.sortedWith(compareBy({ it.name }, { it.riwaya }))
    }

    suspend fun download(context: Context, reciter: Reciter, surah: Int, onProgress: (Float) -> Unit = {}): File {
        require(reciter.id.matches(Regex("[0-9]+-[0-9]+"))) { "Invalid reciter identifier" }
        require(reciter.server.startsWith("https://")) { "Invalid reciter server" }
        val lock = downloadLocks.getOrPut("${reciter.id}/$surah") { kotlinx.coroutines.sync.Mutex() }
        lock.lock()
        try { return downloadUnlocked(context, reciter, surah, onProgress) } finally { lock.unlock() }
    }

    private suspend fun downloadUnlocked(context: Context, reciter: Reciter, surah: Int, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            require(surah in reciter.surahs) { "Surah not offered by this reciter" }
            val directory = File(context.filesDir, "quran/reciters/${reciter.id}").apply { mkdirs() }
            val destination = File(directory, "%03d.mp3".format(java.util.Locale.US, surah))
            if (destination.isFile && destination.length() > 0L) return@withContext destination
            val temp = File(directory, destination.name + ".part")
            try {
                val connection = URL(reciter.server.trimEnd('/') + "/" + destination.name).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true
                    require(connection.responseCode in 200..299) { "Audio server returned HTTP ${connection.responseCode}" }
                    require(!connection.contentType.orEmpty().startsWith("text/")) { "Audio server returned a non-audio response" }
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var transferred = 0L
                            var lastPercent = -1
                            val total = connection.contentLengthLong
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                transferred += read
                                if (total > 0) {
                                    val fraction = (transferred.toFloat() / total).coerceIn(0f, 1f)
                                    val percent = (fraction * 100).toInt()
                                    if (percent != lastPercent) { lastPercent = percent; onProgress(fraction) }
                                }
                            }
                        }
                    }
                    coroutineContext.ensureActive()
                    require(temp.length() > 0L) { "Empty audio response" }
                    check(temp.renameTo(destination)) { "Could not save downloaded audio" }
                } finally {
                    connection.disconnect()
                }
                destination
            } finally {
                temp.delete()
            }
        }
}
