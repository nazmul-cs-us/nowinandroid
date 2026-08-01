/*
 * Copyright 2026 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared treatment for modal sheets. Colors come from the active app theme so
 * sheets follow light/dark mode and the user's selected palette automatically.
 */
object NiaBottomSheetDefaults {
    val FloatingShape = RoundedCornerShape(32.dp)

    val WarmCanvas = Color(0xFFE9E6D8)
    val WarmCard = Color(0xFFFFFDF7)
    val Ink = Color(0xFF0A0808)
    val Sand = Color(0xFFCEC3A1)
    val Slate = Color(0xFF5D6574)
    val Blue = Color(0xFF4F779D)
    val Rust = Color(0xFF99593C)
    val Brown = Color(0xFF694531)
    val Gold = Color(0xFFD8AB59)
    val Panel = Color(0xFF2D2D2D)
    val PanelLow = Color(0xFF343434)
    val PanelHigh = Color(0xFF3D3D3D)
    val PanelHighest = Color(0xFF484848)
    val PanelText = Color(0xFFFBFBFB)
    val PanelMuted = Color(0xFFB8B7B3)

    @Composable
    fun colorScheme(): ColorScheme = MaterialTheme.colorScheme

    @Composable
    fun containerColor(): Color = colorScheme().surfaceContainer

    @Composable
    fun contentColor(): Color = colorScheme().onSurface

    @Composable
    fun scrimColor(): Color = colorScheme().scrim.copy(alpha = 0.48f)
}

/** Applies the sheet-specific palette to every component inside a modal sheet. */
@Composable
fun NiaBottomSheetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NiaBottomSheetDefaults.colorScheme(),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/**
 * Floating sheet body. The transparent Material modal supplies motion and
 * gestures; this theme-aware surface supplies the visible silhouette and the
 * side/bottom breathing room.
 */
@Composable
fun NiaBottomSheetFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = NiaBottomSheetDefaults.FloatingShape,
        color = NiaBottomSheetDefaults.containerColor(),
        contentColor = NiaBottomSheetDefaults.contentColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/** Compact, high-contrast handle used by all app modal sheets. */
@Composable
fun NiaBottomSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(46.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            ),
    )
}
