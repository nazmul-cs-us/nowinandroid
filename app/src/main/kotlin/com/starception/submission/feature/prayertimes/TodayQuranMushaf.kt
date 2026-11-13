package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.feature.surah.MushafPagerView
import com.starception.submission.feature.surah.tajweed.TajweedAnnotation

/** Home embeds the real Surah Mushaf, rather than approximating it with a TextView. */
@Composable
internal fun TodayQuranMushaf(
    ayahs: List<Ayah>,
    translations: Map<Int, String>,
    arabicFont: String,
    arabicFontSize: Float,
    showTranslation: Boolean,
    showTajweed: Boolean,
    tajweedAnnotations: Map<Int, List<TajweedAnnotation>>,
    textAlignment: String,
    translationCode: String,
    onOpenSurah: () -> Unit,
    modifier: Modifier = Modifier,
    onPinchZoom: ((Float) -> Unit)? = null,
) {
    if (ayahs.isEmpty()) return
    key(ayahs.first().surahNumber) {
        val readerAyahs = remember(ayahs, translations) {
            ayahs.map { ayah ->
                val translation = translations[ayah.numberInSurah]?.takeIf(String::isNotBlank)
                if (translation == null) ayah else ayah.copy(text = ayah.text + "\n\n" + translation)
            }
        }
        var page by remember { mutableIntStateOf(0) }
        var pageCount by remember { mutableIntStateOf(1) }
        Column(modifier.fillMaxSize().testTag("todayQuranMushaf")) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                MushafPagerView(
                    ayahs = readerAyahs,
                    arabicFont = arabicFont,
                    arabicFontSize = arabicFontSize,
                    showTranslation = showTranslation,
                    showTajweed = showTajweed,
                    tajweedAnnotations = tajweedAnnotations,
                    showBismillah = ayahs.first().surahNumber !in listOf(1, 9),
                    textAlignment = textAlignment,
                    translationCode = translationCode,
                    onAyahLongPress = { onOpenSurah() },
                    onPageChange = { current, total -> page = current; pageCount = total.coerceAtLeast(1) },
                    embedded = true,
                    showEdgeControls = false,
                    requestedPage = page,
                    onPinchZoom = onPinchZoom,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                )
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).offset(y = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { page-- }, enabled = page > 0,
                    modifier = Modifier.semantics { contentDescription = "Previous Mushaf page" }) {
                    Text("Previous", color = Color.White.copy(alpha = if (page > 0) 1f else .38f))
                }
                Text("Page ${page + 1} of $pageCount", modifier = Modifier.testTag("todayQuranMushafPageNumber"),
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center)
                TextButton(onClick = { page++ }, enabled = page < pageCount - 1,
                    modifier = Modifier.semantics { contentDescription = "Next Mushaf page" }) {
                    Text("Next", color = Color.White.copy(alpha = if (page < pageCount - 1) 1f else .38f))
                }
            }
        }
    }
}
