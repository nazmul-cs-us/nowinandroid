package com.starception.submission.settings.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun artworkDialogProperties() = DialogProperties(
    usePlatformDefaultWidth = false,
    usePlatformInsets = false,
    dismissOnClickOutside = false,
)
