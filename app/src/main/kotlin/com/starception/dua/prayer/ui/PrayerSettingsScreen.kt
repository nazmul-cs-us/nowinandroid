package com.starception.dua.prayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.starception.dua.core.designsystem.theme.NiaTheme
import com.starception.dua.prayer.model.*

/**
 * Prayer settings configuration screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsScreen(
    settings: PrayerSettings,
    onSettingsChanged: (PrayerSettings) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prayer Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calculation Method Section
            SettingsSection(title = "Calculation Method") {
                CalculationMethodDropdown(
                    selectedMethod = settings.calculationMethod,
                    onMethodSelected = { method ->
                        onSettingsChanged(settings.copy(calculationMethod = method))
                    }
                )
            }
            
            // Asr Madhhab Section
            SettingsSection(title = "Asr Calculation") {
                AsrMadhhabSelector(
                    selectedMadhhab = settings.asrMadhhab,
                    onMadhhabSelected = { madhhab ->
                        onSettingsChanged(settings.copy(asrMadhhab = madhhab))
                    }
                )
            }
            
            // High Latitude Adjustment Section
            SettingsSection(title = "High Latitude Adjustment") {
                HighLatitudeAdjustmentDropdown(
                    selectedAdjustment = settings.highLatitudeAdjustment,
                    onAdjustmentSelected = { adjustment ->
                        onSettingsChanged(settings.copy(highLatitudeAdjustment = adjustment))
                    }
                )
            }
            
            // Custom Angles Section
            SettingsSection(title = "Custom Angles") {
                CustomAnglesSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged
                )
            }
            
            // Time Offsets Section
            SettingsSection(title = "Time Adjustments (minutes)") {
                TimeOffsetsSection(
                    offsets = settings.timeOffsets,
                    onOffsetsChanged = { offsets ->
                        onSettingsChanged(settings.copy(timeOffsets = offsets))
                    }
                )
            }
            
            // Location Section
            SettingsSection(title = "Location") {
                LocationSection(
                    useGps = settings.useGpsLocation,
                    location = settings.location,
                    onUseGpsChanged = { useGps ->
                        onSettingsChanged(settings.copy(useGpsLocation = useGps))
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculationMethodDropdown(
    selectedMethod: CalculationMethod,
    onMethodSelected: (CalculationMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedMethod.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CalculationMethod.values().forEach { method ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(method.displayName)
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

@Composable
private fun AsrMadhhabSelector(
    selectedMadhhab: AsrMadhhab,
    onMadhhabSelected: (AsrMadhhab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AsrMadhhab.values().forEach { madhhab ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = selectedMadhhab == madhhab,
                    onClick = { onMadhhabSelected(madhhab) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = madhhab.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = madhhab.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighLatitudeAdjustmentDropdown(
    selectedAdjustment: HighLatitudeAdjustment,
    onAdjustmentSelected: (HighLatitudeAdjustment) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedAdjustment.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("High Latitude Method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            HighLatitudeAdjustment.values().forEach { adjustment ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(adjustment.displayName)
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
private fun CustomAnglesSection(
    settings: PrayerSettings,
    onSettingsChanged: (PrayerSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = settings.customFajrAngle?.toString() ?: "",
                onValueChange = { value ->
                    val angle = value.toDoubleOrNull()
                    onSettingsChanged(settings.copy(customFajrAngle = angle))
                },
                label = { Text("Fajr Angle (°)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            
            OutlinedTextField(
                value = settings.customIshaAngle?.toString() ?: "",
                onValueChange = { value ->
                    val angle = value.toDoubleOrNull()
                    onSettingsChanged(settings.copy(customIshaAngle = angle))
                },
                label = { Text("Isha Angle (°)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        
        OutlinedTextField(
            value = settings.customIshaDelay?.toString() ?: "",
            onValueChange = { value ->
                val delay = value.toIntOrNull()
                onSettingsChanged(settings.copy(customIshaDelay = delay))
            },
            label = { Text("Isha Delay (minutes after Maghrib)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TimeOffsetsSection(
    offsets: PrayerTimeOffsets,
    onOffsetsChanged: (PrayerTimeOffsets) -> Unit,
    modifier: Modifier = Modifier
) {
    val prayerNames = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    val currentOffsets = listOf(
        offsets.fajr, offsets.sunrise, offsets.dhuhr, 
        offsets.asr, offsets.maghrib, offsets.isha
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prayerNames.forEachIndexed { index, prayerName ->
            OutlinedTextField(
                value = currentOffsets[index].toString(),
                onValueChange = { value ->
                    val offset = value.toIntOrNull() ?: 0
                    val newOffsets = when (index) {
                        0 -> offsets.copy(fajr = offset)
                        1 -> offsets.copy(sunrise = offset)
                        2 -> offsets.copy(dhuhr = offset)
                        3 -> offsets.copy(asr = offset)
                        4 -> offsets.copy(maghrib = offset)
                        5 -> offsets.copy(isha = offset)
                        else -> offsets
                    }
                    onOffsetsChanged(newOffsets)
                },
                label = { Text("$prayerName Offset") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LocationSection(
    useGps: Boolean,
    location: Location?,
    onUseGpsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Switch(
                checked = useGps,
                onCheckedChange = onUseGpsChanged
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use GPS Location")
        }
        
        if (location != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Current: ${location.getDisplayName()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview
@Composable
private fun PrayerSettingsScreenPreview() {
    NiaTheme {
        val sampleSettings = PrayerSettings(
            calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            asrMadhhab = AsrMadhhab.STANDARD,
            highLatitudeAdjustment = HighLatitudeAdjustment.NONE
        )
        
        PrayerSettingsScreen(
            settings = sampleSettings,
            onSettingsChanged = {},
            onBackClick = {}
        )
    }
}