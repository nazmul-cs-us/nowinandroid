package com.starception.submission.shared.audio

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.downloadTaskWithURL
import kotlin.coroutines.resume

/**
 * Downloads a reciter's MP3 before handing its local file URL to AVAudioFile.
 * Unlike AVPlayer, the current iOS QuranAudioPlayer requires local audio files.
 */
suspend fun downloadIosReciterSurah(
    reciter: QuranReciterSource,
    surah: Int,
): String? {
    val remote = reciter.urlForSurah(surah) ?: return null
    val root = NSTemporaryDirectory().trimEnd('/') + "/deenly-reciter-audio/" + reciter.id
    val files = NSFileManager.defaultManager
    if (!files.fileExistsAtPath(root)) {
        if (!files.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)) return null
    }
    val target = root + "/" + surah.toString().padStart(3, '0') + ".mp3"
    if (files.fileExistsAtPath(target)) return NSURL.fileURLWithPath(target).absoluteString
    val source = NSURL.URLWithString(remote) ?: return null
    return suspendCancellableCoroutine { continuation ->
        val task = NSURLSession.sharedSession.downloadTaskWithURL(source) { location, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            val result = if (error == null && status in 200..299 && location != null) {
                if (files.moveItemAtURL(location, NSURL.fileURLWithPath(target), null)) {
                    NSURL.fileURLWithPath(target).absoluteString
                } else null
            } else null
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}
