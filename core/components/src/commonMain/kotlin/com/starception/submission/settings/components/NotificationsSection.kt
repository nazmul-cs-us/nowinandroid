/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.prayer.model.PrayerNotificationPreferences

/**
 * Notifications section with collapsible sub-sections for prayer notification settings.
 */
@Composable
fun NotificationsSection(
    preferences: PrayerNotificationPreferences,
    onPreferencesChanged: (PrayerNotificationPreferences) -> Unit,
    modifier: Modifier = Modifier,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: (onGranted: () -> Unit) -> Unit = { it() },
    /**
     * Whether the platform has granted Do Not Disturb control. Android must ask;
     * iOS has nothing to ask for, so it passes true.
     */
    hasDndAccess: Boolean = true,
    onOpenDndAccessSettings: () -> Unit = {},
    showSilentDuringPrayer: Boolean = true,
) {
    val notificationsActive = preferences.notificationsEnabled && notificationPermissionGranted

    Column(modifier = modifier) {
        // Master toggle
        ListItem(
            headlineContent = {
                Text(
                    "Prayer Notifications",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            },
            supportingContent = {
                Text(
                    when {
                        preferences.notificationsEnabled && !notificationPermissionGranted ->
                            "Permission required"
                        preferences.notificationsEnabled -> "Enabled"
                        else -> "Disabled"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                FlaticonIcon(
                    glyph = if (notificationsActive) {
                        FlaticonIcons.NOTIFICATIONS_ACTIVE
                    } else {
                        FlaticonIcons.NOTIFICATIONS
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 23.sp,
                )
            },
            trailingContent = {
                Switch(
                    checked = notificationsActive,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onRequestNotificationPermission {
                                onPreferencesChanged(preferences.copy(notificationsEnabled = true))
                            }
                        } else {
                            onPreferencesChanged(preferences.copy(notificationsEnabled = false))
                        }
                    },
                )
            },
        )

        // Sub-settings - only visible when notifications enabled
        AnimatedVisibility(
            visible = notificationsActive,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Prior Notification Section
                CollapsibleSubSection(
                    title = "Prior Notification",
                    subtitle = "Minutes before prayer",
                ) {
                    SliderItem("Fajr", preferences.fajrPriorMinutes, 60) { value ->
                        onPreferencesChanged(preferences.copy(fajrPriorMinutes = value))
                    }
                    SliderItem("Dhuhr", preferences.dhuhrPriorMinutes, 60) { value ->
                        onPreferencesChanged(preferences.copy(dhuhrPriorMinutes = value))
                    }
                    SliderItem("Asr", preferences.asrPriorMinutes, 60) { value ->
                        onPreferencesChanged(preferences.copy(asrPriorMinutes = value))
                    }
                    SliderItem("Maghrib", preferences.maghribPriorMinutes, 60) { value ->
                        onPreferencesChanged(preferences.copy(maghribPriorMinutes = value))
                    }
                    SliderItem("Isha", preferences.ishaPriorMinutes, 60) { value ->
                        onPreferencesChanged(preferences.copy(ishaPriorMinutes = value))
                    }
                }

                // Go to Mosque Section
                CollapsibleSubSection(
                    title = "Go to Mosque Phase",
                    subtitle = "Duration after prayer starts",
                ) {
                    SliderItem("Fajr", preferences.fajrGoToMosqueDuration, 45) { value ->
                        onPreferencesChanged(preferences.copy(fajrGoToMosqueDuration = value))
                    }
                    SliderItem("Dhuhr", preferences.dhuhrGoToMosqueDuration, 45) { value ->
                        onPreferencesChanged(preferences.copy(dhuhrGoToMosqueDuration = value))
                    }
                    SliderItem("Asr", preferences.asrGoToMosqueDuration, 45) { value ->
                        onPreferencesChanged(preferences.copy(asrGoToMosqueDuration = value))
                    }
                    SliderItem("Maghrib", preferences.maghribGoToMosqueDuration, 45) { value ->
                        onPreferencesChanged(preferences.copy(maghribGoToMosqueDuration = value))
                    }
                    SliderItem("Isha", preferences.ishaGoToMosqueDuration, 45) { value ->
                        onPreferencesChanged(preferences.copy(ishaGoToMosqueDuration = value))
                    }
                }

                // Per-prayer toggles
                CollapsibleSubSection(
                    title = "Prayer Toggles",
                    subtitle = "Enable for specific prayers",
                ) {
                    ToggleItem("Fajr", preferences.fajrNotificationEnabled) { enabled ->
                        onPreferencesChanged(preferences.copy(fajrNotificationEnabled = enabled))
                    }
                    ToggleItem("Dhuhr", preferences.dhuhrNotificationEnabled) { enabled ->
                        onPreferencesChanged(preferences.copy(dhuhrNotificationEnabled = enabled))
                    }
                    ToggleItem("Asr", preferences.asrNotificationEnabled) { enabled ->
                        onPreferencesChanged(preferences.copy(asrNotificationEnabled = enabled))
                    }
                    ToggleItem("Maghrib", preferences.maghribNotificationEnabled) { enabled ->
                        onPreferencesChanged(preferences.copy(maghribNotificationEnabled = enabled))
                    }
                    ToggleItem("Isha", preferences.ishaNotificationEnabled) { enabled ->
                        onPreferencesChanged(preferences.copy(ishaNotificationEnabled = enabled))
                    }
                }

                // Adhan playback per prayer
                AdhanPlaybackSection(
                    preferences = preferences,
                    onPreferencesChanged = onPreferencesChanged,
                )

                if (showSilentDuringPrayer) {
                    SilentDuringPrayerSection(
                        preferences = preferences,
                        onPreferencesChanged = onPreferencesChanged,
                        hasDndAccess = hasDndAccess,
                        onOpenDndAccessSettings = onOpenDndAccessSettings,
                    )
                }
            }
        }
    }
}

// The same reset glyph the home prayer tile's "Reset to default" uses
// (app/src/main/res/drawable/ic_reset_settings.xml), rebuilt as an ImageVector
// so this module stays self-contained.
private val ResetSettingsIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "ResetSettings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M520,630L520,570L680,570L680,630L520,630ZM580,840L580,790L520,790L520,730L580,730L580,680L640,680L640,840L580,840ZM680,790L680,730L840,730L840,790L680,790ZM720,680L720,520L780,520L780,570L840,570L840,630L780,630L780,680L720,680ZM831,400L748,400Q722,312 649,256Q576,200 480,200Q363,200 281.5,281.5Q200,363 200,480Q200,552 232.5,612Q265,672 320,710L320,600L400,600L400,840L160,840L160,760L254,760Q192,710 156,637.5Q120,565 120,480Q120,405 148.5,339.5Q177,274 225.5,225.5Q274,177 339.5,148.5Q405,120 480,120Q609,120 706.5,199.5Q804,279 831,400Z",
            ).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}

