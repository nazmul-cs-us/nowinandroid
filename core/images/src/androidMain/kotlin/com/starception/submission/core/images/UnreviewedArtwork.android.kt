package com.starception.submission.core.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
actual fun rememberArtworkReviewRepository(): ArtworkReviewRepository {
    val scope = rememberCoroutineScope()
    return remember(scope) { ArtworkReviewRepository(AndroidArtworkReviewPlatform(), scope) }
}

internal class AndroidArtworkReviewPlatform : ArtworkReviewPlatform {
    override suspend fun download(url: String, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        var target = URL(url)
        repeat(4) {
            val address = target.toExternalForm()
            require(allowedArtworkReviewDownload(address))
            require(target.protocol == "https" && target.userInfo == null && target.port in listOf(-1, 443))
            val connection = target.openConnection() as HttpsURLConnection
            Log.i("ArtworkReview", "Starting preview request host=${target.host}")
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "StarceptionArtworkReview/1.0 (+https://github.com/nazmul-cs-us/nowinandroid)")
            connection.setRequestProperty("Accept", if (artworkReviewMetadataUrl(address)) "application/json" else "image/jpeg")
            connection.setRequestProperty("Cache-Control", "no-cache")
            try {
                val status = connection.responseCode
                Log.i("ArtworkReview", "Preview-only request host=${target.host} status=$status")
                if (status in listOf(301, 302, 303, 307, 308)) {
                    target = URL(target, requireNotNull(connection.getHeaderField("Location")))
                    return@repeat
                }
                check(status == 200) { "Artwork review request failed" }
                require(connection.contentLengthLong <= maximumBytes)
                return@withContext connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= maximumBytes) { "Artwork preview response too large" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray().also { require(it.isNotEmpty()) }
                }
            } catch (failure: Exception) {
                Log.w("ArtworkReview", "Preview request failed host=${target.host} reason=${failure.javaClass.simpleName}")
                throw failure
            } finally { connection.disconnect() }
        }
        error("Too many artwork preview redirects")
    }
}

internal actual fun decodeArtworkReviewPreview(bytes: ByteArray): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 100..1800 && bounds.outHeight in 100..1800)
    val maximum = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maximum / sample > 1440) sample *= 2
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }))
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= 720) return bitmap.asImageBitmap()
    val scale = 720f / longest
    val preview = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    if (preview !== bitmap) bitmap.recycle()
    return preview.asImageBitmap()
}
