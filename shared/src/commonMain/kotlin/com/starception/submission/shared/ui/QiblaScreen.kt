/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.qibla.HeadingProvider
import com.starception.submission.shared.qibla.HeadingReading
import com.starception.submission.shared.qibla.cardinalDirection
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.qibla.relativeQiblaTurn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun QiblaScreen(latitude: Double, longitude: Double, onBack: () -> Unit) {
    val bearing = remember(latitude, longitude) { qiblaBearing(latitude, longitude) }
    val provider = remember { HeadingProvider() }
    var reading by remember { mutableStateOf(HeadingReading(unavailableReason = "Waiting for compass heading")) }
    DisposableEffect(provider) {
        provider.start { reading = it }
        onDispose { provider.stop() }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Column(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().align(Alignment.TopCenter)
                    .safeDrawingPadding().padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconTapTarget(
                        icon = NiaIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = onBack,
                    )
                    Text("Qibla compass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        CompassPanel(bearing, reading, Modifier.weight(1f))
                        GlobePanel(latitude, longitude, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(bottom = 20.dp),
                    ) {
                        CompassPanel(bearing, reading)
                        Spacer(Modifier.height(14.dp))
                        GlobePanel(latitude, longitude)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassPanel(bearing: Double, reading: HeadingReading, modifier: Modifier = Modifier) {
    val heading = reading.headingDegrees
    val turn = heading?.let { relativeQiblaTurn(bearing, it) }
    val aligned = turn != null && abs(turn) <= 5.0
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (aligned) "Aligned with Qibla" else "${bearing.roundToInt()}° ${cardinalDirection(bearing)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    heading == null -> reading.unavailableReason ?: "Heading unavailable"
                    aligned -> "Keep the top of the phone pointing ahead"
                    turn!! > 0 -> "Turn right ${abs(turn).roundToInt()}°"
                    else -> "Turn left ${abs(turn).roundToInt()}°"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QiblaCompass(
                bearing = bearing,
                heading = heading,
                aligned = aligned,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp),
            )
            val accuracy = reading.accuracyDegrees
            Text(
                when {
                    heading == null -> "The fixed bearing remains valid; live turn guidance needs a physical compass."
                    accuracy != null -> "Heading ${heading.roundToInt()}° · accuracy ±${accuracy.roundToInt()}°"
                    else -> "Heading ${heading.roundToInt()}° · accuracy unavailable"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QiblaCompass(
    bearing: Double,
    heading: Double?,
    aligned: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (heading == null) {
                "Qibla bearing ${bearing.roundToInt()} degrees; live heading unavailable"
            } else {
                "Qibla compass, ${abs(relativeQiblaTurn(bearing, heading)).roundToInt()} degrees from alignment"
            }
        },
    ) {
        val radius = min(size.width, size.height) / 2f
        drawCircle(surface, radius)
        drawCircle(outline.copy(alpha = 0.5f), radius, style = Stroke(2.dp.toPx()))
        repeat(36) { index ->
            val angle = index * 10.0 - (heading ?: 0.0) - 90.0
            val outer = polar(center, radius * 0.92f, angle)
            val inner = polar(center, radius * if (index % 9 == 0) 0.78f else 0.84f, angle)
            drawLine(outline, inner, outer, if (index % 9 == 0) 3.dp.toPx() else 1.dp.toPx())
        }
        val relative = bearing - (heading ?: 0.0) - 90.0
        val tip = polar(center, radius * 0.72f, relative)
        val back = polar(center, radius * 0.28f, relative + 180.0)
        drawLine(
            color = if (aligned) primary else Color(0xFFC79838),
            start = back,
            end = tip,
            strokeWidth = 10.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(primary, 9.dp.toPx(), center)
        drawCircle(Color.White, 3.dp.toPx(), center)
    }
}

@Composable
private fun GlobePanel(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val ocean = Color(0xFF153F66)
    val grid = Color.White.copy(alpha = 0.28f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Route to Makkah", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Pinch to zoom and drag the globe overview.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.45f).padding(top = 12.dp)
                    .background(ocean, RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            pan += panChange
                        }
                    }
                    .semantics {
                        contentDescription = "Interactive globe overview from your location to Makkah"
                    },
            ) {
                val radius = min(size.width, size.height) * 0.42f * zoom
                val globeCenter = center + pan
                drawCircle(ocean, radius, globeCenter)
                drawCircle(Color.White.copy(alpha = 0.5f), radius, globeCenter, style = Stroke(2.dp.toPx()))
                listOf(-60, -30, 0, 30, 60).forEach { latitudeLine ->
                    val y = globeCenter.y - radius * (latitudeLine / 90f)
                    val halfWidth = radius * cos(latitudeLine * PI / 180.0).toFloat()
                    drawOval(
                        color = grid,
                        topLeft = Offset(globeCenter.x - halfWidth, y - radius * 0.06f),
                        size = androidx.compose.ui.geometry.Size(halfWidth * 2, radius * 0.12f),
                        style = Stroke(1.dp.toPx()),
                    )
                }
                listOf(-120, -60, 0, 60, 120).forEach { longitudeLine ->
                    val x = globeCenter.x + radius * (longitudeLine / 180f)
                    drawLine(grid, Offset(x, globeCenter.y - radius), Offset(x, globeCenter.y + radius), 1.dp.toPx())
                }
                val user = project(latitude, longitude, globeCenter, radius)
                val makkah = project(21.4225, 39.8262, globeCenter, radius)
                drawLine(Color(0xFFF2C14E), user, makkah, 4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Color(0xFF62A8E5), 8.dp.toPx(), user)
                drawCircle(Color(0xFFF2C14E), 9.dp.toPx(), makkah)
            }
        }
    }
}

private fun polar(center: Offset, radius: Float, degrees: Double): Offset {
    val radians = degrees * PI / 180.0
    return Offset(
        x = center.x + cos(radians).toFloat() * radius,
        y = center.y + sin(radians).toFloat() * radius,
    )
}

private fun project(latitude: Double, longitude: Double, center: Offset, radius: Float): Offset = Offset(
    x = center.x + (longitude / 180.0).toFloat() * radius,
    y = center.y - (latitude / 90.0).toFloat() * radius,
)
