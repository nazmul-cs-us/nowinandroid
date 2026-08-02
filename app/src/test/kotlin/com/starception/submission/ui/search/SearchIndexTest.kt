package com.starception.submission.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    private data class Surah(
        val number: Int,
        val name: String,
        val aliases: String = "",
    )

    private val index = FieldWeightedIndex(
        items = listOf(
            Surah(1, "Al-Fatihah"),
            Surah(2, "Al-Baqarah"),
            Surah(3, "Aal-i-Imraan"),
            Surah(36, "Ya-Sin", aliases = "yaseen"),
            Surah(54, "Al-Qamar"),
            Surah(90, "Al-Balad"),
            Surah(93, "Ad-Duha", aliases = "dhuha zoha"),
            Surah(108, "Al-Kawthar"),
            Surah(112, "Al-Ikhlas"),
        ),
        fields = listOf(
            IndexedField("name", 10.0) { it.name },
            IndexedField("aliases", 9.5) { it.aliases },
        ),
    )

    @Test
    fun transliterationPrefix_bakar_findsAlBaqarah() {
        assertEquals(listOf(2), search("bakar").map { it.item.number })
    }

    @Test
    fun transliterationTypo_baqra_findsAlBaqarah() {
        assertEquals(2, search("baqra").first().item.number)
    }

    @Test
    fun phoneticVowels_fateha_findsAlFatihah() {
        assertEquals(1, search("fateha").first().item.number)
    }

    @Test
    fun optionalDigraph_iklas_findsAlIkhlas() {
        assertEquals(112, search("iklas").first().item.number)
    }

    @Test
    fun compactHyphenatedName_yasin_findsYaSin() {
        assertEquals(36, search("yasin").first().item.number)
    }

    @Test
    fun conventionalAlias_zoha_findsAdDuha() {
        assertEquals(93, search("zoha").first().item.number)
    }

    @Test
    fun transposedLetters_kawthra_findsAlKawthar() {
        assertEquals(108, search("kawthra").first().item.number)
    }

    @Test
    fun approximateMatch_doesNotReplaceExactRanking() {
        val results = search("kawthar")
        val nextBestScore = results.drop(1).maxOfOrNull { it.score } ?: 0.0

        assertEquals(108, results.first().item.number)
        assertTrue(results.first().score > nextBestScore)
    }

    @Test
    fun fortressChapterTypo_greif_findsWorryAndGrief() {
        data class FortressDua(val id: Int, val chapter: String, val translation: String)

        val fortressIndex = FieldWeightedIndex(
            items = listOf(
                FortressDua(
                    1,
                    "Invocations in times of worry and grief",
                    "O Allah, I seek refuge in You.",
                ),
                FortressDua(2, "When waking up", "Praise is to Allah who gave us life."),
            ),
            fields = listOf(
                IndexedField("chapter", 10.0) { it.chapter },
                IndexedField("translation", 7.0) { it.translation },
            ),
        )

        val results = fortressIndex.query(
            queryTokens = SearchTokenizer.tokenize("greif"),
            fullNormalizedQuery = SearchTokenizer.normalize("greif"),
            limit = 5,
        )

        assertEquals(1, results.first().item.id)
    }

    @Test
    fun fortressAliasTypo_anxity_findsWorryAndGrief() {
        data class FortressDua(val id: Int, val chapter: String, val aliases: String)

        val fortressIndex = FieldWeightedIndex(
            items = listOf(
                FortressDua(
                    1,
                    "Invocations in times of worry and grief",
                    "anxiety anxious distress depression sadness sorrow",
                ),
                FortressDua(2, "When waking up", "morning wake"),
            ),
            fields = listOf(
                IndexedField("chapter", 10.0) { it.chapter },
                IndexedField("aliases", 9.5) { it.aliases },
            ),
        )

        val results = fortressIndex.query(
            queryTokens = SearchTokenizer.tokenize("anxity"),
            fullNormalizedQuery = SearchTokenizer.normalize("anxity"),
            limit = 5,
        )

        assertEquals(1, results.first().item.id)
    }

    private fun search(query: String): List<RankedHit<Surah>> = index.query(
        queryTokens = SearchTokenizer.tokenize(query),
        fullNormalizedQuery = SearchTokenizer.normalize(query),
        limit = 5,
    )
}
