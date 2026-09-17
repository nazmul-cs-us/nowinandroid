package com.starception.submission.prayer.sky

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Where something stands in the observer's sky. Azimuth is from north, eastward. */
public data class AltAz(val altitudeDeg: Double, val azimuthDeg: Double)

/**
 * Equatorial to horizontal — Meeus 13.5 and 13.6.
 *
 * Meeus measures azimuth westward from *south*; this returns the compass convention,
 * eastward from *north*, because that is what both a reader and a compass expect.
 */
public fun equatorialToHorizontal(
    rightAscensionDeg: Double,
    declinationDeg: Double,
    latitudeDeg: Double,
    localSiderealTimeDeg: Double,
): AltAz {
    val hourAngle = normalizeSignedDegrees(localSiderealTimeDeg - rightAscensionDeg).degToRad()
    val declination = declinationDeg.degToRad()
    val latitude = latitudeDeg.degToRad()
    val altitude = asin(
        sin(latitude) * sin(declination) +
            cos(latitude) * cos(declination) * cos(hourAngle),
    )
    val azimuthFromSouth = atan2(
        sin(hourAngle),
        cos(hourAngle) * sin(latitude) - tan(declination) * cos(latitude),
    )
    return AltAz(
        altitudeDeg = altitude.radToDeg(),
        azimuthDeg = normalizeDegrees(azimuthFromSouth.radToDeg() + 180.0),
    )
}

/**
 * Atmospheric refraction, in degrees, to be *added* to a true altitude — Bennett,
 * Meeus 16.4.
 *
 * Worth applying to the sun and the moon, whose discs are lifted by more than their
 * own width at the horizon — which is where the prayer times are defined. Not worth
 * applying to stars, where it is under a pixel and would cost a tangent each.
 */
public fun refractionDeg(trueAltitudeDeg: Double): Double {
    // Bennett's formula diverges below the horizon; there is nothing to lift there.
    if (trueAltitudeDeg < -1.0) return 0.0
    val arcMinutes = 1.02 / tan((trueAltitudeDeg + 10.3 / (trueAltitudeDeg + 5.11)).degToRad())
    return arcMinutes / 60.0
}

/**
 * Parallactic angle, in degrees — Meeus 14.1.
 *
 * The angle at the body between celestial north and the observer's zenith. The moon's
 * bright limb is measured from celestial north, but a screen's up is the zenith, so
 * this is what turns one into the other — and it is why a waxing crescent in the
 * tropics is drawn as a smile rather than the northern-hemisphere D.
 */
public fun parallacticAngleDeg(
    rightAscensionDeg: Double,
    declinationDeg: Double,
    latitudeDeg: Double,
    localSiderealTimeDeg: Double,
): Double {
    val hourAngle = normalizeSignedDegrees(localSiderealTimeDeg - rightAscensionDeg).degToRad()
    val declination = declinationDeg.degToRad()
    val latitude = latitudeDeg.degToRad()
    return atan2(
        sin(hourAngle),
        tan(latitude) * cos(declination) - sin(declination) * cos(hourAngle),
    ).radToDeg()
}
