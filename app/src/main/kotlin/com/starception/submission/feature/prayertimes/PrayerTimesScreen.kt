package com.starception.submission.feature.prayertimes

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus.Denied
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.feature.prayertimes.utils.getCurrentDate
import com.starception.submission.feature.prayertimes.utils.formatTime
import com.starception.submission.feature.prayertimes.data.PrayerTimesCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration

/**
 * Beautiful Prayer Times screen using Material 3 expressive design
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
    
    // Live update state
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
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
    
    // Live time updates every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            kotlinx.coroutines.delay(60000) // Update every minute
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
    
    // Get next prayer and current prayer
    fun getNextPrayer(): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null
        
        val prayers = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        // Find next prayer today
        val nextPrayer = prayers.find { it.second.isAfter(currentTime) }
        return nextPrayer ?: prayers.first() // If no prayers left today, return Fajr (tomorrow)
    }
    
    fun getCurrentPrayer(): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null
        
        val prayers = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        // Find current prayer (the one we're in the time window for)
        for (i in prayers.indices) {
            val prayer = prayers[i]
            val nextPrayer = if (i < prayers.size - 1) prayers[i + 1] else null
            
            if (nextPrayer != null) {
                if (currentTime.isAfter(prayer.second) && currentTime.isBefore(nextPrayer.second)) {
                    return prayer
                }
            } else {
                // For Isha, check if we're within 2 hours after it starts
                if (currentTime.isAfter(prayer.second) && currentTime.isBefore(prayer.second.plusHours(2))) {
                    return prayer
                }
            }
        }
        return null
    }
    
    // Calculate time until next prayer
    fun getTimeUntilNextPrayer(): String {
        val nextPrayer = getNextPrayer() ?: return "2:30 till Dhuhr"
        
        val duration = Duration.between(currentTime, nextPrayer.second)
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        
        return when {
            hours > 0 -> "${hours}:${String.format("%02d", minutes)} till ${nextPrayer.first}"
            minutes > 0 -> "${minutes}m till ${nextPrayer.first}"
            else -> "Now"
        }
    }
    
    // Get prayer status
    fun getPrayerStatus(prayerName: String): String {
        val nextPrayer = getNextPrayer()
        val currentPrayer = getCurrentPrayer()
        
        return when {
            currentPrayer?.first == prayerName -> "Current"
            nextPrayer?.first == prayerName -> "Next"
            else -> "Upcoming"
        }
    }
    
    // Get prayer time display
    fun getPrayerTimeDisplay(prayerName: String): String {
        val times = prayerTimes ?: return "00:00 AM"
        
        val time = when (prayerName) {
            "Fajr" -> times.fajr
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }
        
        return time.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }
    
    // Use the app's standard background for consistency
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading) {
            // Loading state with Material 3 design
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header using Material 3 expressive typography
                Text(
                    text = "Daily Prayers",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Main prayer section - shows next prayer or current prayer with expressive shape
                val mainPrayer = getNextPrayer() ?: getCurrentPrayer()
                if (mainPrayer != null) {
                    // Layered background effect with expressive asymmetrical shape
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        // Background cream layer (peeking through) - positioned behind
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = 8.dp, y = 8.dp)
                                .zIndex(0f),
                            shape = RoundedCornerShape(
                                topStart = 32.dp,    // Large rounded corner
                                topEnd = 16.dp,      // Smaller rounded corner
                                bottomStart = 16.dp,  // Smaller rounded corner
                                bottomEnd = 32.dp     // Large rounded corner
                            ),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 4.dp
                        ) {}
                        
                        // Main prayer card with expressive asymmetrical shape - positioned on top
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f),
                            shape = RoundedCornerShape(
                                topStart = 40.dp,    // Very large rounded corner (organic)
                                topEnd = 20.dp,      // Smaller rounded corner
                                bottomStart = 20.dp,  // Smaller rounded corner
                                bottomEnd = 40.dp     // Very large rounded corner (organic)
                            ),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 12.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left side - Prayer info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Prayer name and status
                                    Column {
                                        Text(
                                            text = mainPrayer.first,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = getPrayerStatus(mainPrayer.first),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    // Prayer time
                                    Text(
                                        text = getPrayerTimeDisplay(mainPrayer.first),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    // Additional info for Fajr
                                    if (mainPrayer.first == "Fajr") {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Sunrise: ${prayerTimes?.sunrise?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: "06:30 AM"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                            Text(
                                                text = "Iqamah: ${prayerTimes?.fajr?.plusMinutes(20)?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: "05:35 AM"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                                
                                // Right side - Status and countdown
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Status indicator using Material 3 design
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = when (getPrayerStatus(mainPrayer.first)) {
                                            "Current" -> MaterialTheme.colorScheme.tertiary
                                            "Next" -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline
                                        },
                                        shadowElevation = 4.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val icon = when (getPrayerStatus(mainPrayer.first)) {
                                                "Current" -> Icons.Filled.RadioButtonUnchecked
                                                "Next" -> Icons.Filled.RadioButtonUnchecked
                                                else -> Icons.Filled.Check
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = getPrayerStatus(mainPrayer.first),
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    
                                    // Countdown timer with Material 3 design
                                    Surface(
                                        modifier = Modifier.size(88.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shadowElevation = 6.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            // Progress indicator
                                            CircularProgressIndicator(
                                                progress = { 0.7f },
                                                modifier = Modifier.size(80.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 4.dp
                                            )
                                            
                                            // Timer text
                                            Text(
                                                text = getTimeUntilNextPrayer(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Other prayer times using Material 3 design
                // First row: Dhuhr and Asr
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dhuhr prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Dhuhr")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "Dhuhr",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = when (getPrayerStatus("Dhuhr")) {
                                        "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                // Status indicator
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = when (getPrayerStatus("Dhuhr")) {
                                        "Current" -> MaterialTheme.colorScheme.tertiary
                                        "Next" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ) {}
                            }
                            Text(
                                text = getPrayerTimeDisplay("Dhuhr"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Dhuhr")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Asr prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Asr")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "Asr",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = when (getPrayerStatus("Asr")) {
                                        "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                // Status indicator
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = when (getPrayerStatus("Asr")) {
                                        "Current" -> MaterialTheme.colorScheme.tertiary
                                        "Next" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ) {}
                            }
                            Text(
                                text = getPrayerTimeDisplay("Asr"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Asr")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Second row: Maghrib and Isha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Maghrib prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Maghrib")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "Maghrib",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = when (getPrayerStatus("Maghrib")) {
                                        "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                // Status indicator
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = when (getPrayerStatus("Maghrib")) {
                                        "Current" -> MaterialTheme.colorScheme.tertiary
                                        "Next" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ) {}
                            }
                            Text(
                                text = getPrayerTimeDisplay("Maghrib"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Maghrib")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Isha prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Isha")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "Isha",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = when (getPrayerStatus("Isha")) {
                                        "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                // Status indicator
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = when (getPrayerStatus("Isha")) {
                                        "Current" -> MaterialTheme.colorScheme.tertiary
                                        "Next" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ) {}
                            }
                            Text(
                                text = getPrayerTimeDisplay("Isha"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Isha")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Location info using Material 3 design
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}