package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    /**
     * Whether the platform has granted Do Not Disturb control. Android must ask;
     * iOS has nothing to ask for, so it passes true.
     */
    hasDndAccess: Boolean = true,
    onOpenDndAccessSettings: () -> Unit = {},
    showSilentDuringPrayer: Boolean = true,
) {
    Column(modifier = modifier) {
        // Master toggle
        ListItem(
            headlineContent = {
                Text(
                    "Prayer Notifications",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                    if (preferences.notificationsEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                FlaticonIcon(
                    glyph = if (preferences.notificationsEnabled)
                        FlaticonIcons.NOTIFICATIONS_ACTIVE
                    else
                        FlaticonIcons.NOTIFICATIONS,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 23.sp,
                )
            },
            trailingContent = {
                Switch(
                    checked = preferences.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        onPreferencesChanged(preferences.copy(notificationsEnabled = enabled))
                    }
                )
            }
        )

        // Sub-settings - only visible when notifications enabled
        AnimatedVisibility(
            visible = preferences.notificationsEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Prior Notification Section
                CollapsibleSubSection(
                    title = "Prior Notification",
                    subtitle = "Minutes before prayer"
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
                    subtitle = "Duration after prayer starts"
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
                    subtitle = "Enable for specific prayers"
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
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlaticonIcon(
                    glyph = if (isExpanded)
                        FlaticonIcons.ANGLE_UP
                    else
                        FlaticonIcons.ANGLE_DOWN,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
            }

            // Collapsible content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
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
    onValueChange: (Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var previousValue by remember { mutableStateOf(value) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = prayerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(70.dp),
            maxLines = 1
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
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (value > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (value == 0) "Off" else "${value}m",
                        style = MaterialTheme.typography.labelMedium.copy(
                            lineHeight = 14.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (value > 0)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun ToggleItem(
    prayerName: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prayerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}
