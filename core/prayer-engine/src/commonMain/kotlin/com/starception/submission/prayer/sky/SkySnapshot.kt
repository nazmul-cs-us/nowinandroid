package com.starception.submission.prayer.sky

/**
 * Everything a drawing needs about the sky at one instant from one place.
 *
 * ## How accurate this is, and why it stops there
 *
 * The widget card this serves is under a thousand pixels wide and shows the whole
 * sky: roughly a quarter of a degree per pixel by day, a sixth by night. **A tenth
 * of a degree is the visibility floor** — anything finer is discarded by the cast to
 * an integer pixel.
 *
 * So the models here are chosen to land near 0.05 degrees and stop: Meeus's
 * low-precision solar series (0.01), his lunar series abridged to sixteen terms
 * (0.03), mean sidereal time without the equation of the equinoxes (1.1 arcseconds),
 * and no nutation in longitude, no aberration beyond the sun's constant term, no
 * proper motion, and no correction for ΔT — seventy seconds of which moves the moon
 * by 0.011 degrees, the fastest thing in the sky.
 *
 * The one refinement that *is* kept is the moon's parallax, at up to a full degree.
 * It is several pixels, and it is what makes moonrise happen when it happens.
 */
public data class SkySnapshot(
    val localSiderealTimeDeg: Double,
    val sun: SolarPosition,
    /** The sun where it appears, refraction included. */
    val sunAltAz: AltAz,
    val moon: LunarPosition,
    /** The moon where it appears: refraction and parallax both applied. */
    val moonAltAz: AltAz,
    val moonPhase: LunarPhase,
    /**
     * The bright limb's tilt as a rotation on screen, clockwise from up.
     *
     * [LunarPhase.brightLimbAngleDeg] is measured from celestial north; a screen's up
     * is the zenith. The difference between the two is the parallactic angle.
     */
    val moonBrightLimbScreenDeg: Double,
)

/**
 * The whole sky at one instant — one call, so a renderer does not evaluate the same
 * series three times over.
 */
public fun skySnapshotAt(
    julianDay: Double,
    latitudeDeg: Double,
    longitudeEastDeg: Double,
): SkySnapshot {
    val lst = localSiderealTime(julianDay, longitudeEastDeg)
    val sun = solarPosition(julianDay)
    val moon = lunarPosition(julianDay)

    val sunGeometric = equatorialToHorizontal(
        sun.rightAscensionDeg, sun.declinationDeg, latitudeDeg, lst,
    )
    val moonGeometric = equatorialToHorizontal(
        moon.rightAscensionDeg, moon.declinationDeg, latitudeDeg, lst,
    )
    val moonTopocentric = moonGeometric.altitudeDeg -
        lunarParallaxDeg(moon.distanceKm, moonGeometric.altitudeDeg)

    val parallactic = parallacticAngleDeg(
        moon.rightAscensionDeg, moon.declinationDeg, latitudeDeg, lst,
    )
    val phase = lunarPhase(sun, moon)

    return SkySnapshot(
        localSiderealTimeDeg = lst,
        sun = sun,
        sunAltAz = sunGeometric.copy(
            altitudeDeg = sunGeometric.altitudeDeg + refractionDeg(sunGeometric.altitudeDeg),
        ),
        moon = moon,
        moonAltAz = moonGeometric.copy(
            altitudeDeg = moonTopocentric + refractionDeg(moonTopocentric),
        ),
        moonPhase = phase,
        moonBrightLimbScreenDeg = normalizeSignedDegrees(
            phase.brightLimbAngleDeg - parallactic,
        ),
    )
}

/**
 * The sun's apparent altitude alone.
 *
 * The day chart samples this a few dozen times across the card to plot the sun's
 * path, and has no use for the moon or the phase on any of those samples.
 */
public fun sunAltitudeAt(
    julianDay: Double,
    latitudeDeg: Double,
    longitudeEastDeg: Double,
): Double {
    val sun = solarPosition(julianDay)
    val geometric = equatorialToHorizontal(
        sun.rightAscensionDeg,
        sun.declinationDeg,
        latitudeDeg,
        localSiderealTime(julianDay, longitudeEastDeg),
    )
    return geometric.altitudeDeg + refractionDeg(geometric.altitudeDeg)
}
