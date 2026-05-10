/*
 * Copyright 2025 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.core.ui

/**
 * Single Arabic line to display above the card excerpt (or null if none).
 *
 * - Dua: first line of the Arabic recitation.
 * - Surah: the Arabic surah name.
 * - Hadith: not currently provided in card content.
 */
fun smartCardArabicLine(content: String, type: String): String? {
    val lower = type.lowercase()
    val raw = when {
        // Surah cards prefer the first ayah; fall back to the surah name.
        "surah" in lower -> extractSection(content, "FirstAyah")
            ?: extractSection(content, "Arabic")
        "dua" in lower -> extractSection(content, "Arabic")
        else -> null
    } ?: return null
    return raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
}

/**
 * Picks the most useful preview text for a news card based on its [type].
 *
 * Content in this app is generated with **Section:** markers (see
 * core/contentdatabase/NewsDbGenerator.kt). Different types deserve different
 * highlights:
 *
 * - Dua: the English translation is the most readable; skip Arabic/transliteration/explanation.
 * - Surah: a compact "Type • N verses" line; the full preamble is filler.
 * - Hadith: the hadith text only; the narrator is shown as a separate metadata in the detail view.
 * - Default (Article/Video/Codelab/etc.): the first non-empty paragraph.
 */
fun smartCardExcerpt(content: String, type: String): String {
    val lower = type.lowercase()
    return when {
        "dua" in lower -> extractSection(content, "Translation")
            ?: extractSection(content, "Context")
            ?: firstParagraph(content)
        "surah" in lower -> buildSurahSummary(content) ?: firstParagraph(content)
        "hadith" in lower -> stripNarrator(content)
        else -> firstParagraph(content)
    }.trim()
}

/** Returns the body of `**Name:**` ... up to the next `**` marker, blank line, or end. */
private fun extractSection(content: String, name: String): String? {
    val regex = Regex(
        pattern = """\*\*${Regex.escape(name)}:?\*\*\s*([\s\S]*?)(?=\n\s*\n|\n\s*\*\*|\z)""",
        option = RegexOption.IGNORE_CASE,
    )
    return regex.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/** "Medinan • 286 verses" — falls back to null if the markers are missing. */
private fun buildSurahSummary(content: String): String? {
    val type = extractSection(content, "Type")
    val verses = extractSection(content, "Verses")
    return when {
        type != null && verses != null -> "$type • $verses verses"
        type != null -> type
        verses != null -> "$verses verses"
        else -> null
    }
}

/** Hadith content is "[narrator]\n\n[text]"; drop the narrator line. */
private fun stripNarrator(content: String): String {
    val parts = content.split("\n\n", limit = 2)
    return if (parts.size == 2) parts[1].trim() else content.trim()
}

/** First non-empty paragraph; strips any leading **Marker:** label. */
private fun firstParagraph(content: String): String {
    val paragraph = content
        .split("\n\n")
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: content
    return paragraph
        .replace(Regex("""^\*\*[^*]+\*\*\s*"""), "")
        .trim()
}
