package com.starception.submission.ui.search

import java.text.Normalizer

/**
 * Tokenization + normalization shared by every in-memory search source.
 *
 * Rules (applied at index build AND at query time):
 * - lowercase
 * - strip Arabic tashkīl (U+064B..U+065F, U+0670, U+06D6..U+06ED)
 * - normalize Arabic letter variants (أإآٱ → ا, ى → ي, ة → ه) so user input
 *   without diacritics still matches stored canonical text
 * - NFKD-decompose and drop combining marks (folds Latin accents)
 * - split on whitespace, hyphens, underscores, common punctuation
 * - drop common Islamic-search stop words; if the query is *only* stop words,
 *   keep the longest one so "dua" / "surah" still searches
 */
object SearchTokenizer {

    val STOP_WORDS = setOf(
        "surah", "sura", "soorah", "sourah",
        "al", "the", "of", "and",
        "ayah", "ayat", "aya", "verse",
        "chapter",
        "dua", "duas",
        "prayer",
    )

    private val ARABIC_DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val SPLIT_REGEX = Regex("[\\s\\-_/.,:;!?\"'()\\[\\]{}]+")
    private val COMBINING_MARKS = Regex("\\p{M}+")

    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        var s = input
        s = ARABIC_DIACRITICS.replace(s, "")
        s = s.replace('أ', 'ا') // أ -> ا
            .replace('إ', 'ا') // إ -> ا
            .replace('آ', 'ا') // آ -> ا
            .replace('ٱ', 'ا') // ٱ -> ا
            .replace('ى', 'ي') // ى -> ي
            .replace('ة', 'ه') // ة -> ه
        s = Normalizer.normalize(s, Normalizer.Form.NFKD)
        s = COMBINING_MARKS.replace(s, "")
        return s.lowercase()
    }

    fun splitWords(normalized: String): List<String> {
        if (normalized.isBlank()) return emptyList()
        return normalized.split(SPLIT_REGEX).filter { it.length >= 2 }
    }

    /**
     * Returns the meaningful query tokens. Drops stop words; if the query is
     * entirely stop words, keep the longest one so a bare "dua" still searches.
     */
    fun tokenize(input: String): List<String> {
        val raw = splitWords(normalize(input))
        if (raw.isEmpty()) return emptyList()
        val nonStop = raw.filter { it !in STOP_WORDS }
        return if (nonStop.isNotEmpty()) nonStop
        else listOf(raw.maxByOrNull { it.length } ?: raw.first())
    }
}

/** A single item that matched a query, with the score used for ranking. */
data class RankedHit<T>(
    val item: T,
    val score: Double,
    val tokensMatched: Int,
)

/** Field descriptor for an indexed item. Higher [weight] = more important. */
data class IndexedField<T>(
    val name: String,
    val weight: Double,
    val getter: (T) -> String?,
)

/**
 * Generic in-memory inverted index with field-weighted token ranking.
 *
 * Build cost: O(items × words-per-item). For 114 surahs × ~3 fields, build is
 * <10ms on a Pixel 6 cold. Query is <2ms for typical 1–3 token queries.
 *
 * Ranking signals:
 * - field weight × position bonus per exact token hit (starts-word=1.5, in-word=1.0)
 * - last-token prefix match at 0.6× weight, for live autocomplete ("im" → Imran)
 * - all-tokens-hit ×1.5 multiplier (boosts AND-style matches)
 * - exact full-phrase substring bonus +5× field weight (pins "ayatul kursi")
 * - 1-edit Levenshtein fallback for tokens ≥5 chars at 0.4× weight, only on
 *   items missing a hit for that token (so "imrran" still finds "Imran")
 */
