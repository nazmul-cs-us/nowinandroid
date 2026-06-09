package com.starception.submission.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Material 3 colour picker dialog for the CUSTOM theme. The user edits three
 * accent roles (Primary, Secondary, Tertiary) via a single shared HSV pad +
 * horizontal hue strip, switching roles through a segmented selector at the
 * top. A connected three-pane swatch at the bottom shows the live palette.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HsvColorWheelDialog(
    initialPrimary: Color,
    initialSecondary: Color,
    initialTertiary: Color,
    onConfirm: (primary: Color, secondary: Color, tertiary: Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val (pH, pS, pV) = remember(initialPrimary) { colorToHsv(initialPrimary) }
    val (sH, sS, sV) = remember(initialSecondary) { colorToHsv(initialSecondary) }
    val (tH, tS, tV) = remember(initialTertiary) { colorToHsv(initialTertiary) }

    var primaryHue by remember { mutableFloatStateOf(pH) }
    var primarySat by remember { mutableFloatStateOf(pS) }
    var primaryVal by remember { mutableFloatStateOf(pV) }

    var secondaryHue by remember { mutableFloatStateOf(sH) }
    var secondarySat by remember { mutableFloatStateOf(sS) }
    var secondaryVal by remember { mutableFloatStateOf(sV) }

    var tertiaryHue by remember { mutableFloatStateOf(tH) }
    var tertiarySat by remember { mutableFloatStateOf(tS) }
    var tertiaryVal by remember { mutableFloatStateOf(tV) }

    var activeRole by remember { mutableIntStateOf(0) }

    val primaryColor = remember(primaryHue, primarySat, primaryVal) { hsvToColor(primaryHue, primarySat, primaryVal) }
    val secondaryColor = remember(secondaryHue, secondarySat, secondaryVal) { hsvToColor(secondaryHue, secondarySat, secondaryVal) }
    val tertiaryColor = remember(tertiaryHue, tertiarySat, tertiaryVal) { hsvToColor(tertiaryHue, tertiarySat, tertiaryVal) }

    val activeHue = when (activeRole) { 0 -> primaryHue; 1 -> secondaryHue; else -> tertiaryHue }
    val activeSat = when (activeRole) { 0 -> primarySat; 1 -> secondarySat; else -> tertiarySat }
    val activeVal = when (activeRole) { 0 -> primaryVal; 1 -> secondaryVal; else -> tertiaryVal }
    val activeColor = when (activeRole) { 0 -> primaryColor; 1 -> secondaryColor; else -> tertiaryColor }

    val setHue: (Float) -> Unit = { h ->
        when (activeRole) {
            0 -> primaryHue = h
            1 -> secondaryHue = h
            else -> tertiaryHue = h
        }
    }
    val setSv: (Float, Float) -> Unit = { s, v ->
        when (activeRole) {
            0 -> { primarySat = s; primaryVal = v }
            1 -> { secondarySat = s; secondaryVal = v }
            else -> { tertiarySat = s; tertiaryVal = v }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme palette", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Role selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Primary", "Secondary", "Tertiary").forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = activeRole == idx,
                            onClick = { activeRole = idx },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Saturation/Value pad
                SaturationValuePad(
                    hue = activeHue,
                    sat = activeSat,
                    value = activeVal,
                    onChange = setSv,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f),
                )

                // Hue slider
                HueSlider(
                    hue = activeHue,
                    onHueChange = setHue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                )

                // Hex code
                Text(
                    text = "#%06X".format(activeColor.toArgb() and 0xFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Connected palette preview
                PalettePreview(
                    primary = primaryColor,
                    secondary = secondaryColor,
                    tertiary = tertiaryColor,
                    activeIndex = activeRole,
                )

                // Reference palettes — copy any named theme's triplet as a starting point
                Text(
                    text = "Start from a theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReferencePaletteRow(
                    onPalettePicked = { (p, s, t) ->
                        val (ph, ps, pv) = colorToHsv(p)
                        val (sh, ss, sv) = colorToHsv(s)
                        val (th, tsv, tv) = colorToHsv(t)
                        primaryHue = ph; primarySat = ps; primaryVal = pv
                        secondaryHue = sh; secondarySat = ss; secondaryVal = sv
                        tertiaryHue = th; tertiarySat = tsv; tertiaryVal = tv
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(primaryColor, secondaryColor, tertiaryColor) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Triplets are the (primary, secondary, tertiary) accents pulled from the
 * named LIGHT-mode schemes in core/designsystem/theme/Theme.kt. These let the
 * user inspect the existing palettes and start from one.
 */
private data class NamedPalette(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private val ReferencePalettes = listOf(
    // Default — Material You purple/teal
    NamedPalette("Default", Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260)),
    // Android — green
    NamedPalette("Android", Color(0xFF006E2C), Color(0xFF526350), Color(0xFF39656B)),
    // Coastal — teal
    NamedPalette("Coastal", Color(0xFF006874), Color(0xFF4A6267), Color(0xFF525E7C)),
    // Royal — Lapis / Gold / Sage
    NamedPalette("Royal", Color(0xFF2D5DA8), Color(0xFFC9A227), Color(0xFF7B9F7E)),
)

@Composable
private fun ReferencePaletteRow(
    onPalettePicked: (Triple<Color, Color, Color>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReferencePalettes.forEach { palette ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(palette.name) {
                        detectTapGestures {
                            onPalettePicked(Triple(palette.primary, palette.secondary, palette.tertiary))
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(6.dp),
                        ),
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(palette.primary))
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(palette.secondary))
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(palette.tertiary))
                }
                Text(
                    palette.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PalettePreview(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    activeIndex: Int,
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .border(1.dp, outlineColor.copy(alpha = 0.3f), shape),
    ) {
        listOf(primary, secondary, tertiary).forEachIndexed { idx, color ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (idx == activeIndex) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.9f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SaturationValuePad(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = remember(hue) { hsvToColor(hue, 1f, 1f) }
    val shape = RoundedCornerShape(12.dp)
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, outlineColor, shape)
            .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val sx = (change.position.x / size.width).coerceIn(0f, 1f)
                    val sy = (change.position.y / size.height).coerceIn(0f, 1f)
                    onChange(sx, 1f - sy)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val sx = (offset.x / size.width).coerceIn(0f, 1f)
                    val sy = (offset.y / size.height).coerceIn(0f, 1f)
                    onChange(sx, 1f - sy)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotX = sat * size.width
            val dotY = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.035f,
                center = Offset(dotX, dotY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.012f),
            )
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                    ),
                ),
            )
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                    onHueChange(frac * 360f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onHueChange(frac * 360f)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotX = (hue / 360f) * size.width
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(dotX - size.height * 0.18f, -1f),
                size = androidx.compose.ui.geometry.Size(size.height * 0.36f, size.height + 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.18f, size.height * 0.18f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            )
        }
    }
}

private fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    val v = max
    return Triple(h, s, v)
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val hp = (h / 60f) % 6f
    val x = c * (1f - kotlin.math.abs((hp % 2f) - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(r1 + m, g1 + m, b1 + m)
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
