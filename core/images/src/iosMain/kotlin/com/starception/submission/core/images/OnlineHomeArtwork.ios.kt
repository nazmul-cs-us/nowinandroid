@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val onlineRepository by lazy { HomeArtworkRepository(IosHomeArtworkPlatform()) }

@Composable
actual fun rememberHomeArtworkRepository(): HomeArtworkRepository = onlineRepository

private class IosHomeArtworkPlatform : HomeArtworkPlatform {
    private val directory = NSHomeDirectory() + "/Library/Application Support/home-artwork-online"
    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    override suspend fun download(url: String, maximumBytes: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val target = requireNotNull(NSURL.URLWithString(url))
        require(target.scheme == "https" &&
            ((target.host == "www.getty.edu" && target.path == "/art/collection/api/search") ||
                (target.host == "media.getty.edu" && target.path?.startsWith("/iiif/image/") == true)))
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.timeoutIntervalForRequest = 20.0
        configuration.timeoutIntervalForResource = 30.0
        val session = NSURLSession.sessionWithConfiguration(configuration)
        val request = NSMutableURLRequest(uRL = target)
        request.setValue(if (target.host == "media.getty.edu") "image/jpeg" else "application/json", "Accept")
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
