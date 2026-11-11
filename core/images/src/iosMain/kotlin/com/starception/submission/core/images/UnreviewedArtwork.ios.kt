@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberArtworkReviewRepository(): ArtworkReviewRepository {
    val scope = rememberCoroutineScope()
    return remember(scope) { ArtworkReviewRepository(IosArtworkReviewPlatform(), scope) }
}

private fun safeReviewUrl(url: NSURL?): Boolean = url != null && url.scheme == "https" &&
    url.user == null && url.password == null && (url.port == null || url.port?.intValue == 443) &&
    allowedArtworkReviewDownload(url.absoluteString.orEmpty())

private class ReviewRedirects : NSObject(), NSURLSessionTaskDelegateProtocol {
    private var redirects = 0
    override fun URLSession(session: NSURLSession, task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse, newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit) {
        completionHandler(if (++redirects <= 3 && safeReviewUrl(newRequest.URL)) newRequest else null)
    }
}

private class IosArtworkReviewPlatform : ArtworkReviewPlatform {
    override suspend fun download(url: String, maximumBytes: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val target = requireNotNull(NSURL.URLWithString(url))
        require(safeReviewUrl(target))
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.timeoutIntervalForRequest = 20.0
        configuration.timeoutIntervalForResource = 30.0
        val session = NSURLSession.sessionWithConfiguration(configuration, ReviewRedirects(), null)
        val request = NSMutableURLRequest(uRL = target)
        request.setValue(if (artworkReviewMetadataUrl(url)) "application/json" else "image/jpeg", "Accept")
        request.setValue("StarceptionArtworkReview/1.0 (+https://github.com/nazmul-cs-us/nowinandroid)", "User-Agent")
        request.setValue("no-cache", "Cache-Control")
        val task = session.dataTaskWithRequest(request) { data, response, error ->
            if (continuation.isActive) {
                runCatching {
                    check(error == null && (response as? NSHTTPURLResponse)?.statusCode == 200L)
                    require(safeReviewUrl(response?.URL))
                    val body = requireNotNull(data)
                    require(body.length > 0u && body.length <= maximumBytes.toULong())
                    ByteArray(body.length.toInt()).also { result ->
                        result.usePinned { memcpy(it.addressOf(0), body.bytes, body.length) }
                    }
                }.fold(continuation::resume, continuation::resumeWithException)
            }
            session.finishTasksAndInvalidate()
        }
        continuation.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
        task.resume()
    }
}

@OptIn(ExperimentalResourceApi::class)
internal actual fun decodeArtworkReviewPreview(bytes: ByteArray): ImageBitmap {
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val image = requireNotNull(UIImage.imageWithData(data))
    image.size.useContents { require(width in 100.0..1800.0 && height in 100.0..1800.0) }
    val bitmap = bytes.decodeToImageBitmap()
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= 720) return bitmap
    val scale = 720f / longest
    val targetSize = IntSize((bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
    return ImageBitmap(targetSize.width, targetSize.height).also {
        Canvas(it).drawImageRect(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height), IntOffset.Zero, targetSize, Paint())
    }
}
