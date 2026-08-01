/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.core.designsystem.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.starception.submission.core.model.data.ThemeBrand

/**
 * CompositionLocal to provide dark theme state to child composables.
 * This reflects the actual app theme setting (not just system setting).
 */
val LocalDarkTheme = compositionLocalOf { false }

/**
 * Light default theme color scheme
 */
@VisibleForTesting
val LightDefaultColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple10,
    secondary = Orange40,
    onSecondary = Color.White,
    secondaryContainer = Orange90,
    onSecondaryContainer = Orange10,
    tertiary = Blue40,
    onTertiary = Color.White,
    tertiaryContainer = Blue90,
    onTertiaryContainer = Blue10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = DarkPurpleGray99,
    onBackground = DarkPurpleGray10,
    surface = DarkPurpleGray99,
    onSurface = DarkPurpleGray10,
    surfaceVariant = PurpleGray90,
    onSurfaceVariant = PurpleGray30,
    inverseSurface = DarkPurpleGray20,
    inverseOnSurface = DarkPurpleGray95,
    outline = PurpleGray50,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAF8),
    surfaceContainer = Color(0xFFF4F4F2),
    surfaceContainerHigh = Color(0xFFEEEEEC),
    surfaceContainerHighest = Color(0xFFE8E8E6),
)

/**
 * Dark default theme color scheme
 */
@VisibleForTesting
val DarkDefaultColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Purple20,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,
    secondary = Orange80,
    onSecondary = Orange20,
    secondaryContainer = Orange30,
    onSecondaryContainer = Orange90,
    tertiary = Blue80,
    onTertiary = Blue20,
    tertiaryContainer = Blue30,
    onTertiaryContainer = Blue90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Color(0xFF120E10),
    onBackground = DarkPurpleGray90,
    surface = Color(0xFF171214),
    onSurface = DarkPurpleGray90,
    surfaceVariant = Color(0xFF2B2329),
    onSurfaceVariant = PurpleGray80,
    inverseSurface = DarkPurpleGray90,
    inverseOnSurface = DarkPurpleGray10,
    outline = PurpleGray60,
    outlineVariant = Color(0xFF443A41),
    surfaceContainerLowest = Color(0xFF0D0A0B),
    surfaceContainerLow = Color(0xFF1B1518),
    surfaceContainer = Color(0xFF211A1E),
    surfaceContainerHigh = Color(0xFF282026),
    surfaceContainerHighest = Color(0xFF30272D),
    scrim = Color.Black,
)

/**
 * Light Android theme color scheme
 */
@VisibleForTesting
val LightAndroidColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = DarkGreen40,
    onSecondary = Color.White,
    secondaryContainer = DarkGreen90,
    onSecondaryContainer = DarkGreen10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = DarkGreenGray99,
    onBackground = DarkGreenGray10,
    surface = DarkGreenGray99,
    onSurface = DarkGreenGray10,
    surfaceVariant = GreenGray90,
    onSurfaceVariant = GreenGray30,
    inverseSurface = DarkGreenGray20,
    inverseOnSurface = DarkGreenGray95,
    outline = GreenGray50,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAF6),
    surfaceContainer = Color(0xFFF2F4F0),
    surfaceContainerHigh = Color(0xFFECEFEA),
    surfaceContainerHighest = Color(0xFFE6E9E4),
)

/**
 * Dark Android theme color scheme
 */
@VisibleForTesting
val DarkAndroidColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = DarkGreen80,
    onSecondary = DarkGreen20,
    secondaryContainer = DarkGreen30,
    onSecondaryContainer = DarkGreen90,
    tertiary = Teal80,
    onTertiary = Teal20,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Color(0xFF0C100D),
    onBackground = DarkGreenGray90,
    surface = Color(0xFF111612),
    onSurface = DarkGreenGray90,
    surfaceVariant = Color(0xFF202A22),
    onSurfaceVariant = GreenGray80,
    inverseSurface = DarkGreenGray90,
    inverseOnSurface = DarkGreenGray10,
    outline = GreenGray60,
    outlineVariant = Color(0xFF354138),
    surfaceContainerLowest = Color(0xFF070B08),
    surfaceContainerLow = Color(0xFF151D17),
    surfaceContainer = Color(0xFF19231B),
    surfaceContainerHigh = Color(0xFF1F2921),
    surfaceContainerHighest = Color(0xFF273229),
    scrim = Color.Black,
)

