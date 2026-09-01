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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TravelDuaTriggerPolicyTest {
    private val settings = TravelDuaSettings(
        playbackDelaySeconds = 10,
        cooldownMinutes = 1,
        gapToleranceMinutes = 1,
        drivingSpeedThresholdKmh = 10,
    )

    @Test
    fun triggersOnceAfterContinuousDrivingDelay() {
        val policy = TravelDuaTriggerPolicy()

        assertFalse(policy.update(5.0, 0, settings))
        assertFalse(policy.update(5.0, 9_999, settings))
        assertTrue(policy.update(5.0, 10_000, settings))
        assertFalse(policy.update(5.0, 80_000, settings))
    }

    @Test
    fun longStopStartsANewTripAndHonorsCooldown() {
        val policy = TravelDuaTriggerPolicy()

        assertFalse(policy.update(5.0, 0, settings))
        assertTrue(policy.update(5.0, 10_000, settings))
        assertFalse(policy.update(0.0, 71_000, settings))
        assertFalse(policy.update(5.0, 72_000, settings))
        assertTrue(policy.update(5.0, 82_000, settings))
    }

    @Test
    fun disabledSettingClearsAccumulatedTrip() {
        val policy = TravelDuaTriggerPolicy()

        assertFalse(policy.update(5.0, 0, settings))
        assertFalse(policy.update(5.0, 9_000, settings.copy(enabled = false)))
        assertFalse(policy.update(5.0, 10_000, settings))
    }
}
