package com.starception.submission.download

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.designsystem.animation.NiaTransitions

@Composable
fun AssetDownloadScreen(
    viewModel: AssetDownloadViewModel,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isReady = screenState is DownloadScreenState.AllReady

    LaunchedEffect(isReady) {
        if (isReady) onReady()
    }

    AnimatedContent(
        targetState = screenState,
        // Download progress produces a new NeedsDownload data object for every
        // update. Keep all of those objects on one stable content key so only
        // the progress UI recomposes; animate only real screen-phase changes.
        contentKey = { state ->
            when (state) {
                is DownloadScreenState.Loading -> DownloadScreenPhase.Loading
                is DownloadScreenState.AllReady -> DownloadScreenPhase.AllReady
                is DownloadScreenState.Error -> DownloadScreenPhase.Error
                is DownloadScreenState.NeedsDownload -> DownloadScreenPhase.NeedsDownload
            }
        },
        transitionSpec = {
            NiaTransitions.fadeThroughEnter() togetherWith NiaTransitions.fadeThroughExit()
        },
        label = "screen_state",
    ) { state ->
        when (state) {
            is DownloadScreenState.Loading -> {
                LoadingContent(modifier)
            }
            is DownloadScreenState.AllReady -> {
                // onReady() fires from the LaunchedEffect above; nothing to draw here.
            }
            is DownloadScreenState.Error -> {
                ErrorContent(
                    message = state.message,
                    modifier = modifier,
                )
            }
            is DownloadScreenState.NeedsDownload -> {
                DownloadContent(
                    state = state,
                    onDownloadRequired = viewModel::downloadRequired,
                    onDownloadCategory = viewModel::downloadCategory,
                    onDeleteCategory = viewModel::deleteCategory,
                    onSkip = {
                        viewModel.skipOptionalDownloads()
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

private enum class DownloadScreenPhase {
    Loading,
    AllReady,
    Error,
    NeedsDownload,
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Checking content...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Unable to load content manifest",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DownloadContent(
    state: DownloadScreenState.NeedsDownload,
    onDownloadRequired: () -> Unit,
    onDownloadCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requiredCategories = state.categories.filter { it.required }
    val optionalCategories = state.categories.filter { !it.required }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .displayCutoutPadding()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Content Setup",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Download content for the best experience.\nRequired content enables core features.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Overall progress bar when downloading
            if (state.isDownloading) {
                item {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.overallProgress,
                        animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "overall_progress",
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Downloading... ${(state.overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Error message
            if (state.error != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = state.error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // Required section
            if (requiredCategories.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Required",
                        subtitle = AssetDownloadViewModel.formatSize(state.totalRequiredSize),
                    )
                }
                items(requiredCategories, key = { it.categoryKey }) { cat ->
                    CategoryCard(
                        state = cat,
                        onDownload = { onDownloadCategory(cat.categoryKey) },
                        onDelete = null,
                    )
                }
            }

            // Optional section with grouped sub-headers
            if (optionalCategories.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Optional",
                        subtitle = AssetDownloadViewModel.formatSize(state.totalOptionalSize),
                    )
                }

                // Group categories by their group name
                val grouped = optionalCategories.groupBy {
                    AssetDownloadViewModel.categoryGroup(it.categoryKey)
                }

                // Ungrouped items first (empty group key)
                val ungrouped = grouped[""] ?: emptyList()
                items(ungrouped, key = { it.categoryKey }) { cat ->
                    CategoryCard(
                        state = cat,
                        onDownload = { onDownloadCategory(cat.categoryKey) },
                        onDelete = if (cat.isComplete) {
                            { onDeleteCategory(cat.categoryKey) }
                        } else null,
                    )
                }

                // Then grouped items with collapsible sub-headers
                for ((group, cats) in grouped.filter { it.key.isNotEmpty() }) {
                    val groupSize = cats.sumOf { it.totalSize }
                    val isExpanded = expandedGroups[group] ?: false
                    item(key = "group_$group") {
                        CollapsibleGroupHeader(
                            title = group,
                            subtitle = AssetDownloadViewModel.formatSize(groupSize),
                            itemCount = cats.size,
                            isExpanded = isExpanded,
                            onToggle = {
                                expandedGroups[group] = !isExpanded
                            },
                        )
                    }
                    if (isExpanded) {
                        items(cats, key = { it.categoryKey }) { cat ->
                            Box(modifier = Modifier.animateItem()) {
                                CategoryCard(
                                    state = cat,
                                    onDownload = { onDownloadCategory(cat.categoryKey) },
                                    onDelete = if (cat.isComplete) {
                                        { onDeleteCategory(cat.categoryKey) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Bottom action bar
        BottomActionBar(
            requiredComplete = state.requiredComplete,
            isDownloading = state.isDownloading,
            onDownloadRequired = onDownloadRequired,
            onContinue = onSkip,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($subtitle)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CollapsibleGroupHeader(
    title: String,
    subtitle: String,
    itemCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "chevron_rotation",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotationAngle),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (!isExpanded) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($itemCount items)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun CategoryCard(
    state: CategoryDownloadState,
    onDownload: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isComplete) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else if (state.isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Not downloaded",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.description.isNotEmpty()) {
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = AssetDownloadViewModel.formatSize(state.totalSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (state.required) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Required",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // Progress bar for partially downloaded
                if (!state.isComplete && state.progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }

            // Action buttons
            if (!state.isComplete) {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download ${state.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete ${state.displayName}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    requiredComplete: Boolean,
    isDownloading: Boolean,
    onDownloadRequired: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!requiredComplete) {
            NiaOutlinedButton(
                onClick = onDownloadRequired,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Downloading...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Continue/Skip button
        if (requiredComplete) {
            NiaOutlinedButton(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
