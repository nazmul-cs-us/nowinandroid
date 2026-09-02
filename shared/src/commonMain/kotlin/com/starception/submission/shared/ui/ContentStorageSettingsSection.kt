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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons

data class ContentStorageCategoryState(
    val categoryKey: String,
    val displayName: String,
    val description: String,
    val totalSize: Long,
    val downloadedSize: Long,
    val availableSize: Long,
    val fileCount: Int,
    val required: Boolean,
    val isDownloaded: Boolean,
    val isAvailable: Boolean,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
)

data class ContentStorageState(
    val isLoading: Boolean = false,
    val categories: List<ContentStorageCategoryState> = emptyList(),
    val error: String? = null,
    val retryCategoryKey: String? = null,
) {
    val totalDownloadedSize: Long get() = categories.sumOf { it.downloadedSize }
    val totalAvailableSize: Long get() = categories.sumOf { it.availableSize }
    val totalSize: Long get() = categories.sumOf { it.totalSize }
    val isDownloading: Boolean get() = categories.any { it.isDownloading }
}

data class ContentStorageActions(
    val onRefresh: () -> Unit = {},
    val onDownloadCategory: (String) -> Unit = {},
    val onCancelDownload: () -> Unit = {},
    val onDeleteCategory: (String) -> Unit = {},
)

@Composable
fun ContentStorageSettingsSection(
    state: ContentStorageState,
    actions: ContentStorageActions,
    modifier: Modifier = Modifier,
) {
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val groupedCategories = state.categories
        .groupBy { storageCategoryGroup(it.categoryKey) }
        .entries
        .sortedBy { storageGroupOrder(it.key) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isLoading && state.categories.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(10.dp))
                Text("Checking downloaded content")
            }
        }

        state.error?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    NiaOutlinedButton(
                        onClick = state.retryCategoryKey?.let { category ->
                            { actions.onDownloadCategory(category) }
                        } ?: actions.onRefresh,
                    ) {
                        Text(if (state.retryCategoryKey == null) "Try again" else "Retry download")
                    }
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${formatStorageSize(state.totalDownloadedSize)} downloaded",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${formatStorageSize(state.totalAvailableSize)} ready of " +
                            "${formatStorageSize(state.totalSize)} across " +
                            "${state.categories.size} categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            groupedCategories.forEach { (groupName, categories) ->
                StorageGroupCard(
                    groupName = groupName,
                    categories = categories,
                    isExpanded = expandedGroups[groupName] ?: false,
                    actionsEnabled = !state.isDownloading && !state.isLoading,
                    onToggleExpanded = {
                        expandedGroups[groupName] = !(expandedGroups[groupName] ?: false)
                    },
                    onDownloadCategory = actions.onDownloadCategory,
                    onCancelDownload = actions.onCancelDownload,
                    onDeleteCategory = actions.onDeleteCategory,
                )
            }
        }
    }
}

