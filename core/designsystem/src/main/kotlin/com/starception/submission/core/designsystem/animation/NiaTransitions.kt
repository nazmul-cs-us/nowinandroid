/*
 * Copyright 2025 Starception
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

package com.starception.submission.core.designsystem.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import com.starception.submission.core.designsystem.animation.NiaMotion.Duration

/**
 * Canonical enter/exit transition recipes, ported from material-components-android's
 * transition package (MaterialFadeThrough, MaterialSharedAxis, MaterialFade) so every
 * dialog, popup, tab switch, and in-screen reveal shares the same motion language.
 */
object NiaTransitions {

    /** Fraction of a fade-through spent fading OUT before the new content fades in. */
    private const val FADE_THROUGH_THRESHOLD = 0.35f

    /** Incoming content of a fade-through/shared-axis starts at this scale. */
    private const val INCOMING_SCALE = 0.92f

    /** Popup/dialog content (MaterialFade) starts at this scale. */
    private const val FADE_SCALE = 0.8f

    /**
     * Fade through — for swapping content that has no spatial relationship
     * (tab switches, toggling between empty/loaded states). Outgoing fades in the
     * first 35% of the window, incoming fades + grows in over the rest.
     */
    fun fadeThroughEnter(durationMillis: Int = Duration.MEDIUM_2): EnterTransition {
        val fadeOutMs = (durationMillis * FADE_THROUGH_THRESHOLD).toInt()
        return fadeIn(
            animationSpec = tween(
                durationMillis = durationMillis - fadeOutMs,
                delayMillis = fadeOutMs,
                easing = NiaMotion.StandardDecelerate,
            ),
        ) + scaleIn(
            initialScale = INCOMING_SCALE,
            animationSpec = tween(
                durationMillis = durationMillis - fadeOutMs,
                delayMillis = fadeOutMs,
                easing = NiaMotion.StandardDecelerate,
            ),
        )
    }

    fun fadeThroughExit(durationMillis: Int = Duration.MEDIUM_2): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = (durationMillis * FADE_THROUGH_THRESHOLD).toInt(),
                easing = NiaMotion.StandardAccelerate,
            ),
        )

    /**
     * Shared axis X — for navigating between peers/levels with a spatial relationship.
     * [forward] true when moving deeper (content comes from the right).
     * [slideDistancePx] defaults to a subtle 30dp-ish push; pass e.g. width for full slides.
     */
    fun sharedAxisXEnter(
        forward: Boolean,
        slideDistancePx: (fullWidth: Int) -> Int = { it / 12 }, // ~30dp on a 360dp screen
        durationMillis: Int = Duration.LONG_1,
    ): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { if (forward) slideDistancePx(it) else -slideDistancePx(it) },
            animationSpec = tween(durationMillis, easing = NiaMotion.Emphasized),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = (durationMillis * (1f - FADE_THROUGH_THRESHOLD)).toInt(),
                delayMillis = (durationMillis * FADE_THROUGH_THRESHOLD).toInt(),
                easing = NiaMotion.StandardDecelerate,
            ),
        )

    fun sharedAxisXExit(
        forward: Boolean,
        slideDistancePx: (fullWidth: Int) -> Int = { it / 12 },
        durationMillis: Int = Duration.LONG_1,
    ): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { if (forward) -slideDistancePx(it) else slideDistancePx(it) },
            animationSpec = tween(durationMillis, easing = NiaMotion.Emphasized),
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = (durationMillis * FADE_THROUGH_THRESHOLD).toInt(),
                easing = NiaMotion.StandardAccelerate,
            ),
        )

    /**
     * Material fade — for popups, dialogs, menus, FABs and other transient surfaces.
     * Enters quickly with a gentle grow; exits even quicker with a plain fade.
     */
    fun popupEnter(durationMillis: Int = Duration.MEDIUM_4): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = (durationMillis * 0.3f).toInt(),
                easing = NiaMotion.EmphasizedDecelerate,
            ),
        ) + scaleIn(
            initialScale = FADE_SCALE,
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedDecelerate),
        )

    fun popupExit(durationMillis: Int = Duration.SHORT_3): ExitTransition =
        fadeOut(animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedAccelerate))

    /**
     * Bottom-anchored surface (sheets, mini-bars, snack-like cards) sliding up into view.
     */
    fun slideUpEnter(durationMillis: Int = Duration.MEDIUM_4): EnterTransition =
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedDecelerate),
        ) + fadeIn(
            animationSpec = tween((durationMillis * 0.5f).toInt(), easing = NiaMotion.StandardDecelerate),
        )

    fun slideDownExit(durationMillis: Int = Duration.SHORT_4): ExitTransition =
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedAccelerate),
        ) + fadeOut(
            animationSpec = tween(durationMillis, easing = NiaMotion.StandardAccelerate),
        )

    /**
     * Detail push — full-width slide used when navigating INTO a detail screen
     * (Surah/Hadith/Dua/Topic/Course detail). The outgoing screen recedes a fifth of
     * the way with a partial fade, so the pair reads as one connected move.
     */
    fun detailEnter(durationMillis: Int = Duration.MEDIUM_4): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedDecelerate),
        ) + fadeIn(
            animationSpec = tween(durationMillis / 2, easing = NiaMotion.StandardDecelerate),
        )

    fun detailExit(durationMillis: Int = Duration.MEDIUM_4): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { -it / 5 },
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedDecelerate),
        ) + fadeOut(
            animationSpec = tween(durationMillis / 2, easing = NiaMotion.StandardAccelerate),
        )

    fun detailPopEnter(durationMillis: Int = Duration.MEDIUM_4): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { -it / 5 },
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedDecelerate),
        ) + fadeIn(
            animationSpec = tween(durationMillis / 2, easing = NiaMotion.StandardDecelerate),
        )

    fun detailPopExit(durationMillis: Int = Duration.MEDIUM_3): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedAccelerate),
        ) + fadeOut(
            animationSpec = tween(durationMillis / 2, easing = NiaMotion.StandardAccelerate),
        )

    /** Scale + fade pair for FABs and small floating controls. */
    fun fabEnter(durationMillis: Int = Duration.MEDIUM_2): EnterTransition =
        scaleIn(
            initialScale = FADE_SCALE,
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedDecelerate),
        ) + fadeIn(
            animationSpec = tween((durationMillis * 0.5f).toInt(), easing = NiaMotion.StandardDecelerate),
        )

    fun fabExit(durationMillis: Int = Duration.SHORT_3): ExitTransition =
        scaleOut(
            targetScale = FADE_SCALE,
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedAccelerate),
        ) + fadeOut(
            animationSpec = tween(durationMillis, easing = NiaMotion.EmphasizedAccelerate),
        )
}
