package com.starception.submission.shared.settings

import com.starception.submission.shared.storage.InMemoryKeyValueStore
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.model.data.DarkThemeConfig
import kotlin.test.*

class AppearanceParityTest {
    @Test fun customAccentsSurviveRecreationAndPresetSwitches() {
        val store = InMemoryKeyValueStore()
        val preferences = UserAppearanceSettings(store)
        preferences.saveCustomColors(0xFF123456.toInt(), 0xFF987654.toInt(), 0xFFABCDEF.toInt())
        preferences.saveDarkTheme(DarkThemeConfig.LIGHT)
        val restored = UserAppearanceSettings(store).settings()
        assertEquals(ThemeBrand.CUSTOM, restored.brand)
        assertEquals(0xFF123456.toInt(), restored.customColor)
        assertEquals(0xFF987654.toInt(), restored.customSecondaryColor)
        assertEquals(0xFFABCDEF.toInt(), restored.customTertiaryColor)
        assertEquals(DarkThemeConfig.LIGHT, restored.darkThemeConfig)
        preferences.saveBrand(ThemeBrand.CHILI_SPICE)
        assertEquals(ThemeBrand.CHILI_SPICE, preferences.settings().brand)
        preferences.saveBrand(ThemeBrand.CUSTOM)
        assertEquals(restored, preferences.settings())
    }
}
