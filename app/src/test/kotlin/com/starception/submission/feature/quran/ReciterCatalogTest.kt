package com.starception.submission.feature.quran

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReciterCatalogTest {
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
