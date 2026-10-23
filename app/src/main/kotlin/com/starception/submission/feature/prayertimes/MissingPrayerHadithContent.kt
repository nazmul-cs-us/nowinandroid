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

package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.model.data.HadithCollection
import com.starception.submission.core.model.data.HadithCollections
import com.starception.submission.download.MissingContentCard
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The existing inline asset downloader, offered only after an empty prayer search. */
@Composable
internal fun MissingPrayerHadithContent(onDownloadComplete: () -> Unit) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf<List<HadithCollection>?>(null) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        missing = withContext(Dispatchers.IO) {
            HadithCollections.all.filterNot { HadithDatabase.isDatabaseAvailable(context, it.databaseFile) }
        }
    }
    val collections = missing ?: return
    if (collections.isEmpty()) {
        Text("All Hadith collections are available. No matching narration was found for this prayer.", color = Color.White)
        return
    }
    val selected = collections.firstOrNull { it.databaseFile == selectedFile } ?: collections.first()
    val downloadManager = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, SherpaOnnxTtsEntryPoint::class.java)
            .assetDownloadManager()
    }
    Column(Modifier.fillMaxWidth()) {
        Text("Download more Hadith collections", color = Color.White)
        TextButton(onClick = { choosing = true }) { Text("Choose collection: ${selected.name}") }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            collections.forEach { collection ->
                DropdownMenuItem(
                    text = { Text(collection.name) },
                    onClick = { selectedFile = collection.databaseFile; choosing = false },
                )
            }
        }
        MissingContentCard(
            resourceName = selected.name,
            category = selected.downloadCategory,
            description = "Add this collection to search for a Hadith relevant to the current prayer.",
            downloadManager = downloadManager,
            onDownloadComplete = onDownloadComplete,
        )
    }
}
