package com.starception.submission.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HadithRecordingIntegrityTest {
    @Test
    fun existingNumberedDownloadDoesNotProveItMatchesTheRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val existing = context.filesDir.resolve("cdn_assets/audio/bukhari/bn/bukhari_0615.ogg")
        existing.parentFile!!.mkdirs()
        existing.writeText("An existing but unverified recording")
        try {
            val helper = AudioDownloadHelper(AssetDownloadManager(context, OkHttpClient()), context)
            assertNull(helper.resolveHadithAudioFile(615))
            val result = helper.downloadAudio("audio/bukhari/bn/bukhari_0615.ogg")
            assertTrue(result is AssetDownloadManager.DownloadState.Failed)
            assertTrue((result as AssetDownloadManager.DownloadState.Failed).error.contains("not verified"))
        } finally {
            existing.delete()
        }
    }
}
