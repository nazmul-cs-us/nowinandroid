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

package com.starception.submission.core.model.deenly

/** Counts describe completed source reads, never model generation time. */
data class InsightSearchProgress(
    val title: String = "Preparing your reading",
    val detail: String = "Checking available downloaded sources…",
    val completed: Int = 0,
    val total: Int = 0,
    val sources: List<String> = emptyList(),
    val determinate: Boolean = false,
    val previewText: String? = null,
)
