/*
 * Copyright 2026 Starception
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
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.widget.WidgetAppearanceSettings
import com.starception.submission.widget.WidgetBackgroundType
import com.starception.submission.widget.WidgetColorMode
import com.starception.submission.widget.effectiveBackgroundAlpha
import com.starception.submission.widget.timeAwareBasicGradientColors
import java.util.Calendar
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

@Composable
private fun WidgetAppearancePreview(
    settings: WidgetAppearanceSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val systemDark = when (context.getSystemService(UiModeManager::class.java)?.nightMode) {
        UiModeManager.MODE_NIGHT_YES -> true
        UiModeManager.MODE_NIGHT_NO -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
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
            colors = timeAwareBasicGradientColors(
                darkTheme = dark,
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            ),
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
    val contentColor = palette.onSurface
    val secondaryContentColor = contentColor.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = palette.primary.copy(alpha = 0.18f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.PRAYER_TIMES,
                        contentDescription = null,
                        tint = palette.primary,
                        fontSize = 24.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Next prayer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = "Asr · in 1 hr 24 min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor,
                )
            }
            Text(
                text = "4:32",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
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
