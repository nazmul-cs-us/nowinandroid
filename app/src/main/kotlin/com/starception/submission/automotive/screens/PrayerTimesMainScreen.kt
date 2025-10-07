package com.starception.submission.automotive.screens

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
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.DistanceSpan
import androidx.lifecycle.lifecycleScope
import com.starception.submission.R
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTime
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.model.AsrMadhhab
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Main Android Auto screen displaying prayer times
 * 
 * Shows current prayer times in a car-optimized interface
 * with large text and simple navigation suitable for driving.
 */
@AndroidEntryPoint
class PrayerTimesMainScreen(carContext: CarContext) : Screen(carContext) {

    @Inject
    lateinit var prayerCalculatorService: PrayerTimeCalculatorService

    private val _prayerTimes = MutableStateFlow<DayPrayerTimes?>(null)
    private val prayerTimes: StateFlow<DayPrayerTimes?> = _prayerTimes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    private val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        lifecycle.addObserver(this)
        loadPrayerTimes()
    }

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        if (isLoading.value) {
            // Enhanced loading state with icon
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(createStyledText("🕌 Loading Prayer Times...", CarColor.PRIMARY))
                    .addText(createStyledText("Please wait while we calculate precise prayer times", CarColor.SECONDARY))
                    .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.PRIMARY).build())
                    .build()
            )
        } else {
            prayerTimes.value?.let { dayPrayerTimes ->
                val actualPrayers = dayPrayerTimes.getActualPrayers()
                val nextPrayer = dayPrayerTimes.getNextPrayer()
                val timeUntilNext = dayPrayerTimes.getTimeUntilNextPrayer()

                // Enhanced countdown section with prominent styling
                nextPrayer?.let { prayer ->
                    val countdownText = timeUntilNext ?: "Loading..."
                    itemListBuilder.addItem(
                        Row.Builder()
                            .setTitle(createStyledText("⏰ Next: ${prayer.name}", CarColor.PRIMARY))
                            .addText(createStyledText("in $countdownText", CarColor.GREEN))
                            .setImage(
                                CarIcon.Builder(getEnhancedPrayerIcon(prayer.name))
                                    .setTint(CarColor.PRIMARY)
                                    .build()
                            )
                            .build()
                    )
                    
                    // Add separator
                    itemListBuilder.addItem(
                        Row.Builder()
                            .setTitle("─────────────────────")
                            .build()
                    )
                }

                // Enhanced prayer times with rich visual indicators
                actualPrayers.forEach { prayerTime ->
                    val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
                    val formattedTime = prayerTime.time.format(timeFormat)
                    
                    val (titleColor, statusEmoji, statusColor) = when {
                        prayerTime.isCurrently -> Triple(CarColor.GREEN, "🔔", CarColor.GREEN)
                        prayerTime.isNext -> Triple(CarColor.BLUE, "⏳", CarColor.BLUE)
                        else -> Triple(CarColor.DEFAULT, "🕐", CarColor.SECONDARY)
                    }
                    
                    val statusText = when {
                        prayerTime.isCurrently -> "$statusEmoji Current Prayer Time"
                        prayerTime.isNext -> "$statusEmoji Coming Up Next"
                        else -> "Prayer Time"
                    }
                    
                    itemListBuilder.addItem(
                        Row.Builder()
                            .setTitle(createStyledText("${getPrayerEmoji(prayerTime.name)} ${prayerTime.name}", titleColor))
                            .addText(createStyledText(formattedTime, CarColor.PRIMARY))
                            .addText(createStyledText(statusText, statusColor))
                            .setImage(
                                CarIcon.Builder(getEnhancedPrayerIcon(prayerTime.name))
                                    .setTint(titleColor)
                                    .build()
                            )
                            .build()
                    )
                }
                
                // Add location and method info at bottom
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("─────────────────────")
                        .build()
                )
                
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("📍 Location: New York, USA", CarColor.SECONDARY))
                        .addText(createStyledText("📐 Method: ISNA (Islamic Society of North America)", CarColor.SECONDARY))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.SECONDARY).build())
                        .build()
                )
                
            } ?: run {
                // Enhanced error state
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("⚠️ Prayer Times Unavailable", CarColor.RED))
                        .addText(createStyledText("Please check location permissions", CarColor.SECONDARY))
                        .addText(createStyledText("Tap refresh to try again", CarColor.SECONDARY))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.RED).build())
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle(createStyledText("🕌 Islamic Prayer Times", CarColor.PRIMARY))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("🔄 Refresh")
                            .setOnClickListener { loadPrayerTimes() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("🧭 Qibla")
                            .setOnClickListener { 
                                screenManager.push(QiblaCompassCarScreen(carContext))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun loadPrayerTimes() {
        lifecycleScope.launch {
            try {
                _isLoading.value = true
                
                // Use default location for demonstration (can be enhanced with actual location service)
                val defaultLocation = Location(
                    latitude = 40.7128, // New York as default
                    longitude = -74.0060,
                    timezone = java.util.TimeZone.getDefault().id,
                    cityName = "New York",
                    countryName = "United States"
                )
                
                val date = LocalDate.now()
                
                // Create default prayer settings for Android Auto
                val defaultSettings = PrayerSettings(
                    calculationMethod = CalculationMethod.ISNA,
                    asrMadhhab = AsrMadhhab.STANDARD,
                    location = defaultLocation
                )
                
                val dayPrayerTimes = prayerCalculatorService.calculatePrayerTimes(
                    date = date,
                    location = defaultLocation,
                    settings = defaultSettings
                )
                
                _prayerTimes.value = dayPrayerTimes
                _isLoading.value = false
                invalidate() // Refresh the UI
                
            } catch (e: Exception) {
                android.util.Log.e("PrayerTimesCarScreen", "Error loading prayer times", e)
                _isLoading.value = false
                invalidate()
            }
        }
    }

    /**
     * Create styled text with color for Android Auto
     */
    private fun createStyledText(text: String, color: CarColor): CarText {
        return CarText.Builder(text)
            .addSpan(ForegroundCarColorSpan.create(color), 0, text.length)
            .build()
    }

    /**
     * Get prayer-specific emoji for visual enhancement
     */
    private fun getPrayerEmoji(prayerName: String): String {
        return when (prayerName.lowercase()) {
            "fajr" -> "🌅"      // Dawn/Sunrise
            "dhuhr" -> "☀️"     // Sun/Noon
            "asr" -> "🌇"       // Afternoon
            "maghrib" -> "🌆"   // Sunset
            "isha" -> "🌙"      // Night/Moon
            else -> "🕐"        // Default clock
        }
    }

    /**
     * Get enhanced prayer icons (using existing icon but allowing future customization)
     */
    private fun getEnhancedPrayerIcon(prayerName: String): Int {
        return when (prayerName.lowercase()) {
            "fajr" -> R.drawable.ic_prayer          // Could be dawn-specific icon
            "dhuhr" -> R.drawable.ic_prayer         // Could be sun icon
            "asr" -> R.drawable.ic_prayer           // Could be afternoon icon
            "maghrib" -> R.drawable.ic_prayer       // Could be sunset icon
            "isha" -> R.drawable.ic_prayer          // Could be moon icon
            else -> R.drawable.ic_prayer
        }
    }

    /**
     * Get basic prayer icon (legacy support)
     */
    private fun getPrayerIcon(prayerName: String): Int {
        return getEnhancedPrayerIcon(prayerName)
    }
}