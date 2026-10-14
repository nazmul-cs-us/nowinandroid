/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.MaterialTheme
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Android's VoiceWaveOverlay: while voice search listens, the search pill's
 * chrome fades out and this three-layer organic hill range rises up inside
 * it. Two receding layers give parallax depth behind the hero range; the
 * hero carries a soft vertical gradient sheen and a crisp ridgeline.
 *
 * The shared speech recognizer exposes no live audio level, so the amplitude
 * breathes on two overlapping oscillators — the visual cadence of Android's
 * wave without the microphone feed.
 */
@Composable
internal fun VoiceWave(
    appearProgress: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val breatheA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breatheA",
    )
    val breatheB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breatheB",
    )
    // Simulated speech level: quiet base with organic bursts.
    val level = 0.30f + 0.50f * (breatheA * 0.6f + breatheB * 0.4f)

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val midColor = lerp(tertiary, primary, 0.45f)

    Canvas(modifier = modifier) {
        val bounds = Rect(0f, 0f, size.width, size.height)
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

        val startX = bounds.left
        val endX = bounds.right
        val span = endX - startX
        if (span <= 0f) return@Canvas

        val twoPi = 2f * PI.toFloat()
        val baseline = bounds.bottom
        val ceiling = (baseline - bounds.top) * 0.96f

        // Concave amplitude response: a small voice lift already reads big.
        val voice = sqrt((level * 2.2f).coerceIn(0f, 1f))
        val amp = 0.28f + 0.72f * voice
        val appear = appearProgress.coerceIn(0f, 1f)

        // Ramps height from 0 at the edges up across the outer ~14% so the
        // range meets the pill ends without a vertical wall.
        fun edgeFade(u: Float): Float = (min(u, 1f - u) / 0.14f).coerceIn(0f, 1f)

        // Organic silhouette: three sine octaves drifting over time, squared
        // to deepen the valleys into distinct rounded hills.
        fun profile(u: Float, phase: Float, speed: Float): Float {
            val a = sin((u * 2.6f + t * speed + phase) * twoPi)
            val b = 0.45f * sin((u * 5.1f - t * (speed + 1f) + phase * 1.7f) * twoPi)
            val c = 0.22f * sin((u * 9.7f + t * (speed + 2f) + phase * 2.3f) * twoPi)
            val ridge = ((a + b + c) / 1.67f) * 0.5f + 0.5f
            return ridge * ridge
        }

        val samples = 140

        fun ridge(phase: Float, speed: Float, scale: Float): Pair<Path, Path> {
            val fill = Path()
            val top = Path()
            fill.moveTo(startX, baseline)
            for (s in 0..samples) {
                val u = s / samples.toFloat()
                val x = startX + u * span
                val y = baseline - profile(u, phase, speed) * edgeFade(u) * ceiling * amp * scale
                fill.lineTo(x, y)
                if (s == 0) top.moveTo(x, y) else top.lineTo(x, y)
            }
            fill.lineTo(endX, baseline)
            fill.close()
            return fill to top
        }

        // Clip to the pill (a stadium: corner radius = half its height).
        val pill = Path().apply {
            addRoundRect(
                RoundRect(
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    cornerRadius = CornerRadius(bounds.height / 2f),
                ),
            )
        }
        // Slide the whole range up from below the pill as it appears (and
        // back down on stop); the pill clip hides the offset part so it
        // reads as the wave rising out of the bar.
        clipPath(pill) {
            translate(top = (1f - appear) * bounds.height) {
                drawPath(
                    path = ridge(phase = 0.55f, speed = 1f, scale = 1f).first,
                    color = tertiary,
                    alpha = 0.26f * appear,
                )
                drawPath(
                    path = ridge(phase = 0.28f, speed = 2f, scale = 0.9f).first,
                    color = midColor,
                    alpha = 0.34f * appear,
                )
                val (frontFill, frontTop) = ridge(phase = 0f, speed = 3f, scale = 0.94f)
                drawPath(
                    path = frontFill,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.96f),
                            primary.copy(alpha = 0.72f),
                        ),
                        startY = bounds.top,
                        endY = baseline,
                    ),
                    alpha = appear,
                )
                drawPath(
                    path = frontTop,
                    color = Color.White.copy(alpha = 0.55f + 0.35f * voice),
                    style = Stroke(width = (1.4f + 1.1f * voice) * appear.coerceIn(0f, 1f)),
                    alpha = appear,
                )
            }
        }
    }
}
