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

package com.starception.submission.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.model.deenly.InsightSearchProgress

@Composable
fun InsightSearchStatus(progress: InsightSearchProgress, modifier: Modifier = Modifier) {
    val accent = Color(0xFFE3C078)
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(progress.title, color = Color.White, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
        Text(progress.detail, color = Color.White.copy(alpha = .8f), fontSize = 14.sp, lineHeight = 21.sp)
        if (progress.determinate && progress.total > 0) {
            LinearProgressIndicator(progress = { (progress.completed.toFloat() / progress.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = accent, trackColor = Color.White.copy(alpha = .12f))
            Text("${progress.completed} of ${progress.total} checked", color = accent, style = MaterialTheme.typography.labelMedium)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent, trackColor = Color.White.copy(alpha = .12f))
        }
        progress.previewText?.takeIf(String::isNotBlank)?.let { passage ->
            Text("PASSAGE UNDER REVIEW", color = accent, style = MaterialTheme.typography.labelSmall)
            Text(passage, color = Color.White.copy(alpha = .9f), fontSize = 17.sp, lineHeight = 26.sp,
                maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (progress.sources.isNotEmpty()) {
            Text("Sources · " + progress.sources.distinct().joinToString(" · "),
                color = Color.White.copy(alpha = .6f), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