/**
 * Adhan playback controls: a master volume row and one row per prayer with
 * an on/off switch, a compact volume readout, and a full-width volume slider
 * beneath the name. Everything in a row morphs together — colors spring
 * between live and disabled states, the thumb scales while dragging, and the
 * reset icon pops in when a custom volume is set. The adhan plays through
 * AdhanPlaybackService when that prayer's notification fires; volume keys or
 * the Mute action silence it mid-play.
 */
@Composable
private fun AdhanPlaybackSection(
    preferences: PrayerNotificationPreferences,
    onPreferencesChanged: (PrayerNotificationPreferences) -> Unit,
) {
    CollapsibleSubSection(
        title = "Adhan Playback",
        subtitle = "Per-prayer adhan sound and volume",
    ) {
        // Master volume: applies to every prayer without its own override,
        // and dragging it resets all overrides so every prayer follows it.
        // The slider hides while silent ("Silent" readout restores it).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Master",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                AdhanVolumeValue(
                    value = preferences.adhanVolume,
                    enabled = true,
                    onRestore = { onPreferencesChanged(preferences.copy(adhanVolume = 100)) },
                )
            }
            AnimatedVisibility(
                visible = preferences.adhanVolume > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AdhanVolumeTrack(
                    value = preferences.adhanVolume,
                    enabled = true,
                ) { value ->
                    onPreferencesChanged(
                        preferences.copy(
                            adhanVolume = value,
                            fajrAdhanVolume = null,
                            dhuhrAdhanVolume = null,
                            asrAdhanVolume = null,
                            maghribAdhanVolume = null,
                            ishaAdhanVolume = null,
                        ),
                    )
                }
            }
        }

        AdhanPrayerRow(
            prayerName = "Fajr",
            masterVolume = preferences.adhanVolume,
            adhanEnabled = preferences.fajrAdhanEnabled,
            volume = preferences.fajrAdhanVolume ?: preferences.adhanVolume,
            isCustomVolume = preferences.fajrAdhanVolume != null,
            onEnabledChange = { enabled ->
                onPreferencesChanged(preferences.copy(fajrAdhanEnabled = enabled))
            },
            onVolumeChange = { value ->
                onPreferencesChanged(preferences.copy(fajrAdhanVolume = value))
            },
            onResetVolume = {
                onPreferencesChanged(preferences.copy(fajrAdhanVolume = null))
            },
        )
        AdhanPrayerRow(
            prayerName = "Dhuhr",
            masterVolume = preferences.adhanVolume,
            adhanEnabled = preferences.dhuhrAdhanEnabled,
            volume = preferences.dhuhrAdhanVolume ?: preferences.adhanVolume,
            isCustomVolume = preferences.dhuhrAdhanVolume != null,
            onEnabledChange = { enabled ->
                onPreferencesChanged(preferences.copy(dhuhrAdhanEnabled = enabled))
            },
            onVolumeChange = { value ->
                onPreferencesChanged(preferences.copy(dhuhrAdhanVolume = value))
            },
            onResetVolume = {
                onPreferencesChanged(preferences.copy(dhuhrAdhanVolume = null))
            },
        )
        AdhanPrayerRow(
            prayerName = "Asr",
            masterVolume = preferences.adhanVolume,
            adhanEnabled = preferences.asrAdhanEnabled,
            volume = preferences.asrAdhanVolume ?: preferences.adhanVolume,
            isCustomVolume = preferences.asrAdhanVolume != null,
            onEnabledChange = { enabled ->
                onPreferencesChanged(preferences.copy(asrAdhanEnabled = enabled))
            },
            onVolumeChange = { value ->
                onPreferencesChanged(preferences.copy(asrAdhanVolume = value))
            },
            onResetVolume = {
                onPreferencesChanged(preferences.copy(asrAdhanVolume = null))
            },
        )
        AdhanPrayerRow(
            prayerName = "Maghrib",
            masterVolume = preferences.adhanVolume,
            adhanEnabled = preferences.maghribAdhanEnabled,
            volume = preferences.maghribAdhanVolume ?: preferences.adhanVolume,
            isCustomVolume = preferences.maghribAdhanVolume != null,
            onEnabledChange = { enabled ->
                onPreferencesChanged(preferences.copy(maghribAdhanEnabled = enabled))
            },
            onVolumeChange = { value ->
                onPreferencesChanged(preferences.copy(maghribAdhanVolume = value))
            },
            onResetVolume = {
                onPreferencesChanged(preferences.copy(maghribAdhanVolume = null))
            },
        )
        AdhanPrayerRow(
            prayerName = "Isha",
            masterVolume = preferences.adhanVolume,
            adhanEnabled = preferences.ishaAdhanEnabled,
            volume = preferences.ishaAdhanVolume ?: preferences.adhanVolume,
            isCustomVolume = preferences.ishaAdhanVolume != null,
            onEnabledChange = { enabled ->
                onPreferencesChanged(preferences.copy(ishaAdhanEnabled = enabled))
            },
            onVolumeChange = { value ->
                onPreferencesChanged(preferences.copy(ishaAdhanVolume = value))
            },
            onResetVolume = {
                onPreferencesChanged(preferences.copy(ishaAdhanVolume = null))
            },
        )
    }
}

