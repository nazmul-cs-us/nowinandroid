package com.starception.submission.feature.quran

/** Language-scoped first-run downloads. Published editions remain optional after setup. */
object QuranSetupContent {
    fun tafsirEdition(language: String, setupComplete: Boolean = false): PublishedTafsirEdition? =
        if (setupComplete) null else PublishedTafsirs.forLanguage(language)

    fun tafsirAssets(language: String, setupComplete: Boolean = false): List<String> {
        val edition = tafsirEdition(language, setupComplete) ?: return emptyList()
        return (1..114).map(edition::chapterKey) + listOf("${edition.cdnPrefix}/manifest.json", "${edition.cdnPrefix}/NOTICE.txt")
    }

    fun requiredCategories(base: Set<String>, language: String, setupComplete: Boolean = false): Set<String> =
        base + listOfNotNull(tafsirEdition(language, setupComplete)?.category)

    fun tafsirReady(language: String, setupComplete: Boolean = false, isAvailable: (String) -> Boolean): Boolean =
        tafsirAssets(language, setupComplete).all(isAvailable)
}
