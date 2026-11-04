package com.starception.submission.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Compose drawing keeps the same hue/saturation wheel available without Android bitmaps. */
@Composable
internal fun SharedAccentColorDialog(primary: Color, secondary: Color, tertiary: Color,
    onConfirm: (Color, Color, Color) -> Unit, onDismiss: () -> Unit) {
    var colors by remember { mutableStateOf(listOf(primary, secondary, tertiary)) }
    var selected by remember { mutableIntStateOf(0) }
    val initial = remember(selected) { colorHsv(colors[selected]) }
    var hue by remember(selected) { mutableFloatStateOf(initial[0]) }
    var saturation by remember(selected) { mutableFloatStateOf(initial[1]) }
    var brightness by remember(selected) { mutableFloatStateOf(initial[2]) }
    fun updateColor() { colors = colors.toMutableList().also { it[selected] = Color.hsv(hue, saturation, brightness) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Custom colors") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Primary", "Secondary", "Tertiary").forEachIndexed { index, name ->
                    FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(name) })
                }
            }
            Canvas(Modifier.size(220.dp).pointerInput(selected) {
                fun pick(position: Offset) {
                    val delta = position - Offset(size.width / 2f, size.height / 2f)
                    hue = ((atan2(delta.y, delta.x) * 180f / PI.toFloat()) + 360f) % 360f
                    saturation = (delta.getDistance() / (size.width / 2f)).coerceIn(0f, 1f)
                    updateColor()
                }
                detectDragGestures(onDragStart = { pick(it) }) { change, _ -> change.consume(); pick(change.position) }
            }.pointerInput(selected) {
                detectTapGestures { position ->
                    val delta = position - Offset(size.width / 2f, size.height / 2f)
                    hue = ((atan2(delta.y, delta.x) * 180f / PI.toFloat()) + 360f) % 360f
                    saturation = (delta.getDistance() / (size.width / 2f)).coerceIn(0f, 1f)
                    updateColor()
                }
            }) {
                drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent)))
                val angle = hue * PI.toFloat() / 180f
                val point = center + Offset(cos(angle), sin(angle)) * (size.minDimension / 2f * saturation)
                drawCircle(colors[selected], radius = 9.dp.toPx(), center = point)
                drawCircle(Color.White, radius = 10.dp.toPx(), center = point, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = .3f), radius = 12.dp.toPx(), center = point, style = Stroke(1.dp.toPx()))
            }
            Text("Brightness", style = MaterialTheme.typography.labelMedium)
            Slider(value = brightness, onValueChange = { brightness = it; updateColor() })
            Surface(color = colors[selected], shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(32.dp)) { }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(colors[0], colors[1], colors[2]) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun colorHsv(color: Color): FloatArray {
    val high = maxOf(color.red, color.green, color.blue)
    val low = minOf(color.red, color.green, color.blue)
    val delta = high - low
    val hue = when {
        delta == 0f -> 0f
        high == color.red -> 60f * (((color.green - color.blue) / delta) % 6f)
        high == color.green -> 60f * ((color.blue - color.red) / delta + 2f)
        else -> 60f * ((color.red - color.green) / delta + 4f)
    }
    return floatArrayOf((hue + 360f) % 360f, if (high == 0f) 0f else delta / high, high)
}
