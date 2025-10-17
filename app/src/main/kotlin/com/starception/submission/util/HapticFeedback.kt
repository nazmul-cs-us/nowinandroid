package com.starception.submission.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

/**
 * Performs haptic feedback
 */
fun View.performHaptic() {
    performHapticFeedback(
        HapticFeedbackConstants.CONTEXT_CLICK,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
    )
}

/**
 * Modifier to perform haptic feedback on click
 */
fun Modifier.hapticClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit
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
            }
        )
    )
}
