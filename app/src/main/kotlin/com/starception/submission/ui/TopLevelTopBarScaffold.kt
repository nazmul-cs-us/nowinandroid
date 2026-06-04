/*
 * Copyright 2025 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ui

import androidx.compose.foundation.layout.Box
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
 * its own SearchBar at Y=0 of the NavHost. Wraps the page content inside
 * [AppTopSearchBar] so the SearchBar can morph into a full-screen SearchView
 * when tapped, matching the M3 catalog's SearchBarWithAppBarIcons demo.
 */
@Composable
fun TopLevelTopBarScaffold(
    titleRes: Int,
    @Suppress("UNUSED_PARAMETER") onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showTopBar: Boolean = true,
    onVerseClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onSearchSubmit: (query: String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (showTopBar) {
        val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            .asPaddingValues()
            .calculateTopPadding()
        val wobbleIntensity = LocalWobbleIntensity.current
        val dynamicTopInset = statusBarInset * (1f - (wobbleIntensity * 2f).coerceAtMost(1f))
        AppTopSearchBar(
            title = stringResource(id = titleRes),
            onSettingsClick = onSettingsClick,
            topInset = dynamicTopInset,
            onVerseClick = onVerseClick,
            onSearchSubmit = onSearchSubmit,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            ) {
                content()
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets(0, 0, 0, 0)),
        ) {
            content()
        }
    }
}
