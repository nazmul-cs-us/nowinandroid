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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.starception.submission.core.images.HomeArtwork
import com.starception.submission.core.images.currentHomeArtworkPainter
import kotlinx.coroutines.launch

@Composable
internal fun HomeArtworkCollectionDialog(artworks: List<HomeArtwork>, onDismiss: () -> Unit) {
    val pager = rememberPagerState(pageCount = { artworks.size })
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    fun openLink(url: String) {
        linkError = null
        runCatching { uriHandler.openUri(url) }
            .onFailure { linkError = "This link could not be opened. Please try again." }
    }

    Dialog(onDismissRequest = onDismiss, properties = artworkDialogProperties()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("homeArtworkCollection")) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Today's collection", style = MaterialTheme.typography.titleLarge)
                            Text("${artworks.size} artworks for your Home", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()
                                .aspectRatio(1.15f).clip(RoundedCornerShape(24.dp))
                                .testTag("homeArtworkCollectionPager")) { page ->
                                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Image(currentHomeArtworkPainter(page), contentDescription = artworks[page].title,
                                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                                Text("${pager.currentPage + 1} / ${artworks.size}",
                                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            artworks.forEachIndexed { index, artwork ->
                                val isSelected = pager.currentPage == index
                                val shape = RoundedCornerShape(12.dp)
                                Box(Modifier.weight(1f).aspectRatio(1f)
                                    .border(if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape)
                                    .clip(shape).clickable(role = Role.Tab) { scope.launch { pager.animateScrollToPage(index) } }
                                    .semantics {
                                        selected = isSelected
                                        contentDescription = "Artwork ${index + 1}: ${artwork.title}"
                                    }.testTag("homeArtworkCollectionItem$index").padding(4.dp)) {
                                    Image(currentHomeArtworkPainter(index), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                                }
                            }
                        }
                        artworks.getOrNull(pager.currentPage)?.let { artwork ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ARTWORK ${pager.currentPage + 1}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(artwork.title, style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.testTag("homeArtworkCollectionTitle"))
                                if (artwork.artist.isNotBlank() && !artwork.artist.equals("Unknown", ignoreCase = true)) {
                                    Text(artwork.artist, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Credits & reuse", style = MaterialTheme.typography.titleSmall)
                                        Text(artwork.licenseName, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                                        val stack = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f
                                        val source: @Composable (Modifier) -> Unit = { modifier ->
                                            FilledTonalButton(onClick = { openLink(artwork.sourceUrl) },
                                                modifier = modifier.testTag("homeArtworkCollectionSource")) {
                                                Text("View source")
                                                Icon(Icons.Outlined.OpenInNew, null, Modifier.padding(start = 8.dp).size(16.dp))
                                            }
                                        }
                                        val license: @Composable (Modifier) -> Unit = { modifier ->
                                            OutlinedButton(onClick = { openLink(artwork.licenseUrl) },
                                                modifier = modifier.testTag("homeArtworkCollectionLicense")) { Text("View license") }
                                        }
                                        if (stack) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                source(Modifier.fillMaxWidth())
                                                license(Modifier.fillMaxWidth())
                                            }
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                source(Modifier.weight(1f))
                                                license(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                    linkError?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
