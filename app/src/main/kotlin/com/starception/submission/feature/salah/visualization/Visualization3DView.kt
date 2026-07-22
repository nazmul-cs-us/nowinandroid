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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import kotlinx.coroutines.delay

/**
 * Tears down the LibGDX [fragment] in the order LibGDX requires, so it does NOT deadlock-kill
 * the whole process.
 *
 * The hazard: LibGDX's AndroidGraphics.pause() (fired by the fragment's onPause during removal)
 * posts a `queueEvent` to the GLSurfaceView's GL thread and blocks up to 4s waiting for it to be
 * acknowledged; if it isn't, LibGDX calls Process.killProcess() ("waiting for pause synchronization
 * took too long; assuming deadlock and killing"). During a Compose navigation pop, the container
 * view — and with it the GLSurfaceView — is detached FIRST, which stops the GL thread, so the
 * queued event never runs and the handshake deadlocks.
 *
 * Two defenses, in order:
 *  1. Revive the GLSurfaceView's render thread (onResumeGLSurfaceView) so a queued pause event can
 *     still be processed even if a detach already began.
 *  2. Remove the fragment SYNCHRONOUSLY (commitNow) so the full onPause -> onDestroyView -> dispose
 *     runs here, while we still control the ordering, rather than being scheduled for after detach.
 *
 * Idempotent: safe to call from both the DisposableEffect (which runs while the view is still
 * attached — the important one) and AndroidView.onRelease (fallback).
 */
/**
 * Hook for tearing down the live 3D view BEFORE its composable is removed on paths Compose
 * gives us no early signal for (the card's Hide toggle). The screen calls [disposeActive]
 * in the toggle's click handler — while the GL surface is still attached — then flips the
 * visibility state. At most one 3D view exists at a time (LibGDX is process-global), so a
 * single slot suffices.
 */
object Visualization3DTeardown {
    internal var activeTeardown: (() -> Unit)? = null

    /** Synchronously tear down the live GL fragment, if any. Safe no-op otherwise. */
    fun disposeActive() {
        activeTeardown?.invoke()
    }
}

