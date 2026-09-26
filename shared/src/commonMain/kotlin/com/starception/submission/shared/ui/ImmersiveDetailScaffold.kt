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

package com.starception.submission.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons

/**
 * Immersive detail scaffold matching the Android app's album-header pages
 * (SurahDetailScreen / HadithDetailScreen / DuaDetailScreen): full-bleed
 * artwork with a scrim gradient, floating circular back button, content
 * below — no plain text header.
 */
@Composable
internal fun ImmersiveDetailScaffold(
    onBack: () -> Unit,
    maxContentWidth: Dp = 720.dp,
    header: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    header()
                    // Floating back over the artwork, Android-style.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(40.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                NiaIcons.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/** Title block Android shows over the scrim: large title + supporting line. */
@Composable
internal fun ImmersiveDetailHeaderScrim(
    title: String,
    supportingText: String? = null,
    arabicTitle: String? = null,
    titleColor: Color = Color.White,
    scrimColor: Color = Color.Black,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    0f to scrimColor.copy(alpha = 0.05f),
                    0.4f to scrimColor.copy(alpha = 0.15f),
                    0.75f to scrimColor.copy(alpha = 0.55f),
                    1f to scrimColor.copy(alpha = 0.75f),
                ),
            ),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
            if (supportingText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = titleColor.copy(alpha = 0.85f),
                )
            }
        }
        if (arabicTitle != null) {
            Text(
                text = arabicTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }
    }
}

/** Android's reader-tag chip used under detail headers. */
@Composable
internal fun ImmersiveDetailTagRow(vararg tags: String) {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag -> Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
