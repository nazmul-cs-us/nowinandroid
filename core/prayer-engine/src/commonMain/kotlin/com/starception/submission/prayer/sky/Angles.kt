package com.starception.submission.prayer.sky

import kotlin.math.PI
import kotlin.math.floor

/**
 * Degree/radian conversion for the sky maths.
 *
 * `kotlin.math` has no `toRadians`/`toDegrees` and `java.lang.Math` does not exist
 * on Kotlin/Native, so they are spelled out here exactly as [com.starception.submission.prayer.calculator.AstronomicalCalculator]
 * spells them — `angdeg / 180.0 * PI`, not an algebraically equivalent rearrangement.
 * Floating-point multiplication is not associative and the two files are checked
 * against each other, so they must round identically.
 */
internal fun Double.degToRad(): Double = this / 180.0 * PI

internal fun Double.radToDeg(): Double = this * 180.0 / PI

/** Wraps an angle into `0 until 360`. */
internal fun normalizeDegrees(degrees: Double): Double =
    degrees - 360.0 * floor(degrees / 360.0)

/**
 * Wraps an angle into `-180..180`.
 *
 * Hour angles and bearings are compared and interpolated, and both go wrong at the
 * wrap point unless they are signed: an hour angle of 359 degrees is one degree
 * before transit, not most of a day after it.
 */
internal fun normalizeSignedDegrees(degrees: Double): Double {
    val wrapped = normalizeDegrees(degrees)
    return if (wrapped > 180.0) wrapped - 360.0 else wrapped
}
