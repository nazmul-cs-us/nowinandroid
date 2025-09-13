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
    
    // ALGORITHM IMPLEMENTATION: Initialization 
    var settings by remember { mutableStateOf(PrayerSettings()) }
    var isLoading by remember { mutableStateOf(true) }
    val repository = remember { com.starception.submission.prayer.repository.PrayerSettingsRepository(context) }
    
    // Initialization: Load settings following the algorithm
    LaunchedEffect(Unit) {
        try {
            Log.i("PrayerSettingsDialog", "🔄 ALGORITHM: Starting initialization...")
            
            // 1. Detect cached country
            val cachedCountry = repository.getCachedCountry()
            Log.i("PrayerSettingsDialog", "📍 Cached country: $cachedCountry")
            
            // 2. Load auto-detected settings for that country (if available)
            val autoDetectedSettings = cachedCountry?.let { repository.getAutoDetectedSettingsForCountry(it) }
            Log.i("PrayerSettingsDialog", "🤖 Auto-detected settings: ${autoDetectedSettings != null}")
            
            // 3. Load cached prayer settings from preferences
            val cachedSettings = repository.getCachedPrayerSettings()
            Log.i("PrayerSettingsDialog", "💾 Cached settings: ${cachedSettings != null}")
            
            // 4. Populate UI fields following priority
            val initialSettings = when {
                cachedSettings != null -> {
                    Log.i("PrayerSettingsDialog", "✅ Using cached prayer settings")
                    cachedSettings
                }
                autoDetectedSettings != null -> {
                    Log.i("PrayerSettingsDialog", "✅ Using auto-detected settings for $cachedCountry")
                    autoDetectedSettings
                }
                else -> {
                    Log.i("PrayerSettingsDialog", "✅ Using default settings")
                    PrayerSettings()
                }
            }
            
            settings = initialSettings
            isLoading = false
            Log.i("PrayerSettingsDialog", "✅ Initialization complete")
        } catch (e: Exception) {
            Log.e("PrayerSettingsDialog", "❌ Initialization failed", e)
            isLoading = false
        }
    }
    
    // ALGORITHM: Restore Option Logic
    // Compare cached_prayer_settings with auto-detected settings to show/hide restore option
    val hasSettingsChanged = remember(settings, isLoading) {
        if (isLoading) {
            false
        } else {
            // Use algorithm's shouldShowRestoreOption method
            repository.shouldShowRestoreOption()
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
                    Log.i("PrayerSettingsDialog", "📝 ALGORITHM: User changed settings")
                    
                    // ALGORITHM: Update cached_prayer_settings and recalculate
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            // The repository.updateSettings follows the algorithm:
                            // 1. Update cached_prayer_settings (JSON in preferences)
                            // 2. Immediately recalculate prayer times
                            repository.updateSettings(newSettings)
                            
                            // Update local UI state
                            withContext(Dispatchers.Main) {
                                settings = newSettings
                                Log.i("PrayerSettingsDialog", "✅ Local UI updated")
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
                    Log.i("PrayerSettingsDialog", "🔄 ALGORITHM: Restore to auto-detected clicked")
                    
                    // ALGORITHM: Use repository's restore method
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val success = repository.restoreToAutoDetected()
                            
                            if (success) {
                                // Get the restored settings and update UI
                                val restoredSettings = repository.getCachedPrayerSettings()
                                if (restoredSettings != null) {
                                    withContext(Dispatchers.Main) {
                                        settings = restoredSettings
                                        Log.i("PrayerSettingsDialog", "✅ Restored to auto-detected settings")
                                    }
                                }
                            } else {
                                Log.w("PrayerSettingsDialog", "⚠️ Restore failed - no auto-detected settings available")
                            }
                        } catch (e: Exception) {
                            Log.e("PrayerSettingsDialog", "❌ Failed to restore settings", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ALGORITHM IMPLEMENTATION COMPLETE
// The Prayer Settings now follow the specified algorithm:
// 1. Initialization: Detect cached country → Load auto-detected → Load cached → Populate UI
// 2. User changes: Update cached_prayer_settings → Recalculate times
// 3. Restore logic: Compare cached vs auto-detected JSON → Show/hide restore option