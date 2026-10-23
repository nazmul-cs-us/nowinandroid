/*
 * Copyright 2021 The Android Open Source Project
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val LocalContentDownloadState = compositionLocalOf { ContentStorageState() }
internal val LocalContentDownloadActions = compositionLocalOf<ContentStorageActions?> { null }

/** Uses the same category jobs, verification, cancellation and state as Content & Storage. */
@Composable
internal fun MissingContentDownloadCard(
    category: String,
    resourceName: String,
    description: String = "Download this content to use it offline.",
    onDownloadComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = LocalContentDownloadActions.current ?: return
    val state = LocalContentDownloadState.current
    val status = state.categories.firstOrNull { it.categoryKey == category }
    var requested by remember(category) { mutableStateOf(false) }
    var wasUnavailable by remember(category) { mutableStateOf(status?.isAvailable != true) }
    LaunchedEffect(category) { if (state.categories.isEmpty()) actions.onRefresh() }
    LaunchedEffect(category, status?.isAvailable, requested) {
        if ((requested || wasUnavailable) && status?.isAvailable == true) {
            requested = false
            wasUnavailable = false
            onDownloadComplete()
        } else if (status?.isAvailable == false) {
            wasUnavailable = true
        }
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(resourceName, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (status?.isDownloading == true) {
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                Text("Downloading… ${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { actions.onCancelDownload(category) }) { Text("Cancel") }
            } else {
                val error = state.error.takeIf { state.retryCategoryKey == category }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = !state.isLoading,
                        onClick = {
                            if (status?.isAvailable == true) onDownloadComplete()
                            else { requested = true; actions.onDownloadCategory(category) }
                        },
                    ) {
                        Text(when {
                            status?.isAvailable == true -> "Reload content"
                            error != null -> "Retry download"
                            state.isLoading -> "Checking download…"
                            else -> "Download"
                        })
                    }
                }
            }
        }
    }
}

@Composable
internal fun MissingHadithCollectionsDownloadCard(onDownloadComplete: () -> Unit) {
    val state = LocalContentDownloadState.current
    val collections = com.starception.submission.core.model.data.HadithCollections.all.filter { collection ->
        state.categories.firstOrNull { it.categoryKey == collection.downloadCategory }?.isAvailable != true
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val collection = com.starception.submission.core.model.data.HadithCollections.byDatabaseFile(selected.orEmpty())
        ?: collections.firstOrNull()
    if (collection == null) {
        Text("All Hadith collections are available. No matching narration was found for this prayer.", color = androidx.compose.ui.graphics.Color.White)
        return
    }
    LaunchedEffect(collection.databaseFile) { if (selected == null) selected = collection.databaseFile }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = true }) { Text("Choose collection: ${collection.name}") }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            collections.forEach { candidate ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(candidate.name) },
                    onClick = { selected = candidate.databaseFile; expanded = false },
                )
            }
        }
        // Keep the chosen card stable through completion so its callback can
        // reload the prayer search before moving on to another missing book.
        MissingContentDownloadCard(collection.downloadCategory, collection.name,
            "Add this collection to search for a Hadith relevant to the current prayer.",
            onDownloadComplete = { onDownloadComplete(); selected = null })
    }
}

@Composable
internal fun TopicContentDownloadPrompt(topicId: Int, onDownloadComplete: () -> Unit) {
    val collection = com.starception.submission.shared.content.hadithCollectionDatabaseForTopic[topicId]
        ?.let(com.starception.submission.core.model.data.HadithCollections::byDatabaseFile)
    val category = when (topicId) {
        7 -> "quran_core"
        8 -> "json_data"
        9 -> "hadith_shamayele_tirmidhi"
        else -> collection?.downloadCategory ?: return
    }
    val name = when (topicId) {
        7 -> "Holy Quran"
        8 -> "Sahih al-Bukhari topic content"
        9 -> "Shama’il At-Tirmidhi"
        else -> collection?.name ?: return
    }
    val available = LocalContentDownloadState.current.categories.firstOrNull { it.categoryKey == category }?.isAvailable == true
    var wasMissing by remember(category) { mutableStateOf(!available) }
    LaunchedEffect(category, available) {
        if (available && wasMissing) { wasMissing = false; onDownloadComplete() }
        else if (!available) wasMissing = true
    }
    if (!available) MissingContentDownloadCard(category, name, onDownloadComplete = onDownloadComplete)
}
