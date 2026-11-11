@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import platform.CoreCrypto.CC_SHA256
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val onlineRepository by lazy { HomeArtworkRepository(IosHomeArtworkPlatform()) }

@Composable
actual fun rememberHomeArtworkRepository(): HomeArtworkRepository = onlineRepository

internal actual fun artworkSha256(bytes: ByteArray): String {
    require(bytes.isNotEmpty())
    val digest = UByteArray(32)
    bytes.usePinned { source -> digest.usePinned { target ->
        CC_SHA256(source.addressOf(0), bytes.size.toUInt(), target.addressOf(0))
    } }
    return digest.joinToString("") { it.toString(16).padStart(2, '0') }
}

private class IosHomeArtworkPlatform : HomeArtworkPlatform {
    private val directory = NSHomeDirectory() + "/Library/Application Support/home-artwork-online"
    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    override suspend fun download(url: String, maximumBytes: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val target = requireNotNull(NSURL.URLWithString(url))
        require(allowedArtworkDownload(url))
        require(target.scheme == "https" && target.user == null && target.password == null &&
            (target.port == null || target.port?.intValue == 443) &&
            (url == HOME_ARTWORK_FEED_URL || (target.host == "images.metmuseum.org" && target.path?.startsWith("/CRDImages/is/web-large/") == true) ||
                (target.host == "www.clevelandart.org" && target.path == "/_next/image") ||
                target.host == "openaccess-cdn.clevelandart.org" ||
                (target.host == "images-assets.nasa.gov" && target.path?.startsWith("/image/") == true) ||
                (target.host == "fastly.picsum.photos" && target.path?.startsWith("/id/") == true) ||
                target.host in setOf("api.openverse.org", "live.staticflickr.com", "upload.wikimedia.org")))
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.timeoutIntervalForRequest = 20.0
        configuration.timeoutIntervalForResource = 30.0
        val session = NSURLSession.sessionWithConfiguration(configuration)
        val request = NSMutableURLRequest(uRL = target)
        request.setValue(if (url == HOME_ARTWORK_FEED_URL) "application/json" else "image/jpeg", "Accept")
        request.setValue("no-cache", "Cache-Control")
        val task = session.dataTaskWithRequest(request) { data, response, error ->
            if (continuation.isActive) {
                val result = runCatching {
                    check(error == null && (response as? NSHTTPURLResponse)?.statusCode == 200L)
                    check(response?.URL?.host == target.host && response.URL?.scheme == "https")
                    requireNotNull(data).bytes(maximumBytes)
                }
                result.fold(continuation::resume, continuation::resumeWithException)
            }
            session.finishTasksAndInvalidate()
        }
        continuation.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
        task.resume()
    }

    override fun readMetadata(): String? = NSData.dataWithContentsOfFile("$directory/collection.json")?.bytes(1_000_000)?.decodeToString()
    override fun writeMetadata(value: String) = write("$directory/collection.json", value.encodeToByteArray())
    override fun readImage(id: String): ByteArray? = NSData.dataWithContentsOfFile(image(id))?.bytes(8_000_000)
    override fun writeImage(id: String, bytes: ByteArray) = write(image(id), bytes)
    override fun retainImages(ids: Set<String>) {
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, null)?.forEach { item ->
            val file = item as? String ?: return@forEach
            if (file.endsWith(".jpg") && file.removeSuffix(".jpg") !in ids) {
                NSFileManager.defaultManager.removeItemAtPath("$directory/$file", null)
            }
        }
    }

    private fun image(id: String): String {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        return "$directory/$id.jpg"
    }

    private fun write(path: String, bytes: ByteArray) {
        bytes.usePinned {
            check(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true))
        }
    }

    private fun NSData.bytes(limit: Int): ByteArray {
        require(length > 0u && length <= limit.toULong())
        return ByteArray(length.toInt()).also { array ->
            array.usePinned { memcpy(it.addressOf(0), bytes, length) }
        }
    }
}
