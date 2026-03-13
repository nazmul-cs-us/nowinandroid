package com.starception.submission.feature.salah.visualization

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
        val viz = visualization
        val cfg = config ?: AndroidApplicationConfiguration()

        if (viz == null) {
            // Fragment was recreated by Android without our pending refs — return empty view.
            // This only happens during Activity recreation; Compose will re-create the view.
            return FrameLayout(requireContext())
        }

        val view = initializeForView(viz, cfg)

        // Enable touch handling for orbit camera
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        // Prevent parent scroll views from intercepting touch events during gestures
        view.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false // Let LibGDX handle touches through its input processor
        }

        return view
    }
}
