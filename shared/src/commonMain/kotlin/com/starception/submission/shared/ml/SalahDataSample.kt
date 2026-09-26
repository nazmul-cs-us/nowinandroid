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

/**
 * One 100ms sensor window (5 samples at 50Hz) plus derived features — the
 * shared mirror of the Android app's SalahDataSample, without the org.json
 * serialization (kept for the Kotlin Multiplatform hosts).
 */
data class SalahDataSample(
    val timestamp: Long,
    val sessionId: String,
    val posture: SalahPosture,

    /** Raw accelerometer data (5 samples at 50Hz = 100ms window). */
    val accelX: FloatArray,
    val accelY: FloatArray,
    val accelZ: FloatArray,

    /** Raw gyroscope data (5 samples at 50Hz = 100ms window). */
    val gyroX: FloatArray,
    val gyroY: FloatArray,
    val gyroZ: FloatArray,

    /** Derived features for quick validation. */
    val pitch: Float,
    val roll: Float,
    val accelMagnitude: Float,
    val gyroMagnitude: Float,
) {
    /**
     * JSONL export line (same shape as the Android exporter), built without
     * org.json so it works on every KMP target.
     */
    fun toJson(): String {
        fun arr(v: FloatArray) = "[" + v.joinToString(",") + "]"
        return "{" +
            "\"timestamp\":$timestamp," +
            "\"session_id\":\"$sessionId\"," +
            "\"posture\":\"${posture.name}\"," +
            "\"accelX\":${arr(accelX)},\"accelY\":${arr(accelY)},\"accelZ\":${arr(accelZ)}," +
            "\"gyroX\":${arr(gyroX)},\"gyroY\":${arr(gyroY)},\"gyroZ\":${arr(gyroZ)}," +
            "\"pitch\":$pitch,\"roll\":$roll," +
            "\"accelMagnitude\":$accelMagnitude,\"gyroMagnitude\":$gyroMagnitude" +
            "}"
    }
}
