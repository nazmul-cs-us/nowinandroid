/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Indefinite
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.starception.submission.R
import com.starception.submission.core.designsystem.component.NiaBackground
import com.starception.submission.core.designsystem.component.NiaGradientBackground
import com.starception.submission.core.designsystem.component.NiaNavigationRailItem
import com.starception.submission.core.designsystem.component.NiaNavigationSuiteScaffold
import com.starception.submission.core.designsystem.component.NiaTopAppBar
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.designsystem.theme.GradientColors
import com.starception.submission.core.designsystem.theme.LocalGradientColors
import com.starception.submission.navigation.NiaNavHost
import com.starception.submission.settings.navigation.navigateToSettings
import com.starception.submission.navigation.TopLevelDestination
import kotlin.reflect.KClass
import android.app.Activity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.starception.submission.MainActivityViewModel
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import com.starception.submission.feature.settings.R as settingsR

@Composable
fun NiaApp(
    appState: NiaAppState,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    mainViewModel: MainActivityViewModel? = null,
    deepLinkCourseId: String? = null,
) {
    val shouldShowGradientBackground =
        appState.currentTopLevelDestination == TopLevelDestination.FOR_YOU

    NiaBackground(modifier = modifier) {
        NiaGradientBackground(
            gradientColors = if (shouldShowGradientBackground) {
                LocalGradientColors.current
            } else {
                GradientColors()
            },
        ) {
            val snackbarHostState = remember { SnackbarHostState() }

            val isOffline by appState.isOffline.collectAsStateWithLifecycle()

            // If user is not connected to the internet show a snack bar to inform them.
            val notConnectedMessage = stringResource(R.string.not_connected)
            LaunchedEffect(isOffline) {
                if (isOffline) {
                    snackbarHostState.showSnackbar(
                        message = notConnectedMessage,
                        duration = Indefinite,
                    )
                }
            }

            NiaAppContent(
                appState = appState,
                snackbarHostState = snackbarHostState,
                onTopAppBarActionClick = { appState.navController.navigateToSettings() },
                windowAdaptiveInfo = windowAdaptiveInfo,
                mainViewModel = mainViewModel,
                deepLinkCourseId = deepLinkCourseId,
            )
        }
    }
}

@Composable
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalComposeUiApi::class,
)
internal fun NiaAppContent(
    appState: NiaAppState,
    snackbarHostState: SnackbarHostState,
    onTopAppBarActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    mainViewModel: MainActivityViewModel? = null,
    deepLinkCourseId: String? = null,
) {
    val unreadDestinations by appState.topLevelDestinationsWithUnreadResources
        .collectAsStateWithLifecycle()
    val currentDestination = appState.currentDestination

    // Check if we're in landscape mode
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Check if we should hide the bottom navigation bar (for detail screens)
    val shouldHideBottomBar = appState.shouldHideBottomBar

    // Status bar visibility: show on parent tabs, hide on detail screens and Settings
    val view = LocalView.current
    val shouldHideStatusBar = appState.shouldHideStatusBar
    DisposableEffect(shouldHideStatusBar) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (shouldHideStatusBar) {
            // Detail screens and Settings: hide status bar (immersive)
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Parent tabs: show status bar
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {}
    }

    // When bottom bar should be hidden, render content directly without navigation scaffold
    if (shouldHideBottomBar) {
        NiaMainContent(
            appState = appState,
            snackbarHostState = snackbarHostState,
            onTopAppBarActionClick = onTopAppBarActionClick,
            modifier = modifier,
            isLandscape = isLandscape,
            mainViewModel = mainViewModel,
            deepLinkCourseId = deepLinkCourseId,
        )
    } else if (isLandscape) {
        // In landscape mode, use custom centered NavigationRail layout
        NiaLandscapeLayout(
            appState = appState,
            snackbarHostState = snackbarHostState,
            onTopAppBarActionClick = onTopAppBarActionClick,
            unreadDestinations = unreadDestinations,
            currentDestination = currentDestination,
            modifier = modifier,
            mainViewModel = mainViewModel,
            deepLinkCourseId = deepLinkCourseId,
        )
    } else {
        // Portrait mode: use standard NiaNavigationSuiteScaffold
        NiaNavigationSuiteScaffold(
            navigationSuiteItems = {
                appState.topLevelDestinations.forEach { destination ->
                    val hasUnread = unreadDestinations.contains(destination)
                    val selected = currentDestination
                        .isRouteInHierarchy(destination.baseRoute)
                    item(
                        selected = selected,
                        onClick = { appState.navigateToTopLevelDestination(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        selectedIcon = {
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.iconTextId)) },
                        modifier = Modifier
                            .testTag("NiaNavItem")
                            .then(if (hasUnread) Modifier.notificationDot() else Modifier),
                    )
                }
            },
            windowAdaptiveInfo = windowAdaptiveInfo,
        ) {
            NiaMainContent(
                appState = appState,
                snackbarHostState = snackbarHostState,
                onTopAppBarActionClick = onTopAppBarActionClick,
                modifier = modifier,
                isLandscape = false,
                mainViewModel = mainViewModel,
                deepLinkCourseId = deepLinkCourseId,
            )
        }
    }
}

