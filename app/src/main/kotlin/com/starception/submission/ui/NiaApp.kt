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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.starception.submission.MainActivityViewModel
import com.starception.submission.R
import com.starception.submission.auth.AuthUiState
import com.starception.submission.auth.AuthViewModel
import com.starception.submission.auth.ProfileSheet
import com.starception.submission.core.designsystem.component.NiaBackground
import com.starception.submission.core.designsystem.component.NiaGradientBackground
import com.starception.submission.core.designsystem.theme.GradientColors
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.designsystem.theme.LocalGradientColors
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.feature.prayertimes.wobble.PrayerAlertState
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import com.starception.submission.media.MediaControllerUiState
import com.starception.submission.navigation.NiaNavHost
import com.starception.submission.navigation.TopLevelDestination
import com.starception.submission.navigation.navigateToMediaSourceDetail
import com.starception.submission.settings.navigation.navigateToSettings
import com.starception.submission.ui.search.VoiceAssistantNudgeBus
import com.starception.submission.usersettings.ui.CountrySwitchConsentSheet
import com.starception.submission.usersettings.ui.CountrySwitchViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.reflect.KClass

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

            // Offline is now surfaced inside the PullToSyncContainer strip (see
            // NiaMainContent) rather than as a bottom snackbar.

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

    // Once the bot has become a Now Nudge, the destination pill can expand into
    // its vacated slot. On dismissal this value drives the slot open again while
    // the same shared-bounds surface returns to the bot.
    var nudgeOccupiesNavigation by remember { mutableStateOf(false) }
    val voiceSlotWidth by animateDpAsState(
        targetValue = if (nudgeOccupiesNavigation) 0.dp else 62.dp,
        animationSpec = tween(
            durationMillis = NUDGE_MORPH_DURATION_MILLIS,
            easing = FastOutSlowInEasing,
        ),
        label = "bottomNavigationVoiceSlotWidth",
    )

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
    val voiceButton = @Composable { voiceModifier: Modifier ->
        val listening by com.starception.submission.ui.search.SearchPrefillBus.listening
            .collectAsStateWithLifecycle()
        val processing by com.starception.submission.ui.search.SearchPrefillBus.processing
            .collectAsStateWithLifecycle()
        val level by com.starception.submission.ui.search.SearchPrefillBus.voiceLevel
            .collectAsStateWithLifecycle()
        val nudge by VoiceAssistantNudgeBus.nudge.collectAsStateWithLifecycle()
        val generatingSuggestion by VoiceAssistantNudgeBus.generatingSuggestion
            .collectAsStateWithLifecycle()
        VoiceAssistantButton(
            listening = listening,
            processing = processing,
            level = level,
            nudge = nudge,
            generatingSuggestion = generatingSuggestion,
            verticalLayout = vertical,
            // Tracks navBarHeight so the voice button stays proportional to the pill.
            buttonSize = if (vertical) 44.dp else 52.dp,
            onVoiceClick = {
                com.starception.submission.ui.search.SearchPrefillBus.requestVoiceSearch()
            },
            onTypedQuestion = VoiceAssistantNudgeBus::submitTypedPrompt,
            onNudgeAction = VoiceAssistantNudgeBus::requestAction,
            onNudgeRequest = { VoiceAssistantNudgeBus.requestSuggestion() },
            onNudgeDismiss = VoiceAssistantNudgeBus::requestDismiss,
            onNavigationOccupationChanged = { nudgeOccupiesNavigation = it },
            modifier = voiceModifier,
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
            voiceButton(Modifier)
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pill(Modifier.weight(1f))
                Spacer(modifier = Modifier.width(voiceSlotWidth))
            }
            voiceButton(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(end = 12.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * Voice-assistant button: one five-bar unit across idle, listening, and
 * transcribing. Listening uses a mic-responsive traveling ripple. When capture
 * ends, those same straight paths progressively bend into five independent
 * curved dashes. The full-size dashes spiral inward at staggered depths without
 * joining into a circle or propeller. A nudge first replaces the bars with a
 * generation sparkle, then morphs directly into the suggestion. The thinking
 * surface stays present throughout so the result never appears after a blank gap.
 */
private enum class VoiceAssistantSurface {
    Button,
    Composer,
    Thinking,
    Nudge,
    Quiz,
}

private const val NUDGE_MORPH_DURATION_MILLIS = 420
private const val NUDGE_REVEAL_DELAY_MILLIS = 5_600L
private const val NUDGE_THINKING_MESSAGE_MILLIS = 1_400L
private const val NUDGE_RETURN_CONTENT_HOLD_MILLIS =
    NUDGE_MORPH_DURATION_MILLIS.toLong() + 40L

@Composable
private fun VoiceAssistantButton(
    listening: Boolean,
    processing: Boolean,
    level: Float,
    nudge: DeenlyNudge?,
    generatingSuggestion: Boolean,
    verticalLayout: Boolean,
    buttonSize: Dp = 60.dp,
    onVoiceClick: () -> Unit,
    onTypedQuestion: (String) -> Unit,
    onNudgeAction: () -> Unit,
    onNudgeRequest: () -> Unit,
    onNudgeDismiss: () -> Unit,
    onNavigationOccupationChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
    val nudgeGlowPulse by barMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "assistantNudgeGlow",
    )
    val container = MaterialTheme.colorScheme.onSurface
    val barColor = MaterialTheme.colorScheme.surface
    val quizOpen by VoiceAssistantNudgeBus.quizOpen.collectAsStateWithLifecycle()
    val generatedQuizQuestion by VoiceAssistantNudgeBus.generatedQuizQuestion
        .collectAsStateWithLifecycle()
    BackHandler(enabled = quizOpen, onBack = VoiceAssistantNudgeBus::closeQuiz)
    var composerOpen by remember { mutableStateOf(false) }
    var typedQuestion by remember { mutableStateOf("") }
    val composerFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    BackHandler(enabled = composerOpen) {
        composerOpen = false
        keyboardController?.hide()
    }
    LaunchedEffect(composerOpen) {
        if (composerOpen) {
            delay(NUDGE_MORPH_DURATION_MILLIS.toLong())
            composerFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    fun submitTypedQuestion() {
        val question = typedQuestion.trim()
        if (question.isEmpty()) return
        typedQuestion = ""
        composerOpen = false
        keyboardController?.hide()
        onTypedQuestion(question)
    }
    var revealedNudgeId by remember { mutableStateOf<String?>(null) }
    var sparklingNudgeId by remember { mutableStateOf<String?>(null) }
    var requestedNudgeId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        VoiceAssistantNudgeBus.suggestionReady.collect { readyNudgeId ->
            requestedNudgeId = readyNudgeId
        }
    }
    LaunchedEffect(nudge?.id, requestedNudgeId) {
        revealedNudgeId = null
        sparklingNudgeId = null
        if (nudge == null) {
            requestedNudgeId = null
            return@LaunchedEffect
        }
        val nudgeId = nudge.id
        if (requestedNudgeId != nudgeId) return@LaunchedEffect
        sparklingNudgeId = nudgeId
        delay(NUDGE_REVEAL_DELAY_MILLIS)
        revealedNudgeId = nudgeId
        sparklingNudgeId = null
    }
    val canPresentNudge = nudge != null && requestedNudgeId == nudge.id && !listening && !processing
    val isPreparingNudge = canPresentNudge && revealedNudgeId != nudge?.id
    val isGeneratingNudge = generatingSuggestion || isPreparingNudge
    val showNudge = canPresentNudge && revealedNudgeId == nudge?.id
    val thinkingMessages = listOf(
        "Understanding context",
        "Finding trusted source",
        "Verifying source",
        when (nudge?.action) {
            DeenlyNudgeAction.PLAY_QUIZ -> "Composing question"
            DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION -> "Preparing insight"
            else -> "Preparing suggestion"
        },
    )
    var thinkingMessageIndex by remember { mutableStateOf(0) }
    LaunchedEffect(isGeneratingNudge) {
        thinkingMessageIndex = 0
        while (isGeneratingNudge && thinkingMessageIndex < thinkingMessages.lastIndex) {
            delay(NUDGE_THINKING_MESSAGE_MILLIS)
            thinkingMessageIndex++
        }
    }
    val thinkingTextStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
    )
    val thinkingTextMeasurer = rememberTextMeasurer()
    val thinkingMessage = thinkingMessages[thinkingMessageIndex]
    val thinkingPillWidth by animateDpAsState(
        targetValue = with(LocalDensity.current) {
            thinkingTextMeasurer.measure(
                text = thinkingMessage,
                style = thinkingTextStyle,
                maxLines = 1,
            ).size.width.toDp()
        } + if (verticalLayout) 54.dp else 66.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "assistantThinkingWidth",
    )
    var renderedNudge by remember { mutableStateOf(nudge) }
    LaunchedEffect(nudge) {
        if (nudge != null) {
            renderedNudge = nudge
        } else {
            // Keep the outgoing card stable until its shared-bounds return to the bot completes.
            delay(NUDGE_RETURN_CONTENT_HOLD_MILLIS)
            renderedNudge = null
        }
    }
    val nudgeBlend by animateFloatAsState(
        targetValue = if (isGeneratingNudge) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "assistantNudgeBlend",
    )
    val nudgeBrush = Brush.horizontalGradient(
        colors = if (LocalDarkTheme.current) {
            listOf(Color(0xFF303044), Color(0xFF25383E), Color(0xFF273D38))
        } else {
            listOf(Color(0xFFEDEAFF), Color(0xFFE8F7FF), Color(0xFFE6FBF2))
        },
    )
    val assistantGlowColor = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val nudgePullThreshold = with(LocalDensity.current) { 40.dp.toPx() }
    val nudgePullHapticStep = with(LocalDensity.current) { 12.dp.toPx() }
    var nudgePullDistance by remember(nudge?.id) { mutableStateOf(0f) }
    val assistantSurface = when {
        quizOpen -> VoiceAssistantSurface.Quiz
        composerOpen -> VoiceAssistantSurface.Composer
        isGeneratingNudge -> VoiceAssistantSurface.Thinking
        showNudge -> VoiceAssistantSurface.Nudge
        else -> VoiceAssistantSurface.Button
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(assistantSurface, verticalLayout) {
        onNavigationOccupationChanged(
            !verticalLayout && assistantSurface == VoiceAssistantSurface.Nudge,
        )
    }
    val containerCorner by animateDpAsState(
        targetValue = when (assistantSurface) {
            VoiceAssistantSurface.Button -> buttonSize / 2
            VoiceAssistantSurface.Composer -> 22.dp
            VoiceAssistantSurface.Thinking -> 22.dp
            VoiceAssistantSurface.Nudge -> 24.dp
            VoiceAssistantSurface.Quiz -> 28.dp
        },
        animationSpec = tween(NUDGE_MORPH_DURATION_MILLIS, easing = FastOutSlowInEasing),
        label = "assistantContainerCorner",
    )
    val containerElevation by animateDpAsState(
        targetValue = when (assistantSurface) {
            VoiceAssistantSurface.Button -> 2.dp
            VoiceAssistantSurface.Composer -> 4.dp
            VoiceAssistantSurface.Thinking -> 3.dp
            VoiceAssistantSurface.Nudge -> 3.dp
            VoiceAssistantSurface.Quiz -> 8.dp
        },
        animationSpec = tween(NUDGE_MORPH_DURATION_MILLIS, easing = FastOutSlowInEasing),
        label = "assistantContainerElevation",
    )
    val expandedContainerColor = if (LocalDarkTheme.current) {
        Color(0xFF29373B)
    } else {
        Color(0xFFE9F5F7)
    }
    val containerColor by animateColorAsState(
        targetValue = if (assistantSurface == VoiceAssistantSurface.Button) {
            container
        } else {
            expandedContainerColor
        },
        animationSpec = tween(NUDGE_MORPH_DURATION_MILLIS, easing = FastOutSlowInEasing),
        label = "assistantContainerColor",
    )
    SharedTransitionLayout(
        modifier = if (verticalLayout) modifier.size(buttonSize) else modifier,
    ) {
        val voiceContainerState = rememberSharedContentState(
            key = "app-shell-voice-container",
        )
        Box(
            modifier = if (verticalLayout) Modifier else Modifier.fillMaxSize(),
            contentAlignment = if (verticalLayout) Alignment.Center else Alignment.BottomEnd,
        ) {
            AnimatedContent(
                // Loosen the incoming minimum width while retaining the app-shell's
                // maximum width. The idle bot can therefore stay circular, while
                // the nudge target is still free to fill the complete bottom bar.
                modifier = Modifier.wrapContentSize(),
                targetState = assistantSurface,
                transitionSpec = {
                    (EnterTransition.None togetherWith ExitTransition.None)
                        .using(
                            // sharedBounds owns the position and size morph. Snapping only the
                            // AnimatedContent host prevents a second competing bounds animation.
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ -> snap() },
                            ),
                        )
                },
                contentAlignment = if (verticalLayout) Alignment.Center else Alignment.BottomEnd,
                label = "assistantSurfaceContent",
            ) { surface ->
                when (surface) {
                VoiceAssistantSurface.Button -> Surface(
                    shape = RoundedCornerShape(containerCorner),
                    color = containerColor,
                    shadowElevation = containerElevation,
                    modifier = Modifier
                        .sharedBounds(
                            sharedContentState = voiceContainerState,
                            animatedVisibilityScope = this,
                            boundsTransform = { _, _ ->
                                tween(
                                    durationMillis = NUDGE_MORPH_DURATION_MILLIS,
                                    easing = FastOutSlowInEasing,
                                )
                            },
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        )
                        .size(buttonSize)
                        .clip(RoundedCornerShape(containerCorner))
                .pointerInput(
                    nudge?.id,
                    listening,
                    processing,
                    generatingSuggestion,
                    requestedNudgeId,
                ) {
                    val nudgeId = nudge?.id
                    val canRequestNudge = !listening &&
                        !processing &&
                        !generatingSuggestion &&
                        (nudgeId == null || requestedNudgeId != nudgeId)
                    if (!canRequestNudge) return@pointerInput

                    var totalDragY = 0f
                    var lastHapticStep = 0
                    var activationHapticSent = false
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragY = (totalDragY + dragAmount.y).coerceAtLeast(0f)
                            nudgePullDistance = totalDragY.coerceAtMost(nudgePullThreshold)
                            val hapticStep = (totalDragY / nudgePullHapticStep).toInt()
                            if (hapticStep > lastHapticStep && totalDragY < nudgePullThreshold) {
                                lastHapticStep = hapticStep
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (!activationHapticSent && totalDragY >= nudgePullThreshold) {
                                activationHapticSent = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragCancel = { nudgePullDistance = 0f },
                        onDragEnd = {
                            if (totalDragY >= nudgePullThreshold) {
                                onNudgeRequest()
                            }
                            nudgePullDistance = 0f
                        },
                    )
                }
                .combinedClickable(
                    onClick = when {
                        showNudge -> onNudgeAction
                        isPreparingNudge -> ({})
                        else -> ({ composerOpen = true })
                    },
                    onLongClick = onNudgeRequest,
                )
                .semantics {
                    contentDescription = when {
                        showNudge -> nudge?.label.orEmpty()
                        isPreparingNudge -> "Generating suggestion"
                        generatingSuggestion -> "Generating suggestion"
                        processing -> "Voice processing in progress"
                        listening -> "Finish listening"
                        nudge != null ->
                            "Now Nudge. Tap to type, long press or swipe down for a suggestion"
                        else -> "Now Nudge. Tap to type or long press for a suggestion"
                    }
                }
                .graphicsLayer {
                    val s = 1f + 0.06f * ampActive
                    scaleX = s
                    scaleY = s
                    translationY = nudgePullDistance * 0.24f
                },
                ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = fadeIn(
                            tween(durationMillis = 120, delayMillis = 280),
                        ),
                        exit = fadeOut(tween(durationMillis = 70)),
                    ),
            ) {
                Canvas(
                    modifier = Modifier
                        .size(if (buttonSize < 56.dp) 25.dp else 30.dp)
                        .graphicsLayer {
                            val iconScale = 1f + 0.25f * processingBlend - 0.10f * nudgeBlend
                            scaleX = iconScale
                            scaleY = iconScale
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
                    fun morph(from: Offset, to: Offset, fraction: Float) = Offset(
                        from.x + (to.x - from.x) * fraction,
                        from.y + (to.y - from.y) * fraction,
                    )

                    val processedStart = morph(lineStart, dashStart, processingBlend)
                    val processedControl1 = morph(lineControl1, dashControl1, processingBlend)
                    val processedControl2 = morph(lineControl2, dashControl2, processingBlend)
                    val processedEnd = morph(lineEnd, dashEnd, processingBlend)
                    val bentPath = Path().apply {
                        moveTo(processedStart.x, processedStart.y)
                        cubicTo(
                            processedControl1.x,
                            processedControl1.y,
                            processedControl2.x,
                            processedControl2.y,
                            processedEnd.x,
                            processedEnd.y,
                        )
                    }

                    val edgeFade = minOf(
                        (inwardProgress / 0.14f).coerceIn(0f, 1f),
                        ((1f - inwardProgress) / 0.20f).coerceIn(0f, 1f),
                    )
                    val dashAlpha = 0.12f + edgeFade * 0.88f
                    val processedAlpha = barAlpha + (dashAlpha - barAlpha) * processingBlend
                    val alpha = processedAlpha * (1f - nudgeBlend)
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
                VoiceNudgeSparkles(
                    color = barColor,
                    morph = nudgeBlend,
                    modifier = Modifier
                        .size(if (buttonSize < 56.dp) 38.dp else 44.dp)
                        .graphicsLayer {
                            val scale = 0.90f + 0.10f * nudgeBlend
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
                }

                VoiceAssistantSurface.Composer -> Surface(
                    shape = RoundedCornerShape(containerCorner),
                    color = containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = containerElevation,
                    modifier = Modifier
                        .wrapContentSize(
                            align = if (verticalLayout) Alignment.CenterStart else Alignment.BottomEnd,
                            unbounded = true,
                        )
                        .offset(
                            x = if (verticalLayout) buttonSize + 8.dp else 0.dp,
                            y = if (verticalLayout) {
                                0.dp
                            } else {
                                -buttonSize - if (imeVisible) 96.dp else 8.dp
                            },
                        )
                        .sharedBounds(
                            sharedContentState = voiceContainerState,
                            animatedVisibilityScope = this,
                            boundsTransform = { _, _ ->
                                tween(
                                    durationMillis = NUDGE_MORPH_DURATION_MILLIS,
                                    easing = FastOutSlowInEasing,
                                )
                            },
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        )
                        .width(
                            if (verticalLayout) {
                                300.dp
                            } else {
                                minOf(
                                    360.dp,
                                    (LocalConfiguration.current.screenWidthDp - 24).dp,
                                )
                            },
                        )
                        .graphicsLayer {
                            shadowElevation = (6.dp + 2.dp * nudgeGlowPulse).toPx()
                            shape = RoundedCornerShape(containerCorner)
                            ambientShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                            spotShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                            clip = false
                        }
                        .drawBehind {
                            drawIntoCanvas { canvas ->
                                val glowPaint = android.graphics.Paint(
                                    android.graphics.Paint.ANTI_ALIAS_FLAG,
                                ).apply {
                                    color = assistantGlowColor.toArgb()
                                    alpha = ((0.34f + nudgeGlowPulse * 0.10f) * 255).toInt()
                                    maskFilter = BlurMaskFilter(
                                        11.dp.toPx(),
                                        BlurMaskFilter.Blur.NORMAL,
                                    )
                                }
                                canvas.nativeCanvas.drawRoundRect(
                                    0f,
                                    0f,
                                    size.width,
                                    size.height,
                                    containerCorner.toPx(),
                                    containerCorner.toPx(),
                                    glowPaint,
                                )
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .background(nudgeBrush)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Ask Now Nudge",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Answers use your downloaded Quran, Hadith, and dua sources",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    composerOpen = false
                                    keyboardController?.hide()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close")
                            }
                        }
                        OutlinedTextField(
                            value = typedQuestion,
                            onValueChange = { typedQuestion = it.take(240) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(composerFocusRequester),
                            label = { Text("Your question") },
                            placeholder = { Text("For example: fasting dua") },
                            minLines = 2,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitTypedQuestion() }),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tip: type “start quiz”",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    composerOpen = false
                                    keyboardController?.hide()
                                    onVoiceClick()
                                },
                            ) {
                                Icon(Icons.Rounded.Mic, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Voice")
                            }
                            Button(
                                onClick = ::submitTypedQuestion,
                                enabled = typedQuestion.isNotBlank(),
                            ) {
                                Text("Ask")
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                            }
                        }
                    }
                }

                VoiceAssistantSurface.Thinking -> Surface(
                    shape = RoundedCornerShape(containerCorner),
                    color = containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = containerElevation,
                    modifier = Modifier
                        .wrapContentSize(
                            align = if (verticalLayout) {
                                Alignment.CenterStart
                            } else {
                                Alignment.BottomEnd
                            },
                            unbounded = true,
                        )
                        .offset(
                            x = if (verticalLayout) buttonSize + 8.dp else 0.dp,
                            y = if (verticalLayout) 0.dp else -buttonSize - 8.dp,
                        )
                        .sharedBounds(
                            sharedContentState = voiceContainerState,
                            animatedVisibilityScope = this,
                            boundsTransform = { _, _ ->
                                tween(
                                    durationMillis = NUDGE_MORPH_DURATION_MILLIS,
                                    easing = FastOutSlowInEasing,
                                )
                            },
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        )
                        .width(thinkingPillWidth)
                        .heightIn(min = if (verticalLayout) 38.dp else 44.dp)
                        .graphicsLayer {
                            shadowElevation = (6.dp + 2.dp * nudgeGlowPulse).toPx()
                            shape = RoundedCornerShape(containerCorner)
                            ambientShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                            spotShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                            clip = false
                        }
                        .drawBehind {
                            drawIntoCanvas { canvas ->
                                val glowPaint = android.graphics.Paint(
                                    android.graphics.Paint.ANTI_ALIAS_FLAG,
                                ).apply {
                                    color = assistantGlowColor.toArgb()
                                    alpha = ((0.34f + nudgeGlowPulse * 0.10f) * 255).toInt()
                                    maskFilter = BlurMaskFilter(
                                        11.dp.toPx(),
                                        BlurMaskFilter.Blur.NORMAL,
                                    )
                                }
                                canvas.nativeCanvas.drawRoundRect(
                                    0f,
                                    0f,
                                    size.width,
                                    size.height,
                                    containerCorner.toPx(),
                                    containerCorner.toPx(),
                                    glowPaint,
                                )
                            }
                        }
                        .semantics {
                            contentDescription = thinkingMessage
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .background(nudgeBrush)
                            .padding(
                                horizontal = if (verticalLayout) 10.dp else 14.dp,
                                vertical = if (verticalLayout) 6.dp else 8.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VoiceNudgeSparkles(
                            color = MaterialTheme.colorScheme.primary,
                            morph = 1f,
                            modifier = Modifier.size(if (verticalLayout) 26.dp else 30.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        AnimatedContent(
                            targetState = thinkingMessage,
                            transitionSpec = {
                                fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                            },
                            label = "nowNudgeThinkingMessage",
                        ) { message ->
                            Text(
                                text = message,
                                style = thinkingTextStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                VoiceAssistantSurface.Nudge -> Surface(
                    onClick = onNudgeAction,
                    shape = RoundedCornerShape(containerCorner),
                    color = containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = containerElevation,
                    modifier = Modifier
                        .wrapContentSize(
                            align = if (verticalLayout) {
                                Alignment.CenterStart
                            } else {
                                Alignment.BottomEnd
                            },
                            unbounded = true,
                        )
                        .offset(
                            x = if (verticalLayout) buttonSize + 8.dp else 0.dp,
                            y = if (verticalLayout) 0.dp else -buttonSize - 8.dp,
                        )
                        .sharedBounds(
                            sharedContentState = voiceContainerState,
                            animatedVisibilityScope = this,
                            boundsTransform = { _, _ ->
                                tween(
                                    durationMillis = NUDGE_MORPH_DURATION_MILLIS,
                                    easing = FastOutSlowInEasing,
                                )
                            },
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        )
                        .width(
                            if (verticalLayout) {
                                300.dp
                            } else {
                                minOf(
                                    360.dp,
                                    (LocalConfiguration.current.screenWidthDp - 24).dp,
                                )
                            },
                        )
                        .heightIn(min = if (verticalLayout) 36.dp else 44.dp)
                        .graphicsLayer {
                            shadowElevation = (6.dp + 2.dp * nudgeGlowPulse).toPx()
                            shape = RoundedCornerShape(containerCorner)
                            ambientShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                            spotShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                            clip = false
                        }
                        .drawBehind {
                        drawIntoCanvas { canvas ->
                            val glowPaint = android.graphics.Paint(
                                android.graphics.Paint.ANTI_ALIAS_FLAG,
                            ).apply {
                                color = assistantGlowColor.toArgb()
                                alpha = ((0.34f + nudgeGlowPulse * 0.10f) * 255).toInt()
                                maskFilter = BlurMaskFilter(
                                    11.dp.toPx(),
                                    BlurMaskFilter.Blur.NORMAL,
                                )
                            }
                            canvas.nativeCanvas.drawRoundRect(
                                0f,
                                0f,
                                size.width,
                                size.height,
                                containerCorner.toPx(),
                                containerCorner.toPx(),
                                glowPaint,
                            )
                        }
                        }
                        .semantics {
                            contentDescription = listOfNotNull(
                                renderedNudge?.label,
                                renderedNudge?.supportingText,
                                renderedNudge?.sourceLabel,
                            ).joinToString(separator = ". ")
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateEnterExit(
                                enter = fadeIn(
                                    tween(durationMillis = 150, delayMillis = 190),
                                ),
                                exit = fadeOut(tween(durationMillis = 80)),
                            )
                            .clip(RoundedCornerShape(containerCorner))
                            .background(nudgeBrush)
                            .padding(
                                horizontal = if (verticalLayout) 14.dp else 18.dp,
                                vertical = if (verticalLayout) 12.dp else 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(
                            if (verticalLayout) 8.dp else 12.dp,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NudgePreviewThumbnail(
                                action = renderedNudge?.action,
                                size = if (verticalLayout) 30.dp else 36.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "NOW NUDGE",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = onNudgeDismiss,
                                modifier = Modifier.size(if (verticalLayout) 28.dp else 32.dp),
                            ) {
                                FlaticonIcon(
                                    glyph = FlaticonIcons.REMOVE,
                                    contentDescription = "Dismiss suggestion",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = if (verticalLayout) 14.sp else 16.sp,
                                )
                            }
                        }
                        Text(
                            text = renderedNudge?.label.orEmpty(),
                            style = if (verticalLayout) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        renderedNudge?.supportingText?.takeIf(String::isNotBlank)?.let { body ->
                            Text(
                                text = body,
                                style = if (verticalLayout) {
                                    MaterialTheme.typography.bodySmall
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        renderedNudge?.sourceLabel?.takeIf(String::isNotBlank)?.let { sourceLabel ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    text = sourceLabel,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                VoiceAssistantSurface.Quiz -> {
                    val configuration = LocalConfiguration.current
                    val quizWidth = minOf(
                        320.dp,
                        (configuration.screenWidthDp - if (verticalLayout) 104 else 32).dp,
                    )
                    Surface(
                        shape = RoundedCornerShape(containerCorner),
                        color = containerColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = containerElevation,
                        modifier = Modifier
                            .wrapContentSize(
                                align = if (verticalLayout) {
                                    Alignment.CenterStart
                                } else {
                                    Alignment.BottomEnd
                                },
                                unbounded = true,
                            )
                            .offset(
                                x = if (verticalLayout) buttonSize + 12.dp else 0.dp,
                                y = if (verticalLayout) 0.dp else -buttonSize - 12.dp,
                            )
                            .sharedBounds(
                                sharedContentState = voiceContainerState,
                                animatedVisibilityScope = this,
                                boundsTransform = { _, _ ->
                                    tween(
                                        durationMillis = NUDGE_MORPH_DURATION_MILLIS,
                                        easing = FastOutSlowInEasing,
                                    )
                                },
                                enter = EnterTransition.None,
                                exit = ExitTransition.None,
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                            .width(quizWidth)
                            .heightIn(
                                max = if (verticalLayout) {
                                    (configuration.screenHeightDp - 32).dp
                                } else {
                                    minOf(480.dp, (configuration.screenHeightDp - 160).dp)
                                },
                            )
                            .graphicsLayer {
                                shadowElevation = (6.dp + 2.dp * nudgeGlowPulse).toPx()
                                shape = RoundedCornerShape(containerCorner)
                                ambientShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                                spotShadowColor = assistantGlowColor.copy(alpha = 0.55f)
                                clip = false
                            }
                            .drawBehind {
                                drawIntoCanvas { canvas ->
                                    val glowPaint = android.graphics.Paint(
                                        android.graphics.Paint.ANTI_ALIAS_FLAG,
                                    ).apply {
                                        color = assistantGlowColor.toArgb()
                                        alpha = ((0.34f + nudgeGlowPulse * 0.10f) * 255).toInt()
                                        maskFilter = BlurMaskFilter(
                                            11.dp.toPx(),
                                            BlurMaskFilter.Blur.NORMAL,
                                        )
                                    }
                                    canvas.nativeCanvas.drawRoundRect(
                                        0f,
                                        0f,
                                        size.width,
                                        size.height,
                                        containerCorner.toPx(),
                                        containerCorner.toPx(),
                                        glowPaint,
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(containerCorner))
                            .background(nudgeBrush)
                            .semantics {
                                contentDescription = "Islamic quiz"
                                dismiss {
                                    VoiceAssistantNudgeBus.closeQuiz()
                                    true
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier.animateEnterExit(
                                enter = fadeIn(
                                    tween(durationMillis = 150, delayMillis = 190),
                                ),
                                exit = fadeOut(tween(durationMillis = 80)),
                            ),
                        ) {
                            com.starception.submission.feature.prayertimes.components.IslamicQuizContent(
                                onDismiss = VoiceAssistantNudgeBus::closeQuiz,
                                featuredQuestion = generatedQuizQuestion,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun NudgePreviewThumbnail(
    action: DeenlyNudgeAction?,
    size: Dp = 32.dp,
) {
    val glyph = when (action) {
        DeenlyNudgeAction.MARK_PRAYED -> FlaticonIcons.CHECK
        DeenlyNudgeAction.PLAY_TRAVEL_DUA -> FlaticonIcons.TRAVEL
        DeenlyNudgeAction.PLAY_QUIZ -> FlaticonIcons.KNOWLEDGE
        DeenlyNudgeAction.OPEN_QIBLA,
        DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
        null -> FlaticonIcons.QUICK_ACTION
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        FlaticonIcon(
            glyph = glyph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = if (size < 30.dp) 16.sp else 19.sp,
        )
    }
}

@Composable
private fun VoiceNudgeSparkles(
    color: Color,
    morph: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "voiceNudgeSparkles")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "voiceNudgeSparkleOrbit",
    )
    val sizes = floatArrayOf(0.27f, 0.19f, 0.15f)

    Canvas(modifier) {
        if (morph <= 0f) return@Canvas
        val orbitRadius = size.minDimension * 0.13f
        sizes.forEachIndexed { index, sizeFraction ->
            val angle = phase + index * (Math.PI.toFloat() * 2f / sizes.size)
            val pulse = 0.72f + 0.28f * (
                kotlin.math.sin((angle * 2f).toDouble()).toFloat() + 1f
                ) / 2f
            val radius = size.minDimension * sizeFraction * pulse
            val orbitCenter = Offset(
                x = center.x + kotlin.math.cos(angle.toDouble()).toFloat() * orbitRadius,
                y = center.y + kotlin.math.sin(angle.toDouble()).toFloat() * orbitRadius,
            )
            val barCenter = Offset(
                x = size.width * when (index) {
                    0 -> 0.23f
                    1 -> 0.50f
                    else -> 0.77f
                },
                y = center.y,
            )
            val sparkleCenter = Offset(
                x = barCenter.x + (orbitCenter.x - barCenter.x) * morph,
                y = barCenter.y + (orbitCenter.y - barCenter.y) * morph,
            )
            val barHalfLength = size.minDimension * if (index == 1) 0.335f else 0.134f
            val barHalfWidth = size.minDimension * 0.031f
            val verticalRadius = barHalfLength + (radius - barHalfLength) * morph
            val horizontalRadius = barHalfWidth + (radius * 0.72f - barHalfWidth) * morph
            val innerRadiusX = barHalfWidth + (radius * 0.20f - barHalfWidth) * morph
            val barInnerRadiusY = (barHalfLength - barHalfWidth).coerceAtLeast(0f)
            val innerRadiusY = barInnerRadiusY +
                (verticalRadius * 0.20f - barInnerRadiusY) * morph
            val sparkle = Path().apply {
                moveTo(sparkleCenter.x, sparkleCenter.y - verticalRadius)
                lineTo(sparkleCenter.x + innerRadiusX, sparkleCenter.y - innerRadiusY)
                lineTo(sparkleCenter.x + horizontalRadius, sparkleCenter.y)
                lineTo(sparkleCenter.x + innerRadiusX, sparkleCenter.y + innerRadiusY)
                lineTo(sparkleCenter.x, sparkleCenter.y + verticalRadius)
                lineTo(sparkleCenter.x - innerRadiusX, sparkleCenter.y + innerRadiusY)
                lineTo(sparkleCenter.x - horizontalRadius, sparkleCenter.y)
                lineTo(sparkleCenter.x - innerRadiusX, sparkleCenter.y - innerRadiusY)
                close()
            }
            drawPath(path = sparkle, color = color)
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
        // Connectivity banner: shown in the pull-to-sync strip on every page,
        // including Home (the always-present app-level container carries it, so
        // Home's own inner container does not need to know about it).
        val isOffline by appState.isOffline.collectAsStateWithLifecycle()
        val notConnectedMessage = stringResource(R.string.not_connected)
        val isDownloadingRaw = if (mainViewModel != null) {
            val d by mainViewModel.isContentDownloading.collectAsStateWithLifecycle()
            d
        } else {
            false
        }
        val rawDownloadProgress = if (mainViewModel != null) {
            val p by mainViewModel.contentDownloadProgress.collectAsStateWithLifecycle()
            p
        } else {
            0f
        }
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
        } else {
            false
        }
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
        val appLevelIslamicEventState = if (!isOnHome) {
            rawIslamicEventState
        } else {
            com.starception.submission.feature.prayertimes.wobble.IslamicEventState()
        }
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
            isOffline = if (isOnHome) false else isOffline,
            offlineText = notConnectedMessage,
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
                onJumpToPage = { page ->
                    com.starception.submission.feature.surah.MushafMiniBarBus.onJumpToPage?.invoke(page)
                },
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
                            WindowInsets.safeDrawing.only(WindowInsetsSides.End),
                        )
                    } else {
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                        )
                    },
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
