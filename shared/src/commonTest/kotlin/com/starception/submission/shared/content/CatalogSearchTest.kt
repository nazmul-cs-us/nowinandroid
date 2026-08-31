package com.starception.submission.shared.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSearchTest {
    @Test
    fun searchesQuranByEnglishArabicAndNumber() {
        assertEquals(18, (searchCatalog("kahf").single() as CatalogResult.Quran).surah.number)
        assertEquals(1, (searchCatalog("ٱلْفَاتِحَة").single() as CatalogResult.Quran).surah.number)
        assertTrue(searchCatalog("80").any { it is CatalogResult.Quran && it.surah.number == 80 })
    }

    @Test
    fun searchesCompleteBukhariBookCatalog() {
        val result = searchCatalog("Invocations").single() as CatalogResult.Bukhari
        assertEquals(80, result.book.id)
        assertEquals(97, com.starception.submission.core.model.data.BukhariBooks.all.size)
    }
}
