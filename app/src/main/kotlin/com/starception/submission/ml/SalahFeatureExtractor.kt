package com.starception.submission.ml

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Extracts 30 statistical features from a single 100ms sensor window,
 * matching the Python training pipeline (feature_engineering.py) exactly.
 *
 * Feature layout (30 features):
 *  0-2:   accel_mean_x, accel_mean_y, accel_mean_z
 *  3-5:   accel_std_x, accel_std_y, accel_std_z
 *  6-7:   accel_mag_mean, accel_mag_var
 *  8-10:  gyro_mean_x, gyro_mean_y, gyro_mean_z
 *  11-13: gyro_std_x, gyro_std_y, gyro_std_z
 *  14-15: gyro_mag_mean, gyro_mag_var
 *  16-17: pitch (precomputed), pitch_var (from raw)
 *  18-19: roll (precomputed), roll_var (from raw)
 *  20-21: accel_mag_min, accel_mag_max
 *  22-23: gyro_mag_min, gyro_mag_max
 *  24-25: pitch_range, roll_range (max - min across window)
 *  26-27: accel_magnitude (precomputed), gyro_magnitude (precomputed)
 *  28-29: accel_energy, gyro_energy
 */
object SalahFeatureExtractor {

    const val FEATURES_PER_WINDOW = 30
    const val SEQUENCE_LENGTH = 20

    fun extractFeatures(sample: SalahDataSample): FloatArray {
        val ax = sample.accelX
        val ay = sample.accelY
        val az = sample.accelZ
        val gx = sample.gyroX
        val gy = sample.gyroY
        val gz = sample.gyroZ

        // Accelerometer statistics
        val accelMeanX = ax.mean()
        val accelMeanY = ay.mean()
        val accelMeanZ = az.mean()
        val accelStdX = ax.std()
        val accelStdY = ay.std()
        val accelStdZ = az.std()

        // Accelerometer magnitude per sample
        val accelMag = FloatArray(ax.size) { i ->
            sqrt(ax[i] * ax[i] + ay[i] * ay[i] + az[i] * az[i])
        }
        val accelMagMean = accelMag.mean()
        val accelMagVar = accelMag.variance()

        // Gyroscope statistics
        val gyroMeanX = gx.mean()
        val gyroMeanY = gy.mean()
        val gyroMeanZ = gz.mean()
        val gyroStdX = gx.std()
        val gyroStdY = gy.std()
        val gyroStdZ = gz.std()

        // Gyroscope magnitude per sample
        val gyroMag = FloatArray(gx.size) { i ->
            sqrt(gx[i] * gx[i] + gy[i] * gy[i] + gz[i] * gz[i])
        }
        val gyroMagMean = gyroMag.mean()
        val gyroMagVar = gyroMag.variance()

        // Pitch and roll from precomputed values
        val pitch = sample.pitch
        val roll = sample.roll

        // Per-sample pitch/roll for variance (matching Python: atan2(ay, az) and atan2(ax, az))
        val pitches = FloatArray(ay.size) { i ->
            Math.toDegrees(atan2(ay[i].toDouble(), az[i].toDouble())).toFloat()
        }
        val rolls = FloatArray(ax.size) { i ->
            Math.toDegrees(atan2(ax[i].toDouble(), az[i].toDouble())).toFloat()
        }
        val pitchVar = pitches.variance()
        val rollVar = rolls.variance()

        // Min/max ranges
        val accelMin = accelMag.minOrNull() ?: 0f
        val accelMax = accelMag.maxOrNull() ?: 0f
        val gyroMin = gyroMag.minOrNull() ?: 0f
        val gyroMax = gyroMag.maxOrNull() ?: 0f

        // Precomputed values from Android
        val accelMagnitude = sample.accelMagnitude
        val gyroMagnitude = sample.gyroMagnitude

        // Pitch/roll range (max - min across window samples)
        val pitchRange = (pitches.maxOrNull() ?: 0f) - (pitches.minOrNull() ?: 0f)
        val rollRange = (rolls.maxOrNull() ?: 0f) - (rolls.minOrNull() ?: 0f)

        // Energy (sum of squares / N)
        var accelEnergySum = 0f
        for (i in ax.indices) {
            accelEnergySum += ax[i] * ax[i] + ay[i] * ay[i] + az[i] * az[i]
        }
        val accelEnergy = accelEnergySum / ax.size

        var gyroEnergySum = 0f
        for (i in gx.indices) {
            gyroEnergySum += gx[i] * gx[i] + gy[i] * gy[i] + gz[i] * gz[i]
        }
        val gyroEnergy = gyroEnergySum / gx.size

        return floatArrayOf(
            accelMeanX, accelMeanY, accelMeanZ,       // 0-2
            accelStdX, accelStdY, accelStdZ,           // 3-5
            accelMagMean, accelMagVar,                  // 6-7
            gyroMeanX, gyroMeanY, gyroMeanZ,           // 8-10
            gyroStdX, gyroStdY, gyroStdZ,              // 11-13
            gyroMagMean, gyroMagVar,                    // 14-15
            pitch, pitchVar,                            // 16-17
            roll, rollVar,                              // 18-19
            accelMin, accelMax,                         // 20-21
            gyroMin, gyroMax,                           // 22-23
            pitchRange, rollRange,                      // 24-25
            accelMagnitude, gyroMagnitude,              // 26-27
            accelEnergy, gyroEnergy                     // 28-29
        )
    }

    // Population std (matching numpy default ddof=0)
    private fun FloatArray.std(): Float {
        val m = this.mean()
        var sum = 0f
        for (v in this) {
            val diff = v - m
            sum += diff * diff
        }
        return sqrt(sum / this.size)
    }

    // Population variance (matching numpy default ddof=0)
    private fun FloatArray.variance(): Float {
        val m = this.mean()
        var sum = 0f
        for (v in this) {
            val diff = v - m
            sum += diff * diff
        }
        return sum / this.size
    }

    private fun FloatArray.mean(): Float {
        var sum = 0f
        for (v in this) sum += v
        return sum / this.size
    }
}
