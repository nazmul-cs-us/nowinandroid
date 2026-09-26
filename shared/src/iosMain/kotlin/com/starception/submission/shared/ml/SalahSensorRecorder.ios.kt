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

package com.starception.submission.shared.ml

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private const val SAMPLE_HZ = 50.0
private const val WINDOW_SIZE = 5

/**
 * Streams 50Hz sensor windows through CoreMotion's accelerometer and gyroscope
 * (50Hz updates), assembling 5-sample / 100ms [SalahDataSample] windows —
 * the same cadence as the Android SalahDataCollectionService.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SalahSensorRecorder actual constructor() {
    private val motion = CMMotionManager()

    private var sessionId: String = ""
    private var onSample: ((SalahDataSample) -> Unit)? = null

    private val ax = FloatArray(WINDOW_SIZE)
    private val ay = FloatArray(WINDOW_SIZE)
    private val az = FloatArray(WINDOW_SIZE)
    private val gx = FloatArray(WINDOW_SIZE)
    private val gy = FloatArray(WINDOW_SIZE)
    private val gz = FloatArray(WINDOW_SIZE)
    private var windowFill = 0

    private var lastAccelX = 0.0
    private var lastAccelY = 0.0
    private var lastAccelZ = 0.0
    private var lastGyroX = 0.0
    private var lastGyroY = 0.0
    private var lastGyroZ = 0.0

    actual fun start(sessionId: String, onSample: (SalahDataSample) -> Unit): Boolean {
        // TODO: CValue<CMAcceleration> field interop — the CoreMotion struct
        // accessors need a cinterop pass; returns unavailable until wired.
        stop()
        this.sessionId = sessionId
        this.onSample = onSample
        return false
        stop()
        this.sessionId = sessionId
        this.onSample = onSample
        windowFill = 0
        val interval = 1.0 / SAMPLE_HZ
        return false
    }

    private fun maybeEmitWindow() {
        if (windowFill < WINDOW_SIZE) {
            val i = windowFill
            ax[i] = lastAccelX.toFloat()
            ay[i] = lastAccelY.toFloat()
            az[i] = lastAccelZ.toFloat()
            // CoreMotion gyroscope is rad/s; Android's SensorManager reports
            // rad/s too, so both feed the model identically.
            gx[i] = lastGyroX.toFloat()
            gy[i] = lastGyroY.toFloat()
            gz[i] = lastGyroZ.toFloat()
            windowFill++
            if (windowFill < WINDOW_SIZE) return
        }

        // Window full — emit and start the next one.
        val pitch = (atan2(lastAccelY, lastAccelZ) * 180.0 / PI).toFloat()
        val roll = (atan2(lastAccelX, lastAccelZ) * 180.0 / PI).toFloat()
        val accelMag = sqrt(lastAccelX * lastAccelX + lastAccelY * lastAccelY + lastAccelZ * lastAccelZ).toFloat()
        val gyroMag = sqrt(lastGyroX * lastGyroX + lastGyroY * lastGyroY + lastGyroZ * lastGyroZ).toFloat()

        val sample = SalahDataSample(
            timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong(),
            sessionId = sessionId,
            // The shared recorder captures raw windows; classification happens
            // in the detection engine, not from a labeled posture.
            posture = SalahPosture.SUJUD,
            accelX = ax.copyOf(),
            accelY = ay.copyOf(),
            accelZ = az.copyOf(),
            gyroX = gx.copyOf(),
            gyroY = gy.copyOf(),
            gyroZ = gz.copyOf(),
            pitch = pitch,
            roll = roll,
            accelMagnitude = accelMag,
            gyroMagnitude = gyroMag,
        )
        onSample?.invoke(sample)
        windowFill = 0
    }

    actual fun stop() {
        runCatching { if (motion.isAccelerometerActive()) motion.stopAccelerometerUpdates() }
        runCatching { if (motion.isGyroActive()) motion.stopGyroUpdates() }
        onSample = null
        windowFill = 0
    }
}
