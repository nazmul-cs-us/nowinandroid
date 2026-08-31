package com.starception.submission.shared.qibla

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
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
                val degrees = didUpdateHeading.trueHeading.takeIf { it >= 0.0 }
                    ?: didUpdateHeading.magneticHeading
                onReading(
                    HeadingReading(
                        headingDegrees = degrees,
                        accuracyDegrees = didUpdateHeading.headingAccuracy.takeIf { it >= 0.0 },
                    ),
                )
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
        manager.headingFilter = 1.0
        manager.startUpdatingHeading()
    }

    actual fun stop() {
        manager.stopUpdatingHeading()
        manager.delegate = null
        delegate = null
    }
}
