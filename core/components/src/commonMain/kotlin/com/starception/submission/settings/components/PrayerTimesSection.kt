package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.prayer.model.AsrMadhhab
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.HighLatitudeAdjustment
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.model.PrayerTimeOffsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesSection(
    prayerSettings: PrayerSettings,
    showRestoreOption: Boolean,
    autoDetectedCountryName: String?,
    onSettingsChange: (PrayerSettings) -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column(modifier = modifier) {
        // Calculation Method Dropdown
        SectionLabel("Calculation Method")
        CalculationMethodDropdown(
            selectedMethod = prayerSettings.calculationMethod,
            onMethodSelected = { method ->
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettingsChange(prayerSettings.copy(calculationMethod = method))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Asr Madhhab Selection
        SectionLabel("Asr Calculation")
        Column(Modifier.selectableGroup()) {
            MadhhabRow(
                text = "Standard (Shafi'i, Maliki, Hanbali)",
                description = "Shadow equals object length",
                selected = prayerSettings.asrMadhhab == AsrMadhhab.STANDARD,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsChange(prayerSettings.copy(asrMadhhab = AsrMadhhab.STANDARD))
                }
            )
            MadhhabRow(
                text = "Hanafi",
                description = "Shadow equals twice object length",
                selected = prayerSettings.asrMadhhab == AsrMadhhab.HANAFI,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsChange(prayerSettings.copy(asrMadhhab = AsrMadhhab.HANAFI))
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // High Latitude Adjustment
        SectionLabel("High Latitude Adjustment")
        HighLatitudeDropdown(
            selectedAdjustment = prayerSettings.highLatitudeAdjustment,
            onAdjustmentSelected = { adjustment ->
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettingsChange(prayerSettings.copy(highLatitudeAdjustment = adjustment))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Angles Section
        SectionLabel("Custom Angles (degrees)")
        CustomAnglesGrid(
            fajrAngle = prayerSettings.customFajrAngle,
            ishaAngle = prayerSettings.customIshaAngle,
            defaultFajrAngle = prayerSettings.calculationMethod.fajrAngle,
            defaultIshaAngle = prayerSettings.calculationMethod.ishaAngle,
            onFajrAngleChange = { angle ->
                onSettingsChange(prayerSettings.copy(customFajrAngle = angle))
            },
            onIshaAngleChange = { angle ->
                onSettingsChange(prayerSettings.copy(customIshaAngle = angle))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Overrides Section
        SectionLabel("Advanced Overrides")
        AdvancedOverridesGrid(
            ishaDelay = prayerSettings.customIshaDelay,
            maghribOffset = prayerSettings.customMaghribOffset,
            defaultIshaDelay = prayerSettings.calculationMethod.ishaDelay,
            defaultMaghribOffset = prayerSettings.calculationMethod.maghribOffset,
            onIshaDelayChange = { delay ->
                onSettingsChange(prayerSettings.copy(customIshaDelay = delay))
            },
            onMaghribOffsetChange = { offset ->
                onSettingsChange(prayerSettings.copy(customMaghribOffset = offset))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Time Offsets Section
        SectionLabel("Time Adjustments (minutes)")
        TimeOffsetsGrid(
            offsets = prayerSettings.timeOffsets,
            onOffsetsChange = { newOffsets ->
                onSettingsChange(prayerSettings.copy(timeOffsets = newOffsets))
            }
        )

        // Restore Button (if available)
        if (showRestoreOption && autoDetectedCountryName != null) {
            Spacer(modifier = Modifier.height(16.dp))
            RestoreButton(
                countryName = autoDetectedCountryName,
                onClick = onRestoreClick
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculationMethodDropdown(
    selectedMethod: CalculationMethod,
    onMethodSelected: (CalculationMethod) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedMethod.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CalculationMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = method.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = method.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onMethodSelected(method)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighLatitudeDropdown(
    selectedAdjustment: HighLatitudeAdjustment,
    onAdjustmentSelected: (HighLatitudeAdjustment) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedAdjustment.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            HighLatitudeAdjustment.entries.forEach { adjustment ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = adjustment.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = adjustment.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onAdjustmentSelected(adjustment)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MadhhabRow(
    text: String,
    description: String,
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
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeOffsetsGrid(
    offsets: PrayerTimeOffsets,
    onOffsetsChange: (PrayerTimeOffsets) -> Unit
) {
    val fields = listOf(
        OffsetFieldSpec("Fajr", offsets.fajr) { onOffsetsChange(offsets.copy(fajr = it)) },
        OffsetFieldSpec("Sunrise", offsets.sunrise) { onOffsetsChange(offsets.copy(sunrise = it)) },
        OffsetFieldSpec("Dhuhr", offsets.dhuhr) { onOffsetsChange(offsets.copy(dhuhr = it)) },
        OffsetFieldSpec("Asr", offsets.asr) { onOffsetsChange(offsets.copy(asr = it)) },
        OffsetFieldSpec("Maghrib", offsets.maghrib) { onOffsetsChange(offsets.copy(maghrib = it)) },
        OffsetFieldSpec("Isha", offsets.isha) { onOffsetsChange(offsets.copy(isha = it)) },
    )
    BoxWithConstraints {
        val columns = if (maxWidth < 360.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.chunked(columns).forEach { rowFields ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowFields.forEach { field ->
                        OffsetField(
                            label = field.label,
                            value = field.value,
                            onValueChange = field.onValueChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private data class OffsetFieldSpec(
    val label: String,
    val value: Int,
    val onValueChange: (Int) -> Unit,
)

@Composable
private fun OffsetField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            // Allow empty, minus sign, or valid integers
            if (newValue.isEmpty() || newValue == "-" || newValue.toIntOrNull() != null) {
                textValue = newValue
                val intValue = newValue.toIntOrNull() ?: 0
                onValueChange(intValue)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun CustomAnglesGrid(
    fajrAngle: Double?,
    ishaAngle: Double?,
    defaultFajrAngle: Double,
    defaultIshaAngle: Double?,
    onFajrAngleChange: (Double?) -> Unit,
    onIshaAngleChange: (Double?) -> Unit
) {
    // Use 18.0 as fallback for Isha if method uses time-based delay instead of angle
    val effectiveDefaultIshaAngle = defaultIshaAngle ?: 18.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResponsiveSettingsFieldPair(
            first = { modifier ->
                AngleField(
                    label = "Fajr",
                    value = fajrAngle,
                    defaultValue = defaultFajrAngle,
                    onValueChange = onFajrAngleChange,
                    modifier = modifier,
                )
            },
            second = { modifier ->
                AngleField(
                    label = "Isha",
                    value = ishaAngle,
                    defaultValue = effectiveDefaultIshaAngle,
                    onValueChange = onIshaAngleChange,
                    modifier = modifier,
                )
            },
        )
        Text(
            text = "Leave empty to use method defaults (Fajr: ${defaultFajrAngle}°, Isha: ${effectiveDefaultIshaAngle}°)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AngleField(
    label: String,
    value: Double?,
    defaultValue: Double,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) {
        mutableStateOf(value?.toString() ?: "")
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            // Allow empty, or valid decimal numbers
            if (newValue.isEmpty() || newValue == "." || newValue.toDoubleOrNull() != null) {
                textValue = newValue
                val doubleValue = newValue.toDoubleOrNull()
                onValueChange(doubleValue)
            }
        },
        label = {
            Text(
                text = if (value == null) "$label (${defaultValue}°)" else label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        placeholder = { Text("${defaultValue}°") },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun AdvancedOverridesGrid(
    ishaDelay: Int?,
    maghribOffset: Int?,
    defaultIshaDelay: Int?,
    defaultMaghribOffset: Int,
    onIshaDelayChange: (Int?) -> Unit,
    onMaghribOffsetChange: (Int?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResponsiveSettingsFieldPair(
            first = { modifier ->
                MinuteField(
                    label = "Isha Delay",
                    value = ishaDelay,
                    defaultValue = defaultIshaDelay,
                    onValueChange = onIshaDelayChange,
                    hint = "min after Maghrib",
                    modifier = modifier,
                )
            },
            second = { modifier ->
                MinuteField(
                    label = "Maghrib Offset",
                    value = maghribOffset,
                    defaultValue = defaultMaghribOffset,
                    onValueChange = onMaghribOffsetChange,
                    hint = "min after sunset",
                    modifier = modifier,
                )
            },
        )
        Text(
            text = buildString {
                append("Isha delay: minutes after Maghrib (used by Umm al-Qura). ")
                append("Maghrib offset: minutes after sunset (used by some Shia methods).")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResponsiveSettingsFieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints {
        if (maxWidth < 320.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MinuteField(
    label: String,
    value: Int?,
    defaultValue: Int?,
    onValueChange: (Int?) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) {
        mutableStateOf(value?.toString() ?: "")
    }

    val displayDefault = defaultValue?.toString() ?: "N/A"

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            // Allow empty, minus sign, or valid integers
            if (newValue.isEmpty() || newValue == "-" || newValue.toIntOrNull() != null) {
                textValue = newValue
                val intValue = newValue.toIntOrNull()
                onValueChange(intValue)
            }
        },
        label = {
            Text(
                text = if (value == null) "$label ($displayDefault)" else label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        placeholder = { Text(hint) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun RestoreButton(
    countryName: String,
    onClick: () -> Unit
) {
    NiaOutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Restore settings for $countryName")
    }
}
