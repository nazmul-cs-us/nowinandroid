package com.starception.submission.shared.qibla

/** Android keeps using its existing sensor stack rather than creating a competing one here. */
actual class HeadingProvider actual constructor() {
    actual fun start(onReading: (HeadingReading) -> Unit) {
        onReading(HeadingReading(unavailableReason = "Heading is provided by the Android app"))
    }

    actual fun stop() = Unit
}
