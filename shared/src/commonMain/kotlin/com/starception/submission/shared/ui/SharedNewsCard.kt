/*
 * Copyright 2026 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.content.SharedNewsResource
import com.starception.submission.shared.content.SharedTopic

/** NiA's expanded news treatment, backed by the shared news database model. */
@Composable
internal fun SharedNewsResourceCard(
    news: SharedNewsResource,
    topicsById: Map<Int, SharedTopic>,
    bookmarked: Boolean,
    viewed: Boolean,
    onToggleBookmark: () -> Unit,
    onClick: () -> Unit,
    onTopicClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    currentTopicId: Int? = null,
    compact: Boolean = false,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth().semantics {
            stateDescription = if (viewed) "Read" else "Unread"
        },
    ) {
        Column {
            NewsHeaderArtwork(
                resourceName = news.headerImageUrl
                    ?.takeIf { it.startsWith(DRAWABLE_PREFIX) }
                    ?.substringAfter(DRAWABLE_PREFIX)
                    ?.takeIf(String::isNotBlank)
                    ?: DEFAULT_NEWS_HEADER,
                modifier = Modifier.fillMaxWidth().height(if (compact) 148.dp else 240.dp),
            )
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = if (compact) 12.dp else 16.dp,
                    vertical = if (compact) 6.dp else 8.dp,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        news.title,
                        style = if (compact) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = if (compact) FontWeight.SemiBold else null,
                        maxLines = if (compact) 2 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconToggleButton(
                        checked = bookmarked,
                        onCheckedChange = { onToggleBookmark() },
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = if (bookmarked) NiaIcons.Bookmark else NiaIcons.BookmarkBorder,
                            contentDescription = if (bookmarked) {
                                "Remove bookmark for ${news.title}"
                            } else {
                                "Bookmark ${news.title}"
                            },
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!viewed) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        newsMetadata(news),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                sharedNewsArabicLine(news.content, news.type)?.let { arabic ->
                    Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                    Text(
                        arabic,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = if (compact) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    sharedNewsExcerpt(news.content, news.type),
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (news.topicIds.isNotEmpty()) {
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(news.topicIds.toList(), key = { it }) { topicId ->
                            val topicName = topicsById[topicId]?.name ?: "Topic $topicId"
                            val isCurrentTopic = topicId == currentTopicId
                            Surface(
                                onClick = { onTopicClick(topicId) },
                                enabled = !isCurrentTopic,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .semantics {
                                        contentDescription = if (isCurrentTopic) {
                                            "$topicName, current topic"
                                        } else {
                                            "Open topic: $topicName"
                                        }
                                    },
                            ) {
                                Text(
                                    topicName.uppercase(),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun SharedNewsDetailContent(news: SharedNewsResource) {
    NewsHeaderArtwork(
        resourceName = news.headerImageUrl
            ?.takeIf { it.startsWith(DRAWABLE_PREFIX) }
            ?.substringAfter(DRAWABLE_PREFIX)
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_NEWS_HEADER,
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
    )
    Spacer(Modifier.height(16.dp))
    Text(news.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        newsMetadata(news),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(18.dp))
    val sections = sharedNewsSections(news.content)
    sections.forEach { (title, body) ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (title != null) {
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    body,
                    style = if (title.equals("Arabic", ignoreCase = true)) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    textAlign = if (title.equals("Arabic", ignoreCase = true)) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun newsMetadata(news: SharedNewsResource): String = buildList {
    news.publishDate.toString().substringBefore('T').takeIf(String::isNotBlank)?.let(::add)
    news.type.takeIf(String::isNotBlank)?.let(::add)
    news.source?.takeIf(String::isNotBlank)?.let(::add)
}.joinToString(" · ")

internal fun sharedNewsArabicLine(content: String, type: String): String? {
    val section = if (type.contains("surah", ignoreCase = true)) {
        extractNewsSection(content, "FirstAyah") ?: extractNewsSection(content, "Arabic")
    } else {
        extractNewsSection(content, "Arabic")
    }
    return section?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
}

internal fun sharedNewsExcerpt(content: String, type: String): String {
    val excerpt = when {
        type.contains("dua", ignoreCase = true) ->
            extractNewsSection(content, "Translation") ?: extractNewsSection(content, "Context")
        type.contains("surah", ignoreCase = true) -> {
            val revelation = extractNewsSection(content, "Type")
            val verses = extractNewsSection(content, "Verses")
            listOfNotNull(revelation, verses?.let { "$it verses" }).joinToString(" · ")
                .takeIf(String::isNotBlank)
        }
        type.contains("hadith", ignoreCase = true) -> content.split("\n\n", limit = 2).lastOrNull()
        else -> null
    }
    return (excerpt ?: content.split("\n\n").firstOrNull { it.isNotBlank() }.orEmpty())
        .replace(Regex("""^\*\*[^*]+\*\*\s*"""), "")
        .trim()
}

internal fun sharedNewsSections(content: String): List<Pair<String?, String>> {
    val marker = Regex("""\*\*([^*]+?):?\*\*\s*""")
    val matches = marker.findAll(content).toList()
    if (matches.isEmpty()) return content.trim().takeIf(String::isNotEmpty)?.let { listOf(null to it) }.orEmpty()

    val sections = mutableListOf<Pair<String?, String>>()
    val preamble = content.substring(0, matches.first().range.first).trim()
    if (preamble.isNotEmpty()) sections += null to preamble
    matches.forEachIndexed { index, match ->
        val start = match.range.last + 1
        val end = matches.getOrNull(index + 1)?.range?.first ?: content.length
        val body = content.substring(start, end).trim()
        val title = match.groupValues[1].removeSuffix(":").trim()
        if (body.isNotEmpty() && !title.equals("Audio", ignoreCase = true)) sections += title to body
    }
    return sections
}

private fun extractNewsSection(content: String, name: String): String? {
    val regex = Regex(
        pattern = """\*\*${Regex.escape(name)}:?\*\*\s*([\s\S]*?)(?=\n\s*\n|\n\s*\*\*|\z)""",
        option = RegexOption.IGNORE_CASE,
    )
    return regex.find(content)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)
}

private const val DRAWABLE_PREFIX = "drawable://"
private const val DEFAULT_NEWS_HEADER = "masjid_al_nawabi"
