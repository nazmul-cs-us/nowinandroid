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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The iOS touch treatment for every plain `clickable` in the app: Apple's
 * controls dim with a soft highlight under the finger — never Material's
 * rectangular ripple. A gentle circular scrim fades in for the touch
 * duration and eases out on release, fitting circles, pills, cards, and
 * icon buttons of any shape.
 *
 * Installed app-wide through
 * [androidx.compose.foundation.LocalIndication]; Material components keep
 * their own (ripple disabled via `LocalRippleConfiguration`), and the
 * Cupertino adaptive widgets carry native feedback of their own.
 */
object CirclePressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        CirclePressNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = "CirclePressIndication".hashCode()
}

/** Observes presses and eases the highlight — the node-based indication. */
private class CirclePressNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    // Reading the Animatable's snapshot state inside draw() auto-invalidates.
    override val shouldAutoInvalidate: Boolean get() = true

    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val target = when (interaction) {
                    is PressInteraction.Press -> 1f
                    is PressInteraction.Release,
                    is PressInteraction.Cancel,
                    -> 0f
                    else -> return@collect
                }
                val duration = if (target > 0f) 90 else 170
                alpha.animateTo(target, tween(duration, easing = LinearEasing))
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = alpha.value
        if (a > 0.01f) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.08f * a),
                radius = min(size.width, size.height) / 2f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
}
