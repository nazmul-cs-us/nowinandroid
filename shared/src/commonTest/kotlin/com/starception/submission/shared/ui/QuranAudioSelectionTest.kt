package com.starception.submission.shared.ui

import com.starception.submission.core.assetcache.AssetEntry
import com.starception.submission.shared.audio.selectQuranAudioAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuranAudioSelectionTest {
    private fun asset(key: String, category: String) = AssetEntry(key, 1, "checksum", category, false)

    @Test
    fun selectedTranslationDoesNotFallBackToDownloadedArabic() {
        val arabic = asset("audio/quran/arabic/001-al-fatihah.ogg", "quran_audio_arabic")
        assertNull(selectQuranAudioAsset(1, "en", listOf(arabic)))
    }

    @Test
    fun selectsTranslationAndSurahDespiteFilenameSpelling() {
        val english = asset("audio/quran/english/031Surah_luqman.ogg", "quran_audio_english")
        val wrong = asset("audio/quran/english/0310-other.ogg", "quran_audio_english")
        assertEquals(english, selectQuranAudioAsset(31, "en", listOf(wrong, english)))
    }

    @Test
    fun matchesBengaliUnicodeFilename() {
        val bengali = asset("audio/quran/bengali/001 - Al-Fatihah - سورة الفاتحة.ogg", "quran_audio_bengali")
        assertEquals(bengali, selectQuranAudioAsset(1, "bn", listOf(bengali)))
    }

    @Test
    fun matchesUrduAndHindiMp3Recordings() {
        for ((code, language) in listOf("ur" to "urdu", "hi" to "hindi")) {
            val entry = asset("audio/quran/$language/114 - An-Nas.mp3", "quran_audio_$language")
            assertEquals(entry, selectQuranAudioAsset(114, code, listOf(entry)))
        }
    }
}
