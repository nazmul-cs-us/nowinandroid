package com.starception.submission.feature.surah

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mushaf mini-bar mirrors MediaMiniBar but drives Quran page navigation
 * instead of audio playback. Shown in PullToSyncContainer's sage area when
 * the user is reading in Mushaf mode and no audio mini-bar is active.
 */
@Composable
fun MushafMiniBar(
    state: MushafMiniBarState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val canGoPrevious = state.currentPage > 1
    val canGoNext = state.currentPage < state.totalPages
    val pageProgress = if (state.totalPages > 0) {
        state.currentPage.toFloat() / state.totalPages
    } else {
        0f
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The title is also the quickest route back to the Surah overview.
            // This preserves the pull-down gesture while making the information
            // discoverable and accessible with one tap.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenInfo()
                        },
                    )
                    .padding(start = 2.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(contentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.surahNumber.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.76f),
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.surahNameArabic,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 14.sp,
                        lineHeight = 15.sp,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.surahNameEnglish,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            color = contentColor.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Show Surah information",
                            modifier = Modifier.size(16.dp),
                            tint = contentColor.copy(alpha = 0.62f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Editorial page typography is deliberately separate from the
            // navigation actions: fewer borders, clearer hierarchy, and no
            // control-bar appearance.
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = state.currentPage.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 17.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                )
                Text(
                    text = " of ${state.totalPages}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(5.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(contentColor.copy(alpha = if (canGoPrevious) 0.075f else 0.025f))
                    .clickable(
                        enabled = canGoPrevious,
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPrevious()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    modifier = Modifier.size(21.dp),
                    tint = contentColor.copy(alpha = if (canGoPrevious) 0.84f else 0.22f),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (canGoNext) contentColor.copy(alpha = 0.92f)
                        else contentColor.copy(alpha = 0.04f),
                    )
                    .clickable(
                        enabled = canGoNext,
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNext()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next page",
                    modifier = Modifier.size(21.dp),
                    tint = if (canGoNext) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        contentColor.copy(alpha = 0.22f)
                    },
                )
            }
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(2.5.dp),
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                color = contentColor.copy(alpha = 0.06f),
                size = size,
                cornerRadius = radius,
            )
            drawRoundRect(
                color = contentColor.copy(alpha = 0.78f),
                size = Size(size.width * pageProgress.coerceIn(0f, 1f), size.height),
                cornerRadius = radius,
            )
        }
    }
}

