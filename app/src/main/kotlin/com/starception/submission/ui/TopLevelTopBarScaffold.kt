/*
 * Copyright 2025 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.starception.submission.core.designsystem.component.NiaTopAppBar
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.feature.settings.R as settingsR

/**
 * Wobble intensity (0f..1f) from the app-level PullToSyncContainer. Read by
 * [TopLevelTopBarScaffold] so non-Home pages can collapse the status-bar inset
 * during sync — matching Home's behavior and avoiding a ~50dp gap above the
 * title once the sync banner has already covered the status bar area.
 */
val LocalWobbleIntensity = compositionLocalOf { 0f }

/**
 * Scaffold used by every non-Home top-level destination so each page renders
 * its own NiaTopAppBar at Y=0 of the NavHost. This keeps the NavHost height
 * identical across tabs (Home renders its own top bar inside PrayerTimesScreen),
 * which prevents content from jumping when switching between tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelTopBarScaffold(
    titleRes: Int,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showTopBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) {
            val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                .asPaddingValues()
                .calculateTopPadding()
            // Collapse the status-bar inset once the sync banner has covered the
            // status area, so the title doesn't sit with an extra gap above it.
            // Mirrors PrayerTimesScreen's inner top bar formula.
            val wobbleIntensity = LocalWobbleIntensity.current
            val dynamicTopInset = statusBarInset * (1f - (wobbleIntensity * 2f).coerceAtMost(1f))
            NiaTopAppBar(
                titleRes = titleRes,
                navigationIcon = NiaIcons.Search,
                navigationIconContentDescription = stringResource(
                    id = settingsR.string.feature_settings_top_app_bar_navigation_icon_description,
                ),
                actionIcon = NiaIcons.Settings,
                actionIconContentDescription = stringResource(
                    id = settingsR.string.feature_settings_top_app_bar_action_icon_description,
                ),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                onActionClick = onSettingsClick,
                onNavigationClick = onSearchClick,
                topInset = dynamicTopInset,
            )
        }
        Box(
            modifier = Modifier.consumeWindowInsets(
                if (showTopBar) {
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                } else {
                    WindowInsets(0, 0, 0, 0)
                },
            ),
        ) {
            content()
        }
    }
}
