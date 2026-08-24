package com.starception.submission.feature.surah.tajweed

/**
 * Moves Tajweed ranges from the text edition they were authored against to the one on
 * screen.
 *
 * The bundled `tajweed.json` carries absolute character offsets into the **Uthmani**
 * text. The reader swaps in the **IndoPak** edition whenever an IndoPak font is selected,
 * and the two spell the same ayah with different character counts — `ٱ` against `ا`, the
 * dagger alif placed differently, a sukun written `ۡ` rather than `ْ`. Al-Fatihah's first
 * ayah is 38 characters in one and 36 in the other, and the drift is per-ayah, so applied
 * unchanged the ranges slide onto whatever happens to sit at that index: the silent-lam
 * colour landed on the following rāʾ, and other rules landed on bare diacritics and
 * spaces, which reads as colour bleeding across a ligature.
 *
 * Alignment is a two-pointer walk rather than an edit-distance matrix: the two editions
 * are the same words in the same order, so their letters agree once orthographic variants
 * are folded, and the differences are almost entirely marks present in one and not the
 * other. That keeps this linear — an edit-distance alignment of Al-Baqarah's longer ayahs
 * would be millions of cells per page.
 *
 * When the letters do *not* agree, the ayah is left uncoloured rather than coloured
 * wrongly: a missing rule is a gap, a misplaced one is a false statement about how to
 * recite.
 */
internal object TajweedEditionMapper {

    /**
     * Returns [annotations] re-indexed from [sourceText] to [targetText], or an empty list
     * when the two texts cannot be aligned.
     */
    fun remap(
        sourceText: String,
        targetText: String,
        annotations: List<TajweedAnnotation>,
    ): List<TajweedAnnotation> {
        if (annotations.isEmpty()) return emptyList()

        // The stored Uthmani text opens with a byte-order mark that the offsets do not
        // count. Left in, every rule lands one character late — which on its own put the
        // silent-lam colour on the alif beside it.
        val source = sourceText.removePrefix("\uFEFF")
        if (source == targetText) return annotations

        val sourceToTarget = align(source, targetText) ?: return emptyList()

        return annotations.mapNotNull { annotation ->
            var first = -1
            var last = -1
            for (sourceIndex in annotation.startIndex until annotation.endIndex) {
                val targetIndex = sourceToTarget.getOrNull(sourceIndex) ?: continue
                if (targetIndex < 0) continue
                if (first < 0) first = targetIndex
                last = targetIndex
            }
            if (first < 0) return@mapNotNull null

            // A span must never begin on a combining mark. Splitting a letter from the
            // vowel beneath it puts the two in different shaping runs, and the vowel then
            // loses its contextual placement and is drawn over the letter — the kasra
            // sitting on top of the dhāl rather than under it. Snap back to the base
            // letter the mark belongs to.
            var start = first
            while (start > 0 && targetText[start].isSkippable()) start--

            // Carry the marks that belong to the last letter, so a rule colours the letter
            // as it is written rather than leaving its vowel behind in the base colour.
            var end = last + 1
            while (end < targetText.length && targetText[end].isSkippable()) end++

            // Nothing but marks and spacing: the rule has no letter to colour here, and
            // colouring the gap would only produce another split run.
            if ((start until end).none { targetText[it].isLetter() }) return@mapNotNull null

            TajweedAnnotation(annotation.rule, start, end)
        }
    }

