package com.starception.submission.feature.surah

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.ui.MiniBarShell

/**
 * Compact Mushaf navigation using the same geometry as the playback mini bar.
 * The strip stays transparent so reading content remains visually dominant.
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
    val secondaryColor = contentColor.copy(alpha = 0.68f)
    val canGoPrevious = state.currentPage > 1
    val canGoNext = state.currentPage < state.totalPages
    val progress = if (state.totalPages > 0) {
        state.currentPage.toFloat() / state.totalPages.toFloat()
    } else {
        0f
    }

    MiniBarShell(
        progress = progress,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenInfo()
                    },
                )
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.surahNameArabic,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "· ${state.surahNameEnglish} · ${state.surahNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Show Surah information",
                modifier = Modifier.size(13.dp),
                tint = secondaryColor,
            )
        }

        Text(
            text = "${state.currentPage}/${state.totalPages}",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor.copy(alpha = 0.82f),
            maxLines = 1,
        )

        Spacer(modifier = Modifier.width(2.dp))

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPrevious()
            },
            enabled = canGoPrevious,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous page",
                modifier = Modifier.size(18.dp),
                tint = contentColor.copy(alpha = if (canGoPrevious) 0.78f else 0.22f),
            )
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNext()
            },
            enabled = canGoNext,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next page",
                modifier = Modifier.size(18.dp),
                tint = contentColor.copy(alpha = if (canGoNext) 0.90f else 0.22f),
            )
        }
    }
}
