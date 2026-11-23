package com.starception.submission.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI

/** Shared Home/reader disclosure: reversible stretch → neck → split, not a rectangular drawer. */
@Composable
fun FluidReveal(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    containerColor: Color,
    outlineColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = 0.dp,
    headerVisibleWhenCollapsed: Boolean = false,
    cornerRadius: Dp = 24.dp,
    separation: Dp = 28.dp,
    bringContentIntoView: Boolean = true,
    header: @Composable () -> Unit,
    content: @Composable (enabled: Boolean, collapse: () -> Unit) -> Unit,
) {
    val transition = updateTransition(expanded, label = "fluidReveal")
    val progress by transition.animateFloat(
        transitionSpec = { tween(760, easing = LinearEasing) },
        label = "fluidSeparation",
    ) { if (it) 1f else 0f }
    val bringIntoView = remember { BringIntoViewRequester() }
    var headerHeight by remember { mutableIntStateOf(0) }
    var bodyHeight by remember { mutableIntStateOf(0) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val clearancePx = with(LocalDensity.current) { bottomClearance.toPx() }
    val trailingEdgeHeightPx = with(LocalDensity.current) { 160.dp.toPx() }
    val actionable = expanded && progress >= .99f
    LaunchedEffect(transition.currentState, transition.targetState, measuredSize, clearancePx, bringContentIntoView) {
        if (bringContentIntoView && transition.currentState && transition.targetState) {
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
            .onSizeChanged { measuredSize = it }
            .bringIntoViewRequester(bringIntoView)
            .clipToBounds()
            .drawBehind {
                val header = headerHeight * if (headerVisibleWhenCollapsed) 1f else fluidPhase(progress, 0f, .42f)
                if (header <= 0f || (!headerVisibleWhenCollapsed && progress <= 0f)) return@drawBehind
                val radius = cornerRadius.toPx()
                val gap = if (progress > 0f) fluidGap(progress) * separation.toPx() else 0f
                val lowerTop = header + gap
                val lowerHeight = bodyHeight * fluidPhase(progress, .18f, .76f)
                // The first material appears at the arrow on the divider, then
                // spreads sideways and down into the full-width panel.
                val halfWidth = if (headerVisibleWhenCollapsed) size.width / 2f else
                    20.dp.toPx() + (size.width / 2f - 20.dp.toPx()) * fluidPhase(progress, 0f, .38f)
                var outline = Path().apply {
                    addRoundRect(RoundRect(
                        Rect(size.width / 2f - halfWidth, 0f, size.width / 2f + halfWidth, header),
                        CornerRadius(min(radius, min(halfWidth, header / 2f))),
                    ))
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
                drawPath(outline, containerColor)
                drawPath(outline, outlineColor, style = Stroke(1.dp.toPx()))
            },
        content = {
            Box(
                Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }
                    .graphicsLayer { alpha = if (headerVisibleWhenCollapsed) 1f else fluidPhase(progress, 0f, .3f) }
                    .then(if (headerVisibleWhenCollapsed || expanded || progress > 0f) Modifier else Modifier.clearAndSetSemantics { }),
            ) {
                if (headerVisibleWhenCollapsed || expanded || progress > 0f) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) { header() }
                }
            }
            Box(
                Modifier.fillMaxWidth().onSizeChanged { bodyHeight = it.height }
                    .graphicsLayer { alpha = fluidPhase(progress, .18f, .66f) }
                    .drawWithContent {
                        val visibleHeight = size.height * fluidPhase(progress, .18f, .76f)
                        if (visibleHeight > 0f) {
                            val outline = Path().apply {
                                addRoundRect(RoundRect(
                                    Rect(0f, 0f, size.width, visibleHeight),
                                    CornerRadius(min(cornerRadius.toPx(), visibleHeight / 2f)),
                                ))
                            }
                            clipPath(outline) { this@drawWithContent.drawContent() }
                        }
                    }
                    .then(if (actionable) Modifier else Modifier.clearAndSetSemantics { })
                    .padding(16.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    content(actionable) { onExpandedChange(false) }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val childConstraints = Constraints(minWidth = width, maxWidth = width)
        val header = measurables[0].measure(childConstraints)
        val body = measurables[1].measure(childConstraints)
        val gap = if (progress > 0f) fluidGap(progress) * separation.toPx() else 0f
        val visibleHeader = header.height * if (headerVisibleWhenCollapsed) 1f else fluidPhase(progress, 0f, .42f)
        val bodyTop = visibleHeader + gap
        val height = if (progress > 0f) {
            max(visibleHeader, bodyTop + body.height * fluidPhase(progress, .18f, .76f)) +
                8.dp.toPx() * fluidPhase(progress, 0f, .5f)
        } else visibleHeader
        layout(width, constraints.constrainHeight(height.roundToInt())) {
            header.placeRelative(0, 0)
            body.placeRelative(0, bodyTop.roundToInt())
        }
    }
}

private fun fluidPhase(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

/** Overlap first so the surface stretches before a real gap opens between the cards. */
private fun fluidGap(progress: Float): Float = -0.8f + 1.8f * fluidPhase(progress, .18f, 1f)
