package com.starception.submission.feature.surah

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.starception.submission.R
import com.starception.submission.core.designsystem.theme.ubuntuInspiredFontFamily

/**
 * Inline ayah-marker rosette: an 8-petal Madinah-mushaf flower drawn from the
 * Wikimedia public-domain Scheherazade U+06DD glyph (baked as a vector
 * drawable so rendering doesn't depend on font-engine OpenType support).
 * Arabic-indic ayah digits overlay the rosette in a contrasting "cut-out"
 * color (typically the page surface) so they read as a stamp on the ornament.
 *
 * Sized in `em` via the parent [androidx.compose.ui.text.Placeholder] so the
 * marker scales with the surrounding calligraphy.
 */
@Composable
fun AyahMarkerRosette(
    ayahNumber: Int,
    color: Color,
    digitColor: Color,
    arabicFontFamily: FontFamily,
    digitFontSize: TextUnit = 0.78.em,
) {
    val arabicDigits = remember(ayahNumber) { ayahNumber.toArabicIndicDigits() }
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Opaque disc behind the rosette flower stamps out anything underneath
        // — the SVG path has a transparent interior, so without this, adjacent
        // Arabic letter bearings (sub-baseline tails of ن, ر, ي, etc.) show
        // through the rosette and read as visual overlap.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(CircleShape)
                .background(surface),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_ayah_marker_rosette),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 1.dp),
        )
        Text(
            text = arabicDigits,
            color = digitColor,
            fontFamily = ubuntuInspiredFontFamily,
            fontSize = digitFontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = digitFontSize,
        )
    }
}

private fun Int.toArabicIndicDigits(): String = this.toString().map { c ->
    when (c) {
        '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
        '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
        else -> c
    }
}.joinToString("")
