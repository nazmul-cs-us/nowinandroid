package com.starception.submission.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.settings.components.AboutSection
import com.starception.submission.settings.components.AppearanceSection
import com.starception.submission.settings.components.NotificationsSection
import com.starception.submission.settings.components.PrayerTimesSection
import com.starception.submission.settings.components.SettingsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: UnifiedSettingsViewModel = hiltViewModel()
) {
    val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val prayerSettings by viewModel.prayerSettings.collectAsStateWithLifecycle()
    val expandedSections by viewModel.expandedSections.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showRestoreOption by viewModel.showRestoreOption.collectAsStateWithLifecycle()
    val autoDetectedCountryName by viewModel.autoDetectedCountryName.collectAsStateWithLifecycle()
    val notificationPreferences by viewModel.notificationPreferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Appearance Section
                item {
                    SettingsSection(
                        title = "Appearance",
                        icon = Icons.Outlined.Palette,
                        isExpanded = expandedSections.contains("appearance"),
                        onToggleExpanded = { viewModel.toggleSection("appearance") }
                    ) {
                        AppearanceSection(
                            themeSettings = themeSettings,
                            onChangeThemeBrand = viewModel::updateThemeBrand,
                            onChangeDynamicColorPreference = viewModel::updateDynamicColorPreference,
                            onChangeDarkThemeConfig = viewModel::updateDarkThemeConfig
                        )
                    }
                }

                // Prayer Times Section
                item {
                    SettingsSection(
                        title = "Prayer Times",
                        icon = Icons.Outlined.Schedule,
                        isExpanded = expandedSections.contains("prayer"),
                        onToggleExpanded = { viewModel.toggleSection("prayer") }
                    ) {
                        PrayerTimesSection(
                            prayerSettings = prayerSettings,
                            showRestoreOption = showRestoreOption,
                            autoDetectedCountryName = autoDetectedCountryName,
                            onSettingsChange = viewModel::updatePrayerSettings,
                            onRestoreClick = viewModel::restoreAutoDetectedSettings
                        )
                    }
                }

                // Notifications Section
                item {
                    SettingsSection(
                        title = "Notifications",
                        icon = Icons.Outlined.Notifications,
                        isExpanded = expandedSections.contains("notifications"),
                        onToggleExpanded = { viewModel.toggleSection("notifications") }
                    ) {
                        NotificationsSection(
                            preferences = notificationPreferences,
                            onPreferencesChanged = viewModel::updateNotificationPreferences
                        )
                    }
                }

                // About Section
                item {
                    SettingsSection(
                        title = "About",
                        icon = Icons.Outlined.Info,
                        isExpanded = expandedSections.contains("about"),
                        onToggleExpanded = { viewModel.toggleSection("about") }
                    ) {
                        AboutSection()
                    }
                }
            }
        }
    }
}
