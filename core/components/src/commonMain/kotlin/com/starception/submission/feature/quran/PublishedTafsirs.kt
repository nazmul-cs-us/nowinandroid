package com.starception.submission.feature.quran

/** A published edition, never an automatic translation of another book. Storage indexes are stable. */
data class PublishedTafsirEdition(
    val languageCode: String, val storageIndex: Int, val languageName: String,
    val englishLanguageName: String, val title: String, val bookKey: String,
    val attribution: String, val sourceUrl: String, val cdnPrefix: String, val category: String,
) {
    fun chapterKey(surah: Int): String {
        require(surah in 1..114) { "Invalid surah" }
        return "$cdnPrefix/$surah.json"
    }
}

object PublishedTafsirs {
    // Append new indexes; index 4 and ibn_kathir_bn preserve existing Bengali selections.
    val editions = listOf(
        PublishedTafsirEdition("bn", 4, "বাংলা", "Bengali", "তাফসীর ইবনে কাসীর", "ibn_kathir_bn", "Tawheed Publication", "https://qul.tarteel.ai/resources/tafsir/31", "tafsir/bn-ibn-kathir/v1", "quran_tafsir_bn"),
        PublishedTafsirEdition("en", 5, "English", "English", "Ibn Kathir (abridged)", "ibn_kathir_en", "Hafiz Ibn Kathir", "https://qul.tarteel.ai/resources/tafsir/35", "tafsir/en-ibn-kathir/v1", "quran_tafsir_en"),
        PublishedTafsirEdition("hi", 6, "हिन्दी", "Hindi", "Al-Mukhtasar", "mukhtasar_hi", "QUL / Tarteel", "https://qul.tarteel.ai/resources/tafsir/535", "tafsir/hi-mukhtasar/v1", "quran_tafsir_hi"),
        PublishedTafsirEdition("es", 7, "Español", "Spanish", "Al-Mukhtasar", "mukhtasar_es", "QUL / Tarteel", "https://qul.tarteel.ai/resources/tafsir/268", "tafsir/es-mukhtasar/v1", "quran_tafsir_es"),
        PublishedTafsirEdition("fr", 8, "Français", "French", "Al-Mukhtasar", "mukhtasar_fr", "QUL / Tarteel", "https://qul.tarteel.ai/resources/tafsir/259", "tafsir/fr-mukhtasar/v1", "quran_tafsir_fr"),
        PublishedTafsirEdition("id", 9, "Indonesia", "Indonesian", "Al-Mukhtasar", "mukhtasar_id", "QUL / Tarteel", "https://qul.tarteel.ai/resources/tafsir/260", "tafsir/id-mukhtasar/v1", "quran_tafsir_id"),
        PublishedTafsirEdition("ru", 10, "Русский", "Russian", "Тафсир Ибн Касира", "ibn_kathir_ru", "Hafiz Ibn Kathir", "https://qul.tarteel.ai/resources/tafsir/307", "tafsir/ru-ibn-kathir/v1", "quran_tafsir_ru"),
        PublishedTafsirEdition("tr", 11, "Türkçe", "Turkish", "Al-Mukhtasar", "mukhtasar_tr", "QUL / Tarteel", "https://qul.tarteel.ai/resources/tafsir/258", "tafsir/tr-mukhtasar/v1", "quran_tafsir_tr"),
        PublishedTafsirEdition("ur", 12, "اردو", "Urdu", "تفسیر ابن کثیر", "ibn_kathir_ur", "Hafiz Ibn Kathir", "https://qul.tarteel.ai/resources/tafsir/30", "tafsir/ur-ibn-kathir/v1", "quran_tafsir_ur"),
        PublishedTafsirEdition("zh", 13, "中文", "Chinese", "Al-Mukhtasar", "mukhtasar_zh", "QUL / Tarteel", "https://qul.tarteel.ai/resources/tafsir/264", "tafsir/zh-mukhtasar/v1", "quran_tafsir_zh"),
    )
    val bengali: PublishedTafsirEdition get() = editions.first()
    fun forLanguage(code: String): PublishedTafsirEdition? = editions.firstOrNull { it.languageCode == code }
    fun forBook(key: String): PublishedTafsirEdition? = editions.firstOrNull { it.bookKey == key }
    fun forCategory(category: String): PublishedTafsirEdition? = editions.firstOrNull { it.category == category }
    fun forStorageIndex(index: Int): PublishedTafsirEdition? = editions.firstOrNull { it.storageIndex == index }
    fun defaultStorageIndex(code: String): Int = forLanguage(code)?.storageIndex ?: 0
    fun validStorageIndex(index: Int): Boolean = index in 0..3 || forStorageIndex(index) != null
}
