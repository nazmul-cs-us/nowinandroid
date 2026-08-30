package com.starception.submission.feature.surah

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Optional strip status (prayer alert, sync, Islamic event, …) shown in place
     * of the surah's secondary name line. The strip has exactly one row, so a
     * status that is live while reading rides here rather than claiming a row.
     */
    statusText: String? = null,
    /** See [MiniBarShell]; off in the strip, whose sweep already shows the page. */
    showProgressLine: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryColor = contentColor.copy(alpha = 0.68f)
    val canGoPrevious = state.currentPage > 1 || state.surahNumber > 1
    val canGoNext = state.currentPage < state.totalPages || state.surahNumber < 114
    val progress = if (state.totalPages > 0) {
        state.currentPage.toFloat() / state.totalPages.toFloat()
    } else {
        0f
    }
    var showPageJump by rememberSaveable(state.surahNumber) { mutableStateOf(false) }
    var pageInput by rememberSaveable(state.surahNumber) {
        mutableStateOf(state.currentPage.toString())
    }
    val requestedPage = pageInput.toIntOrNull()
    val pageInputIsValid = requestedPage != null && requestedPage in 1..state.totalPages

    if (showPageJump) {
        AlertDialog(
            onDismissRequest = { showPageJump = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { value ->
                        pageInput = value.filter(Char::isDigit).take(4)
                    },
                    label = { Text("Page 1–${state.totalPages}") },
                    supportingText = if (pageInput.isNotEmpty() && !pageInputIsValid) {
                        { Text("Enter a page between 1 and ${state.totalPages}") }
                    } else {
                        null
                    },
                    isError = pageInput.isNotEmpty() && !pageInputIsValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = pageInputIsValid,
                    onClick = {
                        requestedPage?.let(onJumpToPage)
                        showPageJump = false
                    },
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJump = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    MiniBarShell(
        progress = progress,
        modifier = modifier,
        showProgressLine = showProgressLine,
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
                fontSize = 17.sp,
                lineHeight = 19.sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (statusText != null) {
                    "· $statusText"
                } else {
                    "· ${state.surahNameEnglish} · ${state.surahNumber}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Show Surah information",
                modifier = Modifier.size(15.dp),
                tint = secondaryColor,
            )
        }

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                pageInput = state.currentPage.toString()
                showPageJump = true
            },
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
                .semantics {
                    contentDescription =
                        "Page ${state.currentPage} of ${state.totalPages}. Tap to jump"
                },
            shape = RoundedCornerShape(16.dp),
            color = contentColor.copy(alpha = 0.08f),
            contentColor = contentColor,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${state.currentPage}/${state.totalPages}",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor.copy(alpha = 0.88f),
                    maxLines = 1,
                )
            }
        }

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
                contentDescription = if (state.currentPage == 1) {
                    "Previous Surah"
                } else {
                    "Previous page"
                },
                modifier = Modifier.size(20.dp),
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
                contentDescription = if (state.currentPage == state.totalPages) {
                    "Next Surah"
                } else {
                    "Next page"
                },
                modifier = Modifier.size(20.dp),
                tint = contentColor.copy(alpha = if (canGoNext) 0.90f else 0.22f),
            )
        }
    }
}
