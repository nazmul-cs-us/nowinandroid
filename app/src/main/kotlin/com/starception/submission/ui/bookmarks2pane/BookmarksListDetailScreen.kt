/*
 * Copyright 2024 The Android Open Source Project
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

package com.starception.submission.ui.bookmarks2pane

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldPredictiveBackHandler
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.starception.submission.core.model.data.UserNewsResource
import com.starception.submission.core.ui.extractHadithInfo
import com.starception.submission.core.ui.extractSurahNumber
import com.starception.submission.feature.bookmarks.BookmarksRoute
import com.starception.submission.feature.bookmarks.navigation.BookmarksRoute as BookmarksNavRoute
import com.starception.submission.feature.dua.DuaDetailScreen
import com.starception.submission.feature.hadith.HadithDetailScreen
import com.starception.submission.feature.surah.SurahDetailScreen
import com.starception.submission.ui.foryou2pane.NewsResourceDetailPane
import com.starception.submission.ui.foryou2pane.NewsResourceDetailPlaceholder
import kotlinx.coroutines.launch
import kotlin.math.max

fun NavGraphBuilder.bookmarksListDetailScreen(
    titleRes: Int,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTopicClick: (String) -> Unit = {},
    onShowSnackbar: suspend (String, String?) -> Boolean,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource, String) -> Unit = { _, _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
) {
    composable<BookmarksNavRoute> {
        val isLandscape = LocalConfiguration.current.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        com.starception.submission.ui.TopLevelTopBarScaffold(
            titleRes = titleRes,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            // Two-pane in landscape provides its own chrome.
            showTopBar = !isLandscape,
        ) {
            BookmarksListDetailScreen(
                onTopicClick = onTopicClick,
                onShowSnackbar = onShowSnackbar,
                onSurahClick = onSurahClick,
                onDuaClick = onDuaClick,
                onHadithClick = onHadithClick,
            )
        }
    }
}

@Composable
internal fun BookmarksListDetailScreen(
    viewModel: Bookmarks2PaneViewModel = hiltViewModel(),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    onTopicClick: (String) -> Unit = {},
    onShowSnackbar: suspend (String, String?) -> Boolean,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource, String) -> Unit = { _, _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
) {
    val selectedNewsResource by viewModel.selectedNewsResource.collectAsStateWithLifecycle()

    // Check if we're in landscape mode for two-pane layout
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BookmarksListDetailScreen(
        selectedNewsResource = selectedNewsResource,
        onNewsResourceClick = if (isLandscape) {{ viewModel.onNewsResourceClick(it.id) }} else null,
        onToggleBookmark = { viewModel.toggleBookmark(it) },
        onBackClick = { viewModel.clearSelection() },
        onTopicClick = onTopicClick,
        windowAdaptiveInfo = windowAdaptiveInfo,
        onShowSnackbar = onShowSnackbar,
        onSurahClick = onSurahClick,
        onDuaClick = onDuaClick,
        onHadithClick = onHadithClick,
        isLandscape = isLandscape,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun BookmarksListDetailScreen(
    selectedNewsResource: UserNewsResource?,
    onNewsResourceClick: ((UserNewsResource) -> Unit)?,
    onToggleBookmark: (String) -> Unit,
    onBackClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    windowAdaptiveInfo: WindowAdaptiveInfo,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource, String) -> Unit = { _, _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
    isLandscape: Boolean = false,
) {
    // In portrait mode, just show BookmarksRoute without two-pane scaffold
    if (!isLandscape) {
        BookmarksRoute(
            onTopicClick = onTopicClick,
            onShowSnackbar = onShowSnackbar,
            onSurahClick = onSurahClick,
            onDuaClick = { userNewsResource ->
                val topicId = userNewsResource.followableTopics.firstOrNull()?.topic?.id ?: ""
                onDuaClick(userNewsResource, topicId)
            },
            onHadithClick = onHadithClick,
            onNewsClick = null, // Use default navigation in portrait
        )
        return
    }

    // Landscape mode: Two-pane layout
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedNewsResource != null
            },
        ),
    )
    val coroutineScope = rememberCoroutineScope()

    val paneExpansionState = rememberPaneExpansionState(
        anchors = listOf(
            PaneExpansionAnchor.Proportion(0f),
            PaneExpansionAnchor.Proportion(0.5f),
            PaneExpansionAnchor.Proportion(1f),
        ),
    )

    ThreePaneScaffoldPredictiveBackHandler(
        listDetailNavigator,
        BackNavigationBehavior.PopUntilScaffoldValueChange,
    )
    BackHandler(
        paneExpansionState.currentAnchor == PaneExpansionAnchor.Proportion(0f) &&
            listDetailNavigator.isListPaneVisible() &&
            listDetailNavigator.isDetailPaneVisible(),
    ) {
        coroutineScope.launch {
            paneExpansionState.animateTo(PaneExpansionAnchor.Proportion(1f))
        }
    }

    fun onNewsResourceClickShowDetailPane(newsResource: UserNewsResource) {
        onNewsResourceClick?.invoke(newsResource)
        coroutineScope.launch {
            listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
        }
        if (paneExpansionState.currentAnchor == PaneExpansionAnchor.Proportion(1f)) {
            coroutineScope.launch {
                paneExpansionState.animateTo(PaneExpansionAnchor.Proportion(0f))
            }
        }
    }

    val mutableInteractionSource = remember { MutableInteractionSource() }
    val minPaneWidth = 300.dp

    NavigableListDetailPaneScaffold(
        navigator = listDetailNavigator,
        listPane = {
            AnimatedPane {
                Box(
                    modifier = Modifier.clipToBounds()
                        .layout { measurable, constraints ->
                            val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = minPaneWidth.roundToPx(),
                                    maxWidth = width,
                                    minHeight = constraints.maxHeight,
                                    maxHeight = constraints.maxHeight,
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeRelative(
                                    x = 0,
                                    y = 0,
                                )
                            }
                        },
                ) {
                    BookmarksRoute(
                        onTopicClick = onTopicClick,
                        onShowSnackbar = onShowSnackbar,
                        onSurahClick = onSurahClick,
                        onDuaClick = { userNewsResource ->
                            val topicId = userNewsResource.followableTopics.firstOrNull()?.topic?.id ?: ""
                            onDuaClick(userNewsResource, topicId)
                        },
                        onHadithClick = onHadithClick,
                        onNewsClick = ::onNewsResourceClickShowDetailPane,
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane {
                Box(
                    modifier = Modifier.clipToBounds()
                        .layout { measurable, constraints ->
                            val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = minPaneWidth.roundToPx(),
                                    maxWidth = width,
                                    minHeight = constraints.maxHeight,
                                    maxHeight = constraints.maxHeight,
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeRelative(
                                    x = constraints.maxWidth -
                                        max(constraints.maxWidth, placeable.width),
                                    y = 0,
                                )
                            }
                        },
                ) {
                    DetailPaneContent(
                        newsResource = selectedNewsResource,
                        onBackClick = {
                            onBackClick()
                            coroutineScope.launch {
                                listDetailNavigator.navigateBack()
                            }
                        },
                        onToggleBookmark = onToggleBookmark,
                        onTopicClick = onTopicClick,
                        showBackButton = !listDetailNavigator.isListPaneVisible(),
                    )
                }
            }
        },
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = {
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(
                    state = paneExpansionState,
                    minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                    interactionSource = mutableInteractionSource,
                    semanticsProperties = paneExpansionState.defaultDragHandleSemantics(),
                ),
                interactionSource = mutableInteractionSource,
            )
        },
    )
}

/**
 * Detail pane content that shows the appropriate screen based on news resource type
 */