/**
 * One prayer's adhan control on a subtle background chip: name + volume
 * readout + reset + switch on the first line, a full-width volume slider
 * beneath. The slider hides (animated) when the prayer's adhan is off or the
 * volume is silent; tapping the "Silent" readout restores the volume.
 * Everything morphs together — colors spring between live/disabled states,
 * the thumb scales while dragging, and the reset icon pops in for custom
 * volumes.
 */
@Composable
private fun AdhanPrayerRow(
    prayerName: String,
    masterVolume: Int,
    adhanEnabled: Boolean,
    volume: Int,
    isCustomVolume: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onResetVolume: () -> Unit,
) {
    // One spring spec for every morph so the row moves as a single unit.
    val morphSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val colorMorphSpec = spring<Color>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val nameColor by animateColorAsState(
        targetValue = if (adhanEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = colorMorphSpec,
        label = "adhanNameColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = prayerName,
                style = MaterialTheme.typography.bodyMedium,
                color = nameColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            // Volume + reset merged into one control in a fixed-width slot, so
            // nothing shifts when switching between a custom volume and
            // following the master. Custom: tertiary pill with the reset
            // glyph and the custom percentage (tap = follow master). Default:
            // plain percentage ("Silent" is tappable to restore volume).
            Box(
                modifier = Modifier.width(64.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isCustomVolume,
                    transitionSpec = {
                        (
                            scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            ) + fadeIn()
                            ).togetherWith(scaleOut() + fadeOut())
                    },
                    label = "adhanVolumeControl",
                ) { custom ->
                    if (custom) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .clickable { onResetVolume() }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = ResetSettingsIcon,
                                contentDescription = "Follow master volume",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "$volume%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    } else {
                        Text(
                            text = if (volume == 0) "Silent" else "$volume%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (adhanEnabled && volume > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (volume == 0) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                onVolumeChange(
                                                    if (masterVolume > 0) masterVolume else 100,
                                                )
                                            }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
            Switch(
                checked = adhanEnabled,
                onCheckedChange = onEnabledChange,
            )
        }
        // The volume bar hides while the adhan is off or silent.
        AnimatedVisibility(
            visible = adhanEnabled && volume > 0,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            AdhanVolumeTrack(
                value = volume,
                enabled = adhanEnabled,
                onValueChange = onVolumeChange,
            )
        }
    }
}

