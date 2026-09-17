package com.starception.submission.prayer.sky

import com.starception.submission.prayer.calculator.AstronomicalCalculator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * The sky maths against Meeus's own worked examples.
 *
 * These are the published answers in *Astronomical Algorithms*, so a failure here
 * means the formula is wrong rather than that someone's expectation moved. That
 * matters more than usual for this module: it runs on every target the project
 * builds for, and nobody can eyeball a widget and tell that the moon is a degree out.
 *
 * Tolerances are set at the accuracy each model actually claims — see the note on
 * [SkySnapshot] — not at machine precision. Asserting tighter than the model would
 * only record the truncation, not verify it.
 */
class SkyMathTest {

    /** Meeus example 12.a — 1987 April 10 at 0h UT. */
    @Test
    fun greenwichMeanSiderealTimeMatchesMeeus() {
        // 13h 10m 46.3668s, in degrees.
        val expected = (13.0 + 10.0 / 60.0 + 46.3668 / 3600.0) * 15.0
        assertEquals(expected, greenwichMeanSiderealTime(2446895.5), absoluteTolerance = 1e-4)
    }

    /** Meeus example 25.a — the sun on 1992 October 13 at 0h TD. */
    @Test
    fun solarPositionMatchesMeeus() {
        val sun = solarPosition(2448908.5)
        assertEquals(199.90895, sun.apparentLongitudeDeg, absoluteTolerance = 0.01)
        // 13h 13m 31.4s.
        assertEquals(198.38083, sun.rightAscensionDeg, absoluteTolerance = 0.01)
        // -7 deg 47' 06".
        assertEquals(-7.78507, sun.declinationDeg, absoluteTolerance = 0.01)
    }

    /**
     * Meeus example 13.b — Venus from Washington, converted to the horizon.
     *
     * Meeus works in hour angle; local sidereal time is that plus the right
     * ascension. His azimuth runs westward from south, so the compass value this
     * returns is his plus 180.
     */
    @Test
    fun equatorialToHorizontalMatchesMeeus() {
        // Venus from the US Naval Observatory, 1987 April 10 at 19:21 UT.
        // RA 23h 09m 16.641s, Dec -6 deg 43' 11.61", hour angle 64.352133.
        val rightAscension = (23.0 + 9.0 / 60.0 + 16.641 / 3600.0) * 15.0
        val hourAngle = 64.352133
        val altAz = equatorialToHorizontal(
            rightAscensionDeg = rightAscension,
            declinationDeg = -(6.0 + 43.0 / 60.0 + 11.61 / 3600.0),
            latitudeDeg = 38.9213889,
            localSiderealTimeDeg = hourAngle + rightAscension,
        )
        assertEquals(15.1249, altAz.altitudeDeg, absoluteTolerance = 1e-3)
        assertEquals(68.0337 + 180.0, altAz.azimuthDeg, absoluteTolerance = 1e-3)
    }

    /**
     * Meeus examples 47.a and 13.a — the moon on 1992 April 12 at 0h TD.
     *
     * The tolerance is a tenth of a degree because the series here is abridged to
     * sixteen longitude terms; Meeus's full table would hold about ten arcseconds.
     * A tenth of a degree is under half a pixel on the card this exists for.
     */
    @Test
    fun lunarPositionMatchesMeeus() {
        val moon = lunarPosition(2448724.5)
        assertEquals(134.688470, moon.rightAscensionDeg, absoluteTolerance = 0.1)
        assertEquals(13.768368, moon.declinationDeg, absoluteTolerance = 0.1)
        assertEquals(368409.7, moon.distanceKm, absoluteTolerance = 500.0)
    }

    /** Meeus example 48.a — the same instant, illuminated fraction 0.6786. */
    @Test
    fun lunarPhaseMatchesMeeus() {
        val julianDay = 2448724.5
        val phase = lunarPhase(solarPosition(julianDay), lunarPosition(julianDay))
        assertEquals(0.6786, phase.illuminatedFraction, absoluteTolerance = 0.005)
    }

    /**
     * The moon's parallax is kept while everything else at that scale is dropped,
     * so check it is the size it is supposed to be: about a degree at the horizon,
     * and nothing at all overhead.
     */
    @Test
    fun lunarParallaxIsLargestAtTheHorizonAndVanishesAtTheZenith() {
        val nearDistance = 363300.0
        assertTrue(
            lunarParallaxDeg(nearDistance, geocentricAltitudeDeg = 0.0) > 0.9,
            "parallax at the horizon should exceed 0.9 degrees",
        )
        assertEquals(
            0.0,
            lunarParallaxDeg(nearDistance, geocentricAltitudeDeg = 90.0),
            absoluteTolerance = 1e-9,
        )
    }

    /**
     * The new solar series and the prayer engine's must not drift apart.
     *
     * They are separate on purpose — the calculator logs far too much to sit in a
     * render path — but they describe the same sun, and a silent divergence would
     * show up as a card that disagrees with the prayer times printed underneath it.
     */
    @Test
    fun solarDeclinationAgreesWithThePrayerEngine() {
        val calculator = AstronomicalCalculator()
        var worst = 0.0
        for (dayOfYear in 1..365 step 7) {
            val date = LocalDate(2026, 1, 1).plusDaysNaive(dayOfYear - 1)
            val julianDay = calculator.calculateJulianDay(date)
            val engine = calculator.calculateSolarDeclination(julianDay).radToDeg()
            val ours = solarPosition(julianDay).declinationDeg
            worst = maxOf(worst, abs(engine - ours))
        }
        assertTrue(
            worst < 0.35,
            "solar declination drifted from the prayer engine by $worst degrees",
        )
    }
}

/** kotlinx-datetime's `plus` needs a DatePeriod; this keeps the test readable. */
private fun LocalDate.plusDaysNaive(days: Int): LocalDate =
    LocalDate.fromEpochDays(this.toEpochDays() + days)
