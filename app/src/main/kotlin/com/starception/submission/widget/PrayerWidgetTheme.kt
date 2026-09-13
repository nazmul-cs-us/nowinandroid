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

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.compose.ui.unit.dp
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.designsystem.theme.niaColorScheme
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.UserData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import com.starception.submission.R

/** Repository plus first value used before the DataStore flow produces its first frame. */
internal data class WidgetThemeSource(
    val repository: UserDataRepository,
    val initial: UserData,
)

/**
 * Loads the selected app theme for a widget without maintaining a second preference store.
 */
internal suspend fun loadWidgetThemeSource(context: Context): WidgetThemeSource {
    val repository = EntryPointAccessors.fromApplication(
        context.applicationContext,
        PrayerWidgetEntryPoint::class.java,
    ).userDataRepository()
    return WidgetThemeSource(repository, repository.userData.first())
}

private val LocalWidgetGradient = staticCompositionLocalOf<ImageProvider> {
    error("Widget gradient was not provided")
}
private val LocalCookieWidgetGradient = staticCompositionLocalOf<ImageProvider> {
    error("Cookie widget gradient was not provided")
}

/** Transparent Scaffold paint lets the shared gradient below remain visible. */
internal val TransparentWidgetBackground = ColorProvider(Color.Transparent)

/**
 * Widget equivalent of NiaTheme plus mainPageBackgroundBrush().
 *
 * The same theme resolver is shared with the app, including custom and Material You colors.
 * Supplying the resolved scheme as both Glance variants is deliberate: the in-app Light/Dark
 * selection must win over the launcher's system-night resource choice.
 */
@Composable
internal fun StarceptionWidgetTheme(
    source: WidgetThemeSource,
    drawRectangularBackground: Boolean = true,
    backgroundColor: ColorProvider? = null,
    content: @Composable () -> Unit,
) {
    val userData by source.repository.userData.collectAsState(initial = source.initial)
    val context = androidx.glance.LocalContext.current
    val followsSystemDark =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    val darkTheme = when (userData.darkThemeConfig) {
        DarkThemeConfig.FOLLOW_SYSTEM -> followsSystemDark
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
    }
    val scheme = niaColorScheme(
        context = context,
        darkTheme = darkTheme,
        themeBrand = userData.themeBrand,
        customSeedColor = userData.customThemeColor.asThemeColor(),
        customSecondaryColor = userData.customSecondaryColor.asThemeColor(),
        customTertiaryColor = userData.customTertiaryColor.asThemeColor(),
        disableDynamicTheming = !userData.useDynamicColor,
    )
    val gradientColors = if (darkTheme) {
        listOf(
            scheme.background,
            scheme.surface,
            scheme.primary.copy(alpha = 0.05f).compositeOver(scheme.surface),
            scheme.background,
        )
    } else {
        listOf(
            scheme.background,
            scheme.surfaceContainerLow,
            scheme.secondary.copy(alpha = 0.14f).compositeOver(scheme.surfaceContainerLow),
        )
    }
    val gradient = remember(userData, darkTheme, scheme) {
        createHomeGradient(gradientColors)
    }
    val cookieGradient = remember(userData, darkTheme, scheme) {
        if (drawRectangularBackground) {
            gradient
        } else {
            createMaskedHomeGradient(
                context = context,
                colors = gradientColors,
                maskRes = R.drawable.four_side_cookie_background,
            )
        }
    }

    GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
        CompositionLocalProvider(
            LocalWidgetGradient provides ImageProvider(gradient),
            LocalCookieWidgetGradient provides ImageProvider(cookieGradient),
        ) {
            if (drawRectangularBackground) {
                // This must be a separate RemoteViews layer. Scaffold paints its own
                // background after processing its modifier, so putting the bitmap on
                // the same view is overwritten even when Scaffold's paint is transparent.
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .then(
                            backgroundColor?.let { color -> GlanceModifier.background(color) }
                                ?: GlanceModifier.themedWidgetBackground(),
                        )
                        .cornerRadius(24.dp),
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}

/** Applies the selected-theme home gradient as a stretched, low-cost bitmap background. */
@Composable
internal fun GlanceModifier.themedWidgetBackground(): GlanceModifier = background(
    imageProvider = LocalWidgetGradient.current,
    contentScale = ContentScale.FillBounds,
)

/** Keeps the expressive toolbar's silhouette while filling it with the shared gradient. */
@Composable
internal fun GlanceModifier.themedCookieWidgetBackground(): GlanceModifier = background(
    imageProvider = LocalCookieWidgetGradient.current,
    contentScale = ContentScale.FillBounds,
)

private fun Int.asThemeColor(): Color = if (this == 0) Color.Unspecified else Color(this)

/** RemoteViews cannot carry a Compose Brush, so render the identical stops once to a tiny strip. */
private fun createHomeGradient(
    colors: List<Color>,
    width: Int = 8,
    height: Int = 128,
): Bitmap {
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val stops = colors.indices.map { index ->
            if (colors.lastIndex == 0) 0f else index.toFloat() / colors.lastIndex
        }.toFloatArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                colors.map(Color::toArgb).toIntArray(),
                stops,
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

private fun createMaskedHomeGradient(
    context: Context,
    colors: List<Color>,
    @DrawableRes maskRes: Int,
): Bitmap {
    val size = 256
    val output = createHomeGradient(colors, width = size, height = size)
    val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    context.getDrawable(maskRes)?.mutate()?.apply {
        setBounds(0, 0, size, size)
        draw(Canvas(mask))
    }
    Canvas(output).drawBitmap(
        mask,
        0f,
        0f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        },
    )
    return output
}
