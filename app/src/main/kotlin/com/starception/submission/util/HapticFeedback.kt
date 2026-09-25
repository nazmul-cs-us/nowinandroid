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

package com.starception.submission.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

/**
 * Performs haptic feedback
 */
fun View.performHaptic() {
    performHapticFeedback(
        HapticFeedbackConstants.CONTEXT_CLICK,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
    )
}

/**
 * Modifier to perform haptic feedback on click
 */
fun Modifier.hapticClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    this.then(
        clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = {
                if (enabled) {
                    view.performHaptic()
                }
                onClick()
            },
        ),
    )
}
