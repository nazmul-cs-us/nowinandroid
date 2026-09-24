/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
