/*
 * Native procedural celestial renderer adapted from celestial-native.
 * Copyright (c) 2026, MIT License.
 */
package com.starception.submission.feature.prayertimes.celestial

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.cos

@Composable
internal actual fun PlatformCelestialBody(
    body: CelestialBody,
    lunarCyclePhase: Float,
    playing: Boolean,
    modifier: Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShaderCelestialBody(
            body = body,
            lunarCyclePhase = lunarCyclePhase,
            playing = playing,
            modifier = modifier,
        )
    } else {
        CanvasCelestialBody(
            body = body,
            lunarCyclePhase = lunarCyclePhase,
            modifier = modifier,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RuntimeShaderCelestialBody(
    body: CelestialBody,
    lunarCyclePhase: Float,
    playing: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val shaderSource = remember(context) {
        context.assets.open("celestial/celestial.agsl").bufferedReader().use { it.readText() }
    }
    val shader = remember(shaderSource) {
        runCatching { RuntimeShader(shaderSource) }.getOrNull()
    }
    if (shader == null) {
        CanvasCelestialBody(body, lunarCyclePhase, modifier)
        return
    }
    val paint = remember(shader) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    }
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        val startedAtMillis = SystemClock.elapsedRealtime()
        do {
            elapsedSeconds = (SystemClock.elapsedRealtime() - startedAtMillis) / 1_000f
            if (!playing) break
            delay(FRAME_DELAY_MILLIS)
        } while (true)
    }

    Canvas(modifier = modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", elapsedSeconds)
        shader.setFloatUniform("phase", lunarCyclePhase.coerceIn(0f, 1f))
        shader.setFloatUniform("mode", if (body == CelestialBody.Sun) 1f else 2f)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

@Composable
private fun CanvasCelestialBody(
    body: CelestialBody,
    lunarCyclePhase: Float,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.35f
        val center = Offset(size.width / 2f, size.height / 2f)
        if (body == CelestialBody.Sun) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFFFF1A8),
                    0.58f to Color(0xFFFFB52E),
                    0.84f to Color(0xFFE85D10),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius * 1.3f,
                ),
                radius = radius * 1.3f,
                center = center,
            )
        } else {
            drawCircle(Color(0xFFE1E2DE), radius, center)
            val phaseCosine = cos(lunarCyclePhase * Math.PI * 2.0).toFloat()
            drawCircle(
                color = Color(0xFF151A24).copy(alpha = 0.94f),
                radius = radius,
                center = center.copy(x = center.x + phaseCosine * radius * 1.75f),
            )
        }
    }
}

private const val FRAME_DELAY_MILLIS = 125L