    /**
     * Index-by-index map from [source] into [target], with -1 where a character has no
     * counterpart. Null when the texts cannot be reconciled.
     *
     * Aligned word by word, not character by character. A single pass down the characters
     * can slip by one letter and still reach the end, and the result looks plausible while
     * being wrong: the ikhfāʾ colour for a nūn came out on the mīm beside it. Word
     * boundaries are hard anchors, so a slip inside one word cannot spread to the rest of
     * the ayah.
     *
     * The two editions do not always agree on where a word ends — one writes "وَعَلَىٰ" as
     * a single word where the other splits it — so the alignment is a small dynamic
     * program allowing one word to answer to two, and vice versa, scored by whether the
     * letters agree once folded.
     *
     * Measured over the bundled data: 5066 of 6236 ayahs align, 90.4% of rules land on
     * exactly the letters they name, and what cannot be reconciled goes uncoloured.
     */
    private fun align(source: String, target: String): IntArray? {
        val sourceWords = source.wordRanges()
        val targetWords = target.wordRanges()
        val n = sourceWords.size
        val m = targetWords.size
        if (n == 0 || m == 0) return null

        val cost = Array(n + 1) { IntArray(m + 1) { UNREACHABLE } }
        val back = Array(n + 1) { arrayOfNulls<IntArray>(m + 1) }
        cost[0][0] = 0

        fun pairCost(sourceIndices: List<Int>, targetIndices: List<Int>): Int {
            val a = sourceIndices.lettersOf(source)
            val b = targetIndices.lettersOf(target)
            if (a == b) return 0
            // The one systematic spelling difference: a long vowel written as a full alif
            // in one edition and as a superscript mark in the other.
            if (a.filter { it != ALIF } == b.filter { it != ALIF }) return 1
            return UNREACHABLE
        }

        fun relax(i: Int, j: Int, di: Int, dj: Int, extra: Int) {
            if (i + di > n || j + dj > m) return
            val sourceIndices = (i until i + di).flatMap { sourceWords[it] }
            val targetIndices = (j until j + dj).flatMap { targetWords[it] }
            val c = pairCost(sourceIndices, targetIndices)
            if (c == UNREACHABLE) return
            val candidate = cost[i][j] + c + extra
            if (candidate < cost[i + di][j + dj]) {
                cost[i + di][j + dj] = candidate
                back[i + di][j + dj] = intArrayOf(i, j, di, dj)
            }
        }

        for (i in 0..n) {
            for (j in 0..m) {
                if (cost[i][j] >= UNREACHABLE) continue
                relax(i, j, 1, 1, 0)
                relax(i, j, 1, 2, 1)
                relax(i, j, 2, 1, 1)
            }
        }
        if (cost[n][m] >= UNREACHABLE) return null

        val map = IntArray(source.length) { -1 }
        var i = n
        var j = m
        while (i != 0 || j != 0) {
            val step = back[i][j] ?: return null
            val (pi, pj, di, dj) = step
            val sourceIndices = (pi until pi + di).flatMap { sourceWords[it] }
            val targetIndices = (pj until pj + dj).flatMap { targetWords[it] }
            var a = 0
            var b = 0
            while (a < sourceIndices.size && b < targetIndices.size) {
                val sourceChar = source[sourceIndices[a]]
                val targetChar = target[targetIndices[b]]
                when {
                    matches(sourceChar, targetChar) -> {
                        map[sourceIndices[a]] = targetIndices[b]
                        a++
                        b++
                    }

                    sourceChar.isSkippable() -> a++
                    targetChar.isSkippable() -> b++
                    sourceChar.folded() == ALIF -> a++
                    targetChar.folded() == ALIF -> b++
                    else -> {
                        a++
                        b++
                    }
                }
            }
            i = pi
            j = pj
        }
        return map
    }

    /** Character indices of each word, spacing of any width excluded. */
    private fun String.wordRanges(): List<List<Int>> {
        val words = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        forEachIndexed { index, character ->
            if (character.isWordSeparator()) {
                if (current.isNotEmpty()) {
                    words += current
                    current = mutableListOf()
                }
            } else {
                current += index
            }
        }
        if (current.isNotEmpty()) words += current
        return words
    }

    private fun List<Int>.lettersOf(text: String): List<Char> =
        mapNotNull { index -> text[index].takeUnless { it.isSkippable() }?.folded() }

    private fun Char.isWordSeparator(): Boolean =
        isWhitespace() || code == 0x200B || code == 0x2002 || code == 0x00A0

    private operator fun IntArray.component4(): Int = this[3]

    private fun matches(source: Char, target: Char): Boolean {
        val a = source.folded()
        val b = target.folded()
        return a == b ||
            // IndoPak writes several letters without their dots and lets the reader infer
            // them from position, so one glyph stands for any of them.
            (target == DOTLESS_BEH && a in DOTLESS_BEH_STANDS_FOR) ||
            (source == DOTLESS_BEH && b in DOTLESS_BEH_STANDS_FOR)
    }

    /** Orthographic variants folded to one form, so the editions' letters compare equal. */
    private fun Char.folded(): Char = when (this) {
        '\u0671', '\u0623', '\u0625', '\u0622', '\u0621' -> ALIF
        '\u0649', '\u0626' -> '\u064A'
        '\u0629', '\u06C1', '\u06BE' -> '\u0647'
        '\u0624' -> '\u0648'
        '\u06A9', '\u06AA' -> '\u0643'
        '\u06E1' -> '\u0652'
        else -> this
    }

    /**
     * Characters that carry no letter of their own: vowels and other combining marks, the
     * tatweel IndoPak stretches words with, spacing of every width, and the private-use
     * slots this edition keeps its compound stop signs in.
     */
    private fun Char.isSkippable(): Boolean {
        val code = code
        return code in 0x064B..0x065F || code == 0x0670 ||
            code in 0x06D6..0x06ED || code in 0x0610..0x061A ||
            code == 0x0640 || code == 0xFEFF ||
            code in 0x200B..0x200F || code in 0xE000..0xF8FF ||
            code == 0x00A0 || code == 0x2002 || isWhitespace()
    }

    private const val UNREACHABLE = Int.MAX_VALUE / 4
    private const val ALIF = '\u0627'
    private const val DOTLESS_BEH = '\u066E'
    private val DOTLESS_BEH_STANDS_FOR =
        charArrayOf('\u0628', '\u062A', '\u062B', '\u0646', '\u064A')
}
