package com.starception.submission.family

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.starception.submission.core.designsystem.theme.NiaTheme
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h844dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class FamilyDraggableVideoPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun state() = FamilyUiState(me = "omar@xmpp.starception.com", camera = true,
        call = FamilyCall("active-video", "omar@xmpp.starception.com", "amina.khan@xmpp.starception.com",
            "video", "active", 1, Long.MAX_VALUE, true))

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertContained(outer: Rect, inner: Rect) {
        assertTrue("Preview escaped its safe bounds: $outer / $inner",
            inner.left >= outer.left - 1f && inner.top >= outer.top - 1f &&
                inner.right <= outer.right + 1f && inner.bottom <= outer.bottom + 1f)
    }

    @Test fun draggingMovesThePreviewWithoutRestartingItsRendererOrBlockingHangup() {
        var rendererStarts = 0
        var hangups = 0
        compose.setContent { NiaTheme {
            FamilyCallContent(state(), localReady = true, remoteReady = true,
                actions = FamilyCallActions(hangup = { hangups++ }),
                localThumbnail = {
                    DisposableEffect(Unit) { rendererStarts++; onDispose {} }
                    Box(Modifier.fillMaxSize().background(Color(0xFF527A72)))
                })
        } }
        val before = bounds("familyVideoSelfPreview")
        compose.onNodeWithTag("familyVideoSelfPreview").performTouchInput {
            swipe(center, center + Offset(-500f, 500f), 600)
        }
        val after = bounds("familyVideoSelfPreview")
        assertTrue(after.left < before.left)
        assertTrue(after.top > before.top)
        assertContained(bounds("familyVideoPreviewBounds"), after)
        assertFalse(after.overlaps(bounds("familyVideoShelf")))
        compose.runOnIdle { assertEquals(1, rendererStarts) }
        compose.onNodeWithContentDescription("End call").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, hangups) }
        compose.onRoot().captureFamilyPreview("video-call-preview-dragged")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun landscapeDraggingStaysInsideVideoAndAwayFromTheSideControls() {
        compose.setContent { NiaTheme { FamilyCallContent(state(), localReady = false, remoteReady = true) } }
        compose.onNodeWithTag("familyVideoSelfPreview").performTouchInput {
            swipe(center, center + Offset(-2_000f, 2_000f), 600)
        }
        val preview = bounds("familyVideoSelfPreview")
        assertContained(bounds("familyVideoPreviewBounds"), preview)
        assertFalse(preview.overlaps(bounds("familyVideoShelf")))
        compose.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    @Test fun cameraOffAndCallStatusUpdatesKeepTheChosenPosition() {
        val ui = mutableStateOf(state())
        compose.setContent { NiaTheme { FamilyCallContent(ui.value, localReady = true, remoteReady = true) } }
        compose.onNodeWithTag("familyVideoSelfPreview").performTouchInput {
            swipe(center, center + Offset(-500f, 300f), 600)
        }
        val moved = bounds("familyVideoSelfPreview")
        compose.runOnIdle { ui.value = ui.value.copy(camera = false, muted = true, mediaConnected = true) }
        assertEquals(moved, bounds("familyVideoSelfPreview"))
        compose.onNodeWithContentDescription("Your camera is off").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w700dp-h844dp-mdpi")
    fun resizedVideoReprojectsThePositionWithinItsNewBounds() {
        val landscape = mutableStateOf(false)
        compose.setContent {
            val width = if (landscape.value) 600.dp else 300.dp
            val height = if (landscape.value) 240.dp else 600.dp
            Box(Modifier.size(width, height)) {
                FamilyDraggableVideoPreview("same-call", 88.dp, 112.dp) { Box(Modifier.fillMaxSize()) }
            }
        }
        compose.onNodeWithTag("familyVideoSelfPreview").performTouchInput {
            swipe(center, center + Offset(-500f, 1_000f), 600)
        }
        compose.runOnIdle { landscape.value = true }
        val safe = bounds("familyVideoPreviewBounds")
        val preview = bounds("familyVideoSelfPreview")
        assertContained(safe, preview)
        assertEquals(safe.left, preview.left, 1f)
        assertEquals(safe.bottom, preview.bottom, 1f)
    }

    @Test fun incomingCallNeverCreatesADraggableCameraBeforeAcceptance() {
        val incoming = state().copy(me = "amina.khan@xmpp.starception.com",
            call = state().call?.copy(stage = "ringing"))
        var cameraRenders = 0
        compose.setContent { NiaTheme {
            FamilyCallContent(incoming, localReady = true, remoteReady = true,
                localThumbnail = { cameraRenders++ })
        } }
        compose.onNodeWithTag("familyVideoSelfPreview").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, cameraRenders) }
        compose.onNodeWithText("Accept").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun largeTextKeepsThePreviewOutsideCallControls() {
        compose.setContent { NiaTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                FamilyCallContent(state(), localReady = false, remoteReady = true)
            }
        } }
        val preview = bounds("familyVideoSelfPreview")
        assertTrue(preview.width > 0 && preview.height > 0)
        assertContained(bounds("familyVideoPreviewBounds"), preview)
        assertFalse(preview.overlaps(bounds("familyVideoShelf")))
        compose.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    @Test fun aNewCallResetsThePreviewToTheDefaultCorner() {
        val callId = mutableStateOf("first-call")
        compose.setContent {
            Box(Modifier.size(300.dp, 500.dp)) {
                FamilyDraggableVideoPreview(callId.value, 88.dp, 112.dp) { Box(Modifier.fillMaxSize()) }
            }
        }
        compose.onNodeWithTag("familyVideoSelfPreview").performTouchInput {
            swipe(center, center + Offset(-500f, 500f), 600)
        }
        compose.runOnIdle { callId.value = "second-call" }
        val safe = bounds("familyVideoPreviewBounds")
        val preview = bounds("familyVideoSelfPreview")
        assertEquals(safe.right, preview.right, 1f)
        assertEquals(safe.top, preview.top, 1f)
    }

    @Test fun offsetMathClampsBothEdgesAndVerySmallWindows() {
        assertEquals(Offset(0f, 150f), videoPreviewOffset(Offset(-1f, 2f), Size(200f, 300f), Size(100f, 150f)))
        assertEquals(Offset.Zero, videoPreviewOffset(Offset(1f, 1f), Size(40f, 40f), Size(88f, 112f)))
    }
}
