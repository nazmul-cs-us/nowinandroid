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

package com.starception.submission.shared.quran

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Applies tajweed rule colors to ayah text — the shared counterpart of
 * Android's TajweedTextApplier: annotations sort by start index, clamp
 * to the text, and invalid ranges are skipped.
 */
fun tajweedAnnotatedString(
    text: String,
    annotations: List<SharedTajweedAnnotation>?,
): AnnotatedString {
    if (annotations.isNullOrEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        annotations
            .filter { it.startIndex < it.endIndex }
            .sortedBy { it.startIndex }
            .forEach { annotation ->
                val color = SharedTajweedRules.colors[annotation.ruleKey] ?: return@forEach
                val start = annotation.startIndex.coerceIn(0, text.length)
                val end = annotation.endIndex.coerceIn(0, text.length)
                if (start >= end) return@forEach
                addStyle(
                    style = SpanStyle(color = Color(color)),
                    start = start,
                    end = end,
                )
            }
    }
}
