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

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.os.Build
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
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.model.data.UserData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import com.starception.submission.R

/** App theme state plus the global widget appearance captured for this render. */
internal data class WidgetThemeSource(
    val repository: UserDataRepository,
    val initial: UserData,
    val appearance: WidgetAppearanceSettings,
)

/**
 * Loads both the selected app theme and the widget-specific global appearance.
 */
internal suspend fun loadWidgetThemeSource(context: Context): WidgetThemeSource {
    val repository = EntryPointAccessors.fromApplication(
        context.applicationContext,
        PrayerWidgetEntryPoint::class.java,
    ).userDataRepository()
    return WidgetThemeSource(
        repository = repository,
        initial = repository.userData.first(),
        appearance = WidgetAppearancePreferences.read(context),
    )
}

private val LocalWidgetGradient = staticCompositionLocalOf<ImageProvider> {
    error("Widget gradient was not provided")
}
private val LocalCookieWidgetGradient = staticCompositionLocalOf<ImageProvider> {
    error("Cookie widget gradient was not provided")
}
internal val LocalWidgetAppearance = staticCompositionLocalOf { WidgetAppearanceSettings() }
internal data class TransparentWidgetForeground(
    val primary: ColorProvider,
    val secondary: ColorProvider,
)
internal val LocalTransparentWidgetForeground = staticCompositionLocalOf {
    TransparentWidgetForeground(
        primary = ColorProvider(Color.White),
        secondary = ColorProvider(Color(0xFFE2E7F1)),
    )
}

/** Transparent Scaffold paint lets the shared gradient below remain visible. */
internal val TransparentWidgetBackground = ColorProvider(Color.Transparent)

/**
 * Widget equivalent of NiaTheme plus mainPageBackgroundBrush().
 *
 * Basic backgrounds use the selected app palette; Dynamic color uses the phone wallpaper
 * palette. Supplying the resolved scheme as both Glance variants is deliberate: the explicit
 * widget Light/Dark/Match phone selection must win over the launcher's resource choice.
 */
@Composable
internal fun StarceptionWidgetTheme(
    source: WidgetThemeSource,
    drawRectangularBackground: Boolean = true,
    basicBackgroundColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val userData by source.repository.userData.collectAsState(initial = source.initial)
    val context = androidx.glance.LocalContext.current
    // A widget process can retain the app's own day/night override in its Resources even
    // while the phone itself is in the opposite mode. UiModeManager is the system source
    // of truth and matches the mode the launcher uses for its widget surfaces.
    val systemDark = when (context.getSystemService(UiModeManager::class.java)?.nightMode) {
        UiModeManager.MODE_NIGHT_YES -> true
        UiModeManager.MODE_NIGHT_NO -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }
    val darkTheme = when (source.appearance.colorMode) {
        WidgetColorMode.FOLLOW_SYSTEM -> systemDark
        WidgetColorMode.LIGHT -> false
        WidgetColorMode.DARK -> true
    }
    val transparentForeground = TransparentWidgetForeground(
        primary = ColorProvider(Color(0xFFFFFFFF)),
        secondary = ColorProvider(Color(0xFFFFFFFF)),
    )
    val useWallpaperColors = source.appearance.backgroundType ==
        WidgetBackgroundType.DYNAMIC_COLOR
    val scheme = niaColorScheme(
        context = context,
        darkTheme = darkTheme,
        // Dynamic color means the phone wallpaper palette. Basic retains the app's
        // selected brand/custom colors while deliberately avoiding wallpaper colors.
        themeBrand = if (useWallpaperColors) ThemeBrand.DEFAULT else userData.themeBrand,
        customSeedColor = userData.customThemeColor.asThemeColor(),
        customSecondaryColor = userData.customSecondaryColor.asThemeColor(),
        customTertiaryColor = userData.customTertiaryColor.asThemeColor(),
        disableDynamicTheming = !useWallpaperColors,
    )
    val backgroundAlpha = if (source.appearance.showBackground) {
        1f - source.appearance.backgroundOpacity.coerceIn(0f, 1f)
    } else {
        0f
    }
    // Make nested themed panels obey the same global transparency. Text/icon roles stay
    // opaque so content remains legible as the wallpaper becomes more visible.
    val widgetScheme = scheme.copy(
        background = scheme.background.copy(alpha = backgroundAlpha),
        surface = scheme.surface.copy(alpha = backgroundAlpha),
        surfaceVariant = scheme.surfaceVariant.copy(alpha = backgroundAlpha),
        surfaceDim = scheme.surfaceDim.copy(alpha = backgroundAlpha),
        surfaceBright = scheme.surfaceBright.copy(alpha = backgroundAlpha),
        surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = backgroundAlpha),
        surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = backgroundAlpha),
        surfaceContainer = scheme.surfaceContainer.copy(alpha = backgroundAlpha),
        surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = backgroundAlpha),
        surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = backgroundAlpha),
    )
    // Material's dynamic ColorScheme can be resolved through the app's overridden theme
    // context. Widget backgrounds need the raw system palette used by the launcher, or a
    // dark phone can still receive a light card. The Android 12+ system tones are the
    // shared contract between first- and third-party widgets.
    val launcherAccentSurface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(
            context.getColor(
                if (darkTheme) {
                    android.R.color.system_accent3_700
                } else {
                    android.R.color.system_accent3_100
                },
            ),
        )
    } else {
        scheme.tertiaryContainer
    }
    val launcherNeutralSurface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(
            context.getColor(
                if (darkTheme) {
                    android.R.color.system_neutral2_800
                } else {
                    android.R.color.system_neutral2_50
                },
            ),
        )
    } else {
        scheme.surfaceContainerHigh
    }
    val dynamicGradientColors = if (darkTheme) {
        listOf(
            launcherAccentSurface,
            launcherAccentSurface.copy(alpha = 0.82f)
                .compositeOver(launcherNeutralSurface),
            launcherNeutralSurface,
        )
    } else {
        listOf(
            launcherAccentSurface,
            launcherAccentSurface.copy(alpha = 0.64f)
                .compositeOver(launcherNeutralSurface),
            launcherNeutralSurface,
        )
    }
    val basicColor = basicBackgroundColor ?: scheme.surface
    val backgroundColors = when (source.appearance.backgroundType) {
        WidgetBackgroundType.BASIC -> listOf(basicColor, basicColor)
        WidgetBackgroundType.DYNAMIC_COLOR -> dynamicGradientColors
    }.map { it.copy(alpha = backgroundAlpha) }
    val gradient = remember(userData, source.appearance, darkTheme, scheme, basicColor) {
        createHomeGradient(backgroundColors)
    }
    val cookieGradient = remember(userData, source.appearance, darkTheme, scheme, basicColor) {
        if (drawRectangularBackground) {
            gradient
        } else {
            createMaskedHomeGradient(
                context = context,
                colors = backgroundColors,
                maskRes = R.drawable.four_side_cookie_background,
            )
        }
    }

    GlanceTheme(colors = ColorProviders(light = widgetScheme, dark = widgetScheme)) {
        CompositionLocalProvider(
            LocalWidgetGradient provides ImageProvider(gradient),
            LocalCookieWidgetGradient provides ImageProvider(cookieGradient),
            LocalWidgetAppearance provides source.appearance,
            LocalTransparentWidgetForeground provides transparentForeground,
        ) {
            if (drawRectangularBackground) {
                // This must be a separate RemoteViews layer. Scaffold paints its own
                // background after processing its modifier, so putting the bitmap on
                // the same view is overwritten even when Scaffold's paint is transparent.
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .themedWidgetBackground()
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
    width: Int = 128,
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
                width.toFloat(),
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
