package com.starception.submission.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.starception.submission.R

/**
 * A lightweight, single-track foreground media service for chapter recitations that are NOT
 * part of a playlist — Fortress-of-the-Muslim duas and Sahih Bukhari hadith audio.
 *
 * Unlike QuranPlaybackService (playlist with next/prev), this plays ONE already-resolved audio
 * source at a time and shows a MediaStyle notification + lock-screen controls (play/pause + seek).
 * The caller resolves the source (local cache or CDN URL) and passes the final playable path/URL
 * plus display metadata via [play]; the service is source-agnostic.
 *
 * Playback state + progress are pushed to [ChapterRecitationState] listeners so the in-app media
 * mini-bar (GlobalMediaViewModel) stays in sync with the notification.
 */
class ChapterRecitationService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private var currentSource: String? = null
    private var currentTitle: String = ""
    private var currentSubtitle: String = ""
    // Sherpa/Android TTS owns its audio pipeline, but still needs this service's
    // MediaSession and foreground notification. In that mode there is no MediaPlayer.
    private var isExternalPlayback = false
    private val externalPlaybackWakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Starception:ChapterRecitation",
        ).apply { setReferenceCounted(false) }
    }

    companion object {
        private const val TAG = "ChapterRecitationSvc"
        private const val NOTIFICATION_ID = 2007
        private const val CHANNEL_ID = "chapter_recitation_channel"

        const val ACTION_PLAY_SOURCE = "com.starception.submission.CHAPTER_PLAY_SOURCE"
        const val ACTION_SHOW_EXTERNAL_PLAYBACK =
            "com.starception.submission.CHAPTER_SHOW_EXTERNAL_PLAYBACK"
        const val ACTION_TOGGLE = "com.starception.submission.CHAPTER_TOGGLE"
        const val ACTION_STOP = "com.starception.submission.CHAPTER_STOP"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"

        /** Start (or switch) playback of an already-resolved [source] with display metadata. */
        fun play(context: Context, source: String, title: String, subtitle: String) {
            // A Play-all sequence can advance while the app is backgrounded. Reuse the
            // already-running service directly because Android 12+ rejects another
            // startForegroundService() call from the background, even for a track update.
            if (ChapterRecitationState.requestSourcePlayback(source, title, subtitle)) return

            val intent = Intent(context, ChapterRecitationService::class.java).apply {
                action = ACTION_PLAY_SOURCE
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start recitation service for $title", e)
            }
        }

        /**
         * Publish media controls for audio rendered outside MediaPlayer (for example
         * Sherpa or Android TTS). The external renderer remains responsible for audio;
         * this service owns the foreground lifetime and MediaSession notification.
         */
        fun showExternalPlayback(context: Context, title: String, subtitle: String) {
            if (ChapterRecitationState.requestExternalPlayback(title, subtitle)) return

            val intent = Intent(context, ChapterRecitationService::class.java).apply {
                action = ACTION_SHOW_EXTERNAL_PLAYBACK
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start external recitation service for $title", e)
            }
        }

        fun toggle(context: Context) {
            context.startService(
                Intent(context, ChapterRecitationService::class.java).apply { action = ACTION_TOGGLE },
            )
        }

        fun stop(context: Context) {
            if (ChapterRecitationState.requestStop()) return
            context.startService(
                Intent(context, ChapterRecitationService::class.java).apply { action = ACTION_STOP },
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initSession()
        ChapterRecitationState.onSourcePlaybackRequested = { source, title, subtitle ->
            handler.post { startPlayback(source, title, subtitle) }
        }
        ChapterRecitationState.onExternalPlaybackRequested = { title, subtitle ->
            handler.post { startExternalPlayback(title, subtitle) }
        }
        ChapterRecitationState.onStopRequested = {
            handler.post { stopPlaybackAndSelf() }
        }
    }

    private fun initSession() {
        mediaSession = MediaSessionCompat(this, "ChapterRecitationService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (isExternalPlayback || mediaPlayer?.isPlaying == false) togglePlayPause()
                }
                override fun onPause() {
                    if (isExternalPlayback || mediaPlayer?.isPlaying == true) togglePlayPause()
                }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
                override fun onStop() { stopPlaybackAndSelf() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (intent?.action) {
            ACTION_PLAY_SOURCE -> {
                val source = intent.getStringExtra(EXTRA_SOURCE)
                if (source != null) {
                    startPlayback(
                        source = source,
                        title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                        subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty(),
                    )
                }
            }
            ACTION_SHOW_EXTERNAL_PLAYBACK -> startExternalPlayback(
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty(),
            )
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_STOP -> stopPlaybackAndSelf()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(source: String, title: String, subtitle: String) {
        // Same source already loaded → treat as a play/pause toggle.
        if (source == currentSource && mediaPlayer != null) {
            togglePlayPause()
            return
        }
        currentSource = source
        currentTitle = title
        currentSubtitle = subtitle
        isExternalPlayback = false
        releaseExternalPlaybackWakeLock()

        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        val player = MediaPlayer()
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        player.setOnPreparedListener { mp ->
            mp.start()
            ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
            ChapterRecitationState.publishProgress(0, mp.duration)
            updateMetadata(mp.duration.toLong())
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            startForegroundNotification()
            startProgressUpdates()
        }
        player.setOnCompletionListener {
            Log.d("DuaAutoPlay", "service setOnCompletionListener fired | title=${ChapterRecitationState.title}")
            ChapterRecitationState.markStopped()
            stopProgressUpdates()
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            // Route natural completion to the active content type. Hadith details
            // advance their own numbered sequence; Fortress playback keeps its
            // existing chapter/dua playlist behavior.
            if (currentTitle.startsWith("Hadith #")) {
                val completion = ChapterRecitationState.onHadithCompletion
                if (completion != null) {
                    // Keep foreground eligibility across the small gap before the
                    // playlist coroutine supplies the next track. Its final cleanup
                    // stops the service when there is no next Hadith.
                    completion.invoke()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                ChapterRecitationState.onCompletion?.invoke()
            }
        }
        player.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
            ChapterRecitationState.publish(false, currentTitle, currentSubtitle)
            if (currentTitle.startsWith("Hadith #")) {
                ChapterRecitationState.onHadithCompletion?.invoke()
            }
            true
        }
        mediaPlayer = player
        // Show a "buffering/loading" foreground notification immediately so the service is
        // promoted to foreground within the required window even before prepare completes.
        startForegroundNotification()
        try {
            player.setDataSource(source)
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "setDataSource failed for $source", e)
            ChapterRecitationState.publish(false, currentTitle, currentSubtitle)
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (currentTitle.startsWith("Hadith #")) {
                ChapterRecitationState.onHadithCompletion?.invoke()
            }
        }
    }

    private fun startExternalPlayback(title: String, subtitle: String) {
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
        currentSource = null
        currentTitle = title
        currentSubtitle = subtitle
        isExternalPlayback = true
        acquireExternalPlaybackWakeLock()

        ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
        updateMetadata(0L)
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startForegroundNotification()
    }

    fun togglePlayPause() {
        if (isExternalPlayback) {
            // The Hadith screen owns the TTS renderer. Its callback applies the
            // same stop/restart behavior as the in-app media controller.
            val callback = ChapterRecitationState.onExternalToggle
            if (callback != null) {
                callback.invoke()
            } else {
                // The screen may have been closed while app-scoped Sherpa audio
                // continued. Preserve a working notification control in that case.
                com.starception.submission.media.GlobalMediaViewModel
                    .onHadithFallbackStop?.invoke()
                stopPlaybackAndSelf()
            }
            return
        }
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            ChapterRecitationState.publish(false, currentTitle, currentSubtitle)
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            updateNotification()
            stopProgressUpdates()
        } else {
            player.start()
            ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            startForegroundNotification()
            startProgressUpdates()
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.let {
            runCatching { it.seekTo(position) }
            ChapterRecitationState.publishProgress(it.currentPosition, it.duration)
            updatePlaybackState(if (it.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        }
    }

    private fun stopPlaybackAndSelf() {
        stopProgressUpdates()
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
        currentSource = null
        isExternalPlayback = false
        releaseExternalPlaybackWakeLock()
        ChapterRecitationState.markStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- MediaSession + notification ----

    private fun updatePlaybackState(state: Int) {
        val pos = (mediaPlayer?.currentPosition ?: 0).toLong()
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(
                    state,
                    pos,
                    if (state == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0f,
                )
                .build(),
        )
    }

    private fun updateMetadata(durationMs: Long) {
        val art = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSubtitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
                .build(),
        )
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer
                if (mp != null) {
                    ChapterRecitationState.publishProgress(mp.currentPosition, mp.duration)
                    if (mp.isPlaying) {
                        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recitation Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the currently playing dua / hadith recitation"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val isPlaying = if (isExternalPlayback) {
            ChapterRecitationState.isPlaying
        } else {
            mediaPlayer?.isPlaying ?: false
        }
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPending = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle.ifBlank { "Recitation" })
            .setContentText(currentSubtitle)
            .setSmallIcon(R.drawable.ic_audio_notification)
            .setContentIntent(contentPending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0),
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY,
                ),
            )
            .build()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        ChapterRecitationState.clearServiceRequests()
        stopProgressUpdates()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        mediaPlayer?.release()
        mediaPlayer = null
        releaseExternalPlaybackWakeLock()
    }

    private fun acquireExternalPlaybackWakeLock() {
        try {
            // Refreshed for each track; prevents a stale external renderer from
            // holding the CPU forever if its completion callback is lost.
            if (externalPlaybackWakeLock.isHeld) externalPlaybackWakeLock.release()
            externalPlaybackWakeLock.acquire(60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire recitation wake lock", e)
        }
    }

    private fun releaseExternalPlaybackWakeLock() {
        try {
            if (externalPlaybackWakeLock.isHeld) externalPlaybackWakeLock.release()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to release recitation wake lock", e)
        }
    }
}

/**
 * Process-wide bridge so ChapterRecitationService can report play/pause + progress without a
 * bound connection. The app registers listeners (forwarding to GlobalMediaViewModel) at startup.
 */
object ChapterRecitationState {
    /** (isPlaying, title, subtitle) */
    var onStateChanged: ((Boolean, String, String) -> Unit)? = null
    /** (positionMs, durationMs) */
    var onProgressChanged: ((Int, Int) -> Unit)? = null
    /** Independent observer for the app-wide media container. Kept separate from the app bridge
     * so feature playback bookkeeping cannot replace or miss the global UI listener. */
    var onGlobalStateChanged: ((Boolean, String, String) -> Unit)? = null
    /** Progress observer paired with [onGlobalStateChanged]. */
    var onGlobalProgressChanged: ((Int, Int) -> Unit)? = null
    /** Fired when a recitation finishes on its own (not a pause/stop) — drives dua auto-advance. */
    var onCompletion: (() -> Unit)? = null
    /** Fired when a downloaded Hadith recording naturally finishes. */
    var onHadithCompletion: (() -> Unit)? = null
    /** MediaSession play/pause request for audio rendered by an external TTS engine. */
    var onExternalToggle: (() -> Unit)? = null

    @Volatile
    internal var onSourcePlaybackRequested: ((String, String, String) -> Unit)? = null
    @Volatile
    internal var onExternalPlaybackRequested: ((String, String) -> Unit)? = null
    @Volatile
    internal var onStopRequested: (() -> Unit)? = null

    // Last-known snapshot so the UI can re-sync on app resume even if the process/Activity was
    // recreated while the service kept playing (e.g. user closed and reopened the app).
    @Volatile var isPlaying: Boolean = false
        private set
    @Volatile var title: String = ""
        private set
    @Volatile var subtitle: String = ""
        private set
    @Volatile var positionMs: Int = 0
        private set
    @Volatile var durationMs: Int = 0
        private set

    /** True when a chapter recitation is currently active (playing or paused, not stopped). */
    @Volatile var isActive: Boolean = false
        private set

    fun publish(isPlaying: Boolean, title: String, subtitle: String) {
        this.isPlaying = isPlaying
        this.title = title
        this.subtitle = subtitle
        // A stop() publishes isPlaying=false with the current title but then clears; treat any
        // publish as "active" — the service explicitly calls markStopped() on teardown.
        this.isActive = true
        onStateChanged?.invoke(isPlaying, title, subtitle)
        onGlobalStateChanged?.invoke(isPlaying, title, subtitle)
    }

    fun publishProgress(positionMs: Int, durationMs: Int) {
        this.positionMs = positionMs
        this.durationMs = durationMs
        onProgressChanged?.invoke(positionMs, durationMs)
        onGlobalProgressChanged?.invoke(positionMs, durationMs)
    }

    /** Called when the service stops/completes so resync() knows there's nothing to restore. */
    fun markStopped() {
        isActive = false
        isPlaying = false
        onStateChanged?.invoke(false, title, subtitle)
        onGlobalStateChanged?.invoke(false, title, subtitle)
    }

    internal fun requestSourcePlayback(
        source: String,
        title: String,
        subtitle: String,
    ): Boolean {
        val callback = onSourcePlaybackRequested ?: return false
        callback(source, title, subtitle)
        return true
    }

    internal fun requestExternalPlayback(title: String, subtitle: String): Boolean {
        val callback = onExternalPlaybackRequested ?: return false
        callback(title, subtitle)
        return true
    }

    internal fun requestStop(): Boolean {
        val callback = onStopRequested ?: return false
        callback()
        return true
    }

    internal fun clearServiceRequests() {
        onSourcePlaybackRequested = null
        onExternalPlaybackRequested = null
        onStopRequested = null
    }
}
