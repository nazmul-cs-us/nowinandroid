package com.starception.submission.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** A single spark writes the brand, using the splash's existing 0–1 timeline. */
@Composable
fun DeenlySparkWordmark(progress: () -> Float, modifier: Modifier = Modifier) {
    val strokes = remember {
        deenlyLetterStrokes().map { path -> PathMeasure().apply { setPath(path, false) } }
    }
    val revealedPath = remember { Path() }
    Canvas(modifier.semantics { contentDescription = "Deenly" }) {
        val p = progress().coerceIn(0f, 1f)
        val writing = ((p - .20f) / .62f).coerceIn(0f, 1f) * strokes.size
        val settling = ((p - .82f) / .18f).coerceIn(0f, 1f)
        val wordmarkScale = minOf(size.width / 160f, size.height / 54f)
        if (wordmarkScale <= 0f) return@Canvas
        withTransform({
            translate((size.width - 160f * wordmarkScale) / 2f, (size.height - 54f * wordmarkScale) / 2f)
            scale(wordmarkScale, wordmarkScale, pivot = Offset.Zero)
        }) {
            strokes.forEachIndexed { index, stroke ->
                val reveal = (writing - index).coerceIn(0f, 1f)
                if (reveal <= 0f) return@forEachIndexed
                val distance = stroke.length * reveal
                revealedPath.reset()
                stroke.getSegment(0f, distance, revealedPath, startWithMoveTo = true)
                drawPath(revealedPath, Color(0xFFBD91E2).copy(alpha = .22f * (1f - settling)),
                    style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(revealedPath, Color(0xFFF7F3F9),
                    style = Stroke(1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (reveal < 1f) {
                    val head = stroke.getPosition(distance)
                    drawCircle(Color(0xFFC799EA).copy(alpha = .20f), radius = 6f, center = head)
                    drawCircle(Color(0xFFF1DEFF), radius = 1.8f, center = head)
                    repeat(3) { tail ->
                        val behind = distance - (tail + 1) * 4f
                        if (behind > 0f) drawCircle(
                            Color(0xFFE0CFF3).copy(alpha = .45f / (tail + 1)),
                            radius = .7f, center = stroke.getPosition(behind))
                    }
                }
            }
        }
    }
}

// Centerline paths, rather than outlined font glyphs, let a spark genuinely
// write each letter once. The same geometry renders on Android and iOS.
private fun deenlyLetterStrokes(): List<Path> = listOf(
    Path().apply {
        moveTo(5f, 36f); lineTo(5f, 8f); lineTo(13f, 8f)
        cubicTo(35f, 8f, 35f, 36f, 13f, 36f); lineTo(5f, 36f)
    },
    Path().apply {
        moveTo(37f, 26f); lineTo(54f, 26f)
        cubicTo(54f, 14f, 36f, 14f, 36f, 27f)
        cubicTo(36f, 39f, 51f, 39f, 55f, 32f)
    },
    Path().apply {
        moveTo(65f, 26f); lineTo(82f, 26f)
        cubicTo(82f, 14f, 64f, 14f, 64f, 27f)
        cubicTo(64f, 39f, 79f, 39f, 83f, 32f)
    },
    Path().apply {
        moveTo(92f, 36f); lineTo(92f, 19f); lineTo(92f, 25f)
        cubicTo(97f, 16f, 110f, 16f, 110f, 27f); lineTo(110f, 36f)
    },
    Path().apply {
        moveTo(121f, 6f); lineTo(121f, 32f)
        cubicTo(121f, 35f, 122f, 36f, 125f, 36f)
    },
    Path().apply { moveTo(134f, 19f); lineTo(142f, 35f) },
    Path().apply {
        moveTo(150f, 19f); lineTo(140f, 42f)
        cubicTo(137f, 49f, 132f, 48f, 130f, 45f)
    },
)
