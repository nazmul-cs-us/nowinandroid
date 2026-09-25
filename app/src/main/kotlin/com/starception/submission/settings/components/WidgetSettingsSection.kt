/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.settings.components

import android.app.UiModeManager
import android.appwidget.AppWidgetProviderInfo
import android.content.res.Configuration
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.starception.submission.R
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.widget.WidgetAppearanceSettings
import com.starception.submission.widget.WidgetBackgroundType
import com.starception.submission.widget.WidgetColorMode
import com.starception.submission.widget.WidgetPinning
import com.starception.submission.widget.WidgetPreviewRegistrar
import com.starception.submission.widget.WidgetPreviewSource
import com.starception.submission.widget.basicPlateColors
import com.starception.submission.widget.effectiveArtworkAlpha
import com.starception.submission.widget.effectiveBackgroundAlpha
import com.starception.submission.widget.needsWallpaperContrast
import com.starception.submission.widget.previewRemoteViews
import com.starception.submission.widget.widgetPreviewSpec
import kotlin.math.roundToInt

/** Global appearance controls shared by every widget supplied by the app. */
@Composable
fun WidgetSettingsSection(
    settings: WidgetAppearanceSettings,
    onSettingsChanged: (WidgetAppearanceSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingOpacity by remember(settings.backgroundOpacity) {
        mutableFloatStateOf(settings.backgroundOpacity)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "These choices apply to every home-screen widget from Starception.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        WidgetPinningGallery()

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = "Widget appearance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        WidgetAppearancePreview(
            settings = settings.copy(backgroundOpacity = pendingOpacity),
        )

        SettingSwitchRow(
            title = "Background",
            description = "Show a rounded surface behind widget content",
            checked = settings.showBackground,
            onCheckedChange = { enabled ->
                onSettingsChanged(settings.copy(showBackground = enabled))
            },
        )

        WidgetSettingGroup(title = "Background type") {
            WidgetChoiceRow(
                title = "Basic",
                description = "Use each widget's original visual style",
                selected = settings.backgroundType == WidgetBackgroundType.BASIC,
                enabled = settings.showBackground,
                onClick = {
                    onSettingsChanged(
                        settings.copy(backgroundType = WidgetBackgroundType.BASIC),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WidgetChoiceRow(
                title = "Dynamic color",
                description = "Use colors generated from the phone wallpaper",
                selected = settings.backgroundType == WidgetBackgroundType.DYNAMIC_COLOR,
                enabled = settings.showBackground,
                onClick = {
                    onSettingsChanged(
                        settings.copy(backgroundType = WidgetBackgroundType.DYNAMIC_COLOR),
                    )
                },
            )
        }

        Column(
            modifier = Modifier.alpha(if (settings.showBackground) 1f else 0.5f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Opacity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Samsung-style glass · 100% solid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${(pendingOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Slider(
                value = pendingOpacity,
                onValueChange = { pendingOpacity = it },
                onValueChangeFinished = {
                    onSettingsChanged(settings.copy(backgroundOpacity = pendingOpacity))
                },
                valueRange = 0f..1f,
                steps = 19,
                enabled = settings.showBackground,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        WidgetSettingGroup(title = "Colors") {
            WidgetChoiceRow(
                title = "Match phone setting",
                description = "Switch automatically with the phone's light or dark mode",
                selected = settings.colorMode == WidgetColorMode.FOLLOW_SYSTEM,
                onClick = {
                    onSettingsChanged(settings.copy(colorMode = WidgetColorMode.FOLLOW_SYSTEM))
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WidgetChoiceRow(
                title = "Light",
                selected = settings.colorMode == WidgetColorMode.LIGHT,
                onClick = {
                    onSettingsChanged(settings.copy(colorMode = WidgetColorMode.LIGHT))
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WidgetChoiceRow(
                title = "Dark",
                selected = settings.colorMode == WidgetColorMode.DARK,
                onClick = {
                    onSettingsChanged(settings.copy(colorMode = WidgetColorMode.DARK))
                },
            )
        }
    }
}

/** In-app counterpart to the launcher's widget picker, based on the platform sample. */
@Composable
private fun WidgetPinningGallery() {
    val context = LocalContext.current
    val providers = remember(context) { WidgetPinning.installedProviders(context) }
    val pinningSupported = remember(context) { WidgetPinning.isPinningSupported(context) }

    // Android 15+ can receive a real RemoteViews preview from the app. Publish the same Glance
    // compositions used below so the launcher picker and this gallery cannot drift apart.
    LaunchedEffect(providers) {
        WidgetPreviewRegistrar.register(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.widget_add_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.widget_add_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!pinningSupported) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = stringResource(R.string.widget_pin_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = providers,
                    key = { it.provider.flattenToString() },
                ) { provider ->
                    PinnableWidgetCard(
                        provider = provider,
                        onAdd = {
                            if (!WidgetPinning.requestPin(context, provider)) {
                                Toast.makeText(
                                    context,
                                    R.string.widget_pin_unavailable,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnableWidgetCard(
    provider: AppWidgetProviderInfo,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(provider, context) {
        provider.loadLabel(context.packageManager).toString()
    }
    val description = remember(provider, context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            provider.loadDescription(context)?.toString().orEmpty()
        } else {
            ""
        }
    }

    Card(
        modifier = Modifier
            .width(304.dp)
            .height(372.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                if (provider.previewImage != 0) {
                    WidgetProviderPreview(
                        provider = provider,
                        contentDescription = description.ifBlank { label },
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.QUICK_ACTION,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 32.sp,
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.widget_add_button))
            }
        }
    }
}

/**
 * Renders the actual Glance widget at its drop footprint. The packaged image is only a loading
 * and compatibility fallback; the same live RemoteViews is also published to Android 15+.
 */
@Composable
private fun WidgetProviderPreview(
    provider: AppWidgetProviderInfo,
    contentDescription: String,
) {
    val context = LocalContext.current
    val spec = remember(provider.provider.className) {
        widgetPreviewSpec(provider.provider.className)
    }
    val livePreview by produceState<android.widget.RemoteViews?>(
        initialValue = null,
        key1 = spec,
    ) {
        value = spec
            ?.takeUnless { it.source == WidgetPreviewSource.PACKAGED_IMAGE }
            ?.let {
                runCatching { it.previewRemoteViews(context, slot = 1) }.getOrNull()
            }
    }
    val painter = painterResource(provider.previewImage)
    val previewAspect = remember(provider, painter, spec) {
        val intrinsicSize = painter.intrinsicSize
        if (
            spec?.source == WidgetPreviewSource.PACKAGED_IMAGE &&
            intrinsicSize.width.isFinite() &&
            intrinsicSize.height.isFinite() &&
            intrinsicSize.height > 0f
        ) {
            intrinsicSize.width / intrinsicSize.height
        } else if (spec != null) {
            spec.size.width.value / spec.size.height.value
        } else if (provider.minWidth > 0 && provider.minHeight > 0) {
            provider.minWidth.toFloat() / provider.minHeight
        } else {
            if (
                intrinsicSize.width.isFinite() &&
                intrinsicSize.height.isFinite() &&
                intrinsicSize.height > 0f
            ) {
                intrinsicSize.width / intrinsicSize.height
            } else {
                1f
            }
        }
    }.coerceIn(0.55f, 3.5f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val availableAspect = maxWidth.value / maxHeight.value
        val previewModifier = if (previewAspect >= availableAspect) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(previewAspect)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(previewAspect)
        }
        val clippedModifier = previewModifier.clip(RoundedCornerShape(12.dp))
        val remoteViews = livePreview
        if (spec?.source == WidgetPreviewSource.PACKAGED_IMAGE || remoteViews == null) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.FillBounds,
                modifier = clippedModifier,
            )
        } else {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { parent ->
                    parent.removeAllViews()
                    runCatching { remoteViews.apply(parent.context, parent) }
                        .getOrNull()
                        ?.let { rendered ->
                            rendered.layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            parent.addView(rendered)
                        }
                },
                modifier = clippedModifier,
            )
        }
    }
}

@Composable
private fun WidgetAppearancePreview(
    settings: WidgetAppearanceSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val systemDark = when (context.getSystemService(UiModeManager::class.java)?.nightMode) {
        UiModeManager.MODE_NIGHT_YES -> true
        UiModeManager.MODE_NIGHT_NO -> false
        else ->
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }
    val dark = when (settings.colorMode) {
        WidgetColorMode.FOLLOW_SYSTEM -> systemDark
        WidgetColorMode.LIGHT -> false
        WidgetColorMode.DARK -> true
    }
    val palette = if (
        settings.backgroundType == WidgetBackgroundType.DYNAMIC_COLOR &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    val backgroundBrush = when (settings.backgroundType) {
        WidgetBackgroundType.BASIC -> Brush.horizontalGradient(
            colors = basicPlateColors(context = context, darkTheme = dark),
        )
        WidgetBackgroundType.DYNAMIC_COLOR -> {
            val launcherAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Color(
                    context.getColor(
                        if (dark) {
                            android.R.color.system_accent3_700
                        } else {
                            android.R.color.system_accent3_100
                        },
                    ),
                )
            } else {
                palette.tertiaryContainer
            }
            val launcherNeutral = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Color(
                    context.getColor(
                        if (dark) {
                            android.R.color.system_neutral2_800
                        } else {
                            android.R.color.system_neutral2_50
                        },
                    ),
                )
            } else {
                palette.surfaceContainerHigh
            }
            Brush.linearGradient(colors = listOf(launcherAccent, launcherNeutral))
        }
    }
    val wallpaperSafeContent = settings.needsWallpaperContrast()
    val contentColor = if (wallpaperSafeContent) Color.White else palette.onSurface
    val secondaryContentColor = if (wallpaperSafeContent) {
        Color(0xFFE2E7F1)
    } else {
        contentColor.copy(alpha = 0.72f)
    }

    // A flat surface behind the plate would make every opacity look identical. Stand in
    // for the home-screen wallpaper with something the sheet can visibly let through.
    val wallpaperStandIn = Brush.linearGradient(
        colors = listOf(Color(0xFF8E7B86), Color(0xFFB9A4A0), Color(0xFF5B5D74)),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(wallpaperStandIn),
    ) {
        if (settings.showBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = settings.effectiveBackgroundAlpha()
                    }
                    .background(backgroundBrush),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(29.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(50),
                    color = palette.primary.copy(alpha = 0.16f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.ic_flaticon_location_marker),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dubai",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                    )
                    Text(
                        text = "Thu, 17 Sep · 6 Rabi' al-Thani",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryContentColor,
                        maxLines = 1,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = "☾  34°  Clear sky",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(50),
                    color = palette.surface.copy(alpha = 0.72f),
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.REFRESH,
                        contentDescription = null,
                        tint = contentColor,
                        fontSize = 12.sp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.prayer_widget_reference_hero_v4),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(settings.effectiveArtworkAlpha(minimumEnabledAlpha = 0.86f)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(152.dp)
                        .padding(start = 13.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = "Best Time to Pray Isha",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFF4F1E6),
                        maxLines = 1,
                    )
                    Text(
                        text = "35m since Isha",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF4F1E6),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Fajr",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF4F1E6),
                        maxLines = 1,
                    )
                    Text(
                        text = "in 8h 34m",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD8C9A8),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun WidgetSettingGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun WidgetChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
