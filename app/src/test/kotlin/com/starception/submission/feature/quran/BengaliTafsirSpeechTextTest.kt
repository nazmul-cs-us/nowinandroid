package com.starception.submission.feature.quran

import org.junit.Assert.*
import org.junit.Test

class BengaliTafsirSpeechTextTest {
    @Test fun companionAbbreviationUsesTheRequestedSpokenPhrase() {
        for (name in listOf("আবু হুরাইরা", "উমর")) {
            assertEquals("$name রাদিয়াল্লাহু আনহু বলেন।",
                normalizeTafsirSpeechText("$name (রাঃ) বলেন।", "bn"))
        }
    }
    @Test fun companionMercyPropheticAndPeaceAbbreviationsHaveDifferentExpansions() {
        assertEquals("রাদিয়াল্লাহু আনহু", normalizeTafsirSpeechText("(রাঃ)", "bn"))
        assertEquals("রহমাতুল্লাহি আলাইহি", normalizeTafsirSpeechText("(রঃ)", "bn"))
        assertEquals("রহমাতুল্লাহি আলাইহি", normalizeTafsirSpeechText("(রহঃ)", "bn"))
        assertEquals("সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম", normalizeTafsirSpeechText("(সঃ)", "bn"))
        assertEquals("সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম", normalizeTafsirSpeechText("(সাঃ)", "bn"))
        assertEquals("আলাইহিস সালাম", normalizeTafsirSpeechText("(আঃ)", "bn"))
    }
    @Test fun punctuationAndSpacingVariantsAndRegionalBengaliAreHandled() {
        for (token in listOf("(রা.)", "(রা:)", "( রাঃ )", "(রা .)"))
            assertEquals("রাদিয়াল্লাহু আনহু", normalizeTafsirSpeechText(token, "bn-BD"))
        assertEquals("রাদিয়াল্লাহু আনহু", normalizeTafsirSpeechText("(র.)", "bn_IN"))
        assertEquals("হযরত রাদিয়াল্লাহু আনহু বলেন।", normalizeTafsirSpeechText("হযরত(রাঃ)বলেন।", "bn"))
        for (token in listOf("(সা.)", "(স.)", "(সা:)"))
            assertEquals("সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম", normalizeTafsirSpeechText(token, "bn"))
        assertEquals("আলাইহিস সালাম", normalizeTafsirSpeechText("(আ.)", "bn"))
        assertEquals("রহমাতুল্লাহি আলাইহি", normalizeTafsirSpeechText("(রহ.)", "bn"))
    }
    @Test fun honorificsInsideLargerParenthesesAreExpandedWithoutRemovingTheirWords() {
        assertEquals("(হে নবী সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম!)",
            normalizeTafsirSpeechText("(হে নবী সঃ!)", "bn"))
        assertEquals("ইবনে কাসীর রহমাতুল্লাহি আলাইহি বলেছেন।", normalizeTafsirSpeechText("ইবনে কাসীর রঃ বলেছেন।", "bn"))
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
        assertTrue(text.contains("রাদিয়াল্লাহু আনহু"))
    }
    @Test fun alreadyWrittenFullHonorificsAndTheirPronounsArePreserved() {
        val text = "আয়েশা (রাদিয়াল্লাহু আনহা), সাহাবিগণ (রাদিয়াল্লাহু আনহুম), নবী (সাল্লাল্লাহু আলাইহি ওয়া সাল্লাম)।"
        assertEquals(text, normalizeTafsirSpeechText(text, "bn"))
    }

}