@Composable
private fun DetailPaneContent(
    newsResource: UserNewsResource?,
    onBackClick: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    showBackButton: Boolean,
) {
    if (newsResource == null) {
        NewsResourceDetailPlaceholder()
        return
    }

    // Detect news resource type
    val surahNumber = extractSurahNumber(
        title = newsResource.title,
        url = newsResource.url,
        type = newsResource.type,
    )
    val isDuaItem = newsResource.type.contains("Dua", ignoreCase = true)
    val isHadithItem = newsResource.type.contains("Hadith", ignoreCase = true)
    val hadithInfo = if (isHadithItem) extractHadithInfo(newsResource.url) else null

    AnimatedContent(
        targetState = newsResource.id,
        label = "DetailPaneAnimation",
    ) { _ ->
        when {
            // Surah detail
            surahNumber != null -> {
                SurahDetailScreen(
                    surahNumber = surahNumber,
                    newsResourceId = newsResource.id,
                    onBackClick = onBackClick,
                    onTopicClick = onTopicClick,
                )
            }
            // Hadith detail
            isHadithItem && hadithInfo != null -> {
                val collectionName = hadithInfo.first.removeSuffix(".db")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                HadithDetailScreen(
                    collectionName = collectionName,
                    hadithNumber = hadithInfo.second,
                    databaseFile = hadithInfo.first,
                    onBackClick = onBackClick,
                )
            }
            // Dua detail
            isDuaItem -> {
                val duaNumber = Regex("Dua (\\d+)").find(newsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("#(\\d+)").find(newsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                DuaDetailScreen(
                    title = newsResource.title,
                    content = newsResource.content,
                    onBackClick = onBackClick,
                    initialNewsResourceId = newsResource.id,
                    isNiaBookmarked = { newsResource.isSaved },
                    onToggleNiaBookmark = { onToggleBookmark(newsResource.id) },
                    topicId = newsResource.followableTopics.firstOrNull()?.topic?.id ?: "",
                    onTopicClick = onTopicClick,
                )
            }
            // Regular news resource
            else -> {
                NewsResourceDetailPane(
                    userNewsResource = newsResource,
                    isBookmarked = newsResource.isSaved,
                    onToggleBookmark = { onToggleBookmark(newsResource.id) },
                    onTopicClick = onTopicClick,
                    showBackButton = showBackButton,
                    onBackClick = onBackClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun <T> ThreePaneScaffoldNavigator<T>.isListPaneVisible(): Boolean =
    scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun <T> ThreePaneScaffoldNavigator<T>.isDetailPaneVisible(): Boolean =
    scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
