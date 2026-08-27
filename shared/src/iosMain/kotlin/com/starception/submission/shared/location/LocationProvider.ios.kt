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

import com.starception.submission.core.logging.SharedLog
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLPlacemark
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSError
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.darwin.NSObject

private const val TAG = "LocationProvider"
private const val FIX_TIMEOUT_MS = 8_000L
private const val GEOCODE_TIMEOUT_MS = 5_000L

/**
 * Core Location implementation.
 *
 * The delegate is held in a property, not passed inline: `CLLocationManager`
 * keeps only a weak reference to it, so a delegate that exists solely as an
 * argument is collected immediately and no callback ever arrives.
 */
@OptIn(ExperimentalForeignApi::class)
actual class LocationProvider actual constructor() {

    private val manager = CLLocationManager()
    private var delegate: NSObject? = null

    actual suspend fun current(): DeviceLocation? {
        val fix = withTimeoutOrNull(FIX_TIMEOUT_MS) { awaitFix() }
        if (fix == null) {
            SharedLog.w(TAG, "No location fix within ${FIX_TIMEOUT_MS}ms")
            return null
        }

        val (latitude, longitude) = fix
        val place = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { describe(latitude, longitude) }

        return DeviceLocation(
            latitude = latitude,
            longitude = longitude,
            // The offset must be the one *at those coordinates*, not the device's.
            // They differ whenever the phone's clock has not caught up with where
            // it is, and using the device's silently shifts every prayer time by
            // the difference — the schedule stays internally consistent, so it
            // looks plausible rather than broken.
            //
            // Falls back to the device timezone only if geocoding failed, which
            // is better than nothing but carries exactly that risk.
            timeZoneOffset = place?.offsetHours
                ?: (NSTimeZone.localTimeZone.secondsFromGMT / 3600.0),
            placeName = place?.name.orEmpty(),
            countryCode = place?.countryCode.orEmpty(),
        )
    }

    private suspend fun awaitFix(): Pair<Double, Double>? =
        suspendCancellableCoroutine { continuation ->
            val handler = object : NSObject(), CLLocationManagerDelegateProtocol {
                private var settled = false

                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>,
                ) {
                    if (settled) return
                    val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                    settled = true
                    manager.stopUpdatingLocation()
                    location.coordinate.useContents {
                        continuation.resume(latitude to longitude)
                    }
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: NSError,
                ) {
                    if (settled) return
                    settled = true
                    manager.stopUpdatingLocation()
                    SharedLog.w(TAG, "Location failed: ${didFailWithError.localizedDescription}")
                    continuation.resume(null)
                }

                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    val status = manager.authorizationStatus
                    if (status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                        status == kCLAuthorizationStatusAuthorizedAlways
                    ) {
                        manager.startUpdatingLocation()
                    }
                }
            }

            delegate = handler
            manager.delegate = handler
            manager.requestWhenInUseAuthorization()
            manager.startUpdatingLocation()

            continuation.invokeOnCancellation { manager.stopUpdatingLocation() }
        }

    private class Place(val name: String, val offsetHours: Double?, val countryCode: String)

    /**
     * Reverse geocodes for the place name *and* its timezone.
     *
     * The timezone is the reason this is not merely cosmetic: it is the only way
     * to learn the offset where the user actually is, rather than where their
     * phone thinks it is.
     */
    private suspend fun describe(latitude: Double, longitude: Double): Place =
        suspendCancellableCoroutine { continuation ->
            CLGeocoder().reverseGeocodeLocation(
                CLLocation(latitude = latitude, longitude = longitude),
            ) { placemarks, _ ->
                val placemark = placemarks?.firstOrNull() as? CLPlacemark
                // Neighbourhood reads better than the city alone, matching how the
                // Android app names the location, but either alone is acceptable.
                val name = listOfNotNull(
                    placemark?.subLocality ?: placemark?.locality,
                    placemark?.administrativeArea ?: placemark?.country,
                ).distinct().joinToString(", ")
                // Seconds, and not every offset is a whole hour: India is +5.5,
                // Nepal +5.75. secondsFromGMT already accounts for daylight saving.
                val offset = placemark?.timeZone?.secondsFromGMT?.let { it / 3600.0 }
                continuation.resume(
                    Place(name, offset, placemark?.ISOcountryCode.orEmpty()),
                )
            }
        }
}
