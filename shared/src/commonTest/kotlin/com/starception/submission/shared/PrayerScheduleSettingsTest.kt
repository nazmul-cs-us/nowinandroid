package com.starception.submission.shared

import com.starception.submission.prayer.model.AsrMadhhab
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.HighLatitudeAdjustment
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.prayer.model.prayerDefaultsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the coupling between settings and prayer times.
 *
 * The settings screen and the schedule are the same feature: a change here must
 * move the times in a specific, checkable way. These fix Dubai on a known date so
 * a regression shows as a wrong minute rather than a plausible one.
 */
class PrayerScheduleSettingsTest {

    private val dubaiLat = 25.1030198
    private val dubaiLon = 55.1677409
    private val gulfOffset = 4.0

    private fun schedule(
        settings: PrayerSettings,
        countryCode: String = "AE",
    ): Map<String, String> = PrayerSchedule.forDate(
        year = 2026,
        month = 8,
        day = 27,
        latitude = dubaiLat,
        longitude = dubaiLon,
        timeZoneOffset = gulfOffset,
        defaults = prayerDefaultsFor(countryCode),
        settings = settings,
        nowHour = 12,
        nowMinute = 0,
    ).slots.associate { it.name to it.display }

    private val uaeCountry = requireNotNull(prayerDefaultsFor("AE"))
    private val uaeOffsets = PrayerTimeOffsets(
        fajr = uaeCountry.timeOffsets["fajr"] ?: 0,
        sunrise = uaeCountry.timeOffsets["sunrise"] ?: 0,
        dhuhr = uaeCountry.timeOffsets["dhuhr"] ?: 0,
        asr = uaeCountry.timeOffsets["asr"] ?: 0,
        maghrib = uaeCountry.timeOffsets["maghrib"] ?: 0,
        isha = uaeCountry.timeOffsets["isha"] ?: 0,
    )
    private val uaeDefaults = PrayerSettings(
        calculationMethod = CalculationMethod.UAE_IACAD,
        asrMadhhab = AsrMadhhab.STANDARD,
        timeOffsets = uaeOffsets,
    )

    @Test
    fun hanafiMovesAsrLaterAndLeavesTheRestAlone() {
        val standard = schedule(uaeDefaults)
        val hanafi = schedule(uaeDefaults.copy(asrMadhhab = AsrMadhhab.HANAFI))

        assertTrue(
            hanafi.getValue("Asr") > standard.getValue("Asr"),
            "Hanafi Asr ${hanafi["Asr"]} should be later than standard ${standard["Asr"]}",
        )
        // The madhhab only defines Asr's shadow; nothing else may move.
        listOf("Fajr", "Sunrise", "Dhuhr", "Maghrib", "Isha").forEach { prayer ->
            assertEquals(standard[prayer], hanafi[prayer], "$prayer moved with the madhhab")
        }
    }

    @Test
    fun theMethodMovesOnlyFajrAndIsha() {
        val uae = schedule(uaeDefaults)
        val isna = schedule(uaeDefaults.copy(calculationMethod = CalculationMethod.ISNA))

        // ISNA uses 15 degrees against IACAD's 18.2, so Fajr is later, Isha earlier.
        assertTrue(isna.getValue("Fajr") > uae.getValue("Fajr"))
        assertTrue(isna.getValue("Isha") < uae.getValue("Isha"))
        // Sunrise, Dhuhr and Maghrib are solar events and cannot depend on method.
        listOf("Sunrise", "Dhuhr", "Maghrib").forEach { prayer ->
            assertEquals(uae[prayer], isna[prayer], "$prayer moved with the method")
        }
    }

    @Test
    fun ummAlQuraPutsIshaNinetyMinutesAfterMaghrib() {
        val settings = uaeDefaults.copy(
            calculationMethod = CalculationMethod.UMM_AL_QURA,
            timeOffsets = PrayerTimeOffsets(),
        )
        // A country with no published offsets, so the method is measured alone.
        val times = schedule(settings, countryCode = "ZZ")

        val maghrib = assertNotNull(times["Maghrib"]).toMinutes()
        val isha = assertNotNull(times["Isha"]).toMinutes()
        // The method defines Isha as a delay, not an angle. A zero angle would
        // put Isha at sunset instead, which is what a naive port does.
        assertEquals(90, isha - maghrib)
    }