/**
 * Light Elegant theme color scheme - sophisticated and peaceful for prayer times
 */
@VisibleForTesting
val LightCoastalColorScheme = lightColorScheme(
    primary = ForestGreen40,
    onPrimary = Color.White,
    primaryContainer = ForestGreen90,
    onPrimaryContainer = ForestGreen10,
    secondary = WarmGold40,
    onSecondary = Color.White,
    secondaryContainer = WarmGold90,
    onSecondaryContainer = WarmGold10,
    tertiary = SoftSage40,
    onTertiary = Color.White,
    tertiaryContainer = SoftSage90,
    onTertiaryContainer = SoftSage10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Color(0xFFFBFCF9),
    onBackground = ForestGreen10,
    surface = Color(0xFFF6F8F5),
    onSurface = ForestGreen10,
    surfaceVariant = Color(0xFFF0F2EF),
    onSurfaceVariant = SoftSage30,
    inverseSurface = ForestGreen20,
    inverseOnSurface = ForestGreen90,
    outline = SoftSage40,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAF6),
    surfaceContainer = Color(0xFFF2F4F0),
    surfaceContainerHigh = Color(0xFFECEFEA),
    surfaceContainerHighest = Color(0xFFE6E9E4),
)

/**
 * Dark Coastal — mirrors the light Coastal palette (Forest green + Soft sage),
 * adapted to dark mode tones. Was previously a Tesla-style red scheme; that
 * leaked an aggressive red into every primary slot in dark mode, which read as
 * "error / warning" everywhere instead of the calm coastal feel.
 */
@VisibleForTesting
val DarkCoastalColorScheme = darkColorScheme(
    primary = ForestGreen80,
    onPrimary = ForestGreen10,
    primaryContainer = ForestGreen30,
    onPrimaryContainer = ForestGreen90,
    secondary = SoftSage80,
    onSecondary = SoftSage10,
    secondaryContainer = SoftSage30,
    onSecondaryContainer = SoftSage90,
    tertiary = Teal80,
    onTertiary = Teal10,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    // A deep green-black canvas keeps the Coastal identity without crushing
    // cards and controls into one flat near-black layer.
    background = Color(0xFF0A0F0C),
    onBackground = Color(0xFFF0F5F0),
    surface = Color(0xFF0F1612),
    onSurface = Color(0xFFF0F5F0),
    surfaceVariant = Color(0xFF202A23),
    onSurfaceVariant = Color(0xFFC3CEC2),
    inverseSurface = Color(0xFFE7ECE7),
    inverseOnSurface = Color(0xFF111713),
    outline = Color(0xFF7A887C),
    outlineVariant = Color(0xFF354139),
    surfaceContainerLowest = Color(0xFF070B08),
    surfaceContainerLow = Color(0xFF151E18),
    surfaceContainer = Color(0xFF19231C),
    surfaceContainerHigh = Color(0xFF1E2921),
    surfaceContainerHighest = Color(0xFF263329),
    scrim = Color.Black,
)

/**
 * Light Royal theme — Lapis primary, Gold secondary, Sage tertiary, Garnet error.
 */
@VisibleForTesting
val LightRoyalColorScheme = lightColorScheme(
    primary = Lapis40,
    onPrimary = Color.White,
    primaryContainer = Lapis90,
    onPrimaryContainer = Lapis10,
    secondary = WarmGold40,
    onSecondary = Color.White,
    secondaryContainer = WarmGold90,
    onSecondaryContainer = WarmGold10,
    tertiary = SoftSage40,
    onTertiary = Color.White,
    tertiaryContainer = SoftSage90,
    onTertiaryContainer = SoftSage10,
    error = Garnet40,
    onError = Color.White,
    errorContainer = Garnet90,
    onErrorContainer = Garnet10,
    background = Color(0xFFFCFBFA),
    onBackground = Lapis10,
    surface = Color(0xFFF7F6F4),
    onSurface = Lapis10,
    surfaceVariant = Color(0xFFEFEDE9),
    onSurfaceVariant = Lapis30,
    inverseSurface = Lapis20,
    inverseOnSurface = Lapis90,
    outline = WarmGold40,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F8F6),
    surfaceContainer = Color(0xFFF3F2EF),
    surfaceContainerHigh = Color(0xFFEDEBE7),
    surfaceContainerHighest = Color(0xFFE7E5E0),
)

