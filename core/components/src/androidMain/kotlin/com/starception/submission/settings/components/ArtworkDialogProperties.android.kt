package com.starception.submission.settings.components

import androidx.compose.ui.window.DialogProperties

internal actual fun artworkDialogProperties() = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
    dismissOnClickOutside = false,
)
