package com.starception.submission.prayer.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.prayer.model.*

/**
 * Prayer settings configuration screen
 */

private object PrayerSettingsLogger {
    private const val TAG = "PrayerSettingsScreen"
    
    fun logAutoDetectionChange(setting: String, isAutoDetected: Boolean, country: String?) {
        if (isAutoDetected && country != null) {
            Log.i(TAG, "🔧 Auto-detection activated: $setting for $country")
        } else {
            Log.d(TAG, "🔧 Auto-detection status: $setting = ${if (isAutoDetected) "enabled" else "manual"}")
        }
    }
    
    fun logSettingChange(setting: String, oldValue: Any?, newValue: Any?) {
        Log.d(TAG, "⚙️ Setting changed: $setting = $oldValue → $newValue")
    }
    
    fun logScreenComposition(gpsEnabled: Boolean) {
        Log.d(TAG, "📱 Prayer Settings screen composed (GPS: ${if (gpsEnabled) "enabled" else "disabled"})")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsScreen(
    settings: PrayerSettings,
    onSettingsChanged: (PrayerSettings) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAsDialog: Boolean = false,
    hasSettingsChanged: Boolean = false,
    onRestoreClick: () -> Unit = {}
) {
    // Log screen composition with auto-detection status
    PrayerSettingsLogger.logScreenComposition(settings.useGpsLocation)
    
    // Log auto-detection status for all supported settings
    LaunchedEffect(settings.isMethodAutoDetected, settings.autoDetectedCountryName) {
        PrayerSettingsLogger.logAutoDetectionChange("Calculation Method", settings.isMethodAutoDetected, settings.autoDetectedCountryName)
    }
    
    LaunchedEffect(settings.isMadhhabAutoDetected, settings.autoDetectedCountryName) {
        PrayerSettingsLogger.logAutoDetectionChange("Asr Madhhab", settings.isMadhhabAutoDetected, settings.autoDetectedCountryName)
    }
    
    LaunchedEffect(settings.areCustomAnglesAutoDetected, settings.autoDetectedCountryName) {
        PrayerSettingsLogger.logAutoDetectionChange("Custom Angles", settings.areCustomAnglesAutoDetected, settings.autoDetectedCountryName)
    }
    
    // Handle back gesture properly - dismiss dialog and return to previous screen
    BackHandler {
        Log.d("PrayerSettingsScreen", "🔙 Back gesture detected - calling onBackClick")
        onBackClick()
    }
    
    if (showAsDialog) {
        // Dialog mode - no Scaffold, just content with Material 3 theme
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            PrayerSettingsContent(
                settings = settings,
                onSettingsChanged = onSettingsChanged,
                onBackClick = onBackClick,
                hasSettingsChanged = hasSettingsChanged,
                onRestoreClick = onRestoreClick,
                modifier = Modifier.fillMaxSize(),
                showTopBar = true
            )
        }
    } else {
        // Full screen mode - with Scaffold and Material 3 theme
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "Prayer Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
        ) { paddingValues ->
            PrayerSettingsContent(
                settings = settings,
                onSettingsChanged = onSettingsChanged,
                onBackClick = onBackClick,
                hasSettingsChanged = hasSettingsChanged,
                onRestoreClick = onRestoreClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                showTopBar = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerSettingsContent(
    settings: PrayerSettings,
    onSettingsChanged: (PrayerSettings) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = false,
    hasSettingsChanged: Boolean = false,
    onRestoreClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top bar for dialog mode with Material 3 styling
        if (showTopBar) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Prayer Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        
        // Restore Auto-Detected Settings - Show at top when available
        if (hasSettingsChanged && settings.autoDetectedCountryName?.isNotEmpty() == true) {
            RestoreAutoSettingsButton(
                countryName = settings.autoDetectedCountryName ?: "",
                onRestoreClick = onRestoreClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Calculation Method Section
        SettingsSection(
            title = "Calculation Method",
            showAutoDetectedBadge = settings.isMethodAutoDetected
        ) {
            CalculationMethodDropdown(
                selectedMethod = settings.calculationMethod,
                onMethodSelected = { method ->
                    onSettingsChanged(settings.copy(calculationMethod = method))
                },
                isAutoDetected = settings.isMethodAutoDetected,
                autoDetectedCountry = settings.autoDetectedCountryName
            )
        }
        
        // Asr Madhhab Section
        SettingsSection(
            title = "Asr Calculation",
            showAutoDetectedBadge = settings.isMadhhabAutoDetected
        ) {
            AsrMadhhabSelector(
                selectedMadhhab = settings.asrMadhhab,
                onMadhhabSelected = { madhhab ->
                    onSettingsChanged(settings.copy(asrMadhhab = madhhab))
                },
                isAutoDetected = settings.isMadhhabAutoDetected,
                autoDetectedCountry = settings.autoDetectedCountryName
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
        SettingsSection(
            title = "Custom Angles",
            showAutoDetectedBadge = settings.areCustomAnglesAutoDetected
        ) {
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
        
        
        // Restore button moved to top of screen for better visibility
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    showAutoDetectedBadge: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (showAutoDetectedBadge) {
                    AutoDetectionBadge()
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculationMethodDropdown(
    selectedMethod: CalculationMethod,
    onMethodSelected: (CalculationMethod) -> Unit,
    modifier: Modifier = Modifier,
    isAutoDetected: Boolean = false,
    autoDetectedCountry: String? = null
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
                .menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    modifier: Modifier = Modifier,
    isAutoDetected: Boolean = false,
    autoDetectedCountry: String? = null
) {
    Column(modifier = modifier) {
        AsrMadhhab.values().forEach { madhhab ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = selectedMadhhab == madhhab,
                    onClick = { onMadhhabSelected(madhhab) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = madhhab.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                .menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    android.util.Log.d("CustomAnglesSection", "📱 UI Debug: customFajrAngle=${settings.customFajrAngle}, customIshaAngle=${settings.customIshaAngle}, customIshaDelay=${settings.customIshaDelay}, areCustomAnglesAutoDetected=${settings.areCustomAnglesAutoDetected}")

    val focusManager = LocalFocusManager.current

    // Local state to handle text input properly - only reset when settings change from external source
    var fajrAngleText by remember { mutableStateOf(settings.customFajrAngle?.toString() ?: "") }
    var ishaAngleText by remember { mutableStateOf(settings.customIshaAngle?.toString() ?: "") }
    var ishaDelayText by remember { mutableStateOf(settings.customIshaDelay?.toString() ?: "") }
    
    // Update text fields only when settings change from external sources (like restore)
    LaunchedEffect(settings.customFajrAngle) {
        // Only update if the current text doesn't represent the same value
        val currentValue = fajrAngleText.toDoubleOrNull()
        if (currentValue != settings.customFajrAngle) {
            fajrAngleText = settings.customFajrAngle?.toString() ?: ""
        }
    }
    
    LaunchedEffect(settings.customIshaAngle) {
        val currentValue = ishaAngleText.toDoubleOrNull()
        if (currentValue != settings.customIshaAngle) {
            ishaAngleText = settings.customIshaAngle?.toString() ?: ""
        }
    }
    
    LaunchedEffect(settings.customIshaDelay) {
        val currentValue = ishaDelayText.toIntOrNull()
        if (currentValue != settings.customIshaDelay) {
            ishaDelayText = settings.customIshaDelay?.toString() ?: ""
        }
    }
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = fajrAngleText,
                onValueChange = { value ->
                    fajrAngleText = value
                    // Only update settings when value is valid or empty
                    val angle = if (value.isEmpty()) null else value.toDoubleOrNull()
                    if (value.isEmpty() || angle != null) {
                        onSettingsChanged(settings.copy(customFajrAngle = angle))
                    }
                },
                label = { Text("Fajr Angle (°)") },
                placeholder = { Text("Auto-saved") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.weight(1f),
                isError = fajrAngleText.isNotEmpty() && fajrAngleText.toDoubleOrNull() == null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    errorLabelColor = MaterialTheme.colorScheme.error
                ),
                singleLine = true
            )
            
            OutlinedTextField(
                value = ishaAngleText,
                onValueChange = { value ->
                    ishaAngleText = value
                    // Only update settings when value is valid or empty
                    val angle = if (value.isEmpty()) null else value.toDoubleOrNull()
                    if (value.isEmpty() || angle != null) {
                        onSettingsChanged(settings.copy(customIshaAngle = angle))
                    }
                },
                label = { Text("Isha Angle (°)") },
                placeholder = { Text("Auto-saved") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.weight(1f),
                isError = ishaAngleText.isNotEmpty() && ishaAngleText.toDoubleOrNull() == null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    errorLabelColor = MaterialTheme.colorScheme.error
                ),
                singleLine = true
            )
        }
        
        OutlinedTextField(
            value = ishaDelayText,
            onValueChange = { value ->
                ishaDelayText = value
                // Only update settings when value is valid or empty
                val delay = if (value.isEmpty()) null else value.toIntOrNull()
                if (value.isEmpty() || delay != null) {
                    onSettingsChanged(settings.copy(customIshaDelay = delay))
                }
            },
            label = { Text("Isha Delay (minutes after Maghrib)") },
            placeholder = { Text("Auto-saved") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier.fillMaxWidth(),
            isError = ishaDelayText.isNotEmpty() && ishaDelayText.toIntOrNull() == null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error
            ),
            singleLine = true
        )
        
    }
}

@Composable
private fun TimeOffsetsSection(
    offsets: PrayerTimeOffsets,
    onOffsetsChanged: (PrayerTimeOffsets) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val prayerNames = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    val currentOffsets = listOf(
        offsets.fajr, offsets.sunrise, offsets.dhuhr, 
        offsets.asr, offsets.maghrib, offsets.isha
    )
    
    // Local state for text input to handle negative values properly
    val offsetTexts = remember {
        mutableStateListOf<String>().apply {
            addAll(currentOffsets.map { it.toString() })
        }
    }
    
    // Update text fields when offsets change from external sources
    LaunchedEffect(offsets) {
        android.util.Log.d("PrayerSettingsScreen", "📥 OFFSETS RELOAD DETECTED:")
        android.util.Log.d("PrayerSettingsScreen", "   🌅 Fajr: ${offsets.fajr}")
        android.util.Log.d("PrayerSettingsScreen", "   🌄 Sunrise: ${offsets.sunrise}")
        android.util.Log.d("PrayerSettingsScreen", "   🌞 Dhuhr: ${offsets.dhuhr}")
        android.util.Log.d("PrayerSettingsScreen", "   🌇 Asr: ${offsets.asr}")
        android.util.Log.d("PrayerSettingsScreen", "   🌆 Maghrib: ${offsets.maghrib}")
        android.util.Log.d("PrayerSettingsScreen", "   🌙 Isha: ${offsets.isha}")
        
        currentOffsets.forEachIndexed { index, offset ->
            val currentTextValue = offsetTexts[index].toIntOrNull()
            if (currentTextValue != offset) {
                android.util.Log.d("PrayerSettingsScreen", "📝 UPDATING UI field ${prayerNames[index]}: ${offsetTexts[index]} → ${offset}")
                offsetTexts[index] = offset.toString()
            }
        }
        android.util.Log.d("PrayerSettingsScreen", "✅ UI FIELDS UPDATED with stored offsets")
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prayerNames.forEachIndexed { index, prayerName ->
            OutlinedTextField(
                value = offsetTexts[index],
                onValueChange = { value ->
                    android.util.Log.v("PrayerSettingsScreen", "⌨️ USER INPUT: ${prayerName} field changed to '$value'")
                    
                    // Filter input to only allow digits and minus sign at the beginning
                    val filteredValue = when {
                        value.isEmpty() -> value
                        value == "-" -> value
                        value.matches(Regex("^-?\\d+$")) -> value // Only signed integers
                        else -> {
                            android.util.Log.v("PrayerSettingsScreen", "⚠️ INVALID INPUT: '$value' rejected, keeping '${offsetTexts[index]}'")
                            offsetTexts[index] // Keep previous valid value
                        }
                    }
                    
                    offsetTexts[index] = filteredValue
                    
                    // Only update settings when value is valid or empty
                    val offset = if (filteredValue.isEmpty()) 0 else filteredValue.toIntOrNull()
                    if (filteredValue.isEmpty() || offset != null) {
                        val actualOffset = offset ?: 0
                        android.util.Log.d("PrayerSettingsScreen", "📝 UPDATING $prayerName offset: '$filteredValue' → $actualOffset minutes")
                        
                        val newOffsets = when (index) {
                            0 -> offsets.copy(fajr = actualOffset)
                            1 -> offsets.copy(sunrise = actualOffset)
                            2 -> offsets.copy(dhuhr = actualOffset)
                            3 -> offsets.copy(asr = actualOffset)
                            4 -> offsets.copy(maghrib = actualOffset)
                            5 -> offsets.copy(isha = actualOffset)
                            else -> offsets
                        }
                        
                        android.util.Log.d("PrayerSettingsScreen", "💾 CALLING onOffsetsChanged with $prayerName = $actualOffset")
                        onOffsetsChanged(newOffsets)
                        android.util.Log.v("PrayerSettingsScreen", "✅ $prayerName offset update complete")
                    }
                },
                label = { Text("$prayerName Offset (minutes)") },
                placeholder = { Text("Auto-saved") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.fillMaxWidth(),
                isError = offsetTexts[index].isNotEmpty() && offsetTexts[index].toIntOrNull() == null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    errorLabelColor = MaterialTheme.colorScheme.error
                ),
                singleLine = true
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
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use GPS Location")
        }
        
    }
}

/**
 * Compact inline auto-detection badge
 * Shows as a small badge next to option labels
 */
@Composable
private fun AutoDetectionBadge(
    modifier: Modifier = Modifier
) {
    // Material 3 expressive entrance animation
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300) // Delay for staggered appearance
        isVisible = true
    }
    
    // Pulsing animation for the icon
    val iconPulse by rememberInfiniteTransition(label = "icon_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )
    
    // Subtle glow animation
    val glowAlpha by rememberInfiniteTransition(label = "glow_alpha").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        ) + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            )
        ),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = glowAlpha),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 0.dp,
            modifier = Modifier.graphicsLayer {
                // Subtle breathing effect
                scaleX = 1f + (glowAlpha - 0.45f) * 0.1f
                scaleY = 1f + (glowAlpha - 0.45f) * 0.1f
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoMode,
                    contentDescription = "Auto-detected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            scaleX = iconPulse
                            scaleY = iconPulse
                            rotationZ = (iconPulse - 1f) * 20f // Subtle rotation
                        }
                )
                Text(
                    text = "Auto-detected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Restore button for auto-generated prayer settings
 * Shows when user has auto-detected settings that can be restored
 */
@Composable
private fun RestoreAutoSettingsButton(
    countryName: String,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var buttonPressed by remember { mutableStateOf(false) }
    
    // Material 3 expressive animation for button press
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "button_scale"
    )
    
    val cardElevation by animateFloatAsState(
        targetValue = if (buttonPressed) 1f else 4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_elevation"
    )
    
    Card(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onRestoreClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
                shadowElevation = cardElevation
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoMode,
                    contentDescription = "Auto-detected settings",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "Auto-configured for $countryName",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Show what will be restored (always show all available options)
            val restoredItems = listOf("Calculation Method", "Asr Calculation", "Custom Angles")
            
            Text(
                text = "Will restore country-based settings: ${restoredItems.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            OutlinedButton(
                onClick = onRestoreClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restore",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Restore Auto-Generated Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }
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