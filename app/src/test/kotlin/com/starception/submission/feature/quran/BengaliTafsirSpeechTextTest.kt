package com.starception.submission.feature.quran

import org.junit.Assert.*
import org.junit.Test

class BengaliTafsirSpeechTextTest {
    @Test fun expandsCompanionHonorificWithoutGuessingThePersonsGender() {
        for (name in listOf("আবু হুরাইরা", "আয়েশা")) {
            assertEquals("$name আল্লাহ তাঁর প্রতি সন্তুষ্ট হোন বলেন।",
                normalizeTafsirSpeechText("$name (রাঃ) বলেন।", "bn"))
        }
    }
    @Test fun companionMercyPropheticAndPeaceAbbreviationsHaveDifferentExpansions() {
        assertEquals("আল্লাহ তাঁর প্রতি সন্তুষ্ট হোন", normalizeTafsirSpeechText("(রাঃ)", "bn"))
        assertEquals("আল্লাহ তাঁর প্রতি রহম করুন", normalizeTafsirSpeechText("(রঃ)", "bn"))
        assertEquals("আল্লাহ তাঁর প্রতি রহম করুন", normalizeTafsirSpeechText("(রহঃ)", "bn"))
        assertEquals("সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম", normalizeTafsirSpeechText("(সঃ)", "bn"))
        assertEquals("সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম", normalizeTafsirSpeechText("(সাঃ)", "bn"))
        assertEquals("আল্লাহর শান্তি বর্ষিত হোক", normalizeTafsirSpeechText("(আঃ)", "bn"))
    }
    @Test fun punctuationAndSpacingVariantsAndRegionalBengaliAreHandled() {
        for (token in listOf("(রা.)", "(রা:)", "( রাঃ )", "(রা .)"))
            assertEquals("আল্লাহ তাঁর প্রতি সন্তুষ্ট হোন", normalizeTafsirSpeechText(token, "bn-BD"))
        assertEquals("আল্লাহ তাঁর প্রতি রহম করুন", normalizeTafsirSpeechText("(র.)", "bn_IN"))
        assertEquals("হযরত আল্লাহ তাঁর প্রতি সন্তুষ্ট হোন বলেন।", normalizeTafsirSpeechText("হযরত(রাঃ)বলেন।", "bn"))
    }
    @Test fun honorificsInsideLargerParenthesesAreExpandedWithoutRemovingTheirWords() {
        assertEquals("(হে নবী সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম!)",
            normalizeTafsirSpeechText("(হে নবী সঃ!)", "bn"))
        assertEquals("ইবনে কাসীর আল্লাহ তাঁর প্রতি রহম করুন বলেছেন।", normalizeTafsirSpeechText("ইবনে কাসীর রঃ বলেছেন।", "bn"))
    }
    @Test fun ordinaryWordsInitialsVerseReferencesAndMissingArabicPlaceholdersArePreserved() {
        val text = "তারা। রাগ। ধারা: আলোচনা। র. ক. নাম। (২:২৫৫) (আরবি) (আরবী)"
        assertEquals(text, normalizeTafsirSpeechText(text, "bn"))
    }
    @Test fun nonBengaliTextIsNeverChanged() {
        val text = "A quotation (রাঃ), ﷺ, رضي الله عنه."
        assertEquals(text, normalizeTafsirSpeechText(text, "en"))
        assertEquals(text, normalizeTafsirSpeechText(text, "ar"))
    }
    @Test fun expansionIsIdempotentAndAlsoHandlesHonorificSymbols() {
        val text = normalizeTafsirSpeechText("নবী ﷺ ও সাহাবী ؓ", "bn")
        assertEquals(text, normalizeTafsirSpeechText(text, "bn"))
        assertTrue(text.contains("সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম"))
        assertTrue(text.contains("আল্লাহ তাঁর প্রতি সন্তুষ্ট হোন"))
    }
}
