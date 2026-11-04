package com.starception.submission.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

fun seedColorScheme(
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
