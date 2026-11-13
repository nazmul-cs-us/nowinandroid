package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.images.ArtworkReviewRepository
import com.starception.submission.core.images.ArtworkTheme
import com.starception.submission.core.images.LocalArtworkReviewRepository
import com.starception.submission.core.images.LocalHomeArtworkRepository
import com.starception.submission.core.images.HomeArtworkRepository
import com.starception.submission.settings.components.HomeArtworkReviewControls
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h844dp-mdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkReviewControlsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var platform: ReviewPlatformFixture
    private lateinit var repository: ArtworkReviewRepository
    private lateinit var homePlatform: OnlineHomeArtworkTest.Platform
    private lateinit var home: HomeArtworkRepository

    private fun render() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            platform = remember { ReviewPlatformFixture() }
            repository = remember { ArtworkReviewRepository(platform, scope, dispatcher = Dispatchers.Main.immediate, initialPageOffset = 0) }
            homePlatform = remember { OnlineHomeArtworkTest.Platform() }
            home = remember { HomeArtworkRepository(homePlatform, scope, ioDispatcher = Dispatchers.Main.immediate) }
            CompositionLocalProvider(LocalArtworkReviewRepository provides repository, LocalHomeArtworkRepository provides home) {
                MaterialTheme(colorScheme = darkColorScheme()) { HomeArtworkReviewControls(ArtworkTheme.NATURE) }
            }
        }
    }

    private fun openGallery() {
        compose.onNodeWithTag("homeArtworkRestrictedMode").performClick()
        compose.onNodeWithTag("homeArtworkReviewConsent").performClick()
        compose.onNodeWithTag("homeArtworkReviewGallery").assertExists()
        compose.waitForIdle()
        try {
            compose.waitUntil(15_000) { platform.requests.isNotEmpty() && !repository.state.value.loading }
        } catch (error: Throwable) {
            throw AssertionError("Review state=${repository.state.value}; requests=${platform.requests}", error)
        }
        compose.runOnIdle {
            assertEquals("Review state=${repository.state.value}; requests=${platform.requests}", 5, repository.state.value.candidates.size)
        }
    }

    @Test fun restrictedDefaultAndCancelledConsentNeverFetchUnapprovedImages() {
        render()
        compose.onNodeWithTag("homeArtworkRestrictedMode").assertIsOn()
        compose.onNodeWithTag("homeArtworkRestrictedMode").performClick()
        compose.onNodeWithText("View unapproved images?").assertExists()
        compose.runOnIdle { assertTrue(platform.requests.isEmpty()) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("homeArtworkRestrictedMode").assertIsOn()
        compose.onNodeWithTag("homeArtworkReviewGallery").assertDoesNotExist()
        compose.runOnIdle { assertTrue(platform.requests.isEmpty()) }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun confirmingUnrestrictedFetchesASeparateClearlyLabelledManualReviewGallery() {
        render()
        openGallery()
        compose.onNodeWithTag("homeArtworkReviewGallery").assertExists()
        compose.onNodeWithText("Preview only until you approve.", substring = true).assertExists()
        compose.onAllNodesWithText("Not approved")[0].assertExists()
        val file = File(System.getProperty("java.io.tmpdir"), "opencode/unapproved-artwork-review.png")
        file.parentFile.mkdirs()
        compose.onNodeWithTag("homeArtworkReviewGallery").captureRoboImage(file.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val first = repository.state.value.candidates.map { it.artwork.id }.toSet()
        compose.onNodeWithTag("homeArtworkReviewFetchMore").performClick()
        compose.waitUntil(15_000) { !repository.state.value.loading }
        compose.runOnIdle {
            assertEquals(5, repository.state.value.candidates.size)
            assertTrue(first.intersect(repository.state.value.candidates.map { it.artwork.id }.toSet()).isEmpty())
        }
    }

    @Test fun restrictingAgainClearsImagesAndStopsTheReviewSession() {
        render()
        openGallery()
        compose.onNodeWithTag("homeArtworkReviewRestrict").performClick()
        compose.onNodeWithTag("homeArtworkReviewGallery").assertDoesNotExist()
        compose.onNodeWithTag("homeArtworkRestrictedMode").assertIsOn()
        compose.onNodeWithTag("homeArtworkReviewOpen").assertDoesNotExist()
        compose.runOnIdle { assertTrue(repository.state.value.candidates.isEmpty()); assertFalse(repository.state.value.loading) }
    }

    @Test fun manuallyDismissingAPreviewDoesNotApproveIt() {
        render()
        openGallery()
        compose.onAllNodesWithText("Skip")[0].performClick()
        compose.runOnIdle { assertEquals(4, repository.state.value.candidates.size) }
        compose.onNodeWithText("Preview only until you approve.", substring = true).assertExists()
    }

    @Test fun approvalRequiresBothConfirmationsAndAddsAnImageOnlyToThisDevice() {
        render()
        openGallery()
        compose.onAllNodesWithTag("homeArtworkApprove")[0].performClick()
        compose.onNodeWithTag("homeArtworkApproveConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("homeArtworkConfirmContent").performClick()
        compose.onNodeWithTag("homeArtworkApproveConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("homeArtworkConfirmRights").performClick()
        compose.onNodeWithTag("homeArtworkApproveConfirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Approved on this device · Added to Home").assertExists()
        compose.runOnIdle {
            assertEquals(1, home.state.value.artworks.size)
            assertTrue(home.state.value.artworks.single().id.startsWith("personal-"))
            assertNotNull(homePlatform.personalMetadata)
            assertTrue(homePlatform.requests.isEmpty())
        }
    }
}
