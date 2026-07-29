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

/**
 * The warm editorial canvas shared by the app's main destinations.
 *
 * Keeping the top color and the body gradient together prevents the native
 * search-bar host from looking like a separate strip above Compose content.
 */
@Composable
fun mainPageTopColor(): Color = if (LocalDarkTheme.current) {
    MaterialTheme.colorScheme.background
} else {
    Color(0xFFF0F1F2)
}

@Composable
fun mainPageBackgroundBrush(): Brush = if (LocalDarkTheme.current) {
    Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.background,
        ),
    )
} else {
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF0F1F2),
            Color(0xFFE9E6D8),
            Color(0xFFD8AB59).copy(alpha = 0.34f),
        ),
    )
}
