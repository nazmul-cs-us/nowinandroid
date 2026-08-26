package com.starception.submission.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.starception.submission.prayer.util.FileLogger

/**
 * Process-independent endpoint for Google Play services activity transitions.
 *
 * This receiver is manifest-declared and is reached through an explicit mutable
 * PendingIntent. That lets Play services attach [ActivityTransitionResult] and
 * deliver it even when the app UI has never been opened in the current process.
 */
class DrivingActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            FileLogger.w(TAG, "Activity transition broadcast contained no result")
            return
        }

        val events = ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty()
        events.forEach { event ->
            Log.d(
                TAG,
                "Transition: ${activityName(event.activityType)} / ${event.transitionType}",
            )
            FileLogger.i(
                TAG,
                "Google transition: ${activityName(event.activityType)} / ${event.transitionType}",
            )
        }

        // Keep the IN_VEHICLE edge separate from the display activity. A batch commonly
        // contains IN_VEHICLE EXIT + STILL ENTER; collapsing that to only STILL loses the
        // authoritative vehicle exit and leaves the driving session latched forever.
        val vehicleTransitionType = events
            .lastOrNull { it.activityType == DetectedActivity.IN_VEHICLE }
            ?.transitionType
            ?: -1
        val lastEnteredActivity = events.lastOrNull {
            it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        }

        val detectedActivity: String
        val transitionType: Int
        if (vehicleTransitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            // An IN_VEHICLE enter is authoritative even if the same delivery also contains
            // cleanup transitions for the previous activity (for example STILL EXIT).
            detectedActivity = "DRIVING"
            transitionType = vehicleTransitionType
        } else if (
            lastEnteredActivity != null &&
            !(vehicleTransitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT &&
                lastEnteredActivity.activityType == DetectedActivity.IN_VEHICLE)
        ) {
            detectedActivity = activityName(lastEnteredActivity.activityType)
            transitionType = lastEnteredActivity.transitionType
        } else if (vehicleTransitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
            detectedActivity = "UNKNOWN"
            transitionType = vehicleTransitionType
        } else {
            return
        }

        val serviceIntent = Intent(context, PrayerNotificationService::class.java).apply {
            action = PrayerNotificationService.ACTION_ACTIVITY_TRANSITION
            putExtra(PrayerNotificationService.EXTRA_DETECTED_ACTIVITY, detectedActivity)
            putExtra(PrayerNotificationService.EXTRA_TRANSITION_TYPE, transitionType)
            putExtra(
                PrayerNotificationService.EXTRA_IN_VEHICLE_TRANSITION_TYPE,
                vehicleTransitionType,
            )
        }

        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }.onFailure {
            FileLogger.e(TAG, "Unable to deliver background activity transition to service", it)
        }
    }

    private fun activityName(activityType: Int): String = when (activityType) {
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.ON_BICYCLE -> "CYCLING"
        DetectedActivity.IN_VEHICLE -> "DRIVING"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.TILTING -> "TILTING"
        else -> "UNKNOWN"
    }

    private companion object {
        const val TAG = "DrivingTransitionRx"
    }
}
