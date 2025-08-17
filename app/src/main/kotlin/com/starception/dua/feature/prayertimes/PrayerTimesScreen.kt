package com.starception.submission.feature.prayertimes

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.prayer.ui.PrayerTimesCard
import com.starception.submission.prayer.viewmodel.PrayerTimesViewModel

/**
 * Prayer Times screen showing daily prayer schedule
 * Settings are accessed via the main app's context-aware settings button
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Handle location permissions
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    PrayerTimesContent(
        uiState = uiState,
        locationPermissions = locationPermissions,
        onRefresh = { viewModel.refresh(showLoading = false) }, // Pull-to-refresh: smooth animation
        onRefreshButton = { viewModel.refresh(showLoading = true) }, // Manual button: show loading
        onRequestLocation = {
            if (locationPermissions.allPermissionsGranted) {
                viewModel.requestCurrentLocation()
            } else {
                locationPermissions.launchMultiplePermissionRequest()
            }
        },
        onClearError = viewModel::clearError,
        modifier = modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PrayerTimesContent(
            uiState: com.starception.submission.prayer.viewmodel.PrayerTimesUiState,
    locationPermissions: com.google.accompanist.permissions.MultiplePermissionsState,
    onRefresh: () -> Unit,
    onRefreshButton: () -> Unit,
    onRequestLocation: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(300.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 24.dp,
        modifier = modifier.fillMaxSize()
    ) {
        // Error handling
        uiState.error?.let { error ->
            item(span = StaggeredGridItemSpan.FullLine, contentType = "error") {
                Card(
                modifier = Modifier.padding(horizontal = 8.dp),
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
                        if (error.contains("Location", ignoreCase = true) || error.contains("permission", ignoreCase = true)) {
                            TextButton(onClick = onRequestLocation) {
                                Text(
                                    if (locationPermissions.allPermissionsGranted) "Get Location" 
                                    else "Grant Permission"
                                )
                            }
                        }
                        TextButton(onClick = onRefreshButton) {
                            Text("Retry")
                        }
                    }
                }
            }
            }
        }
        
        // Loading state
        if (uiState.isLoading) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "loading") {
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Calculating prayer times...")
                    }
                }
            }
            }
        }
        
        // Location loading
        if (uiState.isLoadingLocation) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "locationLoading") {
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
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
        }
        
        // Prayer times display
        uiState.prayerTimes?.let { prayerTimes ->
            item(span = StaggeredGridItemSpan.FullLine, contentType = "prayerTimes") {
                PrayerTimesCard(
                    prayerTimes = prayerTimes,
                    timeUntilNext = uiState.timeUntilNext,
                    calculationMethod = uiState.calculationMethod,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    onRefreshButton = onRefreshButton,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        
        // No prayer times available
        if (!uiState.isLoading && uiState.prayerTimes == null && uiState.error == null) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "noPrayerTimes") {
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
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
                        Text(
                            if (locationPermissions.allPermissionsGranted) "Get My Location"
                            else "Grant Location Permission"
                        )
                    }
                }
            }
            }
        }
    }
}