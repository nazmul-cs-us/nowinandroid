/*
 * Copyright 2026 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The warm editorial canvas shared by the app's main destinations.
 *
 * Keeping the top color and the body gradient together prevents the native
 * search-bar host from looking like a separate strip above Compose content.
 */
@Composable
fun mainPageTopColor(): Color = MaterialTheme.colorScheme.background

@Composable
fun mainPageBackgroundBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        colors = if (LocalDarkTheme.current) {
            listOf(
                scheme.background,
                scheme.surfaceContainer,
                scheme.background,
            )
        } else {
            listOf(
                scheme.background,
                scheme.surfaceContainerLow,
                scheme.secondary.copy(alpha = 0.14f).compositeOver(scheme.surfaceContainerLow),
            )
        },
    )
}
