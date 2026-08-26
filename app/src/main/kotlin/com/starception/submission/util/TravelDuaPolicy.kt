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
}