class FieldWeightedIndex<T>(
    items: List<T>,
    private val fields: List<IndexedField<T>>,
) {
    private data class Posting(
        val itemIndex: Int,
        val fieldIndex: Int,
        val isStartOfWord: Boolean,
    )

    private val items: List<T> = items
    private val postingsByToken: Map<String, List<Posting>>
    /** Per item, per field: the normalized full text. Used for substring + phrase match. */
    private val fieldNormText: List<List<String>>

    init {
        val byToken = HashMap<String, MutableList<Posting>>()
        val normText = ArrayList<List<String>>(items.size)
        items.forEachIndexed { itemIdx, item ->
            val row = ArrayList<String>(fields.size)
            fields.forEachIndexed { fieldIdx, field ->
                val norm = SearchTokenizer.normalize(field.getter(item).orEmpty())
                row.add(norm)
                if (norm.isBlank()) return@forEachIndexed
                SearchTokenizer.splitWords(norm).forEachIndexed { wordIdx, word ->
                    byToken.getOrPut(word) { ArrayList() }.add(
                        Posting(
                            itemIndex = itemIdx,
                            fieldIndex = fieldIdx,
                            isStartOfWord = wordIdx == 0,
                        ),
                    )
                }
            }
            normText.add(row)
        }
        this.postingsByToken = byToken
        this.fieldNormText = normText
    }

    /**
     * Returns the top [limit] hits ranked by score, descending.
     *
     * @param queryTokens the normalized, stop-word-filtered query tokens
     * @param fullNormalizedQuery the full normalized query (used for phrase boost)
     */
    fun query(queryTokens: List<String>, fullNormalizedQuery: String, limit: Int): List<RankedHit<T>> {
        if (queryTokens.isEmpty()) return emptyList()

        // [itemIdx] -> [score, tokensMatchedCount]
        val scores = HashMap<Int, DoubleArray>()

        fun bump(itemIdx: Int, fieldIdx: Int, addScore: Double) {
            val arr = scores.getOrPut(itemIdx) { doubleArrayOf(0.0, 0.0) }
            arr[0] += addScore * fields[fieldIdx].weight
        }

        queryTokens.forEachIndexed { tIdx, token ->
            val isLast = tIdx == queryTokens.size - 1
            val itemsHitThisToken = HashSet<Int>()

            // 1. Exact token match
            postingsByToken[token]?.forEach { p ->
                val pos = if (p.isStartOfWord) 1.5 else 1.2
                bump(p.itemIndex, p.fieldIndex, pos)
                itemsHitThisToken.add(p.itemIndex)
            }

            // 2. Prefix match on the LAST token (live autocomplete)
            if (isLast && token.length >= 2) {
                postingsByToken.forEach { (indexedToken, list) ->
                    if (indexedToken == token) return@forEach
                    if (!indexedToken.startsWith(token)) return@forEach
                    list.forEach { p ->
                        if (p.itemIndex in itemsHitThisToken) return@forEach
                        val pos = if (p.isStartOfWord) 1.5 else 1.2
                        bump(p.itemIndex, p.fieldIndex, pos * 0.6)
                        itemsHitThisToken.add(p.itemIndex)
                    }
                }
            }

            // 3. In-word substring fallback against the full normalized field
            //    (catches "khlas" → "ikhlas" without exploding the postings dict)
            fieldNormText.forEachIndexed { itemIdx, row ->
                if (itemIdx in itemsHitThisToken) return@forEachIndexed
                row.forEachIndexed innerLoop@{ fieldIdx, norm ->
                    if (norm.contains(token)) {
                        bump(itemIdx, fieldIdx, 0.8)
                        itemsHitThisToken.add(itemIdx)
                        return@innerLoop
                    }
                }
            }

            // 4. 1-edit Levenshtein fuzzy match for tokens ≥5 chars, only on
            //    items that still haven't matched this token. Catches common
            //    transliteration variants ("imran" ↔ "imraan", "rahman" ↔
            //    "rahmaan", "fatiha" ↔ "fatihah"). Weighted close to substring
            //    (0.7) because these variants are usually the *intended* match,
            //    not a stretch — anything weaker gets buried by SQL priors.
            if (token.length >= 5) {
                postingsByToken.forEach inner@{ (indexedToken, list) ->
                    if (indexedToken == token) return@inner
                    if (kotlin.math.abs(indexedToken.length - token.length) > 1) return@inner
                    if (!isOneEditAway(indexedToken, token)) return@inner
                    list.forEach { p ->
                        if (p.itemIndex in itemsHitThisToken) return@forEach
                        bump(p.itemIndex, p.fieldIndex, 0.7)
                        itemsHitThisToken.add(p.itemIndex)
                    }
                }
            }

            // Tally tokensMatched — fuzzy hits count too, so "imran" finds
            // "Aal-i-Imraan" without being filtered out by the matched==0 check.
            itemsHitThisToken.forEach { itemIdx ->
                scores[itemIdx]!![1] += 1.0
            }
        }

        // 5. Full-phrase exact substring bonus (e.g., "ayatul kursi")
        if (fullNormalizedQuery.length >= 3) {
            fieldNormText.forEachIndexed { itemIdx, row ->
                row.forEachIndexed { fieldIdx, norm ->
                    if (norm.contains(fullNormalizedQuery)) {
                        bump(itemIdx, fieldIdx, 5.0)
                    }
                }
            }
        }

        // 6. All-tokens-hit multiplier; require at least one hit to keep the item
        val tokenCount = queryTokens.size.toDouble()
        return scores.entries
            .mapNotNull { (itemIdx, arr) ->
                val rawScore = arr[0]
                val matched = arr[1].toInt()
                if (matched == 0) return@mapNotNull null
                val finalScore = if (matched >= tokenCount) rawScore * 1.5 else rawScore
                RankedHit(items[itemIdx], finalScore, matched)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /** True if [a] and [b] differ by exactly one insert/delete/substitute. */
    private fun isOneEditAway(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == b.length) {
            var diffs = 0
            for (i in a.indices) {
                if (a[i] != b[i]) {
                    diffs++
                    if (diffs > 1) return false
                }
            }
            return diffs == 1
        }
        val (longer, shorter) = if (a.length > b.length) a to b else b to a
        if (longer.length - shorter.length != 1) return false
        var i = 0
        var j = 0
        var skipped = false
        while (i < longer.length && j < shorter.length) {
            if (longer[i] != shorter[j]) {
                if (skipped) return false
                skipped = true
                i++
            } else {
                i++; j++
            }
        }
        return true
    }
}
