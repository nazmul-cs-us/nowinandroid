package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

        Spacer(modifier = Modifier.height(4.dp))

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
                    .clickable(onClick = onToggleExpanded)
                    .padding(16.dp),
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
                        )
                    }
                    Text(
                        text = "$completedCount/${categories.size} items  \u00b7  ${AssetDownloadViewModel.formatSize(totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
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
            if (!allComplete && totalSize > 0) {
                LinearProgressIndicator(
                    progress = { downloadedSize.toFloat() / totalSize },
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
                            onDelete = if (cat.isComplete && !cat.required) {
                                { onDeleteCategory(cat.categoryKey) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentCategoryRow(
    state: CategoryDownloadState,
    onDownload: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Small status indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (state.isComplete) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (state.isComplete) {
                    AssetDownloadViewModel.formatSize(state.totalSize)
                } else if (state.downloadedSize > 0) {
                    "${AssetDownloadViewModel.formatSize(state.downloadedSize)} / ${AssetDownloadViewModel.formatSize(state.totalSize)}"
                } else {
                    AssetDownloadViewModel.formatSize(state.totalSize)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (!state.isComplete && state.progress > 0f) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                )
            }
        }

        // Action
        if (!state.isComplete) {
            IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                FlaticonIcon(
                    glyph = FlaticonIcons.DOWNLOAD,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                )
            }
        } else if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                FlaticonIcon(
                    glyph = FlaticonIcons.DELETE,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
