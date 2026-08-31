/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons

/** A destination in the floating bar. */
data class BottomBarItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val enabled: Boolean = true,
)

/**
 * The destinations the shared app shows.
 *
 * Mirrors the Android bar's order. Each destination has a lean shared
 * implementation backed by the platform key-value store.
 */
val SharedBottomBarItems = listOf(
    BottomBarItem("Home", NiaIcons.Home, NiaIcons.HomeBorder),
    BottomBarItem("For you", NiaIcons.Upcoming, NiaIcons.UpcomingBorder),
    BottomBarItem("Saved", NiaIcons.Bookmarks, NiaIcons.BookmarksBorder),
    BottomBarItem("Course", NiaIcons.Course, NiaIcons.CourseBorder),
    BottomBarItem("Interests", NiaIcons.Grid3x3, NiaIcons.Grid3x3),
)

/**
 * The floating navigation pill.
 *
 * A rounded surface that hovers over the content rather than a bar attached to
 * the bottom edge, matching Android. The selection indicator animates between
 * items with a spring, so it reads as one mass travelling rather than a
 * highlight cutting from place to place — the reason it is a single animated
 * offset instead of a per-item background.
 */
@Composable
fun FloatingBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var barWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val horizontalInset = 12.dp
    val itemWidth = with(density) {
        ((barWidth / density.density).dp - horizontalInset * 2) /
            items.size.coerceAtLeast(1)
    }
    val isDarkTheme = MaterialTheme.colorScheme.background
        .let { it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f < 0.5f }

    val indicatorOffset by animateDpAsState(
        targetValue = 4.dp + itemWidth * selectedIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navIndicator",
    )

    Surface(
        modifier = modifier
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(26.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onSizeChanged { barWidth = it.width },
        ) {
            if (barWidth > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .size(width = itemWidth + 16.dp, height = 48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDarkTheme) MaterialTheme.colorScheme.surfaceBright else Color.White,
                        ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = horizontalInset),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable(enabled = item.enabled) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = when {
                                !item.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.35f)
                                selected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Floating navigation rail used by the Android and iOS landscape dashboards. */
@Composable
fun FloatingSideBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .safeDrawingPadding()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .clickable(enabled = item.enabled) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = when {
                            !item.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.35f)
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
        }
    }
}
