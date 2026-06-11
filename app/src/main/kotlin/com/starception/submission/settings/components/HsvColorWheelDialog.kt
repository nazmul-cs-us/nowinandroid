package com.starception.submission.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.starception.submission.core.designsystem.component.NiaNavigationSuiteScaffold
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.feature.prayertimes.PrayerTimesScreen
import com.starception.submission.navigation.TopLevelDestination
import com.starception.submission.util.PreviewThemeOverride
import com.starception.submission.util.ThemeColorBridge
import kotlinx.coroutines.flow.drop

/**
 * Modal bottom-sheet palette editor for the CUSTOM theme. Built around the
 * production-grade [HsvColorPicker] from `skydoves/colorpicker-compose` plus a
 * brightness rail, with a role selector for Primary/Secondary/Tertiary, a row
 * of named-theme reference palettes, and a full-screen live preview of the
 * home page rendered with the candidate palette.
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
    var showPreview by remember { mutableStateOf(false) }

    val activeColor = when (activeRole) {
        0 -> primary
        1 -> secondary
        else -> tertiary
    }

    val controller = rememberColorPickerController()
    val thumbBitmap = rememberThumbBitmap(26.dp)

    // The library only applies initialColor once (at first layout), so the wheel
    // and brightness rail must be re-pointed whenever the edited role changes.
    // drop(1) skips the initial composition, which initialColor already handles.
    LaunchedEffect(controller) {
        snapshotFlow { activeRole }
            .drop(1)
            .collect { role ->
                val target = when (role) {
                    0 -> primary
                    1 -> secondary
                    else -> tertiary
                }
                controller.selectByColor(target, fromUser = false)
            }
    }

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
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
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
                wheelImageBitmap = thumbBitmap,
                initialColor = initialPrimary,
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

            // Brightness rail. The library slider clips its thumb at the track's
            // rounded ends (thumb center travels to x = width inside a clipped
            // canvas), so we draw our own rail with inset thumb travel. An
            // invisible library slider stays attached because the controller only
            // persists the brightness channel across wheel drags when its
            // internal isAttachedBrightnessSlider flag is set.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            ) {
                BrightnessSlider(
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f),
                    controller = controller,
                    initialColor = initialPrimary,
                )
                BrightnessRail(
                    activeColor = activeColor,
                    controller = controller,
                    modifier = Modifier.fillMaxSize(),
                )
            }

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
                    controller.selectByColor(
                        when (activeRole) {
                            0 -> p
                            1 -> s
                            else -> t
                        },
                        fromUser = false,
                    )
                },
            )

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showPreview = true }) { Text("Preview") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = { onConfirm(primary, secondary, tertiary) }) {
                    Text("Save")
                }
            }
        }
    }

    if (showPreview) {
        val darkTheme = LocalDarkTheme.current
        // The home page hosts its body inside a View-based ComposeView island
        // (AppTopSearchBar) with its own composition that can't see this
        // NiaTheme — route the candidate palette to it via the bridge.
        DisposableEffect(primary, secondary, tertiary) {
            ThemeColorBridge.previewOverride = PreviewThemeOverride(
                brand = ThemeBrand.CUSTOM,
                customSeedArgb = primary.toArgb(),
                customSecondaryArgb = secondary.toArgb(),
                customTertiaryArgb = tertiary.toArgb(),
            )
            onDispose { ThemeColorBridge.previewOverride = null }
        }
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                // Edge-to-edge so the embedded home screen handles status/nav bar
                // insets exactly like the real one.
                decorFitsSystemWindows = false,
            ),
        ) {
            NiaTheme(
                darkTheme = darkTheme,
                themeBrand = ThemeBrand.CUSTOM,
                customSeedColor = primary,
                customSecondaryColor = secondary,
                customTertiaryColor = tertiary,
            ) {
                ThemePreviewScreen(
                    onBack = { showPreview = false },
                    onApply = { onConfirm(primary, secondary, tertiary) },
                )
            }
        }
    }
}

/**
 * Full-screen preview that embeds the real home page ([PrayerTimesScreen])
 * inside the candidate theme, wrapped in the app's real bottom-navigation
 * scaffold so it matches the live home page exactly. A transparent overlay
 * swallows all touch input so the embedded screen is view-only; floating
 * controls return to the editor or apply the palette.
 */
@Composable
private fun ThemePreviewScreen(
    onBack: () -> Unit,
    onApply: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NiaNavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    item(
                        selected = destination == TopLevelDestination.HOME,
                        onClick = {},
                        icon = {
                            Icon(
                                imageVector = destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        selectedIcon = {
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.iconTextId)) },
                    )
                }
            },
        ) {
            PrayerTimesScreen()
        }

        // Swallow every touch so the embedded live screen is view-only.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
        )

        // Floating preview controls
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Button(onClick = onApply) { Text("Apply theme") }
            }
        }
    }
}

/**
 * Custom brightness slider: gradient track from black to the active hue at full
 * brightness, with a bordered thumb whose travel is inset by its radius so it
 * stays a full visible circle at both extremes.
 */
@Composable
private fun BrightnessRail(
    activeColor: Color,
    controller: com.github.skydoves.colorpicker.compose.ColorPickerController,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    fun railValue(x: Float, width: Int, thumbRadius: Float): Float =
        ((x - thumbRadius) / (width - 2f * thumbRadius)).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .pointerInput(controller) {
                detectTapGestures { offset ->
                    controller.setBrightness(
                        railValue(offset.x, size.width, size.height / 2f),
                        fromUser = true,
                    )
                }
            }
            .pointerInput(controller) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    controller.setBrightness(
                        railValue(change.position.x, size.width, size.height / 2f),
                        fromUser = true,
                    )
                }
            },
    ) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(activeColor.toArgb(), hsv)
        val pureColor = Color.hsv(hsv[0], hsv[1], 1f)
        val value = hsv[2]

        val thumbRadius = size.height / 2f
        val trackHeight = size.height * 0.62f
        val trackTop = (size.height - trackHeight) / 2f
        val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, pureColor)),
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx()),
        )

        val center = Offset(thumbRadius + value * (size.width - 2f * thumbRadius), size.height / 2f)
        drawCircle(
            color = Color.Black.copy(alpha = 0.18f),
            radius = thumbRadius * 0.92f,
            center = center + Offset(0f, 1.dp.toPx()),
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.85f,
            center = center,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = thumbRadius * 0.85f,
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

/**
 * Selector thumb for the HSV wheel: white body with a dark outline and soft
 * shadow so it stays visible over the white sheet background (the library
 * default is a plain white circle, which vanishes at the wheel rim).
 */
@Composable
private fun rememberThumbBitmap(diameter: Dp): ImageBitmap {
    val density = LocalDensity.current
    return remember(density, diameter) {
        val px = with(density) { diameter.roundToPx() }.coerceAtLeast(8)
        val bitmap = android.graphics.Bitmap.createBitmap(
            px,
            px,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val center = px / 2f
        val bodyRadius = px * 0.38f

        // Soft drop shadow
        paint.color = 0x30000000
        canvas.drawCircle(center, center + px * 0.05f, bodyRadius + px * 0.05f, paint)
        // White body
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, bodyRadius, paint)
        // Dark outline
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = px * 0.07f
        paint.color = 0x66000000
        canvas.drawCircle(center, center, bodyRadius, paint)

        bitmap.asImageBitmap()
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
