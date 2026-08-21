/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.core.ui

import com.starception.submission.core.designsystem.theme.QuranFonts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.starception.submission.core.designsystem.R.drawable
import com.starception.submission.core.designsystem.component.NiaIconToggleButton
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.FollowableTopic
import com.starception.submission.core.model.data.NewsResource
import com.starception.submission.core.model.data.UserNewsResource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * [NewsResource] card used on the following screens: For You, Saved
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsResourceCardExpanded(
    userNewsResource: UserNewsResource,
    isBookmarked: Boolean,
    hasBeenViewed: Boolean,
    onToggleBookmark: () -> Unit,
    onClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
) {
    val clickActionLabel = stringResource(R.string.core_ui_card_tap_action)
    val surahNumber = remember(
        userNewsResource.title,
        userNewsResource.url,
        userNewsResource.type,
    ) {
        extractSurahNumber(
            title = userNewsResource.title,
            url = userNewsResource.url,
            type = userNewsResource.type,
        )
    }
    val headerImageUrl = remember(userNewsResource.headerImageUrl, surahNumber) {
        if (surahNumber != null && surahNumber in 1..114) {
            "drawable://surah_%03d".format(Locale.US, surahNumber)
        } else {
            userNewsResource.headerImageUrl
        }
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Use custom label for accessibility services to communicate button's action to user.
        // Pass null for action to only override the label and not the actual action.
        modifier = modifier
            .semantics {
                onClick(label = clickActionLabel, action = null)
            }
            .testTag("newsResourceCard:${userNewsResource.id}"),
    ) {
        Column {
            Row {
                NewsResourceHeaderImage(
                    headerImageUrl = headerImageUrl,
                )
            }
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column {
                    val chapterAudioUrl = remember(userNewsResource.content, userNewsResource.type) {
                        extractChapterAudioUrl(userNewsResource.content, userNewsResource.type)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NewsResourceTitle(
                            newsResourceTitle = userNewsResource.title,
                            searchQuery = searchQuery,
                            modifier = Modifier.fillMaxWidth((.7f)),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (chapterAudioUrl != null) {
                            ChapterPlayButton(chapterAudioUrl, userNewsResource.title)
                        }
                        BookmarkButton(isBookmarked, onToggleBookmark)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!hasBeenViewed) {
                            NotificationDot(
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(8.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                        NewsResourceMetaData(
                            publishDate = userNewsResource.publishDate,
                            resourceType = userNewsResource.type,
                            lastOpenedTimeMillis = userNewsResource.lastOpenedTimeMillis,
                        )
                    }
                    // Per-Surah reading-progress badge (hidden for non-Surah cards
                    // or when the user has not opened this Surah yet).
                    val cardContext = androidx.compose.ui.platform.LocalContext.current
                    val surahProgress = surahNumber?.let { sn ->
                        SurahReadingProgressRepository.progressFor(cardContext, sn)
                    }
                    if (surahProgress != null && surahProgress.totalAyahs > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SurahReadingProgressRow(progress = surahProgress)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    val arabicLine = smartCardArabicLine(
                        content = userNewsResource.content,
                        type = userNewsResource.type,
                    )
                    if (arabicLine != null) {
                        NewsResourceArabicLine(arabicLine)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    NewsResourceShortDescription(
                        newsResourceShortDescription = smartCardExcerpt(
                            content = userNewsResource.content,
                            type = userNewsResource.type,
                        ),
                        searchQuery = searchQuery,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NewsResourceTopics(
                        topics = userNewsResource.followableTopics,
                        onTopicClick = onTopicClick,
                    )
                }
            }
        }
    }
}

/**
 * Pulls the chapter recitation URL from the hidden `**Audio:**` marker that
 * [NewsDbGenerator] appends to dua content. Returns null for non-dua cards.
 */
private fun extractChapterAudioUrl(content: String, type: String): String? {
    if (!type.lowercase().contains("dua")) return null
    val regex = Regex(
        pattern = """\*\*Audio:\*\*\s*([\s\S]*?)(?=\n\s*\n|\n\s*\*\*|\z)""",
        option = RegexOption.IGNORE_CASE,
    )
    return regex.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.startsWith("http") }
}

@Composable
private fun ChapterPlayButton(audioUrl: String, title: String) {
    val isThisPlaying = ChapterAudioController.currentUrl == audioUrl && ChapterAudioController.isPlaying
    val isThisLoading = ChapterAudioController.loadingUrl == audioUrl
    IconButton(onClick = {
        // Set the title before toggling so the global media bar can label this track.
        ChapterAudioController.currentTitle = title
        ChapterAudioController.toggle(audioUrl)
    }) {
        if (isThisLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isThisPlaying) "Pause recitation" else "Play chapter recitation",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun NewsResourceHeaderImage(
    headerImageUrl: String?,
) {
    val hasValidUrl = !headerImageUrl.isNullOrEmpty()
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val containerHeightDp = 240f

    val isDrawableResource = headerImageUrl?.startsWith("drawable://") == true
    val drawableResId = remember(headerImageUrl) {
        if (isDrawableResource) {
            val drawableName = headerImageUrl?.substringAfter("drawable://").orEmpty()
            context.resources.getIdentifier(drawableName, "drawable", context.packageName)
                .takeIf { it != 0 }
                ?: when (drawableName) {
                    "masjid_al_haram" -> com.starception.submission.core.designsystem.R.drawable.masjid_al_haram
                    "masjid_al_nawabi" -> com.starception.submission.core.designsystem.R.drawable.masjid_al_nawabi
                    else -> null
                }
        } else {
            null
        }
    }

    // Decode at the rendered card size instead of materializing each 2560x1440
    // master bitmap on the main thread. Coil performs this work off-thread and
    // caches the downsampled result for subsequent cards/scrolls.
    val targetWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val targetHeightPx = with(density) { containerHeightDp.dp.roundToPx() }
    val imageRequest = remember(
        headerImageUrl,
        drawableResId,
        targetWidthPx,
        targetHeightPx,
    ) {
        val model = if (isDrawableResource) drawableResId else headerImageUrl
        model?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(targetWidthPx, targetHeightPx)
                .placeholder(drawable.core_designsystem_ic_placeholder_default)
                .error(drawable.core_designsystem_ic_placeholder_default)
                .crossfade(false)
                .build()
        }
    }
    val imageLoader = rememberAsyncImagePainter(
        model = imageRequest,
    )
    val isLocalInspection = LocalInspectionMode.current

    // Track the card's position for parallax effect
    var cardYPosition by remember { mutableStateOf(0f) }

    // Keep enough overscan around the image for its parallax translation.
    // Without a baseline scale, moving an edge-positioned card exposed the
    // white Card surface above the bitmap.

    // Smooth easing function for professional feel
    fun easeInOutCubic(x: Float): Float {
        return if (x < 0.5f) {
            4f * x * x * x
        } else {
            1f - (-2f * x + 2f).let { it * it * it } / 2f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(containerHeightDp.dp)
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                cardYPosition = coordinates.positionInRoot().y
            },
        contentAlignment = Alignment.Center,
    ) {
        // Removed loading spinner - images fade in smoothly instead

        Image(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Read position inside the layer so scrolling invalidates
                    // only this GPU transform instead of recomposing the card.
                    val normalizedPosition =
                        (cardYPosition / screenHeightPx).coerceIn(0f, 1f)
                    val centeredProgress = (normalizedPosition - 0.5f) * 2f
                    val easedProgress =
                        easeInOutCubic(kotlin.math.abs(centeredProgress)) *
                            kotlin.math.sign(centeredProgress)

                    // A permanent 16% overscan safely covers the full 15dp
                    // translation; add a subtle extra zoom near screen center.
                    val scaleValue = 1.16f +
                        (1f - kotlin.math.abs(centeredProgress)) * 0.04f
                    scaleX = scaleValue
                    scaleY = scaleValue

                    val maxTranslation = with(density) { 15.dp.toPx() }
                    translationY = -easedProgress * maxTranslation

                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            painter = if (imageRequest != null && !isLocalInspection) {
                imageLoader
            } else {
                painterResource(drawable.core_designsystem_ic_placeholder_default)
            },
            contentDescription = null,
        )

        // Subtle gradient overlay at bottom for depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.15f)
                        )
                    )
                )
        )
    }
}

@Composable
fun NewsResourceTitle(
    newsResourceTitle: String,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
) {
    // Check if title contains Arabic text (Unicode range 0600-06FF)
    val containsArabic = newsResourceTitle.any { it in '\u0600'..'\u06FF' }

    // Get selected Arabic font from SharedPreferences if title contains Arabic
    val context = androidx.compose.ui.platform.LocalContext.current
    val arabicFontFamily = if (containsArabic) {
        val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
        val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
        getArabicFontFamilyForSelection(selectedFont)
    } else {
        null
    }

    val highlightColor = MaterialTheme.colorScheme.tertiary
    val highlightedText = if (searchQuery.isNotBlank()) {
        highlightText(newsResourceTitle, searchQuery, highlightColor)
    } else {
        AnnotatedString(newsResourceTitle)
    }

    Text(
        text = highlightedText,
        style = if (containsArabic && arabicFontFamily != null) {
            MaterialTheme.typography.headlineSmall.copy(
                fontFamily = arabicFontFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
            )
        } else {
            MaterialTheme.typography.headlineSmall
        },
        modifier = modifier
    )
}

// Helper function to get Arabic font family
@Composable
private fun getArabicFontFamilyForSelection(selectedFont: String): androidx.compose.ui.text.font.FontFamily {
    return when (selectedFont) {
        "pdms_saleem" -> QuranFonts.PDMSSaleem
        "noor_e_hidayat" -> QuranFonts.NoorEHidayat
        "thabit" -> QuranFonts.Thabit
        "uthmani_script" -> QuranFonts.UthmanicScript
        "indopak_script" -> QuranFonts.IndoPakScript
        else -> QuranFonts.PDMSSaleem
    }
}

@Composable
fun BookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NiaIconToggleButton(
        checked = isBookmarked,
        onCheckedChange = { onClick() },
        modifier = modifier,
        icon = {
            Icon(
                imageVector = NiaIcons.BookmarkBorder,
                contentDescription = stringResource(R.string.core_ui_bookmark),
            )
        },
        checkedIcon = {
            Icon(
                imageVector = NiaIcons.Bookmark,
                contentDescription = stringResource(R.string.core_ui_unbookmark),
            )
        },
    )
}