/**
 * Compact volume readout: "Silent" at 0, otherwise a percentage. Fixed width
 * so the layout does not jitter while dragging. When the slider is hidden
 * because the volume is silent, tapping the readout restores the volume.
 */
@Composable
private fun AdhanVolumeValue(
    value: Int,
    enabled: Boolean,
    onRestore: (() -> Unit)? = null,
) {
    val color by animateColorAsState(
        targetValue = if (enabled && value > 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring<Color>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "adhanValueColor",
    )
    Text(
        text = if (value == 0) "Silent" else "$value%",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier
            .width(48.dp)
            .then(
                if (value == 0 && onRestore != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRestore() }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * Full-width slim volume slider with a small round thumb that scales up while
 * the user drags it. Track and thumb colors morph with springs between the
 * live and disabled states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdhanVolumeTrack(
    value: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var previousValue by remember { mutableStateOf(value) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    val morphSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val colorMorphSpec = spring<Color>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // Thumb grows while the user is dragging the slider.
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1f,
        animationSpec = morphSpec,
        label = "adhanThumbScale",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (enabled && value > 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = colorMorphSpec,
        label = "adhanThumbColor",
    )
    val activeTrackColor by animateColorAsState(
        targetValue = if (enabled && value > 0) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
        },
        animationSpec = colorMorphSpec,
        label = "adhanActiveTrack",
    )
    val inactiveTrackColor by animateColorAsState(
        targetValue = if (enabled && value > 0) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        animationSpec = colorMorphSpec,
        label = "adhanInactiveTrack",
    )

    Slider(
        value = value.toFloat(),
        onValueChange = { newValue ->
            val newIntValue = newValue.toInt()
            // Trigger haptic feedback on each percent change
            if (newIntValue != previousValue) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                previousValue = newIntValue
            }
            onValueChange(newIntValue)
        },
        valueRange = 0f..100f,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .shadow(
                        elevation = if (isDragging) 6.dp else 2.dp,
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        },
        colors = SliderDefaults.colors(
            activeTrackColor = activeTrackColor,
            inactiveTrackColor = inactiveTrackColor,
            disabledActiveTrackColor = activeTrackColor,
            disabledInactiveTrackColor = inactiveTrackColor,
        ),
    )
}

@Composable
private fun SilentDuringPrayerSection(
    preferences: PrayerNotificationPreferences,
    onPreferencesChanged: (PrayerNotificationPreferences) -> Unit,
    hasDndAccess: Boolean,
    onOpenDndAccessSettings: () -> Unit,
) {
    // Do Not Disturb access is an Android permission with no iOS counterpart, so
    // both checking it and opening its settings page come in from the caller.
    // iOS passes hasDndAccess = true and a no-op, because there is nothing to
    // grant — Focus is controlled by the user, not by the app.
    CollapsibleSubSection(
        title = "Silent During Prayer",
        subtitle = "Auto-enable Do Not Disturb at prayer time",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Enable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = preferences.silentDuringPrayerEnabled,
                onCheckedChange = { enabled ->
                    // Persist the requested state before leaving for special-access
                    // settings. Previously enabling from OFF opened Settings and
                    // returned early, so the feature remained disabled after access
                    // was granted.
                    onPreferencesChanged(preferences.copy(silentDuringPrayerEnabled = enabled))
                    if (enabled && !hasDndAccess) {
                        onOpenDndAccessSettings()
                        return@Switch
                    }
                },
            )
        }
        // Grant-access hint: shown when the feature is on but DND access is still missing.
        AnimatedVisibility(
            visible = preferences.silentDuringPrayerEnabled && !hasDndAccess,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = "Needs Do Not Disturb access — tap to grant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDndAccessSettings() }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        AnimatedVisibility(
            visible = preferences.silentDuringPrayerEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SliderItem(
                prayerName = "Duration",
                value = preferences.silentDuringPrayerMinutes,
                maxValue = 60,
            ) { value ->
                onPreferencesChanged(preferences.copy(silentDuringPrayerMinutes = value.coerceAtLeast(5)))
            }
        }
    }
}

@Composable
private fun CollapsibleSubSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlaticonIcon(
                    glyph = if (isExpanded) {
                        FlaticonIcons.ANGLE_UP
                    } else {
                        FlaticonIcons.ANGLE_DOWN
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
            }

            // Collapsible content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    content()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderItem(
    prayerName: String,
    value: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var previousValue by remember { mutableStateOf(value) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = prayerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(70.dp),
            maxLines = 1,
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { newValue ->
                val newIntValue = newValue.toInt()
                // Trigger haptic feedback on each minute change
                if (newIntValue != previousValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    previousValue = newIntValue
                }
                onValueChange(newIntValue)
            },
            valueRange = 0f..maxValue.toFloat(),
            modifier = Modifier.weight(1f),
            interactionSource = interactionSource,
            thumb = {
                // Custom thumb with value label
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (value > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (value == 0) "Off" else "${value}m",
                        style = MaterialTheme.typography.labelMedium.copy(
                            lineHeight = 14.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (value > 0) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            },
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun ToggleItem(
    prayerName: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prayerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}
