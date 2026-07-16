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
    private const val ALPHA = 0.15f // response low-pass (smaller = smoother/slower)
    // Baseline low-pass: a running estimate of the phone's *resting* orientation.
    // Parallax is published relative to this baseline (a high-pass), so holding
    // the phone at any angle settles back to neutral — the same look it has lying
    // flat — while movement still drives the effect. It only advances while the
    // phone is fairly still (see MOTION_REF); it's frozen mid-tilt so the up/down
    // parallax persists through the gesture instead of fading out under the hand.
    private const val BASE_ALPHA = 0.03f
    // Innovation magnitude (raw-vs-smoothed) at/above which the phone is treated
    // as actively tilting rather than at rest — gates the baseline recenter so a
    // real tilt keeps its 3D shift while a static hold still settles to neutral.
    private const val MOTION_REF = 0.03f

    private val _tilt = mutableStateOf(Offset.Zero)
    val tilt: State<Offset> get() = _tilt

    private var sensorManager: SensorManager? = null
    private var subscribers = 0

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothX = 0f
    private var smoothY = 0f
    private var baseX = 0f
    private var baseY = 0f
    private var primed = false

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
            if (!primed) {
                // First reading: snap both the fast follower and the resting
                // baseline to the current pose so parallax starts at neutral no
                // matter how the phone is being held when the tiles appear.
                smoothX = rollN
                smoothY = pitchN
                baseX = rollN
                baseY = pitchN
                primed = true
            } else {
                // Innovation magnitude = how far the raw reading is from the
                // smoothed value → a motion indicator: large while the user is
                // actively tilting, ~sensor-noise while the phone is held still.
                val motion = kotlin.math.abs(rollN - smoothX) + kotlin.math.abs(pitchN - smoothY)
                smoothX += ALPHA * (rollN - smoothX)
                smoothY += ALPHA * (pitchN - smoothY)
                // Recenter the resting baseline only while fairly still; freeze it
                // during active tilts so the up/down parallax persists through the
                // gesture (engaging 3D) rather than fading under a steady hand.
                val stillness = (1f - motion / MOTION_REF).coerceIn(0f, 1f)
                baseX += BASE_ALPHA * stillness * (smoothX - baseX)
                baseY += BASE_ALPHA * stillness * (smoothY - baseY)
            }
            // Publish tilt *relative* to the resting baseline (high-pass): the
            // sustained component fades out so any hold angle reads as neutral,
            // leaving only movement to drive the parallax.
            //
            // Deadband: the low-pass output never stops changing at float
            // precision, so publishing every event would invalidate every
            // parallax layer at sensor rate (~50Hz) even at rest. Only publish
            // visible movement (~0.1px at typical depths).
            val next = Offset(
                (smoothX - baseX).coerceIn(-1f, 1f),
                (smoothY - baseY).coerceIn(-1f, 1f),
            )
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
            // Reset so a re-acquire re-primes the baseline to the new pose.
            smoothX = 0f
            smoothY = 0f
            baseX = 0f
            baseY = 0f
            primed = false
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
