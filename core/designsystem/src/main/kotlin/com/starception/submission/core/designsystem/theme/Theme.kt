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
    background = DarkPurpleGray10,
    onBackground = DarkPurpleGray90,
    surface = DarkPurpleGray10,
    onSurface = DarkPurpleGray90,
    surfaceVariant = PurpleGray30,
    onSurfaceVariant = PurpleGray80,
    inverseSurface = DarkPurpleGray90,
    inverseOnSurface = DarkPurpleGray10,
    outline = PurpleGray60,
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
    background = DarkGreenGray10,
    onBackground = DarkGreenGray90,
    surface = DarkGreenGray10,
    onSurface = DarkGreenGray90,
    surfaceVariant = GreenGray30,
    onSurfaceVariant = GreenGray80,
    inverseSurface = DarkGreenGray90,
    inverseOnSurface = DarkGreenGray10,
    outline = GreenGray60,
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
)

/**
 * Dark Elegant theme color scheme - sophisticated dark mode for peaceful reflection
 */
@VisibleForTesting
val DarkCoastalColorScheme = darkColorScheme(
    primary = ForestGreen80,
    onPrimary = ForestGreen10,
    primaryContainer = ForestGreen30,
    onPrimaryContainer = ForestGreen90,
    secondary = WarmGold80,
    onSecondary = WarmGold10,
    secondaryContainer = WarmGold30,
    onSecondaryContainer = WarmGold90,
    tertiary = SoftSage80,
    onTertiary = SoftSage10,
    tertiaryContainer = SoftSage30,
    onTertiaryContainer = SoftSage90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Color(0xFF0F130E),
    onBackground = Color(0xFFF2F4F0),
    surface = Color(0xFF171B16),
    onSurface = Color(0xFFF2F4F0),
    surfaceVariant = Color(0xFF1F241E),
    onSurfaceVariant = SoftSage80,
    inverseSurface = ForestGreen90,
    inverseOnSurface = ForestGreen10,
    outline = SoftSage40,
)

/**
 * Light Android gradient colors
 */
val LightAndroidGradientColors = GradientColors(container = DarkGreenGray95)

/**
 * Dark Android gradient colors
 */
val DarkAndroidGradientColors = GradientColors(container = Color.Black)

/**
 * Light Android background theme
 */
val LightAndroidBackgroundTheme = BackgroundTheme(color = DarkGreenGray95)

/**
 * Dark Android background theme
 */
val DarkAndroidBackgroundTheme = BackgroundTheme(color = Color.Black)

/**
 * Light Elegant gradient colors
 */
val LightCoastalGradientColors = GradientColors(container = Color(0xFFF8F9F7))

/**
 * Dark Elegant gradient colors
 */
val DarkCoastalGradientColors = GradientColors(container = Color(0xFF0F130E))

/**
 * Light Elegant background theme
 */
val LightCoastalBackgroundTheme = BackgroundTheme(color = Color(0xFFFBFCF9))

/**
 * Dark Elegant background theme
 */
val DarkCoastalBackgroundTheme = BackgroundTheme(color = Color(0xFF0F130E))

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
    themeBrand: ThemeBrand = ThemeBrand.DEFAULT,
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
    }
    val tintTheme = when (themeBrand) {
        ThemeBrand.DEFAULT -> when {
            !disableDynamicTheming && supportsDynamicTheming() -> TintTheme(colorScheme.primary)
            else -> TintTheme()
        }
        ThemeBrand.ANDROID -> TintTheme()
        ThemeBrand.COASTAL -> TintTheme()
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
