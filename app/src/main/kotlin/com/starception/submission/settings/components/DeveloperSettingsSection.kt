package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Developer settings state for database refresh operations
 */
data class DeveloperSettingsState(
    val newsInfo: DatabaseDisplayInfo? = null,
    val topicsInfo: DatabaseDisplayInfo? = null,
    val duasInfo: DatabaseDisplayInfo? = null,
    val quranicDuasInfo: DatabaseDisplayInfo? = null,
    val isRefreshing: Boolean = false,
    val refreshingDatabase: String? = null,
    val lastRefreshResult: RefreshResult? = null
)

/**
 * Unified database display info
 */
data class DatabaseDisplayInfo(
    val name: String,
    val itemCount: Int,
    val itemLabel: String = "items",
    val lastModified: Long,
    val sizeBytes: Long
)

/**
 * Result of a refresh operation
 */
data class RefreshResult(
    val databaseName: String,
    val success: Boolean,
    val message: String
)

@Composable
fun DeveloperSettingsSection(
    state: DeveloperSettingsState,
    onRefreshNews: () -> Unit,
    onRefreshTopics: () -> Unit,
    onRefreshDuas: () -> Unit,
    onRefreshQuranicDuas: () -> Unit,
    onRefreshAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Warning banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Debug builds only. Refreshing will reload databases from assets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Refresh result message
        AnimatedVisibility(visible = state.lastRefreshResult != null) {
            state.lastRefreshResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // News Database Card
        state.newsInfo?.let { info ->
            DatabaseInfoCard(
                info = info,
                isRefreshing = state.isRefreshing && state.refreshingDatabase == "news",
                onRefresh = onRefreshNews,
                enabled = !state.isRefreshing
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Topics Database Card
        state.topicsInfo?.let { info ->
            DatabaseInfoCard(
                info = info,
                isRefreshing = state.isRefreshing && state.refreshingDatabase == "topics",
                onRefresh = onRefreshTopics,
                enabled = !state.isRefreshing
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Duas Database Card (Fortress of the Muslim)
        state.duasInfo?.let { info ->
            DatabaseInfoCard(
                info = info,
                isRefreshing = state.isRefreshing && state.refreshingDatabase == "duas",
                onRefresh = onRefreshDuas,
                enabled = !state.isRefreshing
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Quranic Duas Database Card
        state.quranicDuasInfo?.let { info ->
            DatabaseInfoCard(
                info = info,
                isRefreshing = state.isRefreshing && state.refreshingDatabase == "quranic_duas",
                onRefresh = onRefreshQuranicDuas,
                enabled = !state.isRefreshing
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Refresh All Button
        Button(
            onClick = onRefreshAll,
            enabled = !state.isRefreshing,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isRefreshing && state.refreshingDatabase == "all") {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refreshing All...")
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh All Databases")
            }
        }
    }
}

@Composable
private fun DatabaseInfoCard(
    info: DatabaseDisplayInfo,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${info.itemCount} ${info.itemLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Modified: ${formatDate(info.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Size: ${formatSize(info.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onRefresh,
                enabled = enabled && !isRefreshing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown"
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatSize(bytes: Long): String {
    if (bytes == 0L) return "Unknown"
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
