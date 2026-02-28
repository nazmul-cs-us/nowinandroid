package com.starception.submission.settings.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
 * Travel Dua settings section for configuring when travel dua plays during driving.
 */
@Composable
fun TravelDuaSection(
    settings: TravelDuaSettings,
    onSettingsChanged: (TravelDuaSettings) -> Unit,
    onTriggerAudioChain: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // State to track if we need to trigger audio chain after permission granted
    var pendingAudioChainTrigger by remember { mutableStateOf(false) }

    // Permission launcher for RECORD_AUDIO
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingAudioChainTrigger) {
            // Permission granted, trigger audio chain
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
            // Permission already granted, play immediately
            onTriggerAudioChain()
        } else {
            // Request permission
            pendingAudioChainTrigger = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(modifier = modifier) {
        // Master toggle
        ListItem(
            headlineContent = {
                Text(
                    "Travel Dua",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                    if (settings.enabled) "Plays dua when driving" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(enabled = enabled))
                    }
                )
            }
        )

        // Sub-settings - only visible when enabled
        AnimatedVisibility(
            visible = settings.enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cooldown setting
                TravelDuaSliderItem(
                    label = "Cooldown",
                    description = "Wait time before dua can play again",
                    value = settings.cooldownMinutes,
                    minValue = 1,
                    maxValue = 30,
                    unit = "min",
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(cooldownMinutes = value))
                    }
                )

                // Playback delay setting
                TravelDuaSliderItem(
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

                // Gap tolerance setting
                TravelDuaSliderItem(
                    label = "Stop Tolerance",
                    description = "Max stop time (traffic lights) before reset",
                    value = settings.gapToleranceMinutes,
                    minValue = 1,
                    maxValue = 15,
                    unit = "min",
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(gapToleranceMinutes = value))
                    }
                )

                // Driving speed threshold setting
                TravelDuaSliderItem(
                    label = "Speed Threshold",
                    description = "Min speed to detect driving (lower = more sensitive)",
                    value = settings.drivingSpeedThresholdKmh,
                    minValue = 10,
                    maxValue = 40,
                    unit = "km/h",
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(drivingSpeedThresholdKmh = value))
                    }
                )

                // Manual trigger button
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { checkPermissionAndPlay() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Play Audio Chain")
                }
                Text(
                    text = "Plays: Travel Dua → Hadith (if enrolled) → Quran (if enrolled)\nSay YES/NO to mark lessons complete (mic permission required)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Info text
                Text(
                    text = "The travel dua will play after driving continuously for ${settings.playbackDelaySeconds} seconds. " +
                            "Brief stops under ${settings.gapToleranceMinutes} minutes (like traffic lights) won't reset the timer. " +
                            "After playing, the dua won't repeat for ${settings.cooldownMinutes} minutes. " +
                            "Driving is detected when speed exceeds ${settings.drivingSpeedThresholdKmh} km/h.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelDuaSliderItem(
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

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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

        Spacer(modifier = Modifier.height(8.dp))

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
                        fontSize = 12.sp,
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
