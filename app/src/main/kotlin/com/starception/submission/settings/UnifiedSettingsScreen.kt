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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.ui.ImmersiveFullScreenEffect
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.settings.components.AboutSection
import com.starception.submission.core.designsystem.theme.supportsDynamicTheming
import com.starception.submission.settings.components.AppearanceSection
import com.starception.submission.settings.components.HsvColorWheelDialog
import com.starception.submission.settings.components.DeveloperSettingsSection
import com.starception.submission.settings.components.NotificationsSection
import com.starception.submission.settings.components.rememberAudioChainPermissionGate
import com.starception.submission.settings.components.rememberDndAccess
import com.starception.submission.settings.components.PrayerTimesSection
import com.starception.submission.settings.components.SettingsSection
import com.starception.submission.settings.components.TravelDuaSection
import com.starception.submission.settings.components.ContentManagementSection
import com.starception.submission.settings.components.TtsSettingsSection
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.settings.components.VoiceSettingsSection
import androidx.compose.material.icons.outlined.Storage
import com.starception.submission.core.designsystem.theme.FloatingNavClearance

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

    val listState = rememberLazyListState()

    // Absolute LazyColumn indices include the hero and the three group labels.
    val sectionItemIndices = remember(contentCategories.isNotEmpty()) {
        buildMap {
            put("appearance", 2)
            put("prayer", 3)
            put("notifications", 4)
            put("traveldua", 5)
            put("voice", 7)
            put("tts", 8)
            put("salah", 9)
            put("content", 11)
            put("about", if (contentCategories.isNotEmpty()) 12 else 11)
            put("developer", if (contentCategories.isNotEmpty()) 13 else 12)
        }
    }
    var skipInitialExpandScroll by remember { mutableStateOf(true) }
    LaunchedEffect(expandedSections) {
        if (skipInitialExpandScroll) {
            skipInitialExpandScroll = false
            return@LaunchedEffect
        }
        val id = expandedSections.firstOrNull() ?: return@LaunchedEffect
        val index = sectionItemIndices[id]
        if (index != null) {
            kotlinx.coroutines.delay(120) // let the expand animation begin first
            listState.animateScrollToItem(index)
        }
    }
    val density = LocalDensity.current
    val headerHeight = 132.dp
    val headerHeightPx = with(density) { headerHeight.toPx() }
    val toolbarHeight = 64.dp
    val toolbarHeightPx = with(density) { toolbarHeight.toPx() }
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) headerHeightPx
            else listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val collapseProgress by remember {
        derivedStateOf {
            (scrollOffset / (headerHeightPx - toolbarHeightPx)).coerceIn(0f, 1f)
        }
    }

    // Track positions - heroTitleOrigin is relative to the inner container
    var heroTitleWindowPos by remember { mutableStateOf<Offset?>(null) }
    var containerWindowPos by remember { mutableStateOf<Offset?>(null) }
    var toolbarTitleTarget by remember { mutableStateOf<Offset?>(null) }

    // Calculate hero position relative to container (for local positioning of floating title)
    val heroTitleLocalPos by remember {
        derivedStateOf {
            val hero = heroTitleWindowPos
            val container = containerWindowPos
            if (hero != null && container != null) {
                Offset(hero.x - container.x, hero.y - container.y)
            } else null
        }
    }

    val floatingTitleState by remember {
        derivedStateOf {
            val start = heroTitleLocalPos
            val end = toolbarTitleTarget
            val container = containerWindowPos
            if (start != null && end != null && container != null) {
                // End position needs to be relative to container too
                val endLocal = Offset(end.x - container.x, end.y - container.y)
                Triple(
                    start.y + ((endLocal.y - start.y) * collapseProgress),
                    collapseProgress,
                    start.x + ((endLocal.x - start.x) * collapseProgress),
                )
            } else {
                Triple(0f, collapseProgress, 0f)
            }
        }
    }


    LaunchedEffect(Unit) {
        viewModel.consumePendingSectionRequest()?.let { sectionId ->
            viewModel.expandSection(sectionId)
        }
    }

    // Hide the status bar like the Surah/Hadith/Dua detail screens so the back
    // button sits at the same top position as on those pages; the toolbar's own
    // 8dp top padding then matches their layout exactly. Restored on dispose.
    ImmersiveFullScreenEffect()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerWindowPos = coordinates.positionInWindow()
                    }
            ) {
                LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Bottom padding clears the floating nav pill so the last section's
                // header and content are fully reachable.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FloatingNavClearance),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SettingsHeroSection(
                        collapseProgress = collapseProgress,
                        onTitlePositioned = { heroTitleWindowPos = it },
                    )
                }

                item { SettingsGroupLabel("Prayer & personalization") }

                // Appearance Section
                item {
                    SettingsSection(
                        title = "Appearance",
                        subtitle = "Theme, colors & display mode",
                iconGlyph = FlaticonIcons.APPEARANCE,
                        isExpanded = expandedSections.contains("appearance"),
                        onToggleExpanded = { viewModel.toggleSection("appearance") }
                    ) {
                        AppearanceSection(
                            themeSettings = themeSettings,
                            onChangeThemeBrand = viewModel::updateThemeBrand,
                            onChangeDynamicColorPreference = viewModel::updateDynamicColorPreference,
                            onChangeDarkThemeConfig = viewModel::updateDarkThemeConfig,
                            onChangeCustomColors = viewModel::updateCustomThemeColors,
                            // Both of these are Android-only and are supplied
                            // here so the section itself can be shared: Material
                            // You is a Build.VERSION check, and the colour wheel
                            // is drawn with Bitmap, Canvas and Paint.
                            supportDynamicColor = supportsDynamicTheming(),
                            colorPickerDialog = { primary, secondary, tertiary, onConfirm, onDismiss ->
                                HsvColorWheelDialog(
                                    initialPrimary = primary,
                                    initialSecondary = secondary,
                                    initialTertiary = tertiary,
                                    onConfirm = onConfirm,
                                    onDismiss = onDismiss,
                                )
                            },
                        )
                    }
                }

                // Prayer Times Section
                item {
                    SettingsSection(
                        title = "Prayer Times",
                        subtitle = "Calculation method & location",
                        iconGlyph = FlaticonIcons.SCHEDULE,
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
                iconGlyph = FlaticonIcons.NOTIFICATIONS,
                        isExpanded = expandedSections.contains("notifications"),
                        onToggleExpanded = { viewModel.toggleSection("notifications") }
                    ) {
                        // DND access is checked here rather than inside the
                        // section: the section is shared with iOS, which has no
                        // such permission to grant.
                        val hasDndAccess by rememberDndAccess()
                        val dndContext = LocalContext.current
                        NotificationsSection(
                            preferences = notificationPreferences,
                            onPreferencesChanged = viewModel::updateNotificationPreferences,
                            hasDndAccess = hasDndAccess,
                            onOpenDndAccessSettings = {
                                com.starception.submission.prayer.silent
                                    .openDndAccessSettings(dndContext)
                            },
                        )
                    }
                }

                // Travel Dua Section
                item {
                    SettingsSection(
                        title = "Travel Dua",
                        subtitle = "Auto-play dua when driving",
                iconGlyph = FlaticonIcons.TRAVEL,
                        isExpanded = expandedSections.contains("traveldua"),
                        onToggleExpanded = { viewModel.toggleSection("traveldua") }
                    ) {
                        val audioChainPermissionGate = rememberAudioChainPermissionGate()
                        TravelDuaSection(
                            settings = travelDuaSettings,
                            onSettingsChanged = viewModel::updateTravelDuaSettings,
                            onTriggerAudioChain = viewModel::triggerFullAudioChain,
                            onStopAudioChain = viewModel::stopAudioChain,
                            isPlaying = isAudioChainPlaying
                        )
                    }
                }

                item { SettingsGroupLabel("Voice & Salah intelligence") }

                // Voice Settings Section
                item {
                    SettingsSection(
                        title = "Voice Recognition",
                        subtitle = "Speech detection engine",
                iconGlyph = FlaticonIcons.MICROPHONE,
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
                iconGlyph = FlaticonIcons.VOLUME,
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

                // Salah Training Section
                item {
                    SettingsSection(
                        title = "Salah Training",
                        subtitle = "Improve on-device posture detection",
                        iconGlyph = FlaticonIcons.POSTURE_TRAINING,
                        isExpanded = expandedSections.contains("salah"),
                        onToggleExpanded = { viewModel.toggleSection("salah") }
                    ) {
                        SalahTrainingSection(
                            onNavigateToDataCollection = onNavigateToSalahDataCollection
                        )
                    }
                }

                item { SettingsGroupLabel("App & support") }

                // Content Management Section
                if (contentCategories.isNotEmpty()) {
                    item {
                        SettingsSection(
                            title = "Content & Storage",
                            subtitle = "Manage downloaded content",
                    iconGlyph = FlaticonIcons.STORAGE,
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
                iconGlyph = FlaticonIcons.INFO,
                        isExpanded = expandedSections.contains("about"),
                        onToggleExpanded = { viewModel.toggleSection("about") }
                    ) {
                        val aboutContext = LocalContext.current
                        AboutSection(
                            // OssLicensesMenuActivity is an Activity, so the
                            // section takes the action rather than the intent.
                            onOpenLicenses = {
                                aboutContext.startActivity(
                                    android.content.Intent(
                                        aboutContext,
                                        com.google.android.gms.oss.licenses
                                            .OssLicensesMenuActivity::class.java,
                                    ),
                                )
                            },
                        )
                    }
                }

                // Developer Section
                item {
                    SettingsSection(
                        title = "Developer Options",
                        subtitle = "Debug & testing tools",
                iconGlyph = FlaticonIcons.DEVELOPER,
                        isExpanded = expandedSections.contains("developer"),
                        onToggleExpanded = { viewModel.toggleSection("developer") }
                    ) {
                        DeveloperSettingsSection(
                            state = developerSettings,
                            onRefreshNews = viewModel::refreshNewsDatabase,
                            onRefreshTopics = viewModel::refreshTopicsDatabase,
                            onRefreshDuas = viewModel::refreshDuasDatabase,
                            onRefreshQuranicDuas = viewModel::refreshQuranicDuasDatabase,
                            onRefreshAll = viewModel::refreshAllDatabases
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

        ModernSettingsTopBar(
            onBackClick = onBackClick,
            collapseProgress = collapseProgress,
            onTitleTargetPositioned = { toolbarTitleTarget = it },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Floating title rendered on top of toolbar - uses local coordinates relative to container
        if (heroTitleLocalPos != null && toolbarTitleTarget != null && containerWindowPos != null) {
            SettingsFloatingTitle(
                titleYPx = floatingTitleState.first,
                collapseProgress = floatingTitleState.second,
                titleXPx = floatingTitleState.third,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernSettingsTopBar(
    onBackClick: () -> Unit,
    collapseProgress: Float,
    onTitleTargetPositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val titleStartInsetPx = with(density) { 12.dp.toPx() }
    val collapsedTitleScale = 0.78f
    var titleSlotPosition by remember { mutableStateOf<Offset?>(null) }
    var titleSlotSize by remember { mutableStateOf<IntSize?>(null) }
    var measuredTitleSize by remember { mutableStateOf<IntSize?>(null) }

    LaunchedEffect(titleSlotPosition, titleSlotSize, measuredTitleSize) {
        val slotPosition = titleSlotPosition
        val slotSize = titleSlotSize
        val titleSize = measuredTitleSize
        if (slotPosition != null && slotSize != null && titleSize != null) {
            onTitleTargetPositioned(
                Offset(
                    x = slotPosition.x + titleStartInsetPx,
                    y = slotPosition.y + ((slotSize.height - (titleSize.height * collapsedTitleScale)) / 2f),
                ),
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = collapseProgress),
        tonalElevation = (4 * collapseProgress).dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                // Align the back button's left edge with the hero title and the
                // section cards (16dp content margin).
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f + (0.07f * collapseProgress)),
            ) {
                IconButton(onClick = onBackClick) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.ARROW_BACK,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned { coordinates ->
                        titleSlotPosition = coordinates.positionInWindow()
                        titleSlotSize = coordinates.size
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                // Invisible placeholder to measure toolbar title position
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Transparent,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .onGloballyPositioned { coordinates ->
                            measuredTitleSize = coordinates.size
                        },
                )
            }
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun SettingsFloatingTitle(
    titleYPx: Float,
    collapseProgress: Float,
    titleXPx: Float,
) {
    val scale = 1f - (collapseProgress * 0.22f)

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = titleXPx
                translationY = titleYPx
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsHeroSection(
    collapseProgress: Float,
    onTitlePositioned: (Offset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // No extra horizontal padding: the LazyColumn's 16dp contentPadding
            // already lines the title up with the back button and section cards.
            .padding(
                top = 76.dp,
                bottom = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Invisible placeholder - actual title is rendered by SettingsFloatingTitle
        // Use positionInWindow for coordinates that work with the floating title's graphicsLayer
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Transparent,
            maxLines = 1,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                // Get position relative to root so floating title can use absolute positioning
                val posInWindow = coordinates.positionInWindow()
                onTitlePositioned(posInWindow)
            },
        )
        // Subtitle fades out as user scrolls
        Text(
            text = "Prayer, audio, notifications and app preferences.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - collapseProgress
            },
        )
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
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SalahTrainingSection(
    onNavigateToDataCollection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Build a more reliable on-device prayer model with guided posture recordings and clear quality checks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        NiaOutlinedButton(
            onClick = onNavigateToDataCollection,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            FlaticonIcon(
                glyph = FlaticonIcons.DEVELOPER,
                contentDescription = null,
                fontSize = 22.sp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Open Training Lab",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
