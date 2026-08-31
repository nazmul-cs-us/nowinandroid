/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.settings.ThemeSettingsState
import com.starception.submission.settings.components.AboutSection
import com.starception.submission.settings.components.AppearanceSection
import com.starception.submission.settings.components.NotificationsSection
import com.starception.submission.settings.components.PrayerTimesSection
import com.starception.submission.settings.components.SettingsSection

/**
 * The prayer settings screen.
 *
 * Deliberately only a container. The controls inside are [PrayerTimesSection],
 * the same composable the Android settings screen uses, moved to :core:components
 * so there is one implementation rather than a second that drifts. An earlier
 * version of this file was a hand-written sheet with its own method list; it was
 * deleted, because a settings screen that merely resembles the real one is worse
 * than none — it looks authoritative while disagreeing about what the app does.
 *
 */
@Composable
fun PrayerSettingsScreen(
    settings: PrayerSettings,
    countryName: String?,
    showRestoreOption: Boolean,
    onSettingsChange: (PrayerSettings) -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    notifications: PrayerNotificationPreferences = PrayerNotificationPreferences(),
    onNotificationsChange: (PrayerNotificationPreferences) -> Unit = {},
    themeSettings: ThemeSettingsState = ThemeSettingsState(),
    onThemeBrandChange: (ThemeBrand) -> Unit = {},
    onDarkThemeConfigChange: (DarkThemeConfig) -> Unit = {},
    appVersion: String = "Unknown",
) {
    // One section open at a time, as on Android: the sections are long enough
    // that several open at once buries the one being read.
    var expanded by remember { mutableStateOf<String?>(SECTION_PRAYER) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                // The gesture works too, but a visible control is needed: this
                // is reached from a tap, not a sheet the user swiped up.
                IconTapTarget(
                    icon = NiaIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = onBack,
                )
            }

            SettingsSection(
                title = "Prayer Times",
                subtitle = "Calculation method & location",
                icon = NiaIcons.Home,
                isExpanded = expanded == SECTION_PRAYER,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_PRAYER) null else SECTION_PRAYER
                },
            ) {
                PrayerTimesSection(
                    prayerSettings = settings,
                    showRestoreOption = showRestoreOption,
                    autoDetectedCountryName = countryName,
                    onSettingsChange = onSettingsChange,
                    onRestoreClick = onRestore,
                )
            }

            SettingsSection(
                title = "Notifications",
                subtitle = "Prayer alerts & reminders",
                icon = NiaIcons.Upcoming,
                isExpanded = expanded == SECTION_NOTIFICATIONS,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_NOTIFICATIONS) null else SECTION_NOTIFICATIONS
                },
            ) {
                NotificationsSection(
                    preferences = notifications,
                    onPreferencesChanged = onNotificationsChange,
                    // iOS has no Do Not Disturb permission to grant: Focus is the
                    // user's to set, so there is nothing to prompt for.
                    hasDndAccess = true,
                    showSilentDuringPrayer = false,
                )
            }

            SettingsSection(
                title = "Appearance",
                subtitle = "Theme, colors & display mode",
                icon = NiaIcons.ViewDay,
                isExpanded = expanded == SECTION_APPEARANCE,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_APPEARANCE) null else SECTION_APPEARANCE
                },
            ) {
                AppearanceSection(
                    themeSettings = themeSettings,
                    onChangeThemeBrand = onThemeBrandChange,
                    onChangeDynamicColorPreference = {},
                    onChangeDarkThemeConfig = onDarkThemeConfigChange,
                    // Material You does not exist here, and the colour wheel is
                    // drawn with Android graphics, so neither is offered.
                    supportDynamicColor = false,
                    showCustomTheme = false,
                    colorPickerDialog = null,
                )
            }

            SettingsSection(
                title = "About",
                subtitle = "Version & attributions",
                icon = NiaIcons.Person,
                isExpanded = expanded == SECTION_ABOUT,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_ABOUT) null else SECTION_ABOUT
                },
            ) {
                AboutSection(
                    versionName = "$appVersion (iOS)",
                    showLicenses = false,
                    showProjectLinks = false,
                )
            }
        }
    }
}

private const val SECTION_PRAYER = "prayer"
private const val SECTION_NOTIFICATIONS = "notifications"
private const val SECTION_APPEARANCE = "appearance"
private const val SECTION_ABOUT = "about"
