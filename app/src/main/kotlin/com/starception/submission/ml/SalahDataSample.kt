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
        return timestamp == other.timestamp &&
            sessionId == other.sessionId &&
            posture == other.posture &&
            accelX.contentEquals(other.accelX) &&
            accelY.contentEquals(other.accelY) &&
            accelZ.contentEquals(other.accelZ) &&
            gyroX.contentEquals(other.gyroX) &&
            gyroY.contentEquals(other.gyroY) &&
            gyroZ.contentEquals(other.gyroZ) &&
            pitch == other.pitch &&
            roll == other.roll &&
            accelMagnitude == other.accelMagnitude &&
            gyroMagnitude == other.gyroMagnitude
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + posture.hashCode()
        result = 31 * result + accelX.contentHashCode()
        result = 31 * result + accelY.contentHashCode()
        result = 31 * result + accelZ.contentHashCode()
        result = 31 * result + gyroX.contentHashCode()
        result = 31 * result + gyroY.contentHashCode()
        result = 31 * result + gyroZ.contentHashCode()
        result = 31 * result + pitch.hashCode()
        result = 31 * result + roll.hashCode()
        result = 31 * result + accelMagnitude.hashCode()
        result = 31 * result + gyroMagnitude.hashCode()
        return result
    }

    /** Mean of the 5 accel X samples */
    val meanAccelX: Float get() = accelX.average().toFloat()
    val meanAccelY: Float get() = accelY.average().toFloat()
    val meanAccelZ: Float get() = accelZ.average().toFloat()
    val meanGyroX: Float get() = gyroX.average().toFloat()
    val meanGyroY: Float get() = gyroY.average().toFloat()
    val meanGyroZ: Float get() = gyroZ.average().toFloat()

    fun getAxisValue(axis: String): Float = when (axis) {
        "pitch" -> pitch
        "roll" -> roll
        "ax" -> meanAccelX
        "ay" -> meanAccelY
        "az" -> meanAccelZ
        "am" -> accelMagnitude
        "gx" -> meanGyroX
        "gy" -> meanGyroY
        "gz" -> meanGyroZ
        "gm" -> gyroMagnitude
        else -> 0f
    }

    companion object {
        fun fromJson(json: JSONObject): SalahDataSample {
            return SalahDataSample(
                timestamp = json.getLong("timestamp"),
                sessionId = json.getString("session_id"),
                posture = SalahPosture.valueOf(json.getString("posture")),
                accelX = json.getJSONArray("accel_x").toFloatArray(),
                accelY = json.getJSONArray("accel_y").toFloatArray(),
                accelZ = json.getJSONArray("accel_z").toFloatArray(),
                gyroX = json.getJSONArray("gyro_x").toFloatArray(),
                gyroY = json.getJSONArray("gyro_y").toFloatArray(),
                gyroZ = json.getJSONArray("gyro_z").toFloatArray(),
                pitch = json.getDouble("pitch").toFloat(),
                roll = json.getDouble("roll").toFloat(),
                accelMagnitude = json.getDouble("accel_magnitude").toFloat(),
                gyroMagnitude = json.optDouble("gyro_magnitude", 0.0).toFloat()
            )
        }

        private fun JSONArray.toFloatArray(): FloatArray {
            return FloatArray(length()) { i -> getDouble(i).toFloat() }
        }
    }
}
