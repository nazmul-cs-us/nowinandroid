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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The app's colour schemes, moved here so the shared iOS UI uses the real
// palette rather than a copy that drifts. lightColorScheme/darkColorScheme are
// Material 3 Compose APIs and work unchanged on both platforms.
//
// The package is deliberately unchanged from when these lived in
// core:designsystem's Theme.kt, so none of the ~60 files referencing this
// package needed touching. NiaTheme stays in core:designsystem: it applies
// dynamic colour, which is Android-only.

/**
 * Light default theme color scheme
 */
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
