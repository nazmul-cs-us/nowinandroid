/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.shared.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The iOS HUD capsule's near-black glass with a white rim. */
private val CapsuleBackground = Color(0xFF2A2A2E).copy(alpha = 0.96f)
private val CapsuleRim = Color.White.copy(alpha = 0.18f)
private val FillBright = Color.White.copy(alpha = 0.97f)
private val FillMuted = Color(0xFF9E9EA3)
private val TrackRecess = Color.Black.copy(alpha = 0.35f)

/**
 * The iOS system volume HUD replica — the vertical capsule the OS pins to
 * the left edge: near-black glass with a white rim, a chunky white fill
 * rising from the bottom, and the speaker glyph at the foot. The fill
 * springs like the system control; touch works everywhere, and on devices
 * the hardware volume keys drive the same value through the capture session.
 */
@Composable
internal fun IosVolumeBar(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var lastStep by remember { mutableIntStateOf(value) }
    val targetFraction = (value / 100f).coerceIn(0f, 1f)
    // The system fill eases with a near-critical spring, never bouncing.
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 900f,
        ),
        label = "volumeFillSpring",
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = CapsuleBackground,
        shadowElevation = 12.dp,
        modifier = modifier.border(1.dp, CapsuleRim, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fraction = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        val next = (fraction * 100f).roundToInt()
                        if (next != value) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        lastStep = next
                        onValueChange(next)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val fraction = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        val next = (fraction * 100f).roundToInt()
                        if (next != lastStep) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastStep = next
                        }
                        onValueChange(next)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                drawVolumeTrack(fraction = fraction, muted = value == 0)
                drawSpeakerGlyph(muted = value == 0)
            }
        }
    }
}

/**
 * The fill: a chunky recessed track taking most of the capsule's inner
 * width, with the bright white volume rising from the bottom. The glyph
 * keeps its own space below the track.
 */
private fun DrawScope.drawVolumeTrack(fraction: Float, muted: Boolean) {
    val inset = size.width * 0.14f
    val trackWidth = size.width - inset * 2f
    val glyphHeight = size.width * 0.72f
    val glyphGap = size.width * 0.38f
    val trackBottom = size.height - glyphHeight - glyphGap
    val trackHeight = trackBottom.coerceAtLeast(1f)
    val corner = trackWidth / 2f

    // The recess the fill slides in — a touch darker than the capsule glass.
    drawRoundRect(
        color = TrackRecess,
        topLeft = Offset(inset, 0f),
        size = Size(trackWidth, trackHeight),
        cornerRadius = CornerRadius(corner, corner),
    )
    val fillHeight = trackHeight * fraction
    if (fillHeight > 0f) {
        drawRoundRect(
            color = if (muted) FillMuted else FillBright,
            topLeft = Offset(inset, trackBottom - fillHeight),
            size = Size(trackWidth, fillHeight),
            cornerRadius = CornerRadius(corner, corner),
        )
        // The system fill carries a soft specular on its top edge.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(inset, trackBottom - fillHeight),
            size = Size(trackWidth, (fillHeight * 0.10f).coerceAtMost(corner)),
            cornerRadius = CornerRadius(corner / 2f, corner / 2f),
        )
    }
}

/**
 * The speaker.wave.2 glyph at the capsule's foot: rounded cabinet, cone,
 * and two sound arcs — a diagonal slash when muted, like the system.
 */
private fun DrawScope.drawSpeakerGlyph(muted: Boolean) {
    val glyphHeight = size.width * 0.72f
    val centerY = size.height - glyphHeight / 2f - 1f
    val bodyWidth = glyphHeight * 0.26f
    val coneWidth = glyphHeight * 0.34f
    val totalWidth = bodyWidth + coneWidth
    val left = (size.width - totalWidth) / 2f - glyphHeight * 0.06f
    val bodyCorner = glyphHeight * 0.09f

    val bodyPath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = left,
                top = centerY - glyphHeight * 0.24f,
                right = left + bodyWidth,
                bottom = centerY + glyphHeight * 0.24f,
                cornerRadius = CornerRadius(bodyCorner, bodyCorner),
            ),
        )
    }
    val conePath = Path().apply {
        moveTo(left + bodyWidth, centerY - glyphHeight * 0.18f)
        // The cone flares out with rounded corners, like SF's glyph.
        cubicTo(
            left + bodyWidth + coneWidth * 0.45f,
            centerY - glyphHeight * 0.38f,
            left + totalWidth,
            centerY - glyphHeight * 0.42f,
            left + totalWidth,
            centerY - glyphHeight * 0.10f,
        )
        lineTo(left + totalWidth, centerY + glyphHeight * 0.10f)
        cubicTo(
            left + totalWidth,
            centerY + glyphHeight * 0.42f,
            left + bodyWidth + coneWidth * 0.45f,
            centerY + glyphHeight * 0.38f,
            left + bodyWidth,
            centerY + glyphHeight * 0.18f,
        )
        close()
    }
    val stroke = Stroke(width = glyphHeight * 0.11f, cap = StrokeCap.Round)
    val waveCenterX = left + totalWidth + glyphHeight * 0.16f
    drawPath(bodyPath, FillBright)
    drawPath(conePath, FillBright)
    drawArc(
        color = FillBright,
        startAngle = -44f,
        sweepAngle = 88f,
        useCenter = false,
        style = stroke,
        topLeft = Offset(waveCenterX - glyphHeight * 0.24f, centerY - glyphHeight * 0.24f),
        size = Size(glyphHeight * 0.48f, glyphHeight * 0.48f),
    )
    drawArc(
        color = FillBright,
        startAngle = -44f,
        sweepAngle = 88f,
        useCenter = false,
        style = stroke,
        topLeft = Offset(waveCenterX - glyphHeight * 0.46f, centerY - glyphHeight * 0.46f),
        size = Size(glyphHeight * 0.92f, glyphHeight * 0.92f),
    )

    if (muted) {
        drawLine(
            color = CapsuleBackground,
            start = Offset(left - glyphHeight * 0.10f, centerY + glyphHeight * 0.52f),
            end = Offset(
                left + totalWidth + glyphHeight * 1.05f,
                centerY - glyphHeight * 0.52f,
            ),
            strokeWidth = glyphHeight * 0.13f,
            cap = StrokeCap.Round,
        )
    }
}