@Composable
private fun StorageGroupCard(
    groupName: String,
    categories: List<ContentStorageCategoryState>,
    isExpanded: Boolean,
    actionsEnabled: Boolean,
    onToggleExpanded: () -> Unit,
    onDownloadCategory: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteCategory: (String) -> Unit,
) {
    val totalSize = categories.sumOf { it.totalSize }
    val availableSize = categories.sumOf { it.availableSize }
    val availableCount = categories.count { it.isAvailable }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (availableCount == categories.size) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FlaticonIcon(
                        glyph = if (availableCount == categories.size) {
                            FlaticonIcons.COMPLETED
                        } else {
                            FlaticonIcons.DOWNLOAD
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        fontSize = 19.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$availableCount/${categories.size} ready / " +
                            formatStorageSize(totalSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlaticonIcon(
                    glyph = if (isExpanded) FlaticonIcons.ANGLE_UP else FlaticonIcons.ANGLE_DOWN,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                )
            }

            if (availableSize > 0L && availableSize < totalSize) {
                LinearProgressIndicator(
                    progress = { availableSize.toFloat() / totalSize },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(8.dp))
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { category ->
                        StorageCategoryRow(
                            state = category,
                            actionsEnabled = actionsEnabled,
                            onDownload = { onDownloadCategory(category.categoryKey) },
                            onCancel = onCancelDownload,
                            onDelete = { onDeleteCategory(category.categoryKey) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCategoryRow(
    state: ContentStorageCategoryState,
    actionsEnabled: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (state.isDownloaded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (state.description.isNotEmpty()) {
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = storageStatusText(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (state.isDownloading) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }

        if (state.isDownloading) {
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                FlaticonIcon(
                    glyph = FlaticonIcons.PAUSE,
                    contentDescription = "Cancel download",
                    tint = MaterialTheme.colorScheme.error,
                    fontSize = 18.sp,
                )
            }
        } else {
            if (!state.isAvailable) {
                IconButton(
                    onClick = onDownload,
                    enabled = actionsEnabled,
                    modifier = Modifier.size(36.dp),
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.DOWNLOAD,
                        contentDescription = "Download ${state.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                    )
                }
            }
            if (state.downloadedSize > 0L && !state.required) {
                IconButton(
                    onClick = onDelete,
                    enabled = actionsEnabled,
                    modifier = Modifier.size(36.dp),
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.DELETE,
                        contentDescription = "Delete ${state.displayName}",
                        tint = MaterialTheme.colorScheme.error,
                        fontSize = 18.sp,
                    )
                }
            }
        }
    }
}

private fun storageStatusText(state: ContentStorageCategoryState): String = when {
    state.isDownloading -> "Downloading ${(state.progress * 100).toInt()}%"
    state.isDownloaded -> "Downloaded / ${formatStorageSize(state.totalSize)}"
    state.isAvailable && state.downloadedSize > 0L ->
        "Ready / ${formatStorageSize(state.downloadedSize)} downloaded"
    state.isAvailable -> "Included with app / ${formatStorageSize(state.totalSize)}"
    state.downloadedSize > 0L ->
        "${formatStorageSize(state.downloadedSize)} of ${formatStorageSize(state.totalSize)} downloaded"
    state.availableSize > 0L ->
        "${formatStorageSize(state.availableSize)} of ${formatStorageSize(state.totalSize)} ready"
    else -> "${formatStorageSize(state.totalSize)} / ${state.fileCount} files"
}

internal fun storageCategoryDisplayName(category: String): String = when (category) {
    "quran_core" -> "Quran (Arabic + Tafseer)"
    "quran_translation" -> "Quran Translations"
    "hadith_sahih_bukhari" -> "Sahih Bukhari"
    "hadith_sahih_muslim" -> "Sahih Muslim"
    "hadith_sunan_abu_dawud" -> "Sunan Abu Dawud"
    "hadith_sunan_tirmidhi" -> "Jami at-Tirmidhi"
    "hadith_sunan_nasai" -> "Sunan an-Nasa'i"
    "hadith_sunan_ibn_majah" -> "Sunan Ibn Majah"
    "hadith_musnad_ahmad" -> "Musnad Ahmad"
    "hadith_muwatta_malik" -> "Muwatta Malik"
    "hadith_sunan_darimi" -> "Sunan ad-Darimi"
    "json_data" -> "Islamic Reference Data"
    "news" -> "News Content"
    "model_tts_kokoro" -> "Kokoro TTS Engine"
    "model_tts_vits" -> "VITS TTS Engine"
    "model_tts_ryan" -> "Ryan TTS Voice"
    "model_tts_espeak" -> "eSpeak Phoneme Data"
    "model_asr" -> "Speech Recognition"
    "model_whisper" -> "Whisper STT Engine"
    "model_kws" -> "Keyword Detection"
    "bukhari_audio_bn" -> "Bukhari Audio (Bengali)"
    "fortress_audio_arabic" -> "Fortress Dua Audio (Arabic)"
    "quran_audio_arabic" -> "Quran Audio (Arabic)"
    "quran_audio_bengali" -> "Quran Audio (Bengali)"
    "quran_audio_english" -> "Quran Audio (English)"
    else -> category.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun storageCategoryDescription(category: String): String = when (category) {
    "quran_core" -> "Arabic text, Tafseer, and word meanings"
    "quran_translation" -> "Translations in 11 languages"
    "json_data" -> "Duas, topics, and reference content"
    "news" -> "Islamic news and articles"
    "model_tts_kokoro" -> "High-quality neural English voice"
    "model_tts_vits" -> "Multi-speaker British English voice"
    "model_asr" -> "On-device speech-to-text engine"
    "model_whisper" -> "Offline English transcription"
    "model_kws" -> "Offline yes/no detection"
    "bukhari_audio_bn" -> "Sahih Bukhari narration in Bengali"
    "fortress_audio_arabic" -> "Fortress of the Muslim recitations"
    "quran_audio_arabic" -> "Arabic Quran recitation"
    "quran_audio_bengali" -> "Bengali Quran translation audio"
    "quran_audio_english" -> "English Quran translation audio"
    else -> ""
}

private fun storageCategoryGroup(category: String): String = when {
    category == "quran_core" || category == "quran_translation" -> "Holy Quran"
    category.startsWith("hadith_") -> "Hadith Collections"
    category.startsWith("quran_audio_") || category.endsWith("_audio_bn") ||
        category.startsWith("fortress_audio_") -> "Audio Recitations"
    category.startsWith("model_tts_") -> "Text-to-Speech"
    category.startsWith("model_") -> "Voice Recognition"
    else -> "App Data"
}

private fun storageGroupOrder(group: String): Int = when (group) {
    "Holy Quran" -> 0
    "Hadith Collections" -> 1
    "Audio Recitations" -> 2
    "Text-to-Speech" -> 3
    "Voice Recognition" -> 4
    "App Data" -> 5
    else -> 6
}

internal fun formatStorageSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> formatStorageUnit(bytes, 1024L * 1024L, "MB")
    else -> formatStorageUnit(bytes, 1024L * 1024L * 1024L, "GB")
}

private fun formatStorageUnit(bytes: Long, unit: Long, suffix: String): String {
    val tenths = bytes * 10L / unit
    return "${tenths / 10L}.${tenths % 10L} $suffix"
}
