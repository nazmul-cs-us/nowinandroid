package com.starception.submission.shared.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HomePageReflectionsTest {
    @Test fun persistedEvidenceKeepsItsExactDestination() {
        listOf(
            DeenlyKnowledgeTarget.Surah(18),
            DeenlyKnowledgeTarget.FortressDua(12),
            DeenlyKnowledgeTarget.BukhariHadith(25),
            DeenlyKnowledgeTarget.ShamayelBook(3),
        ).forEach { target ->
            val card = DeenlyKnowledgeCard("A reflection", "Source text\nwith vocalization: السَّلَامُ", "Verified source", target)
            assertEquals(card, Json.decodeFromString<DeenlyKnowledgeCard>(Json.encodeToString(card)))
        }
    }
}
