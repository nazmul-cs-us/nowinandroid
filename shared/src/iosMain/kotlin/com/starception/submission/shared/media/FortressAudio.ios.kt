package com.starception.submission.shared.audio

import com.starception.submission.core.assets.AssetPlatform
import com.starception.submission.shared.assets.ContentDownloadBus
import com.starception.submission.shared.assets.ContentDownloadProgressThrottle
import com.starception.submission.shared.assets.ContentDownloadStatus
import platform.Foundation.NSURL

actual suspend fun resolveFortressAudioUrl(url: String): String? {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    // Exact URL encoding avoids cache collisions and unsafe path characters.
    val encoded = url.encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    val key = "fortress/audio/" + encoded.chunked(120).joinToString("/") + "/recording.mp3"
    AssetPlatform.cachedPath(key)?.let { return NSURL.fileURLWithPath(it).absoluteString }
    val bannerKey = "fortress:$encoded"
    val throttle = ContentDownloadProgressThrottle()
    ContentDownloadBus.publishReciter(bannerKey, ContentDownloadStatus("Dua audio", 0.001f))
    return try {
        AssetPlatform.download(url, key) { done, total ->
            val fraction = if (total > 0) done.toFloat() / total else 0.001f
            if (throttle.shouldPublish(fraction)) ContentDownloadBus.publishReciter(
                bannerKey, ContentDownloadStatus("Dua audio", fraction))
        }?.let { NSURL.fileURLWithPath(it).absoluteString }
    } finally {
        ContentDownloadBus.publishReciter(bannerKey, null)
    }
}
