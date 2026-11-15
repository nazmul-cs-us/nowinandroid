package com.starception.submission.feature.prayertimes

import android.app.Application
import com.starception.submission.core.images.AndroidArtworkReviewPlatform
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ArtworkReviewHttpContractTest {
    @Test fun openverseThumbnailNegotiatesAnApiRedirectBeforeDownloadingTheImage() = runTest {
        val thumbnail = "https://api.openverse.org/v1/images/test-image/thumb/"
        val image = "https://upload.wikimedia.org/wikipedia/commons/test-image.jpg"
        val bytes = byteArrayOf(1, 2, 3, 4)
        val requests = mutableListOf<URL>()
        val platform = AndroidArtworkReviewPlatform { url ->
            requests += url
            // Openverse negotiates an API renderer before issuing its redirect.
            // An image/jpeg-only Accept header yields 406 without reaching the CDN.
            object : HttpsURLConnection(url) {
                override fun getResponseCode(): Int {
                    val accept = getRequestProperty("Accept").orEmpty()
                    return if (url.host == "api.openverse.org") {
                        if (accept.contains("*/*") || accept.contains("application/json")) 302 else 406
                    } else 200
                }
                override fun getHeaderField(name: String): String? =
                    if (name.equals("Location", ignoreCase = true)) image else null
                override fun getContentLengthLong() = bytes.size.toLong()
                override fun getInputStream() = ByteArrayInputStream(bytes)
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getCipherSuite() = "TLS_TEST"
                override fun getLocalCertificates(): Array<Certificate>? = null
                override fun getServerCertificates(): Array<Certificate> = emptyArray()
            }
        }
        assertArrayEquals(bytes, platform.download(thumbnail, maximumBytes = 100))
        assertEquals(listOf(thumbnail, image), requests.map { it.toExternalForm() })
    }
}
