package com.starception.submission.ui

import android.app.Application
import android.graphics.Bitmap
import com.starception.submission.core.images.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class HomeArtworkImageQualityTest {
    @Test fun approvalAndRestoreKeepWallpaperResolutionInsteadOfThe720PixelPickerPreview() = runTest {
        val bitmap = Bitmap.createBitmap(1600, 3000, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        bitmap.recycle()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val platform = MemoryArtworkPlatform()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeArtworkRepository(platform, scope = this, ioDispatcher = dispatcher)
        val candidate = UnreviewedArtwork(OnlineHomeArtwork("commons-test", "Forest", "Photographer",
            "https://commons.wikimedia.org/wiki/File:Forest.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/forest.jpg"), hash)
        repository.approveForDevice(candidate, ArtworkTheme.NATURE, bytes)
        val approved = repository.state.value.artworks.single().bitmap!!
        assertTrue("Home must not use the 720px picker bitmap", approved.height >= 2560 && approved.width >= 1440)

        val restored = HomeArtworkRepository(platform, scope = this, ioDispatcher = dispatcher)
        advanceUntilIdle()
        val cached = restored.state.value.artworks.single().bitmap!!
        assertTrue("Restoring the approved image must preserve detail", cached.height >= 2560 && cached.width >= 1440)
    }

    private class MemoryArtworkPlatform : HomeArtworkPlatform {
        private var metadata: String? = null
        private var approvals: String? = null
        private val images = mutableMapOf<String, ByteArray>()
        override suspend fun download(url: String, maximumBytes: Int): ByteArray = error("Test must not use the network")
        override fun readMetadata() = metadata
        override fun writeMetadata(value: String) { metadata = value }
        override fun readPersonalApprovals() = approvals
        override fun writePersonalApprovals(value: String) { approvals = value }
        override fun readImage(id: String) = images[id]
        override fun writeImage(id: String, bytes: ByteArray) { images[id] = bytes }
        override fun retainImages(ids: Set<String>) = Unit
    }
}
