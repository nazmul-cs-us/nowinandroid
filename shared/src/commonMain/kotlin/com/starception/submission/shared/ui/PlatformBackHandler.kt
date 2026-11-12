package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable

/** Compose's native back dispatcher and Android's Activity dispatcher have different APIs. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
