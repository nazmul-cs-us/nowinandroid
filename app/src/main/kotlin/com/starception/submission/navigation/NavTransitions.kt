package com.starception.submission.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

private const val ENTER_DURATION = 380
private const val EXIT_DURATION = 280

// Forward navigation: new screen slides in from right, old screen slides out slightly left
fun detailEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(ENTER_DURATION, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(ENTER_DURATION / 2, easing = FastOutSlowInEasing))

fun detailExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { -it / 5 },
        animationSpec = tween(ENTER_DURATION, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(ENTER_DURATION / 2))

// Back navigation: detail slides out to right, list screen slides back in from slight left
fun detailPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { -it / 5 },
        animationSpec = tween(ENTER_DURATION, easing = LinearOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(ENTER_DURATION / 2))

fun detailPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(EXIT_DURATION, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(EXIT_DURATION / 2))
