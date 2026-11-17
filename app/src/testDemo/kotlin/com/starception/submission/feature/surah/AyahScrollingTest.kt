package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.feature.quran.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h600dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AyahScrollingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun narrationFollowsAndHighlightsUntilManualScrollingThenOffersAnExplicitFollowAction() {
        val narration = mutableStateOf<IntRange?>(null)
        lateinit var scrollGuide: AyahScrollGuide
        lateinit var state: LazyListState
        compose.setContent { MaterialTheme { Surface { Box(Modifier.height(480.dp)) {
            state = rememberLazyListState()
            val guide = rememberAyahScrollGuide(state, "1:en", true, narration.value, 0)
            scrollGuide = guide
            val fling = rememberAyahFlingBehavior(state, 0)
            LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize().testTag("ayah-list")) {
                items(10) { index -> AyahNarrationGroup(index + 1, narration.value?.contains(index + 1) == true) {
                    Column(Modifier.height(180.dp).padding(24.dp)) { Text("Ayah ${index + 1}"); Text("Tafsir for ayah ${index + 1}") }
                } }
            }
            AyahScrollHint(guide, narration.value != null, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        } } } }
        compose.runOnIdle { narration.value = 4..4 }
        compose.waitForIdle()
        compose.waitUntil(5000) { state.firstVisibleItemIndex == 3 && !state.isScrollInProgress }
        compose.runOnIdle {
            scrollGuide.scrollConnection.onPostScroll(Offset(0f, -40f), Offset.Zero,
                androidx.compose.ui.input.nestedscroll.NestedScrollSource.SideEffect)
        }
        assertTrue(scrollGuide.following)
        compose.onNodeWithTag("tafsir-ayah-4").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Narrating ayah 4"))
        compose.onNodeWithTag("ayah-list").performTouchInput {
            swipe(Offset(centerX, height * 0.7f), Offset(centerX, height * 0.45f), 700)
        }
        compose.onNodeWithText("Auto-follow paused").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/tafsir_follow_hint.png")
        compose.runOnIdle { narration.value = 6..6 }
        compose.waitForIdle()
        assertNotEquals(5, state.firstVisibleItemIndex)
        compose.onNodeWithText("Follow ayah").performClick()
        compose.waitForIdle()
        compose.waitUntil(5000) { state.firstVisibleItemIndex == 5 && !state.isScrollInProgress }
        compose.onNodeWithTag("tafsir-ayah-6").assertIsDisplayed()
    }
    @Test fun longAyahScrollsFreelyAndExplainsHowToKeepReading() {
        lateinit var state: LazyListState
        compose.setContent { MaterialTheme { Surface { Box(Modifier.height(480.dp)) {
            state = rememberLazyListState()
            val guide = rememberAyahScrollGuide(state, "long", true, null, 0)
            val fling = rememberAyahFlingBehavior(state, 0)
            LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize().testTag("ayah-list")) {
                items(2) { index -> Column(Modifier.height(1500.dp).padding(24.dp)) { Text("Ayah ${index + 1}"); Text("Long tafsir paragraph. ".repeat(80)) } }
            }
            AyahScrollHint(guide, false, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        } } } }
        compose.onNodeWithTag("ayah-list").performTouchInput {
            swipe(Offset(centerX, 280f), Offset(centerX, 240f), 1000)
        }
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        assertTrue("Long text must not snap back to the beginning", state.firstVisibleItemScrollOffset > 0)
        compose.onNodeWithText("Keep scrolling to read this ayah’s tafsir").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/tafsir_long_reading_hint.png")
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Keep scrolling to read this ayah’s tafsir").assertDoesNotExist()
    }
    @Test fun aShortAyahSettlesAtTheNextBoundary() {
        lateinit var state: LazyListState
        lateinit var fling: FlingBehavior
        lateinit var scope: CoroutineScope
        compose.setContent { MaterialTheme {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            fling = rememberAyahFlingBehavior(state, 0)
            LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.height(480.dp)) {
                items(10) { index -> Box(Modifier.height(180.dp)) { Text("Ayah ${index + 1}") } }
            }
        } }
        compose.runOnIdle { scope.launch {
            state.scrollToItem(0, 160)
            val scroll = object : ScrollScope { override fun scrollBy(pixels: Float) = state.dispatchRawDelta(pixels) }
            with(fling) { scroll.performFling(0f) }
        } }
        compose.waitUntil(5000) { state.firstVisibleItemIndex == 1 && state.firstVisibleItemScrollOffset == 0 }
    }
}
