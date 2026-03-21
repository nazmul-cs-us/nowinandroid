package com.starception.submission.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.settings.components.AboutSection
import com.starception.submission.settings.components.AppearanceSection
import com.starception.submission.settings.components.DeveloperSettingsSection
import com.starception.submission.settings.components.NotificationsSection
import com.starception.submission.settings.components.PrayerTimesSection
import com.starception.submission.settings.components.SettingsSection
import com.starception.submission.settings.components.TravelDuaSection
import com.starception.submission.settings.components.ContentManagementSection
import com.starception.submission.settings.components.TtsSettingsSection
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.settings.components.VoiceSettingsSection
import androidx.compose.material.icons.outlined.Storage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToSalahDataCollection: () -> Unit = {},
    viewModel: UnifiedSettingsViewModel = hiltViewModel()
) {
    val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val prayerSettings by viewModel.prayerSettings.collectAsStateWithLifecycle()
    val expandedSections by viewModel.expandedSections.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showRestoreOption by viewModel.showRestoreOption.collectAsStateWithLifecycle()
    val autoDetectedCountryName by viewModel.autoDetectedCountryName.collectAsStateWithLifecycle()
    val notificationPreferences by viewModel.notificationPreferences.collectAsStateWithLifecycle()
    val travelDuaSettings by viewModel.travelDuaSettings.collectAsStateWithLifecycle()
    val isAudioChainPlaying by viewModel.isAudioChainPlaying.collectAsStateWithLifecycle()
    val developerSettings by viewModel.developerSettings.collectAsStateWithLifecycle()
    val voiceSettings by viewModel.voiceSettings.collectAsStateWithLifecycle()
    val ttsSettings by viewModel.ttsSettings.collectAsStateWithLifecycle()
    val contentCategories by viewModel.contentCategories.collectAsStateWithLifecycle()
    val totalDownloadedSize by viewModel.totalDownloadedSize.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    LaunchedEffect(Unit) {
        viewModel.consumePendingSectionRequest()?.let { sectionId ->
            viewModel.expandSection(sectionId)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ModernSettingsTopBar(
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }
        }

        AnimatedVisibility(
            visible = !isLoading,
            enter = fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Appearance Section
                item {
                    SettingsSection(
                        title = "Appearance",
                        subtitle = "Theme, colors & display mode",
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
                        subtitle = "Calculation method & location",
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
                        subtitle = "Prayer alerts & reminders",
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

                // Travel Dua Section
                item {
                    SettingsSection(
                        title = "Travel Dua",
                        subtitle = "Auto-play dua when driving",
                        icon = Icons.Outlined.DirectionsCar,
                        isExpanded = expandedSections.contains("traveldua"),
                        onToggleExpanded = { viewModel.toggleSection("traveldua") }
                    ) {
                        TravelDuaSection(
                            settings = travelDuaSettings,
                            onSettingsChanged = viewModel::updateTravelDuaSettings,
                            onTriggerAudioChain = viewModel::triggerFullAudioChain,
                            onStopAudioChain = viewModel::stopAudioChain,
                            isPlaying = isAudioChainPlaying
                        )
                    }
                }

                // Voice Settings Section
                item {
                    SettingsSection(
                        title = "Voice Recognition",
                        subtitle = "Speech detection engine",
                        icon = Icons.Outlined.Mic,
                        isExpanded = expandedSections.contains("voice"),
                        onToggleExpanded = { viewModel.toggleSection("voice") }
                    ) {
                        VoiceSettingsSection(
                            state = voiceSettings,
                            onEngineSelected = viewModel::updateVoiceSettings,
                            onTestVoice = viewModel::startVoiceTest,
                            onStopTest = viewModel::stopVoiceTest,
                            downloadManager = viewModel.getDownloadManager(),
                            onDownloadComplete = viewModel::refreshAfterModelDownload
                        )
                    }
                }

                // TTS Settings Section
                item {
                    SettingsSection(
                        title = "Text-to-Speech",
                        subtitle = "Voice output settings",
                        icon = Icons.Outlined.VolumeUp,
                        isExpanded = expandedSections.contains("tts"),
                        onToggleExpanded = { viewModel.toggleSection("tts") }
                    ) {
                        TtsSettingsSection(
                            state = ttsSettings,
                            onTestTts = viewModel::startTtsTest,
                            onStopTts = viewModel::stopTts,
                            onVoiceChanged = viewModel::updateTtsVoice,
                            onSpeakerChanged = viewModel::updateTtsSpeakerId,
                            downloadManager = viewModel.getDownloadManager(),
                            onDownloadComplete = viewModel::refreshAfterModelDownload
                        )
                    }
                }

                // Content Management Section
                if (contentCategories.isNotEmpty()) {
                    item {
                        SettingsSection(
                            title = "Content & Storage",
                            subtitle = "Manage downloaded content",
                            icon = Icons.Outlined.Storage,
                            isExpanded = expandedSections.contains("content"),
                            onToggleExpanded = { viewModel.toggleSection("content") }
                        ) {
                            ContentManagementSection(
                                categories = contentCategories,
                                totalDownloadedSize = totalDownloadedSize,
                                onDownloadCategory = viewModel::downloadContent,
                                onDeleteCategory = viewModel::deleteContent,
                            )
                        }
                    }
                }

                // About Section
                item {
                    SettingsSection(
                        title = "About",
                        subtitle = "Version & legal info",
                        icon = Icons.Outlined.Info,
                        isExpanded = expandedSections.contains("about"),
                        onToggleExpanded = { viewModel.toggleSection("about") }
                    ) {
                        AboutSection()
                    }
                }

                // Developer Section
                item {
                    SettingsSection(
                        title = "Developer Options",
                        subtitle = "Debug & testing tools",
                        icon = Icons.Outlined.Code,
                        isExpanded = expandedSections.contains("developer"),
                        onToggleExpanded = { viewModel.toggleSection("developer") }
                    ) {
                        DeveloperSettingsSection(
                            state = developerSettings,
                            onRefreshNews = viewModel::refreshNewsDatabase,
                            onRefreshTopics = viewModel::refreshTopicsDatabase,
                            onRefreshDuas = viewModel::refreshDuasDatabase,
                            onRefreshQuranicDuas = viewModel::refreshQuranicDuasDatabase,
                            onRefreshAll = viewModel::refreshAllDatabases,
                            onNavigateToSalahDataCollection = onNavigateToSalahDataCollection
                        )
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernSettingsTopBar(
    onBackClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeTopAppBar(
        title = {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        navigationIcon = {
            Surface(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        scrollBehavior = scrollBehavior
    )
}
