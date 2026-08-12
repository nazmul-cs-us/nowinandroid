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
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import com.starception.submission.auth.AuthUiState
import com.starception.submission.auth.AuthViewModel
import com.starception.submission.auth.ProfileSheet
import com.starception.submission.usersettings.ui.CountrySwitchConsentSheet
import com.starception.submission.usersettings.ui.CountrySwitchViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
import com.starception.submission.core.designsystem.component.NiaNavigationSuiteScaffold
import com.starception.submission.core.designsystem.component.NiaTopAppBar
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.designsystem.theme.GradientColors
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.designsystem.theme.LocalGradientColors
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush
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
    val isDarkTheme = LocalDarkTheme.current
    DisposableEffect(shouldHideStatusBar, isDarkTheme) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        // The app theme can differ from the device theme, so edge-to-edge cannot
        // rely on enableEdgeToEdge's one-time system setting for icon contrast.
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Draw the shared main-page canvas at the app-shell level so it
                // continues behind the floating navigation and gesture inset.
                .background(mainPageBackgroundBrush()),
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
 * Floating pill navigation (reference design): the existing top-level
 * destinations in a rounded floating container — the selected tab gets its own
 * tinted bubble — plus the circular voice-search button. Hidden while the
 * search overlay is open so it never floats over the results list.
 *
 * Lays out horizontally along the screen bottom in portrait, and vertically
 * along the left edge in landscape ([vertical] = true) where it replaces the
 * old navigation rail.
 */
