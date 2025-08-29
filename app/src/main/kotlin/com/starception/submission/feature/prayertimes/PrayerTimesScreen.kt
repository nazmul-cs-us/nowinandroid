package com.starception.submission.feature.prayertimes

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus.Denied
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.feature.prayertimes.components.PrayerTimesHeaderCard
import com.starception.submission.feature.prayertimes.components.PrayerTimesLoadingCard
import com.starception.submission.feature.prayertimes.components.PrayerTimeCard
import com.starception.submission.feature.prayertimes.utils.getCurrentDate
import com.starception.submission.feature.prayertimes.utils.formatTime
import com.starception.submission.feature.prayertimes.data.PrayerTimesCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Prayer Times screen showing daily prayer schedule
 * Settings are accessed via the main app's context-aware settings button
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var prayerTimes by remember { mutableStateOf<com.starception.submission.prayer.model.DayPrayerTimes?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf("Loading location...") }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    
    // Request notification permission for prayer alerts
    val notificationPermissionState = rememberPermissionState(
        permission = Manifest.permission.POST_NOTIFICATIONS
    )
    
    // Request location permission for accurate prayer times
    val locationPermissionState = rememberPermissionState(
        permission = Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    // Request permissions when screen opens
    LaunchedEffect(Unit) {
        // Request location permission first for accurate prayer times
        val locationStatus = locationPermissionState.status
        if (locationStatus is Denied && !locationStatus.shouldShowRationale) {
            locationPermissionState.launchPermissionRequest()
        }
        
        // Then request notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val notificationStatus = notificationPermissionState.status
            if (notificationStatus is Denied && !notificationStatus.shouldShowRationale) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }
    
    // Function to calculate prayer times
    suspend fun calculatePrayerTimes() {
        try {
            withContext(Dispatchers.Default) {
                val calculator = PrayerTimesCalculator(context)
                val result = calculator.calculateDefaultPrayerTimes()
                
                prayerTimes = result.first
                location = result.second
            }
        } catch (e: Exception) {
            // Keep current values if calculation fails
        }
    }
    
    // Calculate prayer times in background to prevent blocking
    // Recalculate when permissions are granted
    LaunchedEffect(locationPermissionState.status) {
        isLoading = true
        calculatePrayerTimes()
        isLoading = false
    }
    
    // Handle pull-to-refresh
    suspend fun onRefresh() {
        isRefreshing = true
        calculatePrayerTimes()
        isRefreshing = false
    }
    
    // PNG File Icon Design with App Theme
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        if (isLoading) {
            // Loading state centered
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Loading Prayer Times...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top section with preview area using app theme
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🕌",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Prayer Times",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "📍 $location",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "📅 ${getCurrentDate()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Prayer times list
                val times = prayerTimes
                val prayers = if (times != null) {
                    listOf(
                        "Fajr" to formatTime(times.fajr),
                        "Sunrise" to formatTime(times.sunrise),
                        "Dhuhr" to formatTime(times.dhuhr),
                        "Asr" to formatTime(times.asr),
                        "Maghrib" to formatTime(times.maghrib),
                        "Isha" to formatTime(times.isha)
                    )
                } else {
                    listOf(
                        "Fajr" to "5:30 AM",
                        "Sunrise" to "6:45 AM",
                        "Dhuhr" to "12:15 PM",
                        "Asr" to "3:45 PM",
                        "Maghrib" to "6:30 PM",
                        "Isha" to "8:00 PM"
                    )
                }
                
                prayers.forEach { (name, time) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // Bottom label
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PRAYER",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}