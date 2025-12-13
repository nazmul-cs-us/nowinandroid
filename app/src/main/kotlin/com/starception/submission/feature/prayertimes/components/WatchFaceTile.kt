package com.starception.submission.feature.prayertimes.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.starception.submission.core.designsystem.theme.ubuntuInspiredFontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * Watch face tile for the Control Center
 * Features a dual-ring rotating clock with hours in the center
 */

// Style configurations
data class WatchDialStyle(
    val stepsWidth: Dp = 1.dp,
    val majorStepsWidth: Dp = 1.5.dp,
    val stepsColor: Color = Color.White,
    val majorStepsColor: Color = Color.White,
    val accentColor: Color = Color.Red,
    val normalStepsLineHeight: Dp = 4.dp,
    val fiveStepsLineHeight: Dp = 10.dp,
    val stepsTextStyle: TextStyle = TextStyle(),
    val stepsLabelTopPadding: Dp = 10.dp,
    val showMinorTicks: Boolean = true,
    val showAccentMarks: Boolean = false,
)

data class WatchClockStyle(
    val secondsDialStyle: WatchDialStyle = WatchDialStyle(),
    val minutesDialStyle: WatchDialStyle = WatchDialStyle(),
    val hoursDialStyle: WatchDialStyle = WatchDialStyle(),
    val hourLabelStyle: TextStyle = TextStyle(),
    val overlayStrokeWidth: Dp = 1.5.dp,
    val overlayStrokeColor: Color = Color.White
)

// Default style for the Control Center (white on glass) - Helix/Timex inspired professional styling
val ControlCenterClockStyle = WatchClockStyle(
    secondsDialStyle = WatchDialStyle(
        stepsTextStyle = TextStyle(
            fontFamily = ubuntuInspiredFontFamily,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        ),
        stepsWidth = 1.dp,
        majorStepsWidth = 1.8.dp,
        stepsColor = Color.White.copy(alpha = 0.6f),
        majorStepsColor = Color.White,
        accentColor = Color(0xFFE53935),
        stepsLabelTopPadding = 6.dp,
        normalStepsLineHeight = 5.dp,
        fiveStepsLineHeight = 12.dp,
        showMinorTicks = true,
        showAccentMarks = true
    ),
    minutesDialStyle = WatchDialStyle(
        stepsTextStyle = TextStyle(
            fontFamily = ubuntuInspiredFontFamily,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal
        ),
        stepsWidth = 0.8.dp,
        majorStepsWidth = 1.5.dp,
        stepsColor = Color.White.copy(alpha = 0.5f),
        majorStepsColor = Color.White.copy(alpha = 0.9f),
        accentColor = Color(0xFFE53935),
        stepsLabelTopPadding = 5.dp,
        normalStepsLineHeight = 4.dp,
        fiveStepsLineHeight = 10.dp,
        showMinorTicks = true,
        showAccentMarks = true
    ),
    hoursDialStyle = WatchDialStyle(
        stepsTextStyle = TextStyle(
            fontFamily = ubuntuInspiredFontFamily,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        ),
        stepsWidth = 1.dp,
        majorStepsWidth = 1.8.dp,
        stepsColor = Color.White.copy(alpha = 0.6f),
        majorStepsColor = Color.White,
        accentColor = Color(0xFFE53935),
        stepsLabelTopPadding = 6.dp,
        normalStepsLineHeight = 5.dp,
        fiveStepsLineHeight = 12.dp,
        showMinorTicks = true,
        showAccentMarks = false
    ),
    hourLabelStyle = TextStyle(
        fontFamily = ubuntuInspiredFontFamily,
        color = Color.White,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    overlayStrokeColor = Color.White.copy(alpha = 0.6f),
    overlayStrokeWidth = 1.dp
)

@Composable
fun WatchFaceTile(
    modifier: Modifier = Modifier,
    clockStyle: WatchClockStyle = ControlCenterClockStyle
) {
    val textMeasurer = rememberTextMeasurer()

    var minuteRotation by remember { mutableFloatStateOf(0f) }
    var secondRotation by remember { mutableFloatStateOf(0f) }
    var hour by remember { mutableIntStateOf(0) }
    var minute by remember { mutableIntStateOf(0) }

    // Initialize with current time
    LaunchedEffect(key1 = true) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentSecond = calendar.get(Calendar.SECOND)
        secondRotation = -(currentSecond) * 6f
        minuteRotation = -(currentMinute) * 6f
        hour = currentHour
        minute = currentMinute
    }

    // Smooth second rotation (60fps)
    LaunchedEffect(key1 = true) {
        while (true) {
            delay(16)
            secondRotation -= 0.096f
        }
    }

    // Minute rotation (updates every second)
    LaunchedEffect(key1 = true) {
        while (true) {
            delay(1000)
            minuteRotation -= 0.1f
            // Update minute display
            val calendar = Calendar.getInstance()
            minute = calendar.get(Calendar.MINUTE)
        }
    }

    // Hour update (every minute)
    LaunchedEffect(key1 = true) {
        while (true) {
            delay(60 * 1000L)
            val calendar = Calendar.getInstance()
            hour = calendar.get(Calendar.HOUR_OF_DAY)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val outerRadius = minOf(size.width, size.height) / 2f - 4.dp.toPx()
            val innerRadius = outerRadius - 28.dp.toPx()

            // Seconds Dial (outer)
            watchDial(
                radius = outerRadius,
                rotation = secondRotation,
                textMeasurer = textMeasurer,
                dialStyle = clockStyle.secondsDialStyle,
                showLabels = true
            )

            // Minutes Dial (inner)
            watchDial(
                radius = innerRadius,
                rotation = minuteRotation,
                textMeasurer = textMeasurer,
                dialStyle = clockStyle.minutesDialStyle,
                showLabels = false // Hide labels on inner ring for cleaner look
            )

            // Draw hour:minute in center
            val timeString = String.format("%02d:%02d", hour, minute)
            val timeTextMeasureOutput = textMeasurer.measure(
                text = buildAnnotatedString { append(timeString) },
                style = clockStyle.hourLabelStyle
            )
            val timeTopLeft = Offset(
                x = center.x - (timeTextMeasureOutput.size.width / 2),
                y = center.y - (timeTextMeasureOutput.size.height / 2),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = timeString,
                topLeft = timeTopLeft,
                style = clockStyle.hourLabelStyle
            )

            // Draw overlay path connecting the dials
            val overlayPath = Path().apply {
                val startOffset = Offset(
                    x = center.x + (outerRadius * cos(8f * Math.PI / 180f)).toFloat(),
                    y = center.y - (outerRadius * sin(8f * Math.PI / 180f)).toFloat(),
                )
                val endOffset = Offset(
                    x = center.x + (outerRadius * cos(-8f * Math.PI / 180f)).toFloat(),
                    y = center.y - (outerRadius * sin(-8f * Math.PI / 180f)).toFloat(),
                )
                val overlayRadius = (endOffset.y - startOffset.y) / 2f

                val overlayLineX = center.x + innerRadius - 10.dp.toPx()

                moveTo(x = startOffset.x, y = startOffset.y)
                lineTo(x = overlayLineX, y = startOffset.y)
                cubicTo(
                    x1 = overlayLineX - overlayRadius,
                    y1 = startOffset.y,
                    x2 = overlayLineX - overlayRadius,
                    y2 = endOffset.y,
                    x3 = overlayLineX,
                    y3 = endOffset.y
                )
                lineTo(endOffset.x, endOffset.y)
            }

            drawPath(
                path = overlayPath,
                color = clockStyle.overlayStrokeColor,
                style = Stroke(width = clockStyle.overlayStrokeWidth.toPx())
            )
        }
    }
}

