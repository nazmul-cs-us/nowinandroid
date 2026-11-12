package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.compose.ui.backhandler.BackHandler(enabled = enabled, onBack = onBack)
}
