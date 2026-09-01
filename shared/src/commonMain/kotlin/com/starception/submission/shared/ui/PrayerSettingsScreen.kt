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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.settings.ThemeSettingsState
import com.starception.submission.settings.components.AboutSection
import com.starception.submission.settings.components.AppearanceSection
import com.starception.submission.settings.components.NotificationsSection
import com.starception.submission.settings.components.PrayerTimesSection
import com.starception.submission.settings.components.SettingsSection
import com.starception.submission.settings.components.TravelDuaSection

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
    audioState: AudioSettingsState = AudioSettingsState(),
    audioActions: AudioSettingsActions = AudioSettingsActions(),
) {
    // One section open at a time, as on Android: the sections are long enough
    // that several open at once buries the one being read.
    var expanded by remember { mutableStateOf<String?>(SECTION_APPEARANCE) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTapTarget(
                    icon = NiaIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    visualSize = 40.dp,
                    onClick = onBack,
                )
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Prayer, audio, notifications and app preferences.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsGroupLabel("Prayer & personalization")

            SettingsSection(
                title = "Appearance",
                subtitle = "Theme, colors & display mode",
                iconGlyph = FlaticonIcons.APPEARANCE,
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
                    supportDynamicColor = false,
                    showCustomTheme = false,
                    colorPickerDialog = null,
                )
            }

            SettingsSection(
                title = "Prayer Times",
                subtitle = "Calculation method & location",
                iconGlyph = FlaticonIcons.SCHEDULE,
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
                iconGlyph = FlaticonIcons.NOTIFICATIONS,
                isExpanded = expanded == SECTION_NOTIFICATIONS,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_NOTIFICATIONS) null else SECTION_NOTIFICATIONS
                },
            ) {
                NotificationsSection(
                    preferences = notifications,
                    onPreferencesChanged = onNotificationsChange,
                    hasDndAccess = true,
                    showSilentDuringPrayer = false,
                )
            }

            SettingsSection(
                title = "Travel Dua",
                subtitle = "Auto-play dua when driving",
                iconGlyph = FlaticonIcons.TRAVEL,
                isExpanded = expanded == SECTION_TRAVEL,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_TRAVEL) null else SECTION_TRAVEL
                },
            ) {
                TravelDuaSection(
                    settings = audioState.travelDua,
                    onSettingsChanged = audioActions.onTravelDuaChange,
                    onTriggerAudioChain = audioActions.onTestTravelDua,
                    onStopAudioChain = audioActions.onStopTravelDua,
                    isPlaying = audioState.isTravelDuaPlaying,
                    testButtonLabel = "Test Travel Dua",
                    playbackDescription = "Best effort while iOS location updates are available.",
                )
            }

            SettingsGroupLabel("Voice & Salah intelligence")

            SettingsSection(
                title = "Voice Recognition",
                subtitle = "Speech detection engine",
                iconGlyph = FlaticonIcons.MICROPHONE,
                isExpanded = expanded == SECTION_VOICE,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_VOICE) null else SECTION_VOICE
                },
            ) {
                VoiceRecognitionSettingsSection(
                    selectedMode = audioState.recognitionMode,
                    testState = audioState.recognitionTestState,
                    testText = audioState.recognitionTestText,
                    onModeSelected = audioActions.onRecognitionModeSelected,
                    onStartTest = audioActions.onStartRecognitionTest,
                    onStopTest = audioActions.onStopRecognitionTest,
                )
            }

            SettingsSection(
                title = "Text-to-Speech",
                subtitle = "Voice output settings",
                iconGlyph = FlaticonIcons.VOLUME,
                isExpanded = expanded == SECTION_NARRATION,
                onToggleExpanded = {
                    expanded = if (expanded == SECTION_NARRATION) null else SECTION_NARRATION
                },
            ) {
                NarrationSettingsSection(
                    voices = audioState.narrationVoices,
                    selectedIdentifier = audioState.selectedNarrationVoiceIdentifier,
                    selectedSpeakerId = audioState.selectedNarrationSpeakerId,
                    isSpeaking = audioState.isNarrationSpeaking,
                    error = audioState.narrationError,
                    onVoiceSelected = audioActions.onNarrationVoiceSelected,
                    onSpeakerSelected = audioActions.onNarrationSpeakerSelected,
                    onPreview = audioActions.onPreviewNarration,
                    onStop = audioActions.onStopNarration,
                )
            }

            SettingsGroupLabel("App & support")

            SettingsSection(
                title = "About",
                subtitle = "Version & attributions",
                iconGlyph = FlaticonIcons.INFO,
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

            Spacer(Modifier.fillMaxWidth().height(32.dp))
        }
    }
}

@Composable
private fun SettingsGroupLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

private const val SECTION_PRAYER = "prayer"
private const val SECTION_NOTIFICATIONS = "notifications"
private const val SECTION_APPEARANCE = "appearance"
private const val SECTION_ABOUT = "about"
private const val SECTION_TRAVEL = "travel"
private const val SECTION_VOICE = "voice"
private const val SECTION_NARRATION = "narration"
