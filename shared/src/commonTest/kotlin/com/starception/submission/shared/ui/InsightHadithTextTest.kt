package com.starception.submission.shared.ui

import com.starception.submission.core.designsystem.component.formatInsightHadithText
import kotlin.test.Test
import kotlin.test.assertEquals

class InsightHadithTextTest {
    @Test
    fun removesHardWrappingAndRepeatedBlankLinesWithoutChangingWords() {
        assertEquals("The Prophet said: Pray on time.", formatInsightHadithText(" \nThe Prophet\r\n\r\nsaid:\n\n\n Pray\t on time.\n"))
    }

    @Test
    fun keepsNarratorSeparateFromOneContinuousBodyParagraph() {
        assertEquals("Narrated Abu Huraira:\n\nThe Prophet said: Pray on time.", formatInsightHadithText("Narrated Abu Huraira:\n\n\nThe Prophet said:\n\nPray on time."))
    }

    @Test
    fun preservesArabicAndQuotationMarks() {
        assertEquals("قال: «الصلاة نور»", formatInsightHadithText("قال:\n\n«الصلاة\u00a0نور»"))
    }

    @Test
    fun formattingDoesNotAccumulateExtraParagraphs() {
        val text = "Narrated Ibn Umar:\n\nPrayer is a light."
        assertEquals(text, formatInsightHadithText(formatInsightHadithText(text)))
    }
}
