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

package com.starception.submission.shared.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual class HeadingProvider actual constructor() {
    private val manager = CLLocationManager()
    private var delegate: NSObject? = null

    actual fun start(onReading: (HeadingReading) -> Unit) {
        if (!CLLocationManager.headingAvailable()) {
            onReading(HeadingReading(unavailableReason = "Compass heading is unavailable on this device"))
            return
        }
        val handler = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
                if (didUpdateHeading.headingAccuracy < 0.0) {
                    onReading(HeadingReading(unavailableReason = "Compass needs calibration. Move your phone in a figure-8."))
                    return
                }
                val degrees = didUpdateHeading.trueHeading.takeIf { it.isFinite() && it >= 0.0 }
                    ?: didUpdateHeading.magneticHeading
                if (!degrees.isFinite() || degrees < 0.0) return
                onReading(
                    HeadingReading(
                        headingDegrees = degrees,
                        accuracyDegrees = didUpdateHeading.headingAccuracy.takeIf { it >= 0.0 },
                    ),
                )
            }

            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                // Location is consumed by Core Location to correct magnetic north.
            }

            override fun locationManagerShouldDisplayHeadingCalibration(
                manager: CLLocationManager,
            ): Boolean = true

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                onReading(HeadingReading(unavailableReason = didFailWithError.localizedDescription))
            }
        }
        delegate = handler
        manager.delegate = handler
        // Core Location needs location updates on this manager to report true
        // north, which is the reference used by the geographic Qibla bearing.
        // Reuse the app's existing permission; do not request another prompt.
        val status = CLLocationManager.authorizationStatus()
        if (status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways) {
            manager.desiredAccuracy = kCLLocationAccuracyKilometer
            manager.startUpdatingLocation()
        }
        manager.headingFilter = 1.0
        manager.startUpdatingHeading()
    }

    actual fun stop() {
        manager.stopUpdatingHeading()
        manager.stopUpdatingLocation()
        manager.delegate = null
        delegate = null
    }
}
