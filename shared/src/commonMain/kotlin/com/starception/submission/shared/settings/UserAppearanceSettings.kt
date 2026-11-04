/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.settings

import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.settings.ThemeSettingsState
import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UserAppearanceSettings(private val store: KeyValueStore = platformKeyValueStore()) {

    fun settings(): ThemeSettingsState = ThemeSettingsState(
        brand = store.getString(KEY_BRAND)
            ?.let { name -> ThemeBrand.entries.firstOrNull { it.name == name } }
            ?: ThemeBrand.COASTAL,
        customColor = store.getString("ios_custom_primary")?.toIntOrNull() ?: 0,
        customSecondaryColor = store.getString("ios_custom_secondary")?.toIntOrNull() ?: 0,
        customTertiaryColor = store.getString("ios_custom_tertiary")?.toIntOrNull() ?: 0,
        darkThemeConfig = store.getString(KEY_DARK_MODE)
            ?.let { name -> DarkThemeConfig.entries.firstOrNull { it.name == name } }
            ?: DarkThemeConfig.FOLLOW_SYSTEM,
    )

    fun saveBrand(brand: ThemeBrand) {
        run {
            store.putString(KEY_BRAND, brand.name)
            appearanceRevision.value += 1
        }
    }

    fun saveCustomColors(primary: Int, secondary: Int, tertiary: Int) {
        store.putString("ios_custom_primary", primary.toString())
        store.putString("ios_custom_secondary", secondary.toString())
        store.putString("ios_custom_tertiary", tertiary.toString())
        store.putString(KEY_BRAND, ThemeBrand.CUSTOM.name)
        appearanceRevision.value += 1
    }

    fun saveDarkTheme(config: DarkThemeConfig) {
        store.putString(KEY_DARK_MODE, config.name)
        appearanceRevision.value += 1
    }

    val changes: StateFlow<Int>
        get() = appearanceRevision

    private companion object {
        const val KEY_BRAND = "ios_theme_brand"
        const val KEY_DARK_MODE = "ios_dark_theme_config"
        val appearanceRevision = MutableStateFlow(0)
    }
}
