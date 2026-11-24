package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeReflectionSwipeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun directionsWrapAndRespectRtlWithoutReactingToSmallDrags() {
        assertEquals(1, homeReflectionSwipeTarget(0, -100f, 56f, false))
        assertEquals(4, homeReflectionSwipeTarget(0, 100f, 56f, false))
        assertEquals(0, homeReflectionSwipeTarget(4, -100f, 56f, false))
        assertEquals(4, homeReflectionSwipeTarget(0, -100f, 56f, true))
        assertEquals(1, homeReflectionSwipeTarget(0, 100f, 56f, true))
        assertNull(homeReflectionSwipeTarget(0, 10f, 56f, false))
        assertNull(homeReflectionSwipeTarget(0, Float.NaN, 56f, false))
    }

    @Test fun lowerAreaSwipesChangePagesWithoutOpeningThePassage() {
        var page by mutableIntStateOf(0)
        var opened = 0
        compose.setContent {
            MaterialTheme { HomeReflectionPrompt(focusedPage = page, scrollPosition = page.toFloat(),
                onSwipePage = { page = it }, onClick = { opened++ }) }
        }
        compose.onNodeWithTag("homeReflectionSwipeArea").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, page); assertEquals(0, opened) }
        compose.onNodeWithTag("homeReflectionSwipeArea").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, page); assertEquals(0, opened) }
        compose.onNodeWithText(homeFeedPage(0).prompt).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun verticalGestureStillScrollsTheHomeFeed() {
        lateinit var scroll: ScrollState
        var pageChanges = 0
        compose.setContent {
            scroll = rememberScrollState()
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    HomeReflectionPrompt(onSwipePage = { pageChanges++ }, onClick = {})
                    Spacer(Modifier.height(1500.dp))
                }
            }
        }
        compose.onNodeWithTag("homeReflectionSwipeArea").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scroll.value > 0); assertEquals(0, pageChanges) }
    }

    @Test fun fastShortFlickRegistersWithoutRequiringALongDrag() {
        var page by mutableIntStateOf(0)
        var opened = 0
        compose.setContent {
            MaterialTheme { HomeReflectionPrompt(focusedPage = page, scrollPosition = page.toFloat(),
                onSwipePage = { page = it }, onClick = { opened++ }) }
        }
        compose.onNodeWithTag("homeReflectionSwipeArea").performTouchInput {
            swipe(start = Offset(width * .65f, centerY), end = Offset(width * .57f, centerY), durationMillis = 40)
        }
        compose.runOnIdle { assertEquals(1, page); assertEquals(0, opened) }
    }

    @Test fun slowSmallDragDoesNotAccidentallySwitchPages() {
        var pageChanges = 0
        compose.setContent {
            MaterialTheme { HomeReflectionPrompt(onSwipePage = { pageChanges++ }, onClick = {}) }
        }
        compose.onNodeWithTag("homeReflectionSwipeArea").performTouchInput {
            swipe(start = Offset(width * .65f, centerY), end = Offset(width * .59f, centerY), durationMillis = 1000)
        }
        compose.runOnIdle { assertEquals(0, pageChanges) }
        assertEquals(1, homeReflectionSwipeTarget(0, -12f, 32f, false, -900f, 500f, 6f))
        assertNull(homeReflectionSwipeTarget(0, -3f, 32f, false, -900f, 500f, 6f))
    }
}
