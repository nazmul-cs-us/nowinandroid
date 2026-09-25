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

package com.starception.submission.widget

import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.ContentScale
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.starception.submission.R
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.designsystem.theme.niaColorScheme
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.model.data.UserData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

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

private val LocalCookieWidgetGradient = staticCompositionLocalOf<ImageProvider> {
    error("Cookie widget gradient was not provided")
}
internal val LocalWidgetAppearance = staticCompositionLocalOf { WidgetAppearanceSettings() }

/**
 * The resolved day/night state for this render, after applying the widget's own colour-mode
 * override on top of the system mode. Header ink that sits directly on the plate reads this
 * to stay legible when the plate turns dark.
 */
internal val LocalWidgetDarkTheme = staticCompositionLocalOf { false }
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
internal val LocalWidgetHeroAccent = staticCompositionLocalOf {
    ColorProvider(Color(0xFFD7E9D2))
}

internal val TransparentWidgetBackground = ColorProvider(Color.Transparent)

/**
 * Paint for the one view marked as android.R.id.background — the widget's plate.
 *
 * A plain translucent colour rather than a bitmap: it carries the global opacity directly,
 * and a disabled widget background stays fully transparent.
 */
internal val LocalWidgetHostBackground = staticCompositionLocalOf {
    TransparentWidgetBackground
}

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
        else ->
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
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
    val backgroundAlpha = source.appearance.effectiveBackgroundAlpha()
    val transparentBackground = source.appearance.needsWallpaperContrast()
    val readableOnWallpaper = Color.White
    val readableOnWallpaperMuted = Color(0xFFE2E7F1)
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
        primaryContainer = scheme.primaryContainer.copy(alpha = backgroundAlpha),
        secondaryContainer = scheme.secondaryContainer.copy(alpha = backgroundAlpha),
        tertiaryContainer = scheme.tertiaryContainer.copy(alpha = backgroundAlpha),
        inverseSurface = scheme.inverseSurface.copy(alpha = backgroundAlpha),
        onBackground = if (transparentBackground) readableOnWallpaper else scheme.onBackground,
        onSurface = if (transparentBackground) readableOnWallpaper else scheme.onSurface,
        onSurfaceVariant = if (transparentBackground) {
            readableOnWallpaperMuted
        } else {
            scheme.onSurfaceVariant
        },
        onPrimaryContainer = if (transparentBackground) {
            readableOnWallpaper
        } else {
            scheme.onPrimaryContainer
        },
        onSecondaryContainer = if (transparentBackground) {
            readableOnWallpaper
        } else {
            scheme.onSecondaryContainer
        },
        onTertiaryContainer = if (transparentBackground) {
            readableOnWallpaper
        } else {
            scheme.onTertiaryContainer
        },
        primary = if (transparentBackground) readableOnWallpaper else scheme.primary,
        secondary = if (transparentBackground) readableOnWallpaperMuted else scheme.secondary,
        tertiary = if (transparentBackground) readableOnWallpaperMuted else scheme.tertiary,
    )
    // The hero uses the selected primary hue on a fixed forest photograph. Some custom
    // themes resolve to a dark or muted primary that disappears on that surface, so lift
    // only as far toward white as WCAG contrast requires while retaining the chosen hue.
    val heroAccent = readableHeroAccent(scheme.primary)
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
    val basicGradientColors = basicBackgroundColor?.let { listOf(it, it) }
        ?: basicPlateColors(context, darkTheme)
    val backgroundColors = when (source.appearance.backgroundType) {
        WidgetBackgroundType.BASIC -> basicGradientColors
        WidgetBackgroundType.DYNAMIC_COLOR -> dynamicGradientColors
    }
    val useSamsungGradientDirection = source.appearance.backgroundType ==
        WidgetBackgroundType.BASIC && basicBackgroundColor == null
    val gradient = remember(
        userData,
        source.appearance,
        darkTheme,
        scheme,
        backgroundColors,
        useSamsungGradientDirection,
    ) {
        createHomeGradient(
            colors = backgroundColors,
            horizontal = useSamsungGradientDirection,
        )
    }
    val cookieGradient = remember(
        userData,
        source.appearance,
        darkTheme,
        scheme,
        backgroundColors,
        useSamsungGradientDirection,
    ) {
        if (drawRectangularBackground) {
            gradient
        } else {
            createMaskedHomeGradient(
                context = context,
                colors = backgroundColors,
                maskRes = R.drawable.four_side_cookie_background,
                horizontal = useSamsungGradientDirection,
            )
        }
    }

    GlanceTheme(colors = ColorProviders(light = widgetScheme, dark = widgetScheme)) {
        CompositionLocalProvider(
            LocalCookieWidgetGradient provides ImageProvider(cookieGradient),
            LocalWidgetAppearance provides source.appearance,
            LocalWidgetDarkTheme provides darkTheme,
            LocalTransparentWidgetForeground provides transparentForeground,
            LocalWidgetHeroAccent provides ColorProvider(heroAccent),
            // The plate is the colour on the @android:id/background view itself, not a
            // bitmap layered behind it, so the slider's alpha is the view's alpha and the
            // wallpaper shows through exactly as much as the setting says.
            LocalWidgetHostBackground provides if (source.appearance.showBackground) {
                ColorProvider(backgroundColors.first().copy(alpha = backgroundAlpha))
            } else {
                TransparentWidgetBackground
            },
        ) {
            content()
        }
    }
}

