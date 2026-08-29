package com.starception.submission.util

/** Renders a content number with the numeral system used by the selected reading language. */
fun Int.toLocalizedDigits(languageCode: String): String {
    val normalizedCode = languageCode.lowercase().substringBefore('-').substringBefore('_')
    val zero = when (normalizedCode) {
        "ar" -> '\u0660' // ٠ Arabic-Indic
        "ur", "fa" -> '\u06F0' // ۰ Extended Arabic-Indic
        "bn" -> '\u09E6' // ০ Bengali
        else -> return toString()
    }
    return toString().map { character ->
        if (character in '0'..'9') zero + (character - '0') else character
    }.joinToString("")
}
