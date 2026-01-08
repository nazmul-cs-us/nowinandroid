package com.starception.submission.automotive

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
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
import java.time.format.DateTimeFormatter

/**
 * Android Auto screen displaying Islamic prayer times
 *
 * Shows real prayer times calculated using the app's existing infrastructure.
 * Features:
 * - All 5 daily prayers with times
 * - Next prayer countdown
 * - Current/next prayer indicators
 * - Location and calculation method info
 * - Navigation to Qibla compass screen
 */
class PrayerTimesMainScreen(
    carContext: CarContext,
    private val dataProvider: AutomotivePrayerDataProvider
) : Screen(carContext) {

    companion object {
        private const val TAG = "PrayerTimesMainScreen"
    }

    // Coroutine scope for async data loading
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current prayer data state
    private var prayerData: AutomotivePrayerDataProvider.AutomotivePrayerData? = null
    private var isLoading = true
    private var errorMessage: String? = null

    init {
        loadPrayerTimes()
    }

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        when {
            isLoading -> {
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("🕌 Loading Prayer Times...")
                        .addText("Fetching accurate Islamic prayer times")
                        .build()
                )
            }

            errorMessage != null -> {
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("⚠️ Unable to Load Prayer Times")
                        .addText(errorMessage ?: "Unknown error")
                        .build()
                )
                itemListBuilder.addItem(
                    Row.Builder()
                        .setTitle("Tap Refresh to try again")
                        .build()
                )
            }

            prayerData != null -> {
                buildPrayerTimesList(itemListBuilder, prayerData!!)
            }
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setTitle("🕌 Islamic Prayer Times")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("🧭 Qibla")
                            .setOnClickListener { navigateToQibla() }
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

    private fun buildPrayerTimesList(
        builder: ItemList.Builder,
        data: AutomotivePrayerDataProvider.AutomotivePrayerData
    ) {
        val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

        // Next prayer countdown header
        val nextPrayer = data.prayerTimes.find { it.isNext }
        if (nextPrayer != null && data.nextPrayerCountdown != null) {
            builder.addItem(
                Row.Builder()
                    .setTitle("⏰ Next: ${nextPrayer.name}")
                    .addText("in ${data.nextPrayerCountdown}")
                    .build()
            )

            // Separator
            builder.addItem(
                Row.Builder()
                    .setTitle("─────────────────────")
                    .build()
            )
        }

        // All prayer times with status
        data.prayerTimes.forEach { prayer ->
            val formattedTime = prayer.time.format(timeFormat)

            val (statusEmoji, statusText) = when {
                prayer.isCurrent -> "🔔" to "Current Prayer Time"
                prayer.isNext -> "⏳" to "Coming Up Next"
                else -> getPrayerEmoji(prayer.name) to "Prayer Time"
            }

            builder.addItem(
                Row.Builder()
                    .setTitle("${getPrayerEmoji(prayer.name)} ${prayer.name}")
                    .addText(formattedTime)
                    .addText("$statusEmoji $statusText")
                    .build()
            )
        }

        // Location and method info
        builder.addItem(
            Row.Builder()
                .setTitle("─────────────────────")
                .build()
        )

        builder.addItem(
            Row.Builder()
                .setTitle("📍 ${data.locationName}")
                .addText("📐 Method: ${data.calculationMethod}")
                .build()
        )
    }

    private fun loadPrayerTimes() {
        Log.i(TAG, "🚗 Loading prayer times for Android Auto")
        isLoading = true
        errorMessage = null
        invalidate()

        screenScope.launch {
            try {
                val data = dataProvider.getPrayerTimes()
                prayerData = data
                isLoading = false
                errorMessage = null

                Log.i(TAG, "✅ Prayer times loaded: ${data.prayerTimes.size} prayers")
                Log.i(TAG, "📍 Location: ${data.locationName}")
                Log.i(TAG, "📐 Method: ${data.calculationMethod}")

                invalidate()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading prayer times", e)
                isLoading = false
                errorMessage = "Could not load prayer times"
                invalidate()
            }
        }
    }

    private fun navigateToQibla() {
        Log.i(TAG, "🧭 Navigating to Qibla compass screen")
        carContext.getCarService(ScreenManager::class.java)
            .push(QiblaCompassScreen(carContext, dataProvider))
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

}
