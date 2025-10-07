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
import androidx.lifecycle.lifecycleScope
import com.starception.submission.R
import com.starception.submission.prayer.model.Location
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Android Auto Qibla compass screen
 * 
 * Provides Qibla direction information in a car-safe interface
 * with clear directional guidance for prayers.
 */
@AndroidEntryPoint
class QiblaCompassCarScreen(carContext: CarContext) : Screen(carContext) {

    private val _qiblaDirection = MutableStateFlow<QiblaInfo?>(null)
    private val qiblaDirection: StateFlow<QiblaInfo?> = _qiblaDirection.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    private val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Kaaba coordinates
    private val kaabaLatitude = 21.4225
    private val kaabaLongitude = 39.8262

    data class QiblaInfo(
        val direction: Double,       // Degrees from North
        val distance: Double,        // Distance in KM
        val compassDirection: String, // N, NE, E, SE, S, SW, W, NW
        val locationName: String
    )

    init {
        lifecycle.addObserver(this)
        calculateQiblaDirection()
    }

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        if (isLoading.value) {
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(createStyledText("🧭 Calculating Qibla Direction...", CarColor.PRIMARY))
                    .addText(createStyledText("Determining direction to Makkah", CarColor.SECONDARY))
                    .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.PRIMARY).build())
                    .build()
            )
        } else {
            qiblaDirection.value?.let { qibla ->
                // Main Qibla direction info
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("🕋 Qibla Direction", CarColor.PRIMARY))
                        .addText(createStyledText("${qibla.compassDirection} (${qibla.direction.toInt()}°)", CarColor.GREEN))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.PRIMARY).build())
                        .build()
                )

                // Distance to Makkah
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("📏 Distance to Makkah", CarColor.BLUE))
                        .addText(createStyledText("${String.format("%.0f", qibla.distance)} km", CarColor.BLUE))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.BLUE).build())
                        .build()
                )

                // Separator
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("─────────────────────")
                        .build()
                )

                // Directional guidance
                val guidance = getDirectionalGuidance(qibla.direction)
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("🧭 Compass Guidance", CarColor.SECONDARY))
                        .addText(createStyledText(guidance, CarColor.PRIMARY))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.SECONDARY).build())
                        .build()
                )

                // Prayer reminder
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("🤲 Prayer Direction", CarColor.GREEN))
                        .addText(createStyledText("Face this direction during Salah", CarColor.GREEN))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.GREEN).build())
                        .build()
                )

                // Location info
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("─────────────────────")
                        .build()
                )

                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("📍 Your Location", CarColor.SECONDARY))
                        .addText(createStyledText(qibla.locationName, CarColor.SECONDARY))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.SECONDARY).build())
                        .build()
                )

            } ?: run {
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle(createStyledText("⚠️ Qibla Direction Unavailable", CarColor.RED))
                        .addText(createStyledText("Unable to calculate direction", CarColor.SECONDARY))
                        .setImage(CarIcon.Builder(R.drawable.ic_prayer).setTint(CarColor.RED).build())
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle(createStyledText("🧭 Qibla Compass", CarColor.PRIMARY))
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("🔄 Refresh")
                            .setOnClickListener { calculateQiblaDirection() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("🏠 Prayer Times")
                            .setOnClickListener { 
                                screenManager.pop()
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun calculateQiblaDirection() {
        lifecycleScope.launch {
            try {
                _isLoading.value = true

                // Use default location (can be enhanced with actual location)
                val userLatitude = 40.7128  // New York
                val userLongitude = -74.0060

                val direction = calculateBearing(
                    userLatitude, userLongitude,
                    kaabaLatitude, kaabaLongitude
                )

                val distance = calculateDistance(
                    userLatitude, userLongitude,
                    kaabaLatitude, kaabaLongitude
                )

                val compassDirection = getCompassDirection(direction)

                _qiblaDirection.value = QiblaInfo(
                    direction = direction,
                    distance = distance,
                    compassDirection = compassDirection,
                    locationName = "New York, USA"
                )

                _isLoading.value = false
                invalidate()

            } catch (e: Exception) {
                android.util.Log.e("QiblaCompassCarScreen", "Error calculating Qibla", e)
                _isLoading.value = false
                invalidate()
            }
        }
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val x = sin(deltaLonRad) * cos(lat2Rad)
        val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)

        val bearingRad = atan2(x, y)
        val bearingDeg = Math.toDegrees(bearingRad)

        return (bearingDeg + 360) % 360
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun getCompassDirection(bearing: Double): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "North"
            bearing < 67.5 -> "Northeast"
            bearing < 112.5 -> "East"
            bearing < 157.5 -> "Southeast"
            bearing < 202.5 -> "South"
            bearing < 247.5 -> "Southwest"
            bearing < 292.5 -> "West"
            bearing < 337.5 -> "Northwest"
            else -> "North"
        }
    }

    private fun getDirectionalGuidance(bearing: Double): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "Turn to face North"
            bearing < 67.5 -> "Turn to face Northeast"
            bearing < 112.5 -> "Turn to face East"
            bearing < 157.5 -> "Turn to face Southeast"
            bearing < 202.5 -> "Turn to face South"
            bearing < 247.5 -> "Turn to face Southwest"
            bearing < 292.5 -> "Turn to face West"
            bearing < 337.5 -> "Turn to face Northwest"
            else -> "Turn to face North"
        }
    }

    private fun createStyledText(text: String, color: CarColor): CarText {
        return CarText.Builder(text)
            .addSpan(ForegroundCarColorSpan.create(color), 0, text.length)
            .build()
    }
}