package com.starception.dua.prayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.starception.dua.core.designsystem.theme.NiaTheme
import com.starception.dua.prayer.model.CalculationMethod
import com.starception.dua.prayer.model.DayPrayerTimes
import com.starception.dua.prayer.model.Location
import com.starception.dua.prayer.model.PrayerTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Prayer times display card component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesCard(
    prayerTimes: DayPrayerTimes,
    timeUntilNext: String? = null,
    calculationMethod: CalculationMethod? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with location and next prayer info
            PrayerTimesHeader(
                prayerTimes = prayerTimes,
                location = prayerTimes.location,
                nextPrayer = prayerTimes.getNextPrayer(),
                timeUntilNext = timeUntilNext,
                calculationMethod = calculationMethod
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Prayer times list
            PrayerTimesList(prayers = prayerTimes.getAllPrayers())
        }
    }
}

@Composable
private fun PrayerTimesHeader(
    prayerTimes: DayPrayerTimes,
    location: Location,
    nextPrayer: PrayerTime?,
    timeUntilNext: String?,
    calculationMethod: CalculationMethod?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Location info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = location.getDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Calculation method info
        calculationMethod?.let { method ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Using ${method.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Next prayer info or last prayer info
        if (timeUntilNext != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = if (nextPrayer != null) "Next Prayer" else "Last Prayer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    if (nextPrayer != null) {
                        // Show next prayer
                        val isNextPrayerToday = prayerTimes.getAllPrayers().any { it.time.isAfter(LocalTime.now()) }
                        val displayText = if (isNextPrayerToday) {
                            "Next: ${nextPrayer.name}"
                        } else {
                            "Next: ${nextPrayer.name} (tomorrow)"
                        }
                        
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "in $timeUntilNext",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // This case should not happen anymore as we always have a next prayer (including tomorrow's Fajr)
                        Text(
                            text = "Prayer times calculated",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (nextPrayer != null) {
                    Text(
                        text = nextPrayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerTimesList(
    prayers: List<PrayerTime>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        prayers.forEach { prayer ->
            PrayerTimeItem(
                prayer = prayer,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PrayerTimeItem(
    prayer: PrayerTime,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    prayer.isCurrently -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    prayer.isNext -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prayer.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (prayer.isNext || prayer.isCurrently) FontWeight.Medium else FontWeight.Normal,
            color = when {
                prayer.isCurrently -> MaterialTheme.colorScheme.primary
                prayer.isNext -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        
        if (prayer.isCurrently) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(
            text = prayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (prayer.isNext || prayer.isCurrently) FontWeight.Medium else FontWeight.Normal,
            color = when {
                prayer.isCurrently -> MaterialTheme.colorScheme.primary
                prayer.isNext -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End
        )
    }
}

@Preview
@Composable
private fun PrayerTimesCardPreview() {
    NiaTheme {
        val sampleLocation = Location(
            latitude = 25.276987,
            longitude = 55.296249,
            timeZoneOffset = 4.0,
            city = "Dubai",
            country = "UAE"
        )
        
        val samplePrayerTimes = DayPrayerTimes(
            date = java.time.LocalDate.now().atStartOfDay(),
            fajr = LocalTime.of(5, 15),
            sunrise = LocalTime.of(6, 30),
            dhuhr = LocalTime.of(12, 15),
            asr = LocalTime.of(15, 45),
            maghrib = LocalTime.of(18, 20),
            isha = LocalTime.of(19, 50),
            location = sampleLocation
        )
        
        PrayerTimesCard(
            prayerTimes = samplePrayerTimes,
            timeUntilNext = "2h 45m",
            calculationMethod = CalculationMethod.UMM_AL_QURA,
            modifier = Modifier.padding(16.dp)
        )
    }
}