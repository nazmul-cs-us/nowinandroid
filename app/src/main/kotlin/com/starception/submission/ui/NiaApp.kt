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
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.starception.submission.auth.AuthUiState
import com.starception.submission.auth.AuthViewModel
import com.starception.submission.auth.ProfileSheet
import com.starception.submission.usersettings.ui.CountrySwitchConsentSheet
import com.starception.submission.usersettings.ui.CountrySwitchViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.starception.submission.feature.surah.navigation.navigateToSurah
import com.starception.submission.navigation.navigateToMediaSourceDetail
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
import com.starception.submission.feature.prayertimes.wobble.PrayerAlertState
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import com.starception.submission.media.MediaControllerUiState
import com.starception.submission.feature.settings.R as settingsR

/** Unwraps the hosting [Activity] from a Compose [Context], needed for Firebase OAuth flows. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

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

    // Account/sign-in state for the search-bar profile icon. Hosted here at the top of
    // the app so the icon (deep inside AppTopSearchBar) can open the sheet via the
    // LocalProfileClick CompositionLocal without threading a lambda through every screen.
    val authViewModel: AuthViewModel = hiltViewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var showProfileSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Country-change consent: when the app detects a move to a new country it proposes switching
    // prayer settings; nothing changes until the user confirms in the bottom sheet below.
    val countrySwitchViewModel: CountrySwitchViewModel = hiltViewModel()
    val pendingCountrySwitch by countrySwitchViewModel.pending.collectAsStateWithLifecycle()
    // Re-check on every app open/resume so the prompt reappears until the user decides.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        countrySwitchViewModel.revalidate()
    }

    // Surface sign-in errors from the auth engine as toasts.
    LaunchedEffect(Unit) {
        authViewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

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

            CompositionLocalProvider(
                LocalProfileClick provides { showProfileSheet = true },
                LocalProfileAvatarUrl provides (authUiState as? AuthUiState.LoggedIn)?.avatarUrl,
            ) {
                NiaAppContent(
                    appState = appState,
                    snackbarHostState = snackbarHostState,
                    onTopAppBarActionClick = { appState.navController.navigateToSettings() },
                    windowAdaptiveInfo = windowAdaptiveInfo,
                    mainViewModel = mainViewModel,
                    deepLinkCourseId = deepLinkCourseId,
                )
            }

            if (showProfileSheet) {
                ProfileSheet(
                    uiState = authUiState,
                    onSignIn = { provider ->
                        activity?.let { authViewModel.signIn(it, provider) }
                        showProfileSheet = false
                    },
                    onSignOut = {
                        authViewModel.signOut()
                        showProfileSheet = false
                    },
                    onDismiss = { showProfileSheet = false },
                )
            }

            pendingCountrySwitch?.let { proposal ->
                CountrySwitchConsentSheet(
                    proposal = proposal,
                    onApply = { countrySwitchViewModel.apply() },
                    onKeepCurrent = { countrySwitchViewModel.keepCurrent() },
                    onDismissForNow = { countrySwitchViewModel.dismissForNow() },
                )
            }
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
        // Portrait mode: content fills the screen and the navigation is a
        // floating pill bar (reference design) overlaid at the bottom, with
        // the circular voice-search button beside it.
        Box(modifier = Modifier.fillMaxSize()) {
            NiaMainContent(
                appState = appState,
                snackbarHostState = snackbarHostState,
                onTopAppBarActionClick = onTopAppBarActionClick,
                modifier = modifier,
                isLandscape = false,
                mainViewModel = mainViewModel,
                deepLinkCourseId = deepLinkCourseId,
            )
            NiaFloatingBottomBar(
                appState = appState,
                unreadDestinations = unreadDestinations,
                currentDestination = currentDestination,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Floating pill bottom navigation (reference design): the existing top-level
 * destinations in a rounded floating container — the selected tab gets its own
 * tinted bubble — plus the circular voice-search button. Hidden while the
 * search overlay is open so it never floats over the results list.
 */