@Composable
private fun NiaFloatingBottomBar(
    appState: NiaAppState,
    unreadDestinations: Set<TopLevelDestination>,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val isSearchOpen by com.starception.submission.ui.search.SearchPrefillBus
        .isSearchOpen.collectAsStateWithLifecycle()
    if (isSearchOpen) return

    // The rounded pill holding the destination items with the gooey selection
    // bubble. [sizeModifier] carries the scope-specific main-axis sizing —
    // weight(1f) from the portrait Row, width(64.dp) from the landscape Column —
    // since weight() can only be resolved inside the calling Row/Column scope.
    // Keep the pill's composable identity stable while the destination changes.
    // Recreating this capturing lambda resets the indicator animation at its new target,
    // which looks like a jump instead of a travelling liquid mass.
    val latestCurrentDestination = rememberUpdatedState(currentDestination)
    val latestUnreadDestinations = rememberUpdatedState(unreadDestinations)
    val pill = remember(appState, vertical) {
        @Composable { sizeModifier: Modifier ->
            val darkTheme = LocalDarkTheme.current
            val colorScheme = MaterialTheme.colorScheme
            val navBarHeight = if (vertical) 56.dp else 64.dp
            val capsuleGap = 4.dp
            Surface(
                shape = RoundedCornerShape(navBarHeight / 2),
                color = if (darkTheme) {
                    colorScheme.surfaceContainer
                } else {
                    colorScheme.surfaceContainerHighest
                },
                shadowElevation = 4.dp,
                modifier = sizeModifier,
            ) {
                val destinations = appState.topLevelDestinations
                val selectedIndex = destinations.indexOfFirst { destination ->
                    latestCurrentDestination.value
                        .isRouteInHierarchy(destination.baseRoute)
                }
                BoxWithConstraints(
                    modifier = if (vertical) {
                        // Landscape has much less vertical room than portrait has width.
                        // Keep each destination to a compact 44dp cell so the rail and
                        // voice action read as floating controls instead of a full-height
                        // sidebar.
                        Modifier
                            .width(56.dp)
                            .height((destinations.size * 44 + 12).dp)
                            .padding(vertical = 6.dp)
                    } else {
                        Modifier.height(navBarHeight)
                    },
                ) {
                    // Size of a single item cell along the main axis, and the
                    // bubble's target position for the selected tab.
                    val horizontalContentInset = 12.dp
                    val itemExtent = if (vertical) {
                        maxHeight / destinations.size
                    } else {
                        (maxWidth - horizontalContentInset * 2) / destinations.size
                    }
                    // Extend the active shape beyond its icon cell while keeping
                    // the first and last capsules inset from the bar's outer edge.
                    val bubbleMainSize = if (vertical) {
                        38.dp
                    } else {
                        itemExtent + (horizontalContentInset - capsuleGap) * 2
                    }
                    val bubbleTarget = if (vertical) {
                        itemExtent * selectedIndex.coerceAtLeast(0) +
                            (itemExtent - bubbleMainSize) / 2
                    } else {
                        horizontalContentInset +
                            itemExtent * selectedIndex.coerceAtLeast(0) +
                            (itemExtent - bubbleMainSize) / 2
                    }

                    // Match the reference motion: the edge facing the destination
                    // moves first, then the opposite edge follows. Height and corner
                    // radius never change, so the indicator remains one clean capsule.
                    val density = LocalDensity.current
                    val targetPx = with(density) { bubbleTarget.toPx() }
                    val leadingPosition = remember { Animatable(targetPx) }
                    val trailingPosition = remember { Animatable(targetPx) }
                    LaunchedEffect(targetPx, selectedIndex) {
                        if (selectedIndex < 0 || abs(targetPx - leadingPosition.value) < 0.5f) {
                            return@LaunchedEffect
                        }
                        coroutineScope {
                            launch {
                                leadingPosition.animateTo(
                                    targetValue = targetPx,
                                    animationSpec = tween(
                                        durationMillis = 280,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                            launch {
                                delay(85)
                                trailingPosition.animateTo(
                                    targetValue = targetPx,
                                    animationSpec = tween(
                                        durationMillis = 280,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                        }
                    }
                    val bubbleAlpha by animateFloatAsState(
                        // Hidden when no top-level tab is selected (detail screens).
                        targetValue = if (selectedIndex >= 0) 1f else 0f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "navBubbleAlpha",
                    )
                    val bubbleColor = if (darkTheme) {
                        colorScheme.surfaceBright
                    } else {
                        Color.White
                    }
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = bubbleAlpha },
                    ) {
                        val mainSize = bubbleMainSize.toPx()
                        val crossSize = if (vertical) {
                            44.dp.toPx()
                        } else {
                            (navBarHeight - capsuleGap * 2).toPx()
                        }
                        if (vertical) {
                            val halfCross = crossSize / 2f
                            val top = minOf(leadingPosition.value, trailingPosition.value)
                            val bottom = maxOf(leadingPosition.value, trailingPosition.value) + mainSize
                            drawRoundRect(
                                color = bubbleColor,
                                topLeft = Offset(size.width / 2f - halfCross, top),
                                size = Size(crossSize, bottom - top),
                                cornerRadius = CornerRadius(halfCross, halfCross),
                            )
                        } else {
                            val halfCross = crossSize / 2f
                            val left = minOf(leadingPosition.value, trailingPosition.value)
                            val right = maxOf(leadingPosition.value, trailingPosition.value) + mainSize
                            drawRoundRect(
                                color = bubbleColor,
                                topLeft = Offset(left, size.height / 2f - halfCross),
                                size = Size(right - left, crossSize),
                                cornerRadius = CornerRadius(halfCross, halfCross),
                            )
                        }
                    }

                    // Match the reference's icon-only cells. Destination names remain
                    // available to accessibility through each icon's description.
                    val itemCell = @Composable { destination: TopLevelDestination, weight: Modifier ->
                        val hasUnread = latestUnreadDestinations.value.contains(destination)
                        val interactionSource = remember(destination) { MutableInteractionSource() }
                        val contentTint = MaterialTheme.colorScheme.onSurface
                        Box(
                            modifier = weight
                                .fillMaxSize()
                                .clip(RoundedCornerShape(50))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { appState.navigateToTopLevelDestination(destination) }
                                .testTag("NiaNavItem")
                                .then(if (hasUnread) Modifier.notificationDot() else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = destination.unselectedIcon,
                                contentDescription = stringResource(destination.iconTextId),
                                tint = contentTint,
                                modifier = Modifier.size(if (vertical) 22.dp else 24.dp),
                            )
                        }
                    }

                    if (vertical) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            destinations.forEach { destination ->
                                itemCell(destination, Modifier.weight(1f))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = horizontalContentInset),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            destinations.forEach { destination ->
                                itemCell(destination, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Circular voice-assistant button beside the floating nav — the same
    // on-device Whisper flow as the search bar's mic (via the bus), now with a
    // live listening animation driven by the real capture state and mic level.
    val voiceButton = @Composable {
        val listening by com.starception.submission.ui.search.SearchPrefillBus.listening
            .collectAsStateWithLifecycle()
        val level by com.starception.submission.ui.search.SearchPrefillBus.voiceLevel
            .collectAsStateWithLifecycle()
        VoiceAssistantButton(
            listening = listening,
            level = level,
            buttonSize = if (vertical) 50.dp else 60.dp,
            onClick = { com.starception.submission.ui.search.SearchPrefillBus.requestVoiceSearch() },
        )
    }

    if (vertical) {
        // Left edge: pill + voice button stacked and vertically centered,
        // respecting the camera cutout / system bars on the start/top/bottom.
        Column(
            modifier = modifier
                .fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(start = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            pill(Modifier.width(56.dp))
            Spacer(modifier = Modifier.height(8.dp))
            voiceButton()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pill(Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            voiceButton()
        }
    }
}

/**
 * Voice-assistant button: the circular button beside the floating nav. It idles
 * as the five-bar graphic-eq mark; while [listening], those same five bars
 * become a live equalizer — each bar rises and falls with the mic [level] (with
 * a gentle shimmer even in silence so it reads as actively listening), and the
 * button breathes subtly with your voice. Tapping toggles capture (start / stop)
 * via the shared bus; this composable only reflects state.
 */
@Composable
private fun VoiceAssistantButton(
    listening: Boolean,
    level: Float,
    buttonSize: Dp = 60.dp,
    onClick: () -> Unit,
) {
    // Eased state 0 (idle) → 1 (listening); a light spring gives an organic
    // wake-up / settle rather than a hard switch.
    val progress by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "assistantProgress",
    )
    // Smoothed mic amplitude; folded with progress so it only drives the bars
    // while actually listening (and never lingers after stop).
    val amp by animateFloatAsState(
        targetValue = (level * 14f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f),
        label = "assistantAmp",
    )
    val ampActive = amp * progress

    val container = MaterialTheme.colorScheme.onSurface
    val barColor = MaterialTheme.colorScheme.surface

    // Time source for the per-bar shimmer while listening.
    val infinite = rememberInfiniteTransition(label = "assistant")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "assistantBars",
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        shadowElevation = 2.dp,
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                val s = 1f + 0.06f * ampActive
                scaleX = s
                scaleY = s
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.size(if (buttonSize < 56.dp) 21.dp else 24.dp)) {
                // Five bars echoing the graphic-eq icon. The resting silhouette
                // (heights below) shows when idle; while listening each bar is
                // driven by the mic level plus a small per-bar shimmer, so it
                // visibly tracks the user's voice.
                val rest = floatArrayOf(0.4f, 0.6f, 1f, 0.6f, 0.4f)
                val bars = rest.size
                val slot = size.width / bars
                val barW = slot * 0.5f
                val cy = size.height / 2f
                val maxH = size.height
                val twoPi = 2f * Math.PI.toFloat()
                for (i in 0 until bars) {
                    val wob = 0.5f + 0.5f * kotlin.math.sin((t + i * 0.16f) * twoPi)
                    // Small idle shimmer while listening + a voice-driven leap.
                    val drive = (ampActive + 0.12f * progress).coerceIn(0f, 1f)
                    val live = ((0.2f + 0.8f * wob) * drive + 0.14f).coerceIn(0.12f, 1f)
                    // Blend from the resting icon shape (idle) to the live bar.
                    val frac = rest[i] * (1f - progress) + live * progress
                    val h = (maxH * frac).coerceAtLeast(barW)
                    val x = slot * i + slot / 2f
                    drawLine(
                        color = barColor,
                        start = Offset(x, cy - h / 2f),
                        end = Offset(x, cy + h / 2f),
                        strokeWidth = barW,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * Custom landscape layout: the content fills the screen and the vertical
 * floating pill bar (the same reference design used at the bottom in portrait)
 * is overlaid on the left edge, replacing the old navigation rail.
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
    Box(modifier = Modifier.fillMaxSize()) {
        // Inset the content to the RIGHT of the vertical floating nav bar so the bar
        // never overlaps tile content. The bar is a 64.dp pill with 12.dp start
        // padding, itself shifted right by the safe-area start inset (camera cutout
        // sits on the left edge in landscape), so the content must reserve that same
        // inset PLUS 76.dp to clear the compact landscape pill.
        NiaMainContent(
            appState = appState,
            snackbarHostState = snackbarHostState,
            onTopAppBarActionClick = onTopAppBarActionClick,
            modifier = modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                .padding(start = 76.dp),
            isLandscape = true,
            mainViewModel = mainViewModel,
            deepLinkCourseId = deepLinkCourseId,
        )
        NiaFloatingBottomBar(
            appState = appState,
            unreadDestinations = unreadDestinations,
            currentDestination = currentDestination,
            vertical = true,
            modifier = Modifier.align(Alignment.CenterStart),
        )
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
        // Top-level pages own their canvas. Keeping the shell transparent lets
        // that canvas continue behind the floating nav and system gesture bar.
        containerColor = Color.Transparent,
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
        // Non-download long tasks (e.g. synthesising a guided session's voice lines)
        // share the same banner, but a real CDN download always wins.
        val appTaskProgress by AppTaskProgressBus.state.collectAsStateWithLifecycle()
        // Suppress on HOME — PrayerTimesScreen has its own PullToSyncContainer that shows it.
        val downloadProgress = when {
            isOnHome -> 0f
            isDownloadingRaw -> rawDownloadProgress
            else -> appTaskProgress?.progress ?: 0f
        }
        val vmDownloadLabel = if (mainViewModel != null) {
            val label by mainViewModel.contentDownloadLabel.collectAsStateWithLifecycle()
            label
        } else {
            ""
        }
        val downloadLabel = if (!isDownloadingRaw && appTaskProgress != null) {
            appTaskProgress?.label.orEmpty()
        } else {
            vmDownloadLabel
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
            idleContainerColor = Color.Transparent,
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
            onMushafOpenInfo = { com.starception.submission.feature.surah.MushafMiniBarBus.onOpenInfo?.invoke() },
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
