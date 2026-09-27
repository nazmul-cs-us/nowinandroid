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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.settings.VoiceRecognitionMode
import com.starception.submission.shared.voice.PlatformSpeechRecognizer
import com.starception.submission.shared.voice.SpeechRecognitionEvent

/**
 * App-level search state — the shared counterpart of Android's SearchPrefillBus:
 * one controller hosts the query, the overlay visibility, and the live voice
 * transcription so EVERY page opens the same search surface.
 */
class SharedSearchController internal constructor() {
    var active by mutableStateOf(false)
    var query by mutableStateOf("")
    var voiceActive by mutableStateOf(false)

    fun open() {
        active = true
    }

    fun close() {
        query = ""
        active = false
    }
}

/**
 * The app-level SearchView overlay + its voice flow. Renders on top of any
 * page; destinations come from the host so both the home and the other tabs
 * resolve results identically.
 */
@Composable
fun rememberSharedSearchController(
    onOpenSurah: (Int) -> Unit,
    onOpenBukhariBook: (Int) -> Unit,
    onOpenBukhariHadith: (Int) -> Unit,
    onOpenQuranicDua: (Int) -> Unit,
    onOpenFortressChapter: (Int) -> Unit,
    onRecordRecent: (String) -> Unit,
): SharedSearchController {
    val controller = remember { SharedSearchController() }
    val recognizer = remember { PlatformSpeechRecognizer() }

    fun startVoice() {
        controller.active = true
        controller.voiceActive = true
        recognizer.start(VoiceRecognitionMode.TRANSCRIPTION) { event ->
            when (event) {
                SpeechRecognitionEvent.Listening -> controller.voiceActive = true
                is SpeechRecognitionEvent.Partial -> controller.query = event.text
                is SpeechRecognitionEvent.Result -> {
                    controller.query = event.text
                    controller.voiceActive = false
                }
                is SpeechRecognitionEvent.Error -> controller.voiceActive = false
            }
        }
    }

    // expose the voice entry points through the controller's tag
    controller.tag = { startVoice() }
    LaunchedEffect(controller.active) {
        if (!controller.active) {
            recognizer.stop()
            controller.voiceActive = false
        }
    }
    DisposableEffect(controller) {
        onDispose { recognizer.stop() }
    }
    // Keep destination callbacks fresh without recreating the controller.
    controller.destinations = SharedSearchDestinations(
        onOpenSurah = onOpenSurah,
        onOpenBukhariBook = onOpenBukhariBook,
        onOpenBukhariHadith = onOpenBukhariHadith,
        onOpenQuranicDua = onOpenQuranicDua,
        onOpenFortressChapter = onOpenFortressChapter,
        onRecordRecent = onRecordRecent,
    )
    return controller
}

internal class SharedSearchDestinations(
    var onOpenSurah: (Int) -> Unit,
    var onOpenBukhariBook: (Int) -> Unit,
    var onOpenBukhariHadith: (Int) -> Unit,
    var onOpenQuranicDua: (Int) -> Unit,
    var onOpenFortressChapter: (Int) -> Unit,
    var onRecordRecent: (String) -> Unit,
)

/** Opens search with live voice capture — the FAB and mic behavior. */
fun SharedSearchController.openVoice() {
    (tag as? () -> Unit)?.invoke()
}

internal var SharedSearchController.tag: Any? by mutableStateOf(null)

internal var SharedSearchController.destinations: SharedSearchDestinations? by mutableStateOf(null)

/**
 * The full-screen SearchView surface: back arrow + input toolbar with the
 * suggestions scrolling underneath — drawn over whatever page is showing.
 */
@Composable
fun SharedSearchOverlay(controller: SharedSearchController, onOpenProfile: () -> Unit = {}) {
    if (!controller.active) return
    val destinations = controller.destinations ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconTapTarget(
                    icon = NiaIcons.ArrowBack,
                    contentDescription = "Close search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    visualSize = 34.dp,
                    iconSize = 22.dp,
                    showBackground = false,
                    onClick = { controller.close() },
                )
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .focusRequester(focusRequester),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = NiaIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        BasicTextField(
                            value = controller.query,
                            onValueChange = { controller.query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerField ->
                                Box(Modifier.weight(1f)) {
                                    if (controller.query.isEmpty()) {
                                        Text(
                                            "Search Quran, Hadith and more",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerField()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (controller.query.isNotEmpty()) {
                            IconTapTarget(
                                icon = Icons.Filled.Close,
                                contentDescription = "Clear query",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                visualSize = 30.dp,
                                iconSize = 20.dp,
                                showBackground = false,
                                onClick = { controller.query = "" },
                            )
                        }
                        IconTapTarget(
                            icon = Icons.Filled.Mic,
                            contentDescription = if (controller.voiceActive) {
                                "Stop voice search"
                            } else {
                                "Voice search"
                            },
                            tint = if (controller.voiceActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            visualSize = 30.dp,
                            iconSize = 20.dp,
                            showBackground = false,
                            onClick = {
                                if (controller.voiceActive) {
                                    controller.voiceActive = false
                                } else {
                                    controller.openVoice()
                                }
                            },
                        )
                        if (controller.voiceActive) {
                            Text(
                                "Listening…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                SearchSuggestionsOverlayContent(
                    query = controller.query,
                    onOpenSurah = {
                        controller.close()
                        destinations.onOpenSurah(it)
                    },
                    onOpenBukhariBook = {
                        controller.close()
                        destinations.onOpenBukhariBook(it)
                    },
                    onOpenBukhariHadith = {
                        controller.close()
                        destinations.onOpenBukhariHadith(it)
                    },
                    onOpenQuranicDua = {
                        controller.close()
                        destinations.onOpenQuranicDua(it)
                    },
                    onOpenFortressChapter = {
                        controller.close()
                        destinations.onOpenFortressChapter(it)
                    },
                    onRecordRecent = destinations.onRecordRecent,
                )
            }
        }
    }
}

/**
 * The tab pages' inline search header — Android's AppTopSearchBar on every
 * destination: the pill opens the shared overlay, the mic opens it with live
 * transcription.
 */
@Composable
fun SharedTabSearchHeader(
    controller: SharedSearchController,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .semantics {
                contentDescription = "Page search"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Surface(
            onClick = { controller.open() },
            modifier = Modifier
                .weight(1.6f)
                .heightIn(min = 44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = NiaIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Search Quran, Hadith and more",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconTapTarget(
                    icon = Icons.Filled.Mic,
                    contentDescription = "Voice search",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    visualSize = 30.dp,
                    iconSize = 18.dp,
                    showBackground = false,
                    onClick = { controller.openVoice() },
                )
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}
