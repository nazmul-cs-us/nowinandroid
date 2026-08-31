package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.settings.ThemeSettingsState

@Composable
fun AppearanceSection(
    themeSettings: ThemeSettingsState,
    onChangeThemeBrand: (ThemeBrand) -> Unit,
    onChangeDynamicColorPreference: (Boolean) -> Unit,
    onChangeDarkThemeConfig: (DarkThemeConfig) -> Unit,
    onChangeCustomColors: (primary: Int, secondary: Int, tertiary: Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    /**
     * Whether the platform offers dynamic colour. On Android this is a
     * Build.VERSION check for Material You; on iOS there is no such thing, so it
     * passes false and the option is not offered.
     */
    supportDynamicColor: Boolean = false,
    showCustomTheme: Boolean = true,
    /**
     * The custom-accent picker. Android's draws its wheel with Bitmap, Canvas and
     * Paint, which do not cross, so the dialog comes from the caller. When null
     * the custom colours option simply does not open one.
     */
    colorPickerDialog: (@Composable (
        initialPrimary: Color,
        initialSecondary: Color,
        initialTertiary: Color,
        onConfirm: (Color, Color, Color) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val showColorPickerState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val customColorOrFallback = if (themeSettings.customColor != 0) Color(themeSettings.customColor) else Color(0xFF6750A4)
    val customSecondaryOrFallback = if (themeSettings.customSecondaryColor != 0) Color(themeSettings.customSecondaryColor) else Color(0xFF625B71)
    val customTertiaryOrFallback = if (themeSettings.customTertiaryColor != 0) Color(themeSettings.customTertiaryColor) else Color(0xFF7D5260)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme Brand Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernSectionTitle(text = "Color Theme")

            // Theme pills: content-sized in a scrollable row so labels never
            // get clipped — equal weights used to trim "Default"/"Android"/
            // "Coastal"/"Custom" down to whatever fit.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemePill(
                    label = "Default",
                    selected = themeSettings.brand == ThemeBrand.DEFAULT,
                    color = Color(0xFF6750A4),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChangeThemeBrand(ThemeBrand.DEFAULT)
                    },
                )
                ThemePill(
                    label = "Android",
                    selected = themeSettings.brand == ThemeBrand.ANDROID,
                    color = Color(0xFF3DDC84),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChangeThemeBrand(ThemeBrand.ANDROID)
                    },
                )
                ThemePill(
                    label = "Coastal",
                    selected = themeSettings.brand == ThemeBrand.COASTAL,
                    color = Color(0xFF0097A7),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChangeThemeBrand(ThemeBrand.COASTAL)
                    },
                )
                ThemePill(
                    label = "Royal",
                    selected = themeSettings.brand == ThemeBrand.ROYAL,
                    color = Color(0xFF2D5DA8),  // Lapis
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChangeThemeBrand(ThemeBrand.ROYAL)
                    },
                )
                if (showCustomTheme) {
                    ThemePill(
                        label = "Custom",
                        selected = themeSettings.brand == ThemeBrand.CUSTOM,
                        color = customColorOrFallback,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showColorPickerState.value = true
                        },
                    )
                }
            }
        }

        if (showColorPickerState.value && colorPickerDialog != null) {
            colorPickerDialog(
                customColorOrFallback,
                customSecondaryOrFallback,
                customTertiaryOrFallback,
                { primary, secondary, tertiary ->
                    onChangeCustomColors(primary.toArgb(), secondary.toArgb(), tertiary.toArgb())
                    showColorPickerState.value = false
                },
                { showColorPickerState.value = false },
            )
        }

        // Dynamic Color Section (only visible when Default theme and Android 12+)
        AnimatedVisibility(
            visible = themeSettings.brand == ThemeBrand.DEFAULT && supportDynamicColor
        ) {
            ModernSwitchRow(
                title = "Dynamic Colors",
                subtitle = "Use colors from your wallpaper",
                checked = themeSettings.useDynamicColor,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChangeDynamicColorPreference(it)
                }
            )
        }

        // Dark Mode Section with modern segmented button
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernSectionTitle(text = "Display Mode")

            ModernSegmentedButtons(
                options = listOf(
                    SegmentOption(
                        label = "System",
                        iconGlyph = FlaticonIcons.SYSTEM_THEME,
                        selected = themeSettings.darkThemeConfig == DarkThemeConfig.FOLLOW_SYSTEM
                    ),
                    SegmentOption(
                        label = "Light",
                        iconGlyph = FlaticonIcons.LIGHT_THEME,
                        selected = themeSettings.darkThemeConfig == DarkThemeConfig.LIGHT
                    ),
                    SegmentOption(
                        label = "Dark",
                        iconGlyph = FlaticonIcons.DARK_THEME,
                        selected = themeSettings.darkThemeConfig == DarkThemeConfig.DARK
                    )
                ),
                onOptionSelected = { index ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (index) {
                        0 -> onChangeDarkThemeConfig(DarkThemeConfig.FOLLOW_SYSTEM)
                        1 -> onChangeDarkThemeConfig(DarkThemeConfig.LIGHT)
                        2 -> onChangeDarkThemeConfig(DarkThemeConfig.DARK)
                    }
                }
            )
        }
    }
}

@Composable
private fun ModernSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ThemePill(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected)
            color.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected)
            color
        else
            Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillBorder"
    )

    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = color),
                onClick = onClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(modifier = Modifier.size(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) color else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )

            if (selected) {
                Spacer(modifier = Modifier.size(4.dp))
                FlaticonIcon(
                    glyph = FlaticonIcons.CHECK,
                    contentDescription = "Selected",
                    tint = color,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

data class SegmentOption(
    val label: String,
    val iconGlyph: String? = null,
    val selected: Boolean = false
)

@Composable
fun ModernSegmentedButtons(
    options: List<SegmentOption>,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, option ->
                val backgroundColor by animateColorAsState(
                    targetValue = if (option.selected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "segmentBackground"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = true,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            onClick = { onOptionSelected(index) }
                        ),
                    color = backgroundColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (option.iconGlyph != null) {
                            FlaticonIcon(
                                glyph = option.iconGlyph,
                                contentDescription = null,
                                tint = if (option.selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp,
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (option.selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = { onCheckedChange(!checked) }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}
