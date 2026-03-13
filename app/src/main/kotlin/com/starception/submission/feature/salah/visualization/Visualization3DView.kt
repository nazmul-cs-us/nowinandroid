package com.starception.submission.feature.salah.visualization

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture

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

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Update data when samples change
    LaunchedEffect(samples) {
        visualizationRef?.takeIf { it.isReady }?.setData(samples)
    }

    // Update visualization mode
    LaunchedEffect(state.mode) {
        visualizationRef?.takeIf { it.isReady }?.setMode(state.mode)
    }

    // Update visible postures filter
    LaunchedEffect(state.visiblePostures) {
        visualizationRef?.takeIf { it.isReady }?.setVisiblePostures(state.visiblePostures)
    }

    // Update playback state
    LaunchedEffect(state.isPlaying) {
        visualizationRef?.takeIf { it.isReady }?.setPlaying(state.isPlaying)
    }

    LaunchedEffect(state.playbackSpeed) {
        visualizationRef?.takeIf { it.isReady }?.setPlaybackSpeed(state.playbackSpeed)
    }

    // Update axis mapping for scatter plot mode
    LaunchedEffect(state.axisX, state.axisY, state.axisZ) {
        visualizationRef?.takeIf { it.isReady }?.setAxisMapping(state.axisX, state.axisY, state.axisZ)
    }

    // Update point size
    LaunchedEffect(state.pointSize) {
        visualizationRef?.takeIf { it.isReady }?.setPointSize(state.pointSize)
    }

    // Manual playback index changes (from step buttons)
    LaunchedEffect(state.playbackIndex) {
        if (!state.isPlaying) {
            visualizationRef?.takeIf { it.isReady }?.setPlaybackIndex(state.playbackIndex)
        }
    }

    AndroidView(
        factory = { ctx ->
            val container = FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // Callback for LibGDX to update Compose state during playback
            val onPlaybackUpdate = { index: Int, posture: SalahPosture?, pitch: Float, roll: Float, accelMag: Float, gyroMag: Float ->
                onStateChange(state.copy(
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

            // Add LibGDX fragment to container
            fragmentActivity.supportFragmentManager.beginTransaction()
                .add(containerId, fragment, "salah_viz_3d_${containerId}")
                .commitAllowingStateLoss()

            // Lifecycle observer for proper OpenGL surface management
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        try { fragment.onResume() } catch (_: Exception) {}
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        try { fragment.onPause() } catch (_: Exception) {}
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

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
