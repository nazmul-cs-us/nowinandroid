package com.starception.submission.download

/** A reading preference, not a request to download every translation or any audio. */
internal object ContentReadingLanguage {
    const val CATEGORY = "selected_reading_translation"

    val languages: List<Pair<String, String>> = listOf(
        "ar" to "العربية (Arabic)",
        "en" to "English",
        "bn" to "বাংলা",
        "hi" to "हिन्दी",
        "es" to "Español",
        "fr" to "Français",
        "id" to "Indonesia",
        "ru" to "Русский",
        "sv" to "Svenska",
        "tr" to "Türkçe",
        "ur" to "اردو",
        "zh" to "中文",
    )

    fun assetKey(code: String): String? = when (code) {
        "", "ar" -> null
        else -> "databases/quran/quran_$code.db"
    }

    fun displayName(code: String): String = languages.firstOrNull { it.first == code }?.second ?: code

    suspend fun ensureAvailable(
        code: String,
        isAvailable: (String) -> Boolean,
        download: suspend (String) -> Boolean,
    ): Boolean {
        val key = assetKey(code) ?: return true
        if (isAvailable(key)) return true
        return download(key) && isAvailable(key)
    }
}
