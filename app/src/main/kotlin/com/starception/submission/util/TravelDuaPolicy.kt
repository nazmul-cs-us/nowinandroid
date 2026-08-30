/*
 * Copyright 2024 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.util

/** Pure policy checks shared by Travel Dua tracking and its wake-up alarm. */
internal object TravelDuaPolicy {
    // A real journey produces fresh GPS speed samples continuously. Keeping this shorter
    // than the default 60-second playback delay rejects a one-off speed spike at detection.
    const val DRIVING_EVIDENCE_MAX_AGE_MILLIS = 45_000L

    // A foreground detector receives GPS roughly once per second. A reliable
    // near-zero sample newer than the last driving-speed sample is direct
    // evidence that a cached Activity Recognition result is wrong.
    const val ZERO_SPEED_EVIDENCE_MAX_AGE_MILLIS = 30_000L

    // Activity Transition ENTER is delivered by Google Play services and remains active
    // until its matching EXIT. Bound the fallback as protection against a missed EXIT.
    // This covers the configurable driving delay (up to 3 minutes) plus alarm delivery lag.
    const val GOOGLE_DRIVING_EVIDENCE_MAX_AGE_MILLIS = 5 * 60_000L

    fun isWithinTripGap(
        drivingStopTimeMillis: Long,
        nowMillis: Long,
        gapToleranceMillis: Long,
    ): Boolean {
        if (drivingStopTimeMillis <= 0L || nowMillis < drivingStopTimeMillis) return false
        return nowMillis - drivingStopTimeMillis < gapToleranceMillis
    }

    fun hasRecentDrivingEvidence(
        nowElapsedMillis: Long,
        lastEvidenceElapsedMillis: Long,
        maxAgeMillis: Long = DRIVING_EVIDENCE_MAX_AGE_MILLIS,
    ): Boolean {
        if (lastEvidenceElapsedMillis <= 0L || nowElapsedMillis < lastEvidenceElapsedMillis) {
            return false
        }
        return nowElapsedMillis - lastEvidenceElapsedMillis <= maxAgeMillis
    }

    fun hasActiveGoogleDrivingEvidence(
        nowElapsedMillis: Long,
        googleDrivingConfirmed: Boolean,
        confirmationElapsedMillis: Long,
        maxAgeMillis: Long = GOOGLE_DRIVING_EVIDENCE_MAX_AGE_MILLIS,
    ): Boolean {
        return googleDrivingConfirmed && hasRecentDrivingEvidence(
            nowElapsedMillis = nowElapsedMillis,
            lastEvidenceElapsedMillis = confirmationElapsedMillis,
            maxAgeMillis = maxAgeMillis,
        )
    }

    fun shouldAllowTravelDuaPlayback(
        nowElapsedMillis: Long,
        lastDrivingSpeedElapsedMillis: Long,
        lastZeroSpeedElapsedMillis: Long,
        googleDrivingConfirmed: Boolean,
        googleConfirmationElapsedMillis: Long,
    ): Boolean {
        val hasRecentDrivingSpeed = hasRecentDrivingEvidence(
            nowElapsedMillis = nowElapsedMillis,
            lastEvidenceElapsedMillis = lastDrivingSpeedElapsedMillis,
        )
        val hasRecentZeroSpeed = hasRecentDrivingEvidence(
            nowElapsedMillis = nowElapsedMillis,
            lastEvidenceElapsedMillis = lastZeroSpeedElapsedMillis,
            maxAgeMillis = ZERO_SPEED_EVIDENCE_MAX_AGE_MILLIS,
        )

        // When GPS has supplied both kinds of evidence, the newest reliable
        // sample wins. This lets a journey resume after a stop while preventing
        // an old speed sample from overriding the phone currently sitting still.
        if (hasRecentDrivingSpeed || hasRecentZeroSpeed) {
            return hasRecentDrivingSpeed &&
                (!hasRecentZeroSpeed ||
                    lastDrivingSpeedElapsedMillis > lastZeroSpeedElapsedMillis)
        }

        return hasActiveGoogleDrivingEvidence(
            nowElapsedMillis = nowElapsedMillis,
            googleDrivingConfirmed = googleDrivingConfirmed,
            confirmationElapsedMillis = googleConfirmationElapsedMillis,
        )
    }
}
