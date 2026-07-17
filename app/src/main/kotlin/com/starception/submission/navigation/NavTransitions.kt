package com.starception.submission.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.starception.submission.core.designsystem.animation.NiaTransitions

fun detailEnterTransition(): EnterTransition = NiaTransitions.detailEnter()

fun detailExitTransition(): ExitTransition = NiaTransitions.detailExit()

fun detailPopEnterTransition(): EnterTransition = NiaTransitions.detailPopEnter()

fun detailPopExitTransition(): ExitTransition = NiaTransitions.detailPopExit()
