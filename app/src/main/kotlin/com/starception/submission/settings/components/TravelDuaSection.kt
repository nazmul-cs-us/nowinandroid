package com.starception.submission.settings.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.starception.submission.config.TravelDuaSettings

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // State to track if we need to trigger audio chain after permission granted
    var pendingAudioChainTrigger by remember { mutableStateOf(false) }

    // Permission launcher for RECORD_AUDIO
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingAudioChainTrigger) {
            pendingAudioChainTrigger = false
            onTriggerAudioChain()
        }
        pendingAudioChainTrigger = false
    }

    // Helper function to check permission and trigger audio chain
    fun checkPermissionAndPlay() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onTriggerAudioChain()
        } else {
            pendingAudioChainTrigger = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
                        icon = Icons.Outlined.Timer,
                        label = "Driving",
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
                        icon = Icons.Outlined.Schedule,
                        label = "Cooldown",
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
                        icon = Icons.Outlined.Traffic,
                        label = "Gap",
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
                        icon = Icons.Outlined.Speed,
                        label = "Speed",
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
                Button(
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
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        contentColor = if (isPlaying)
                            MaterialTheme.colorScheme.onError
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )
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
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSliderCard(
    icon: ImageVector,
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var previousValue by remember { mutableStateOf(value) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header row with icon, label, and value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Value badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
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
}
