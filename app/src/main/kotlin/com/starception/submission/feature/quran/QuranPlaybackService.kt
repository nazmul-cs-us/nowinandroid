package com.starception.submission.feature.quran

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
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
import java.io.File

class QuranPlaybackService : Service() {

    private val binder = QuranBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    private var currentSurahIndex = 0
    private var audioLanguage = AudioLanguage.ARABIC_ONLY

    private val quranArabicPath = "/sdcard/Quran/Arabic"
    private val quranBengaliPath = "/sdcard/Quran/Bengali"
    private val quranEnglishPath = "/sdcard/Quran/English"

    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onSurahChanged: ((Int) -> Unit)? = null
    var onProgressChanged: ((Int, Int) -> Unit)? = null

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "quran_playback_channel"
        const val ACTION_PLAY = "com.starception.submission.ACTION_PLAY"
        const val ACTION_PAUSE = "com.starception.submission.ACTION_PAUSE"
        const val ACTION_NEXT = "com.starception.submission.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.starception.submission.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.starception.submission.ACTION_STOP"
    }

    inner class QuranBinder : Binder() {
        fun getService(): QuranPlaybackService = this@QuranPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d("QuranService", "Service created")
        createNotificationChannel()
        acquireWakeLock()
        initializeMediaSession()
        initializePlayer()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuranPlayer::WakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours
        }
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "QuranPlaybackService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (mediaPlayer?.isPlaying == false) {
                        togglePlayPause()
                    }
                }

                override fun onPause() {
                    if (mediaPlayer?.isPlaying == true) {
                        togglePlayPause()
                    }
                }

                override fun onSkipToNext() {
                    playNext()
                }

                override fun onSkipToPrevious() {
                    playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                }

                override fun onStop() {
                    stopSelf()
                }
            })

            isActive = true
        }
    }

    private fun initializePlayer() {
        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setOnCompletionListener {
                playNext()
            }
            setOnPreparedListener { mp ->
                mp.start()
                onPlaybackStateChanged?.invoke(true)
                updateMediaSessionMetadata() // Update with actual duration
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                updateNotification()
                startProgressUpdates()
            }
            setOnErrorListener { _, what, extra ->
                Log.e("QuranService", "MediaPlayer error: what=$what, extra=$extra")
                true
            }
        }
    }

    fun playSurah(index: Int) {
        try {
            currentSurahIndex = index
            val audioFile = getAudioFile(index)

            if (!audioFile.exists()) {
                Log.e("QuranService", "Audio file not found: ${audioFile.absolutePath}")
                return
            }

            mediaPlayer?.apply {
                reset()
                setDataSource(audioFile.absolutePath)
                prepareAsync()
            }

            onSurahChanged?.invoke(index)
            updateMediaSessionMetadata()
            startForeground(NOTIFICATION_ID, createNotification())

        } catch (e: Exception) {
            Log.e("QuranService", "Failed to play surah", e)
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                onPlaybackStateChanged?.invoke(false)
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                updateNotification()
                stopProgressUpdates()
            } else {
                player.start()
                onPlaybackStateChanged?.invoke(true)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForeground(NOTIFICATION_ID, createNotification())
                } else {
                    updateNotification()
                }
                startProgressUpdates()
            }
        }
    }

    fun playNext() {
        currentSurahIndex = (currentSurahIndex + 1) % QuranData.surahs.size
        playSurah(currentSurahIndex)
    }

    fun playPrevious() {
        currentSurahIndex = if (currentSurahIndex > 0) currentSurahIndex - 1 else QuranData.surahs.size - 1
        playSurah(currentSurahIndex)
    }

    fun setAudioLanguage(language: AudioLanguage) {
        audioLanguage = language
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentSurahIndex(): Int = currentSurahIndex

    private fun getAudioFile(index: Int): File {
        val surah = QuranData.surahs[index]
        return when (audioLanguage) {
            AudioLanguage.ARABIC_ONLY -> {
                val fileName = String.format("%03d", surah.number) + "-" + surah.nameEnglish.lowercase().replace(" ", "-") + ".ogg"
                File(quranArabicPath, fileName)
            }
            AudioLanguage.BENGALI_TRANSLATION -> {
                val bengaliDir = File(quranBengaliPath)
                val allFiles = bengaliDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                if (index < allFiles.size) allFiles[index] else File("")
            }
            AudioLanguage.ENGLISH_TRANSLATION -> {
                val englishDir = File(quranEnglishPath)
                val pattern = String.format("%03d", surah.number)
                englishDir.listFiles()?.find { it.name.startsWith(pattern) }
                    ?: File(englishDir, "${pattern} ${surah.nameEnglish.lowercase().replace(" ", "_")}.ogg")
            }
        }
    }

    private fun updateMediaSessionMetadata() {
        val surah = QuranData.surahs[currentSurahIndex]

        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, surah.nameEnglish)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "القرآن الكريم")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Quran - ${surah.nameArabic}")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration().toLong())
                .build()
        )
    }

    private fun updatePlaybackState(state: Int) {
        val position = getCurrentPosition().toLong()
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, position, 1.0f)
                .build()
        )
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (mediaPlayer?.isPlaying == true) {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    handler.postDelayed(this, 1000) // Update every second
                }
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let {
            handler.removeCallbacks(it)
            progressUpdateRunnable = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quran Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing Surah"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val surah = QuranData.surahs[currentSurahIndex]
        val isPlaying = mediaPlayer?.isPlaying ?: false

        // Create intent for opening the app
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(surah.nameEnglish)
            .setContentText("${surah.nameArabic} - القرآن الكريم")
            .setSubText("Quran Player")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // Add media session token for media controls
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            // Add media control actions
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
                )
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> togglePlayPause()
            ACTION_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("QuranService", "Service destroyed")
        stopProgressUpdates()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.release()
        wakeLock = null
    }
}
