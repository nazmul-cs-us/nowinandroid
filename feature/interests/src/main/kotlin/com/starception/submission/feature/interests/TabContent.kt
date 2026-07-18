/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.feature.interests

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.scrollbar.DraggableScrollbar
import com.starception.submission.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.starception.submission.core.designsystem.component.scrollbar.scrollbarState
import com.starception.submission.core.model.data.FollowableTopic
import com.starception.submission.core.ui.InterestsItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.height
import com.starception.submission.core.designsystem.theme.FloatingNavClearance

@Composable
fun TopicsTabContent(
    topics: List<FollowableTopic>,
    onTopicClick: (String) -> Unit,
    onFollowButtonClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    withBottomSpacer: Boolean = true,
    selectedTopicId: String? = null,
    shouldHighlightSelectedTopic: Boolean = false,
    isDraggable: Boolean = false,
    onReorder: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onReorderComplete: ((newOrder: List<String>) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        val lazyListState = rememberLazyListState()

        // Local mutable list for smooth drag reordering without triggering external updates
        val localTopics = remember(topics) { topics.toMutableStateList() }

        // Track if list was modified during drag
        var wasReordered by remember { mutableStateOf(false) }

        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            // Update local list immediately for smooth visual feedback
            localTopics.apply {
                add(to.index, removeAt(from.index))
            }
            wasReordered = true
        }

        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .testTag("interests:topics"),
            contentPadding = PaddingValues(vertical = 16.dp),
            state = lazyListState,
        ) {
            items(
                items = localTopics,
                key = { item -> item.topic.id }
            ) { followableTopic ->
                val topicId = followableTopic.topic.id
                val isSelected = shouldHighlightSelectedTopic && topicId == selectedTopicId

                ReorderableItem(reorderableLazyListState, key = topicId) { isDragging ->
                    InterestsItem(
                        name = followableTopic.topic.name,
                        following = followableTopic.isFollowed,
                        description = followableTopic.topic.shortDescription,
                        topicImageUrl = followableTopic.topic.imageUrl,
                        onClick = { onTopicClick(topicId) },
                        onFollowButtonClick = { onFollowButtonClick(topicId, it) },
                        isSelected = isSelected,
                        isDraggable = isDraggable,
                        isDragging = isDragging,
                        dragHandleModifier = if (isDraggable) {
                            Modifier.draggableHandle(
                                onDragStopped = {
                                    // Persist final order when drag ends
                                    if (wasReordered) {
                                        onReorderComplete?.invoke(localTopics.map { it.topic.id })
                                        wasReordered = false
                                    }
                                }
                            )
                        } else {
                            Modifier
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .longPressDraggableHandle(
                                onDragStopped = {
                                    // Persist final order when drag ends
                                    if (wasReordered) {
                                        onReorderComplete?.invoke(localTopics.map { it.topic.id })
                                        wasReordered = false
                                    }
                                }
                            ),
                    )
                }
            }

            if (withBottomSpacer) {
                item {
                    Spacer(Modifier.height(FloatingNavClearance))
                }
            }
        }
        val scrollbarState = lazyListState.scrollbarState(
            itemsAvailable = topics.size,
        )
        lazyListState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMoved = lazyListState.rememberDraggableScroller(
                itemsAvailable = topics.size,
            ),
        )
    }
}
