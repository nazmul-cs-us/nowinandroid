/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.shared.settings

import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.settings.ThemeSettingsState
import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore

class UserAppearanceSettings(private val store: KeyValueStore = platformKeyValueStore()) {

    fun settings(): ThemeSettingsState = ThemeSettingsState(
        brand = store.getString(KEY_BRAND)
            ?.let { name -> ThemeBrand.entries.firstOrNull { it.name == name } }
            ?.takeUnless { it == ThemeBrand.CUSTOM }
            ?: ThemeBrand.COASTAL,
        darkThemeConfig = store.getString(KEY_DARK_MODE)
            ?.let { name -> DarkThemeConfig.entries.firstOrNull { it.name == name } }
            ?: DarkThemeConfig.FOLLOW_SYSTEM,
    )

    fun saveBrand(brand: ThemeBrand) {
        if (brand != ThemeBrand.CUSTOM) store.putString(KEY_BRAND, brand.name)
    }

    fun saveDarkTheme(config: DarkThemeConfig) {
        store.putString(KEY_DARK_MODE, config.name)
    }

    private companion object {
        const val KEY_BRAND = "ios_theme_brand"
        const val KEY_DARK_MODE = "ios_dark_theme_config"
    }
}
