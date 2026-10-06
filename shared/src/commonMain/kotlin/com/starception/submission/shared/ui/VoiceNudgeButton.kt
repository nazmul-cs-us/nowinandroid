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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.core.model.deenly.DeenlyNudge
import kotlinx.coroutines.delay
import kotlin.math.PI

/** Voice search while idle, a sparkle while generating, and then a tappable suggestion. */
@Composable
internal fun VoiceNudgeButton(
    onVoiceTap: () -> Unit,
    nudge: DeenlyNudge?,
    onNudgeAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var revealedNudgeId by remember { mutableStateOf<String?>(null) }
    var sparklingNudgeId by remember { mutableStateOf<String?>(null) }
    var requestedNudgeId by remember { mutableStateOf<String?>(null) }
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
        delay(5_600)
        revealedNudgeId = nudgeId
        sparklingNudgeId = null
    }
    val canPresentNudge = nudge != null && requestedNudgeId == nudge.id
    val isPreparing = canPresentNudge && revealedNudgeId != nudge?.id
    val isGenerating = isPreparing
    val showSuggestion = canPresentNudge && revealedNudgeId == nudge?.id
    val haptic = LocalHapticFeedback.current
    val nudgePullThreshold = with(LocalDensity.current) { 40.dp.toPx() }
    val nudgePullHapticStep = with(LocalDensity.current) { 12.dp.toPx() }
    var nudgePullDistance by remember(nudge?.id) { mutableStateOf(0f) }
    val suggestionBrush = Brush.horizontalGradient(
        listOf(Color(0xFFEDEAFF), Color(0xFFE8F7FF), Color(0xFFE6FBF2)),
    )

    Box(
        modifier = modifier.size(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = when {
                showSuggestion && onNudgeAction != null -> onNudgeAction
                isPreparing -> ({})
                else -> onVoiceTap
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(nudge?.id, requestedNudgeId) {
                    val nudgeId = nudge?.id
                    if (nudgeId == null || requestedNudgeId == nudgeId) {
                        return@pointerInput
                    }

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
                                requestedNudgeId = nudgeId
                            }
                            nudgePullDistance = 0f
                        },
                    )
                }
                .semantics {
                    role = Role.Button
                    contentDescription = when {
                        showSuggestion -> nudge?.label.orEmpty()
                        isPreparing -> "Generating suggestion"
                        nudge != null -> "Start voice search. Swipe down for a Now Nudge suggestion"
                        else -> "Start voice search"
                    }
                }
                .graphicsLayer {
                    translationY = nudgePullDistance * 0.24f
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SharedVoiceNudgeBars(
                    generating = isGenerating,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = showSuggestion,
            enter = fadeIn(tween(180, delayMillis = 35)) +
                scaleIn(
                    initialScale = 0.16f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    transformOrigin = TransformOrigin(1f, 1f),
                ) + expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                clip = false,
            ),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize(align = Alignment.BottomEnd, unbounded = true)
                .offset(y = (-58).dp),
        ) {
            Surface(
                onClick = { onNudgeAction?.invoke() },
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                contentColor = Color(0xFF202124),
                shadowElevation = 3.dp,
            ) {
                Box(
                    modifier = Modifier
                        .background(suggestionBrush)
                        .widthIn(max = 240.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = nudge?.label.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedVoiceNudgeBars(
    generating: Boolean,
    modifier: Modifier = Modifier,
) {
    val morph by animateFloatAsState(
        targetValue = if (generating) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "sharedVoiceNudgeMorph",
    )
    val transition = rememberInfiniteTransition(label = "sharedVoiceNudgeBars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sharedVoiceNudgeSpin",
    )
    val barColor = MaterialTheme.colorScheme.surface
    val rest = floatArrayOf(0.40f, 0.62f, 1f, 0.62f, 0.40f)

    Canvas(
        modifier = modifier.graphicsLayer {
            val scale = 1f + 0.42f * morph
            scaleX = scale
            scaleY = scale
        },
    ) {
        val slot = size.width / rest.size
        val barWidth = slot * 0.46f
        rest.forEachIndexed { index, heightFraction ->
            val idleX = slot * index + slot / 2f
            val idleHalfLength = ((size.height * heightFraction - barWidth) / 2f)
                .coerceAtLeast(0f)
            drawLine(
                color = barColor.copy(alpha = 1f - morph),
                start = Offset(idleX, center.y - idleHalfLength),
                end = Offset(idleX, center.y + idleHalfLength),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }

        val sparkleSizes = floatArrayOf(0.27f, 0.19f, 0.15f)
        val orbitRadius = size.minDimension * 0.13f
        sparkleSizes.forEachIndexed { index, sizeFraction ->
            if (morph <= 0f) return@forEachIndexed
            val angle = phase + index * (PI.toFloat() * 2f / sparkleSizes.size)
            val pulse = 0.72f + 0.28f * (
                kotlin.math.sin((angle * 2f).toDouble()).toFloat() + 1f
                ) / 2f
            val radius = size.minDimension * sizeFraction * pulse
            val orbitCenter = Offset(
                center.x + kotlin.math.cos(angle.toDouble()).toFloat() * orbitRadius,
                center.y + kotlin.math.sin(angle.toDouble()).toFloat() * orbitRadius,
            )
            val targetBarIndex = index * 2
            val barCenter = Offset(slot * targetBarIndex + slot / 2f, center.y)
            val sparkleCenter = Offset(
                barCenter.x + (orbitCenter.x - barCenter.x) * morph,
                barCenter.y + (orbitCenter.y - barCenter.y) * morph,
            )
            val barHalfLength = size.height * rest[targetBarIndex] / 2f
            val barHalfWidth = barWidth / 2f
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
            drawPath(path = sparkle, color = barColor)
        }
    }
}
