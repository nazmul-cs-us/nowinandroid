package com.starception.submission.shared.settings

import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.prayer.model.prayerDefaultsFor
import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.shared.location.DeviceLocation
import com.starception.submission.shared.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserSettingsPersistenceTest {

    @Test
    fun prayerSettingsAreScopedAndRestoredPerCountry() {
        val store = InMemoryKeyValueStore()
        val settings = UserPrayerSettings(store)
        val uae = prayerDefaultsFor("AE")
        val canada = prayerDefaultsFor("CA")
        val uaeChange = settings.settings("AE", uae).copy(
            calculationMethod = CalculationMethod.ISNA,
        )
        val canadaChange = settings.settings("CA", canada).copy(
            calculationMethod = CalculationMethod.EGYPTIAN_AUTHORITY,
        )

        settings.save("AE", uaeChange)
        settings.save("CA", canadaChange)

        assertEquals(uaeChange, settings.settings("AE", uae))
        assertEquals(canadaChange, settings.settings("CA", canada))
        assertTrue(settings.isChanged("AE", uae))
        settings.restoreDefaults("AE")
        assertFalse(settings.isChanged("AE", uae))
        assertEquals(
            PrayerTimeOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
            settings.settings("AE", uae).timeOffsets,
        )
        assertEquals(canadaChange, settings.settings("CA", canada))
    }

    @Test
    fun legacyPrayerSettingsMigrateOnlyToTheActiveCountry() {
        val sourceStore = InMemoryKeyValueStore()
        val source = UserPrayerSettings(sourceStore)
        val legacy = PrayerSettings(calculationMethod = CalculationMethod.ISNA)
        source.save("AE", legacy)

        val store = InMemoryKeyValueStore().apply {
            putString(
                "cached_prayer_settings",
                sourceStore.getString("cached_prayer_settings_country_AE").orEmpty(),
            )
        }
        val settings = UserPrayerSettings(store)

        val migrated = settings.settings("AE", prayerDefaultsFor("AE"))
        assertEquals(
            PrayerTimeOffsets(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
            migrated.timeOffsets,
        )
        assertEquals(
            CalculationMethod.ISNA,
            migrated.calculationMethod,
        )
        assertEquals(
            prayerDefaultsFor("CA")?.method,
            settings.settings("CA", prayerDefaultsFor("CA")).calculationMethod,
        )
        assertTrue(store.getString("cached_prayer_settings").isNullOrEmpty())

        // Reading again must not add the UAE values a second time.
        assertEquals(migrated, settings.settings("AE", prayerDefaultsFor("AE")))
    }

    @Test
    fun appearanceSettingsPersistSupportedIosChoices() {
        val store = InMemoryKeyValueStore()
        val settings = UserAppearanceSettings(store)

        settings.saveBrand(ThemeBrand.ROYAL)
        settings.saveDarkTheme(DarkThemeConfig.DARK)

        assertEquals(ThemeBrand.ROYAL, UserAppearanceSettings(store).settings().brand)
        assertEquals(DarkThemeConfig.DARK, UserAppearanceSettings(store).settings().darkThemeConfig)

        store.putString("ios_theme_brand", ThemeBrand.CUSTOM.name)
        assertEquals(ThemeBrand.COASTAL, UserAppearanceSettings(store).settings().brand)
    }

    @Test
    fun audioSettingsPersistAndClampSupportedChoices() {
        val store = InMemoryKeyValueStore()
        val settings = UserAudioSettings(store)

        settings.saveTravelDua(
            TravelDuaSettings(
                enabled = false,
                cooldownMinutes = 100,
                playbackDelaySeconds = 1,
                gapToleranceMinutes = 100,
                drivingSpeedThresholdKmh = 1,
            ),
        )
        settings.saveRecognitionMode(VoiceRecognitionMode.TRANSCRIPTION)
        settings.saveNarrationVoiceIdentifier("com.apple.voice.compact.en-GB.Daniel")

        assertEquals(
            TravelDuaSettings(
                enabled = false,
                cooldownMinutes = 30,
                playbackDelaySeconds = 10,
                gapToleranceMinutes = 15,
                drivingSpeedThresholdKmh = 10,
            ),
            UserAudioSettings(store).travelDua(),
        )
        assertEquals(VoiceRecognitionMode.TRANSCRIPTION, settings.recognitionMode())
        assertEquals(
            "com.apple.voice.compact.en-GB.Daniel",
            settings.narrationVoiceIdentifier(),
        )
    }

    @Test
    fun lastSuccessfulLocationRoundTripsAndRejectsInvalidData() {
        val store = InMemoryKeyValueStore()
        val locations = LastLocationStore(store)
        val toronto = DeviceLocation(
            latitude = 43.6532,
            longitude = -79.3832,
            timeZoneOffset = -4.0,
            placeName = "Toronto, Ontario",
            countryCode = "CA",
        )

        assertNull(locations.location())
        locations.save(toronto)
        assertEquals(toronto, LastLocationStore(store).location())

        store.putString(
            "ios_last_successful_location",
            """{"latitude":100.0,"longitude":0.0,"timeZoneOffset":0.0,"placeName":"","countryCode":""}""",
        )
        assertNull(locations.location())
    }
}
