package com.starception.submission.automotive

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.starception.submission.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Android Auto screen displaying Islamic prayer times
 * 
 * Optimized for car displays with simple, distraction-free interface
 */
class PrayerTimesMainScreen(carContext: CarContext) : Screen(carContext) {
    
    // Create a supervised scope for this screen
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Simple data class for prayer times
    data class SimplePrayerTime(
        val name: String,
        val time: LocalTime,
        val isNext: Boolean = false,
        val isCurrent: Boolean = false
    )

    private val _prayerTimes = MutableStateFlow<List<SimplePrayerTime>>(emptyList())
    private val prayerTimes: StateFlow<List<SimplePrayerTime>> = _prayerTimes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    private val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSimplePrayerTimes()
    }

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        if (isLoading.value) {
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("🕌 Loading Prayer Times...")
                    .addText("Calculating precise Islamic prayer times")
                    .build()
            )
        } else {
            val prayers = prayerTimes.value
            val nextPrayer = prayers.find { it.isNext }
            val currentPrayer = prayers.find { it.isCurrent }

            // Add next prayer countdown
            nextPrayer?.let { prayer ->
                val timeUntilNext = calculateTimeUntil(prayer.time)
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("⏰ Next: ${prayer.name}")
                        .addText("in $timeUntilNext")
                        .build()
                )
                
                // Add separator
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("─────────────────────")
                        .build()
                )
            }

            // Add all prayer times with status
            prayers.forEach { prayer ->
                val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
                val formattedTime = prayer.time.format(timeFormat)
                
                val (titleColor, statusEmoji, statusText) = when {
                    prayer.isCurrent -> Triple(CarColor.GREEN, "🔔", "Current Prayer Time")
                    prayer.isNext -> Triple(CarColor.BLUE, "⏳", "Coming Up Next")
                    else -> Triple(CarColor.DEFAULT, getPrayerEmoji(prayer.name), "Prayer Time")
                }
                
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("${getPrayerEmoji(prayer.name)} ${prayer.name}")
                        .addText(formattedTime)
                        .addText("$statusEmoji $statusText")
                        .build()
                )
            }
            
            // Add location info
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("─────────────────────")
                    .build()
            )
            
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("📍 Location: New York, USA")
                    .addText("📐 Method: ISNA (Islamic Society of North America)")
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle("🕌 Islamic Prayer Times")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("🔄 Refresh")
                            .setOnClickListener { loadSimplePrayerTimes() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun loadSimplePrayerTimes() {
        screenScope.launch {
            _isLoading.value = true
            
            // Simulate loading delay
            kotlinx.coroutines.delay(1000)
            
            // Create sample prayer times for demonstration
            val now = LocalTime.now()
            val samplePrayers = listOf(
                SimplePrayerTime("Fajr", LocalTime.of(5, 30)),
                SimplePrayerTime("Dhuhr", LocalTime.of(12, 15)),
                SimplePrayerTime("Asr", LocalTime.of(15, 30)),
                SimplePrayerTime("Maghrib", LocalTime.of(18, 45)),
                SimplePrayerTime("Isha", LocalTime.of(20, 30))
            )
            
            // Determine current and next prayers
            val nextPrayerIndex = samplePrayers.indexOfFirst { it.time.isAfter(now) }
            val prayers = samplePrayers.mapIndexed { index, prayer ->
                val isNext = index == nextPrayerIndex || (nextPrayerIndex == -1 && index == 0)
                val isCurrent = if (index < samplePrayers.size - 1) {
                    now.isAfter(prayer.time) && now.isBefore(samplePrayers[index + 1].time)
                } else {
                    now.isAfter(prayer.time) && now.isBefore(prayer.time.plusHours(2))
                }
                
                prayer.copy(isNext = isNext, isCurrent = isCurrent)
            }
            
            _prayerTimes.value = prayers
            _isLoading.value = false
            invalidate()
        }
    }

    private fun calculateTimeUntil(prayerTime: LocalTime): String {
        val now = LocalTime.now()
        
        return if (prayerTime.isAfter(now)) {
            val duration = java.time.Duration.between(now, prayerTime)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()
            
            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        } else {
            // Next day
            val duration = java.time.Duration.between(now, LocalTime.MAX) + 
                         java.time.Duration.between(LocalTime.MIN, prayerTime)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()
            
            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        }
    }

    // Removed styled text for simplicity

    private fun getPrayerEmoji(prayerName: String): String {
        return when (prayerName.lowercase()) {
            "fajr" -> "🌅"      // Dawn
            "dhuhr" -> "☀️"     // Sun
            "asr" -> "🌇"       // Afternoon
            "maghrib" -> "🌆"   // Sunset
            "isha" -> "🌙"      // Night
            else -> "🕐"        // Clock
        }
    }
}