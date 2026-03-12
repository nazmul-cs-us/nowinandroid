package com.starception.submission.ml

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single labeled sensor data sample for salah posture training.
 * Represents a 100ms window of accelerometer + gyroscope data
 * collected at 50Hz (5 samples per window).
 */
data class SalahDataSample(
    val timestamp: Long,
    val sessionId: String,
    val posture: SalahPosture,

    // Raw accelerometer data (5 samples at 50Hz = 100ms window)
    val accelX: FloatArray,
    val accelY: FloatArray,
    val accelZ: FloatArray,

    // Raw gyroscope data (5 samples at 50Hz = 100ms window)
    val gyroX: FloatArray,
    val gyroY: FloatArray,
    val gyroZ: FloatArray,

    // Computed features for quick validation
    val pitch: Float,
    val roll: Float,
    val accelMagnitude: Float,
    val gyroMagnitude: Float
) {
    /**
     * Serialize to JSON for JSONL export.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("session_id", sessionId)
            put("posture", posture.name)
            put("accel_x", JSONArray(accelX.toList()))
            put("accel_y", JSONArray(accelY.toList()))
            put("accel_z", JSONArray(accelZ.toList()))
            put("gyro_x", JSONArray(gyroX.toList()))
            put("gyro_y", JSONArray(gyroY.toList()))
            put("gyro_z", JSONArray(gyroZ.toList()))
            put("pitch", pitch.toDouble())
            put("roll", roll.toDouble())
            put("accel_magnitude", accelMagnitude.toDouble())
            put("gyro_magnitude", gyroMagnitude.toDouble())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SalahDataSample) return false
        return timestamp == other.timestamp && sessionId == other.sessionId && posture == other.posture
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + posture.hashCode()
        return result
    }
}
