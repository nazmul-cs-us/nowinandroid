package com.starception.submission.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Material roles for the four predefined themes shared by Android and iOS. */
private fun lightPresetScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    outline: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = Color.White,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onBackground,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    inverseSurface = onBackground,
    inverseOnSurface = background,
    outline = outline,
    outlineVariant = surfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = surface,
    surfaceContainer = lerp(surface, surfaceVariant, 0.35f),
    surfaceContainerHigh = lerp(surface, surfaceVariant, 0.7f),
    surfaceContainerHighest = surfaceVariant,
)

private fun darkPresetScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    outline: Color,
    onTertiaryContainer: Color = onBackground,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onBackground,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onBackground,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onBackground,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    inverseSurface = onBackground,
    inverseOnSurface = background,
    outline = outline,
    outlineVariant = surfaceVariant,
    surfaceContainerLowest = lerp(background, Color.Black, 0.25f),
    surfaceContainerLow = surface,
    surfaceContainer = lerp(surface, surfaceVariant, 0.3f),
    surfaceContainerHigh = lerp(surface, surfaceVariant, 0.6f),
    surfaceContainerHighest = surfaceVariant,
    scrim = Color.Black,
)

val LightPurpleGoldColorScheme = lightPresetScheme(
    primary = Color(0xFF5C237A), primaryContainer = Color(0xFFEEDBF6),
    onPrimaryContainer = Color(0xFF32103F),
    secondary = Color(0xFF795614), secondaryContainer = Color(0xFFF8E7BA),
    onSecondaryContainer = Color(0xFF392706),
    tertiary = Color(0xFF705270), tertiaryContainer = Color(0xFFEEDFED),
    onTertiaryContainer = Color(0xFF341F34),
    background = Color(0xFFFCF9F3), onBackground = Color(0xFF231A28),
    surface = Color(0xFFF8F3EC), surfaceVariant = Color(0xFFEDE3E8),
    onSurfaceVariant = Color(0xFF514655), outline = Color(0xFF81717F),
)

val DarkPurpleGoldColorScheme = darkPresetScheme(
    primary = Color(0xFFE7BEF5), onPrimary = Color(0xFF3B1652),
    primaryContainer = Color(0xFF5E2F73),
    secondary = Color(0xFFF3C86F), onSecondary = Color(0xFF422C04),
    secondaryContainer = Color(0xFF634719),
    tertiary = Color(0xFFDFC0E0), onTertiary = Color(0xFF462A47),
    tertiaryContainer = Color(0xFF5F4060),
    background = Color(0xFF18131B), onBackground = Color(0xFFF7EFF5),
    surface = Color(0xFF211A24), surfaceVariant = Color(0xFF423544),
    onSurfaceVariant = Color(0xFFDDC9DB), outline = Color(0xFFAB97A9),
)

val LightChocolateTruffleColorScheme = lightPresetScheme(
    primary = Color(0xFF593A2B), primaryContainer = Color(0xFFECD7C8),
    onPrimaryContainer = Color(0xFF321E16),
    secondary = Color(0xFF89501C), secondaryContainer = Color(0xFFF8DDBB),
    onSecondaryContainer = Color(0xFF3D230F),
    tertiary = Color(0xFF6F5448), tertiaryContainer = Color(0xFFEADDD4),
    onTertiaryContainer = Color(0xFF35261F),
    background = Color(0xFFFCF5E9), onBackground = Color(0xFF2B211C),
    surface = Color(0xFFF9F0E3), surfaceVariant = Color(0xFFECDDCF),
    onSurfaceVariant = Color(0xFF58483F), outline = Color(0xFF877368),
)

val DarkChocolateTruffleColorScheme = darkPresetScheme(
    primary = Color(0xFFE4BFA3), onPrimary = Color(0xFF432819),
    primaryContainer = Color(0xFF61412F),
    secondary = Color(0xFFF2BE83), onSecondary = Color(0xFF4D2A0C),
    secondaryContainer = Color(0xFF70411B),
    tertiary = Color(0xFFD9B9A8), onTertiary = Color(0xFF463027),
    tertiaryContainer = Color(0xFF604438),
    background = Color(0xFF191310), onBackground = Color(0xFFF6EBDF),
    surface = Color(0xFF251B17), surfaceVariant = Color(0xFF44342D),
    onSurfaceVariant = Color(0xFFDEC9BA), outline = Color(0xFFA89182),
)

val LightDamsonCuracaoColorScheme = lightPresetScheme(
    primary = Color(0xFF854C65), primaryContainer = Color(0xFFF3DCE7),
    onPrimaryContainer = Color(0xFF3B1B2B),
    secondary = Color(0xFF005871), secondaryContainer = Color(0xFFB6EEF2),
    onSecondaryContainer = Color(0xFF003544),
    tertiary = Color(0xFF815744), tertiaryContainer = Color(0xFFA47764),
    onTertiaryContainer = Color(0xFF261713),
    background = Color(0xFFFBF7F5), onBackground = Color(0xFF2C2027),
    surface = Color(0xFFF5EEEA), surfaceVariant = Color(0xFFE6D8D2),
    onSurfaceVariant = Color(0xFF594951), outline = Color(0xFF907A83),
)

val DarkDamsonCuracaoColorScheme = darkPresetScheme(
    primary = Color(0xFFDDB4C9), onPrimary = Color(0xFF4D263B),
    primaryContainer = Color(0xFF854C65),
    secondary = Color(0xFF33BECC), onSecondary = Color(0xFF003542),
    secondaryContainer = Color(0xFF005871),
    tertiary = Color(0xFFE2B79F), onTertiary = Color(0xFF4C3026),
    tertiaryContainer = Color(0xFFA47764),
    background = Color(0xFF1B161A), onBackground = Color(0xFFF6EBEE),
    surface = Color(0xFF261D21), surfaceVariant = Color(0xFF49363A),
    onSurfaceVariant = Color(0xFFDCC6CC), outline = Color(0xFFAA9099),
    onTertiaryContainer = Color(0xFF241610),
)

val LightChiliSpiceColorScheme = lightPresetScheme(
    primary = Color(0xFFA33121), primaryContainer = Color(0xFFFFDDD5),
    onPrimaryContainer = Color(0xFF4D150F),
    secondary = Color(0xFF92511A), secondaryContainer = Color(0xFFFFE0BE),
    onSecondaryContainer = Color(0xFF49250B),
    tertiary = Color(0xFF8A4233), tertiaryContainer = Color(0xFFF7D7CE),
    onTertiaryContainer = Color(0xFF481F17),
    background = Color(0xFFFFF8F2), onBackground = Color(0xFF301D19),
    surface = Color(0xFFFCF1E8), surfaceVariant = Color(0xFFF1DDD3),
    onSurfaceVariant = Color(0xFF614840), outline = Color(0xFF907168),
)

val DarkChiliSpiceColorScheme = darkPresetScheme(
    primary = Color(0xFFFFB2A2), onPrimary = Color(0xFF591B13),
    primaryContainer = Color(0xFF8E3022),
    secondary = Color(0xFFFFC194), onSecondary = Color(0xFF4A2609),
    secondaryContainer = Color(0xFF70401B),
    tertiary = Color(0xFFF0AB92), onTertiary = Color(0xFF51291C),
    tertiaryContainer = Color(0xFF754333),
    background = Color(0xFF1B120F), onBackground = Color(0xFFFFEDE5),
    surface = Color(0xFF281A15), surfaceVariant = Color(0xFF4A322A),
    onSurfaceVariant = Color(0xFFECCBC0), outline = Color(0xFFB59689),
)
