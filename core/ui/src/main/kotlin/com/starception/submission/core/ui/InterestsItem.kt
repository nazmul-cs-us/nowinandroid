/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.starception.submission.core.designsystem.component.DynamicAsyncImage
import com.starception.submission.core.designsystem.theme.QuranFonts
import com.starception.submission.core.designsystem.component.NiaIconToggleButton
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.designsystem.icon.topicIconResFor
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.ui.R.string

@Composable
fun InterestsItem(
    name: String,
    following: Boolean,
    topicImageUrl: String,
    onClick: () -> Unit,
    onFollowButtonClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    description: String = "",
    isSelected: Boolean = false,
    isDraggable: Boolean = false,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    // Check if name or description contains Arabic text
    val nameContainsArabic = name.any { it in '\u0600'..'\u06FF' }
    val descriptionContainsArabic = description.any { it in '\u0600'..'\u06FF' }

    // Get selected Arabic font from SharedPreferences
    val context = androidx.compose.ui.platform.LocalContext.current
    val arabicFontFamily = if (nameContainsArabic || descriptionContainsArabic) {
        val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
        val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
        getArabicFontFamilyForInterests(selectedFont)
    } else {
        null
    }

    ListItem(
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDraggable) {
                    Icon(
                        imageVector = NiaIcons.DragHandle,
                        contentDescription = stringResource(id = string.core_ui_drag_handle_content_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = dragHandleModifier
                            .padding(end = 8.dp)
                            .size(24.dp),
                    )
                }
                InterestsIcon(name, topicImageUrl, iconModifier.size(48.dp))
            }
        },
        headlineContent = {
            Text(
                text = name,
                style = if (nameContainsArabic && arabicFontFamily != null) {
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = arabicFontFamily,
                        fontWeight = FontWeight.Normal
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                }
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = if (descriptionContainsArabic && arabicFontFamily != null) {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = arabicFontFamily,
                        fontWeight = FontWeight.Normal
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                }
            )
        },
        trailingContent = {
            NiaIconToggleButton(
                checked = following,
                onCheckedChange = onFollowButtonClick,
                icon = {
                    Icon(
                        imageVector = NiaIcons.Add,
                        contentDescription = stringResource(
                            id = string.core_ui_interests_card_follow_button_content_desc,
                        ),
                    )
                },
                checkedIcon = {
                    Icon(
                        imageVector = NiaIcons.Check,
                        contentDescription = stringResource(
                            id = string.core_ui_interests_card_unfollow_button_content_desc,
                        ),
                    )
                },
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.primaryContainer
                isSelected -> MaterialTheme.colorScheme.surfaceVariant
                else -> Color.Transparent
            },
        ),
        modifier = modifier
            .semantics(mergeDescendants = true) {
                selected = isSelected
            }
            .graphicsLayer {
                // Elevation effect when dragging
                shadowElevation = if (isDragging) 8f else 0f
            }
            .zIndex(if (isDragging) 1f else 0f)
            .clickable(enabled = true, onClick = onClick),
    )
}

@Composable
private fun InterestsIcon(name: String, topicImageUrl: String, modifier: Modifier = Modifier) {
    val topicIconRes = topicIconResFor(name)
    when {
        topicIconRes != null -> Image(
            painter = painterResource(topicIconRes),
            contentDescription = null,
            modifier = modifier.padding(2.dp),
        )
        topicImageUrl.isEmpty() -> Icon(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp),
            imageVector = NiaIcons.Person,
            // decorative image
            contentDescription = null,
        )
        else -> DynamicAsyncImage(
            imageUrl = topicImageUrl,
            contentDescription = null,
            modifier = modifier,
        )
    }
}

@Preview
@Composable
private fun InterestsCardPreview() {
    NiaTheme {
        Surface {
            InterestsItem(
                name = "Compose",
                description = "Description",
                following = false,
                topicImageUrl = "",
                onClick = { },
                onFollowButtonClick = { },
            )
        }
    }
}

@Preview
@Composable
private fun InterestsCardLongNamePreview() {
    NiaTheme {
        Surface {
            InterestsItem(
                name = "This is a very very very very long name",
                description = "Description",
                following = true,
                topicImageUrl = "",
                onClick = { },
                onFollowButtonClick = { },
            )
        }
    }
}

@Preview
@Composable
private fun InterestsCardLongDescriptionPreview() {
    NiaTheme {
        Surface {
            InterestsItem(
                name = "Compose",
                description = "This is a very very very very very very very " +
                    "very very very long description",
                following = false,
                topicImageUrl = "",
                onClick = { },
                onFollowButtonClick = { },
            )
        }
    }
}

@Preview
@Composable
private fun InterestsCardWithEmptyDescriptionPreview() {
    NiaTheme {
        Surface {
            InterestsItem(
                name = "Compose",
                description = "",
                following = true,
                topicImageUrl = "",
                onClick = { },
                onFollowButtonClick = { },
            )
        }
    }
}

@Preview
@Composable
private fun InterestsCardSelectedPreview() {
    NiaTheme {
        Surface {
            InterestsItem(
                name = "Compose",
                description = "",
                following = true,
                topicImageUrl = "",
                onClick = { },
                onFollowButtonClick = { },
                isSelected = true,
            )
        }
    }
}

// Helper function to get Arabic font family based on user selection
@Composable
private fun getArabicFontFamilyForInterests(selectedFont: String): androidx.compose.ui.text.font.FontFamily {
    return when (selectedFont) {
        "pdms_saleem" -> QuranFonts.PDMSSaleem
        "noor_e_hidayat" -> QuranFonts.NoorEHidayat
        "thabit" -> QuranFonts.Thabit
        "uthmani_script" -> QuranFonts.UthmanicScript
        "indopak_script" -> QuranFonts.IndoPakScript
        else -> QuranFonts.PDMSSaleem
    }
}
