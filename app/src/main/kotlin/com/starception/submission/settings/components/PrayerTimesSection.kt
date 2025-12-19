package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Arrangement
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OffsetField(
                label = "Fajr",
                value = offsets.fajr,
                onValueChange = { onOffsetsChange(offsets.copy(fajr = it)) },
                modifier = Modifier.weight(1f)
            )
            OffsetField(
                label = "Sunrise",
                value = offsets.sunrise,
                onValueChange = { onOffsetsChange(offsets.copy(sunrise = it)) },
                modifier = Modifier.weight(1f)
            )
            OffsetField(
                label = "Dhuhr",
                value = offsets.dhuhr,
                onValueChange = { onOffsetsChange(offsets.copy(dhuhr = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OffsetField(
                label = "Asr",
                value = offsets.asr,
                onValueChange = { onOffsetsChange(offsets.copy(asr = it)) },
                modifier = Modifier.weight(1f)
            )
            OffsetField(
                label = "Maghrib",
                value = offsets.maghrib,
                onValueChange = { onOffsetsChange(offsets.copy(maghrib = it)) },
                modifier = Modifier.weight(1f)
            )
            OffsetField(
                label = "Isha",
                value = offsets.isha,
                onValueChange = { onOffsetsChange(offsets.copy(isha = it)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

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
private fun RestoreButton(
    countryName: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Restore settings for $countryName")
    }
}
