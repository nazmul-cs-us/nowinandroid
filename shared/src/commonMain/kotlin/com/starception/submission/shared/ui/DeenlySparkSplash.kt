package com.starception.submission.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.painterResource
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.deenly_splash_mark
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DeenlySparkSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val finished = rememberUpdatedState(onFinished)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Pause when backgrounded; never launch MainActivity over another app.
        // Animatable also honours the system animator-duration scale (including zero).
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            progress.animateTo(1f, tween((1650 * (1f - progress.value)).toInt(), easing = LinearEasing))
            finished.value()
        }
    }
    PlatformBackHandler { finished.value() }
    MaterialTheme {
        SparkSplashArtwork(progress = { progress.value }, onContinue = { finished.value() })
    }
}

private data class LogoSpark(val angle: Float, val length: Float, val delay: Float, val weight: Float)

/** The mark resolves first, then fine, staggered rays grow out into a quiet constellation. */
@Composable
internal fun SparkSplashArtwork(progress: () -> Float, onContinue: () -> Unit) {
    val sparks = remember {
        val random = Random(42)
        List(72) { index ->
            LogoSpark(
                angle = (index / 72f * 2f * PI + random.nextFloat() * .045f).toFloat(),
                length = .55f + random.nextFloat() * .45f,
                delay = random.nextFloat() * .14f,
                weight = .35f + random.nextFloat() * .65f,
            )
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFF080B14), Color(0xFF11182C), Color(0xFF211A32),
        ))).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 420.dp
        val artworkSize = minOf(maxWidth - 24.dp, if (compact) 220.dp else 340.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(artworkSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val p = progress()
                    val glow = splashPhase(p, .08f, .55f)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFF9A68C8).copy(alpha = glow * .18f), Color.Transparent),
                            center = center, radius = size.minDimension * .48f,
                        ),
                        radius = size.minDimension * .48f,
                    )
                    val innerRadius = 62.dp.toPx().coerceAtMost(size.minDimension * .25f)
                    val travel = (size.minDimension * .47f - innerRadius).coerceAtLeast(0f)
                    for (spark in sparks) {
                        val reveal = splashPhase(p, .24f + spark.delay, .70f + spark.delay)
                        if (reveal <= 0f) continue
                        val direction = Offset(cos(spark.angle), sin(spark.angle))
                        val end = center + direction * (innerRadius + travel * spark.length * reveal)
                        val ink = Color(0xFFE0CFF3).copy(alpha = reveal * (.24f + .46f * spark.weight))
                        drawLine(
                            color = ink.copy(alpha = ink.alpha * .43f),
                            start = center + direction * innerRadius,
                            end = end,
                            strokeWidth = .55.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawCircle(ink, radius = (.65f + spark.weight * .65f).dp.toPx(), center = end)
                        if (spark.weight > .65f) {
                            drawCircle(
                                ink.copy(alpha = ink.alpha * .65f),
                                radius = .65.dp.toPx(),
                                center = center + direction * (innerRadius + travel * spark.length * reveal * .65f),
                            )
                        }
                    }
                }
                Box(
                    Modifier.size(if (compact) 88.dp else 104.dp)
                        .graphicsLayer {
                            val reveal = splashPhase(progress(), 0f, .32f)
                            alpha = reveal
                            scaleX = .86f + .14f * reveal
                            scaleY = scaleX
                        }
                        .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = Color(0xFF9C68BD))
                        .background(Color(0xFFF7F3F9), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // The launcher foreground includes adaptive-icon safe-zone padding.
                    Image(
                        painterResource(Res.drawable.deenly_splash_mark),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(if (compact) 154.dp else 182.dp)
                            .graphicsLayer {
                                alpha = splashPhase(progress(), .12f, .44f)
                                rotationZ = -9f * (1f - alpha)
                            },
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 0.dp else 8.dp))
            Text(
                "Deenly",
                color = Color(0xFFF7F3F9),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer { alpha = splashPhase(progress(), .20f, .48f) },
            )
        }
        TextButton(
            onClick = onContinue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            Text("Continue", color = Color(0xFFD7CBE5))
        }
    }
}

private fun splashPhase(value: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((value - start) / (end - start)).coerceIn(0f, 1f))
