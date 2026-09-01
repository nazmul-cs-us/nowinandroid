package com.starception.submission.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * The Android application uses its richer native QiblaGlobeView. This fallback
 * keeps the shared Android target complete for previews and tests.
 */
@Composable
internal actual fun PlatformWorldWindGlobe(
    latitude: Double,
    longitude: Double,
    headingDegrees: Double?,
    headingAccuracyDegrees: Double?,
    qiblaBearing: Double,
    modifier: Modifier,
) {
    val ocean = Color(0xFF153F66)
    Canvas(modifier.background(ocean)) {
        val radius = min(size.width, size.height) * 0.42f
        drawCircle(ocean, radius)
        drawCircle(Color.White.copy(alpha = 0.5f), radius, style = Stroke(2f))
        listOf(-60, -30, 0, 30, 60).forEach { latitudeLine ->
            val y = center.y - radius * (latitudeLine / 90f)
            val halfWidth = radius * cos(latitudeLine * PI / 180.0).toFloat()
            drawOval(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(center.x - halfWidth, y - radius * 0.06f),
                size = androidx.compose.ui.geometry.Size(halfWidth * 2, radius * 0.12f),
                style = Stroke(1f),
            )
        }
        val user = projectOnGlobe(latitude, longitude, center, radius)
        val makkah = projectOnGlobe(21.4225, 39.8262, center, radius)
        drawLine(Color(0xFFF2C14E), user, makkah, 4f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF62A8E5), 8f, user)
        drawCircle(Color(0xFFF2C14E), 9f, makkah)
    }
}

private fun projectOnGlobe(
    latitude: Double,
    longitude: Double,
    center: Offset,
    radius: Float,
): Offset = Offset(
    x = center.x + (longitude / 180.0).toFloat() * radius,
    y = center.y - (latitude / 90.0).toFloat() * radius,
)
