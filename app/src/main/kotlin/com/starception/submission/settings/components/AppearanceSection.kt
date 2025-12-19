package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.theme.supportsDynamicTheming
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.settings.ThemeSettingsState

@Composable
fun AppearanceSection(
    themeSettings: ThemeSettingsState,
    onChangeThemeBrand: (ThemeBrand) -> Unit,
    onChangeDynamicColorPreference: (Boolean) -> Unit,
    onChangeDarkThemeConfig: (DarkThemeConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val supportDynamicColor = supportsDynamicTheming()

    Column(modifier = modifier) {
        // Theme Brand Section
        SectionTitle(text = "Theme")
        Column(Modifier.selectableGroup()) {
            ThemeChooserRow(
                text = "Default",
                selected = themeSettings.brand == ThemeBrand.DEFAULT,
                onClick = { onChangeThemeBrand(ThemeBrand.DEFAULT) }
            )
            ThemeChooserRow(
                text = "Android",
                selected = themeSettings.brand == ThemeBrand.ANDROID,
                onClick = { onChangeThemeBrand(ThemeBrand.ANDROID) }
            )
            ThemeChooserRow(
                text = "Coastal",
                selected = themeSettings.brand == ThemeBrand.COASTAL,
                onClick = { onChangeThemeBrand(ThemeBrand.COASTAL) }
            )
        }

        // Dynamic Color Section (only visible when Default theme and Android 12+)
        AnimatedVisibility(visible = themeSettings.brand == ThemeBrand.DEFAULT && supportDynamicColor) {
            Column {
                SectionTitle(text = "Dynamic Color")
                Column(Modifier.selectableGroup()) {
                    ThemeChooserRow(
                        text = "Yes",
                        selected = themeSettings.useDynamicColor,
                        onClick = { onChangeDynamicColorPreference(true) }
                    )
                    ThemeChooserRow(
                        text = "No",
                        selected = !themeSettings.useDynamicColor,
                        onClick = { onChangeDynamicColorPreference(false) }
                    )
                }
            }
        }

        // Dark Mode Section
        SectionTitle(text = "Dark Mode")
        Column(Modifier.selectableGroup()) {
            ThemeChooserRow(
                text = "System Default",
                selected = themeSettings.darkThemeConfig == DarkThemeConfig.FOLLOW_SYSTEM,
                onClick = { onChangeDarkThemeConfig(DarkThemeConfig.FOLLOW_SYSTEM) }
            )
            ThemeChooserRow(
                text = "Light",
                selected = themeSettings.darkThemeConfig == DarkThemeConfig.LIGHT,
                onClick = { onChangeDarkThemeConfig(DarkThemeConfig.LIGHT) }
            )
            ThemeChooserRow(
                text = "Dark",
                selected = themeSettings.darkThemeConfig == DarkThemeConfig.DARK,
                onClick = { onChangeDarkThemeConfig(DarkThemeConfig.DARK) }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeChooserRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
