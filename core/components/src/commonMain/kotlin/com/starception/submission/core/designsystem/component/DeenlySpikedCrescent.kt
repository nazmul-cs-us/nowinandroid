package com.starception.submission.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val crescentReveal = CubicBezierEasing(.42f, 0f, .58f, 1f)

/** The rays ARE the mark: each stroke ends at the crescent's inner and outer curves. */
@Composable
fun DeenlySpikedCrescent(progress: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = progress().coerceIn(0f, 1f)
        val radius = size.minDimension * .44f
        val cutoutRadius = radius * .88f
        val cutoutShift = Offset(radius * .43f, -radius * .12f)
        val violet = Color(0xFFB78ADD)
        val highlight = Color(0xFFF1D7AD)
        // A fixed, restrained light field, with no orbiting sparks or surrounding pattern.
        val glow = crescentReveal.transform((p / .5f).coerceIn(0f, 1f))
        drawCircle(Brush.radialGradient(listOf(violet.copy(alpha = .09f * glow), Color.Transparent),
            center = center, radius = size.minDimension * .5f), radius = size.minDimension * .5f)
        repeat(112) { index ->
            val angle = -PI / 2 + index * 2 * PI / 112
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            // Intersect this ray with the offset inner circle. The remaining segment
            // is exactly the visible crescent, leaving its opening completely empty.
            val projection = direction.x * cutoutShift.x + direction.y * cutoutShift.y
            val discriminant = cutoutRadius * cutoutRadius -
                (cutoutShift.x * cutoutShift.x + cutoutShift.y * cutoutShift.y - projection * projection)
            val inner = (projection + sqrt(discriminant.coerceAtLeast(0f))).coerceAtLeast(0f)
            if (inner >= radius) return@repeat
            val delay = index / 112f * .10f
            val reveal = crescentReveal.transform(((p - .04f - delay) / .48f).coerceIn(0f, 1f))
            if (reveal <= 0f) return@repeat
            val start = center + direction * inner
            val end = center + direction * (inner + (radius - inner) * reveal)
            drawLine(
                brush = Brush.linearGradient(listOf(violet.copy(alpha = .64f * reveal),
                    highlight.copy(alpha = .94f * reveal)), start, end),
                start = start, end = end,
                strokeWidth = (.7f + .35f * (index % 3) / 2f).dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
