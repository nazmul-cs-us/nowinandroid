package com.starception.submission.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

/**
 * Modal bottom-sheet palette editor for the CUSTOM theme. Built around the
 * production-grade [HsvColorPicker] from `skydoves/colorpicker-compose` plus a
 * brightness rail, with a role selector for Primary/Secondary/Tertiary and a
 * row of named-theme reference palettes you can use as a starting point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HsvColorWheelDialog(
    initialPrimary: Color,
    initialSecondary: Color,
    initialTertiary: Color,
    onConfirm: (primary: Color, secondary: Color, tertiary: Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var primary by remember { mutableStateOf(initialPrimary) }
    var secondary by remember { mutableStateOf(initialSecondary) }
    var tertiary by remember { mutableStateOf(initialTertiary) }
    var activeRole by remember { mutableIntStateOf(0) }

    val activeColor = when (activeRole) {
        0 -> primary
        1 -> secondary
        else -> tertiary
    }

    val controller = rememberColorPickerController()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Theme palette",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

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

            // Library HSV picker
            HsvColorPicker(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                controller = controller,
                initialColor = activeColor,
                onColorChanged = { envelope: ColorEnvelope ->
                    if (envelope.fromUser) {
                        when (activeRole) {
                            0 -> primary = envelope.color
                            1 -> secondary = envelope.color
                            else -> tertiary = envelope.color
                        }
                    }
                },
            )

            // Brightness rail
            BrightnessSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                controller = controller,
            )

            // Hex value + active swatch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(activeColor)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp),
                        ),
                )
                Text(
                    text = "#%06X".format(activeColor.toArgb() and 0xFFFFFF),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Palette preview strip
            PalettePreview(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                activeIndex = activeRole,
            )

            // Reference palettes
            Text(
                text = "Start from a theme",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReferencePaletteRow(
                onPalettePicked = { (p, s, t) ->
                    primary = p
                    secondary = s
                    tertiary = t
                },
            )

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = { onConfirm(primary, secondary, tertiary) }) {
                    Text("Save")
                }
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
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .border(1.dp, outlineColor, shape),
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
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.9f)),
                    )
                }
            }
        }
    }
}

private data class NamedPalette(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private val ReferencePalettes = listOf(
    NamedPalette("Default", Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260)),
    NamedPalette("Android", Color(0xFF006E2C), Color(0xFF526350), Color(0xFF39656B)),
    NamedPalette("Coastal", Color(0xFF006874), Color(0xFF4A6267), Color(0xFF525E7C)),
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
                        .height(22.dp)
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

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
