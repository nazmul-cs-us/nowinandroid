package com.starception.submission.feature.salah.visualization

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

/**
 * Named public fragment class for hosting LibGDX's GL surface.
 *
 * Android requires fragments to be public, non-anonymous, non-inner classes so they can be
 * recreated via reflection during configuration changes. This class satisfies that requirement
 * while hosting the SalahVisualization3D renderer.
 *
 * Uses a companion-object-based factory to pass the visualization and config references
 * before the fragment is added to the FragmentManager.
 */
class LibGDXFragment : AndroidFragmentApplication() {

    companion object {
        // Temporary holders — set before fragment transaction, consumed in onCreate
        private var pendingVisualization: SalahVisualization3D? = null
        private var pendingConfig: AndroidApplicationConfiguration? = null

        fun newInstance(
            visualization: SalahVisualization3D,
            config: AndroidApplicationConfiguration
        ): LibGDXFragment {
            pendingVisualization = visualization
            pendingConfig = config
            return LibGDXFragment()
        }
    }

    private var visualization: SalahVisualization3D? = null
    private var config: AndroidApplicationConfiguration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Consume the pending references
        visualization = pendingVisualization
        config = pendingConfig
        pendingVisualization = null
        pendingConfig = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val cfg = config ?: AndroidApplicationConfiguration()

        // When Android recreates this fragment from saved state (config change / process
        // restore), our pending renderer ref is gone, so `visualization` is null. We must
        // STILL initialize a GL app here: AndroidFragmentApplication.onResume() unconditionally
        // dereferences its AndroidInput, and if initializeForView() was never called that input
        // is null -> NPE crash the moment the fragment is resumed. A no-op renderer keeps the GL
        // app valid until Compose disposes this leftover fragment and creates a fresh one.
        val viz = visualization ?: SalahVisualization3D { _, _, _, _, _, _, _ -> }

        val view = initializeForView(viz, cfg)

        // Enable touch handling for orbit camera (zoom, pan, rotate)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()

        // DO NOT call view.setOnTouchListener here — initializeForView() sets
        // AndroidInput as the touch listener, which processes all camera gestures
        // (orbit, zoom, pan). Setting another listener would overwrite it since
        // View.setOnTouchListener() only supports one listener.
        //
        // Parent scroll interception (LazyColumn) is handled by the container
        // FrameLayout in Visualization3DView.kt which overrides dispatchTouchEvent()
        // to call requestDisallowInterceptTouchEvent(true).

        return view
    }
}
