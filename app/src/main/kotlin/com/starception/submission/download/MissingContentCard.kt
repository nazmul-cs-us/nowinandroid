package com.starception.submission.download

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable composable that shows an inline download prompt when a CDN resource is missing.
 * Displays resource name, size, description, a download button, and progress during download.
 * Calls [onDownloadComplete] when the download finishes so the parent can reload content.
 */
@Composable
fun MissingContentCard(
    resourceName: String,
    category: String,
    description: String,
    downloadManager: AssetDownloadManager,
    onDownloadComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf<DownloadCardState>(DownloadCardState.Idle) }
    var categorySize by remember { mutableLongStateOf(0L) }

    // Load manifest to get size info
    LaunchedEffect(category) {
        withContext(Dispatchers.IO) {
            try {
                val manifest = downloadManager.loadManifest()
                if (manifest != null) {
                    categorySize = manifest.categories[category]?.totalSize ?: 0L
                }
            } catch (e: Exception) {
                Log.e("MissingContentCard", "Failed to load manifest", e)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = resourceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            when (val state = downloadState) {
                is DownloadCardState.Idle -> {
                    val sizeText = if (categorySize > 0) {
                        " (${formatSize(categorySize)})"
                    } else ""

                    Button(
                        onClick = {
                            downloadState = DownloadCardState.Downloading(0f)
                            scope.launch {
                                try {
                                    val manifest = withContext(Dispatchers.IO) {
                                        downloadManager.loadManifest()
                                    }
                                    if (manifest == null) {
                                        downloadState = DownloadCardState.Failed("No manifest available")
                                        return@launch
                                    }
                                    withContext(Dispatchers.IO) {
                                        downloadManager.downloadCategory(category, manifest) { progress, _, _ ->
                                            downloadState = DownloadCardState.Downloading(progress)
                                        }
                                    }
                                    downloadState = DownloadCardState.Completed
                                    onDownloadComplete()
                                } catch (e: Exception) {
                                    Log.e("MissingContentCard", "Download failed", e)
                                    downloadState = DownloadCardState.Failed(
                                        e.message ?: "Download failed"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download$sizeText")
                    }
                }

                is DownloadCardState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Downloading... ${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is DownloadCardState.Completed -> {
                    // Brief completion state - onDownloadComplete already called
                    Text(
                        text = "Download complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is DownloadCardState.Failed -> {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = { downloadState = DownloadCardState.Idle },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

private sealed class DownloadCardState {
    data object Idle : DownloadCardState()
    data class Downloading(val progress: Float) : DownloadCardState()
    data object Completed : DownloadCardState()
    data class Failed(val error: String) : DownloadCardState()
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes.toFloat() / (1024 * 1024))
    else -> "%.2f GB".format(bytes.toFloat() / (1024 * 1024 * 1024))
}
