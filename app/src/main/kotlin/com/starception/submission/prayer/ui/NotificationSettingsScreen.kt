package com.starception.submission.prayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.starception.submission.prayer.model.PrayerNotificationPreferences

/**
 * Clean list-based notification settings screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    preferences: PrayerNotificationPreferences,
    onPreferencesChanged: (PrayerNotificationPreferences) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notification Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
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

            HorizontalDivider()

            // Settings - only visible when notifications enabled
            AnimatedVisibility(
                visible = preferences.notificationsEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Prior Notification Section - Collapsible
                    CollapsibleSection(
                        title = "Prior Notification",
                        subtitle = "Minutes before prayer"
                    ) {
                        SliderListItem("Fajr", preferences.fajrPriorMinutes, 60) { value ->
                            onPreferencesChanged(preferences.copy(fajrPriorMinutes = value))
                        }
                        SliderListItem("Dhuhr", preferences.dhuhrPriorMinutes, 60) { value ->
                            onPreferencesChanged(preferences.copy(dhuhrPriorMinutes = value))
                        }
                        SliderListItem("Asr", preferences.asrPriorMinutes, 60) { value ->
                            onPreferencesChanged(preferences.copy(asrPriorMinutes = value))
                        }
                        SliderListItem("Maghrib", preferences.maghribPriorMinutes, 60) { value ->
                            onPreferencesChanged(preferences.copy(maghribPriorMinutes = value))
                        }
                        SliderListItem("Isha", preferences.ishaPriorMinutes, 60) { value ->
                            onPreferencesChanged(preferences.copy(ishaPriorMinutes = value))
                        }
                    }

                    // Go to Mosque Section - Collapsible
                    CollapsibleSection(
                        title = "Go to Mosque Phase",
                        subtitle = "Duration after prayer starts"
                    ) {
                        SliderListItem("Fajr", preferences.fajrGoToMosqueDuration, 45) { value ->
                            onPreferencesChanged(preferences.copy(fajrGoToMosqueDuration = value))
                        }
                        SliderListItem("Dhuhr", preferences.dhuhrGoToMosqueDuration, 45) { value ->
                            onPreferencesChanged(preferences.copy(dhuhrGoToMosqueDuration = value))
                        }
                        SliderListItem("Asr", preferences.asrGoToMosqueDuration, 45) { value ->
                            onPreferencesChanged(preferences.copy(asrGoToMosqueDuration = value))
                        }
                        SliderListItem("Maghrib", preferences.maghribGoToMosqueDuration, 45) { value ->
                            onPreferencesChanged(preferences.copy(maghribGoToMosqueDuration = value))
                        }
                        SliderListItem("Isha", preferences.ishaGoToMosqueDuration, 45) { value ->
                            onPreferencesChanged(preferences.copy(ishaGoToMosqueDuration = value))
                        }
                    }

                    // Per-prayer toggles - Collapsible
                    CollapsibleSection(
                        title = "Prayer Toggles",
                        subtitle = "Enable for specific prayers"
                    ) {
                        ToggleListItem("Fajr", preferences.fajrNotificationEnabled) { enabled ->
                            onPreferencesChanged(preferences.copy(fajrNotificationEnabled = enabled))
                        }
                        ToggleListItem("Dhuhr", preferences.dhuhrNotificationEnabled) { enabled ->
                            onPreferencesChanged(preferences.copy(dhuhrNotificationEnabled = enabled))
                        }
                        ToggleListItem("Asr", preferences.asrNotificationEnabled) { enabled ->
                            onPreferencesChanged(preferences.copy(asrNotificationEnabled = enabled))
                        }
                        ToggleListItem("Maghrib", preferences.maghribNotificationEnabled) { enabled ->
                            onPreferencesChanged(preferences.copy(maghribNotificationEnabled = enabled))
                        }
                        ToggleListItem("Isha", preferences.ishaNotificationEnabled) { enabled ->
                            onPreferencesChanged(preferences.copy(ishaNotificationEnabled = enabled))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.KeyboardArrowUp
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
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
private fun SliderListItem(
    prayerName: String,
    value: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
private fun ToggleListItem(
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
