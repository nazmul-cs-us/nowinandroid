package com.starception.submission.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

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
            Log.w(TAG, "Activity transition broadcast contained no result")
            return
        }

        val events = ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty()
        var activityUpdate: String? = null
        var transitionType = -1

        events.forEach { event ->
            Log.d(
                TAG,
                "Transition: ${activityName(event.activityType)} / ${event.transitionType}",
            )
            when {
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    activityUpdate = activityName(event.activityType)
                    transitionType = event.transitionType
                }
                event.activityType == DetectedActivity.IN_VEHICLE &&
                    event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT &&
                    activityUpdate == null -> {
                    // Stop any pending travel-dua countdown immediately; a later ENTER
                    // in the same batch will replace this with the new concrete activity.
                    activityUpdate = "UNKNOWN"
                    transitionType = event.transitionType
                }
            }
        }

        val detectedActivity = activityUpdate ?: return
        val serviceIntent = Intent(context, PrayerNotificationService::class.java).apply {
            action = PrayerNotificationService.ACTION_ACTIVITY_TRANSITION
            putExtra(PrayerNotificationService.EXTRA_DETECTED_ACTIVITY, detectedActivity)
            putExtra(PrayerNotificationService.EXTRA_TRANSITION_TYPE, transitionType)
        }

        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }.onFailure {
            Log.e(TAG, "Unable to deliver background activity transition to service", it)
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
