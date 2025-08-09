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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header with location and next prayer info
            PrayerTimesHeader(
                location = prayerTimes.location,
                nextPrayer = prayerTimes.getNextPrayer(),
                timeUntilNext = timeUntilNext
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Prayer times list
            PrayerTimesList(prayers = prayerTimes.getAllPrayers())
        }
    }
}

@Composable
private fun PrayerTimesHeader(
    location: Location,
    nextPrayer: PrayerTime?,
    timeUntilNext: String?,
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = location.getDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Next prayer info
        if (nextPrayer != null && timeUntilNext != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Next Prayer",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Next: ${nextPrayer.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "in $timeUntilNext",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = nextPrayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    prayer.isCurrently -> MaterialTheme.colorScheme.tertiaryContainer
                    prayer.isNext -> MaterialTheme.colorScheme.secondaryContainer
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
                prayer.isCurrently -> MaterialTheme.colorScheme.onTertiaryContainer
                prayer.isNext -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        
        if (prayer.isCurrently) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.tertiary,
                        RoundedCornerShape(4.dp)
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
                prayer.isCurrently -> MaterialTheme.colorScheme.onTertiaryContainer
                prayer.isNext -> MaterialTheme.colorScheme.onSecondaryContainer
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
            latitude = 25.2048,
            longitude = 55.2708,
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
            modifier = Modifier.padding(16.dp)
        )
    }
}