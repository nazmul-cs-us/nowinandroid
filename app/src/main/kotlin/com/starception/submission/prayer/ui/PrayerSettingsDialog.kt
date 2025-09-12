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
    var isLoading by remember { mutableStateOf(true) }
    
    // Load settings in background without blocking main thread
    LaunchedEffect(Unit) {
        try {
            val repository = com.starception.submission.prayer.repository.PrayerSettingsRepository(context)
            settings = repository.getSettings()
            isLoading = false
        } catch (e: Exception) {
            // Fallback to defaults if loading fails
            Log.w("PrayerSettingsDialog", "Failed to load settings", e)
            isLoading = false
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
                    // Update settings in background to prevent ANR
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val repository = com.starception.submission.prayer.repository.PrayerSettingsRepository(context)
                            repository.updateSettings(newSettings)
                            // Update local state
                            withContext(Dispatchers.Main) {
                                settings = newSettings
                            }
                        } catch (e: Exception) {
                            Log.w("PrayerSettingsDialog", "Failed to update settings", e)
                        }
                    }
                },
                onBackClick = onDismiss,
                showAsDialog = false,
                hasSettingsChanged = false,
                onRestoreClick = {
                    // Reset to defaults
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val repository = com.starception.submission.prayer.repository.PrayerSettingsRepository(context)
                            val defaultSettings = PrayerSettings()
                            repository.updateSettings(defaultSettings)
                            withContext(Dispatchers.Main) {
                                settings = defaultSettings
                            }
                        } catch (e: Exception) {
                            Log.w("PrayerSettingsDialog", "Failed to restore settings", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}