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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * The app-wide motion system. Single source of truth for every duration, easing, and
 * spring used in transitions and animations, so motion feels like one language across
 * all screens.
 *
 * Values are the Material 3 motion tokens (md.sys.motion, tokens v16.0.2) taken from
 * material-components-android. Rule of thumb:
 *  - Something MOVES or RESIZES on screen (position, size, scale) -> spatial spring
 *    ([spatialFast]/[spatialDefault]/[spatialSlow]) or an emphasized tween.
 *  - Something changes APPEARANCE in place (color, alpha, elevation) -> effects spring
 *    ([effectsFast]/[effectsDefault]/[effectsSlow]) or a standard tween.
 *  - Fast = small components (switches, icons), Default = most components,
 *    Slow = large/full-screen surfaces.
 */
object NiaMotion {

    // ---- Durations (md.sys.motion.duration) ----
    object Duration {
        const val SHORT_1 = 50
        const val SHORT_2 = 100
        const val SHORT_3 = 150
        const val SHORT_4 = 200
        const val MEDIUM_1 = 250
        const val MEDIUM_2 = 300
        const val MEDIUM_3 = 350
        const val MEDIUM_4 = 400
        const val LONG_1 = 450
        const val LONG_2 = 500
        const val LONG_3 = 550
        const val LONG_4 = 600
        const val EXTRA_LONG_1 = 700
        const val EXTRA_LONG_2 = 800
        const val EXTRA_LONG_3 = 900
        const val EXTRA_LONG_4 = 1000
    }

    // ---- Easings (md.sys.motion.easing) ----

    /** Utility motion inside the screen: selection, small component changes. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Elements entering the screen at rest. */
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Elements leaving the screen permanently. */
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /**
     * Hero/emphasized motion: screen transitions, large surfaces. Cubic approximation of
     * the M3 emphasized path curve (same approximation compose-material3 uses).
     */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized variant for elements ENTERING the screen. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1f)

    /** Emphasized variant for elements LEAVING the screen. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.2f)

    // ---- Springs (md.sys.motion.spring, standard scheme) ----
    // Spatial springs (position/size/scale) keep a hint of bounce (damping 0.9);
    // effects springs (color/alpha) are critically damped so they never overshoot.

    /** Small components that move: icons, switches, chips. */
    fun <T> spatialFast(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 1400f, visibilityThreshold = visibilityThreshold)

    /** Most moving components: cards, tiles, FABs, list items. */
    fun <T> spatialDefault(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 700f, visibilityThreshold = visibilityThreshold)

    /** Large surfaces that move: sheets, full-screen panes. */
    fun <T> spatialSlow(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 300f, visibilityThreshold = visibilityThreshold)

    /** Small in-place changes: icon tint, toggle track color. */
    fun <T> effectsFast(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3800f, visibilityThreshold = visibilityThreshold)

    /** Most in-place changes: container color, alpha, elevation. */
    fun <T> effectsDefault(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1600f, visibilityThreshold = visibilityThreshold)

    /** Large-surface in-place changes: scrim/backdrop fades. */
    fun <T> effectsSlow(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 800f, visibilityThreshold = visibilityThreshold)

    // ---- Canonical tweens ----

    /** Standard-speed tween for in-screen utility motion. */
    fun <T> standardTween(durationMillis: Int = Duration.MEDIUM_2): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = Standard)

    /** Emphasized tween for transitions and large surfaces. */
    fun <T> emphasizedTween(durationMillis: Int = Duration.LONG_2): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = Emphasized)

    /** Enter-flavored tween (decelerate into place). */
    fun <T> enterTween(durationMillis: Int = Duration.MEDIUM_4): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedDecelerate)

    /** Exit-flavored tween (accelerate off screen). */
    fun <T> exitTween(durationMillis: Int = Duration.SHORT_4): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedAccelerate)
}
