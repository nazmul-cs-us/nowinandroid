package com.starception.submission.prayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.prayer.viewmodel.PrayerTimesViewModel
import com.starception.submission.prayer.model.PrayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Prayer Settings Dialog that wraps PrayerSettingsScreen as full-screen modal
 */
@Composable
fun PrayerSettingsDialog(
    onDismiss: () -> Unit
) {
    // Get context outside of coroutines
    val context = LocalContext.current
    
    // Load real settings from repository without using ViewModel to prevent ANR
    var settings by remember { mutableStateOf(PrayerSettings()) }
    var originalSettings by remember { mutableStateOf(PrayerSettings()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load settings in background without blocking main thread
    LaunchedEffect(Unit) {
        try {
            Log.d("PrayerSettingsDialog", "🔄 Starting to load settings...")
            val repository = com.starception.submission.prayer.repository.PrayerSettingsRepository(context)
            // Use getLoadedSettings() to ensure we get the same settings as other parts of the app
            val loadedSettings = repository.getLoadedSettings()
            Log.d("PrayerSettingsDialog", "📋 Loaded settings via getLoadedSettings():")
            Log.d("PrayerSettingsDialog", "   - Method: ${loadedSettings.calculationMethod.displayName}")
            Log.d("PrayerSettingsDialog", "   - Auto-detected: ${loadedSettings.isMethodAutoDetected}")
            Log.d("PrayerSettingsDialog", "   - Country: ${loadedSettings.autoDetectedCountryName}")
            settings = loadedSettings
            originalSettings = loadedSettings // Store original settings for comparison
            isLoading = false
            Log.d("PrayerSettingsDialog", "✅ Settings applied to UI")
        } catch (e: Exception) {
            // Fallback to defaults if loading fails
            Log.w("PrayerSettingsDialog", "Failed to load settings", e)
            isLoading = false
        }
    }
    
    // Check if settings have changed from original auto-detected values
    val hasSettingsChanged = remember(settings, originalSettings) {
        if (!originalSettings.isMethodAutoDetected && !originalSettings.isMadhhabAutoDetected && !originalSettings.areCustomAnglesAutoDetected) {
            false // No auto-detected settings to compare against
        } else {
            // Check if any auto-detected setting has changed
            (originalSettings.isMethodAutoDetected && settings.calculationMethod != originalSettings.calculationMethod) ||
            (originalSettings.isMadhhabAutoDetected && settings.asrMadhhab != originalSettings.asrMadhhab) ||
            (originalSettings.areCustomAnglesAutoDetected && (
                settings.customFajrAngle != originalSettings.customFajrAngle ||
                settings.customIshaAngle != originalSettings.customIshaAngle ||
                settings.customIshaDelay != originalSettings.customIshaDelay
            ))
        }
    }
    
    // Full screen modal dialog
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoading) {
            // Simple loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else {
            PrayerSettingsScreen(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    Log.i("PrayerSettingsDialog", "🔄 SETTINGS CHANGE TRIGGERED:")
                    Log.i("PrayerSettingsDialog", "   - Old Method: ${settings.calculationMethod.displayName}")
                    Log.i("PrayerSettingsDialog", "   - New Method: ${newSettings.calculationMethod.displayName}")
                    Log.i("PrayerSettingsDialog", "   - Old Madhhab: ${settings.asrMadhhab}")
                    Log.i("PrayerSettingsDialog", "   - New Madhhab: ${newSettings.asrMadhhab}")
                    Log.i("PrayerSettingsDialog", "   - Old Custom Fajr: ${settings.customFajrAngle}")
                    Log.i("PrayerSettingsDialog", "   - New Custom Fajr: ${newSettings.customFajrAngle}")
                    
                    // Clear auto-detection flags when settings are manually changed
                    val updatedSettings = clearAutoDetectionFlags(newSettings, settings)
                    Log.i("PrayerSettingsDialog", "🔄 Auto-detection flags cleared, updating repository...")
                    
                    // Update settings in background to prevent ANR
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            Log.d("PrayerSettingsDialog", "📤 Calling repository.updateSettings()...")
                            val repository = com.starception.submission.prayer.repository.PrayerSettingsRepository(context)
                            repository.updateSettings(updatedSettings)
                            Log.i("PrayerSettingsDialog", "✅ Repository updateSettings() completed")
                            
                            // Update local state
                            withContext(Dispatchers.Main) {
                                settings = updatedSettings
                                Log.i("PrayerSettingsDialog", "✅ Local UI settings updated")
                            }
                        } catch (e: Exception) {
                            Log.e("PrayerSettingsDialog", "❌ Failed to update settings", e)
                        }
                    }
                },
                onBackClick = {
                    Log.i("PrayerSettingsDialog", "🔙 Prayer Settings Dialog CLOSED - changes should now take effect")
                    onDismiss()
                },
                showAsDialog = false,
                hasSettingsChanged = hasSettingsChanged,
                onRestoreClick = {
                    // Restore original auto-detected settings
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val repository = com.starception.submission.prayer.repository.PrayerSettingsRepository(context)
                            repository.updateSettings(originalSettings)
                            withContext(Dispatchers.Main) {
                                settings = originalSettings
                                Log.d("PrayerSettingsDialog", "✅ Auto-detected settings restored")
                            }
                        } catch (e: Exception) {
                            Log.w("PrayerSettingsDialog", "Failed to restore auto-detected settings", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Clear auto-detection flags when settings are manually changed
 */
private fun clearAutoDetectionFlags(newSettings: PrayerSettings, oldSettings: PrayerSettings): PrayerSettings {
    var updatedSettings = newSettings
    
    // Clear calculation method auto-detection flag if method changed
    if (oldSettings.isMethodAutoDetected && newSettings.calculationMethod != oldSettings.calculationMethod) {
        updatedSettings = updatedSettings.copy(isMethodAutoDetected = false)
        Log.d("PrayerSettingsDialog", "🔄 Cleared auto-detection flag for calculation method")
    }
    
    // Clear madhhab auto-detection flag if madhhab changed
    if (oldSettings.isMadhhabAutoDetected && newSettings.asrMadhhab != oldSettings.asrMadhhab) {
        updatedSettings = updatedSettings.copy(isMadhhabAutoDetected = false)
        Log.d("PrayerSettingsDialog", "🔄 Cleared auto-detection flag for Asr madhhab")
    }
    
    // Clear custom angles auto-detection flag if any angle changed
    if (oldSettings.areCustomAnglesAutoDetected && (
        newSettings.customFajrAngle != oldSettings.customFajrAngle ||
        newSettings.customIshaAngle != oldSettings.customIshaAngle ||
        newSettings.customIshaDelay != oldSettings.customIshaDelay
    )) {
        updatedSettings = updatedSettings.copy(areCustomAnglesAutoDetected = false)
        Log.d("PrayerSettingsDialog", "🔄 Cleared auto-detection flag for custom angles")
    }
    
    return updatedSettings
}