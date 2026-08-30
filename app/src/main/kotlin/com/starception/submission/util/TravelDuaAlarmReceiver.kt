/*
 * Copyright 2024 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.prayer.util.FileLogger
import com.starception.submission.services.DrivingAudioService

/**
 * Wake-up endpoint for a pending Travel Dua.
 *
 * AlarmManager delivers this even while the app process is sleeping. The persisted
 * session token and driving flag prevent an old or cancelled trip from starting audio.
 */
class TravelDuaAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PLAY_TRAVEL_DUA) return

        val prefs = context.getSharedPreferences(
            TravelDuaSettings.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val receivedToken = intent.getLongExtra(EXTRA_SESSION_TOKEN, 0L)
        val expectedToken = prefs.getLong(TravelDuaSettings.KEY_PENDING_ALARM_TOKEN, 0L)
        val isDriving = prefs.getBoolean(TravelDuaSettings.KEY_IS_DRIVING, false)
        val enabled = prefs.getBoolean(TravelDuaSettings.KEY_ENABLED, true)

        if (receivedToken == 0L || receivedToken != expectedToken || !isDriving || !enabled) {
            FileLogger.i(
                TAG,
                "Ignoring stale Travel Dua alarm: tokenMatch=${receivedToken == expectedToken}, " +
                    "driving=$isDriving, enabled=$enabled",
            )
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val lastReliableSpeedElapsed = prefs.getLong(
            TravelDuaSettings.KEY_LAST_RELIABLE_SPEED_ELAPSED,
            0L,
        )
        val lastReliableSpeedMps = prefs.getFloat(
            TravelDuaSettings.KEY_LAST_RELIABLE_SPEED_MPS,
            0f,
        )
        val hasRecentSpeed = TravelDuaPolicy.hasRecentDrivingEvidence(
            nowElapsed,
            lastReliableSpeedElapsed,
        )
        val googleDrivingConfirmed = prefs.getBoolean(
            TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED,
            false,
        )
        val googleConfirmationElapsed = prefs.getLong(
            TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED_ELAPSED,
            0L,
        )
        val lastReliableZeroSpeedElapsed = prefs.getLong(
            TravelDuaSettings.KEY_LAST_RELIABLE_ZERO_SPEED_ELAPSED,
            0L,
        )
        val playbackAllowed = TravelDuaPolicy.shouldAllowTravelDuaPlayback(
            nowElapsedMillis = nowElapsed,
            lastDrivingSpeedElapsedMillis = lastReliableSpeedElapsed,
            lastZeroSpeedElapsedMillis = lastReliableZeroSpeedElapsed,
            googleDrivingConfirmed = googleDrivingConfirmed,
            googleConfirmationElapsedMillis = googleConfirmationElapsed,
        )
        if (!playbackAllowed) {
            val evidenceAge = (nowElapsed - lastReliableSpeedElapsed)
                .takeIf { lastReliableSpeedElapsed > 0L && it >= 0L }
            val zeroSpeedEvidenceAge = (nowElapsed - lastReliableZeroSpeedElapsed)
                .takeIf { lastReliableZeroSpeedElapsed > 0L && it >= 0L }
            val googleEvidenceAge = (nowElapsed - googleConfirmationElapsed)
                .takeIf { googleConfirmationElapsed > 0L && it >= 0L }
            FileLogger.w(
                TAG,
                "Blocked Travel Dua: no current driving evidence " +
                    "(evidenceAgeMs=${evidenceAge ?: "none"}, " +
                    "lastSpeedKmh=${"%.1f".format(lastReliableSpeedMps * 3.6f)}, " +
                    "zeroSpeedEvidenceAgeMs=${zeroSpeedEvidenceAge ?: "none"}, " +
                    "googleConfirmed=$googleDrivingConfirmed, " +
                    "googleEvidenceAgeMs=${googleEvidenceAge ?: "none"})",
            )
            // Keep the process-wide tracker and persisted wake-up state aligned.
            // Otherwise its latched Google flag can continue suppressing the
            // sensor's stationary updates and schedule another false alarm.
            ActivityTracker.updateActivity("Still")
            clearPendingAlarm(prefs, clearDrivingState = true)
            return
        }

        val now = System.currentTimeMillis()
        val lastPlayTime = prefs.getLong(KEY_LAST_DUA_PLAY_TIME, 0L)
        val cooldownMinutes = prefs.getInt(
            TravelDuaSettings.KEY_COOLDOWN_MINUTES,
            TravelDuaSettings.DEFAULT_COOLDOWN_MINUTES,
        )
        val cooldownMillis = cooldownMinutes * 60_000L
        if (lastPlayTime > 0L && now - lastPlayTime < cooldownMillis) {
            FileLogger.i(TAG, "Ignoring Travel Dua alarm because cooldown is still active")
            clearPendingAlarm(prefs)
            return
        }

        // Consume the alarm before starting playback. DrivingAudioService writes the
        // cooldown only after MediaPlayer.start() succeeds.
        clearPendingAlarm(prefs)

        val playbackIntent = Intent(context, DrivingAudioService::class.java).apply {
            putExtra(DrivingAudioService.EXTRA_AUDIO_TYPE, DrivingAudioService.TYPE_TRAVEL_DUA)
        }
        try {
            ContextCompat.startForegroundService(context, playbackIntent)
            FileLogger.i(
                TAG,
                if (hasRecentSpeed &&
                    lastReliableSpeedElapsed > lastReliableZeroSpeedElapsed
                ) {
                    "Travel Dua alarm accepted with recent speed " +
                        "${"%.1f".format(lastReliableSpeedMps * 3.6f)} km/h"
                } else {
                    "Travel Dua alarm accepted with active Google IN_VEHICLE confirmation"
                },
            )
        } catch (e: Exception) {
            // Do not write a cooldown here: a failed start must remain eligible for retry.
            FileLogger.e(TAG, "Unable to start Travel Dua playback from wake-up alarm", e)
        }
    }

    companion object {
        private const val TAG = "TravelDuaAlarmReceiver"
        private const val REQUEST_CODE = 7301
        private const val KEY_LAST_DUA_PLAY_TIME = "last_dua_play_time"

        const val ACTION_PLAY_TRAVEL_DUA =
            "com.starception.submission.action.PLAY_TRAVEL_DUA"
        const val EXTRA_SESSION_TOKEN = "travel_dua_session_token"

        fun pendingIntent(context: Context, sessionToken: Long): PendingIntent {
            val intent = Intent(context, TravelDuaAlarmReceiver::class.java).apply {
                action = ACTION_PLAY_TRAVEL_DUA
                putExtra(EXTRA_SESSION_TOKEN, sessionToken)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }

        private fun clearPendingAlarm(
            prefs: android.content.SharedPreferences,
            clearDrivingState: Boolean = false,
        ) {
            prefs.edit()
                .remove(TravelDuaSettings.KEY_PENDING_ALARM_TOKEN)
                .remove(TravelDuaSettings.KEY_PENDING_ALARM_TRIGGER_ELAPSED)
                .apply {
                    if (clearDrivingState) {
                        putBoolean(TravelDuaSettings.KEY_IS_DRIVING, false)
                        putBoolean(TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED, false)
                        remove(TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED_ELAPSED)
                    }
                }
                .apply()
        }
    }
}
