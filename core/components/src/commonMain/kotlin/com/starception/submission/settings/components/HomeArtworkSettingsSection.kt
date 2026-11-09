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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.ArtworkFrequency
import com.starception.submission.core.images.HomeArtwork
import com.starception.submission.core.images.homeArtworkGallery
import com.starception.submission.core.images.homeArtworkIndex
import com.starception.submission.core.images.rememberHomeArtworkState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.starception.submission.core.images.rememberHomeArtworkSettings
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeArtworkSettingsSection() {
    val settings = rememberHomeArtworkSettings()
    val artworkState = rememberHomeArtworkState()
    val rotation = artworkState.rotation
    val frequency = settings.schedule.frequency
    val artworks = (0..4).map { homeArtworkGallery[homeArtworkIndex(it, rotation)] }
    val nextUpdateLabel = if (frequency == ArtworkFrequency.HOURLY) {
        artworkState.nextUpdateAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.let {
            "Next update at ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
        }
    } else null
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.testTag("homeArtworkSettings"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Home artwork",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { showDetails = true }
                        .semantics { contentDescription = "Preview today's five home artworks" }
                        .testTag("homeArtworkPreview"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    artworks.forEach { artwork ->
                        Image(
                            painter = painterResource(artwork.day),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your home collection", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium)
                    Text("Islamic architecture, patterns and quiet nature.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ArtworkAvailability()
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stackControls = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f
                    if (stackControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArtworkFrequencyLabel(frequency, nextUpdateLabel = nextUpdateLabel)
                            ArtworkFrequencyPicker(frequency, settings::setFrequency, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ArtworkFrequencyLabel(frequency, Modifier.weight(1f), nextUpdateLabel)
                            ArtworkFrequencyPicker(frequency, settings::setFrequency)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f) {
                        Column {
                            ArtworkRefreshButton(settings::refreshNow, Modifier.fillMaxWidth())
                            TextButton(onClick = { showDetails = true },
                                modifier = Modifier.align(Alignment.End).testTag("homeArtworkDetails")) {
                                Text("About the artwork", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            ArtworkRefreshButton(settings::refreshNow)
                            TextButton(onClick = { showDetails = true }, modifier = Modifier.testTag("homeArtworkDetails")) {
                                Text("About the artwork", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDetails) ArtworkDetailsDialog(artworks, onDismiss = { showDetails = false })
}

@Composable
private fun ArtworkFrequencyLabel(
    frequency: ArtworkFrequency,
    modifier: Modifier = Modifier,
    nextUpdateLabel: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Change artwork", style = MaterialTheme.typography.bodyMedium)
        Text(nextUpdateLabel ?: frequency.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtworkFrequencyPicker(
    frequency: ArtworkFrequency,
    onSelected: (ArtworkFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = modifier
            .testTag("homeArtworkFrequency"), shape = RoundedCornerShape(12.dp)) {
            Text(frequency.shortLabel, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Outlined.ExpandMore, contentDescription = null,
                modifier = Modifier.padding(start = 6.dp).size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ArtworkFrequency.entries.forEach { option ->
                val isSelected = option == frequency
                DropdownMenuItem(
                    text = { Text(option.label) },
                    modifier = Modifier.semantics { selected = isSelected },
                    onClick = { onSelected(option); expanded = false },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}

@Composable
private fun ArtworkAvailability() {
    Text("${homeArtworkGallery.size} artworks · Offline",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ArtworkRefreshButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.testTag("homeArtworkRefreshNow"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Outlined.Refresh, contentDescription = null,
            modifier = Modifier.padding(end = 6.dp).size(16.dp))
        Text("Refresh now", style = MaterialTheme.typography.labelMedium)
    }
}

private val ArtworkFrequency.shortLabel: String
    get() = when (this) {
        ArtworkFrequency.HOURLY -> "Hourly"
        ArtworkFrequency.DAILY -> "Daily"
        ArtworkFrequency.WEEKLY -> "Weekly"
        ArtworkFrequency.MONTHLY -> "Monthly"
        ArtworkFrequency.FIXED -> "Keep current"
    }

private val ArtworkFrequency.description: String
    get() = when (this) {
        ArtworkFrequency.HOURLY -> "A fresh set every hour"
        ArtworkFrequency.DAILY -> "At local midnight"
        ArtworkFrequency.WEEKLY -> "Every seven days"
        ArtworkFrequency.MONTHLY -> "At the start of each month"
        ArtworkFrequency.FIXED -> "Your current set stays"
    }

@Composable
private fun ArtworkDetailsDialog(artworks: List<HomeArtwork>, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today's collection") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Digital image courtesy of Getty’s Open Content Program.",
                    style = MaterialTheme.typography.bodySmall)
                artworks.forEach { artwork ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { uriHandler.openUri(artwork.sourceUrl) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(painterResource(artwork.day), contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(artwork.title, style = MaterialTheme.typography.labelLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(artwork.artist, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open Getty record",
                            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
