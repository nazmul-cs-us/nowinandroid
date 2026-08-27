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

package com.starception.submission.shared.location

/**
 * Where the device is, and what that place is called.
 *
 * [placeName] is best-effort: reverse geocoding needs the network and can fail
 * or be slow, and the prayer times do not depend on it. Coordinates are what
 * matter; the name is only shown.
 */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    /** Hours from UTC, as the calculator expects — e.g. 4.0 for Gulf Standard Time. */
    val timeZoneOffset: Double,
    val placeName: String,
)

/**
 * Resolves the device's current position.
 *
 * `expect` because there is no shared way to ask: iOS has Core Location behind a
 * permission prompt, Android has its own stack. Deliberately a single suspend
 * call rather than a stream — the prayer schedule only needs a position when it
 * computes, and a continuous feed would mean managing a subscription for no gain.
 */
expect class LocationProvider() {
    /**
     * The current position, or `null` if it cannot be determined — permission
     * denied, location services off, or no fix in reasonable time.
     *
     * Callers must have a fallback. Returning `null` rather than throwing keeps
     * "we don't know where you are" an ordinary outcome, because on a phone it is.
     */
    suspend fun current(): DeviceLocation?
}
