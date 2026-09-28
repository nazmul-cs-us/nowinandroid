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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.shared.content.SharedDatabaseInfo

/**
 * The developer options body — the shared counterpart of Android's
 * DeveloperSettingsSection: one info card per content database with
 * item counts, sizes, and per-database refresh, plus Refresh All.
 */
@Composable
fun DeveloperSettingsSection(
    databases: List<SharedDatabaseInfo>,
    isLoading: Boolean,
    refreshingKey: String?,
    onRefresh: (String) -> Unit,
    onRefreshAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Database tools. Refreshing a CDN database drops its cached copy; the next read re-downloads it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.height(32.dp).padding(4.dp))
            }
        }
        databases.forEach { info ->
            DatabaseInfoCard(info = info, refreshing = refreshingKey == info.key, onRefresh = {
                onRefresh(info.key)
            })
            Spacer(Modifier.height(12.dp))
        }
        NiaOutlinedButton(
            onClick = onRefreshAll,
            enabled = !isLoading && refreshingKey == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (refreshingKey == "all") "Refreshing..." else "Refresh All Databases",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun DatabaseInfoCard(
    info: SharedDatabaseInfo,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(info.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${info.itemCount} ${info.itemLabel} · ${formatSize(info.sizeBytes)}" +
                        if (info.bundled) " · bundled" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (refreshing) {
                CircularProgressIndicator(Modifier.height(22.dp).padding(2.dp))
            } else {
                IconButton(onClick = onRefresh, enabled = !info.bundled) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Refresh ${info.label}",
                        tint = if (info.bundled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${bytes / (1024L * 1024L)} MB"
}
