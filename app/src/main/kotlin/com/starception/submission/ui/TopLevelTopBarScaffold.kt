/*
 * Copyright 2025 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush
import com.starception.submission.core.designsystem.theme.mainPageTopColor

/**
 * Wobble intensity (0f..1f) from the app-level PullToSyncContainer. Read by
 * [TopLevelTopBarScaffold] so non-Home pages can collapse the status-bar inset
 * during sync — matching Home's behavior and avoiding a ~50dp gap above the
 * title once the sync banner has already covered the status bar area.
 */
val LocalWobbleIntensity = compositionLocalOf { 0f }

/**
 * Forwards the app-level PullToSyncContainer's nestedScroll modifier across the
 * View↔Compose island created by [AppTopSearchBar] (CoordinatorLayout + inner
 * ComposeView). Non-Home tabs apply this to their content so drag events reach
 * the outer container's connection.
 */
val LocalPullToSyncModifier = compositionLocalOf<Modifier> { Modifier }

/**
 * The 4 tap-target callbacks fired by the inline FTS suggestion list in
 * [AppTopSearchBar]. Packaged once so the various 2-pane scaffolds can forward
 * them without each accepting four separate lambdas.
 */
data class SearchNavCallbacks(
    val onTopicClick: (String) -> Unit = {},
    val onNewsClick: (com.starception.submission.core.model.data.UserNewsResource) -> Unit = {},
    val onFortressDuaClick: (com.starception.submission.core.duadatabase.Dua) -> Unit = {},
    val onQuranicDuaClick: (com.starception.submission.core.quranicduas.QuranicDuaEntity) -> Unit = {},
    val onBukhariHadithClick: (hadithNumber: Int) -> Unit = {},
    /** Tapped surah (X) + ayah (Y); also used for the curated SuggestedVerses rows. */
    val onVerseClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
)

/**
 * App-wide CompositionLocal providing the search nav callbacks. Set once at the
 * NavHost level; every [TopLevelTopBarScaffold] reads it so we don't have to
 * thread the same 4 lambdas through every 2-pane scaffold's parameter list.
 */
val LocalSearchNavCallbacks = compositionLocalOf { SearchNavCallbacks() }

/**
 * App-wide callback fired when the search bar's leading profile/account icon is tapped.
 * Provided once near the top of the app (NiaApp) so [AppTopSearchBar] can open the
 * account/profile sheet without threading a lambda through every screen. Defaults to a
 * no-op so previews and isolated callers don't crash.
 */
val LocalProfileClick = compositionLocalOf<() -> Unit> { {} }

/**
 * Signed-in user's avatar URL, or null when logged out. Provided once near the top of
 * the app (NiaApp) so [AppTopSearchBar]'s leading button can show the account photo
 * instead of the generic profile glyph.
 */
val LocalProfileAvatarUrl = compositionLocalOf<String?> { null }

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
    searchPillContainerColor: Color = Color.Unspecified,
    searchPillContentColor: Color = Color.Unspecified,
    pageContainerColor: Color = Color.Unspecified,
    onVerseClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onSearchSubmit: (query: String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (showTopBar) {
        val resolvedPageTopColor = if (pageContainerColor == Color.Unspecified) {
            mainPageTopColor()
        } else {
            pageContainerColor
        }
        val pageBackground = mainPageBackgroundBrush()
        val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            .asPaddingValues()
            .calculateTopPadding()
        val wobbleIntensity = LocalWobbleIntensity.current
        val dynamicTopInset = statusBarInset * (1f - (wobbleIntensity * 2f).coerceAtMost(1f))
        val pullToSyncModifier = LocalPullToSyncModifier.current
        val searchNav = LocalSearchNavCallbacks.current
        AppTopSearchBar(
            modifier = Modifier.background(resolvedPageTopColor),
            title = stringResource(id = titleRes),
            onSettingsClick = onSettingsClick,
            topInset = dynamicTopInset,
            pillContainerColor = searchPillContainerColor,
            pillContentColor = searchPillContentColor,
            // Both the scaffold's explicit override and the NavHost-level
            // SearchNavCallbacks fire — the override exists for legacy callers
            // that pre-date the CompositionLocal; new code paths only need to
            // wire it at the NavHost.
            onVerseClick = { s, a ->
                onVerseClick(s, a)
                searchNav.onVerseClick(s, a)
            },
            onSearchSubmit = onSearchSubmit,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBackground)
                    .then(pullToSyncModifier)
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            ) {
                content()
            }
        }
    } else {
        val pageBackground = mainPageBackgroundBrush()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground)
                .consumeWindowInsets(WindowInsets(0, 0, 0, 0)),
        ) {
            content()
        }
    }
}
