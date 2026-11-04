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
    private const val OFFLINE_RECITER_PREFIX = "offline_reciter_"

    data class Reciter(
        val id: String,
        val name: String,
        val server: String,
        val surahs: Set<Int>,
        val riwaya: String
    )

    /** Let the picker display saved voices immediately while refreshing the catalog. */
    suspend fun cached(context: Context): List<Reciter>? = withContext(Dispatchers.IO) {
        val json = context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE)
            .getString("catalog_json", null) ?: return@withContext null
        try { parse(json) } catch (_: org.json.JSONException) { null }
    }

    /** Preserve names and supported surahs independently of the changing online catalog. */
    internal fun rememberDownloaded(context: Context, reciter: Reciter) {
        if (!reciter.id.matches(Regex("[0-9]+-[0-9]+"))) return
        val metadata = JSONObject()
            .put("id", reciter.id)
            .put("name", reciter.name)
            .put("server", reciter.server)
            .put("surahs", reciter.surahs.sorted().joinToString(","))
            .put("riwaya", reciter.riwaya)
        context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE).edit()
            .putString(OFFLINE_RECITER_PREFIX + reciter.id, metadata.toString()).apply()
    }

    internal fun forgetDownloaded(context: Context, reciterId: String) {
        context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE).edit()
            .remove(OFFLINE_RECITER_PREFIX + reciterId).apply()
    }

    /** Include recordings saved before metadata was introduced, even without connectivity. */
    suspend fun offline(context: Context, catalog: List<Reciter> = emptyList()): List<Reciter> = withContext(Dispatchers.IO) {
        val savedIds = File(context.filesDir, "quran/reciters")
            .listFiles()?.filter(File::isDirectory)?.map(File::getName)?.toSet().orEmpty()
        if (savedIds.isEmpty()) return@withContext emptyList()
        val preferences = context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE)
        fun fromFields(id: String, name: String, server: String, surahs: String, riwaya: String): Reciter? {
            val offered = surahs.split(',').mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..114 }.toSet()
            return if (id in savedIds && id.matches(Regex("[0-9]+-[0-9]+")) &&
                server.startsWith("https://") && offered.isNotEmpty()
            ) Reciter(id, name, server, offered, riwaya) else null
        }
        val remembered = preferences.all.mapNotNull { (key, raw) ->
            if (!key.startsWith(OFFLINE_RECITER_PREFIX) || raw !is String) return@mapNotNull null
            runCatching {
                val json = JSONObject(raw)
                fromFields(json.optString("id"), json.optString("name"), json.optString("server"),
                    json.optString("surahs"), json.optString("riwaya"))
            }.getOrNull()
        }
        val selected = preferences.getString("selected_id", null)?.let { id ->
            fromFields(id, preferences.getString("selected_name", "").orEmpty(),
                preferences.getString("selected_server", "").orEmpty(),
                preferences.getString("selected_surahs", "").orEmpty(),
                preferences.getString("selected_riwayah", "").orEmpty())
        }
        val manager = ReciterDownloadManager.get(context)
        val available = (remembered + catalog + cached(context).orEmpty() + listOfNotNull(selected))
            .distinctBy(Reciter::id)
            .filter { it.id in savedIds && manager.snapshot(it).downloadedSurahs > 0 }
        // Bootstrap metadata for existing files before a remote fetch replaces catalog_json.
        available.forEach { rememberDownloaded(context, it) }
        available
    }

    suspend fun fetch(context: Context? = null): List<Reciter> = withContext(Dispatchers.IO) {
        if (context != null) {
            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
            val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            if (capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) {
                return@withContext cached(context) ?: throw java.io.IOException("Connect to load the reciter catalogue.")
            }
        }
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
                         // MP3Quran spells this entry "Abdulrahman Alsudaes". Keep
                         // its stable identity/server while presenting the familiar name.
                         name = if (reciter.optInt("id") == 54) "Abdul Rahman Al-Sudais" else reciter.optString("name"),
                        server = server.trimEnd('/') + "/",
                        surahs = surahs,
                        riwaya = moshaf.optString("name")
                    )
                )
            }
        }
    }.distinctBy { it.id }.sortedWith(compareBy({ it.name }, { it.riwaya }))
    }

    fun matchesSearch(reciter: Reciter, query: String): Boolean {
        val aliases = if (reciter.id.substringBefore('-') == "54") {
            " Abdulrahman Alsudaes Abdur Rahman As Sudais Sudays"
        } else ""
        val searchable = (reciter.name + " " + reciter.riwaya + aliases)
            .lowercase().filter(Char::isLetterOrDigit)
        return query.lowercase().split(' ', '-', '\'', '’')
            .map { it.filter(Char::isLetterOrDigit) }
            .all(searchable::contains)
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
