package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.images.ArtworkReviewRepository
import com.starception.submission.core.images.ArtworkTheme
import com.starception.submission.core.images.LocalArtworkReviewRepository
import com.starception.submission.core.images.LocalHomeArtworkRepository
import com.starception.submission.core.images.HomeArtworkRepository
import com.starception.submission.settings.components.HomeArtworkReviewControls
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    private fun render(dark: Boolean = true) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            platform = remember { ReviewPlatformFixture() }
            repository = remember { ArtworkReviewRepository(platform, scope, dispatcher = Dispatchers.Main.immediate, initialPageOffset = 0) }
            homePlatform = remember { OnlineHomeArtworkTest.Platform() }
            home = remember { HomeArtworkRepository(homePlatform, scope, ioDispatcher = Dispatchers.Main.immediate) }
            CompositionLocalProvider(LocalArtworkReviewRepository provides repository, LocalHomeArtworkRepository provides home) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) { HomeArtworkReviewControls(ArtworkTheme.NATURE) }
            }
        }
    }

    private fun openGallery() {
        compose.onNodeWithTag("homeArtworkReviewOpen").performClick()
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

    @Test fun galleryIsDirectlyAccessibleWithoutAModeSwitchOrConsentDialog() {
        render()
        compose.onNodeWithTag("homeArtworkRestrictedMode").assertDoesNotExist()
        compose.onNodeWithTag("homeArtworkReviewConsent").assertDoesNotExist()
        compose.onNodeWithText("Choose artwork").assertExists()
        compose.runOnIdle { assertTrue(platform.requests.isEmpty()) }
        openGallery()
        compose.onNodeWithText("View unapproved images?").assertDoesNotExist()
    }

    @Test fun openingTheGalleryFetchesPreviewsAndCanFetchMore() {
        render()
        openGallery()
        compose.onNodeWithTag("homeArtworkReviewGallery").assertExists()
        compose.onNodeWithText("Preview only until you approve.", substring = true).assertExists()
        compose.onAllNodesWithText("Not approved")[0].assertExists()
        val bounds = compose.onNodeWithTag("homeArtworkReviewGallery").getUnclippedBoundsInRoot()
        assertEquals(844f, (bounds.bottom - bounds.top).value, 1f)
        compose.onNodeWithTag("homeArtworkReviewGallery").captureRoboImage("src/testDemo/screenshots/home_artwork_choose_dark.png")
        val first = repository.state.value.candidates.map { it.artwork.id }.toSet()
        compose.onNodeWithTag("homeArtworkReviewFetchMore").performClick()
        compose.waitUntil(15_000) { !repository.state.value.loading }
        compose.runOnIdle {
            assertEquals(5, repository.state.value.candidates.size)
            assertTrue(first.intersect(repository.state.value.candidates.map { it.artwork.id }.toSet()).isEmpty())
        }
    }

    @Test fun closingTheGalleryClearsItsSessionAndAllowsReopening() {
        render()
        openGallery()
        compose.onNodeWithTag("homeArtworkReviewRestrict").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("homeArtworkReviewGallery").assertDoesNotExist()
        compose.onNodeWithTag("homeArtworkReviewOpen").assertExists()
        compose.runOnIdle { assertTrue(repository.state.value.candidates.isEmpty()); assertFalse(repository.state.value.loading) }
        openGallery()
    }

    @Test fun manuallyDismissingAPreviewDoesNotApproveIt() {
        render()
        openGallery()
        compose.onAllNodesWithText("Skip")[0].performClick()
        compose.runOnIdle { assertEquals(4, repository.state.value.candidates.size) }
        compose.onNodeWithText("Preview only until you approve.", substring = true).assertExists()
    }

    @Test @Config(qualifiers = "w844dp-h390dp-land-mdpi")
    fun landscapeKeepsThePreviewAndActionsReachable() {
        render(dark = false)
        openGallery()
        compose.onNodeWithTag("homeArtworkApprove").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("homeArtworkReviewFetchMore").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Not approved").performScrollTo()
        compose.onNodeWithTag("homeArtworkReviewGallery").captureRoboImage("src/testDemo/screenshots/home_artwork_choose_landscape.png")
    }

    @Test @Config(qualifiers = "w320dp-h852dp-mdpi")
    fun largeTextApprovalKeepsBothCheckedOptionsAndConfirmActionReachable() {
        RuntimeEnvironment.setFontScale(1.5f)
        render()
        openGallery()
        compose.onNodeWithTag("homeArtworkApprove").performScrollTo().performClick()
        compose.onNodeWithTag("homeArtworkConfirmContent").performScrollTo().assertIsOn()
        compose.onNodeWithTag("homeArtworkConfirmRights").performScrollTo().assertIsOn()
        compose.onNodeWithTag("homeArtworkApproveConfirm").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("homeArtworkApprovalDialog").assertIsDisplayed()
        compose.onNodeWithTag("homeArtworkApproveConfirm").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, home.state.value.artworks.size) }
    }

    @Test fun approvalStartsCheckedAndSavesOnlyAfterExplicitApproval() {
        render()
        openGallery()
        compose.onAllNodesWithTag("homeArtworkApprove")[0].performScrollTo().performClick()
        compose.onNodeWithTag("homeArtworkConfirmContent").assertIsOn()
        compose.onNodeWithTag("homeArtworkConfirmRights").assertIsOn()
        compose.onNodeWithTag("homeArtworkApproveConfirm").assertIsEnabled()
        compose.runOnIdle { assertTrue(home.state.value.artworks.isEmpty()) }
        compose.onNodeWithTag("homeArtworkConfirmContent").performClick().assertIsOff()
        compose.onNodeWithTag("homeArtworkApproveConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("homeArtworkConfirmContent").performClick()
        compose.onNodeWithTag("homeArtworkConfirmRights").performClick().assertIsOff()
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
