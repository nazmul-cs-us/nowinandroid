package com.starception.submission.feature.prayertimes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.starception.submission.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI

/** A reversible stretch → neck → split, rather than a rectangular expanding drawer. */
@Composable
internal fun FluidHomeReveal(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = 0.dp,
    content: @Composable (enabled: Boolean, collapse: () -> Unit) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val transition = updateTransition(expanded, label = "homeOptions")
    val progress by transition.animateFloat(
        transitionSpec = { tween(760, easing = LinearEasing) },
        label = "fluidSeparation",
    ) { if (it) 1f else 0f }
    val bringIntoView = remember { BringIntoViewRequester() }
    val haptics = LocalHapticFeedback.current
    // Match the floating navigation even in light mode; a pale fill looked like
    // a disconnected white card over Home's artwork.
    val panelColor = Color(0xFF1B1E2A).copy(alpha = .97f)
    val panelOutline = Color(0xFF323B52)
    val panelContent = Color(0xFFE3E7F2)
    var headerHeight by remember { mutableIntStateOf(0) }
    var bodyHeight by remember { mutableIntStateOf(0) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val clearancePx = with(LocalDensity.current) { bottomClearance.toPx() }
    val trailingEdgeHeightPx = with(LocalDensity.current) { 160.dp.toPx() }
    val actionable = expanded && progress >= .99f
    val stateLabel = stringResource(if (expanded) R.string.home_options_expanded else R.string.home_options_collapsed)
    val toggleLabel = stringResource(if (expanded) R.string.home_options_collapse else R.string.home_options_expand)
    val toggle = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        expanded = !expanded
    }
    BackHandler(enabled = expanded) { expanded = false }
    LaunchedEffect(transition.currentState, transition.targetState, measuredSize, clearancePx) {
        if (transition.currentState && transition.targetState) {
            // The home navigation floats over the scroll viewport. Include its
            // occluded area in the request so the last row clears it after expansion.
            // A gallery can be taller than the viewport. Request its trailing
            // edge, not an impossible full-card rectangle that leaves it occluded.
            bringIntoView.bringIntoView(Rect(
                0f, (measuredSize.height - min(bodyHeight.toFloat(), trailingEdgeHeightPx)).coerceAtLeast(0f),
                measuredSize.width.toFloat(), measuredSize.height + clearancePx,
            ))
        }
    }

    Layout(
        modifier = modifier.fillMaxWidth()
            .testTag("homeExploreReveal")
            .onSizeChanged { measuredSize = it }
            .bringIntoViewRequester(bringIntoView)
            .clipToBounds()
            .drawBehind {
                val header = headerHeight.toFloat()
                if (header <= 0f) return@drawBehind
                val radius = 24.dp.toPx()
                val gap = fluidGap(progress) * 28.dp.toPx()
                val lowerTop = header + gap
                val lowerHeight = bodyHeight * fluidPhase(progress, 0f, .76f)
                var outline = Path().apply {
                    addRoundRect(RoundRect(Rect(0f, 0f, size.width, header), CornerRadius(radius)))
                }
                if (lowerHeight > .5f) {
                    val lowerRadius = min(radius, lowerHeight / 2f)
                    val lower = Path().apply {
                        addRoundRect(RoundRect(
                            Rect(0f, lowerTop, size.width, lowerTop + lowerHeight),
                            CornerRadius(lowerRadius),
                        ))
                    }
                    outline = Path.combine(PathOperation.Union, outline, lower)
                    if (progress < .94f) {
                        val bridge = Path().apply {
                            // Stretch the material first. The side pinches and interior
                            // perforations then open together, like the reference card.
                            val pinch = fluidPhase(progress, .36f, .94f)
                            val waist = size.width * .5f * pinch
                            val top = header - radius
                            val bottom = lowerTop + lowerRadius
                            val middle = (top + bottom) / 2f
                            moveTo(0f, top)
                            cubicTo(0f, middle, waist, middle - gap / 3f, waist, middle)
                            cubicTo(waist, middle + gap / 3f, 0f, middle, 0f, bottom)
                            lineTo(size.width, bottom)
                            cubicTo(size.width, middle, size.width - waist, middle + gap / 3f, size.width - waist, middle)
                            cubicTo(size.width - waist, middle - gap / 3f, size.width, middle, size.width, top)
                            close()
                        }
                        val holes = Path().apply {
                            val opening = fluidPhase(progress, .28f, .90f)
                            if (opening > 0f) {
                                // The holes briefly bite into both lobes, rather than
                                // becoming straight slits clipped at the card edges.
                                val bulge = sin(PI.toFloat() * opening) * radius * .75f
                                val halfHeight = (max(0f, gap) / 2f + bulge) * opening
                                val halfWidth = size.width * .24f * opening
                                val middle = header + gap / 2f
                                for (position in listOf(.22f, .50f, .78f)) {
                                    val x = size.width * position
                                    addRoundRect(RoundRect(
                                        Rect(x - halfWidth, middle - halfHeight, x + halfWidth, middle + halfHeight),
                                        CornerRadius(min(halfHeight, 9.dp.toPx())),
                                    ))
                                }
                            }
                        }
                        // Union before subtracting so each perforation has a continuous
                        // rounded contour through the stretched surface.
                        outline = Path.combine(
                            PathOperation.Difference,
                            Path.combine(PathOperation.Union, outline, bridge), holes,
                        )
                    }
                }
                drawPath(outline, panelColor)
                drawPath(outline, panelOutline, style = Stroke(1.dp.toPx()))
            },
        content = {
            Column(
                Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }
                    .clickable(role = Role.Button, onClick = toggle)
                    .semantics { stateDescription = stateLabel }
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = HomeTypography.section,
                    color = panelContent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = HomeTypography.body,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = panelContent.copy(alpha = .78f),
                )
            }
            Box(
                Modifier.fillMaxWidth().onSizeChanged { bodyHeight = it.height }
                    .graphicsLayer { alpha = fluidPhase(progress, .18f, .66f) }
                    .drawWithContent {
                        val visibleHeight = size.height * fluidPhase(progress, 0f, .76f)
                        if (visibleHeight > 0f) {
                            val outline = Path().apply {
                                addRoundRect(RoundRect(
                                    Rect(0f, 0f, size.width, visibleHeight),
                                    CornerRadius(min(24.dp.toPx(), visibleHeight / 2f)),
                                ))
                            }
                            clipPath(outline) { this@drawWithContent.drawContent() }
                        }
                    }
                    .then(if (actionable) Modifier else Modifier.clearAndSetSemantics { })
                    .padding(16.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides panelContent) {
                    content(actionable) { expanded = false }
                }
            }
            IconButton(
                onClick = toggle,
                modifier = Modifier.size(48.dp)
                    .semantics { stateDescription = stateLabel },
            ) {
                Box(
                    Modifier.size(40.dp).shadow(2.dp, RoundedCornerShape(14.dp))
                        .background(panelColor, RoundedCornerShape(14.dp))
                        .border(1.dp, panelOutline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = toggleLabel,
                        tint = panelContent,
                        modifier = Modifier.size(23.dp).graphicsLayer { rotationZ = 180f * progress },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val childConstraints = Constraints(minWidth = width, maxWidth = width)
        val header = measurables[0].measure(childConstraints)
        val body = measurables[1].measure(childConstraints)
        val toggleSize = 48.dp.roundToPx()
        val button = measurables[2].measure(Constraints.fixed(toggleSize, toggleSize))
        val gap = fluidGap(progress) * 28.dp.toPx()
        val bodyTop = header.height + gap
        val buttonTop = header.height - toggleSize / 2f + max(0f, gap) / 2f
        val height = max(bodyTop + body.height * fluidPhase(progress, 0f, .76f), buttonTop + toggleSize) + 8.dp.toPx()
        layout(width, constraints.constrainHeight(height.roundToInt())) {
            header.placeRelative(0, 0)
            body.placeRelative(0, bodyTop.roundToInt())
            button.placeRelative((width - toggleSize) / 2, buttonTop.roundToInt())
        }
    }
}

private fun fluidPhase(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

/** Overlap first so the surface stretches before a real gap opens between the cards. */
private fun fluidGap(progress: Float): Float = -0.8f + 1.8f * fluidPhase(progress, .18f, 1f)
