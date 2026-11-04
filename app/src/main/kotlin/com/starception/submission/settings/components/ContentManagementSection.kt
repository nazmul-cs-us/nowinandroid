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

package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.download.AssetDownloadViewModel
import com.starception.submission.download.CategoryDownloadState

/**
 * Content Management section for the Settings screen.
 * Groups categories into collapsible sections (Holy Quran, Hadith, Audio, etc.)
 * with group-level summary and expandable item details.
 */
@Composable
fun ContentManagementSection(
    categories: List<CategoryDownloadState>,
    totalDownloadedSize: Long,
    onDownloadCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track which groups are expanded
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // Group categories using the ViewModel's grouping logic
    val grouped = categories.groupBy { AssetDownloadViewModel.categoryGroup(it.categoryKey) }
        .toSortedMap(compareBy { AssetDownloadViewModel.groupSortOrder(it) })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Storage summary
        Text(
            text = "Total downloaded: ${AssetDownloadViewModel.formatSize(totalDownloadedSize)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Grouped sections
        grouped.forEach { (groupName, groupCategories) ->
            ContentGroupCard(
                groupName = groupName,
                categories = groupCategories,
                isExpanded = expandedGroups[groupName] ?: false,
                onToggleExpanded = {
                    expandedGroups[groupName] = !(expandedGroups[groupName] ?: false)
                },
                onDownloadCategory = onDownloadCategory,
                onDeleteCategory = onDeleteCategory,
            )
        }
    }
}

@Composable
private fun ContentGroupCard(
    groupName: String,
    categories: List<CategoryDownloadState>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDownloadCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSize = categories.sumOf { it.totalSize }
    val downloadedSize = categories.sumOf { it.downloadedSize }
    val completedCount = categories.count { it.isComplete }
    val allComplete = completedCount == categories.size
    val groupDescription = AssetDownloadViewModel.groupDescription(groupName)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            // Group header - always visible, clickable to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggleExpanded)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Group status icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (allComplete) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (allComplete) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.COMPLETED,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 20.sp,
                        )
                    } else {
                        FlaticonIcon(
                            glyph = FlaticonIcons.DOWNLOAD,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 20.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (groupDescription.isNotEmpty()) {
                        Text(
                            text = groupDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "$completedCount/${categories.size} offline · ${AssetDownloadViewModel.formatSize(downloadedSize)} saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FlaticonIcon(
                    glyph = if (isExpanded) FlaticonIcons.ANGLE_UP else FlaticonIcons.ANGLE_DOWN,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 22.sp,
                )
            }

            // Group progress bar (only when not all complete)
            if (!allComplete && totalSize > 0 && categories.any { it.isDownloading }) {
                LinearProgressIndicator(
                    progress = { (downloadedSize.toFloat() / totalSize).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Expandable category list
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { cat ->
                        ContentCategoryRow(
                            state = cat,
                            onDownload = { onDownloadCategory(cat.categoryKey) },
                            onDelete = if (cat.downloadedSize > 0 && !cat.required && !cat.isDownloading) {
                                { onDeleteCategory(cat.categoryKey) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContentCategoryRow(
    state: CategoryDownloadState,
    onDownload: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.displayName, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = when {
                        state.isDownloading -> "Downloading · ${(state.progress * 100).toInt()}%"
                        state.isComplete -> "Available offline · ${AssetDownloadViewModel.formatSize(state.totalSize)}"
                        state.downloadedSize > 0 ->
                            "${AssetDownloadViewModel.formatSize(state.downloadedSize)} of ${AssetDownloadViewModel.formatSize(state.totalSize)} saved"
                        else -> "Not downloaded · ${AssetDownloadViewModel.formatSize(state.totalSize)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                state.isDownloading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                !state.isComplete -> TextButton(onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.semantics { contentDescription = "Download ${state.displayName}" }) {
                    Text(when {
                        state.error != null -> "Retry download"
                        state.downloadedSize > 0 -> "Resume"
                        else -> "Download"
                    })
                }
                onDelete != null -> TextButton(onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.semantics { contentDescription = "Remove ${state.displayName}" }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
            if (onDelete != null && !state.isComplete && !state.isDownloading) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp).semantics {
                    contentDescription = "Remove ${state.displayName}"
                }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (state.isDownloading) {
            LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
