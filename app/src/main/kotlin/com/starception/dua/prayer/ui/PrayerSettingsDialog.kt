package com.starception.dua.prayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.dua.prayer.viewmodel.PrayerTimesViewModel

/**
 * Prayer Settings Dialog that wraps PrayerSettingsScreen
 */
@Composable
fun PrayerSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: PrayerTimesViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    
    PrayerSettingsScreen(
        settings = settings,
        onSettingsChanged = viewModel::updateSettings,
        onBackClick = onDismiss
    )
}