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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders

/**
 * Fixed palette shared by every widget, placed or previewed.
 *
 * Glance's default theme resolves to Material You on API 31+, which means a placed
 * widget takes its colours from the wallpaper. That is the usual recommendation, but it
 * cannot be reconciled with a picker preview: a preview is either a baked PNG (as in
 * platform-samples) or a static layout, and neither can follow the wallpaper. Whichever
 * way round, the widget you see in the list is not the widget you get on the home
 * screen — measured here as a navy preview against a brown-green placed widget.
 *
 * Pinning the palette removes that entirely: preview and placed widget are the same
 * colours on every device. The cost is that widgets no longer tint to the wallpaper.
 *
 * The values are sampled pixel-for-pixel from platform-samples' own preview assets
 * (drawable-nodpi and drawable-night-nodpi sample_check_list_preview.png), so the whole
 * set — the five prayer widgets and the nine ported ones — renders in the palette those
 * previews advertise.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF445E91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE2F9),
    onPrimaryContainer = Color(0xFF141B2C),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    background = Color(0xFFECF0FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFECF0FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF122F60),
    primaryContainer = Color(0xFF2B4678),
    onPrimaryContainer = Color(0xFFDCE2F9),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293042),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDCE2F9),
    background = Color(0xFF283041),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF283041),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9099),
)

/** Pass to `GlanceTheme(colors = PrayerWidgetColors)` in every widget. */
internal val PrayerWidgetColors: ColorProviders = ColorProviders(
    light = LightScheme,
    dark = DarkScheme,
)
