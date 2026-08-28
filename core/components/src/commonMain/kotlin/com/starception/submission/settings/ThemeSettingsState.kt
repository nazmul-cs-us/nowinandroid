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

package com.starception.submission.settings

import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand

/**
 * Theme settings state.
 *
 * Moved out of UnifiedSettingsViewModel so the shared AppearanceSection can read
 * it without the view model, which is Hilt-bound and Android-only.
 */
data class ThemeSettingsState(
    val brand: ThemeBrand = ThemeBrand.COASTAL,
    val useDynamicColor: Boolean = false,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    /** ARGB ints of the three custom accents (primary/secondary/tertiary) when [brand] == ThemeBrand.CUSTOM. 0 = none yet. */
    val customColor: Int = 0,
    val customSecondaryColor: Int = 0,
    val customTertiaryColor: Int = 0,
)