/**
 * Dark Royal theme.
 */
@VisibleForTesting
val DarkRoyalColorScheme = darkColorScheme(
    primary = Lapis80,
    onPrimary = Lapis10,
    primaryContainer = Lapis30,
    onPrimaryContainer = Lapis90,
    secondary = WarmGold80,
    onSecondary = WarmGold10,
    secondaryContainer = WarmGold30,
    onSecondaryContainer = WarmGold90,
    tertiary = SoftSage80,
    onTertiary = SoftSage10,
    tertiaryContainer = SoftSage30,
    onTertiaryContainer = SoftSage90,
    error = Garnet80,
    onError = Garnet20,
    errorContainer = Garnet30,
    onErrorContainer = Garnet90,
    background = Color(0xFF0D0F14),
    onBackground = Color(0xFFF2F4F0),
    surface = Color(0xFF14171E),
    onSurface = Color(0xFFF2F4F0),
    surfaceVariant = Color(0xFF1C2028),
    onSurfaceVariant = Lapis80,
    inverseSurface = Lapis90,
    inverseOnSurface = Lapis10,
    outline = WarmGold80,
    outlineVariant = Color(0xFF3A424F),
    surfaceContainerLowest = Color(0xFF080A0E),
    surfaceContainerLow = Color(0xFF151922),
    surfaceContainer = Color(0xFF1A1F29),
    surfaceContainerHigh = Color(0xFF202733),
    surfaceContainerHighest = Color(0xFF28313D),
    scrim = Color.Black,
)

/** Royal gradient + background — light */
val LightRoyalGradientColors = GradientColors(container = Color(0xFFF7F6F4))
val LightRoyalBackgroundTheme = BackgroundTheme(color = Color(0xFFFCFBFA))

/** Royal gradient + background — dark */
val DarkRoyalGradientColors = GradientColors(container = Color(0xFF0D0F14))
val DarkRoyalBackgroundTheme = BackgroundTheme(color = Color(0xFF0D0F14))

/**
 * Light Android gradient colors
 */
val LightAndroidGradientColors = GradientColors(container = DarkGreenGray95)

/**
 * Dark Android gradient colors
 */
val DarkAndroidGradientColors = GradientColors(container = Color(0xFF0C100D))

/**
 * Light Android background theme
 */
val LightAndroidBackgroundTheme = BackgroundTheme(color = DarkGreenGray95)

/**
 * Dark Android background theme
 */
val DarkAndroidBackgroundTheme = BackgroundTheme(color = Color(0xFF0C100D))

/**
 * Light Elegant gradient colors
 */
val LightCoastalGradientColors = GradientColors(container = Color(0xFFF8F9F7))

/**
 * Dark Elegant gradient colors
 */
val DarkCoastalGradientColors = GradientColors(container = Color(0xFF0A0F0C))

/**
 * Light Elegant background theme
 */
val LightCoastalBackgroundTheme = BackgroundTheme(color = Color(0xFFFBFCF9))

/**
 * Dark Elegant background theme
 */
val DarkCoastalBackgroundTheme = BackgroundTheme(color = Color(0xFF0A0F0C))

/**
 * Now in Android theme with support for multiple theme brands.
 *
 * @param darkTheme Whether the theme should use a dark color scheme (follows system by default).
 * @param themeBrand Which theme brand to use:
 *        - DEFAULT: Custom purple/orange theme
 *        - ANDROID: Green-based Android theme  
 *        - COASTAL: Ocean-inspired blue/seafoam theme
 * @param disableDynamicTheming If `true`, disables the use of dynamic theming, even when it is
 *        supported. This parameter has no effect if [themeBrand] is not DEFAULT.
 */
