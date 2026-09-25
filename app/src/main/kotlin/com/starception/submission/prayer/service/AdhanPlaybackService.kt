/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.prayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.VolumeProviderCompat
import com.starception.submission.MainActivity
import com.starception.submission.R

/**
 * Adhan Playback Service
 *
 * Plays the adhan sound at prayer time. The adhan used to ride on the
 * notification channel's sound, but that path cannot be volume-controlled
 * or silenced mid-playback. This service owns the audio instead:
 *
 * - MediaPlayer plays [R.raw.short_adhan] at the user's configured volume
 *   ([PrayerNotificationPreferences.adhanVolume], 0–100%)
 * - A MediaSession routes volume-key presses to [mute] — touching any
 *   volume button while the adhan plays silences it immediately
 * - The foreground notification offers an explicit Mute action; tapping
 *   the notification also stops playback and opens the app
 * - Playback ends by itself when the audio completes
 */
class AdhanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializeMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }
            else -> {
                // Anything that hits onStartCommand from a startForegroundService()
                // caller must call startForeground() within 5s or the system kills us.
                val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
                val volumePercent = intent?.getIntExtra(
                    EXTRA_VOLUME_PERCENT,
                    DEFAULT_VOLUME_PERCENT,
                ) ?: DEFAULT_VOLUME_PERCENT
                startForegroundNow(prayerName)
                play(prayerName, volumePercent)
            }
        }
        return START_NOT_STICKY
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "AdhanPlaybackService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPause() {
                    Log.d(TAG, "MediaSession: onPause — muting adhan")
                    mute()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession: onStop — muting adhan")
                    mute()
                }
            })
            // A "playing" playback state makes this the active media session, so
            // volume-key presses route to the volume provider below.
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            // Any volume-key press while the adhan plays silences it.
            // onSetVolumeTo must ignore the system's session-activation
            // volume sync (it re-asserts the current value, not a user
            // action) — treating it as a mute request killed playback
            // ~6ms after start.
            setPlaybackToRemote(
                object : VolumeProviderCompat(
                    VOLUME_CONTROL_ABSOLUTE,
                    MAX_VOLUME,
                    MAX_VOLUME,
                ) {
                    override fun onAdjustVolume(direction: Int) {
                        // direction == 0 is the volume panel opening (ADJUST_SAME)
                        if (direction != 0) {
                            Log.d(TAG, "Volume key pressed during adhan — muting")
                            com.starception.submission.prayer.util.FileLogger.log(
                                "INFO", TAG, "ADHAN_MUTED_BY_VOLUME_KEY",
                            )
                            mute()
                        }
                    }

                    override fun onSetVolumeTo(volume: Int) {
                        // Only a real CHANGE is a user mute request; the system
                        // syncs the provider volume when the session activates.
                        if (volume != currentVolume) {
                            Log.d(TAG, "Volume changed during adhan — muting")
                            com.starception.submission.prayer.util.FileLogger.log(
                                "INFO", TAG, "ADHAN_MUTED_BY_VOLUME_SET ($volume)",
                            )
                            mute()
                        }
                    }
                },
            )
            isActive = true
        }
    }

    private fun play(prayerName: String, volumePercent: Int) {
        // Only release any previous PLAYER — a full stopPlayback() here would
        // also stopForeground()/stopSelf() and have the system destroy the
        // service (and the fresh player with it) the moment onStartCommand
        // returns.
        releasePlayer()
        try {
            val player = MediaPlayer()
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            player.setAudioAttributes(attrs)

            val assetFd = resources.openRawResourceFd(R.raw.short_adhan)
            player.setDataSource(assetFd.fileDescriptor, assetFd.startOffset, assetFd.length)
            assetFd.close()

            val volume = (volumePercent.coerceIn(0, 100) / 100f)
            player.setVolume(volume, volume)

            player.setOnCompletionListener {
                Log.d(TAG, "Adhan for $prayerName completed")
                com.starception.submission.prayer.util.FileLogger.log(
                    "INFO", TAG, "ADHAN_COMPLETED: $prayerName",
                )
                stopPlayback()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Adhan MediaPlayer error: what=$what extra=$extra")
                com.starception.submission.prayer.util.FileLogger.e(
                    TAG, "ADHAN_MEDIA_ERROR for $prayerName: what=$what extra=$extra",
                    RuntimeException("MediaPlayer error what=$what extra=$extra"),
                )
                stopPlayback()
                true
            }
            player.isLooping = false
            requestAudioFocus()
            player.prepare()
            player.start()
            mediaPlayer = player
            com.starception.submission.prayer.util.FileLogger.log(
                "INFO", TAG,
                "ADHAN_PLAYING: $prayerName at $volumePercent% volume",
            )
            Log.d(
                TAG,
                "🕌 Playing adhan for $prayerName at $volumePercent% volume",
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to play adhan", e)
            stopPlayback()
        }
    }

    /** Silences the adhan and stops the service. */
    fun mute() {
        stopPlayback()
    }

    /** Releases the current player and audio focus WITHOUT stopping the service. */
    private fun releasePlayer() {
        mediaPlayer?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        mediaPlayer = null
        abandonAudioFocus()
    }

    /** Full teardown: player, focus, session, foreground state, and the service. */
    private fun stopPlayback() {
        releasePlayer()
        mediaSession?.isActive = false
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
                .build()
            focusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun startForegroundNow(prayerName: String) {
        val notification = buildNotification(prayerName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun buildNotification(prayerName: String): android.app.Notification {
        // Tapping the notification silences the adhan and opens the app.
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val muteIntent = Intent(this, AdhanPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val mutePendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 2,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Adhan — $prayerName")
            .setContentText("Playing adhan. Tap Mute or press a volume key to silence.")
            .setSmallIcon(R.drawable.ic_prayer)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_prayer, "Mute", mutePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Adhan Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows while the adhan plays so it can be muted"
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopPlayback()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AdhanPlaybackService"
        private const val CHANNEL_ID = "adhan_playback_channel"
        private const val NOTIFICATION_ID = 3001

        const val ACTION_STOP = "com.starception.submission.prayer.service.ADHAN_STOP"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_VOLUME_PERCENT = "volume_percent"
        const val DEFAULT_VOLUME_PERCENT = 100

        // Volume-provider capacity used to capture volume-key presses; the
        // adhan's own level comes from the user's adhanVolume preference.
        private const val MAX_VOLUME = 100
        private const val ADHAN_FALLBACK_CHANNEL_ID = "prayer_adhan_fallback"
        private const val FALLBACK_NOTIFICATION_ID = 2003

        /**
         * Starts adhan playback for [prayerName] at [volumePercent] (0–100).
         * No-op when called for a prayer whose adhan toggle is off — callers
         * are expected to check [com.starception.submission.prayer.model.PrayerNotificationPreferences.isAdhanEnabledForPrayer].
         */
        fun start(context: Context, prayerName: String, volumePercent: Int) {
            val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                putExtra(EXTRA_PRAYER_NAME, prayerName)
                putExtra(EXTRA_VOLUME_PERCENT, volumePercent.coerceIn(0, 100))
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Robust entry point for alarm-driven callers: tries the volume-controlled
         * playback service, and when Android 12+/OEM background-start restrictions
         * reject the foreground-service start, falls back to a notification whose
         * channel sound IS the adhan — the system plays it, so the adhan still
         * sounds even if our process is killed immediately afterwards.
         */
        fun startOrFallback(
            context: Context,
            prayerName: String,
            prayerTime: String,
            volumePercent: Int,
        ) {
            try {
                start(context, prayerName, volumePercent)
                com.starception.submission.prayer.util.FileLogger.log(
                    "INFO", "AdhanPlaybackService",
                    "ADHAN_SERVICE_STARTED: $prayerName at $volumePercent%",
                )
            } catch (e: Exception) {
                com.starception.submission.prayer.util.FileLogger.e(
                    "AdhanPlaybackService",
                    "ADHAN_SERVICE_START_FAILED for $prayerName — falling back to channel sound: ${e.message}",
                    e,
                )
                postAdhanFallbackNotification(context, prayerName, prayerTime)
            }
        }

        /**
         * Fallback adhan: high-importance notification on a channel whose sound
         * is the adhan clip. No volume/mute control on this path — strictly
         * better than silence.
         */
        private fun postAdhanFallbackNotification(context: Context, prayerName: String, prayerTime: String) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    val fallbackChannel = NotificationChannel(
                        ADHAN_FALLBACK_CHANNEL_ID,
                        "Prayer Adhan (Fallback)",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "Plays the adhan when the playback service is unavailable"
                        setSound(
                            android.net.Uri.parse(
                                "android.resource://${context.packageName}/${
                                    context.resources.getIdentifier(
                                        "short_adhan", "raw", context.packageName,
                                    )
                                }",
                            ),
                            audioAttributes,
                        )
                    }
                    notificationManager.createNotificationChannel(fallbackChannel)
                }

                val openAppIntent = Intent(context, com.starception.submission.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val contentPendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    FALLBACK_NOTIFICATION_ID,
                    openAppIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                )

                val notification = androidx.core.app.NotificationCompat.Builder(
                    context, ADHAN_FALLBACK_CHANNEL_ID,
                )
                    .setContentTitle("It's time for $prayerName")
                    .setContentText("Adhan for $prayerName (${prayerTime.ifBlank { "now" }})")
                    .setSmallIcon(
                        context.resources.getIdentifier("ic_prayer", "drawable", context.packageName),
                    )
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .build()
                notificationManager.notify(FALLBACK_NOTIFICATION_ID, notification)
                com.starception.submission.prayer.util.FileLogger.log(
                    "INFO", "AdhanPlaybackService",
                    "ADHAN_FALLBACK_POSTED: $prayerName adhan posted via channel sound",
                )
            } catch (e: Exception) {
                com.starception.submission.prayer.util.FileLogger.e(
                    "AdhanPlaybackService",
                    "ADHAN_FALLBACK_FAILED for $prayerName",
                    e,
                )
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
