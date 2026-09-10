/*
 * Copyright 2026 Starception
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

package com.starception.submission.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width

/** Raster equivalent of the app's rounded five-stop location divider. */
private fun fadingSeparatorBitmap(primary: Color, vertical: Boolean): Bitmap {
    val width = if (vertical) 3 else 96
    val height = if (vertical) 96 else 3
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            if (vertical) 0f else width.toFloat(),
            if (vertical) height.toFloat() else 0f,
            intArrayOf(
                primary.copy(alpha = 0f).toArgb(),
                primary.copy(alpha = 0.06f).toArgb(),
                primary.copy(alpha = 0.30f).toArgb(),
                primary.copy(alpha = 0.06f).toArgb(),
                primary.copy(alpha = 0f).toArgb(),
            ),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    Canvas(bitmap).drawRoundRect(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        width / 2f,
        width / 2f,
        paint,
    )
    return bitmap
}

/** The app's location divider, shared by widget header information groups. */
@Composable
internal fun FadingVerticalSeparator(height: Dp = 32.dp) {
    Image(
        provider = ImageProvider(
            fadingSeparatorBitmap(
                GlanceTheme.colors.primary.getColor(LocalContext.current),
                vertical = true,
            ),
        ),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.width(1.dp).height(height),
    )
}

/** The same location divider turned horizontally for sections and list rows. */
@Composable
internal fun FadingHorizontalSeparator() {
    Image(
        provider = ImageProvider(
            fadingSeparatorBitmap(
                GlanceTheme.colors.primary.getColor(LocalContext.current),
                vertical = false,
            ),
        ),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(1.dp),
    )
}
