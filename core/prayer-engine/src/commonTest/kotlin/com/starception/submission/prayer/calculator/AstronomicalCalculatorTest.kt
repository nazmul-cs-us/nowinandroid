package com.starception.submission.prayer.calculator

import com.starception.submission.prayer.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Golden values for the shared engine, so a Kotlin/Native regression is caught
 * here rather than by someone noticing a prayer time is wrong.
 *
 * These run on every target the module builds for. That is the point: the JVM and
 * Kotlin/Native must agree to the second, and floating-point maths is exactly the
 * kind of thing that quietly does not.
 */
class AstronomicalCalculatorTest {

    private val calculator = AstronomicalCalculator()
    private val date = LocalDate(2026, 8, 27)

    /** Where the test device actually is, taken from its own logs. */
    private val nadAlHamar = Location(
        latitude = 25.1030198,
        longitude = 55.1677409,
        timeZoneOffset = 4.0,
    )

    /** Dubai city centre — about 20 km away. */
    private val dubaiCentre = Location(
        latitude = 25.276987,
        longitude = 55.296249,
        timeZoneOffset = 4.0,
    )

    private fun asrAt(location: Location): String {
        val julianDay = calculator.calculateJulianDay(date)
        val decimal = calculator.calculateAsr(location, julianDay, 1)
        val time = calculator.decimalHourToLocalTime(decimal)!!
        return "${time.hour}:${time.minute.toString().padStart(2, '0')}"
    }

    private fun scheduleAt(location: Location): Map<String, String> {
        val julianDay = calculator.calculateJulianDay(date)
        fun fmt(decimal: Double): String {
            val t = calculator.decimalHourToLocalTime(decimal)!!
            return "${t.hour}:${t.minute.toString().padStart(2, '0')}"
        }
        return mapOf(
            "Fajr" to fmt(calculator.calculateFajr(location, julianDay, 18.2)),
            "Sunrise" to fmt(calculator.calculateSunrise(location, julianDay)),
            "Dhuhr" to fmt(calculator.calculateSolarNoon(location, julianDay)),
            "Asr" to fmt(calculator.calculateAsr(location, julianDay, 1)),
            "Maghrib" to fmt(calculator.calculateSunset(location, julianDay)),
            "Isha" to fmt(calculator.calculateIsha(location, julianDay, 18.2, null, 0)),
        )
    }

    @Test
    fun matchesTheAndroidEngineForTheTestDevice() {
        // Golden values taken from the Android app's own logs on 2026-08-27, for
        // the device's real location and settings: UAE_IACAD, Fajr and Isha
        // angles 18.2, Asr madhhab STANDARD (shadow factor 1). Android logged
        // Fajr 04:38:05, Asr 15:50:18, Isha 20:03:54 — so the shared engine must
        // agree to the minute, on every target it builds for.
        val schedule = scheduleAt(nadAlHamar)

        assertEquals("4:38", schedule["Fajr"])
        assertEquals("15:50", schedule["Asr"])
        assertEquals("20:03", schedule["Isha"])
    }

    @Test
    fun asrIsInsensitiveToTwentyKilometresOfLongitude() {
        // Recorded because it corrected a wrong assumption: a one-minute Asr
        // difference between the iOS and Android apps was blamed on Nad Al Hamar
        // versus Dubai centre. It is not — Asr is identical across both. The
        // difference came from the iOS caller passing different angles, not from
        // location and not from the engine.
        assertEquals(asrAt(nadAlHamar), asrAt(dubaiCentre))
    }

    @Test
    fun highLatitudeReturnsNoTimeRatherThanAWrongOne() {
        // Tromsø in midsummer: the sun never reaches the Fajr angle, so the
        // calculator must yield nothing rather than a plausible-looking time.
        val tromso = Location(latitude = 69.6492, longitude = 18.9553, timeZoneOffset = 2.0)
        val julianDay = calculator.calculateJulianDay(LocalDate(2026, 6, 21))
        val fajr = calculator.calculateFajr(tromso, julianDay, 18.2)

        assertTrue(fajr.isNaN() || calculator.decimalHourToLocalTime(fajr) == null)
    }
}
