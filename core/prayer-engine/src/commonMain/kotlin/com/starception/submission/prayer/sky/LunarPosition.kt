package com.starception.submission.prayer.sky

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** The moon's geocentric place, in degrees, with its distance in km. */
public data class LunarPosition(
    val rightAscensionDeg: Double,
    val declinationDeg: Double,
    val distanceKm: Double,
)

/**
 * One periodic term: a coefficient and the multipliers of the four fundamental
 * arguments D, M, M' and F that form its argument.
 *
 * Stored as a flat `IntArray` of `[coeff, d, m, mp, f, ...]` rather than a list of
 * objects: this is walked on every widget render and an object per term would
 * allocate a few hundred times a minute for nothing.
 */
private val LONGITUDE_TERMS = intArrayOf(
    // coeff (1e-6 deg),  D,  M,  M',  F     — Meeus table 47.A, the sixteen largest
    6288774, 0, 0, 1, 0,
    1274027, 2, 0, -1, 0,
    658314, 2, 0, 0, 0,
    213618, 0, 0, 2, 0,
    -185116, 0, 1, 0, 0,
    -114332, 0, 0, 0, 2,
    58793, 2, 0, -2, 0,
    57066, 2, -1, -1, 0,
    53322, 2, 0, 1, 0,
    45758, 2, -1, 0, 0,
    -40923, 0, 1, -1, 0,
    -34720, 1, 0, 0, 0,
    -30383, 0, 1, 1, 0,
    15327, 2, 0, 0, -2,
    -12528, 0, 0, 1, 2,
    10980, 0, 0, 1, -2,
)

private val LATITUDE_TERMS = intArrayOf(
    // coeff (1e-6 deg),  D,  M,  M',  F     — Meeus table 47.B, the ten largest
    5128122, 0, 0, 0, 1,
    280602, 0, 0, 1, 1,
    277693, 0, 0, 1, -1,
    173237, 2, 0, 0, -1,
    55413, 2, 0, -1, 1,
    46271, 2, 0, -1, -1,
    32573, 2, 0, 0, 1,
    17198, 0, 0, 2, 1,
    9266, 2, 0, 1, -1,
    8822, 0, 0, 2, -1,
)

private val DISTANCE_TERMS = intArrayOf(
    // coeff (1e-3 km),  D,  M,  M',  F      — Meeus table 47.A radius column
    -20905355, 0, 0, 1, 0,
    -3699111, 2, 0, -1, 0,
    -2955968, 2, 0, 0, 0,
    -569925, 0, 0, 2, 0,
)

/**
 * The moon's geocentric right ascension, declination and distance — Meeus ch. 47,
 * abridged to the sixteen largest longitude terms, ten latitude and four distance.
 *
 * That truncation is worth about 0.03 degrees in longitude and 0.01 in latitude. The
 * full tables run to sixty terms each and buy roughly ten arcseconds — a fiftieth of
 * a pixel on the card this serves, for four times the arithmetic on every render.
 */
