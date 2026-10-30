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
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertFalse
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

    @Test fun coverCopyNamesTheContentOpenedByEachPage() {
        fun copy(page: Int) = homeCoverCopy(page, "Fajr", "Dhuhr", "2h 10m", "Al-Kahf", 18)
        assertEquals("Make time for Fajr", copy(0).title)
        assertEquals("Next · Dhuhr in 2h 10m", copy(0).detail)
        assertEquals("Hadith for Fajr", copy(1).title)
        assertEquals("Read Al-Kahf", copy(2).title)
        assertEquals("Surah 18 · today's ayahs", copy(2).detail)
        assertEquals("Live compass toward Makkah", copy(3).detail)
        assertEquals("AI SUGGESTED", copy(4).eyebrow)
        assertEquals("Quran, Hadith or dua for this moment", copy(4).detail)
    }

    @Test fun greetingFollowsTimeOfDay() {
        assertEquals("Salam, morning", homeGreetingForHour(5))
        assertEquals("Salam, afternoon", homeGreetingForHour(12))
        assertEquals("Salam, evening", homeGreetingForHour(18))
    }

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun coverOpensTheMatchingReader() {
        var openedPage = -1
        compose.setContent { CoverTestScene(dark = true, onOpen = { openedPage = it }) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover.png")
        compose.onNodeWithText("Make time for Fajr").performClick()
        assertEquals(0, openedPage)
    }

    @Test fun lightCoverFillsThePaddedHomeWidth() {
        compose.setContent { CoverTestScene(dark = false, onOpen = {}) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover_light.png")
        assertTrue(homeCoverCopyUsesLightText(page = 0, useNightArtwork = false, isDarkTheme = false))
    }

    @Test fun nightArtworkKeepsReadableCopyInLightMode() {
        compose.setContent { CoverTestScene(dark = false, useNightArtwork = true, onOpen = {}) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover_night_light_mode.png")
        assertTrue(homeCoverHeaderUsesLightText(page = 0, useNightArtwork = true))
        assertTrue(homeCoverCopyUsesLightText(page = 0, useNightArtwork = true, isDarkTheme = false))
        assertTrue(homeCoverHeaderUsesLightText(page = 0, useNightArtwork = false))
        assertFalse(homeCoverHeaderUsesLightText(page = 2, useNightArtwork = false))
    }

    @Test fun daytimePrayerCoverShowsSunBetweenDatePalms() {
        compose.setContent {
            CoverTestScene(dark = false, epochSeconds = 1_793_174_400.0, onOpen = {})
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_prayer_sun.png")
    }

    @Test fun nighttimePrayerCoverShowsMoonBetweenDatePalms() {
        compose.setContent {
            CoverTestScene(dark = true, epochSeconds = 1_793_214_000.0, onOpen = {})
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_prayer_moon.png")
    }

    @Test fun artworkTracksTheHorizontalSwipe() {
        val positions = mutableListOf<Float>()
        compose.setContent {
            CoverTestScene(dark = true, onOpen = {}, onScrollPositionChanged = positions::add)
        }
        compose.onNodeWithText("Make time for Fajr").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue("Artwork should move during the drag", positions.any { it > .05f && it < .95f })
        assertTrue("The next cover should settle", positions.lastOrNull()?.let { it > .95f } == true)
    }

    @Test fun pageMarksExposeAllFourSwipeDestinations() {
        compose.setContent { CoverTestScene(dark = true, onOpen = {}) }
        (1..5).forEach { page ->
            compose.onNodeWithContentDescription("Show insight $page of 5").assertExists()
        }
        compose.onNodeWithContentDescription("Show insight 5 of 5").performClick()
        compose.onNodeWithText("A verified reading").assertExists()
    }

    @Test fun swipingRightFromFirstCoverWrapsToTheLast() {
        val positions = mutableListOf<Float>()
        compose.setContent {
            CoverTestScene(dark = true, onOpen = {}, onScrollPositionChanged = positions::add)
        }
        compose.onNodeWithText("Make time for Fajr").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue("The previous cover should settle", positions.lastOrNull()?.let { it > 3.95f } == true)
        compose.onNodeWithText("A verified reading").fetchSemanticsNode()
    }

    @Test fun eachCoverOpensItsMatchingInsight() {
        val openedPages = mutableListOf<Int>()
        compose.setContent { CoverTestScene(dark = true, onOpen = openedPages::add) }
        val titles = listOf(
            "Make time for Fajr",
            "Hadith for Fajr",
            "Read Al-Kahf",
            "Find the Qibla",
            "A verified reading",
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

    @Test fun everySwipedCoverKeepsItsPortraitSubjectVisible() {
        compose.setContent { CoverTestScene(dark = true, onOpen = {}) }
        val pages = listOf(
            "Make time for Fajr" to "prayer",
            "Hadith for Fajr" to "salah",
            "Read Al-Kahf" to "quran",
            "Find the Qibla" to "qibla",
            "A verified reading" to "suggestion",
        )
        pages.forEachIndexed { index, (title, name) ->
            if (index > 0) {
                compose.onNodeWithText(pages[index - 1].first).performTouchInput { swipeLeft() }
                compose.waitForIdle()
            }
            compose.onNodeWithText(title).fetchSemanticsNode()
            compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_portrait_$name.png")
        }
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
                                currentPrayerName = "Fajr",
                                readingSurahName = "Al-Kahf",
                                readingSurahNumber = 18,
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
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover_reflection.png")
        compose.onNodeWithText("Make time for Fajr").performTouchInput { swipeLeft() }
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
    useNightArtwork: Boolean = dark,
    epochSeconds: Double? = null,
    onOpen: (Int) -> Unit,
    onScrollPositionChanged: (Float) -> Unit = {},
) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxWidth().height(852.dp)) {
            val position = remember { mutableFloatStateOf(0f) }
            HomeCoverArtworkBackdrop(
                position,
                useNightArtwork = useNightArtwork,
                isDarkTheme = dark,
                isExpanded = false,
                epochSeconds = epochSeconds,
                latitude = epochSeconds?.let { 25.2048 },
                longitude = epochSeconds?.let { 55.2708 },
            )
            Column(Modifier.fillMaxSize().padding(top = 80.dp, start = 24.dp, end = 24.dp)) {
                Box(Modifier.requiredWidth(393.dp).height(588.dp)) {
                    ImmersiveHomeCover(
                        selectedPage = 0,
                        isDarkTheme = dark,
                        useNightArtwork = useNightArtwork,
                        nextPrayerName = "Asr",
                        nextPrayerCountdown = "1h 41m",
                        currentPrayerName = "Fajr",
                        readingSurahName = "Al-Kahf",
                        readingSurahNumber = 18,
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