    @Test
    fun countryOffsetsShiftPrayersIndependentlyOfTheIshaDelay() {
        // The delay is measured on the calculated Maghrib, then each prayer takes
        // its own offset. The UAE shifts Maghrib by +3 and Isha by 0, so the gap
        // on screen reads 87 rather than 90. That is the existing behaviour and
        // is pinned here so it cannot change unnoticed — my first version of the
        // test above asserted 90 against these offsets and was simply wrong about
        // which layer it was measuring.
        val settings = uaeDefaults.copy(calculationMethod = CalculationMethod.UMM_AL_QURA)
        val times = schedule(settings, countryCode = "AE")

        val gap = assertNotNull(times["Isha"]).toMinutes() -
            assertNotNull(times["Maghrib"]).toMinutes()
        assertEquals(87, gap)
    }

    @Test
    fun userOffsetsAddToTheCountrysOwn() {
        val plain = schedule(uaeDefaults)
        val adjusted = schedule(
            uaeDefaults.copy(timeOffsets = uaeOffsets.copy(asr = uaeOffsets.asr + 7)),
        )

        assertEquals(
            plain.getValue("Asr").toMinutes() + 7,
            adjusted.getValue("Asr").toMinutes(),
        )
    }

    @Test
    fun theUaesPublishedOffsetsApplyEvenWithoutUserChanges() {
        // Country AE shifts Asr +3 and Sunrise -3; a country with none must not.
        val withCountry = schedule(uaeDefaults, countryCode = "AE")
        val withoutCountry = schedule(
            uaeDefaults.copy(timeOffsets = PrayerTimeOffsets()),
            countryCode = "ZZ",
        )

        assertEquals(
            withoutCountry.getValue("Asr").toMinutes() + 3,
            withCountry.getValue("Asr").toMinutes(),
        )
        assertEquals(
            withoutCountry.getValue("Sunrise").toMinutes() - 3,
            withCountry.getValue("Sunrise").toMinutes(),
        )
    }

    @Test
    fun highLatitudeSettingsRestoreMissingTwilightTimes() {
        fun stockholm(adjustment: HighLatitudeAdjustment) = PrayerSchedule.forDate(
            year = 2026,
            month = 6,
            day = 21,
            latitude = 59.3293,
            longitude = 18.0686,
            timeZoneOffset = 2.0,
            settings = PrayerSettings(
                calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
                highLatitudeAdjustment = adjustment,
            ),
            nowHour = 12,
            nowMinute = 0,
        ).slots.associateBy { it.name }

        val unadjusted = stockholm(HighLatitudeAdjustment.NONE)
        assertTrue("Sunrise" in unadjusted && "Maghrib" in unadjusted)
        assertTrue("Fajr" !in unadjusted && "Isha" !in unadjusted)

        HighLatitudeAdjustment.entries.filterNot { it == HighLatitudeAdjustment.NONE }.forEach {
            val adjusted = stockholm(it)
            assertTrue("Fajr" in adjusted, "$it did not restore Fajr")
            assertTrue("Isha" in adjusted, "$it did not restore Isha")
        }
    }

    @Test
    fun dashboardStartsWithTheLatestPrayerEventAndWraps() {
        val day = PrayerSchedule.forDate(
            year = 2026,
            month = 8,
            day = 27,
            latitude = dubaiLat,
            longitude = dubaiLon,
            timeZoneOffset = gulfOffset,
            defaults = prayerDefaultsFor("AE"),
            settings = uaeDefaults,
            nowHour = 19,
            nowMinute = 0,
        )

        assertEquals(
            listOf("Maghrib", "Isha", "Fajr", "Sunrise", "Dhuhr", "Asr"),
            day.dashboardSlots().map { it.name },
        )
        assertEquals(19 * 60, day.nowMinute)
    }

    @Test
    fun dashboardKeepsChronologicalOrderBeforeFajr() {
        val day = PrayerSchedule.forDate(
            year = 2026,
            month = 8,
            day = 27,
            latitude = dubaiLat,
            longitude = dubaiLon,
            timeZoneOffset = gulfOffset,
            defaults = prayerDefaultsFor("AE"),
            settings = uaeDefaults,
            nowHour = 1,
            nowMinute = 0,
        )

        assertEquals(
            listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
            day.dashboardSlots().map { it.name },
        )
    }

    private fun String.toMinutes(): Int {
        val (h, m) = split(":").map { it.toInt() }
        return h * 60 + m
    }
}
