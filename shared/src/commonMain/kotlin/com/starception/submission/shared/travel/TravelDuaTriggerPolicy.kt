/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.travel

import com.starception.submission.config.TravelDuaSettings

/** Decides when continuous speed evidence is strong enough to play the Travel Dua. */
class TravelDuaTriggerPolicy {
    private var drivingStartedAt: Long? = null
    private var lastMovingAt: Long? = null
    private var lastPlayedAt: Long? = null
    private var playedForCurrentTrip = false

    fun update(speedMetersPerSecond: Double, nowMillis: Long, settings: TravelDuaSettings): Boolean {
        if (!settings.enabled) {
            resetTrip()
            return false
        }

        val gapMillis = settings.gapToleranceMillis
        val wasMovingAt = lastMovingAt
        if (speedMetersPerSecond < settings.drivingSpeedThresholdMps) {
            if (wasMovingAt != null && nowMillis - wasMovingAt > gapMillis) resetTrip()
            return false
        }

        if (wasMovingAt == null || nowMillis - wasMovingAt > gapMillis) {
            drivingStartedAt = nowMillis
            playedForCurrentTrip = false
        }
        lastMovingAt = nowMillis

        val startedAt = drivingStartedAt ?: nowMillis.also { drivingStartedAt = it }
        val delayReached = nowMillis - startedAt >= settings.playbackDelayMillis
        val cooldownReached = lastPlayedAt?.let { nowMillis - it >= settings.cooldownMillis } ?: true
        if (!playedForCurrentTrip && delayReached && cooldownReached) {
            playedForCurrentTrip = true
            lastPlayedAt = nowMillis
            return true
        }
        return false
    }

    fun resetTrip() {
        drivingStartedAt = null
        lastMovingAt = null
        playedForCurrentTrip = false
    }
}
