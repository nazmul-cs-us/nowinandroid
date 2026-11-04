package com.starception.submission.shared.ui

import androidx.compose.runtime.*

/** The host chooses native bar contrast without changing the shared app theme. */
internal val LocalNativeBarAppearance = staticCompositionLocalOf<(Boolean?) -> Unit> { {} }

@Composable
internal fun NativeBarAppearance(lightIcons: Boolean) {
    val update = LocalNativeBarAppearance.current
    DisposableEffect(update, lightIcons) {
        update(lightIcons)
        onDispose { }
    }
}
