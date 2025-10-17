package com.starception.submission.feature.quran

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.starception.submission.R
import java.io.File

class QuranPlaybackService : Service() {

    private val binder = QuranBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

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
        initializePlayer()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuranPlayer::WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
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
                updateNotification()
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
            } else {
                player.start()
                onPlaybackStateChanged?.invoke(true)
            }
            updateNotification()
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("القرآن الكريم")
            .setContentText("${surah.nameArabic} - ${surah.nameEnglish}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.release()
        wakeLock = null
    }
}
