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

import android.content.ClipData
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.starception.submission.core.designsystem.theme.QuranFonts
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draganddrop.DragAndDropTransferData
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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
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
import kotlinx.datetime.toJavaInstant
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
    val sharingLabel = stringResource(R.string.core_ui_feed_sharing)
    val sharingContent = stringResource(
        R.string.core_ui_feed_sharing_data,
        userNewsResource.title,
        userNewsResource.url,
    )

    val dragAndDropFlags = if (VERSION.SDK_INT >= VERSION_CODES.N) {
        View.DRAG_FLAG_GLOBAL
    } else {
        0
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
                    headerImageUrl = userNewsResource.headerImageUrl
                )
            }
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column {
                    Row {
                        NewsResourceTitle(
                            newsResourceTitle = userNewsResource.title,
                            searchQuery = searchQuery,
                            modifier = Modifier
                                .fillMaxWidth((.8f))
                                .dragAndDropSource { _ ->
                                    DragAndDropTransferData(
                                        ClipData.newPlainText(
                                            sharingLabel,
                                            sharingContent,
                                        ),
                                        flags = dragAndDropFlags,
                                    )
                                },
                        )
                        Spacer(modifier = Modifier.weight(1f))
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
                    Spacer(modifier = Modifier.height(14.dp))
                    NewsResourceShortDescription(
                        newsResourceShortDescription = userNewsResource.content,
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

@Composable
fun NewsResourceHeaderImage(
    headerImageUrl: String?,
) {
    val hasValidUrl = !headerImageUrl.isNullOrEmpty()

    // Check if this is a drawable resource reference
    val isDrawableResource = headerImageUrl?.startsWith("drawable://") == true
    val drawableResId = if (isDrawableResource) {
        when (headerImageUrl?.substringAfter("drawable://")) {
            "masjid_al_haram" -> com.starception.submission.core.designsystem.R.drawable.masjid_al_haram
            "masjid_al_nawabi" -> com.starception.submission.core.designsystem.R.drawable.masjid_al_nawabi
            else -> null
        }
    } else null

    var isLoading by remember { mutableStateOf(hasValidUrl && !isDrawableResource) }
    var isError by remember { mutableStateOf(false) }
    val imageLoader = rememberAsyncImagePainter(
        model = if (isDrawableResource) null else headerImageUrl,
        onState = { state ->
            isLoading = state is AsyncImagePainter.State.Loading
            isError = state is AsyncImagePainter.State.Error
        },
    )
    val isLocalInspection = LocalInspectionMode.current

    // Track the card's position for parallax effect
    var cardYPosition by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Professional parallax configuration:
    // - Show 100% of image initially
    // - Create parallax ILLUSION using scale and subtle translation
    // - Scale: 1.0 -> 1.08 (zoom in as user scrolls)
    // - Translation: subtle vertical shift for depth perception
    val containerHeightDp = 240f

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
        if (isLoading && hasValidUrl) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        // Calculate parallax progress based on screen position
        val normalizedPosition = (cardYPosition / screenHeightPx).coerceIn(0f, 1f)
        // Convert to -1 to +1 range centered at middle of screen
        val centeredProgress = (normalizedPosition - 0.5f) * 2f
        val easedProgress = easeInOutCubic(kotlin.math.abs(centeredProgress)) * kotlin.math.sign(centeredProgress)

        Image(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeightDp.dp)
                .graphicsLayer {
                    // Parallax ILLUSION effects:

                    // 1. Scale effect: zoom in as card moves toward center of screen
                    // At edges: 1.0, at center: 1.08 (8% zoom)
                    val scaleValue = 1f + (1f - kotlin.math.abs(centeredProgress)) * 0.08f
                    scaleX = scaleValue
                    scaleY = scaleValue

                    // 2. Subtle vertical translation for depth (moves opposite to scroll)
                    // Creates illusion of layers moving at different speeds
                    val maxTranslation = with(density) { 15.dp.toPx() }
                    translationY = -easedProgress * maxTranslation

                    // 3. Subtle alpha variation for atmospheric depth
                    alpha = 0.95f + (1f - kotlin.math.abs(centeredProgress)) * 0.05f

                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                },
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.Center,
            painter = if (isDrawableResource && drawableResId != null) {
                painterResource(drawableResId)
            } else if (hasValidUrl && isError.not() && !isLocalInspection) {
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
    .format(publishDate.toJavaInstant())

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
