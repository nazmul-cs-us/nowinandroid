package com.starception.dua.feature.prayertimes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.dua.core.designsystem.theme.NiaTheme
import com.starception.dua.prayer.ui.PrayerTimesCard
import com.starception.dua.prayer.ui.PrayerSettingsScreen
import com.starception.dua.prayer.viewmodel.PrayerTimesViewModel

/**
 * Prayer Times screen showing daily prayer schedule and settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    
    if (showSettings) {
        PrayerSettingsScreen(
            settings = settings,
            onSettingsChanged = viewModel::updateSettings,
            onBackClick = { showSettings = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Prayer Times") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            },
            modifier = modifier
        ) { paddingValues ->
            PrayerTimesContent(
                uiState = uiState,
                onRefresh = viewModel::refresh,
                onRequestLocation = viewModel::requestCurrentLocation,
                onClearError = viewModel::clearError,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@Composable
private fun PrayerTimesContent(
    uiState: com.starception.dua.prayer.viewmodel.PrayerTimesUiState,
    onRefresh: () -> Unit,
    onRequestLocation: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Error handling
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                        if (error.contains("Location", ignoreCase = true)) {
                            TextButton(onClick = onRequestLocation) {
                                Text("Get Location")
                            }
                        }
                        TextButton(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
        
        // Loading state
        if (uiState.isLoading) {
            Card {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Calculating prayer times...")
                    }
                }
            }
        }
        
        // Location loading
        if (uiState.isLoadingLocation) {
            Card {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Getting location...")
                    }
                }
            }
        }
        
        // Prayer times display
        uiState.prayerTimes?.let { prayerTimes ->
            PrayerTimesCard(
                prayerTimes = prayerTimes,
                timeUntilNext = uiState.timeUntilNext
            )
        }
        
        // No prayer times available
        if (!uiState.isLoading && uiState.prayerTimes == null && uiState.error == null) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No Prayer Times Available",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Please set your location to calculate prayer times.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRequestLocation) {
                        Text("Get My Location")
                    }
                }
            }
        }
        
        // Refresh button
        if (uiState.prayerTimes != null) {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Prayer Times")
            }
        }
    }
}