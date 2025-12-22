package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.prayer.model.PrayerNotificationPreferences

/**
 * Notifications section with collapsible sub-sections for prayer notification settings.
 */
@Composable
fun NotificationsSection(
    preferences: PrayerNotificationPreferences,
    onPreferencesChanged: (PrayerNotificationPreferences) -> Unit,
    modifier: Modifier = Modifier
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
                Icon(
                    imageVector = if (preferences.notificationsEnabled)
                        Icons.Default.NotificationsActive
                    else
                        Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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

                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.KeyboardArrowUp
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
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

@Composable
private fun SliderItem(
    prayerName: String,
    value: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = prayerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(60.dp)
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..maxValue.toFloat(),
            modifier = Modifier.weight(1f)
        )

        Surface(
            color = if (value > 0)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = if (value == 0) "Off" else "${value}m",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (value > 0)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
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
