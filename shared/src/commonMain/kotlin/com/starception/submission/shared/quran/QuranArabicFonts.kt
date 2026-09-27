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

package com.starception.submission.shared.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.amiri_quran
import com.starception.submission.core.images.resources.indopak_quran
import com.starception.submission.core.images.resources.noor_hidayat_quran
import com.starception.submission.core.images.resources.pdms_saleem_quran
import org.jetbrains.compose.resources.Font

/**
 * The Arabic Quran fonts the Android app offers (QuranFonts.kt), shared through
 * Compose Multiplatform resources so the iOS host renders the same typefaces.
 * Compose Multiplatform's Font() reads the resource table inside composition,
 * mirroring core:theme's SharedTypography pattern.
 */
object QuranArabicFonts {
    const val PDMS_SALEEM = "pdms_saleem"
    const val INDOPAK = "indopak_script"
    const val UTHMANI = "uthmani_script"
    const val NOOR_E_HIDAYAT = "noor_e_hidayat"

    val selectionOrder = listOf(PDMS_SALEEM, INDOPAK, UTHMANI, NOOR_E_HIDAYAT)

    fun displayName(selectedFont: String): String = when (selectedFont) {
        PDMS_SALEEM -> "PDMS Saleem"
        INDOPAK -> "Indo-Pak"
        UTHMANI -> "Uthmani"
        NOOR_E_HIDAYAT -> "Noor-e-Hidayat"
        else -> "PDMS Saleem"
    }

    @Composable
    fun fontFamily(selectedFont: String): FontFamily = when (selectedFont) {
        INDOPAK -> FontFamily(Font(Res.font.indopak_quran))
        UTHMANI -> FontFamily(Font(Res.font.amiri_quran))
        NOOR_E_HIDAYAT -> FontFamily(Font(Res.font.noor_hidayat_quran))
        else -> FontFamily(Font(Res.font.pdms_saleem_quran))
    }
}

/**
 * Inline ayah-number chip — the shared counterpart of Android's
 * AyahMarkerRosette. Arabic-Indic digits sit in a circular chip that
 * reads as a stamp on the calligraphy; Android's 8-petal mushaf
 * ornament needs its vector drawable, this chip preserves the pattern.
 */
@Composable
fun AyahNumberChip(
    ayahNumber: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
) {
    Box(
        modifier = modifier.size((fontSize.value * 1.9f).dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((fontSize.value * 1.6f).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        )
        Text(
            text = ayahNumber.toString().map { digit ->
                if (digit in '0'..'9') ('٠'.code + (digit - '0')).toChar() else digit
            }.joinToString(""),
            fontSize = (fontSize.value * 0.78f).sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}
