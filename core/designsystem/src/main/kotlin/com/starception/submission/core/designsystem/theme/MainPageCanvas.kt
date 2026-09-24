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
                scheme.surface,
                scheme.primary.copy(alpha = 0.05f).compositeOver(scheme.surface),
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
