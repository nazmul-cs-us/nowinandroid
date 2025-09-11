package com.starception.submission.prayer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.prayer.viewmodel.PrayerTimesViewModel

/**
 * Prayer Settings Dialog that wraps PrayerSettingsScreen as full-screen modal
 */
@Composable
fun PrayerSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: PrayerTimesViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    
    // Full screen modal dialog
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        PrayerSettingsScreen(
            settings = settings,
            onSettingsChanged = viewModel::updateSettingsManually,
            onBackClick = onDismiss,
            showAsDialog = false, // Use full screen mode with scaffold
            hasSettingsChanged = viewModel.hasSettingsChanged(),
            onRestoreClick = viewModel::restoreAutoDetectedSettings,
            onSaveCurrentSettings = viewModel::saveCurrentSettings,
            modifier = Modifier.fillMaxSize()
        )
    }
}