@Composable
private fun NiaFloatingBottomBar(
    appState: NiaAppState,
    unreadDestinations: Set<TopLevelDestination>,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
) {
    val isSearchOpen by com.starception.submission.ui.search.SearchPrefillBus
        .isSearchOpen.collectAsStateWithLifecycle()
    if (isSearchOpen) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.weight(1f),
        ) {
            val destinations = appState.topLevelDestinations
            val selectedIndex = destinations.indexOfFirst { destination ->
                currentDestination.isRouteInHierarchy(destination.baseRoute)
            }
            BoxWithConstraints(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 6.dp),
            ) {
                val itemWidth = maxWidth / destinations.size
                val bubbleTarget = itemWidth * selectedIndex.coerceAtLeast(0)
                // Fluid (gooey/metaball) selection: two bubbles race to the
                // selected tab — a fast leader and a lazy follower — and the
                // blur + alpha-threshold RenderEffect merges them into one
                // stretching droplet that pinches off and snaps together.
                val leaderX by animateDpAsState(
                    targetValue = bubbleTarget,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 1400f),
                    label = "navBubbleLeader",
                )
                val followerX by animateDpAsState(
                    targetValue = bubbleTarget,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 180f),
                    label = "navBubbleFollower",
                )
                val bubbleAlpha by animateFloatAsState(
                    // Hidden when no top-level tab is selected (detail screens).
                    targetValue = if (selectedIndex >= 0) 1f else 0f,
                    animationSpec = tween(200),
                    label = "navBubbleAlpha",
                )
                val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHighest
                // Blur then steeply re-threshold the layer's alpha (a*50 - 5000
                // in 0..255 space) — the classic gooey-effect chain. API 31+.
                val gooEffect = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        android.graphics.RenderEffect.createChainEffect(
                            android.graphics.RenderEffect.createColorFilterEffect(
                                android.graphics.ColorMatrixColorFilter(
                                    android.graphics.ColorMatrix(
                                        floatArrayOf(
                                            1f, 0f, 0f, 0f, 0f,
                                            0f, 1f, 0f, 0f, 0f,
                                            0f, 0f, 1f, 0f, 0f,
                                            0f, 0f, 0f, 50f, -5000f,
                                        ),
                                    ),
                                ),
                            ),
                            android.graphics.RenderEffect.createBlurEffect(
                                60f, 60f, android.graphics.Shader.TileMode.DECAL,
                            ),
                        ).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = gooEffect
                            alpha = bubbleAlpha
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = leaderX)
                            .width(itemWidth)
                            .height(50.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bubbleColor),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = followerX)
                            .width(itemWidth)
                            .height(50.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bubbleColor),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEach { destination ->
                        val hasUnread = unreadDestinations.contains(destination)
                        val selected = currentDestination
                            .isRouteInHierarchy(destination.baseRoute)
                        val contentTint by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(250),
                            label = "navItemTint",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .clickable { appState.navigateToTopLevelDestination(destination) }
                                .padding(vertical = 9.dp)
                                .testTag("NiaNavItem")
                                .then(if (hasUnread) Modifier.notificationDot() else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                                tint = contentTint,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = stringResource(destination.iconTextId),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = contentTint,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Circular voice-search button — same on-device Whisper flow as the
        // search bar's mic, via the bus.
        Surface(
            onClick = { com.starception.submission.ui.search.SearchPrefillBus.requestVoiceSearch() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = "Voice search",
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(24.dp),
                )
            }
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
        val isDownloadingRaw = if (mainViewModel != null) {
            val d by mainViewModel.isContentDownloading.collectAsStateWithLifecycle()
            d
        } else false
        val rawDownloadProgress = if (mainViewModel != null) {
            val p by mainViewModel.contentDownloadProgress.collectAsStateWithLifecycle()
            p
        } else 0f
        // Suppress on HOME — PrayerTimesScreen has its own PullToSyncContainer that shows it.
        val downloadProgress = if (!isOnHome && isDownloadingRaw) rawDownloadProgress else 0f
        val downloadLabel = if (mainViewModel != null) {
            val label by mainViewModel.contentDownloadLabel.collectAsStateWithLifecycle()
            label
        } else {
            ""
        }
        // Refresh state is hoisted to MainActivityViewModel so the sync banner
        // persists when navigating between Home and other tabs mid-sync.
        val isRefreshing = if (mainViewModel != null) {
            val state by mainViewModel.isSyncing.collectAsStateWithLifecycle()
            state
        } else false
        // Non-Home tabs run the generic WorkManager sync (Home runs its own
        // location+prayer refresh and is responsible for clearing the flag).
        LaunchedEffect(isRefreshing, isOnHome) {
            if (isRefreshing && !isOnHome) {
                mainViewModel?.requestSync()
                // Match PullToSyncContainer's 3s syncProgress sweep (and Home's
                // minimum visual hold) so the horizontal progress fills the full
                // width before the flag clears, instead of stopping mid-sweep.
                delay(3000L)
                mainViewModel?.setSyncing(false)
            }
        }

        // Global media controller state
        val mediaState = if (mainViewModel != null) {
            val state by mainViewModel.globalMedia.controllerState.collectAsStateWithLifecycle()
            state
        } else {
            MediaControllerUiState()
        }

        // Re-sync media controller on app resume (restores mini-bar after dismiss + background)
        androidx.lifecycle.compose.LifecycleResumeEffect(mainViewModel) {
            mainViewModel?.globalMedia?.resync()
            onPauseOrDispose { }
        }

        // Suppress media/prayer-alert on HOME — PrayerTimesScreen has its own PullToSyncContainer.
        val appLevelMediaState = if (isOnHome) MediaControllerUiState() else mediaState
        val rawPrayerAlert = if (mainViewModel != null) {
            val alert by mainViewModel.prayerAlertState.collectAsStateWithLifecycle()
            alert
        } else {
            PrayerAlertState()
        }
        // Only pass to app-level container on non-HOME pages (HOME handles its own alert).
        val appLevelPrayerAlert = if (!isOnHome) rawPrayerAlert else PrayerAlertState()

        val silentModeState by com.starception.submission.feature.prayertimes.wobble.rememberSilentModeState()
        val appLevelSilentModeState = if (!isOnHome) silentModeState else com.starception.submission.feature.prayertimes.wobble.SilentModeState()

        val mushafState by com.starception.submission.feature.surah.MushafMiniBarBus.state.collectAsStateWithLifecycle()
        val appLevelMushafState = if (!isOnHome) mushafState else null

        val rawIslamicEventState = mainViewModel?.islamicEventState?.collectAsStateWithLifecycle()?.value
            ?: com.starception.submission.feature.prayertimes.wobble.IslamicEventState()
        val appLevelIslamicEventState = if (!isOnHome) rawIslamicEventState
            else com.starception.submission.feature.prayertimes.wobble.IslamicEventState()
        val rawTtsPreparing = if (mainViewModel != null) {
            val preparing by mainViewModel.isTtsPreparing.collectAsStateWithLifecycle()
            preparing
        } else {
            false
        }
        // Home renders its own PullToSyncContainer — suppress the app-level
        // strip there like media/prayer alerts, or two strips stack.
        val isTtsPreparing = if (isOnHome) false else rawTtsPreparing
        PullToSyncContainer(
            // Suppress the outer visual on Home (the inner container in
            // PrayerTimesScreen renders it there). When the user navigates away
            // mid-sync, this gate flips and the app-level visual picks up the
            // still-true VM state, so the banner persists across the transition.
            isRefreshing = if (isOnHome) false else isRefreshing,
            onRefresh = { mainViewModel?.setSyncing(true) },
            enabled = !isOnHome,
            downloadProgress = downloadProgress,
            downloadLabel = downloadLabel,
            isTtsPreparing = isTtsPreparing,
            mediaState = appLevelMediaState,
            onMediaAction = { action -> mainViewModel?.globalMedia?.handleAction(action) },
            onMediaTitleClick = {
                // Route by playback source so every mini-bar title opens its
                // detail page (surah, hadith, or fortress dua), not just Quran.
                appState.navController.navigateToMediaSourceDetail(
                    appLevelMediaState.playback.source,
                )
            },
            prayerAlertState = appLevelPrayerAlert,
            silentModeState = appLevelSilentModeState,
            islamicEventState = appLevelIslamicEventState,
            onIslamicEventClick = { event ->
                com.starception.submission.ui.search.SearchPrefillBus.requestSearch(event.searchQuery)
            },
            mushafState = appLevelMushafState,
            onMushafPrevious = { com.starception.submission.feature.surah.MushafMiniBarBus.onPrevious?.invoke() },
            onMushafNext = { com.starception.submission.feature.surah.MushafMiniBarBus.onNext?.invoke() },
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
            // Top bar is rendered by each top-level destination itself
            // (Home via PrayerTimesScreen's inner top bar, others via
            // TopLevelTopBarScaffold). NavHost height is therefore identical
            // across tabs, so content does not jump on tab switch.
            // Provide wobble so TopLevelTopBarScaffold can collapse its status-bar
            // inset during sync, matching Home and avoiding a tall gap above the title.
            androidx.compose.runtime.CompositionLocalProvider(
                com.starception.submission.ui.LocalWobbleIntensity provides syncState.wobbleIntensity,
                com.starception.submission.ui.LocalPullToSyncModifier provides syncState.pullModifier,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
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
            }
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
