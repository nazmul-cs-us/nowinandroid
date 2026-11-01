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
    data class Reciter(
        val id: String,
        val name: String,
        val server: String,
        val surahs: Set<Int>,
        val riwaya: String
    )

    suspend fun fetch(): List<Reciter> = withContext(Dispatchers.IO) {
        val connection = URL("https://www.mp3quran.net/api/v3/reciters?language=eng")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.inputStream.bufferedReader().use { reader ->
                val array = JSONObject(reader.readText()).getJSONArray("reciters")
                buildList {
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
                }.sortedWith(compareBy({ it.name }, { it.riwaya }))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(context: Context, reciter: Reciter, surah: Int): File =
        withContext(Dispatchers.IO) {
            require(surah in reciter.surahs) { "Surah not offered by this reciter" }
            val directory = File(context.filesDir, "quran/reciters/${reciter.id}").apply { mkdirs() }
            val destination = File(directory, "%03d.mp3".format(java.util.Locale.US, surah))
            if (destination.isFile && destination.length() > 0L) return@withContext destination
            val temp = File(directory, destination.name + ".part")
            try {
                val connection = URL(reciter.server + destination.name).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true
                    require(connection.responseCode in 200..299) { "Audio server returned HTTP ${connection.responseCode}" }
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
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
