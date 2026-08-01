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
    private val REPEATED_LATIN_LETTERS = Regex("([a-z])\\1+")

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
     * Words stored in the index, including a compact form for short hyphenated
     * names. This lets "yasin" match the canonical display name "Ya-Sin"
     * without weakening normal token boundaries for long descriptions.
     */
    fun indexWords(normalized: String): List<String> {
        val words = splitWords(normalized)
        if (words.size !in 2..3 || normalized.length > 24 ||
            normalized.none { it == '-' || it == '_' || it == '/' } ||
            words.any { word -> word.any { it !in 'a'..'z' } }
        ) return words
        return words + words.joinToString(separator = "")
    }

    /**
     * Produces a conservative key for common English transliteration variants.
     * This is only used as a lower-weight fallback; exact spelling still ranks
     * first. For example, "baqarah" and "bakarah" share a key, while repeated
     * transliteration vowels in "imraan" do not prevent a match for "imran".
     */
    fun transliterationKey(input: String): String {
        var key = normalize(input)
        if (key.isEmpty() || key.any { it !in 'a'..'z' }) return key
        key = key
            .replace("ph", "f")
            .replace("ck", "k")
            .replace('q', 'k')
            .replace('c', 'k')
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("oo", "u")
        return REPEATED_LATIN_LETTERS.replace(key) { match -> match.groupValues[1] }
    }

    fun isLatinSearchKey(input: String): Boolean =
        input.length >= 3 && input.all { it in 'a'..'z' }

    fun characterTrigrams(input: String): Set<String> {
        if (input.length < 3) return emptySet()
        return buildSet {
            for (index in 0..input.length - 3) {
                add(input.substring(index, index + 3))
            }
        }
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
 * - transliteration-aware weighted Damerau-Levenshtein fallback (insert,
 *   delete, substitute, and adjacent transposition)
 * - character-trigram fallback for longer noisy spellings
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
    private val postingsByTransliterationKey: Map<String, List<Posting>>
    private val transliterationKeysByTrigram: Map<String, Set<String>>
    private val transliterationKeysByPrefix: Map<String, Set<String>>
    /** Per item, per field: the normalized full text. Used for substring + phrase match. */
    private val fieldNormText: List<List<String>>

    init {
        val byToken = HashMap<String, MutableList<Posting>>()
        val byTransliterationKey = HashMap<String, MutableList<Posting>>()
        val normText = ArrayList<List<String>>(items.size)
        items.forEachIndexed { itemIdx, item ->
            val row = ArrayList<String>(fields.size)
            fields.forEachIndexed { fieldIdx, field ->
                val norm = SearchTokenizer.normalize(field.getter(item).orEmpty())
                row.add(norm)
                if (norm.isBlank()) return@forEachIndexed
                SearchTokenizer.indexWords(norm).forEachIndexed { wordIdx, word ->
                    val posting = Posting(
                        itemIndex = itemIdx,
                        fieldIndex = fieldIdx,
                        isStartOfWord = wordIdx == 0,
                    )
                    byToken.getOrPut(word) { ArrayList() }.add(posting)
                    val transliterationKey = SearchTokenizer.transliterationKey(word)
                    if (SearchTokenizer.isLatinSearchKey(transliterationKey)) {
                        byTransliterationKey
                            .getOrPut(transliterationKey) { ArrayList() }
                            .add(posting)
                    }
                }
            }
            normText.add(row)
        }
        this.postingsByToken = byToken
        this.postingsByTransliterationKey = byTransliterationKey
        val byTrigram = HashMap<String, MutableSet<String>>()
        val byPrefix = HashMap<String, MutableSet<String>>()
        byTransliterationKey.keys.forEach { key ->
            SearchTokenizer.characterTrigrams(key).forEach { trigram ->
                byTrigram.getOrPut(trigram) { LinkedHashSet() }.add(key)
            }
            byPrefix.getOrPut(key.take(2)) { LinkedHashSet() }.add(key)
        }
        this.transliterationKeysByTrigram = byTrigram
        this.transliterationKeysByPrefix = byPrefix
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

            // 3. Transliteration-aware fallback. Exact keys catch q/k and
            //    repeated-vowel variants; key prefixes catch shortened input
            //    such as "bakar" -> "baqarah".
            val transliterationToken = SearchTokenizer.transliterationKey(token)
            if (SearchTokenizer.isLatinSearchKey(transliterationToken)) {
                postingsByTransliterationKey[transliterationToken]?.forEach { p ->
                    if (p.itemIndex in itemsHitThisToken) return@forEach
                    bump(p.itemIndex, p.fieldIndex, 0.78)
                    itemsHitThisToken.add(p.itemIndex)
                }
                if (isLast) {
                    postingsByTransliterationKey.forEach { (indexedKey, list) ->
                        if (indexedKey == transliterationToken ||
                            !indexedKey.startsWith(transliterationToken)
                        ) return@forEach
                        list.forEach { p ->
                            if (p.itemIndex in itemsHitThisToken) return@forEach
                            bump(p.itemIndex, p.fieldIndex, 0.62)
                            itemsHitThisToken.add(p.itemIndex)
                        }
                    }
                }

                // Retrieve approximate candidates by shared prefix and character
                // trigrams, then rank them with a phonetic weighted Damerau
                // distance. Candidate generation avoids comparing every query
                // with every indexed word as the content index grows.
                if (transliterationToken.length >= 4) {
                    val candidateKeys = LinkedHashSet<String>()
                    transliterationKeysByPrefix[transliterationToken.take(2)]
                        ?.let(candidateKeys::addAll)
                    SearchTokenizer.characterTrigrams(transliterationToken).forEach { trigram ->
                        transliterationKeysByTrigram[trigram]?.let(candidateKeys::addAll)
                    }

                    val bestMatchByItem = HashMap<Int, Pair<Posting, Double>>()
                    candidateKeys.forEach { indexedKey ->
                        if (indexedKey == transliterationToken ||
                            indexedKey.startsWith(transliterationToken)
                        ) return@forEach
                        val approximateScore = approximateMatchScore(
                            query = transliterationToken,
                            indexed = indexedKey,
                        ) ?: return@forEach
                        val list = postingsByTransliterationKey[indexedKey].orEmpty()
                        list.forEach { p ->
                            if (p.itemIndex in itemsHitThisToken) return@forEach
                            val weightedScore = approximateScore * fields[p.fieldIndex].weight
                            val existing = bestMatchByItem[p.itemIndex]
                            if (existing == null ||
                                weightedScore > existing.second * fields[existing.first.fieldIndex].weight
                            ) {
                                bestMatchByItem[p.itemIndex] = p to approximateScore
                            }
                        }
                    }
                    bestMatchByItem.forEach { (itemIndex, match) ->
                        bump(itemIndex, match.first.fieldIndex, match.second)
                        itemsHitThisToken.add(itemIndex)
                    }
                }
            }

            // 4. In-word substring fallback against the full normalized field
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

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var index = 0
        while (index < limit && a[index] == b[index]) index++
        return index
    }

    private fun approximateMatchScore(query: String, indexed: String): Double? {
        val queryTrigrams = SearchTokenizer.characterTrigrams(query)
        val indexedTrigrams = SearchTokenizer.characterTrigrams(indexed)
        val sharedTrigrams = queryTrigrams.count { it in indexedTrigrams }
        val dice = if (queryTrigrams.isEmpty() || indexedTrigrams.isEmpty()) {
            0.0
        } else {
            2.0 * sharedTrigrams / (queryTrigrams.size + indexedTrigrams.size)
        }
        val sharedPrefix = commonPrefixLength(query, indexed)
        val minimumLength = minOf(query.length, indexed.length)
        val minimumDice = if (minimumLength <= 5) 0.50 else 0.42
        if (sharedPrefix < 2 && dice < minimumDice) return null

        val maximumDistance = when (minimumLength) {
            in 0..3 -> 0.75
            4 -> 1.0
            in 5..6 -> 1.35
            in 7..9 -> 1.65
            else -> 2.0
        }
        val distance = weightedDamerauLevenshtein(query, indexed)
        val distanceAccepted = distance <= maximumDistance
        val trigramAccepted = dice >= minimumDice
        if (!distanceAccepted && !trigramAccepted) return null

        val normalizedSimilarity =
            (1.0 - distance / maxOf(query.length, indexed.length)).coerceIn(0.0, 1.0)
        val distanceScore = if (distanceAccepted) 0.40 + normalizedSimilarity * 0.35 else 0.0
        val trigramScore = if (trigramAccepted) 0.32 + dice * 0.28 else 0.0
        return maxOf(distanceScore, trigramScore).coerceAtMost(0.74)
    }

    /**
     * Weighted optimal-string-alignment Damerau-Levenshtein distance.
     * Common transliteration confusions cost less than unrelated letters, and
     * adjacent transpositions are a single edit instead of two substitutions.
     */
    private fun weightedDamerauLevenshtein(a: String, b: String): Double {
        if (a == b) return 0.0
        val matrix = Array(a.length + 1) { DoubleArray(b.length + 1) }
        for (i in 1..a.length) {
            matrix[i][0] = matrix[i - 1][0] + insertionOrDeletionCost(a[i - 1])
        }
        for (j in 1..b.length) {
            matrix[0][j] = matrix[0][j - 1] + insertionOrDeletionCost(b[j - 1])
        }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val deletion = matrix[i - 1][j] + insertionOrDeletionCost(a[i - 1])
                val insertion = matrix[i][j - 1] + insertionOrDeletionCost(b[j - 1])
                val substitution = matrix[i - 1][j - 1] +
                    substitutionCost(a[i - 1], b[j - 1])
                var best = minOf(deletion, insertion, substitution)
                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
                ) {
                    best = minOf(best, matrix[i - 2][j - 2] + TRANSPOSITION_COST)
                }
                matrix[i][j] = best
            }
        }
        return matrix[a.length][b.length]
    }

    private fun insertionOrDeletionCost(character: Char): Double = when (character) {
        'h' -> 0.45
        in VOWELS -> 0.70
        else -> 1.0
    }

    private fun substitutionCost(a: Char, b: Char): Double {
        if (a == b) return 0.0
        if (a in VOWELS && b in VOWELS) return 0.45
        return when {
            samePair(a, b, 'i', 'y') -> 0.25
            samePair(a, b, 'u', 'w') -> 0.30
            samePair(a, b, 'o', 'w') -> 0.35
            samePair(a, b, 'd', 'z') -> 0.45
            samePair(a, b, 's', 'z') -> 0.45
            samePair(a, b, 't', 's') -> 0.55
            samePair(a, b, 'f', 'p') -> 0.45
            samePair(a, b, 'g', 'j') -> 0.55
            samePair(a, b, 'h', 'x') -> 0.55
            else -> 1.0
        }
    }

    private fun samePair(a: Char, b: Char, first: Char, second: Char): Boolean =
        (a == first && b == second) || (a == second && b == first)

    private companion object {
        val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
        const val TRANSPOSITION_COST = 0.55
    }
}
