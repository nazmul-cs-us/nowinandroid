/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.util

/** Renders a content number with the numeral system used by the selected reading language. */
fun Int.toLocalizedDigits(languageCode: String): String {
    val normalizedCode = languageCode.lowercase().substringBefore('-').substringBefore('_')
    val zero = when (normalizedCode) {
        "ar" -> '\u0660' // ٠ Arabic-Indic
        "ur", "fa" -> '\u06F0' // ۰ Extended Arabic-Indic
        "bn" -> '\u09E6' // ০ Bengali
        else -> return toString()
    }
    return toString().map { character ->
        if (character in '0'..'9') zero + (character - '0') else character
    }.joinToString("")
}