/** Keeps the expressive toolbar's silhouette while filling it with the shared gradient. */
@Composable
internal fun GlanceModifier.themedCookieWidgetBackground(): GlanceModifier = background(
    imageProvider = LocalCookieWidgetGradient.current,
    alpha = LocalWidgetAppearance.current.effectiveBackgroundAlpha(),
    contentScale = ContentScale.FillBounds,
)

/** Applies a global widget-opacity value to drawable artwork before it enters RemoteViews. */
internal fun alphaAdjustedImageProvider(
    context: Context,
    @DrawableRes drawableRes: Int,
    alpha: Float,
    targetAspect: Float? = null,
    preservedBand: ClosedFloatingPointRange<Float> = 0.12f..0.88f,
): ImageProvider {
    // Decoding a 1536x512 PNG and re-slicing it on every composition was a visible share
    // of the resize latency; the handful of (artwork, alpha, aspect) variants in use fit
    // comfortably in memory.
    val key = "$drawableRes:$alpha:${targetAspect?.let { "%.3f".format(it) }}:$preservedBand"
    artworkCache.get(key)?.let { return ImageProvider(it) }
    return ImageProvider(
        renderAdjustedArtwork(context, drawableRes, alpha, targetAspect, preservedBand)
            .also { artworkCache.put(key, it) },
    )
}

private val artworkCache = android.util.LruCache<String, Bitmap>(8)

