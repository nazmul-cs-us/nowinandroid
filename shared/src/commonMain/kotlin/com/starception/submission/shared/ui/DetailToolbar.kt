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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import io.github.alexzhirkevich.cupertino.CupertinoActionSheetNative
import io.github.alexzhirkevich.cupertino.cancel
import io.github.alexzhirkevich.cupertino.default
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveWidget

/**
 * One action in a [DetailToolbar] / [DetailOptionsSheet]. The [icon] is
 * nullable so text-only actions (language names, font sizes) still render.
 */
data class DetailAction(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val trailingText: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Toolbar matching the Android detail pages: circular back button on the left,
 * inline actions that fit on the right, and an always-present ⋮ (MoreVert)
 * that opens a bottom sheet listing ALL options — the same pattern as
 * SurahDetailScreen and HadithDetailScreen on Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailToolbar(
    onBack: () -> Unit,
    inlineActions: List<DetailAction> = emptyList(),
    sheetActions: List<DetailAction> = emptyList(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    toolbarTitle: String? = null,
    includeStatusBarInset: Boolean = true,
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    val allActions = inlineActions + sheetActions

    AdaptiveWidget(
        material = {
            MaterialDetailToolbar(
                onBack = onBack,
                inlineActions = inlineActions,
                allActions = allActions,
                contentColor = contentColor,
                toolbarTitle = toolbarTitle,
                includeStatusBarInset = includeStatusBarInset,
                showOptionsSheet = showOptionsSheet,
                onShowOptionsSheet = { showOptionsSheet = true },
                onDismissOptionsSheet = { showOptionsSheet = false },
            )
        },
        cupertino = {
            CupertinoDetailToolbar(
                onBack = onBack,
                inlineActions = inlineActions,
                sheetActions = sheetActions,
                contentColor = contentColor,
                toolbarTitle = toolbarTitle,
                includeStatusBarInset = includeStatusBarInset,
                showOptionsSheet = showOptionsSheet,
                onShowOptionsSheet = { showOptionsSheet = true },
                onDismissOptionsSheet = { showOptionsSheet = false },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialDetailToolbar(
    onBack: () -> Unit,
    inlineActions: List<DetailAction>,
    allActions: List<DetailAction>,
    contentColor: Color,
    toolbarTitle: String?,
    includeStatusBarInset: Boolean,
    showOptionsSheet: Boolean,
    onShowOptionsSheet: () -> Unit,
    onDismissOptionsSheet: () -> Unit,
) {
    if (showOptionsSheet && allActions.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismissOptionsSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                if (toolbarTitle != null) {
                    Text(
                        text = toolbarTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                allActions.forEach { action ->
                    ListItem(
                        headlineContent = { Text(action.label) },
                        leadingContent = action.icon?.let { icon ->
                            {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (action.selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                        trailingContent = action.trailingText?.let { text ->
                            {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (action.selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            headlineColor = if (action.selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                action.onClick()
                                onDismissOptionsSheet()
                            },
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    )
                }
            }
        }
    }

    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
        // The immersive headers are full-bleed under the status bar; the
        // toolbar itself clears the network/notification icons.
        modifier = Modifier
            .fillMaxWidth()
            .then(if (includeStatusBarInset) Modifier.statusBarsPadding() else Modifier)
            .height(68.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // LEFT — circular back button (Android's style).
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.15f),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = NiaIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = contentColor,
                    )
                }
            }

            // RIGHT — inline actions that fit + the MoreVert (⋮) always shown.
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                inlineActions.forEach { action ->
                    IconButton(
                        onClick = action.onClick,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = contentColor,
                        ),
                    ) {
                        val icon = action.icon
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = action.label,
                                // Filled vs outlined artwork already communicates
                                // selection. Keep overlay actions at the requested
                                // contrast color so a selected bookmark never turns
                                // dark and disappears over photography.
                                tint = contentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        } else if (action.trailingText != null) {
                            Text(
                                text = action.trailingText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { if (allActions.isNotEmpty()) onShowOptionsSheet() },
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = contentColor,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CupertinoDetailToolbar(
    onBack: () -> Unit,
    inlineActions: List<DetailAction>,
    sheetActions: List<DetailAction>,
    contentColor: Color,
    toolbarTitle: String?,
    includeStatusBarInset: Boolean,
    showOptionsSheet: Boolean,
    onShowOptionsSheet: () -> Unit,
    onDismissOptionsSheet: () -> Unit,
) {
    CupertinoActionSheetNative(
        visible = showOptionsSheet && sheetActions.isNotEmpty(),
        onDismissRequest = onDismissOptionsSheet,
        title = toolbarTitle,
        buttons = {
            sheetActions.forEach { action ->
                default(
                    onClick = {
                        action.onClick()
                        onDismissOptionsSheet()
                    },
                    title = buildString {
                        append(action.label)
                        action.trailingText?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                )
            }
            cancel(onClick = onDismissOptionsSheet, title = "Cancel")
        },
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (includeStatusBarInset) Modifier.statusBarsPadding() else Modifier)
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = NiaIcons.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            if (inlineActions.isNotEmpty() || sheetActions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        inlineActions.forEach { action ->
                            IconButton(
                                onClick = action.onClick,
                                modifier = Modifier.size(52.dp),
                            ) {
                                action.icon?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = action.label,
                                        modifier = Modifier.size(25.dp),
                                    )
                                } ?: action.trailingText?.let { text ->
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        if (sheetActions.isNotEmpty()) {
                            IconButton(
                                onClick = onShowOptionsSheet,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "More options",
                                    modifier = Modifier.size(27.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
