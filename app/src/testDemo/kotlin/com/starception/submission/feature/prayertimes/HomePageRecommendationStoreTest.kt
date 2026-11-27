package com.starception.submission.feature.prayertimes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.starception.submission.core.model.deenly.packInsightFields
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomePageRecommendationStoreTest {
    @Test fun sourceOnlyReadingsRestoreWithoutWaitingForAnotherLookup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("home_source_only_readings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = HomePageRecommendationStore(prefs)
        val day = "2026-11-24"
        val slot = homeRecommendationSlot(2, KnowledgeSourceKind.SURAH)
        val reading = candidate(2, "Verified source").copy(modelGenerated = false)
        store.save(day, slot, reading, "Isha")
        store.saveAdditional(day, 2, listOf(reading), "Isha")

        val restored = HomePageGenerationState()
        store.restore(day, restored, "Isha")
        assertFalse(restored.needsSource(slot))
        assertFalse(restored.generated[slot]?.modelGenerated == true)
        assertEquals(listOf(reading), restored.additionalRecommendations[2])
    }

    @Test fun additionalReadingsRestoreForTheSameTileDayAndContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("more_home_readings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = HomePageRecommendationStore(prefs)
        val day = "2026-11-24"
        val reading = candidate(2, "Fresh")
        store.saveAdditional(day, 2, listOf(reading, reading), "Isha")

        val restored = HomePageGenerationState()
        store.restore(day, restored, "Isha")
        assertEquals(listOf(reading.sourceId), restored.additionalRecommendations[2]?.map { it.sourceId })
        assertNull(restored.additionalRecommendations[0])
        val stale = HomePageGenerationState()
        store.restore(day, stale, "Fajr")
        assertNull(stale.additionalRecommendations[2])
    }

    @Test fun recommendationsForDifferentTilesAndKindsRestoreIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("typed_home_recommendations_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = HomePageRecommendationStore(prefs)
        val day = "2026-11-24"
        val prayerSurah = homeRecommendationSlot(0, KnowledgeSourceKind.SURAH)
        val prayerHadith = homeRecommendationSlot(0, KnowledgeSourceKind.HADITH)
        val readingSurah = homeRecommendationSlot(2, KnowledgeSourceKind.SURAH)
        store.save(day, prayerSurah, candidate(0, "Prayer surah"))
        store.save(day, prayerHadith, candidate(1, "Prayer hadith"))
        store.save(day, readingSurah, candidate(2, "Reading surah"))

        val restored = HomePageGenerationState()
        store.restore(day, restored)
        assertEquals("Prayer surah 0", restored.generated[prayerSurah]?.nudge?.supportingText)
        assertEquals("Prayer hadith 1", restored.generated[prayerHadith]?.nudge?.supportingText)
        assertEquals("Reading surah 2", restored.generated[readingSurah]?.nudge?.supportingText)
    }

    @Test fun fallbackOrWrongContextNeverPreventsANewModelAttempt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("home_model_freshness_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = HomePageRecommendationStore(prefs)
        val day = "2026-11-24"
        store.save(day, 0, candidate(0, "Fallback").copy(modelGenerated = false), "knowledge-v3|Fajr")
        store.save(day, 1, candidate(1, "Older model"), "question-v5|Fajr")
        store.save(day, 2, candidate(2, "Older prayer"), "knowledge-v3|Isha")
        store.save(day, 3, candidate(3, "Fresh"), "knowledge-v3|Fajr")
        prefs.edit().putString("page_4", packInsightFields(listOf(day,
            PreparedInsightReadings.encodeAiCandidate(candidate(4, "Legacy"))))).commit()
        val state = HomePageGenerationState()
        store.restore(day, state, "knowledge-v3|Fajr")
        for (page in listOf(0, 1, 2, 4)) {
            assertFalse(state.attempted[page] == true)
            assertTrue(state.generated[page] != null)
        }
        assertTrue(state.attempted[3] == true)
        assertTrue(state.generated[3]?.modelGenerated == true)
        assertFalse(state.generated[0]?.modelGenerated == true)
    }

    @Test fun modelProvenanceSurvivesSerializationAndRetryBudgetIsBounded() {
        val original = candidate(0, "Generated")
        assertTrue(PreparedInsightReadings.decodeAiCandidate(PreparedInsightReadings.encodeAiCandidate(original)).modelGenerated)
        val fallback = original.copy(modelGenerated = false)
        assertFalse(PreparedInsightReadings.decodeAiCandidate(PreparedInsightReadings.encodeAiCandidate(fallback)).modelGenerated)
        val state = HomePageGenerationState()
        assertTrue(state.needsGeneration(0))
        state.attempts[0] = 1
        assertTrue(state.needsGeneration(0))
        state.attempts[0] = 2
        assertFalse(state.needsGeneration(0))
        state.attempted[1] = true
        assertFalse(state.needsGeneration(1))
        assertEquals("Generated heading", homeReflectionHeading("Fixed prompt", original.copy(
            nudge = original.nudge.copy(label = "Generated heading"))))
        assertEquals("Fixed prompt", homeReflectionHeading("Fixed prompt", fallback))
    }

    @Test fun prayerContextChangesStartAFreshGenerationWindow() {
        val first = HomePageGenerationCache.forDay("2026-11-24", "Fajr|knowledge-v3")
        first.attempted[0] = true
        assertTrue(HomePageGenerationCache.forDay("2026-11-24", "Fajr|knowledge-v3").attempted[0] == true)
        assertFalse(HomePageGenerationCache.forDay("2026-11-24", "Dhuhr|knowledge-v3").attempted[0] == true)
    }

    @Test
    fun lastRecommendationForEveryPageSurvivesRestartAndIsReplacedWhenRefreshed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("home_recommendation_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = HomePageRecommendationStore(prefs)

        (0..4).forEach { page -> store.save("2026-11-03", page, candidate(page, "Earlier")) }

        val sameDay = HomePageGenerationState()
        store.restore("2026-11-03", sameDay)
        (0..4).forEach { page ->
            assertEquals("Earlier $page", sameDay.generated[page]?.nudge?.supportingText)
            assertTrue(sameDay.attempted[page] == true)
        }

        val nextDay = HomePageGenerationState()
        store.restore("2026-11-04", nextDay)
        (0..4).forEach { page ->
            assertEquals("Earlier $page", nextDay.generated[page]?.nudge?.supportingText)
            assertFalse(nextDay.attempted[page] == true)
        }

        store.save("2026-11-04", 2, candidate(2, "New"))
        val relaunched = HomePageGenerationState()
        store.restore("2026-11-04", relaunched)
        assertEquals("New 2", relaunched.generated[2]?.nudge?.supportingText)
        assertEquals("Reference 2", relaunched.generated[2]?.nudge?.sourceLabel)
        assertEquals(NowNudgeKnowledgeTarget.QuranAyah(3, 1), relaunched.generated[2]?.target)
        assertTrue(relaunched.attempted[2] == true)
        assertEquals("Earlier 1", relaunched.generated[1]?.nudge?.supportingText)
        assertFalse(relaunched.attempted[1] == true)

        prefs.edit().putString("page_4", "invalid candidate").commit()
        val afterCorruption = HomePageGenerationState()
        store.restore("2026-11-04", afterCorruption)
        assertNull(afterCorruption.generated[4])
        assertEquals("New 2", afterCorruption.generated[2]?.nudge?.supportingText)
    }

    private fun candidate(page: Int, text: String) = NowNudgeKnowledgeCandidate(
        nudge = DeenlyNudge(
            id = "home-$page",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            label = "Reading $page",
            supportingText = "$text $page",
            sourceLabel = "Reference $page",
        ),
        target = NowNudgeKnowledgeTarget.QuranAyah(page + 1, 1),
        sourceId = "source-$page",
        collection = "Quran",
        topic = "Reading",
        rankingScore = 1f,
        modelGenerated = true,
    )
}
