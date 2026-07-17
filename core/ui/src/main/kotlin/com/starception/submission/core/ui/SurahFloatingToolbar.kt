/*
 * SURAH FLOATING TOOLBAR COMPONENT
 *
 * Inspired by Material Components Android FloatingToolbar
 * Based on: /Users/smarterai/Documents/GitHub/material-components-android-master/catalog/java/io/material/catalog/floatingtoolbar/
 *
 * This floating toolbar appears when users touch/long-press a Surah news card,
 * providing quick actions for Quran interaction.
 *
 * FEATURES:
 * - Material 3 design with vibrant style
 * - Floating above content with elevation
 * - Horizontal layout with icon buttons
 * - Actions: Play Audio, Bookmark, Share, Download, Info
 * - Smooth entrance/exit animations
 * - Auto-dismiss on outside click
 */
package com.starception.submission.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Surah toolbar action data class
 */
data class SurahAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

/**
 * Floating Toolbar for Surah interactions
 *
 * @param visible Whether the toolbar is visible
 * @param surahNumber The Surah number (1-114)
 * @param surahName The Surah name
 * @param onDismiss Callback when toolbar is dismissed
 * @param onPlayAudio Callback to play Surah audio
 * @param onBookmark Callback to bookmark Surah
 * @param onShare Callback to share Surah
 * @param onDownload Callback to download Surah
 * @param onInfo Callback to show Surah info
 */
@Composable
fun SurahFloatingToolbar(
    visible: Boolean,
    surahNumber: Int,
    surahName: String,
    onDismiss: () -> Unit,
    onPlayAudio: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {},
    onInfo: () -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Trigger haptic feedback when toolbar appears
    LaunchedEffect(visible) {
        if (visible) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Define toolbar actions
    val actions = remember {
        listOf(
            SurahAction(
                icon = Icons.Default.PlayArrow,
                contentDescription = "Play Surah Audio",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayAudio()
                }
            ),
            SurahAction(
                icon = Icons.Default.Favorite,
                contentDescription = "Bookmark Surah",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBookmark()
                }
            ),
            SurahAction(
                icon = Icons.Default.Share,
                contentDescription = "Share Surah",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShare()
                }
            ),
            SurahAction(
                icon = Icons.Default.Download,
                contentDescription = "Download Surah",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDownload()
                }
            ),
            SurahAction(
                icon = Icons.Default.Info,
                contentDescription = "Surah Information",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onInfo()
                }
            )
        )
    }

    if (visible) {
        Popup(
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                focusable = true
            ),
            alignment = Alignment.BottomCenter
        ) {
            // The if(visible) guard removes the Popup instantly on dismiss, so exit specs
            // can't play; a transition state seeded false->true makes the ENTER animate
            // on first composition (plain visible=true would appear with no animation).
            val enterState = remember { MutableTransitionState(false) }.apply { targetState = true }
            AnimatedVisibility(
                visibleState = enterState,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                // Background scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            coroutineScope.launch {
                                delay(100)
                                onDismiss()
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedVisibility(
                    visibleState = enterState,
                    enter = fadeIn(
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + scaleIn(
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150))
                ) {
                    FloatingToolbarContent(
                        surahNumber = surahNumber,
                        surahName = surahName,
                        actions = actions,
                        onDismiss = onDismiss
                    )
                }
                }
            }
        }
    }
}

/**
 * Floating toolbar content with Material 3 vibrant style
 */
@Composable
private fun FloatingToolbarContent(
    surahNumber: Int,
    surahName: String,
    actions: List<SurahAction>,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Prevent click propagation
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Surah title header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Surah $surahNumber",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = surahName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Horizontal action buttons (Material FloatingToolbar style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                actions.forEach { action ->
                    FilledIconButton(
                        onClick = {
                            action.onClick()
                            onDismiss()
                        },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.contentDescription,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
