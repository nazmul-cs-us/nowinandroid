package com.starception.submission.settings.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
 * Modern Travel Dua settings section with Material 3 design.
 */
@Composable
fun TravelDuaSection(
    settings: TravelDuaSettings,
    onSettingsChanged: (TravelDuaSettings) -> Unit,
    onTriggerAudioChain: () -> Unit = {},
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Timing Settings Section
                ModernSliderCard(
                    icon = Icons.Outlined.Timer,
                    label = "Driving Time",
                    description = "How long to drive before dua plays",
                    value = settings.playbackDelaySeconds,
                    minValue = 10,
                    maxValue = 180,
                    unit = "sec",
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(playbackDelaySeconds = value))
                    }
                )

                ModernSliderCard(
                    icon = Icons.Outlined.Schedule,
                    label = "Cooldown Period",
                    description = "Wait time before dua can play again",
                    value = settings.cooldownMinutes,
                    minValue = 1,
                    maxValue = 30,
                    unit = "min",
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(cooldownMinutes = value))
                    }
                )

                ModernSliderCard(
                    icon = Icons.Outlined.Traffic,
                    label = "Stop Tolerance",
                    description = "Max stop time before timer resets",
                    value = settings.gapToleranceMinutes,
                    minValue = 1,
                    maxValue = 15,
                    unit = "min",
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(gapToleranceMinutes = value))
                    }
                )

                ModernSliderCard(
                    icon = Icons.Outlined.Speed,
                    label = "Speed Threshold",
                    description = "Minimum speed to detect driving",
                    value = settings.drivingSpeedThresholdKmh,
                    minValue = 10,
                    maxValue = 40,
                    unit = "km/h",
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(drivingSpeedThresholdKmh = value))
                    }
                )

                // Manual trigger button with modern styling
                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        checkPermissionAndPlay()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Test Audio Chain",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Info card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Audio Chain",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Travel Dua \u2192 Hadith (if enrolled) \u2192 Quran (if enrolled)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Say YES or NO to mark lessons complete. Microphone permission required for voice recognition.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernSliderCard(
    icon: ImageVector,
    label: String,
    description: String,
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon with gradient background
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Modern slider with value thumb
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
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$value$unit",
                            style = MaterialTheme.typography.labelMedium.copy(
                                lineHeight = 14.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}
