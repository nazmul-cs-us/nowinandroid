package com.starception.submission.ui.search

import java.text.Normalizer

enum class SearchIntent {
    Surah,
    Dua,
    Verse,
    General,
}

data class ParsedSearchQuery(
    val normalized: String,
    val words: List<String>,
    val tokens: List<String>,
    val intent: SearchIntent,
)

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

    private val SURAH_INTENT_WORDS = setOf("surah", "sura", "soorah", "sourah", "chapter")
    private val DUA_INTENT_WORDS = setOf("dua", "duas", "supplication", "invocation")
    private val VERSE_INTENT_WORDS = setOf("ayah", "ayat", "aya", "verse", "verses")
    private val COMMAND_WORDS = setOf(
        "open", "show", "find", "search", "play", "read", "please", "tell",
        "give", "take", "bring", "navigate", "go", "me", "for", "to",
    )
    private val SMALL_NUMBERS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

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
            // Users and voice keyboards commonly omit the digraph's silent
            // h ("Ikhlas" -> "Iklas"), so preserve the leading consonant.
            .replace("kh", "k")
            .replace("sh", "s")
            .replace("dh", "z")
            .replace("th", "s")
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
    fun parse(input: String): ParsedSearchQuery {
        val normalized = normalize(input)
        val raw = splitWords(normalized)
        val intent = when {
            raw.any { it in SURAH_INTENT_WORDS } -> SearchIntent.Surah
            raw.any { it in DUA_INTENT_WORDS } -> SearchIntent.Dua
            raw.any { it in VERSE_INTENT_WORDS } -> SearchIntent.Verse
            else -> SearchIntent.General
        }
        if (raw.isEmpty()) {
            return ParsedSearchQuery(normalized, emptyList(), emptyList(), SearchIntent.General)
        }
        val meaningful = raw.filter { it !in STOP_WORDS && it !in COMMAND_WORDS }
        val surahNumber = if (intent == SearchIntent.Surah) parseSurahNumber(meaningful) else null
        val tokens = when {
            surahNumber != null -> listOf(surahNumber.toString())
            meaningful.isNotEmpty() -> meaningful
            else -> listOf(raw.maxByOrNull { it.length } ?: raw.first())
        }
        return ParsedSearchQuery(normalized, raw, tokens, intent)
    }

    fun tokenize(input: String): List<String> = parse(input).tokens

    /** Detects spoken/typed Surah intent after punctuation has been removed. */
    fun hasSurahIntent(input: String): Boolean =
        parse(input).intent == SearchIntent.Surah

    /** Phrase used for ranking after command words such as "Surah" are removed. */
    fun meaningfulNormalizedQuery(input: String): String = tokenize(input).joinToString(" ")

    private fun parseSurahNumber(words: List<String>): Int? {
        words.singleOrNull()?.toIntOrNull()?.let { return it.takeIf { number -> number in 1..114 } }
        if (words.isEmpty() || words.any { it !in SMALL_NUMBERS && it !in TENS && it != "hundred" }) {
            return null
        }
        var total = 0
        var current = 0
        words.forEach { word ->
            when {
                word in SMALL_NUMBERS -> current += SMALL_NUMBERS.getValue(word)
                word in TENS -> current += TENS.getValue(word)
                word == "hundred" -> current = current.coerceAtLeast(1) * 100
            }
        }
        total += current
        return total.takeIf { it in 1..114 }
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

    /** Indexed tokens in sorted order, so a prefix is a binary-searchable range. */
    private val sortedTokens: List<String>

    /** Items containing each token, for IDF. A token in every item discriminates nothing. */
    private val documentFrequency: Map<String, Int>

    /** Word count per item per field, and the per-field mean, for length normalisation. */
    private val fieldWordCount: List<IntArray>
    private val averageFieldWordCount: DoubleArray

    private val maxFieldWeight: Double = fields.maxOfOrNull { it.weight } ?: 1.0

    /** Items this index can return. Zero means its source held no rows when it was built. */
    val size: Int get() = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    init {
        val byToken = HashMap<String, MutableList<Posting>>()
        val byTransliterationKey = HashMap<String, MutableList<Posting>>()
        val normText = ArrayList<List<String>>(items.size)
        val itemsPerToken = HashMap<String, MutableSet<Int>>()
        val wordCounts = ArrayList<IntArray>(items.size)
        val fieldWordTotals = DoubleArray(fields.size)
        items.forEachIndexed { itemIdx, item ->
            val row = ArrayList<String>(fields.size)
            val counts = IntArray(fields.size)
            fields.forEachIndexed { fieldIdx, field ->
                val norm = SearchTokenizer.normalize(field.getter(item).orEmpty())
                row.add(norm)
                if (norm.isBlank()) return@forEachIndexed
                val words = SearchTokenizer.indexWords(norm)
                counts[fieldIdx] = words.size
                fieldWordTotals[fieldIdx] += words.size
                words.forEachIndexed { wordIdx, word ->
                    val posting = Posting(
                        itemIndex = itemIdx,
                        fieldIndex = fieldIdx,
                        isStartOfWord = wordIdx == 0,
                    )
                    byToken.getOrPut(word) { ArrayList() }.add(posting)
                    itemsPerToken.getOrPut(word) { HashSet() }.add(itemIdx)
                    val transliterationKey = SearchTokenizer.transliterationKey(word)
                    if (SearchTokenizer.isLatinSearchKey(transliterationKey)) {
                        byTransliterationKey
                            .getOrPut(transliterationKey) { ArrayList() }
                            .add(posting)
                    }
                }
            }
            normText.add(row)
            wordCounts.add(counts)
        }
        this.documentFrequency = itemsPerToken.mapValues { it.value.size }
        this.fieldWordCount = wordCounts
        this.averageFieldWordCount = DoubleArray(fields.size) { fieldIdx ->
            if (items.isEmpty()) 1.0 else (fieldWordTotals[fieldIdx] / items.size).coerceAtLeast(1.0)
        }
        this.sortedTokens = byToken.keys.sorted()
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
     * Returns the top [limit] hits, scored 0..1 and ranked descending.
     *
     * The score is a normalised BM25F: each matched term contributes its inverse
     * document frequency, scaled by how good the match was (exact beats prefix beats
     * transliteration beats fuzzy), by the field's weight, and by how much of the field
     * the match accounts for. Dividing by the best score the query could have earned
     * makes the result a confidence rather than an arbitrary magnitude, so hits from
     * different indices — surahs against duas against chapters — can be compared.
     *
     * @param queryTokens the normalized, stop-word-filtered query tokens
     * @param fullNormalizedQuery the full normalized query (used for phrase boost)
     */
    fun query(
        queryTokens: List<String>,
        fullNormalizedQuery: String,
        limit: Int,
        allowShortFuzzy: Boolean = false,
    ): List<RankedHit<T>> {
        if (queryTokens.isEmpty() || items.isEmpty()) return emptyList()

        val itemScores = HashMap<Int, Double>()
        val tokensMatched = HashMap<Int, Int>()
        var idealScore = 0.0

        queryTokens.forEachIndexed { tIdx, token ->
            val isLast = tIdx == queryTokens.size - 1
            val idf = inverseDocumentFrequency(token)
            // What this term earns from a plain exact hit in the heaviest field, at
            // that field's average length. Deliberately reachable: scoring against the
            // shortest conceivable field and a start-of-word bonus on top made the
            // ceiling unattainable, so real matches came out fractions of it — "anam"
            // matched Surah Al-Anaam and still scored below the fixed prior of a section
            // whose entries only matched the word "name". A hit better than this ideal
            // (start of word, unusually short field) clamps at 1.0, which is correct:
            // certainty does not keep growing.
            idealScore += idf * AVERAGE_LENGTH_NORMALISATION

            // Best match quality per (item, field). A term is credited once per field,
            // at its strongest reading: an exact hit is never diluted by also having
            // fuzzily matched the same field.
            val bestQuality = HashMap<Int, Double>()
            fun offer(posting: Posting, quality: Double) {
                val key = posting.itemIndex * fields.size + posting.fieldIndex
                val positional = if (posting.isStartOfWord) START_OF_WORD_BONUS else 1.0
                val value = quality * positional
                if ((bestQuality[key] ?: 0.0) < value) bestQuality[key] = value
            }

            // 1. Exact token match.
            postingsByToken[token]?.forEach { offer(it, QUALITY_EXACT) }

            // 2. Prefix match on the LAST token, for live autocomplete ("im" -> Imran).
            //    Walked as a range over the sorted vocabulary rather than a scan of it.
            if (isLast && token.length >= 2) {
                forEachTokenWithPrefix(token) { indexedToken ->
                    if (indexedToken != token) {
                        postingsByToken[indexedToken]?.forEach { offer(it, QUALITY_PREFIX) }
                    }
                }
            }

            // 3. Transliteration-aware fallback. Exact keys catch q/k and repeated-vowel
            //    variants; key prefixes catch shortened input such as "bakar" -> "baqarah".
            val transliterationToken = SearchTokenizer.transliterationKey(token)
            if (SearchTokenizer.isLatinSearchKey(transliterationToken)) {
                postingsByTransliterationKey[transliterationToken]
                    ?.forEach { offer(it, QUALITY_TRANSLITERATION) }

                if (isLast) {
                    postingsByTransliterationKey.forEach { (indexedKey, list) ->
                        if (indexedKey != transliterationToken &&
                            indexedKey.startsWith(transliterationToken)
                        ) {
                            list.forEach { offer(it, QUALITY_TRANSLITERATION_PREFIX) }
                        }
                    }
                }

                // Approximate candidates by shared prefix and character trigrams, ranked
                // by phonetic weighted Damerau distance. Candidate generation avoids
                // comparing the query against every indexed word as the corpus grows.
                if (transliterationToken.length >= 4 ||
                    (allowShortFuzzy && transliterationToken.length == 3)
                ) {
                    val candidateKeys = LinkedHashSet<String>()
                    if (allowShortFuzzy && transliterationToken.length <= 6) {
                        // Short voice results often lose or substitute an opening
                        // sound ("Asd" -> "Sad", "Yomos" -> "Yunus"). Surah indices
                        // are tiny, so a full vocabulary scan is safe and cheap when
                        // the caller has explicit Surah intent.
                        candidateKeys.addAll(postingsByTransliterationKey.keys)
                    } else {
                        transliterationKeysByPrefix[transliterationToken.take(2)]
                            ?.let(candidateKeys::addAll)
                        SearchTokenizer.characterTrigrams(transliterationToken).forEach { trigram ->
                            transliterationKeysByTrigram[trigram]?.let(candidateKeys::addAll)
                        }
                    }
                    candidateKeys.forEach { indexedKey ->
                        if (indexedKey == transliterationToken ||
                            indexedKey.startsWith(transliterationToken)
                        ) return@forEach
                        val approximate = approximateMatchScore(
                            query = transliterationToken,
                            indexed = indexedKey,
                            allowShortMatch = allowShortFuzzy,
                        ) ?: return@forEach
                        postingsByTransliterationKey[indexedKey]?.forEach { offer(it, approximate) }
                    }
                }
            }

            // 4. In-word substring fallback ("khlas" -> "ikhlas"). Only when nothing
            //    above matched: it reads every field of every item, so running it on
            //    terms that already have hits costs a full corpus scan for nothing.
            if (bestQuality.isEmpty()) {
                fieldNormText.forEachIndexed { itemIdx, row ->
                    row.forEachIndexed { fieldIdx, norm ->
                        if (norm.contains(token)) {
                            offer(Posting(itemIdx, fieldIdx, isStartOfWord = false), QUALITY_SUBSTRING)
                        }
                    }
                }
            }

            val itemsThisToken = HashSet<Int>()
            bestQuality.forEach { (key, quality) ->
                val itemIdx = key / fields.size
                val fieldIdx = key % fields.size
                itemScores[itemIdx] = (itemScores[itemIdx] ?: 0.0) +
                    idf * quality * relativeFieldWeight(fieldIdx) * lengthNormalisation(itemIdx, fieldIdx)
                itemsThisToken.add(itemIdx)
            }
            itemsThisToken.forEach { tokensMatched.merge(it, 1, Int::plus) }
        }

        // 5. Full-phrase substring bonus, weighted by how rare the phrase's terms are —
        //    "ayatul kursi" landing whole is strong evidence; "the of" landing whole is not.
        if (fullNormalizedQuery.length >= 3) {
            val phraseIdf = queryTokens.sumOf { inverseDocumentFrequency(it) }
            fieldNormText.forEachIndexed { itemIdx, row ->
                row.forEachIndexed { fieldIdx, norm ->
                    if (norm.contains(fullNormalizedQuery)) {
                        itemScores[itemIdx] = (itemScores[itemIdx] ?: 0.0) +
                            phraseIdf * PHRASE_BONUS * relativeFieldWeight(fieldIdx)
                    }
                }
            }
        }

        if (idealScore <= 0.0) return emptyList()
        val tokenCount = queryTokens.size.toDouble()

        return itemScores.entries
            .mapNotNull { (itemIdx, rawScore) ->
                val matched = tokensMatched[itemIdx] ?: 0
                if (matched == 0) return@mapNotNull null
                // Partial coverage is penalised on a slope rather than a cliff: two of
                // three terms should sit between one and three, not level with one.
                val coverage = COVERAGE_FLOOR +
                    (1.0 - COVERAGE_FLOOR) * (matched / tokenCount)
                val confidence = ((rawScore / idealScore) * coverage).coerceIn(0.0, 1.0)
                RankedHit(items[itemIdx], confidence, matched)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Rarity of a term across the corpus, in the BM25 formulation.
     *
     * A term in every item separates nothing and lands near zero; a term in one item
     * carries the query. Terms absent from the index — which reach here through the
     * fuzzy and prefix paths — are treated as maximally rare, since whatever they end up
     * matching is by definition unusual.
     */
    private fun inverseDocumentFrequency(token: String): Double {
        val n = items.size.toDouble()
        val df = (documentFrequency[token] ?: 1).toDouble().coerceAtMost(n)
        return kotlin.math.ln(1.0 + (n - df + 0.5) / (df + 0.5))
    }

    /**
     * BM25 length normalisation: a term filling a two-word name says more about that
     * item than the same term buried in a paragraph.
     */
    private fun lengthNormalisation(itemIndex: Int, fieldIndex: Int): Double {
        val length = fieldWordCount[itemIndex][fieldIndex].coerceAtLeast(1).toDouble()
        val average = averageFieldWordCount[fieldIndex]
        return (LENGTH_K1 + 1.0) / (1.0 + LENGTH_K1 * (1.0 - LENGTH_B + LENGTH_B * length / average))
    }

    /** Field weights as a 0..1 fraction, so one index's scale is another's. */
    private fun relativeFieldWeight(fieldIndex: Int): Double =
        fields[fieldIndex].weight / maxFieldWeight

    /** Visits every indexed token starting with [prefix], via binary search. */
    private inline fun forEachTokenWithPrefix(prefix: String, action: (String) -> Unit) {
        var low = 0
        var high = sortedTokens.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sortedTokens[mid] < prefix) low = mid + 1 else high = mid
        }
        var index = low
        while (index < sortedTokens.size && sortedTokens[index].startsWith(prefix)) {
            action(sortedTokens[index])
            index++
        }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var index = 0
        while (index < limit && a[index] == b[index]) index++
        return index
    }

    private fun approximateMatchScore(
        query: String,
        indexed: String,
        allowShortMatch: Boolean = false,
    ): Double? {
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
        val maximumDistance = when (minimumLength) {
            in 0..3 -> 0.75
            4 -> 1.0
            in 5..6 -> 1.55
            in 7..9 -> 1.65
            else -> 2.0
        }
        val distance = weightedDamerauLevenshtein(query, indexed)
        val shortDistanceAccepted = allowShortMatch && minimumLength == 3 &&
            distance <= maximumDistance
        if (!shortDistanceAccepted && sharedPrefix < 2 && dice < minimumDice) return null
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
            // Common speech-to-text confusion in short names (Yumos/Yunus).
            samePair(a, b, 'm', 'n') -> 0.55
            else -> 1.0
        }
    }

    private fun samePair(a: Char, b: Char, first: Char, second: Char): Boolean =
        (a == first && b == second) || (a == second && b == first)

    private companion object {
        val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
        const val TRANSPOSITION_COST = 0.55

        /**
         * How much of a term's rarity each kind of match is allowed to claim.
         *
         * Ordered deliberately and with gaps: an exact hit on a weak field must still
         * beat a fuzzy hit on a strong one, which the previous additive scoring could
         * not guarantee.
         */
        const val QUALITY_EXACT = 1.0
        const val QUALITY_TRANSLITERATION = 0.78
        const val QUALITY_PREFIX = 0.62
        const val QUALITY_TRANSLITERATION_PREFIX = 0.55
        const val QUALITY_SUBSTRING = 0.45

        /** A term opening its field is stronger evidence than one inside it. */
        const val START_OF_WORD_BONUS = 1.15

        /** BM25 term-saturation and length-normalisation constants. */
        const val LENGTH_K1 = 1.2
        const val LENGTH_B = 0.75

        /**
         * Length normalisation at a field of average length — the reference point the
         * final score is expressed against.
         */
        const val AVERAGE_LENGTH_NORMALISATION = (LENGTH_K1 + 1.0) / (1.0 + LENGTH_K1)

        /** Weight of a whole-query substring landing inside one field. */
        const val PHRASE_BONUS = 0.55

        /** Score retained by an item matching only one term of a multi-term query. */
        const val COVERAGE_FLOOR = 0.55
    }
}