/**
 * Draw a circular dial with tick marks and optional labels
 */
private fun DrawScope.watchDial(
    radius: Float,
    rotation: Float,
    textMeasurer: TextMeasurer,
    dialStyle: WatchDialStyle,
    showLabels: Boolean = true
) {
    var stepsAngle = 0

    repeat(60) { steps ->
        val stepsHeight = if (steps % 5 == 0) {
            dialStyle.fiveStepsLineHeight.toPx()
        } else {
            dialStyle.normalStepsLineHeight.toPx()
        }

        val stepsStartOffset = Offset(
            x = center.x + (radius * cos((stepsAngle + rotation) * (Math.PI / 180f))).toFloat(),
            y = center.y - (radius * sin((stepsAngle + rotation) * (Math.PI / 180))).toFloat()
        )
        val stepsEndOffset = Offset(
            x = center.x + (radius - stepsHeight) * cos((stepsAngle + rotation) * (Math.PI / 180)).toFloat(),
            y = center.y - (radius - stepsHeight) * sin((stepsAngle + rotation) * (Math.PI / 180)).toFloat()
        )

        drawLine(
            color = dialStyle.stepsColor,
            start = stepsStartOffset,
            end = stepsEndOffset,
            strokeWidth = dialStyle.stepsWidth.toPx(),
            cap = StrokeCap.Round
        )

        // Draw labels at every 5 steps
        if (showLabels && steps % 5 == 0) {
            val stepsLabel = String.format("%02d", steps)
            val stepsLabelTextLayout = textMeasurer.measure(
                text = buildAnnotatedString { append(stepsLabel) },
                style = dialStyle.stepsTextStyle
            )

            val stepsLabelOffset = Offset(
                x = center.x + (radius - stepsHeight - dialStyle.stepsLabelTopPadding.toPx()) * cos((stepsAngle + rotation) * (Math.PI / 180)).toFloat(),
                y = center.y - (radius - stepsHeight - dialStyle.stepsLabelTopPadding.toPx()) * sin((stepsAngle + rotation) * (Math.PI / 180)).toFloat()
            )

            val stepsLabelTopLeft = Offset(
                stepsLabelOffset.x - ((stepsLabelTextLayout.size.width) / 2f),
                stepsLabelOffset.y - (stepsLabelTextLayout.size.height / 2f)
            )

            drawText(
                textMeasurer = textMeasurer,
                text = stepsLabel,
                topLeft = stepsLabelTopLeft,
                style = dialStyle.stepsTextStyle
            )
        }
        stepsAngle += 6
    }
}