public fun lunarPosition(julianDay: Double): LunarPosition {
    val t = julianCenturies(julianDay)
    val t2 = t * t
    val t3 = t2 * t
    val t4 = t3 * t

    // Meeus 47.1 to 47.6.
    val meanLongitude = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 +
        t3 / 538841.0 - t4 / 65194000.0
    val elongation = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 +
        t3 / 545868.0 - t4 / 113065000.0
    val solarAnomaly = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
    val lunarAnomaly = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 +
        t3 / 69699.0 - t4 / 14712000.0
    val argumentOfLatitude = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 -
        t3 / 3526000.0 + t4 / 863310000.0

    // Terms involving the sun's anomaly vary with Earth's orbital eccentricity.
    val eccentricity = 1.0 - 0.002516 * t - 0.0000074 * t2

    fun sumSine(terms: IntArray): Double {
        var total = 0.0
        var i = 0
        while (i < terms.size) {
            val coefficient = terms[i]
            val m = terms[i + 2]
            val argument = terms[i + 1] * elongation +
                m * solarAnomaly +
                terms[i + 3] * lunarAnomaly +
                terms[i + 4] * argumentOfLatitude
            total += coefficient * eccentricity.pow(abs(m)) * sin(argument.degToRad())
            i += 5
        }
        return total
    }

    var distanceSum = 0.0
    var i = 0
    while (i < DISTANCE_TERMS.size) {
        val m = DISTANCE_TERMS[i + 2]
        val argument = DISTANCE_TERMS[i + 1] * elongation +
            m * solarAnomaly +
            DISTANCE_TERMS[i + 3] * lunarAnomaly +
            DISTANCE_TERMS[i + 4] * argumentOfLatitude
        distanceSum += DISTANCE_TERMS[i] * eccentricity.pow(abs(m)) * cos(argument.degToRad())
        i += 5
    }

    val eclipticLongitude = normalizeDegrees(meanLongitude + sumSine(LONGITUDE_TERMS) / 1e6)
    val eclipticLatitude = sumSine(LATITUDE_TERMS) / 1e6
    val distanceKm = 385000.56 + distanceSum / 1e3

    // Ecliptic to equatorial — Meeus 13.3 and 13.4, on the same obliquity the sun uses.
    val omega = 125.04 - 1934.136 * t
    val obliquity = obliquityDeg(t, omega).degToRad()
    val lambda = eclipticLongitude.degToRad()
    val beta = eclipticLatitude.degToRad()
    return LunarPosition(
        rightAscensionDeg = normalizeDegrees(
            atan2(
                sin(lambda) * cos(obliquity) - kotlin.math.tan(beta) * sin(obliquity),
                cos(lambda),
            ).radToDeg(),
        ),
        declinationDeg = asin(
            sin(beta) * cos(obliquity) + cos(beta) * sin(obliquity) * sin(lambda),
        ).radToDeg(),
        distanceKm = distanceKm,
    )
}

/** How the moon is lit, and which way up. */
public data class LunarPhase(
    /** Illuminated fraction of the disc, 0 at new and 1 at full. */
    val illuminatedFraction: Double,
    /** Position angle of the bright limb, from celestial north, counter-clockwise. */
    val brightLimbAngleDeg: Double,
    /** True while the moon is filling — the bright limb leads rather than trails. */
    val waxing: Boolean,
)

/**
 * Illuminated fraction and the tilt of the bright limb — Meeus ch. 48.
 *
 * The limb angle is the detail that makes a drawn moon look observed rather than
 * decorative: it is measured from celestial north, so a caller drawing on a screen
 * must subtract [parallacticAngleDeg] to get a rotation about the zenith.
 */
public fun lunarPhase(sun: SolarPosition, moon: LunarPosition): LunarPhase {
    val sunRa = sun.rightAscensionDeg.degToRad()
    val sunDec = sun.declinationDeg.degToRad()
    val moonRa = moon.rightAscensionDeg.degToRad()
    val moonDec = moon.declinationDeg.degToRad()
    val deltaRa = sunRa - moonRa

    // Geocentric elongation of the moon from the sun.
    val cosElongation = sin(sunDec) * sin(moonDec) + cos(sunDec) * cos(moonDec) * cos(deltaRa)
    val elongation = kotlin.math.acos(cosElongation.coerceIn(-1.0, 1.0))

    // Phase angle: the sun-moon-Earth angle, from the plane triangle.
    val phaseAngle = atan2(
        SUN_DISTANCE_KM * sin(elongation),
        moon.distanceKm - SUN_DISTANCE_KM * cosElongation,
    )
    val brightLimb = atan2(
        cos(sunDec) * sin(deltaRa),
        sin(sunDec) * cos(moonDec) - cos(sunDec) * sin(moonDec) * cos(deltaRa),
    ).radToDeg()
    return LunarPhase(
        illuminatedFraction = (1.0 + cos(phaseAngle)) / 2.0,
        brightLimbAngleDeg = normalizeDegrees(brightLimb),
        // The bright limb points west of north while waxing and east of north while
        // waning, which is the sign of sin(deltaRa) once the wrap is taken out.
        waxing = normalizeSignedDegrees(brightLimb) < 0.0,
    )
}

/**
 * The moon's horizontal parallax correction, in degrees, to be *subtracted* from a
 * geocentric altitude.
 *
 * Unlike the sun's, this one shows: it reaches a full degree, which is four to six
 * pixels on the card, and it is the difference between a moon that rises when it
 * rises and one that does not. The matching azimuth term is second order and
 * suppressed by `cos(altitude)`, so it is left out.
 */
public fun lunarParallaxDeg(distanceKm: Double, geocentricAltitudeDeg: Double): Double {
    val sinParallax = 6378.14 / distanceKm
    return asin(sinParallax).radToDeg() * cos(geocentricAltitudeDeg.degToRad())
}
