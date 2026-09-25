/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.widget.samples.utils

import androidx.compose.runtime.Composable
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
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
