package com.starception.submission.voice.handsfree

import com.starception.submission.feature.quran.QuranData
import org.junit.Assert.*
import org.junit.Test

class HandsFreeCommandTest {
    @Test fun bakarahAliasesResolveToTheSecondSurah() {
        listOf("play Bakarah", "Play Al-Baqarah", "please recite surah baqara", "Hey Deenly, play Bakara, please",
            "Could you please play Surah Baqarah?")
            .forEach { assertEquals(it, HandsFreeCommand.PlaySurah(2), HandsFreeCommandParser.parse(it)) }
    }

    @Test fun allCanonicalSurahNamesAreSupported() {
        QuranData.surahs.forEach { surah ->
            assertEquals(surah.nameEnglish, HandsFreeCommand.PlaySurah(surah.number), HandsFreeCommandParser.parse("play ${surah.nameEnglish}"))
        }
    }

    @Test fun controlsAreExplicitAndDoNotTreatResumeAsAToggle() {
        assertEquals(HandsFreeCommand.Pause, HandsFreeCommandParser.parse("pause"))
        assertEquals(HandsFreeCommand.Resume, HandsFreeCommandParser.parse("resume"))
        assertEquals(HandsFreeCommand.Stop, HandsFreeCommandParser.parse("stop playback"))
        assertEquals(HandsFreeCommand.Next, HandsFreeCommandParser.parse("next surah"))
        assertEquals(HandsFreeCommand.Previous, HandsFreeCommandParser.parse("previous chapter"))
    }

    @Test fun ambiguousInvalidOrDestructiveSpeechDoesNotRunAnything() {
        listOf("Bakarah", "don't play Bakarah", "play 0", "play surah 115", "play Quran", "delete everything",
            "play Bakarah and delete my bookmarks", "", "thank you for watching")
            .forEach { assertNull(it, HandsFreeCommandParser.parse(it)) }
        assertEquals(HandsFreeCommand.PlaySurah(114), HandsFreeCommandParser.parse("play chapter 114"))
    }

    @Test fun wakePhrasesUseValidTokensAndNeverAddYesNoCommands() {
        val vocabulary = "▁HE 49\nY 17\n▁DE 72\nEN 63\nLY 51\nAN 92\n▁LE 170\nE 10"
        val keywords = DeenlyWakePhrase.keywords(vocabulary).lineSequence().filter(String::isNotBlank).toList()
        assertEquals(2, keywords.size)
        assertTrue(keywords.all { it.endsWith("@hey_deenly") })
        assertFalse(keywords.any { "yes" in it || "no" in it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompatibleModelsFailBeforeOpeningTheMicrophone() {
        DeenlyWakePhrase.keywords("<blk> 0\n<unk> 1")
    }
}
