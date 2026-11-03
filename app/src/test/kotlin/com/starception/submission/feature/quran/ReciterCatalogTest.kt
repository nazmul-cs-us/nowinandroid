package com.starception.submission.feature.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReciterCatalogTest {
    @Test fun sudaisProviderSpellingIsDiscoverableByNameAndHaramainFilter() {
        val reciter = ReciterCatalog.parse("""{"reciters":[{"id":54,"name":"Abdulrahman Alsudaes","moshaf":[
            {"id":54,"name":"Rewayat Hafs A'n Assem - Murattal","server":"https://cdn.mp3quran.net/audio/abdulrahman-sudais/r1/","surah_list":"1,2,114"}
        ]}]}""").single()
        assertEquals("54-54", reciter.id)
        assertEquals("Abdul Rahman Al-Sudais", reciter.name)
        assertEquals(ReciterGroup.HARAMAIN_IMAMS, reciterGroup(reciter.name))
        listOf("Abdul Rahman Al Sudais", "Al-Sudais", "Alsudaes", "SUDAYS", "abdur rahman").forEach {
            assertTrue("Missing search match for $it", ReciterCatalog.matchesSearch(reciter, it))
        }
        assertFalse(ReciterCatalog.matchesSearch(reciter, "Alafasy"))
    }

    @Test fun catalogRetainsVariantsAndFiltersUnsupportedSources() {
        val result = ReciterCatalog.parse("""{"reciters":[{"id":1,"name":"Example","moshaf":[
            {"id":2,"name":"Hafs","server":"https://example.org/audio","surah_list":"1, 2,115,bad"},
            {"id":3,"name":"Warsh","server":"https://example.org/warsh/","surah_list":"114"},
            {"id":4,"server":"http://example.org/","surah_list":"1"},
            {"id":5,"server":"https://example.org/","surah_list":""}
        ]}]}""")
        assertEquals(listOf("1-2", "1-3"), result.map { it.id })
        assertEquals(setOf(1, 2), result.first().surahs)
        assertEquals("https://example.org/audio/", result.first().server)
    }
}
