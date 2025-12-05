package com.starception.submission.islamic.salah.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.islamic.qibla.presentation.component.QiblaCompass
import com.starception.submission.islamic.qibla.presentation.component.QiblaGlobeView
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTime
import com.starception.submission.prayer.service.EnhancedLocationService
import com.starception.submission.prayer.viewmodel.PrayerTimesViewModel
import kotlinx.coroutines.launch

/**
 * Islamic Salah (Prayer) Dashboard Screen
 * 
 * Main screen for Islamic prayer times application displaying:
 * - Real-time prayer schedules with status
 * - Next prayer countdown and current prayer indicator
 * - Qibla direction compass for prayer orientation
 * - Location-based accurate prayer time calculations
 * - Islamic theming with Arabic prayer names
 * 
 * ## Features:
 * - **Real-time Updates**: Live prayer status and countdown
 * - **Qibla Compass**: Direction to Mecca for prayer
 * - **Location Services**: GPS-based precise calculations
 * - **Islamic Design**: Green color scheme, Arabic names, Islamic iconography
 * - **Material 3**: Modern, accessible design system
 * 
 * @param onSettingsClick Callback for settings navigation
 * @param locationService Enhanced location service for GPS functionality
 * @param viewModel Prayer times view model (injected via Hilt)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalahDashboard(
    onSettingsClick: () -> Unit = {},
    locationService: EnhancedLocationService? = null,
    viewModel: PrayerTimesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top app bar
        SalahTopBar(
            onSettingsClick = onSettingsClick,
            onRefreshClick = { viewModel.refresh() }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when {
            uiState.isLoading -> {
                SalahLoadingState()
            }
            uiState.prayerTimes != null -> {
                SalahContent(
                    dayPrayerTimes = uiState.prayerTimes!!,
                    locationService = locationService
                )
            }
            uiState.error != null -> {
                SalahErrorState(
                    message = uiState.error!!,
                    onRetryClick = { viewModel.refresh() }
                )
            }
        }
    }
}

/**
 * Top app bar with Islamic styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalahTopBar(
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with Islamic greeting
        Column {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "أوقات الصلاة", // "Prayer Times" in Arabic
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRefreshClick()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh prayer times",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsClick()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Main content with prayer times and Qibla compass
 */
@Composable
private fun SalahContent(
    dayPrayerTimes: DayPrayerTimes,
    locationService: EnhancedLocationService?
) {
    val actualPrayers = dayPrayerTimes.getActualPrayers()
    val nextPrayer = dayPrayerTimes.getNextPrayer()
    val currentPrayer = actualPrayers.find { it.isCurrently }
    val timeUntilNext = dayPrayerTimes.getTimeUntilNextPrayer()
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Next prayer card with Qibla compass
        item {
            NextPrayerCard(
                nextPrayer = nextPrayer,
                currentPrayer = currentPrayer,
                timeUntilNext = timeUntilNext ?: "",
                locationService = locationService
            )
        }

        // 3D Globe showing Qibla direction to Makkah
        item {
            QiblaGlobeCard(
                locationService = locationService
            )
        }

        // Prayer times list
        item {
            PrayerTimesSection(prayers = actualPrayers)
        }

        // Location info
        item {
            LocationCard(location = dayPrayerTimes.location)
        }
    }
}

/**
 * Next prayer card with Qibla compass
 */
