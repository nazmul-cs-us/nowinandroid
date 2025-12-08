package com.starception.submission.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.starception.submission.services.PrayerNotificationService
import com.starception.submission.util.ActivityTracker
import java.util.concurrent.TimeUnit

/**
 * Activity Detection Worker
 *
 * A periodic WorkManager worker that ensures activity detection stays running
 * even if the system kills the service or the device enters Doze mode.
 *
 * This worker runs every 15 minutes and:
 * 1. Checks if activity detection is running
 * 2. Restarts the PrayerNotificationService if needed
 * 3. Reinitializes ActivityTracker if detection stopped
 *
 * WorkManager is more resilient than foreground services against:
 * - Doze mode (battery optimization)
 * - App standby
 * - System memory pressure
 * - Device restarts (when combined with boot receiver)
 */
class ActivityDetectionWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "ActivityDetectionWorker"
        private const val UNIQUE_WORK_NAME = "activity_detection_keep_alive"

        /**
         * Schedule the periodic activity detection keep-alive worker
         * Runs every 15 minutes to ensure detection stays active
         */
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(false) // Run even on low battery
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<ActivityDetectionWorker>(
                    15, TimeUnit.MINUTES // Minimum interval for periodic work
                )
                    .setConstraints(constraints)
                    .setInitialDelay(1, TimeUnit.MINUTES) // Start after 1 minute
                    .addTag("activity_detection")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // Don't replace if already scheduled
                    workRequest
                )

                Log.i(TAG, "📅 Scheduled activity detection keep-alive worker (every 15 minutes)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to schedule activity detection worker: ${e.message}", e)
            }
        }

        /**
         * Cancel the periodic worker
         */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                Log.i(TAG, "🛑 Cancelled activity detection keep-alive worker")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to cancel activity detection worker: ${e.message}", e)
            }
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "⏰ Activity detection keep-alive check running...")

        return try {
            val isDetectionActive = ActivityTracker.isDetectionActive()

            if (!isDetectionActive) {
                Log.w(TAG, "⚠️ Activity detection NOT running - restarting...")

                // Restart the foreground service
                val serviceIntent = Intent(applicationContext, PrayerNotificationService::class.java)
                applicationContext.startForegroundService(serviceIntent)

                // Reinitialize ActivityTracker
                ActivityTracker.initialize(applicationContext, startDetectionNow = true)

                Log.i(TAG, "✅ Activity detection restarted by keep-alive worker")
            } else {
                Log.d(TAG, "✓ Activity detection is running normally")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in activity detection keep-alive: ${e.message}", e)
            Result.retry() // Retry on failure
        }
    }
}
