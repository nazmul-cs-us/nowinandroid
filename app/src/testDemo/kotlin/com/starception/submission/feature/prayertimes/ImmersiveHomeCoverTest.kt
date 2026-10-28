package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmersiveHomeCoverTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun coverOpensTheMatchingReader() {
        var openedPage = -1
        compose.setContent { CoverTestScene(dark = true, onOpen = { openedPage = it }) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover.png")
        compose.onNodeWithText("A moment to pause").performClick()
        assertEquals(0, openedPage)
    }

    @Test fun lightCoverFillsThePaddedHomeWidth() {
        compose.setContent { CoverTestScene(dark = false, onOpen = {}) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover_light.png")
    }

    @Test fun artworkTracksTheHorizontalSwipe() {
        val positions = mutableListOf<Float>()
        compose.setContent {
            CoverTestScene(dark = true, onOpen = {}, onScrollPositionChanged = positions::add)
        }
        compose.onNodeWithText("A moment to pause").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue("Artwork should move during the drag", positions.any { it > .05f && it < .95f })
        assertTrue("The next cover should settle", positions.lastOrNull()?.let { it > .95f } == true)
    }

    @Test fun swipingRightFromFirstCoverWrapsToTheLast() {
        val positions = mutableListOf<Float>()
        compose.setContent {
            CoverTestScene(dark = true, onOpen = {}, onScrollPositionChanged = positions::add)
        }
        compose.onNodeWithText("A moment to pause").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue("The previous cover should settle", positions.lastOrNull()?.let { it > 3.95f } == true)
        compose.onNodeWithText("A thought for today").fetchSemanticsNode()
    }

    @Test fun eachCoverOpensItsMatchingInsight() {
        val openedPages = mutableListOf<Int>()
        compose.setContent { CoverTestScene(dark = true, onOpen = openedPages::add) }
        val titles = listOf(
            "A moment to pause",
            "Make room for reflection",
            "Return to the Quran",
            "Turn toward Makkah",
            "A thought for today",
        )
        titles.forEachIndexed { index, title ->
            if (index > 0) {
                compose.onNodeWithText(titles[index - 1]).performTouchInput { swipeLeft() }
                compose.waitForIdle()
            }
            compose.onNodeWithText(title).performClick()
        }
        assertEquals(listOf(0, 1, 2, 3, 4), openedPages)
    }

    @Test fun swipingCoverUpdatesTheFollowupFeed() {
        val selectedPage = mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalDarkTheme provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column(Modifier.fillMaxWidth().height(852.dp)
                        .background(Color(0xFF080B14)).verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().height(550.dp)) {
                            ImmersiveHomeCover(
                                selectedPage = selectedPage.intValue,
                                isDarkTheme = true,
                                nextPrayerName = "Asr",
                                nextPrayerCountdown = "1h 41m",
                                onSelectionChanged = { selectedPage.intValue = it },
                                onOpen = {},
                            )
                        }
                        HomeReflectionPrompt(focusedPage = selectedPage.intValue, onClick = {})
                        HomeArtworkRail(focusedPage = selectedPage.intValue, onSelect = { _, _ -> })
                    }
                }
            }
        }
        compose.onNodeWithText("A moment to pause").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, selectedPage.intValue)
        compose.onNodeWithText("What will you carry into your next prayer?").fetchSemanticsNode()
        compose.onNodeWithText("Prayer now").fetchSemanticsNode()
    }

    @Test fun scrolledHomeFeedUsesCompactArtworkSections() {
        compose.setContent {
            CompositionLocalProvider(LocalDarkTheme provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column(Modifier.fillMaxWidth().height(950.dp)
                        .background(Color(0xFF080B14)).padding(24.dp)) {
                        HomeArtworkRail(onSelect = { _, _ -> })
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_feed.png")
    }

    @Test fun scrollingKeepsReflectionAndCardSectionsAligned() {
        compose.setContent {
            CompositionLocalProvider(LocalDarkTheme provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val scroll = rememberScrollState()
                    LaunchedEffect(scroll) { scroll.scrollTo(500) }
                    Column(Modifier.fillMaxWidth().height(852.dp)
                        .background(Color(0xFF080B14))
                        .verticalScroll(scroll)
                        .padding(horizontal = 24.dp)) {
                        Spacer(Modifier.height(588.dp))
                        HomeReflectionPrompt(onClick = {})
                        Spacer(Modifier.height(20.dp))
                        HomeArtworkRail(onSelect = { _, _ -> })
                        Spacer(Modifier.height(200.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val reflectionLeft = compose.onNodeWithText("PRAYER REFLECTION")
            .fetchSemanticsNode().boundsInRoot.left
        val cardsLeft = compose.onNodeWithText("EXPLORE YOUR DAY")
            .fetchSemanticsNode().boundsInRoot.left
        assertEquals(reflectionLeft, cardsLeft, .5f)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_scrolled.png")
    }

    @Test fun scrolledLightFeedCoversTheFixedCoverArtwork() {
        compose.setContent {
            CompositionLocalProvider(LocalDarkTheme provides false) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    val scroll = rememberScrollState()
                    val position = remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(scroll) { scroll.scrollTo(700) }
                    Box(Modifier.fillMaxWidth().height(852.dp).background(Color.White)) {
                        HomeCoverArtworkBackdrop(
                            position = position,
                            useNightArtwork = false,
                            isDarkTheme = false,
                            isExpanded = false,
                            scrollOffsetPx = scroll.value,
                            coverHeightPx = 588,
                        )
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp)) {
                            Spacer(Modifier.height(588.dp))
                            HomeReflectionPrompt(onClick = {})
                            Spacer(Modifier.height(20.dp))
                            HomeArtworkRail(onSelect = { _, _ -> })
                            Spacer(Modifier.height(300.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_scrolled_light.png")
    }
}

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun CoverTestScene(
    dark: Boolean,
    onOpen: (Int) -> Unit,
    onScrollPositionChanged: (Float) -> Unit = {},
) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxWidth().height(852.dp)) {
            val position = remember { mutableFloatStateOf(0f) }
            HomeCoverArtworkBackdrop(position, useNightArtwork = dark, isDarkTheme = dark, isExpanded = false)
            Column(Modifier.fillMaxSize().padding(top = 80.dp, start = 24.dp, end = 24.dp)) {
                Box(Modifier.requiredWidth(393.dp).height(588.dp)) {
                    ImmersiveHomeCover(
                        selectedPage = 0,
                        isDarkTheme = dark,
                        nextPrayerName = "Asr",
                        nextPrayerCountdown = "1h 41m",
                        onSelectionChanged = {},
                        onScrollPositionChanged = {
                            position.floatValue = it
                            onScrollPositionChanged(it)
                        },
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}
