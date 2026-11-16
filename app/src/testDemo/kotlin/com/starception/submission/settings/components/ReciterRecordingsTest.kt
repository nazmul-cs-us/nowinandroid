package com.starception.submission.settings.components

import com.starception.submission.feature.quran.distinctReciterRecordings
import com.starception.submission.core.images.reciterPersonKey
import org.junit.Assert.*
import org.junit.Test

class ReciterRecordingsTest {
    private data class Recording(val id: String, val name: String = "Mishary Rashid Al-Alafasy",
        val server: String = "https://example.org/afasy/", val style: String = "Hafs · Murattal",
        val surahs: Set<Int> = setOf(1, 2))
    private fun unique(rows: List<Recording>, selected: String? = null, downloaded: Set<String> = emptySet()) =
        distinctReciterRecordings(rows, { it.id }, { it.name }, { it.server }, { it.style }, { it.surahs }, selected, downloaded)

    @Test fun equivalentAliasesAndTrailingSlashesDoNotProduceDuplicateRecordings() {
        val first = Recording("1")
        val alias = Recording("2", name = "Mshari Al Afasi", server = "https://example.org/afasy")
        assertEquals(listOf(first), unique(listOf(first, first, alias)))
        assertEquals(listOf(alias), unique(listOf(first, alias), selected = "2"))
        assertEquals(listOf(alias), unique(listOf(first, alias), downloaded = setOf("2")))
    }
    @Test fun stylesSourcesAndSurahCoverageRemainDistinct() {
        val rows = listOf(Recording("1"), Recording("2", style = "Warsh"),
            Recording("3", style = "Hafs · Mujawwad"), Recording("4", server = "https://example.org/other/"),
            Recording("5", surahs = setOf(1)))
        assertEquals(rows, unique(rows))
    }
    @Test fun downloadedAliasesAreNotOrphanedByDeduplication() {
        val rows = listOf(Recording("1"), Recording("2"))
        assertEquals(rows, unique(rows, downloaded = setOf("1", "2")))
    }
    @Test fun unknownPeopleAreNotGivenAFamiliarRecitersIdentity() {
        assertNotEquals("abdul-basit", reciterPersonKey("Abdul Basit Abdullah"))
        assertNotEquals("husary", reciterPersonKey("Ibrahim Al Husary"))
        assertNotEquals("maher", reciterPersonKey("Khalid Al Muaiqly"))
        assertEquals("alafasy", reciterPersonKey("Mishary Rashid Al-Alafasy"))
        assertEquals("sudais", reciterPersonKey("Abdulrahman Alsudaes"))
    }
    @Test fun onePersonKeepsAllThreeStylesAndTheirOriginalIds() {
        val rows = listOf(Recording("1-1", "Abdulbasit Abdulsamad"),
            Recording("1-2", "Abdelbasset Abdessamad", style = "Hafs · Mujawwad"),
            Recording("1-3", "Abdul Basit Abdul Samad", style = "Warsh"))
        val people = com.starception.submission.feature.quran.groupReciters(rows, { it.id }, { it.name })
        assertEquals(1, people.size)
        assertEquals("Abdul Basit Abdul Samad", people.single().name)
        assertEquals(rows, people.single().recordings)
    }
    @Test fun defaultAndCatalogAlafasyAreOnePersonWithTwoChoices() {
        val rows = listOf(Recording("default-cdn"), Recording("123-321", name = "Mshari Al Afasi"))
        val people = com.starception.submission.feature.quran.groupReciters(rows, { it.id }, { it.name })
        assertEquals(1, people.size)
        assertEquals(setOf("default-cdn", "123-321"), people.single().recordings.map { it.id }.toSet())
    }

    @Test fun fullQuranEligibilityRequiresEverySurahNotJust114Entries() {
        val full = (1..114).toSet()
        assertTrue(com.starception.submission.feature.quran.hasCompleteQuran(full))
        assertFalse(com.starception.submission.feature.quran.hasCompleteQuran(full - 114))
        assertFalse(com.starception.submission.feature.quran.hasCompleteQuran((full - 50) + 115))
        assertFalse(com.starception.submission.feature.quran.hasCompleteQuran(emptySet()))
    }

}
