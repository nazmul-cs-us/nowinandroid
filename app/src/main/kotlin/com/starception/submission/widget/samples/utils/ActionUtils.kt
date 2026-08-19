package com.starception.submission.widget.samples.utils

import androidx.compose.runtime.Composable
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.ActionParameters
import com.starception.submission.MainActivity

/**
 * Utility functions for creating [Action]s.
 *
 * Upstream this opened a throwaway activity that just echoed which widget element was
 * tapped, so the sample could demonstrate wiring without shipping real destinations.
 * Here every tap goes to [MainActivity] instead; the message is still carried as a
 * parameter so a destination can route on it once these widgets get real targets.
 */
object ActionUtils {
    /** Key naming the widget element that launched the app. */
    val ActionSourceMessageKey = ActionParameters.Key<String>("action_source_message")

    @Composable
    fun actionStartDemoActivity(message: String): Action =
        actionStartActivity<MainActivity>(
            actionParametersOf(ActionSourceMessageKey to message),
        )
}
