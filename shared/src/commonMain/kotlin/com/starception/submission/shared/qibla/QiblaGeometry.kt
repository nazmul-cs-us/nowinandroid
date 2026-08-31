/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val KAABA_LATITUDE = 21.4225
private const val KAABA_LONGITUDE = 39.8262

/** Great-circle initial bearing from a coordinate to the Kaaba, clockwise from north. */
fun qiblaBearing(latitude: Double, longitude: Double): Double {
    val userLatitude = latitude.toRadians()
    val kaabaLatitude = KAABA_LATITUDE.toRadians()
    val longitudeDelta = (KAABA_LONGITUDE - longitude).toRadians()
    val y = sin(longitudeDelta) * cos(kaabaLatitude)
    val x = cos(userLatitude) * sin(kaabaLatitude) -
        sin(userLatitude) * cos(kaabaLatitude) * cos(longitudeDelta)
    return normalizeDegrees(atan2(y, x) * 180.0 / kotlin.math.PI)
}

/** Signed shortest turn, where positive is clockwise/right and negative is left. */
fun relativeQiblaTurn(qibla: Double, heading: Double): Double =
    ((qibla - heading + 540.0) % 360.0) - 180.0

fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

fun cardinalDirection(bearing: Double): String = when (normalizeDegrees(bearing).toInt()) {
    in 23..67 -> "North-east"
    in 68..112 -> "East"
    in 113..157 -> "South-east"
    in 158..202 -> "South"
    in 203..247 -> "South-west"
    in 248..292 -> "West"
    in 293..337 -> "North-west"
    else -> "North"
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0
