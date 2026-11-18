package com.starception.submission.feature.quran

/** Speech-only expansion; the published text and ayah mapping remain untouched. */
fun normalizeTafsirSpeechText(text: String, language: String): String {
    if (!language.substringBefore('-').substringBefore('_').equals("bn", ignoreCase = true)) return text
    return BengaliHonorificSpeech.expand(text)
}

private object BengaliHonorificSpeech {
    // Use the requested Arabic pronunciation in Bengali script for the companion honorific.
    private const val COMPANION = "রাদিয়াল্লাহু আনহু"
    private const val MERCY = "রহমাতুল্লাহি আলাইহি"
    private const val PROPHET = "সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম"
    private const val PEACE = "আলাইহিস সালাম"

    private class Rule(bracketToken: String, embeddedToken: String, val spoken: String) {
        val bracketed = Regex("\\(\\s*(?:$bracketToken)\\s*\\)")
        // Prefix/suffix boundaries avoid replacing letters inside ordinary Bengali words.
        val embedded = Regex("(^|[\\s(\\[“‘,;])(?:$embeddedToken)(?=$|[\\s)\\]”’!?,;।-])")
    }
    private val rules = listOf(
        Rule("(?:রা\\s*[ঃ:.]+|র\\s*\\.)", "রা\\s*[ঃ:]+", COMPANION),
        Rule("(?:রহ|র)\\s*[ঃ:.]+", "(?:রহ|র)\\s*[ঃ:]+", MERCY),
        Rule("সা?\\s*[ঃ:.]+", "সা?\\s*[ঃ:]+", PROPHET),
        Rule("আ\\s*[ঃ:.]+", "আ\\s*[ঃ:]+", PEACE),
    )
    private val symbols = mapOf('ﷺ' to PROPHET, 'ؐ' to PROPHET, 'ؓ' to COMPANION, 'ؒ' to MERCY, 'ؑ' to PEACE)

    fun expand(text: String): String {
        var spoken = text
        for (rule in rules) {
            spoken = spoken.replace(rule.bracketed, " ${rule.spoken} ")
            spoken = spoken.replace(rule.embedded) { "${it.groupValues[1]}${rule.spoken} " }
        }
        for ((symbol, phrase) in symbols) spoken = spoken.replace(symbol.toString(), " $phrase ")
        if (spoken == text) return text
        return spoken.replace(Regex("[ \\t\\u00a0]+"), " ")
            .replace(Regex(" +([,;!?।)])"), "$1")
            .trim()
    }
}
