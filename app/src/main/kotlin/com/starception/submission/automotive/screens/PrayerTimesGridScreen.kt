package com.starception.submission.automotive.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarColor
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.GridItem
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.lifecycle.lifecycleScope
import com.starception.submission.R
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.model.DayPrayerTimes
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Grid-based Android Auto screen for prayer times
 * 
 * Provides a more visual card-based layout for prayer times
 * using Android Auto's Grid Template.
 */
@AndroidEntryPoint
class PrayerTimesGridScreen(carContext: CarContext) : Screen(carContext) {

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
        val gridItemsBuilder = ItemList.Builder()

        if (isLoading.value) {
            // Loading state with single card
            gridItemsBuilder.addItem(
                GridItem.Builder()
                    .setTitle(createStyledText("Loading...", CarColor.PRIMARY))
                    .setText(createStyledText("Please wait", CarColor.SECONDARY))
                    .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.PRIMARY).build())
                    .build()
            )
        } else {
            prayerTimes.value?.let { dayPrayerTimes ->
                val actualPrayers = dayPrayerTimes.getActualPrayers()
                val nextPrayer = dayPrayerTimes.getNextPrayer()
                val timeUntilNext = dayPrayerTimes.getTimeUntilNextPrayer()

                // Add next prayer as prominent first card
                nextPrayer?.let { prayer ->
                    gridItemsBuilder.addItem(
                        GridItem.Builder()
                            .setTitle(createStyledText("⏰ NEXT", CarColor.GREEN))
                            .setText(createStyledText("${prayer.name}", CarColor.PRIMARY))
                            .setImage(CarIcon.Builder(getEnhancedPrayerIcon(prayer.name)).setTint(CarColor.GREEN).build())
                            .setOnClickListener { 
                                screenManager.push(PrayerTimesMainScreen(carContext))
                            }
                            .build()
                    )
                }

                // Add each prayer as a grid card
                actualPrayers.forEach { prayerTime ->
                    val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
                    val formattedTime = prayerTime.time.format(timeFormat)
                    
                    val (titleColor, backgroundColor) = when {
                        prayerTime.isCurrently -> Pair(CarColor.GREEN, CarColor.GREEN)
                        prayerTime.isNext -> Pair(CarColor.BLUE, CarColor.BLUE)
                        else -> Pair(CarColor.DEFAULT, CarColor.SECONDARY)
                    }
                    
                    val statusText = when {
                        prayerTime.isCurrently -> "CURRENT"
                        prayerTime.isNext -> "NEXT"
                        else -> "PRAYER"
                    }
                    
                    gridItemsBuilder.addItem(
                        GridItem.Builder()
                            .setTitle(createStyledText("${getPrayerEmoji(prayerTime.name)} ${prayerTime.name}", titleColor))
                            .setText(createStyledText(formattedTime, CarColor.PRIMARY))
                            .setImage(CarIcon.Builder(getEnhancedPrayerIcon(prayerTime.name)).setTint(titleColor).build())
                            .setOnClickListener {
                                // Could show prayer details in future
                                android.util.Log.d("AndroidAuto", "Prayer ${prayerTime.name} selected")
                            }
                            .build()
                    )
                }

                // Add Qibla compass as a card
                gridItemsBuilder.addItem(
                    GridItem.Builder()
                        .setTitle(createStyledText("🧭 Qibla", CarColor.BLUE))
                        .setText(createStyledText("Compass", CarColor.SECONDARY))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.BLUE).build())
                        .setOnClickListener {
                            screenManager.push(QiblaCompassCarScreen(carContext))
                        }
                        .build()
                )

            } ?: run {
                // Error state
                gridItemsBuilder.addItem(
                    GridItem.Builder()
                        .setTitle(createStyledText("⚠️ Error", CarColor.RED))
                        .setText(createStyledText("Try refresh", CarColor.SECONDARY))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.RED).build())
                        .setOnClickListener { loadPrayerTimes() }
                        .build()
                )
            }
        }

        return GridTemplate.Builder()
            .setSingleList(gridItemsBuilder.build())
            .setTitle(createStyledText("🕌 Prayer Times", CarColor.PRIMARY))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("📋 List View")
                            .setOnClickListener { 
                                screenManager.pushForResult(
                                    PrayerTimesMainScreen(carContext)
                                ) { /* Handle result if needed */ }
                            }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("🔄 Refresh")
                            .setOnClickListener { loadPrayerTimes() }
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
                
                // Use default location for demonstration
                val defaultLocation = Location(
                    latitude = 40.7128,
                    longitude = -74.0060,
                    timezone = java.util.TimeZone.getDefault().id,
                    cityName = "New York",
                    countryName = "United States"
                )
                
                val date = LocalDate.now()
                
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
                invalidate()
                
            } catch (e: Exception) {
                android.util.Log.e("PrayerTimesGridScreen", "Error loading prayer times", e)
                _isLoading.value = false
                invalidate()
            }
        }
    }

    private fun createStyledText(text: String, color: CarColor): CarText {
        return CarText.Builder(text)
            .addSpan(ForegroundCarColorSpan.create(color), 0, text.length)
            .build()
    }

    private fun getPrayerEmoji(prayerName: String): String {
        return when (prayerName.lowercase()) {
            "fajr" -> "🌅"
            "dhuhr" -> "☀️"
            "asr" -> "🌇"
            "maghrib" -> "🌆"
            "isha" -> "🌙"
            else -> "🕐"
        }
    }

    private fun getEnhancedPrayerIcon(prayerName: String): Int {
        return when (prayerName.lowercase()) {
            "fajr" -> R.drawable.ic_prayer
            "dhuhr" -> R.drawable.ic_prayer
            "asr" -> R.drawable.ic_prayer
            "maghrib" -> R.drawable.ic_prayer
            "isha" -> R.drawable.ic_prayer
            else -> R.drawable.ic_prayer
        }
    }
}