@Composable
fun NiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeBrand: ThemeBrand = ThemeBrand.COASTAL,
    customSeedColor: Color = Color.Unspecified,
    customSecondaryColor: Color = Color.Unspecified,
    customTertiaryColor: Color = Color.Unspecified,
    disableDynamicTheming: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Color scheme
    val colorScheme = when (themeBrand) {
        ThemeBrand.DEFAULT -> when {
            !disableDynamicTheming && supportsDynamicTheming() -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme
        }
        ThemeBrand.ANDROID -> if (darkTheme) DarkAndroidColorScheme else LightAndroidColorScheme
        ThemeBrand.COASTAL -> if (darkTheme) DarkCoastalColorScheme else LightCoastalColorScheme
        ThemeBrand.ROYAL -> if (darkTheme) DarkRoyalColorScheme else LightRoyalColorScheme
        ThemeBrand.CUSTOM -> {
            val primary = if (customSeedColor == Color.Unspecified) Color(0xFF6750A4) else customSeedColor
            val secondary = if (customSecondaryColor == Color.Unspecified) null else customSecondaryColor
            val tertiary = if (customTertiaryColor == Color.Unspecified) null else customTertiaryColor
            colorSchemeFromSeeds(primary, secondary, tertiary, darkTheme)
        }
    }
    // Gradient colors
    val emptyGradientColors = GradientColors(container = colorScheme.surfaceColorAtElevation(2.dp))
    val defaultGradientColors = GradientColors(
        top = colorScheme.inverseOnSurface,
        bottom = colorScheme.primaryContainer,
        container = colorScheme.surface,
    )
    val gradientColors = when (themeBrand) {
        ThemeBrand.DEFAULT -> when {
            !disableDynamicTheming && supportsDynamicTheming() -> emptyGradientColors
            else -> defaultGradientColors
        }
        ThemeBrand.ANDROID -> if (darkTheme) DarkAndroidGradientColors else LightAndroidGradientColors
        ThemeBrand.COASTAL -> if (darkTheme) DarkCoastalGradientColors else LightCoastalGradientColors
        ThemeBrand.ROYAL -> if (darkTheme) DarkRoyalGradientColors else LightRoyalGradientColors
        ThemeBrand.CUSTOM -> defaultGradientColors
    }
    // Background theme
    val defaultBackgroundTheme = BackgroundTheme(
        color = colorScheme.surface,
        tonalElevation = 2.dp,
    )
    val backgroundTheme = when (themeBrand) {
        ThemeBrand.DEFAULT -> defaultBackgroundTheme
        ThemeBrand.ANDROID -> if (darkTheme) DarkAndroidBackgroundTheme else LightAndroidBackgroundTheme
        ThemeBrand.COASTAL -> if (darkTheme) DarkCoastalBackgroundTheme else LightCoastalBackgroundTheme
        ThemeBrand.ROYAL -> if (darkTheme) DarkRoyalBackgroundTheme else LightRoyalBackgroundTheme
        ThemeBrand.CUSTOM -> defaultBackgroundTheme
    }
    val tintTheme = when (themeBrand) {
        ThemeBrand.DEFAULT -> when {
            !disableDynamicTheming && supportsDynamicTheming() -> TintTheme(colorScheme.primary)
            else -> TintTheme()
        }
        ThemeBrand.ANDROID -> TintTheme()
        ThemeBrand.COASTAL -> TintTheme()
        ThemeBrand.ROYAL -> TintTheme()
        ThemeBrand.CUSTOM -> TintTheme(colorScheme.primary)
    }
    // Composition locals
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalGradientColors provides gradientColors,
        LocalBackgroundTheme provides backgroundTheme,
        LocalTintTheme provides tintTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NiaTypography,
            content = content,
        )
    }
}


@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Derives a Material 3 ColorScheme from a single seed colour by deriving
 * primary/secondary/tertiary roles via HSL rotations of the seed. This is a
 * pragmatic stand-in for Material's HCT-based scheme generator; it gives a
 * coherent palette without pulling in material-color-utilities.
 */