private fun tearDownGlFragment(activity: FragmentActivity?, fragment: LibGDXFragment?) {
    if (fragment == null) return
    val fm = activity?.supportFragmentManager ?: return
    if (fm.isDestroyed) return
    try {
        (fragment.graphics as? AndroidGraphics)?.onResumeGLSurfaceView()
    } catch (_: Exception) {
    }
    try {
        fm.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
    } catch (_: Exception) {
        try {
            fm.beginTransaction().remove(fragment).commitAllowingStateLoss()
        } catch (_: Exception) {
        }
    }
}

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
    modifier: Modifier = Modifier,
    /**
     * Preferred sink for high-frequency playback ticks. Unlike [onStateChange]
     * (which rebuilds from the composition-time state snapshot and can clobber
     * concurrent async updates like predictions/PCA), this merges into the
     * LATEST state at the owner. Falls back to [onStateChange] when null.
     */
    onPlaybackTick: ((index: Int, posture: SalahPosture?, pitch: Float, roll: Float, accelMag: Float, gyroMag: Float, playing: Boolean) -> Unit)? = null
) {
    var visualizationRef by remember { mutableStateOf<SalahVisualization3D?>(null) }
    var fragmentRef by remember { mutableStateOf<LibGDXFragment?>(null) }
    // Captured at factory time so teardown never depends on fragment.activity, which is
    // usually already null during a navigation pop (leaving the GL thread orphaned).
    var hostActivityRef by remember { mutableStateOf<FragmentActivity?>(null) }

    // Keep latest references for the factory callback (avoids stale closure)
    val currentState by rememberUpdatedState(state)
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    val currentOnPlaybackTick by rememberUpdatedState(onPlaybackTick)

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

    // Diagnostics overlays
    LaunchedEffect(state.flaggedIndices, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setDisagreements(state.flaggedIndices)
    }

    // Model predictions, parallel to `samples` — drives dual humanoid
    // playback (ground-truth vs prediction) in PHONE_MODEL mode once
    // "Analyze predictions" has run. Null clears back to a single figure.
    LaunchedEffect(state.predictions, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setPredictions(state.predictions)
    }

    LaunchedEffect(state.showDisagreements, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setShowDisagreements(state.showDisagreements)
    }

    LaunchedEffect(state.showEllipsoids, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setShowEllipsoids(state.showEllipsoids)
    }

    // PCA projection for FEATURE_PCA mode
    LaunchedEffect(state.pcaPositions, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.setPcaPositions(state.pcaPositions)
    }

    // Reset camera / auto-fit request
    LaunchedEffect(state.cameraResetToken, visualizationRef) {
        val viz = visualizationRef ?: return@LaunchedEffect
        awaitReady(viz)
        viz.resetCamera()
    }

    // Manual playback index changes (from step buttons)
    LaunchedEffect(state.playbackIndex, visualizationRef) {
        if (!state.isPlaying) {
            val viz = visualizationRef ?: return@LaunchedEffect
            awaitReady(viz)
            viz.setPlaybackIndex(state.playbackIndex)
        }
    }

    // Tear the LibGDX fragment down on the destination's ON_STOP — NOT on Compose disposal.
    //
    // LibGDX's onPause() runs graphics.pause() (a blocking GL-thread handshake) and only THEN
    // onPauseGLSurfaceView(); so onPause() is safe ONLY while the GLSurfaceView is still attached.
    // On a navigation pop, Compose detaches the surface during composition disposal, so anything
    // in onDispose / AndroidView.onRelease runs too late — pause() can't reach the (already
    // stopped) GL thread, waits 4s, and LibGDX Process.killProcess()es the app. ON_STOP fires
    // BEFORE that disposal, while the surface is attached, so the removal's onPause completes
    // cleanly.
    //
    // Only tear down when the host Activity is still running (a genuine nav-pop). If the whole app
    // is just backgrounded, the Activity's own lifecycle pauses the fragment normally (surface
    // stays attached, no deadlock) and the viz survives to the foreground — so we skip.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val teardownNow: () -> Unit = {
            tearDownGlFragment(hostActivityRef, fragmentRef)
            visualizationRef = null
            fragmentRef = null
            hostActivityRef = null
        }
        // Hide-toggle path: the screen calls Visualization3DTeardown.disposeActive() from the
        // toggle's click handler, BEFORE flipping the state that removes this composable —
        // the last moment the GL surface is guaranteed attached on that path.
        Visualization3DTeardown.activeTeardown = teardownNow
        val observer = LifecycleEventObserver { _, event ->
            // Navigation path (back OR forward): the destination's ON_STOP fires before the
            // outgoing composition is disposed, i.e. while the GL surface is still attached.
            // The host-activity check separates a real navigation (activity still STARTED →
            // tear down now) from app backgrounding (activity stopping too → leave the
            // fragment alone; the FragmentManager pauses it safely with the surface attached,
            // and it resumes when the app returns to the foreground).
            if (event == Lifecycle.Event.ON_STOP &&
                hostActivityRef?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
            ) {
                teardownNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (Visualization3DTeardown.activeTeardown === teardownNow) {
                Visualization3DTeardown.activeTeardown = null
            }
            // Last-resort fallback; a no-op whenever one of the pre-detach paths above already
            // ran (refs are null by then).
            teardownNow()
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
            val onPlaybackUpdate = { index: Int, posture: SalahPosture?, pitch: Float, roll: Float, accelMag: Float, gyroMag: Float, playing: Boolean ->
                val tick = currentOnPlaybackTick
                if (tick != null) {
                    tick(index, posture, pitch, roll, accelMag, gyroMag, playing)
                } else {
                    currentOnStateChange(currentState.copy(
                        playbackIndex = index,
                        currentPosture = posture,
                        currentPitch = pitch,
                        currentRoll = roll,
                        currentAccelMag = accelMag,
                        currentGyroMag = gyroMag,
                        isPlaying = playing
                    ))
                }
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
            hostActivityRef = fragmentActivity

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
            // Fallback only — the DisposableEffect above is the real teardown (it runs while the
            // view is still attached). Idempotent: no-ops if already torn down.
            tearDownGlFragment(hostActivityRef, fragmentRef)
            visualizationRef = null
            fragmentRef = null
            hostActivityRef = null
        }
    )
}