@Composable
fun NotificationDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.core_ui_unread_resource_dot_content_description)
    Canvas(
        modifier = modifier
            .semantics { contentDescription = description },
        onDraw = {
            drawCircle(
                color,
                radius = size.minDimension / 2,
            )
        },
    )
}

@Composable
fun dateFormatted(publishDate: Instant): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    .format(java.time.Instant.ofEpochMilli(publishDate.toEpochMilliseconds()))

/**
 * Formats a timestamp to relative time string (e.g., "2 minutes ago", "3 days ago")
 */
@Composable
fun formatRelativeTime(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diffMillis = now - timestampMillis
    val diffSeconds = diffMillis / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffSeconds < 60 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffHours < 24 -> "$diffHours hr ago"
        diffDays < 7 -> "$diffDays day${if (diffDays > 1) "s" else ""} ago"
        else -> dateFormatted(kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMillis))
    }
}

@Composable
fun NewsResourceMetaData(
    publishDate: Instant,
    resourceType: String,
    lastOpenedTimeMillis: Long? = null,
) {
    // If lastOpenedTimeMillis is available, show relative time; otherwise show publish date
    val displayText = if (lastOpenedTimeMillis != null) {
        val relativeTime = formatRelativeTime(lastOpenedTimeMillis)
        if (resourceType.isNotBlank()) {
            "Opened $relativeTime • $resourceType"
        } else {
            "Opened $relativeTime"
        }
    } else {
        val formattedDate = dateFormatted(publishDate)
        if (resourceType.isNotBlank()) {
            stringResource(R.string.core_ui_card_meta_data_text, formattedDate, resourceType)
        } else {
            formattedDate
        }
    }

    Text(
        displayText,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun SurahReadingProgressRow(progress: SurahReadingProgress) {
    val accent = if (progress.isComplete) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val label = if (progress.isComplete) {
        "Read all ${progress.totalAyahs} ayahs"
    } else {
        "Resume at ayah ${progress.lastAyahIndex} of ${progress.totalAyahs}"
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tiny status dot — colored to match the progress bar.
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.percent.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun NewsResourceArabicLine(text: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
    val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
    val arabicFontFamily = getArabicFontFamilyForSelection(selectedFont)
    Text(
        text = text,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = arabicFontFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        ),
    )
}

@Composable
fun NewsResourceShortDescription(
    newsResourceShortDescription: String,
    searchQuery: String = "",
) {
    // Check if content contains Arabic text (Unicode range 0600-06FF)
    val containsArabic = newsResourceShortDescription.any { it in '\u0600'..'\u06FF' }

    // Get selected Arabic font from SharedPreferences if content contains Arabic
    val context = androidx.compose.ui.platform.LocalContext.current
    val arabicFontFamily = if (containsArabic) {
        val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
        val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
        getArabicFontFamilyForSelection(selectedFont)
    } else {
        null
    }

    val highlightColor = MaterialTheme.colorScheme.tertiary
    val highlightedText = if (searchQuery.isNotBlank()) {
        highlightText(newsResourceShortDescription, searchQuery, highlightColor)
    } else {
        AnnotatedString(newsResourceShortDescription)
    }

    Text(
        text = highlightedText,
        maxLines = 3,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = if (containsArabic && arabicFontFamily != null) {
            MaterialTheme.typography.bodyLarge.copy(
                fontFamily = arabicFontFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
            )
        } else {
            MaterialTheme.typography.bodyLarge
        }
    )
}

/**
 * Highlights all occurrences of [query] in [text] with the specified [highlightColor].
 * Matching is case-insensitive.
 */
private fun highlightText(
    text: String,
    query: String,
    highlightColor: Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var startIndex = 0

        while (startIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
            if (matchIndex == -1) {
                // No more matches, append the rest of the text
                append(text.substring(startIndex))
                break
            } else {
                // Append text before match
                append(text.substring(startIndex, matchIndex))
                // Append highlighted match
                withStyle(
                    SpanStyle(
                        background = highlightColor.copy(alpha = 0.3f),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                ) {
                    append(text.substring(matchIndex, matchIndex + query.length))
                }
                startIndex = matchIndex + query.length
            }
        }
    }
}

@Composable
fun NewsResourceTopics(
    topics: List<FollowableTopic>,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // causes narrow chips
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (followableTopic in topics) {
            NiaTopicTag(
                followed = followableTopic.isFollowed,
                onClick = { onTopicClick(followableTopic.topic.id) },
                text = {
                    val contentDescription = if (followableTopic.isFollowed) {
                        stringResource(
                            R.string.core_ui_topic_chip_content_description_when_followed,
                            followableTopic.topic.name,
                        )
                    } else {
                        stringResource(
                            R.string.core_ui_topic_chip_content_description_when_not_followed,
                            followableTopic.topic.name,
                        )
                    }
                    Text(
                        text = followableTopic.topic.name.uppercase(Locale.getDefault()),
                        modifier = Modifier
                            .semantics {
                                this.contentDescription = contentDescription
                            }
                            .testTag("topicTag:${followableTopic.topic.id}"),
                    )
                },
            )
        }
    }
}

@Preview("Bookmark Button")
@Composable
private fun BookmarkButtonPreview() {
    NiaTheme {
        Surface {
            BookmarkButton(isBookmarked = false, onClick = { })
        }
    }
}

@Preview("Bookmark Button Bookmarked")
@Composable
private fun BookmarkButtonBookmarkedPreview() {
    NiaTheme {
        Surface {
            BookmarkButton(isBookmarked = true, onClick = { })
        }
    }
}

@Preview("NewsResourceCardExpanded")
@Composable
private fun ExpandedNewsResourcePreview(
    @PreviewParameter(UserNewsResourcePreviewParameterProvider::class)
    userNewsResources: List<UserNewsResource>,
) {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
    ) {
        NiaTheme {
            Surface {
                NewsResourceCardExpanded(
                    userNewsResource = userNewsResources[0],
                    isBookmarked = true,
                    hasBeenViewed = false,
                    onToggleBookmark = {},
                    onClick = {},
                    onTopicClick = {},
                )
            }
        }
    }
}
