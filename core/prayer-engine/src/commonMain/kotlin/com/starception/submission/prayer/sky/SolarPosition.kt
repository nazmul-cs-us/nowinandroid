package com.starception.submission.prayer.sky

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The sun's apparent place, in degrees. */
public data class SolarPosition(
    val rightAscensionDeg: Double,
    val declinationDeg: Double,
    val apparentLongitudeDeg: Double,
)

/**
 * Mean obliquity of the ecliptic plus the largest nutation term — Meeus 22.2 with 25.8.
 *
 * [omegaDeg] is the longitude of the ascending node of the moon's orbit, which is
 * also wanted by the caller, so it is passed in rather than recomputed.
 */
internal fun obliquityDeg(t: Double, omegaDeg: Double): Double =
    23.439291 - 0.0130042 * t - 1.64e-7 * t * t + 5.04e-7 * t * t * t +
        0.00256 * cos(omegaDeg.degToRad())

/**
 * The sun's apparent right ascension and declination — Meeus ch. 25, low precision:
 * about 0.01 degrees between 1950 and 2050, which is a twentieth of a pixel on the
 * card this serves.
 *
 * This is deliberately *not*
 * [com.starception.submission.prayer.calculator.AstronomicalCalculator]. That class
 * computes right ascension inside `calculateEquationOfTime` and throws it away, has
 * no azimuth at all, and logs upwards of twenty lines per call through `SharedLog`,
 * which delegates straight to `android.util.Log` with no debug gate. The widget
 * evaluates the sun about fifty times per render, once a minute, forever.
 *
 * The two must not drift apart, so `SolarAgreementTest` pins this against the
 * calculator's declination across a year.
 */
public fun solarPosition(julianDay: Double): SolarPosition {
    val t = julianCenturies(julianDay)
    val meanLongitude = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
    val meanAnomaly = (357.52911 + 35999.05029 * t - 0.0001537 * t * t).degToRad()
    val equationOfCentre =
        (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(meanAnomaly) +
            (0.019993 - 0.000101 * t) * sin(2.0 * meanAnomaly) +
            0.000289 * sin(3.0 * meanAnomaly)
    // Ascending node of the moon's orbit: carries the largest nutation term. The flat
    // -0.00569 is aberration, which is near enough constant at this precision.
    val omega = 125.04 - 1934.136 * t
    val apparentLongitude = normalizeDegrees(
        meanLongitude + equationOfCentre - 0.00569 - 0.00478 * sin(omega.degToRad()),
    )
    val obliquity = obliquityDeg(t, omega).degToRad()
    val lambda = apparentLongitude.degToRad()
    return SolarPosition(
        rightAscensionDeg = normalizeDegrees(
            atan2(cos(obliquity) * sin(lambda), cos(lambda)).radToDeg(),
        ),
        declinationDeg = asin(sin(obliquity) * sin(lambda)).radToDeg(),
        apparentLongitudeDeg = apparentLongitude,
    )
}

/** Mean distance to the sun, in km. The 1.7% annual variation moves the moon's
 *  phase angle by under 0.02 degrees, so it is not worth a series of its own. */
internal const val SUN_DISTANCE_KM: Double = 149_598_000.0
