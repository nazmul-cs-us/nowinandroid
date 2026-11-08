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

package com.starception.submission.settings.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.ArtworkFrequency
import com.starception.submission.core.images.homeArtworkGallery
import com.starception.submission.core.images.homeArtworkIndex
import com.starception.submission.core.images.rememberHomeArtworkRotation
import com.starception.submission.core.images.rememberHomeArtworkSettings

@Composable
fun HomeArtworkSettingsSection() {
    val settings = rememberHomeArtworkSettings()
    val rotation = rememberHomeArtworkRotation()
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Home artwork", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Text("Rotate all five backgrounds through ${homeArtworkGallery.size} curated nature artworks, without people. Available offline.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ArtworkFrequency.entries.forEach { frequency ->
            val selected = settings.schedule.frequency == frequency
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = selected, role = Role.RadioButton) { settings.setFrequency(frequency) }
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(frequency.label, modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text("Daily changes at local midnight; weekly after seven days; monthly at the start of the next month. Keep current freezes today's set.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Digital image courtesy of Getty’s Open Content Program.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (page in 0..4) {
            val artwork = homeArtworkGallery[homeArtworkIndex(page, rotation)]
            TextButton(onClick = { uriHandler.openUri(artwork.sourceUrl) }) {
                Text("${artwork.title} · ${artwork.artist}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
