/*
 * Copyright 2024 Starception
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

/**
 * Converts display-oriented English text into text that speech engines can read naturally.
 *
 * Hadith translations commonly contain Unicode honorific ligatures and dotted abbreviations.
 * TTS models may spell those symbols out, skip them, or treat the dots as sentence endings.
 */
object EnglishTtsTextNormalizer {
    private val units = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )
    private val tens = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    private val replacements = listOf(
        Regex("\\(\\s*ﷺ\\s*\\)") to " sallallahu alayhi wa sallam ",
        Regex("[ﷺؐ]") to " sallallahu alayhi wa sallam ",
        Regex("\\(\\s*ﷻ\\s*\\)") to " jalla jalaluhu ",
        Regex("ﷻ") to " jalla jalaluhu ",
        Regex("ؑ") to " alayhis salam ",
        Regex("ؒ") to " rahmatullahi alayh ",
        Regex("ؓ") to " radiyallahu anhu ",
        Regex("(?i)(?<![\\p{L}\\p{N}])P\\s*\\.?\\s*B\\s*\\.?\\s*U\\s*\\.?\\s*H\\s*\\.?(?![\\p{L}\\p{N}])") to
            " peace be upon him ",
        Regex("(?<![\\p{L}\\p{N}])SAW(?![\\p{L}\\p{N}])") to
            " sallallahu alayhi wa sallam ",
        Regex("(?i)(?<![\\p{L}\\p{N}])S\\s*\\.\\s*A\\s*\\.\\s*W\\s*\\.?(?![\\p{L}\\p{N}])") to
            " sallallahu alayhi wa sallam ",
        Regex("(?i)(?<![\\p{L}\\p{N}])i\\s*\\.\\s*e\\s*\\.,?(?![\\p{L}\\p{N}])") to " that is, ",
        Regex("(?i)(?<![\\p{L}\\p{N}])e\\s*\\.\\s*g\\s*\\.,?(?![\\p{L}\\p{N}])") to " for example, ",
        Regex("(?i)(?<![\\p{L}\\p{N}])etc\\s*\\.(?![\\p{L}\\p{N}])") to " and so on. ",
    )

    fun normalize(text: String): String {
        val expanded = replacements.fold(text) { result, (pattern, replacement) ->
            result.replace(pattern, replacement)
        }

        return expanded
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *([,;:!?]) *"), "$1 ")
            .replace(Regex("\\s+\\."), ".")
            .replace(Regex("\\(\\s+"), "(")
            .replace(Regex("\\s+\\)"), ")")
            .replace(Regex("\\s*\\n\\s*"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    /**
     * Sherpa's English phonemizers can silently skip decimal digit tokens. Bukhari numbers
     * are therefore written as words before normalization and synthesis.
     */
    fun bukhariIntro(hadithNumber: Int): String =
        "Hadith number ${integerToEnglishWords(hadithNumber)} from Sahih Al-Bukhari."

    internal fun integerToEnglishWords(number: Int): String {
        require(number >= 0) { "Only non-negative numbers are supported" }
        if (number < 20) return units[number]
        if (number < 100) {
            val remainder = number % 10
            return tens[number / 10] + if (remainder == 0) "" else " ${units[remainder]}"
        }
        if (number < 1_000) {
            val remainder = number % 100
            return "${units[number / 100]} hundred" +
                if (remainder == 0) "" else " ${integerToEnglishWords(remainder)}"
        }
        if (number < 1_000_000) {
            val remainder = number % 1_000
            return "${integerToEnglishWords(number / 1_000)} thousand" +
                if (remainder == 0) "" else " ${integerToEnglishWords(remainder)}"
        }
        val remainder = number % 1_000_000
        return "${integerToEnglishWords(number / 1_000_000)} million" +
            if (remainder == 0) "" else " ${integerToEnglishWords(remainder)}"
    }
}
