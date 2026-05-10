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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.starception.submission.core.designsystem.component.NiaTopAppBar
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.feature.settings.R as settingsR

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
                topInset = statusBarInset,
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
