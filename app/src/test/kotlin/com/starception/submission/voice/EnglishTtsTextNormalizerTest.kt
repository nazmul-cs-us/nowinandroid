/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnglishTtsTextNormalizerTest {
    @Test
    fun expandsProphetHonorificLigature() {
        val result = EnglishTtsTextNormalizer.normalize("The Prophet ﷺ said this.")

        assertEquals("The Prophet sallallahu alayhi wa sallam said this.", result)
        assertFalse(result.contains('ﷺ'))
    }

    @Test
    fun expandsParenthesizedHonorificWithoutLeavingParentheses() {
        val result = EnglishTtsTextNormalizer.normalize("Muhammad (ﷺ) said this.")

        assertEquals("Muhammad sallallahu alayhi wa sallam said this.", result)
    }

    @Test
    fun expandsDottedAbbreviationsCaseInsensitively() {
        val result = EnglishTtsTextNormalizer.normalize(
            "Give charity, i.e., help the needy; E.G. food, clothes, etc.",
        )

        assertEquals(
            "Give charity, that is, help the needy; for example, food, clothes, and so on.",
            result,
        )
    }

    @Test
    fun doesNotExpandAbbreviationsInsideWords() {
        val result = EnglishTtsTextNormalizer.normalize("He saw that the field is pie.generalized.")

        assertEquals("He saw that the field is pie.generalized.", result)
    }

    @Test
    fun expandsCommonTextHonorificAbbreviations() {
        val result = EnglishTtsTextNormalizer.normalize("The Prophet (P.B.U.H.) and Muhammad S.A.W.")

        assertEquals(
            "The Prophet (peace be upon him) and Muhammad sallallahu alayhi wa sallam",
            result,
        )
    }

    @Test
    fun bukhariIntroSpellsSingleDigitHadithNumber() {
        assertEquals(
            "Hadith number one from Sahih Al-Bukhari.",
            EnglishTtsTextNormalizer.bukhariIntro(1),
        )
    }

    @Test
    fun bukhariIntroSpellsMultiDigitHadithNumber() {
        assertEquals(
            "Hadith number seven thousand five hundred sixty three from Sahih Al-Bukhari.",
            EnglishTtsTextNormalizer.bukhariIntro(7_563),
        )
    }
}
