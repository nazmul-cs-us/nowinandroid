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

/** The paint used behind every app widget. */
enum class WidgetBackgroundType {
    BASIC,
    DYNAMIC_COLOR,
}

/** Controls whether widgets follow the phone's day/night state or use a fixed mode. */
enum class WidgetColorMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

/** Global appearance shared by every widget published by the app. */
data class WidgetAppearanceSettings(
    val showBackground: Boolean = true,
    val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DYNAMIC_COLOR,
    // Literal slider direction: 0 is the thinnest enabled glass plate and 1 is fully
    // solid. Turning Background off is the separate path to complete transparency.
    val backgroundOpacity: Float = 1f,
    val colorMode: WidgetColorMode = WidgetColorMode.FOLLOW_SYSTEM,
)

/**
 * Slider position 0 is the thinnest enabled plate, not a missing one. Reserving a floor
 * gives the switch a distinct meaning: disabling Background removes the plate completely,
 * while 0% keeps the launcher's lowest enabled opacity.
 */
internal fun WidgetAppearanceSettings.effectiveBackgroundAlpha(): Float =
    if (!showBackground) {
        0f
    } else {
        MIN_ENABLED_BACKGROUND_ALPHA +
            backgroundOpacity.coerceIn(0f, 1f) * (1f - MIN_ENABLED_BACKGROUND_ALPHA)
    }

/**
 * Reference artwork is part of the content, so it cannot disappear as aggressively as the
 * launcher plate without making the overlaid copy unreadable. It still follows the global
 * slider, but retains only the small readability floor requested by each section.
 */
internal fun WidgetAppearanceSettings.effectiveArtworkAlpha(
    minimumEnabledAlpha: Float,
): Float = if (!showBackground) {
    minimumEnabledAlpha.coerceIn(0f, 1f)
} else {
    val minimum = minimumEnabledAlpha.coerceIn(0f, 1f)
    minimum + backgroundOpacity.coerceIn(0f, 1f) * (1f - minimum)
}

/** Low-opacity surfaces need wallpaper-safe foregrounds rather than surface-coloured ink. */
internal fun WidgetAppearanceSettings.needsWallpaperContrast(): Boolean =
    !showBackground || backgroundOpacity <= WALLPAPER_CONTRAST_OPACITY

// The supplied design sits on a near-white sheet (~#F1F1F4 over a blurred wallpaper), and
// One UI itself reports opacity=70 for Now Brief's first slider position. Thinner plates
// were tried (30–40%) to imitate Now Brief's frosted look, but without the launcher's blur
// they read as a grey veil and cost the header's muted date its legibility.
private const val MIN_ENABLED_BACKGROUND_ALPHA = 0.70f
private const val WALLPAPER_CONTRAST_OPACITY = 0.20f

/**
 * Small synchronous preference store for widget-only presentation state.
 *
 * Glance widgets can be rendered while no Activity or ViewModel exists, so this state
 * deliberately lives in app-private SharedPreferences rather than UI memory. A write is
 * visible to the process immediately, allowing the following widget refresh to read the
 * new value even though the preference file is flushed asynchronously.
 */
object WidgetAppearancePreferences {
    private const val PREFERENCES_NAME = "widget_appearance"
    private const val KEY_SHOW_BACKGROUND = "show_background"
    private const val KEY_BACKGROUND_TYPE = "background_type"
    private const val KEY_BACKGROUND_OPACITY = "background_opacity"
    private const val KEY_COLOR_MODE = "color_mode"

    fun read(context: Context): WidgetAppearanceSettings {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        return WidgetAppearanceSettings(
            showBackground = preferences.getBoolean(KEY_SHOW_BACKGROUND, true),
            backgroundType = preferences.getString(KEY_BACKGROUND_TYPE, null)
                .toEnumOrDefault(WidgetBackgroundType.DYNAMIC_COLOR),
            backgroundOpacity = preferences.getFloat(KEY_BACKGROUND_OPACITY, 1f)
                .coerceIn(0f, 1f),
            colorMode = preferences.getString(KEY_COLOR_MODE, null)
                .toEnumOrDefault(WidgetColorMode.FOLLOW_SYSTEM),
        )
    }

    fun write(context: Context, settings: WidgetAppearanceSettings) {
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit()
            .putBoolean(KEY_SHOW_BACKGROUND, settings.showBackground)
            .putString(KEY_BACKGROUND_TYPE, settings.backgroundType.name)
            .putFloat(KEY_BACKGROUND_OPACITY, settings.backgroundOpacity.coerceIn(0f, 1f))
            .putString(KEY_COLOR_MODE, settings.colorMode.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
}
