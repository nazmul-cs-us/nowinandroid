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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.semantics.contentDescription
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
            val navBarHeight = if (vertical) 50.dp else 56.dp
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
        val processing by com.starception.submission.ui.search.SearchPrefillBus.processing
            .collectAsStateWithLifecycle()
        val level by com.starception.submission.ui.search.SearchPrefillBus.voiceLevel
            .collectAsStateWithLifecycle()
        VoiceAssistantButton(
            listening = listening,
            processing = processing,
            level = level,
            // Tracks navBarHeight so the voice button stays proportional to the pill.
            buttonSize = if (vertical) 44.dp else 52.dp,
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
 * Voice-assistant button: one five-bar unit across idle, listening, and
 * transcribing. Listening uses a mic-responsive traveling ripple. When capture
 * ends, those same straight paths progressively bend into five independent
 * curved dashes. The full-size dashes spiral inward at staggered depths without
 * joining into a circle or propeller. Keeping the paths continuous makes the
 * state change feel intentional, while tapping during processing cancels it.
 */
@Composable
private fun VoiceAssistantButton(
    listening: Boolean,
    processing: Boolean,
    level: Float,
    buttonSize: Dp = 60.dp,
    onClick: () -> Unit,
) {
    val listeningBlend by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "assistantListeningBlend",
    )
    val processingBlend by animateFloatAsState(
        targetValue = if (processing) 1f else 0f,
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "assistantProcessingBlend",
    )
    val amp by animateFloatAsState(
        targetValue = (level * 14f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f),
        label = "assistantAmp",
    )
    val ampActive = amp * listeningBlend
    val barMotion = rememberInfiniteTransition(label = "assistantBarMotion")
    val wavePhase by barMotion.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "assistantBarWave",
    )
    val swirlPhase by barMotion.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_320, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "assistantProcessingSwirl",
    )

    val container = MaterialTheme.colorScheme.onSurface
    val barColor = MaterialTheme.colorScheme.surface

    Surface(
        // During processing the same tap routes back to Whisper and cancels it.
        onClick = onClick,
        shape = CircleShape,
        color = container,
        shadowElevation = 2.dp,
        modifier = Modifier
            .size(buttonSize)
            .semantics {
                contentDescription = when {
                    processing -> "Cancel voice processing"
                    listening -> "Finish listening"
                    else -> "Start voice search"
                }
            }
            .graphicsLayer {
                val s = 1f + 0.06f * ampActive
                scaleX = s
                scaleY = s
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .size(if (buttonSize < 56.dp) 25.dp else 30.dp)
                    .graphicsLayer {
                        val processingScale = 1f + 0.25f * processingBlend
                        scaleX = processingScale
                        scaleY = processingScale
                    },
            ) {
                val rest = floatArrayOf(0.40f, 0.62f, 1f, 0.62f, 0.40f)
                val barCount = rest.size
                val slot = size.width / barCount
                val barWidth = slot * 0.46f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val swirlInnerRadius = size.minDimension * 0.16f
                val swirlOuterRadius = size.minDimension * 0.45f
                val processingBarLength = size.minDimension * 0.17f
                val fullTurn = (Math.PI * 2.0).toFloat()
                val segmentStep = fullTurn / barCount

                for (index in 0 until barCount) {
                    // The former processing ripple now communicates active
                    // capture. Mic energy gives louder speech a little more
                    // presence without making the whole button bounce wildly.
                    val captureWave = (
                        kotlin.math.sin((wavePhase - (index * 1.05f)).toDouble()).toFloat() + 1f
                        ) / 2f
                    val captureHeight = (
                        0.28f +
                            captureWave * (0.60f + ampActive * 0.12f) +
                            ampActive * rest[index] * 0.08f
                        ).coerceIn(0.24f, 1f)
                    val captureAlpha = 0.58f + captureWave * 0.42f
                    val barHeightFraction = rest[index] +
                        (captureHeight - rest[index]) * listeningBlend
                    val barAlpha = 1f + (captureAlpha - 1f) * listeningBlend

                    val rowX = slot * index + slot / 2f
                    val barHeight = size.height * barHeightFraction
                    val lineHalfLength = ((barHeight - barWidth) / 2f).coerceAtLeast(0f)
                    val lineStart = Offset(rowX, centerY - lineHalfLength)
                    val lineEnd = Offset(rowX, centerY + lineHalfLength)
                    val lineLength = lineHalfLength * 2f
                    val lineControl1 = Offset(rowX, lineStart.y + lineLength / 3f)
                    val lineControl2 = Offset(rowX, lineStart.y + lineLength * 2f / 3f)

                    // Each dash advances from the outer orbit toward the centre.
                    // Its centre-line length and stroke width stay constant;
                    // only its radius, curvature, angle, and edge fade change.
                    val inwardProgress = (
                        swirlPhase / fullTurn + index.toFloat() / barCount
                        ) % 1f
                    val easedInward = inwardProgress * inwardProgress *
                        (3f - 2f * inwardProgress)
                    val dashRadius = swirlOuterRadius -
                        (swirlOuterRadius - swirlInnerRadius) * easedInward
                    val dashCenterAngle = swirlPhase - (Math.PI / 2.0).toFloat() +
                        index * segmentStep + easedInward * segmentStep * 0.72f
                    val dashSweep = (processingBarLength / dashRadius).coerceAtMost(1.42f)
                    val dashStartAngle = dashCenterAngle - dashSweep / 2f
                    val dashEndAngle = dashCenterAngle + dashSweep / 2f
                    fun circlePoint(angle: Float) = Offset(
                        x = centerX +
                            kotlin.math.cos(angle.toDouble()).toFloat() * dashRadius,
                        y = centerY +
                            kotlin.math.sin(angle.toDouble()).toFloat() * dashRadius,
                    )
                    val dashStart = circlePoint(dashStartAngle)
                    val dashEnd = circlePoint(dashEndAngle)
                    // Cubic Bézier approximation of the short circular arc.
                    val controlDistance = 4f / 3f *
                        kotlin.math.tan((dashSweep / 4f).toDouble()).toFloat() * dashRadius
                    val dashControl1 = Offset(
                        dashStart.x -
                            kotlin.math.sin(dashStartAngle.toDouble()).toFloat() * controlDistance,
                        dashStart.y +
                            kotlin.math.cos(dashStartAngle.toDouble()).toFloat() * controlDistance,
                    )
                    val dashControl2 = Offset(
                        dashEnd.x +
                            kotlin.math.sin(dashEndAngle.toDouble()).toFloat() * controlDistance,
                        dashEnd.y -
                            kotlin.math.cos(dashEndAngle.toDouble()).toFloat() * controlDistance,
                    )
                    fun morph(from: Offset, to: Offset) = Offset(
                        from.x + (to.x - from.x) * processingBlend,
                        from.y + (to.y - from.y) * processingBlend,
                    )

                    val pathStart = morph(lineStart, dashStart)
                    val pathControl1 = morph(lineControl1, dashControl1)
                    val pathControl2 = morph(lineControl2, dashControl2)
                    val pathEnd = morph(lineEnd, dashEnd)
                    val bentPath = Path().apply {
                        moveTo(pathStart.x, pathStart.y)
                        cubicTo(
                            pathControl1.x,
                            pathControl1.y,
                            pathControl2.x,
                            pathControl2.y,
                            pathEnd.x,
                            pathEnd.y,
                        )
                    }

                    val edgeFade = minOf(
                        (inwardProgress / 0.14f).coerceIn(0f, 1f),
                        ((1f - inwardProgress) / 0.20f).coerceIn(0f, 1f),
                    )
                    val dashAlpha = 0.12f + edgeFade * 0.88f
                    val alpha = barAlpha + (dashAlpha - barAlpha) * processingBlend
                    val strokeWidth = barWidth

                    drawPath(
                        path = bentPath,
                        color = barColor.copy(alpha = alpha),
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
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
        val rawForbiddenPrayerTime = if (mainViewModel != null) {
            val warning by mainViewModel.forbiddenPrayerTimeState.collectAsStateWithLifecycle()
            warning
        } else {
            com.starception.submission.feature.prayertimes.wobble.ForbiddenPrayerTimeState()
        }
        val appLevelForbiddenPrayerTime = if (!isOnHome) {
            rawForbiddenPrayerTime
        } else {
            com.starception.submission.feature.prayertimes.wobble.ForbiddenPrayerTimeState()
        }

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
        val rawVoiceFeedback by com.starception.submission.ui.search.SearchPrefillBus.voiceFeedback
            .collectAsStateWithLifecycle()
        val appLevelVoiceFeedback = if (isOnHome) null else rawVoiceFeedback
        PullToSyncContainer(
            // Suppress the outer visual on Home (the inner container in
            // PrayerTimesScreen renders it there). When the user navigates away
            // mid-sync, this gate flips and the app-level visual picks up the
            // still-true VM state, so the banner persists across the transition.
            isRefreshing = if (isOnHome) false else isRefreshing,
            onRefresh = { mainViewModel?.setSyncing(true) },
            syncResultText = appLevelVoiceFeedback,
            onSyncResultClick = appLevelVoiceFeedback?.let {
                {
                    com.starception.submission.ui.search.SearchPrefillBus.clearVoiceFeedback()
                    com.starception.submission.ui.search.SearchPrefillBus.requestVoiceSearch()
                }
            },
            onSyncResultDismiss = {
                com.starception.submission.ui.search.SearchPrefillBus.clearVoiceFeedback()
            },
            idleContainerColor = Color.Transparent,
            enabled = !isOnHome,
            downloadProgress = downloadProgress,
            downloadLabel = downloadLabel,
            isTtsPreparing = isTtsPreparing,
            mediaBar = mediaSyncBarRow(
                state = appLevelMediaState,
                onAction = { action -> mainViewModel?.globalMedia?.handleAction(action) },
                onTitleClick = {
                    // Route by playback source so every mini-bar title opens its
                    // detail page (surah, hadith, or fortress dua), not just Quran.
                    appState.navController.navigateToMediaSourceDetail(
                        appLevelMediaState.playback.source,
                    )
                },
                isTtsPreparing = isTtsPreparing,
            ),
            prayerAlertState = appLevelPrayerAlert,
            forbiddenPrayerTimeState = appLevelForbiddenPrayerTime,
            silentModeState = appLevelSilentModeState,
            islamicEventState = appLevelIslamicEventState,
            onIslamicEventClick = { event ->
                com.starception.submission.ui.search.SearchPrefillBus.requestSearch(event.searchQuery)
            },
            mushafBar = mushafSyncBarRow(
                state = appLevelMushafState,
                onPrevious = { com.starception.submission.feature.surah.MushafMiniBarBus.onPrevious?.invoke() },
                onNext = { com.starception.submission.feature.surah.MushafMiniBarBus.onNext?.invoke() },
                onOpenInfo = { com.starception.submission.feature.surah.MushafMiniBarBus.onOpenInfo?.invoke() },
            ),
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
                radius = 3.5.dp.toPx(),
                // Anchor to the 24dp icon instead of private Material navigation
                // indicator dimensions. The old offset pushed the dot into the
                // rounded cell clip, leaving only a leaf-shaped sliver visible.
                center = center + Offset(10.dp.toPx(), -10.dp.toPx()),
            )
        }
    }

private fun NavDestination?.isRouteInHierarchy(route: KClass<*>) =
    this?.hierarchy?.any {
        it.hasRoute(route)
    } ?: false
