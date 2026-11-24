package com.starception.submission.ui

import com.starception.submission.ml.DeenlyKnowledgeModel
import com.starception.submission.ml.DeenlyKnowledgeTask
import com.starception.submission.ml.DeenlyKnowledgeDecision
import org.junit.Assert.*
import org.junit.Test

class SuggestionModelValidationTest {
    private val source = "And say, \"My Lord, increase me in knowledge.\""

    @Test fun invalidQuizObservedDuringOnDeviceSmokeTestCannotBeDisplayed() {
        val raw = """{"contentType":"question","questionKind":"food","question":"How is knowledge described?","answer":"And say, \"My Lord, increase me in knowledge.\" To: ","evidence":"And say, \"My Lord, increase me in knowledge.","options":["And say, \"My Lord, increase me in knowledge.","and establish prayer and give zakah and obey the","the Most Merciful is patient with His servants and does not","He has taught you what you do not know"]}"""
        assertNull(DeenlyKnowledgeModel.parseDecision(raw, DeenlyKnowledgeTask.QUESTION, source))
    }

    @Test fun groundedAnswerObservedDuringTheSameSmokeTestIsAccepted() {
        val raw = """{"contentType":"answer","answer":"increase me in knowledge.","evidence":"And say, \"My Lord, increase me in knowledge."}"""
        val decision = DeenlyKnowledgeModel.parseDecision(raw, DeenlyKnowledgeTask.ANSWER, source)
        assertTrue(decision is DeenlyKnowledgeDecision.GroundedAnswer)
        assertEquals("increase me in knowledge.", (decision as DeenlyKnowledgeDecision.GroundedAnswer).answer)
    }
}
