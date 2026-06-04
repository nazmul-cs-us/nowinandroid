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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

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
            val wobbleIntensity = LocalWobbleIntensity.current
            val dynamicTopInset = statusBarInset * (1f - (wobbleIntensity * 2f).coerceAtMost(1f))
            AppTopSearchBar(
                title = stringResource(id = titleRes),
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
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
