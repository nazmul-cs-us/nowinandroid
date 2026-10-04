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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveSurface
import io.github.alexzhirkevich.cupertino.adaptive.ExperimentalAdaptiveApi

/**
 * Immersive detail scaffold matching the Android app's album-header pages
 * (SurahDetailScreen / HadithDetailScreen / DuaDetailScreen): full-bleed
 * artwork with a scrim gradient, floating circular back button, content
 * below — no plain text header.
 */
@OptIn(ExperimentalAdaptiveApi::class)
@Composable
internal fun ImmersiveDetailScaffold(
    onBack: () -> Unit,
    maxContentWidth: Dp = 720.dp,
    contentHorizontalPadding: Dp = 16.dp,
    collapsibleHeader: Boolean = false,
    header: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val headerNestedScrollConnection = remember(collapsibleHeader) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!collapsibleHeader || available.y >= 0f || headerHeightPx <= 0f) return Offset.Zero
                val previous = headerOffsetPx
                headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-headerHeightPx, 0f)
                return Offset(x = 0f, y = headerOffsetPx - previous)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!collapsibleHeader || available.y <= 0f || headerHeightPx <= 0f) return Offset.Zero
                val previous = headerOffsetPx
                headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-headerHeightPx, 0f)
                return Offset(x = 0f, y = headerOffsetPx - previous)
            }
        }
    }
    AdaptiveSurface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .then(
                        if (collapsibleHeader) Modifier.nestedScroll(headerNestedScrollConnection) else Modifier,
                    ),
            ) {
                // Android's reader artwork is full-bleed, extending under the
                // status bar; the toolbar inside it clears the inset itself.
                // Only the content below keeps the horizontal and bottom safe
                // insets, so text stays clear of notches and the home indicator.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            headerHeightPx = placeable.height.toFloat()
                            val offset = if (collapsibleHeader) {
                                headerOffsetPx.coerceIn(-headerHeightPx, 0f).toInt()
                            } else {
                                0
                            }
                            layout(
                                width = placeable.width,
                                height = (placeable.height + offset).coerceAtLeast(0),
                            ) {
                                placeable.placeRelative(0, offset)
                            }
                        }
                        .clipToBounds(),
                ) { header() }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        )
                        .padding(horizontal = contentHorizontalPadding)
                        .padding(top = 12.dp),
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
    modifier: Modifier = Modifier.fillMaxSize(),
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
                fontFamily = FontFamily.Default,
                color = MaterialTheme.colorScheme.primary,
                // The toolbar owns the top-right corner and the Latin title
                // owns the bottom edge. The middle-right position keeps this
                // label clear of both.
                modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
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