private fun renderAdjustedArtwork(
    context: Context,
    @DrawableRes drawableRes: Int,
    alpha: Float,
    targetAspect: Float?,
    preservedBand: ClosedFloatingPointRange<Float>,
): Bitmap {
    val drawable = context.getDrawable(drawableRes)?.mutate()
        ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val intrinsicWidth = drawable.intrinsicWidth.coerceAtLeast(1)
    val intrinsicHeight = drawable.intrinsicHeight.coerceAtLeast(1)
    // Bitmap ImageProviders count against Android's per-widget RemoteViews memory cap.
    // 1024px is still above the rendered width on current phones while avoiding a failed
    // update when several reference panels and rasterized text rows share one widget.
    val scale = minOf(1f, MAX_REMOTE_ARTWORK_WIDTH.toFloat() / intrinsicWidth)
    val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
    val naturalHeight = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
    val sourceAspect = intrinsicWidth.toFloat() / intrinsicHeight
    drawable.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()

    // The host stretches a FillBounds background to whatever box it gets. When the box is
    // taller than the artwork, pre-render at the box's aspect instead: the band holding the
    // subject keeps its proportions and only the plain top and bottom margins (sky, foliage,
    // gradient) take up the extra height. Wider boxes still go through FillBounds, whose
    // horizontal squeeze at these ratios is not noticeable.
    if (targetAspect == null || targetAspect >= sourceAspect) {
        val bitmap = Bitmap.createBitmap(width, naturalHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, naturalHeight)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
    val height = (width / targetAspect).toInt().coerceAtLeast(naturalHeight)
    val source = Bitmap.createBitmap(width, naturalHeight, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, width, naturalHeight)
    drawable.draw(Canvas(source))
    val bandTop = (naturalHeight * preservedBand.start).toInt()
    val bandBottom = (naturalHeight * preservedBand.endInclusive).toInt()
    val bandHeight = bandBottom - bandTop
    val extra = height - naturalHeight
    // Split the surplus between the two margins in proportion to their source heights.
    val topSource = bandTop
    val bottomSource = naturalHeight - bandBottom
    val topTarget = topSource + (extra * topSource.toFloat() / (topSource + bottomSource)).toInt()
    val bottomTarget = height - topTarget - bandHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        source,
        Rect(0, 0, width, bandTop),
        Rect(0, 0, width, topTarget),
        paint,
    )
    canvas.drawBitmap(
        source,
        Rect(0, bandTop, width, bandBottom),
        Rect(0, topTarget, width, topTarget + bandHeight),
        paint,
    )
    canvas.drawBitmap(
        source,
        Rect(0, bandBottom, width, naturalHeight),
        Rect(0, topTarget + bandHeight, width, height),
        paint,
    )
    source.recycle()
    return bitmap
}

private const val MAX_REMOTE_ARTWORK_WIDTH = 1024

/**
 * Samsung Now Brief's Basic plate is a colourless sheet — white in light mode, near-black in
 * dark mode — laid over a blur of the wallpaper it covers, so its colour is really the
 * wallpaper's colour muted toward white or black. Now Brief can read the wallpaper bitmap
 * because it is a privileged system app; we cannot on Android 13+, so the nearest honest
 * substitute is the wallpaper's dominant colour from [WallpaperManager.getWallpaperColors],
 * which is open to every app. Folding a little of it into the sheet keeps the plate reading
 * as part of the wallpaper rather than a white card resting on top.
 */
internal fun basicPlateColors(context: Context, darkTheme: Boolean): List<Color> {
    val sheet = if (darkTheme) Color(0xFF1C1C1E) else Color.White
    val tint = wallpaperDominantColor(context) ?: return listOf(sheet, sheet)
    val plate = tint.copy(alpha = WALLPAPER_TINT_IN_PLATE).compositeOver(sheet)
    return listOf(plate, plate)
}

// Just enough for the sheet to take on the wallpaper's cast; the design's plate is close to
// white, so the tint stays a hint rather than a colour of its own.
private const val WALLPAPER_TINT_IN_PLATE = 0.10f

private fun wallpaperDominantColor(context: Context): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
    val colors = runCatching {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    }.getOrNull() ?: return null
    return Color(colors.primaryColor.toArgb())
}

private fun Int.asThemeColor(): Color = if (this == 0) Color.Unspecified else Color(this)

private val HERO_CONTRAST_SURFACE = Color(0xFF0B4D43)

private fun readableHeroAccent(source: Color): Color {
    val opaque = source.copy(alpha = 1f)
    if (contrastRatio(opaque, HERO_CONTRAST_SURFACE) >= 4.5f) return opaque
    for (step in 1..10) {
        val candidate = lerp(opaque, Color.White, step / 10f)
        if (contrastRatio(candidate, HERO_CONTRAST_SURFACE) >= 4.5f) return candidate
    }
    return Color.White
}

private fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** RemoteViews cannot carry a Compose Brush, so render the identical stops once to a tiny strip. */
private fun createHomeGradient(
    colors: List<Color>,
    width: Int = 128,
    height: Int = 128,
    horizontal: Boolean = false,
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
                if (horizontal) 0f else height.toFloat(),
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
    horizontal: Boolean = false,
): Bitmap {
    val size = 256
    val output = createHomeGradient(
        colors = colors,
        width = size,
        height = size,
        horizontal = horizontal,
    )
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
