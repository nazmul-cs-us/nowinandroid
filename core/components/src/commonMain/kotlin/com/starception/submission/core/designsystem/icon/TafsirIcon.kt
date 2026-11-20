package com.starception.submission.core.designsystem.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.flaticon_quran_5452447
import org.jetbrains.compose.resources.painterResource
import com.starception.submission.core.images.resources.Res as ImageRes

/** The original, unmodified Quran PNG by smashingstocks / Flaticon, selected for Tafsir. */
@Composable
fun TafsirIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(ImageRes.drawable.flaticon_quran_5452447),
        contentDescription = contentDescription,
        modifier = modifier.size(24.dp),
        // Tint only the rendered silhouette; retain the original PNG and transparency.
        colorFilter = ColorFilter.tint(LocalContentColor.current),
    )
}
