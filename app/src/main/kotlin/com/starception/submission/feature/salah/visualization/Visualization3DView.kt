package com.starception.submission.feature.salah.visualization

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import kotlinx.coroutines.delay

/**
 * Jetpack Compose wrapper for LibGDX SalahVisualization3D renderer.
 *
 * Uses a named [LibGDXFragment] class to satisfy Android's requirement that fragments
 * be public non-anonymous classes for proper recreation from instance state.
 */
@Composable
fun Visualization3DView(
    samples: List<SalahDataSample>,
    state: VisualizationState,
    onStateChange: (VisualizationState) -> Unit,
    modifier: Modifier = Modifier
) {
    var visualizationRef by remember { mutableStateOf<SalahVisualization3D?>(null) }
    var fragmentRef by remember { mutableStateOf<LibGDXFragment?>(null) }

    // Keep latest references for the factory callback (avoids stale closure)
    val currentState by rememberUpdatedState(state)
    val currentOnStateChange by rememberUpdatedState(onStateChange)

    val context = LocalContext.current

    // Helper: suspends until the renderer's GL context is ready
    suspend fun awaitReady(viz: SalahVisualization3D) {
        while (!viz.isReady) delay(50)
    }

    // Update data when samples change or renderer becomes available
    LaunchedEffect(samples, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setData(samples)
    }

    // Update visualization mode
    LaunchedEffect(state.mode, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setMode(state.mode)
    }

    // Update visible postures filter
    LaunchedEffect(state.visiblePostures, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setVisiblePostures(state.visiblePostures)
    }

    // Update playback state
    LaunchedEffect(state.isPlaying, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setPlaying(state.isPlaying)
    }

    LaunchedEffect(state.playbackSpeed, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setPlaybackSpeed(state.playbackSpeed)
    }

    // Update axis mapping for scatter plot mode
    LaunchedEffect(state.axisX, state.axisY, state.axisZ, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setAxisMapping(state.axisX, state.axisY, state.axisZ)
    }

    // Update point size
    LaunchedEffect(state.pointSize, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setPointSize(state.pointSize)
    }

    // Manual playback index changes (from step buttons)
    LaunchedEffect(state.playbackIndex, visualizationRef) {
        if (!state.isPlaying) {
            val viz = visualizationRef ?: return@LaunchedEffect
            awaitReady(viz)
            viz.setPlaybackIndex(state.playbackIndex)
        }
    }

    AndroidView(
        factory = { ctx ->
            // Custom FrameLayout that prevents LazyColumn from stealing touch events.
            // Without this, the Compose scrollable parent intercepts drags/pinches
            // before the LibGDX GL surface can process them for camera orbit/zoom.
            @SuppressLint("ClickableViewAccessibility")
            val container = object : FrameLayout(ctx) {
                override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                    // Tell all ancestors not to intercept our touch events
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return super.dispatchTouchEvent(ev)
                }

                override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                    // Don't intercept — let the GL surface child handle all touches
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return false
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // Callback for LibGDX to update Compose state during playback.
            // Uses rememberUpdatedState refs to avoid stale closure over initial state.
            val onPlaybackUpdate = { index: Int, posture: SalahPosture?, pitch: Float, roll: Float, accelMag: Float, gyroMag: Float ->
                currentOnStateChange(currentState.copy(
                    playbackIndex = index,
                    currentPosture = posture,
                    currentPitch = pitch,
                    currentRoll = roll,
                    currentAccelMag = accelMag,
                    currentGyroMag = gyroMag
                ))
            }

            // Create LibGDX ApplicationListener
            val visualization = SalahVisualization3D(onPlaybackUpdate)
            visualizationRef = visualization

            // Configure LibGDX backend
            val config = AndroidApplicationConfiguration().apply {
                useAccelerometer = false
                useCompass = false
                useGL30 = false
                numSamples = 2
            }

            // Get FragmentActivity from context
            val fragmentActivity = ctx as? FragmentActivity
                ?: throw IllegalStateException("Visualization3DView requires FragmentActivity context.")

            // Create named fragment (avoids "must be public static class" crash)
            val fragment = LibGDXFragment.newInstance(visualization, config)
            fragmentRef = fragment

            // Generate unique ID for container
            val containerId = View.generateViewId()
            container.id = containerId

            // Add LibGDX fragment to container.
            // The FragmentManager handles the fragment's lifecycle (onResume/onPause)
            // automatically — do NOT add a manual LifecycleEventObserver that calls
            // fragment.onResume()/onPause(), as double-pausing causes LibGDX's
            // AndroidGraphics to deadlock waiting for GL thread pause synchronization,
            // which kills the process with SIGKILL.
            fragmentActivity.supportFragmentManager.beginTransaction()
                .add(containerId, fragment, "salah_viz_3d_${containerId}")
                .commitAllowingStateLoss()

            container
        },
        modifier = modifier,
        update = { container ->
            container.requestLayout()
        },
        onRelease = {
            visualizationRef = null
            // Remove fragment when view is released
            fragmentRef?.let { frag ->
                try {
                    val fm = (frag.activity as? FragmentActivity)?.supportFragmentManager
                    fm?.beginTransaction()?.remove(frag)?.commitAllowingStateLoss()
                } catch (_: Exception) {}
            }
            fragmentRef = null
        }
    )
}