/**
 * Custom landscape layout with centered NavigationRail.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
private fun NiaLandscapeLayout(
    appState: NiaAppState,
    snackbarHostState: SnackbarHostState,
    onTopAppBarActionClick: () -> Unit,
    unreadDestinations: Set<TopLevelDestination>,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
    mainViewModel: MainActivityViewModel? = null,
    deepLinkCourseId: String? = null,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Centered NavigationRail - use Box to center the rail vertically
        // Apply safeDrawing insets to respect camera cutout
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
            contentAlignment = Alignment.Center,
        ) {
            // Use Column with wrapContentHeight to group items together
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                appState.topLevelDestinations.forEach { destination ->
                    val hasUnread = unreadDestinations.contains(destination)
                    val selected = currentDestination
                        .isRouteInHierarchy(destination.baseRoute)
                    NiaNavigationRailItem(
                        selected = selected,
                        onClick = { appState.navigateToTopLevelDestination(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        selectedIcon = {
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.iconTextId)) },
                        modifier = Modifier
                            .testTag("NiaNavItem")
                            .then(if (hasUnread) Modifier.notificationDot() else Modifier),
                    )
                }
            }
        }
        // Content
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            NiaMainContent(
                appState = appState,
                snackbarHostState = snackbarHostState,
                onTopAppBarActionClick = onTopAppBarActionClick,
                modifier = modifier,
                isLandscape = true,
                mainViewModel = mainViewModel,
                deepLinkCourseId = deepLinkCourseId,
            )
        }
    }
}

/**
 * Main content area with Scaffold, top bar, and navigation host.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
private fun NiaMainContent(
    appState: NiaAppState,
    snackbarHostState: SnackbarHostState,
    onTopAppBarActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    mainViewModel: MainActivityViewModel? = null,
    deepLinkCourseId: String? = null,
) {
    Scaffold(
        modifier = modifier.semantics {
            testTagsAsResourceId = true
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.exclude(
                        WindowInsets.ime,
                    ),
                ),
            )
        },
    ) { padding ->
        // Download progress from AssetDownloadManager via MainActivityViewModel
        // Suppress the app-level banner on HOME — the inner PullToSyncContainer
        // inside PrayerTimesScreen already shows it there. All other pages show it here.
        // Home has its own PullToSyncContainer — suppress app-level banner and
        // pull-to-sync there to avoid doubles. All other pages get both.
        val isOnHome = appState.currentTopLevelDestination == TopLevelDestination.HOME
        val downloadProgress = if (mainViewModel != null && !isOnHome) {
            val isDownloading by mainViewModel.isContentDownloading.collectAsStateWithLifecycle()
            val dlProgress by mainViewModel.contentDownloadProgress.collectAsStateWithLifecycle()
            if (isDownloading) dlProgress else 0f
        } else {
            0f
        }
        val downloadLabel = if (mainViewModel != null) {
            val label by mainViewModel.contentDownloadLabel.collectAsStateWithLifecycle()
            label
        } else {
            ""
        }
        // Local refresh state — driven by pull gesture, not WorkManager's isSyncing.
        // WorkManager's state is unreliable for UI (KEEP policy, conflated flow, instant worker).
        // Instead we hold the banner for a minimum 2s so the animation is always visible.
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                mainViewModel?.requestSync()
                delay(2000L)
                isRefreshing = false
            }
        }

        PullToSyncContainer(
            isRefreshing = if (isOnHome) false else isRefreshing,
            onRefresh = { if (!isOnHome) isRefreshing = true },
            enabled = !isOnHome,
            downloadProgress = downloadProgress,
            downloadLabel = downloadLabel,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .then(
                    // In landscape, nav rail handles left inset, so only apply end inset
                    // In portrait, apply full horizontal insets
                    if (isLandscape) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.End)
                        )
                    } else {
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                    }
                ),
        ) { syncState ->
        Column(
            Modifier.fillMaxSize(),
        ) {
            // Show the top app bar on top level destinations
            // Hide top bar for ForYou and Bookmarks in landscape mode (two-pane layout)
            val destination = appState.currentTopLevelDestination
            // Hide top bar for HOME (PrayerTimes) - it renders its own top bar inside PullToSyncContainer
            // so the entire page pushes down together (like Fitbit)
            val hideTopBarForPrayerTimes = destination == TopLevelDestination.HOME
            val hideTopBarForTwoPane = isLandscape && (
                destination == TopLevelDestination.FOR_YOU ||
                destination == TopLevelDestination.BOOKMARKS
            )
            var shouldShowTopAppBar = false

            if (destination != null && !hideTopBarForTwoPane && !hideTopBarForPrayerTimes) {
                shouldShowTopAppBar = true
                // Dynamic top inset: collapses during download/sync (like Home screen)
                // When PullToSyncContainer pushes content down, the banner covers the
                // status bar area so the toolbar doesn't need its own status bar padding.
                val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                    .asPaddingValues().calculateTopPadding()
                val dynamicTopInset = statusBarInset * (1f - (syncState.wobbleIntensity * 2f).coerceAtMost(1f))
                NiaTopAppBar(
                    titleRes = destination.titleTextId,
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
                    onActionClick = { onTopAppBarActionClick() },
                    onNavigationClick = { appState.navigateToSearch() },
                    topInset = dynamicTopInset,
                )
            }

            Box(
                // Workaround for https://issuetracker.google.com/338478720
                modifier = Modifier.consumeWindowInsets(
                    if (shouldShowTopAppBar) {
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                    } else {
                        WindowInsets(0, 0, 0, 0)
                    },
                ),
            ) {
                NiaNavHost(
                    appState = appState,
                    onShowSnackbar = { message, action ->
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = action,
                            duration = Short,
                        ) == ActionPerformed
                    },
                    onTopAppBarActionClick = onTopAppBarActionClick,
                    mainViewModel = mainViewModel,
                    deepLinkCourseId = deepLinkCourseId,
                )
            }

            // TODO: We may want to add padding or spacer when the snackbar is shown so that
            //  content doesn't display behind it.
        }
        }
    }
}

private fun Modifier.notificationDot(): Modifier =
    composed {
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        drawWithContent {
            drawContent()
            drawCircle(
                tertiaryColor,
                radius = 5.dp.toPx(),
                // This is based on the dimensions of the NavigationBar's "indicator pill";
                // however, its parameters are private, so we must depend on them implicitly
                // (NavigationBarTokens.ActiveIndicatorWidth = 64.dp)
                center = center + Offset(
                    64.dp.toPx() * .45f,
                    32.dp.toPx() * -.45f - 6.dp.toPx(),
                ),
            )
        }
    }

private fun NavDestination?.isRouteInHierarchy(route: KClass<*>) =
    this?.hierarchy?.any {
        it.hasRoute(route)
    } ?: false
