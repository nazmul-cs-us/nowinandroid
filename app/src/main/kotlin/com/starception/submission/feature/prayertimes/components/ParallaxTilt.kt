package com.starception.submission.feature.prayertimes.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Process-wide device-tilt source for parallax.
 *
 * A SINGLE [SensorEventListener] is registered no matter how many composables read
 * the tilt — consumers ref-count in/out via [acquire]/[release]. The published value
 * is a normalized [Offset] where x = roll (left/right tilt) and y = pitch
 * (forward/back tilt), each roughly in [-1, 1] and low-pass smoothed so parallax
 * glides rather than jitters.
 *
 * Prefers TYPE_GAME_ROTATION_VECTOR (no magnetometer drift), falling back to
 * ROTATION_VECTOR, then the accelerometer.
 */
object ParallaxTiltSource {
    private const val MAX_TILT_RADIANS = 0.6f
    private const val ALPHA = 0.15f // low-pass factor (smaller = smoother/slower)

    private val _tilt = mutableStateOf(Offset.Zero)
    val tilt: State<Offset> get() = _tilt

    private var sensorManager: SensorManager? = null
    private var subscribers = 0

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothX = 0f
    private var smoothY = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rollN: Float
            val pitchN: Float
            when (event.sensor.type) {
                Sensor.TYPE_GAME_ROTATION_VECTOR,
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val pitch = orientation[1] // around X (forward/back)
                    val roll = orientation[2]  // around Y (left/right)
                    rollN = (roll / MAX_TILT_RADIANS).coerceIn(-1f, 1f)
                    pitchN = (pitch / MAX_TILT_RADIANS).coerceIn(-1f, 1f)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val gx = event.values[0]
                    val gy = event.values[1]
                    rollN = (-gx / 9.81f).coerceIn(-1f, 1f)
                    pitchN = (gy / 9.81f).coerceIn(-1f, 1f)
                }
                else -> return
            }
            smoothX += ALPHA * (rollN - smoothX)
            smoothY += ALPHA * (pitchN - smoothY)
            // Deadband: the low-pass output never stops changing at float
            // precision, so publishing every event invalidates every parallax
            // layer at sensor rate (~50Hz) even with the device at rest on a
            // desk. Only publish visible movement (~0.1px at typical depths).
            val next = Offset(smoothX, smoothY)
            if ((next - _tilt.value).getDistance() > 0.003f) {
                _tilt.value = next
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    @Synchronized
    fun acquire(context: Context) {
        subscribers++
        if (subscribers == 1) {
            val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager = sm
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor != null) {
                sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    @Synchronized
    fun release() {
        subscribers--
        if (subscribers <= 0) {
            subscribers = 0
            sensorManager?.unregisterListener(listener)
            sensorManager = null
            // Ease back to neutral so a re-acquire doesn't jump.
            smoothX = 0f
            smoothY = 0f
            _tilt.value = Offset.Zero
        }
    }
}

/**
 * Compose entry point for [ParallaxTiltSource]. Returns the shared, smoothed tilt
 * Offset (x = roll, y = pitch, ~[-1, 1]); registers/unregisters the shared sensor
 * via ref-counting tied to this composable's lifecycle.
 *
 * Multiply the result by a per-layer depth (px) in a translation so nearer layers
 * move more than farther ones — the parallax depth cue.
 */
@Composable
fun rememberParallaxTilt(): State<Offset> {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        ParallaxTiltSource.acquire(context)
        onDispose { ParallaxTiltSource.release() }
    }
    return ParallaxTiltSource.tilt
}
