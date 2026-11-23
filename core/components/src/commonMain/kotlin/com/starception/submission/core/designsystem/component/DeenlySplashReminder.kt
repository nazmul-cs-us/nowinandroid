package com.starception.submission.core.designsystem.component

data class DeenlySplashReminder(val text: String, val reference: String)

/** Keep the canonical reference for rotation; use a readable source line in the artwork. */
fun splashReminderCitation(reference: String): String {
    val match = Regex("Quran (\\d+):(\\d+)").matchEntire(reference) ?: return reference
    val surah = when (match.groupValues[1]) {
        "2" -> "Al-Baqarah"
        "20" -> "Ta-Ha"
        "94" -> "Ash-Sharh"
        "103" -> "Al-Asr"
        "108" -> "Al-Kawthar"
        "112" -> "Al-Ikhlas"
        else -> return reference
    }
    return "Surah $surah · Ayah ${match.groupValues[2]}"
}

/** Short English translation excerpts. Never synthesize scripture or a hadith quotation. */
fun deenlySplashReminder(day: Long, knowledgeModelDownloaded: Boolean, hadithDownloaded: Boolean): DeenlySplashReminder {
    val quran = listOf(
        DeenlySplashReminder("My Lord, increase me in knowledge.", "Quran 20:114"),
        DeenlySplashReminder("Indeed, with hardship comes ease.", "Quran 94:5"),
        DeenlySplashReminder("So remember Me; I will remember you.", "Quran 2:152"),
        DeenlySplashReminder("Say, He is Allah, who is One.", "Quran 112:1"),
        DeenlySplashReminder("Allah, the Eternal Refuge.", "Quran 112:2"),
        DeenlySplashReminder("By time.", "Quran 103:1"),
        DeenlySplashReminder("Indeed, We have granted you al-Kawthar.", "Quran 108:1"),
    )
    val choices = if (knowledgeModelDownloaded && hadithDownloaded) quran + listOf(
        DeenlySplashReminder("The best among you are those who learn the Quran and teach it.", "Sahih al-Bukhari 5027"),
    ) else quran
    return choices[((day % choices.size + choices.size) % choices.size).toInt()]
}

/** Advance by reference, so a model/catalog change cannot immediately repeat the last passage. */
fun nextDeenlySplashReminder(previousReference: String?, knowledgeModelDownloaded: Boolean,
    hadithDownloaded: Boolean): DeenlySplashReminder {
    val first = deenlySplashReminder(0, knowledgeModelDownloaded, hadithDownloaded)
    var index = 0L
    while (true) {
        val current = deenlySplashReminder(index, knowledgeModelDownloaded, hadithDownloaded)
        if (current.reference == previousReference) {
            return deenlySplashReminder(index + 1, knowledgeModelDownloaded, hadithDownloaded)
        }
        index++
        if (deenlySplashReminder(index, knowledgeModelDownloaded, hadithDownloaded) == first) return first
    }
}
