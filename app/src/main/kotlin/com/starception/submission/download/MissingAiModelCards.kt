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

package com.starception.submission.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.starception.submission.ml.DeenlyKnowledgeModel
import com.starception.submission.ml.DeenlyKnowledgeTask
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders one [MissingContentCard] per Now Nudge GGUF that [tasks] requires but the user has
 * not downloaded yet — the same on-demand download UX as the other missing-asset surfaces.
 * No-op once every required model is present, so AI features can embed it unconditionally.
 *
 * [onDownloadComplete] fires after a download finishes so the parent can retry generation;
 * this composable also re-checks presence itself, so a stale card never lingers.
 */
@Composable
fun MissingAiModelCards(
    tasks: Set<DeenlyKnowledgeTask>,
    onDownloadComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    val context = LocalContext.current
    val downloadManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SherpaOnnxTtsEntryPoint::class.java,
        ).assetDownloadManager()
    }

    var missingModels by remember(tasks) {
        mutableStateOf<List<MissingAiModelSpec>?>(null)
    }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(tasks, reloadTrigger) {
        missingModels = withContext(Dispatchers.IO) {
            val needsQuestion = tasks.any { it != DeenlyKnowledgeTask.ANSWER }
            val needsAnswer = DeenlyKnowledgeTask.ANSWER in tasks
            buildList {
                if (needsQuestion && !DeenlyKnowledgeModel.isModelDownloaded(
                        context,
                        DeenlyKnowledgeTask.KNOWLEDGE,
                    )
                ) {
                    add(
                        MissingAiModelSpec(
                            resourceName = "Now Nudge Question Model",
                            category = "model_now_nudge",
                            description = "Creates source-grounded questions locally " +
                                "without sending content off-device",
                        ),
                    )
                }
                if (needsAnswer && !DeenlyKnowledgeModel.isModelDownloaded(
                        context,
                        DeenlyKnowledgeTask.ANSWER,
                    )
                ) {
                    add(
                        MissingAiModelSpec(
                            resourceName = "Now Nudge Knowledge Model",
                            category = "model_now_nudge_knowledge",
                            description = "Answers your questions from the downloaded " +
                                "Quran and Hadith, entirely on-device",
                        ),
                    )
                }
            }
        }
    }

    val missing = missingModels ?: return
    if (missing.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        missing.forEach { spec ->
            MissingContentCard(
                resourceName = spec.resourceName,
                category = spec.category,
                description = spec.description,
                downloadManager = downloadManager,
                onDownloadComplete = {
                    reloadTrigger++
                    onDownloadComplete()
                },
                modifier = Modifier,
            )
        }
    }
}

private data class MissingAiModelSpec(
    val resourceName: String,
    val category: String,
    val description: String,
)
