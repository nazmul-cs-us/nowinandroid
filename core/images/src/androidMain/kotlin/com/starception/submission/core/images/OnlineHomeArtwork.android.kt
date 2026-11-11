package com.starception.submission.core.images

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

private var repository: Pair<Context, HomeArtworkRepository>? = null

@Composable
actual fun rememberHomeArtworkRepository(): HomeArtworkRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        repository?.takeIf { it.first === context }?.second ?: HomeArtworkRepository(
            AndroidHomeArtworkPlatform(context),
        ).also { repository = context to it }
    }
}

internal actual fun artworkSha256(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

internal class AndroidHomeArtworkPlatform(context: Context) : HomeArtworkPlatform {
    private val directory = File(context.filesDir, "home-artwork-online").apply { mkdirs() }
    private val metadata = File(directory, "collection.json")

    override suspend fun download(url: String, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        val source = URL(url)
        require(allowedArtworkDownload(url))
        require(source.protocol == "https" && source.userInfo == null && source.port in listOf(-1, 443))
        require(url == HOME_ARTWORK_FEED_URL || (source.host == "images.metmuseum.org" && source.path.startsWith("/CRDImages/is/web-large/")) ||
            (source.host == "www.clevelandart.org" && source.path == "/_next/image") ||
            (source.host == "images-assets.nasa.gov" && source.path.startsWith("/image/")) ||
            (source.host == "fastly.picsum.photos" && source.path.startsWith("/id/")) ||
            source.host in setOf("api.openverse.org", "live.staticflickr.com", "upload.wikimedia.org"))
        val connection = source.openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", if (url == HOME_ARTWORK_FEED_URL) "application/json" else "image/jpeg")
        // Refresh now must re-fetch image bytes as well as the feed, not read an
        // HTTP cache entry. The app's validated offline collection is separate.
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            check(connection.responseCode == 200) { "Museum artwork request failed" }
            require(connection.contentLengthLong <= maximumBytes)
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maximumBytes) { "Artwork response too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun readMetadata(): String? = metadata.takeIf { it.isFile && it.length() <= 1_000_000 }?.readText()

    override fun writeMetadata(value: String) = atomicWrite(metadata, value.encodeToByteArray())
    override fun readImage(id: String): ByteArray? = image(id).takeIf { it.isFile && it.length() <= 8_000_000 }?.readBytes()
    override fun writeImage(id: String, bytes: ByteArray) = atomicWrite(image(id), bytes)
    override fun retainImages(ids: Set<String>) {
        directory.listFiles()?.filter { it.name.endsWith(".jpg") && it.name.removeSuffix(".jpg") !in ids }
            ?.forEach { it.delete() }
    }

    private fun image(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        return File(directory, "$id.jpg")
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val output = android.util.AtomicFile(file)
        val stream = output.startWrite()
        try {
            stream.write(bytes)
            output.finishWrite(stream)
        } catch (failure: Exception) {
            output.failWrite(stream)
            throw failure
        }
    }
}