@Composable
private fun NextPrayerCard(
    nextPrayer: PrayerTime?,
    currentPrayer: PrayerTime?,
    timeUntilNext: String,
    locationService: EnhancedLocationService?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Qibla compass
            QiblaCompass(
                progress = 0f,
                size = 120.dp,
                locationService = locationService
            )
            
            // Prayer info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (currentPrayer != null) {
                    Text(
                        text = "Current Prayer",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${currentPrayer.name} (${getArabicName(currentPrayer.name)})",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (nextPrayer != null) {
                    Text(
                        text = "Next Prayer",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${nextPrayer.name} (${getArabicName(nextPrayer.name)})",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "in $timeUntilNext",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Prayer times list section
 */
@Composable
private fun PrayerTimesSection(prayers: List<PrayerTime>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Today's Prayers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            prayers.forEach { prayer ->
                PrayerTimeRow(prayer = prayer)
                if (prayer != prayers.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

/**
 * Individual prayer time row
 */
@Composable
private fun PrayerTimeRow(prayer: PrayerTime) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = prayer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (prayer.isNext || prayer.isCurrently) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    prayer.isCurrently -> MaterialTheme.colorScheme.primary
                    prayer.isNext -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = getArabicName(prayer.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = prayer.time.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = when {
                    prayer.isCurrently -> MaterialTheme.colorScheme.primary
                    prayer.isNext -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            
            // Status indicator
            when {
                prayer.isCurrently -> {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text("NOW", fontSize = 10.sp)
                    }
                }
                prayer.isNext -> {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Text("NEXT", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/**
 * Location information card
 */
@Composable
private fun LocationCard(location: com.starception.submission.prayer.model.Location) {
    // Get location display name
    val locationName = location.getDisplayName()

    // Check if location name contains Arabic text (Unicode range 0600-06FF)
    val containsArabic = locationName.any { it in '\u0600'..'\u06FF' }

    // Get selected Arabic font from SharedPreferences if location name contains Arabic
    val context = androidx.compose.ui.platform.LocalContext.current
    val arabicFontFamily = if (containsArabic) {
        val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
        val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
        getArabicFontFamilyForLocationCard(selectedFont)
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = locationName,
                    style = if (containsArabic && arabicFontFamily != null) {
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = arabicFontFamily,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        )
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun getArabicFontFamilyForLocationCard(selectedFont: String): androidx.compose.ui.text.font.FontFamily {
    return when (selectedFont) {
        "pdms_saleem" -> com.starception.submission.core.designsystem.theme.QuranFonts.PDMSSaleem
        "noor_e_hidayat" -> com.starception.submission.core.designsystem.theme.QuranFonts.NoorEHidayat
        "thabit" -> com.starception.submission.core.designsystem.theme.QuranFonts.Thabit
        "uthmani_script" -> com.starception.submission.core.designsystem.theme.QuranFonts.UthmanicScript
        "indopak_script" -> com.starception.submission.core.designsystem.theme.QuranFonts.IndoPakScript
        else -> com.starception.submission.core.designsystem.theme.QuranFonts.PDMSSaleem
    }
}

/**
 * Loading state
 */
@Composable
private fun SalahLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Calculating prayer times...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state
 */
@Composable
private fun SalahErrorState(
    message: String,
    onRetryClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Prayer Times Unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

/**
 * Qibla Globe Card - 3D visualization of Qibla direction
 */
@Composable
private fun QiblaGlobeCard(
    locationService: EnhancedLocationService?
) {
    var userLocation by remember { mutableStateOf<android.location.Location?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Get user's current location
    LaunchedEffect(locationService) {
        locationService?.let { service ->
            coroutineScope.launch {
                service.getBestAvailableLocation().fold(
                    onSuccess = { location ->
                        userLocation = location
                    },
                    onFailure = {
                        // Handle error - location unavailable
                    }
                )
            }
        }
    }

    // Only show globe if we have user location
    if (userLocation != null) {
        QiblaGlobeView(
            userLatitude = userLocation!!.latitude,
            userLongitude = userLocation!!.longitude,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)  // Match the height we want for the tile
        )
    }
}

/**
 * Helper function to get Arabic name for prayer
 */
private fun getArabicName(prayerName: String): String {
    return when (prayerName) {
        "Fajr" -> "ٱلْفَجْر"
        "Sunrise" -> "ٱلشُّرُوق"
        "Dhuhr" -> "ٱلظُّهْر"
        "Asr" -> "ٱلْعَصْر"
        "Maghrib" -> "ٱلْمَغْرِب"
        "Isha" -> "ٱلْعِشَاء"
        else -> prayerName
    }
}