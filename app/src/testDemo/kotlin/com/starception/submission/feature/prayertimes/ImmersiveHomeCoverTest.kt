package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

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

    @Test fun homeModelTurnsAreRetainedAcrossVisitsButResetForANewDay() {
        val firstVisit = HomePageGenerationCache.forDay("2026-11-03")
        firstVisit.attempted[2] = true
        assertSame(firstVisit, HomePageGenerationCache.forDay("2026-11-03"))
        assertTrue(HomePageGenerationCache.forDay("2026-11-03").attempted[2] == true)
        assertFalse(HomePageGenerationCache.forDay("2026-11-04").attempted[2] == true)
    }

    @Test fun reflectionShowsGroundedExcerptInsteadOfDuplicatingGenericModelTitle() {
        val candidate = NowNudgeKnowledgeCandidate(
            nudge = DeenlyNudge(
                id = "grounded-reading",
                action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                label = "Knowledge and Wisdom in Islam",
                supportingText = "A verified narration about prayer and intention.",
                sourceLabel = "Sunan an-Nasa'i 620",
            ),
            target = NowNudgeKnowledgeTarget.QuranAyah(1, 1),
            sourceId = "test-source",
            collection = "Sunan an-Nasa'i",
            topic = "Prayer",
            rankingScore = 1f,
        )
        compose.setContent {
            MaterialTheme {
                HomeReflectionPrompt(focusedPage = 1, generatedInsights = mapOf(1 to candidate), onClick = {})
            }
        }
        compose.onNodeWithText("What will you carry into your next prayer?").assertExists()
        compose.onNodeWithText("A verified narration about prayer and intention.").assertExists()
        compose.onNodeWithText("Sunan an-Nasa'i 620").assertExists()
        compose.onNodeWithText("Knowledge and Wisdom in Islam").assertDoesNotExist()
    }

    @Test fun reflectionPreviewStartsWithTeachingInsteadOfNarratorIntroduction() {
        assertEquals("If you forget a prayer, pray it when you remember it.", homeEvidencePreview(
            "It was narrated that Abu Hurairah said: The Messenger of Allah said: " +
                "'If you forget a prayer, pray it when you remember it.'",
        ))
    }

    @Test fun reflectionUsesCoverFadeAcrossAdjacentAndWrappedPages() {
        for (step in 0..10) {
            val position = step / 10f
            val outgoing = homeSwipePageOffset(position, 0)
            val incoming = homeSwipePageOffset(position, 1)
            assertEquals("Swipe position $position", 1f, outgoing - incoming, .001f)
            assertEquals(homeCoverCopyOpacity(position), homeCoverCopyOpacity(outgoing), .001f)
            assertEquals(homeCoverCopyOpacity(position - 1f), homeCoverCopyOpacity(incoming), .001f)

            val wrapped = 4f + position
            val last = homeSwipePageOffset(wrapped, 4)
            val first = homeSwipePageOffset(wrapped, 0)
            assertEquals("Wrapped swipe position $wrapped", 1f, last - first, .001f)
        }
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

    @Test fun daytimePrayerCoverMatchesTheWindowLitArtworkFamily() {
        compose.setContent {
            CoverTestScene(dark = false, onOpen = {})
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_prayer_sun.png")
    }

    @Test fun nighttimePrayerCoverKeepsTheSameInteriorComposition() {
        compose.setContent {
            CoverTestScene(dark = true, onOpen = {})
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

    @Test fun coverCopyDriftsGentlyAndRecoversWhenSwipeIsCancelled() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CoverTestScene(dark = false, onOpen = {}) }
        val original = compose.onNodeWithText("Make time for Fajr", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            down(Offset(330f, 380f))
            moveTo(Offset(240f, 380f), delayMillis = 160)
        }
        compose.mainClock.advanceTimeByFrame()
        val duringDrag = compose.onNodeWithText("Make time for Fajr", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onRoot().captureRoboImage("build/outputs/reference-motion/home-copy-drag.png")
        assertTrue("Copy should not travel across the screen: before=$original, during=$duringDrag",
            abs((duringDrag.left - original.left).value) < 12f)
        compose.onRoot().performTouchInput {
            moveTo(Offset(330f, 380f), delayMillis = 400)
            up()
        }
        compose.mainClock.advanceTimeBy(800)
        val restored = compose.onNodeWithText("Make time for Fajr", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(original.left.value, restored.left.value, .5f)

        val directory = File("build/outputs/reference-motion/home-swipe-frames").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(directory, "frame-000.png").path)
        compose.onNodeWithContentDescription("Show insight 2 of 5").performClick()
        for (frame in 1..18) {
            compose.mainClock.advanceTimeBy(32)
            compose.onRoot().captureRoboImage(File(directory, "frame-%03d.png".format(frame)).path)
        }
        compose.onNodeWithText("Hadith for Fajr").assertExists()
        compose.onNodeWithText("Make time for Fajr").assertDoesNotExist()
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
        val scrollPosition = mutableFloatStateOf(0f)
        val dragPositions = mutableListOf<Float>()
        val exploreExpanded = mutableIntStateOf(0)
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
                                 onScrollPositionChanged = {
                                     scrollPosition.floatValue = it
                                     dragPositions += it
                                 },
                                 onOpen = {},
                            )
                        }
                          HomeReflectionPrompt(
                              focusedPage = selectedPage.intValue,
                              scrollPosition = scrollPosition.floatValue,
                             exploreExpanded = exploreExpanded.intValue != 0,
                             onExploreToggle = { exploreExpanded.intValue = 1 - exploreExpanded.intValue },
                             exploreContent = {
                                 HomeArtworkRail(
                                     focusedPage = selectedPage.intValue,
                                     expanded = exploreExpanded.intValue != 0,
                                     onExpandedChange = { exploreExpanded.intValue = if (it) 1 else 0 },
                                     onSelect = { _, _ -> },
                                 )
                             },
                             onClick = {},
                         )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover_reflection.png")
        compose.onNodeWithText("Make time for Fajr").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue("Reflection should receive the in-flight cover position",
            dragPositions.any { it > .1f && it < .9f })
        assertEquals(1, selectedPage.intValue)
        compose.onNodeWithText("What will you carry into your next prayer?").fetchSemanticsNode()
        compose.onNodeWithContentDescription("Expand home options").performScrollTo().performClick()
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
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalDarkTheme provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val scroll = rememberScrollState()
                    val expanded = remember { androidx.compose.runtime.mutableStateOf(false) }
                    LaunchedEffect(scroll) { scroll.scrollTo(500) }
                    Column(Modifier.fillMaxWidth().height(852.dp)
                        .background(Color(0xFF080B14))
                        .verticalScroll(scroll)
                        .padding(horizontal = 24.dp)) {
                        Spacer(Modifier.height(588.dp))
                         HomeReflectionPrompt(
                             exploreExpanded = expanded.value,
                             onExploreToggle = { expanded.value = !expanded.value },
                             exploreContent = {
                                 HomeArtworkRail(
                                     expanded = expanded.value,
                                     onExpandedChange = { expanded.value = it },
                                     onSelect = { _, _ -> },
                                 )
                             },
                             onClick = {},
                         )
                        Spacer(Modifier.height(200.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Expand home options").performScrollTo().performClick()
        compose.waitForIdle()
        // The reflection can scroll entirely off-screen when the opened rail
        // brings its last row into view. Clipped semantics bounds report 0 for
        // that off-screen text even though both elements are inset equally.
        val reflectionLeft = compose.onNodeWithText("PRAYER REFLECTION")
            .getUnclippedBoundsInRoot().left.value
        val cardsLeft = compose.onNodeWithTag("homeExploreReveal")
            .getUnclippedBoundsInRoot().left.value
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
                HomeReflectionPrompt(onDarkCover = true, onClick = {})
            }
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 36.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(homeGreetingForHour(if (useNightArtwork) 21 else 9), style = HomeTypography.greeting,
                    color = Color.White, modifier = Modifier.weight(1f))
                com.starception.submission.ui.AppHeaderActions(onDarkArtwork = true, onSearchClick = {}, onSettingsClick = {})
            }
        }
    }
}
