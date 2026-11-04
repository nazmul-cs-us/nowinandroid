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
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomePageRecommendationStoreTest {
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
    )
}
