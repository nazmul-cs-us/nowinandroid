package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons

/**
 * Compact Travel Dua settings section with Material 3 design.
 * Features play/pause button for audio chain control.
 */
@Composable
fun TravelDuaSection(
    settings: TravelDuaSettings,
    onSettingsChanged: (TravelDuaSettings) -> Unit,
    onTriggerAudioChain: () -> Unit = {},
    onStopAudioChain: () -> Unit = {},
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * Asks for whatever the platform needs before the audio chain may play, and
     * invokes the callback once it may. Android requests RECORD_AUDIO and audio
     * storage; a platform with nothing to ask can invoke it immediately.
     */
    onRequestPlaybackPermission: (onGranted: () -> Unit) -> Unit = { it() },
) {
    val haptic = LocalHapticFeedback.current

    // State to track if we need to trigger audio chain after permissions are granted
    var pendingAudioChainTrigger by remember { mutableStateOf(false) }

    // Playing the chain needs microphone and audio-storage permission on
    // Android, and neither concept maps to iOS in the same shape. The caller
    // decides what "may I play this" means and calls back when it may.
    fun checkPermissionAndPlay() = onRequestPlaybackPermission { onTriggerAudioChain() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Master toggle with modern switch
        ModernSwitchRow(
            title = "Auto-Play Travel Dua",
            subtitle = if (settings.enabled) "Plays dua when driving detected" else "Tap to enable",
            checked = settings.enabled,
            onCheckedChange = { enabled ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSettingsChanged(settings.copy(enabled = enabled))
            }
        )

        // Sub-settings - only visible when enabled
        AnimatedVisibility(
            visible = settings.enabled,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Compact 2x2 grid for sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactSliderCard(
                        iconGlyph = FlaticonIcons.TIMER,
                        label = "Driving",
                        description = "How long you need to drive before the Travel Dua plays. Prevents triggering during short trips.",
                        value = settings.playbackDelaySeconds,
                        minValue = 10,
                        maxValue = 180,
                        unit = "s",
                        modifier = Modifier.weight(1f),
                        onValueChange = { value ->
                            onSettingsChanged(settings.copy(playbackDelaySeconds = value))
                        }
                    )
                    CompactSliderCard(
                        iconGlyph = FlaticonIcons.SCHEDULE,
                        label = "Cooldown",
                        description = "Minimum wait time before the dua can play again. Prevents repeating on the same journey.",
                        value = settings.cooldownMinutes,
                        minValue = 1,
                        maxValue = 30,
                        unit = "m",
                        modifier = Modifier.weight(1f),
                        onValueChange = { value ->
                            onSettingsChanged(settings.copy(cooldownMinutes = value))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactSliderCard(
                        iconGlyph = FlaticonIcons.TRAFFIC,
                        label = "Gap",
                        description = "How long you can stop (traffic light, etc.) before the driving timer resets.",
                        value = settings.gapToleranceMinutes,
                        minValue = 1,
                        maxValue = 15,
                        unit = "m",
                        modifier = Modifier.weight(1f),
                        onValueChange = { value ->
                            onSettingsChanged(settings.copy(gapToleranceMinutes = value))
                        }
                    )
                    CompactSliderCard(
                        iconGlyph = FlaticonIcons.SPEED,
                        label = "Speed",
                        description = "Minimum speed to be considered 'driving'. Lower values may trigger while walking.",
                        value = settings.drivingSpeedThresholdKmh,
                        minValue = 10,
                        maxValue = 40,
                        unit = "km/h",
                        modifier = Modifier.weight(1f),
                        onValueChange = { value ->
                            onSettingsChanged(settings.copy(drivingSpeedThresholdKmh = value))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Play/Pause button with animated content
                NiaOutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isPlaying) {
                            onStopAudioChain()
                        } else {
                            checkPermissionAndPlay()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "play_pause"
                    ) { playing ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FlaticonIcon(
                                glyph = if (playing) FlaticonIcons.PAUSE else FlaticonIcons.VOLUME,
                                contentDescription = null,
                                fontSize = 20.sp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (playing) "Stop" else "Test Audio Chain",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Compact info text
                Text(
                    text = "Travel Dua → Hadith → Quran • Say YES/NO to complete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Compact slider card for 2x2 grid layout.
 * Shows tooltip with description on tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSliderCard(
    iconGlyph: String,
    label: String,
    description: String = "",
    value: Int,
    minValue: Int,
    maxValue: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var previousValue by remember { mutableStateOf(value) }
    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header row with icon, label, and value
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (description.isNotEmpty()) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                showTooltip = !showTooltip
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FlaticonIcon(
                            glyph = iconGlyph,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (description.isNotEmpty()) {
                            FlaticonIcon(
                                glyph = FlaticonIcons.INFO,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    // Value badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$value$unit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Compact slider
                Slider(
                    value = value.toFloat(),
                    onValueChange = { newValue ->
                        val newIntValue = newValue.toInt()
                        if (newIntValue != previousValue) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            previousValue = newIntValue
                        }
                        onValueChange(newIntValue)
                        showTooltip = false // Hide tooltip when adjusting
                    },
                    valueRange = minValue.toFloat()..maxValue.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        thumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Tooltip popup
        if (showTooltip && description.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { showTooltip = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .width(180.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
