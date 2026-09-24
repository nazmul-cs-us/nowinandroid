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

package com.starception.submission.prayer.sky

/**
 * Julian Day from Unix epoch seconds.
 *
 * 2440587.5 is the Julian Day of 1970-01-01T00:00:00Z.
 *
 * Takes a `Double` deliberately. Callers ask for fractional minutes, and for minutes
 * outside their own day: the widget's night runs from Isha, past midnight, to the
 * next Fajr, so both negative offsets and offsets beyond 1440 have to work.
 */
public fun julianDay(epochSeconds: Double): Double = 2440587.5 + epochSeconds / 86400.0

/** Julian centuries from J2000.0, the argument every series below is written in. */
public fun julianCenturies(julianDay: Double): Double = (julianDay - 2451545.0) / 36525.0

/**
 * Greenwich mean sidereal time, in degrees — Meeus, *Astronomical Algorithms* eq. 12.4.
 *
 * Good to about a tenth of an arcsecond this century. The equation of the equinoxes
 * (1.1 arcseconds at most) is left out; see the accuracy note on [SkySnapshot].
 */
public fun greenwichMeanSiderealTime(julianDay: Double): Double {
    val t = julianCenturies(julianDay)
    return normalizeDegrees(
        280.46061837 +
            360.98564736629 * (julianDay - 2451545.0) +
            0.000387933 * t * t -
            t * t * t / 38_710_000.0,
    )
}

/**
 * Local mean sidereal time, in degrees, for an east-positive longitude.
 *
 * This is the one quantity that decides where a star is, and it is why longitude has
 * to reach the widget at all: latitude alone fixes how high the sky turns, but not
 * how far round it has turned.
 */
public fun localSiderealTime(julianDay: Double, longitudeEast: Double): Double =
    normalizeDegrees(greenwichMeanSiderealTime(julianDay) + longitudeEast)
