/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.core.translation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class LocationBasedTranslationDefaultsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)

    @AfterTest
    fun clearPreferences() {
        prefs.edit().clear().commit()
    }

    @Test
    fun supportedCountriesMapToTheirLocalTranslation() {
        assertEquals("ar", LocationBasedTranslationDefaults.languageForCountry("AE"))
        assertEquals("bn", LocationBasedTranslationDefaults.languageForCountry("bd"))
        assertEquals("ur", LocationBasedTranslationDefaults.languageForCountry("PK"))
        assertEquals("id", LocationBasedTranslationDefaults.languageForCountry("ID"))
        assertEquals("tr", LocationBasedTranslationDefaults.languageForCountry("TR"))
        assertEquals("zh", LocationBasedTranslationDefaults.languageForCountry("CN"))
        assertEquals("es", LocationBasedTranslationDefaults.languageForCountry("MX"))
        assertEquals("fr", LocationBasedTranslationDefaults.languageForCountry("FR"))
        assertEquals("ru", LocationBasedTranslationDefaults.languageForCountry("RU"))
        assertEquals("sv", LocationBasedTranslationDefaults.languageForCountry("SE"))
    }

    @Test
    fun unsupportedOrMissingCountryFallsBackToEnglish() {
        assertEquals("en", LocationBasedTranslationDefaults.languageForCountry("US"))
        assertEquals("en", LocationBasedTranslationDefaults.languageForCountry("IN"))
        assertEquals("en", LocationBasedTranslationDefaults.languageForCountry(null))
    }

    @Test
    fun freshInstallStoresDetectedDefaultOnlyOnce() {
        LocationBasedTranslationDefaults.prepare(prefs, isFreshInstall = true)

        assertEquals(
            "bn",
            LocationBasedTranslationDefaults.applyDetectedCountry(prefs, "BD"),
        )
        assertEquals("bn", prefs.getString("quran_translation", null))
        assertNull(LocationBasedTranslationDefaults.applyDetectedCountry(prefs, "TR"))
        assertEquals("bn", prefs.getString("quran_translation", null))
    }

    @Test
    fun manualOrRestoredSelectionIsNeverOverwritten() {
        prefs.edit().putString("quran_translation", "fr").commit()

        LocationBasedTranslationDefaults.prepare(prefs, isFreshInstall = true)

        assertNull(LocationBasedTranslationDefaults.applyDetectedCountry(prefs, "BD"))
        assertEquals("fr", prefs.getString("quran_translation", null))
    }

    @Test
    fun appUpgradeIsNotTreatedAsFirstInstall() {
        LocationBasedTranslationDefaults.prepare(prefs, isFreshInstall = false)

        assertNull(LocationBasedTranslationDefaults.applyDetectedCountry(prefs, "PK"))
        assertNull(prefs.getString("quran_translation", null))
    }

    private companion object {
        const val TEST_PREFS = "location_translation_defaults_test"
    }
}