internal fun colorSchemeFromSeeds(
    primary: Color,
    secondary: Color?,
    tertiary: Color?,
    darkTheme: Boolean,
): androidx.compose.material3.ColorScheme {
    val primaryHsl = primary.toHsl()
    val primaryHue = primaryHsl[0]
    val secondaryHsl = (secondary ?: hslToColor((primaryHue + 30f).mod(360f), 0.40f, 0.45f)).toHsl()
    val tertiaryHsl = (tertiary ?: hslToColor((primaryHue + 60f).mod(360f), 0.45f, 0.45f)).toHsl()
    val secondaryHue = secondaryHsl[0]
    val tertiaryHue = tertiaryHsl[0]

    fun hsl(h: Float, s: Float, l: Float): Color {
        val safeS = s.coerceIn(0f, 1f)
        val safeL = l.coerceIn(0f, 1f)
        return hslToColor(h.mod(360f), safeS, safeL)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = hsl(primaryHue, 0.50f, 0.80f),
            onPrimary = hsl(primaryHue, 0.40f, 0.20f),
            primaryContainer = hsl(primaryHue, 0.40f, 0.30f),
            onPrimaryContainer = hsl(primaryHue, 0.40f, 0.90f),
            secondary = hsl(secondaryHue, 0.30f, 0.75f),
            onSecondary = hsl(secondaryHue, 0.25f, 0.20f),
            secondaryContainer = hsl(secondaryHue, 0.25f, 0.30f),
            onSecondaryContainer = hsl(secondaryHue, 0.30f, 0.90f),
            tertiary = hsl(tertiaryHue, 0.40f, 0.78f),
            onTertiary = hsl(tertiaryHue, 0.30f, 0.20f),
            tertiaryContainer = hsl(tertiaryHue, 0.30f, 0.30f),
            onTertiaryContainer = hsl(tertiaryHue, 0.30f, 0.90f),
            background = hsl(primaryHue, 0.06f, 0.065f),
            onBackground = hsl(primaryHue, 0.04f, 0.94f),
            surface = hsl(primaryHue, 0.06f, 0.09f),
            onSurface = hsl(primaryHue, 0.04f, 0.94f),
            surfaceVariant = hsl(primaryHue, 0.08f, 0.18f),
            onSurfaceVariant = hsl(primaryHue, 0.06f, 0.80f),
            inverseSurface = hsl(primaryHue, 0.04f, 0.90f),
            inverseOnSurface = hsl(primaryHue, 0.06f, 0.12f),
            outline = hsl(primaryHue, 0.06f, 0.55f),
            outlineVariant = hsl(primaryHue, 0.08f, 0.25f),
            surfaceContainerLowest = hsl(primaryHue, 0.05f, 0.045f),
            surfaceContainerLow = hsl(primaryHue, 0.07f, 0.12f),
            surfaceContainer = hsl(primaryHue, 0.07f, 0.14f),
            surfaceContainerHigh = hsl(primaryHue, 0.07f, 0.165f),
            surfaceContainerHighest = hsl(primaryHue, 0.07f, 0.195f),
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = hsl(primaryHue, 0.55f, 0.40f),
            onPrimary = Color.White,
            primaryContainer = hsl(primaryHue, 0.40f, 0.90f),
            onPrimaryContainer = hsl(primaryHue, 0.45f, 0.15f),
            secondary = hsl(secondaryHue, 0.30f, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = hsl(secondaryHue, 0.30f, 0.90f),
            onSecondaryContainer = hsl(secondaryHue, 0.30f, 0.15f),
            tertiary = hsl(tertiaryHue, 0.45f, 0.45f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(tertiaryHue, 0.35f, 0.90f),
            onTertiaryContainer = hsl(tertiaryHue, 0.35f, 0.15f),
            background = hsl(primaryHue, 0.05f, 0.98f),
            onBackground = hsl(primaryHue, 0.05f, 0.10f),
            surface = hsl(primaryHue, 0.05f, 0.98f),
            onSurface = hsl(primaryHue, 0.05f, 0.10f),
            surfaceVariant = hsl(primaryHue, 0.06f, 0.92f),
            onSurfaceVariant = hsl(primaryHue, 0.06f, 0.30f),
            outline = hsl(primaryHue, 0.06f, 0.55f),
        )
    }
}

private fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta).mod(6f))
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return floatArrayOf(h.mod(360f), s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp.mod(2f) - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}
