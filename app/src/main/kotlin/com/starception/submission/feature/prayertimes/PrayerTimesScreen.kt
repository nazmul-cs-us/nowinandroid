package com.starception.submission.feature.prayertimes

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.withContext

/**
 * Prayer Times screen showing daily prayer schedule
 * Settings are accessed via the main app's context-aware settings button
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var prayerTimes by remember { mutableStateOf<com.starception.submission.prayer.model.DayPrayerTimes?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf("Loading location...") }
    
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
    
    // Calculate prayer times in background to prevent blocking
    // Recalculate when permissions are granted
    LaunchedEffect(locationPermissionState.status) {
        try {
            withContext(Dispatchers.Default) {
                val calculator = PrayerTimesCalculator(context)
                val result = calculator.calculateDefaultPrayerTimes()
                
                prayerTimes = result.first
                location = result.second
                isLoading = false
            }
        } catch (e: Exception) {
            // Fallback to static times if calculation fails
            isLoading = false
        }
    }
    
    // Use same layout pattern as other tabs
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 24.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Prayer times header card
            item(span = StaggeredGridItemSpan.FullLine) {
                PrayerTimesHeaderCard(
                    location = location,
                    date = getCurrentDate()
                )
            }
            
            if (isLoading) {
                // Loading card
                item(span = StaggeredGridItemSpan.FullLine) {
                    PrayerTimesLoadingCard()
                }
            } else {
                // Prayer times cards
                val times = prayerTimes
                if (times != null) {
                    // Dynamic prayer times
                    item { PrayerTimeCard("Fajr", formatTime(times.fajr)) }
                    item { PrayerTimeCard("Sunrise", formatTime(times.sunrise)) }
                    item { PrayerTimeCard("Dhuhr", formatTime(times.dhuhr)) }
                    item { PrayerTimeCard("Asr", formatTime(times.asr)) }
                    item { PrayerTimeCard("Maghrib", formatTime(times.maghrib)) }
                    item { PrayerTimeCard("Isha", formatTime(times.isha)) }
                } else {
                    // Fallback static times
                    item { PrayerTimeCard("Fajr", "5:30 AM") }
                    item { PrayerTimeCard("Sunrise", "6:45 AM") }
                    item { PrayerTimeCard("Dhuhr", "12:15 PM") }
                    item { PrayerTimeCard("Asr", "3:45 PM") }
                    item { PrayerTimeCard("Maghrib", "6:30 PM") }
                    item { PrayerTimeCard("Isha", "8:00 PM") }
                }
            }
        }
    }
}