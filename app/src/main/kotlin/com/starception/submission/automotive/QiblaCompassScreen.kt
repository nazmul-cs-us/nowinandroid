package com.starception.submission.automotive

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Auto Qibla compass screen
 *
 * Shows the direction to the Holy Kaaba in Makkah from the user's current location.
 * Uses the app's existing location data to calculate accurate Qibla direction.
 *
 * Features:
 * - Qibla direction in degrees and compass direction (N, NE, E, etc.)
 * - Distance to Makkah in kilometers
 * - Turn direction guidance
 * - Current location display
 */
class QiblaCompassScreen(
    carContext: CarContext,
    private val dataProvider: AutomotivePrayerDataProvider
) : Screen(carContext) {

    companion object {
        private const val TAG = "QiblaCompassScreen"
    }

    // Coroutine scope for async data loading
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current Qibla data state
    private var qiblaInfo: AutomotivePrayerDataProvider.QiblaInfo? = null
    private var isLoading = true
    private var errorMessage: String? = null

    init {
        loadQiblaDirection()
    }

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        when {
            isLoading -> {
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("🧭 Calculating Qibla Direction...")
                        .addText("Determining direction to Holy Kaaba in Makkah")
                        .build()
                )
            }

            errorMessage != null -> {
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("⚠️ Unable to Calculate Qibla")
                        .addText(errorMessage ?: "Unknown error")
                        .build()
                )
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("Tap Refresh to try again")
                        .build()
                )
            }

            qiblaInfo != null -> {
                buildQiblaInfoList(itemListBuilder, qiblaInfo!!)
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
                            .setOnClickListener { loadQiblaDirection() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildQiblaInfoList(
        builder: ItemList.Builder,
        qibla: AutomotivePrayerDataProvider.QiblaInfo
    ) {
        // Check if we have valid location
        if (qibla.latitude == 0.0 && qibla.longitude == 0.0) {
            builder.addItem(
                Row.Builder()
                    .setTitle("📍 Location Required")
                    .addText("Please ensure location is enabled in the main app")
                    .build()
            )
            return
        }

        // Main Qibla direction
        builder.addItem(
            Row.Builder()
                .setTitle("🕋 Qibla Direction")
                .addText("${qibla.compassDirection} (${qibla.direction.toInt()}°)")
                .build()
        )

        // Distance to Makkah
        builder.addItem(
            Row.Builder()
                .setTitle("📏 Distance to Makkah")
                .addText("${String.format("%.0f", qibla.distance)} km")
                .build()
        )

        // Directional guidance
        val guidance = getDirectionalGuidance(qibla.direction)
        builder.addItem(
            Row.Builder()
                .setTitle("🧭 Turn Direction")
                .addText(guidance)
                .build()
        )

        // Prayer reminder
        builder.addItem(
            Row.Builder()
                .setTitle("🤲 For Prayer")
                .addText("Face this direction during Salah")
                .build()
        )

        // Separator
        builder.addItem(
            Row.Builder()
                .setTitle("─────────────────────")
                .build()
        )

        // Location info
        builder.addItem(
            Row.Builder()
                .setTitle("📍 Your Location")
                .addText(qibla.locationName)
                .build()
        )

        // Coordinates (for reference)
        builder.addItem(
            Row.Builder()
                .setTitle("🌐 Coordinates")
                .addText("${String.format("%.4f", qibla.latitude)}°, ${String.format("%.4f", qibla.longitude)}°")
                .build()
        )
    }

    private fun loadQiblaDirection() {
        Log.i(TAG, "🧭 Loading Qibla direction for Android Auto")
        isLoading = true
        errorMessage = null
        invalidate()

        screenScope.launch {
            try {
                val info = dataProvider.getQiblaInfo()
                qiblaInfo = info
                isLoading = false
                errorMessage = null

                Log.i(TAG, "✅ Qibla calculated: ${info.direction.toInt()}° ${info.compassDirection}")
                Log.i(TAG, "📍 Location: ${info.locationName}")
                Log.i(TAG, "📏 Distance: ${info.distance.toInt()} km")

                invalidate()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error calculating Qibla direction", e)
                isLoading = false
                errorMessage = "Could not calculate Qibla direction"
                invalidate()
            }
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

}
