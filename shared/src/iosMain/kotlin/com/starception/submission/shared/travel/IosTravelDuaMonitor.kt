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
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/** Foreground, best-effort driving evidence for iOS. */
@OptIn(ExperimentalForeignApi::class)
internal class IosTravelDuaMonitor(private val onTrigger: () -> Unit) {
    private val manager = CLLocationManager()
    private val policy = TravelDuaTriggerPolicy()
    private var settings = TravelDuaSettings()
    private var running = false

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(
            manager: CLLocationManager,
            didUpdateLocations: List<*>,
        ) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            if (location.speed < 0.0) return
            if (policy.update(location.speed, nowMillis(), settings)) onTrigger()
        }

        override fun locationManager(
            manager: CLLocationManager,
            didFailWithError: NSError,
        ) = Unit

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val status = manager.authorizationStatus
            if (running && (status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                    status == kCLAuthorizationStatusAuthorizedAlways)
            ) {
                manager.startUpdatingLocation()
            }
        }
    }

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 20.0
    }

    fun update(settings: TravelDuaSettings) {
        this.settings = settings
        if (settings.enabled) start() else stop()
    }

    fun start() {
        if (running) return
        running = true
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    fun stop() {
        running = false
        manager.stopUpdatingLocation()
        policy.resetTrip()
    }

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
}
