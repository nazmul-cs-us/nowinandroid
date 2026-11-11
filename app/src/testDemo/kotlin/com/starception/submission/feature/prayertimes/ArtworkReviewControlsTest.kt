package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsOn
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

    private fun render() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            platform = remember { ReviewPlatformFixture() }
            repository = remember { ArtworkReviewRepository(platform, scope, dispatcher = Dispatchers.Main.immediate, initialPageOffset = 0) }
            CompositionLocalProvider(LocalArtworkReviewRepository provides repository) {
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
        compose.onNodeWithText("Nothing here is approved for Home.", substring = true).assertExists()
        compose.onAllNodesWithText("UNREVIEWED", substring = true)[0].assertExists()
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
        compose.onAllNodesWithText("Dismiss this preview")[0].performScrollTo().performClick()
        compose.runOnIdle { assertEquals(4, repository.state.value.candidates.size) }
        compose.onNodeWithText("Nothing here is approved for Home.", substring = true).assertExists()
    }
}
