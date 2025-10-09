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
import androidx.lifecycle.lifecycleScope
import com.starception.submission.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Simple Android Auto Qibla compass screen
 */
class QiblaCompassScreen(carContext: CarContext) : Screen(carContext) {

    data class QiblaInfo(
        val direction: Double,
        val distance: Double,
        val compassDirection: String,
        val locationName: String
    )

    private val _qiblaDirection = MutableStateFlow<QiblaInfo?>(null)
    private val qiblaDirection: StateFlow<QiblaInfo?> = _qiblaDirection.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    private val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Kaaba coordinates
    private val kaabaLatitude = 21.4225
    private val kaabaLongitude = 39.8262

    init {
        calculateQiblaDirection()
    }

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        if (isLoading.value) {
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("🧭 Calculating Qibla Direction...")
                    .addText("Determining direction to Holy Kaaba in Makkah")
                    .build()
            )
        } else {
            qiblaDirection.value?.let { qibla ->
                // Main Qibla direction
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("🕋 Qibla Direction")
                        .addText("${qibla.compassDirection} (${qibla.direction.toInt()}°)")
                        .build()
                )

                // Distance to Makkah
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("📏 Distance to Makkah")
                        .addText("${String.format("%.0f", qibla.distance)} km")
                        .build()
                )

                // Directional guidance
                val guidance = getDirectionalGuidance(qibla.direction)
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("🧭 Turn Direction")
                        .addText(guidance)
                        .build()
                )

                // Prayer reminder
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("🤲 For Prayer")
                        .addText("Face this direction during Salah")
                        .build()
                )

                // Location info
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("📍 Your Location")
                        .addText(qibla.locationName)
                        .build()
                )

            } ?: run {
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("⚠️ Qibla Direction Unavailable")
                        .addText("Unable to calculate direction to Makkah")
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle("🧭 Qibla Compass")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("🔄 Refresh")
                            .setOnClickListener { calculateQiblaDirection() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun calculateQiblaDirection() {
        lifecycleScope.launch {
            _isLoading.value = true
            
            kotlinx.coroutines.delay(500) // Simulate calculation
            
            // Use sample location (New York)
            val userLatitude = 40.7128
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

    // Removed styled text for simplicity
}