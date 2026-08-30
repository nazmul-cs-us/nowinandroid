/*
 * Copyright 2024 Starception
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

package com.starception.submission.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.starception.submission.R
import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.feature.course.CourseProgressTracker
import com.starception.submission.feature.course.QuranListeningProgress
import com.starception.submission.feature.quran.AudioLanguage
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.download.AudioDownloadHelper
import com.starception.submission.prayer.util.FileLogger
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.voice.EnglishTtsTextNormalizer
import com.starception.submission.voice.SherpaOnnxTtsService
import com.starception.submission.voice.VoiceCompletionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * Unified audio playback service for driving mode with full MediaSession support.
 * Handles: Travel Dua → Hadith (audio or TTS) → Quran → Voice Completion Prompt
 *
 * Features:
 * - MediaSession for system media controls (lock screen, notification, Bluetooth)
 * - MediaStyle notification with play/pause/stop controls
 * - Background playback with wake lock
 * - Proper audio chain management
 */
@AndroidEntryPoint
class DrivingAudioService : Service() {

    @Inject
    lateinit var sherpaOnnxTts: SherpaOnnxTtsService

    @Inject
    lateinit var voiceCompletionManager: VoiceCompletionManager

    @Inject
    lateinit var audioDownloadHelper: AudioDownloadHelper

    private val binder = DrivingAudioBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // TTS for hadith playback
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // Current playback state
    private var currentState = PlaybackState.IDLE
    private var currentTitle = "Driving Mode"
    private var currentSubtitle = ""
    private var isPaused = false

    // Pending audio chain
    private var pendingHadithNumber: Int? = null
    private var pendingHadithText: String? = null
    private var pendingCourseId: String? = null
    private var pendingLessonId: String? = null

    // Callbacks
    var onPlaybackComplete: (() -> Unit)? = null
    var onStateChanged: ((PlaybackState) -> Unit)? = null
    // Secondary callback for the global media controller (does not overwrite primary callback)
    var onGlobalStateChanged: ((PlaybackState) -> Unit)? = null

    // Hadith TTS cache tracking - always maintain 3 hadiths in cache
    private val cachedHadithNumbers = mutableSetOf<Int>()
    private val CACHE_TARGET_SIZE = 3

    // Quran playback state tracking
    private var currentQuranSurahIndex = 0
    private var quranPositionUpdateRunnable: Runnable? = null
    private val QURAN_POSITION_UPDATE_INTERVAL_MS = 5000L // Save position every 5 seconds

    enum class PlaybackState {
        IDLE,
        PLAYING_TRAVEL_DUA,
        PLAYING_HADITH_AUDIO,
        PLAYING_HADITH_TTS,
        PLAYING_QURAN,
        VOICE_PROMPT,
        PAUSED
    }

    companion object {
        private const val TAG = "DrivingAudioService"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "driving_audio_channel"

        /**
         * True while the service exists (onCreate..onDestroy). Lets app-session
         * logic (e.g. MainActivityViewModel's orphaned-TTS guard) distinguish
         * driving-mode speech — which must never be stopped by the activity —
         * from activity-bound hadith reading that shares the same TTS singleton.
         */
        @Volatile
        var isRunning = false
            private set

        const val ACTION_PLAY = "com.starception.submission.DRIVING_PLAY"
        const val ACTION_PAUSE = "com.starception.submission.DRIVING_PAUSE"
        const val ACTION_STOP = "com.starception.submission.DRIVING_STOP"
        const val ACTION_SKIP = "com.starception.submission.DRIVING_SKIP"

        const val EXTRA_AUDIO_TYPE = "audio_type"
        const val EXTRA_HADITH_NUMBER = "hadith_number"
        const val EXTRA_HADITH_TEXT = "hadith_text"
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_LESSON_ID = "lesson_id"

        const val TYPE_TRAVEL_DUA = "travel_dua"
        const val TYPE_HADITH_AUDIO = "hadith_audio"
        const val TYPE_HADITH_TTS = "hadith_tts"

        // Bukhari audio path on SD card
        private const val BUKHARI_AUDIO_PATH = "/sdcard/Bukhari/bukhari_audio_bn"

        // Quran audio paths on SD card
        private const val QURAN_ARABIC_PATH = "/sdcard/Quran/Arabic"
        private const val QURAN_BENGALI_PATH = "/sdcard/Quran/Bengali"
        private const val QURAN_ENGLISH_PATH = "/sdcard/Quran/English"

        private const val QURAN_PREFS = "quran_prefs"
        private const val KEY_AUDIO_LANGUAGE = "audio_language"
        private const val KEY_QURAN_AUDIO_LANGUAGE = "quran_audio_language"
        private const val KEY_LAST_DUA_PLAY_TIME = "last_dua_play_time"

        /**
         * Static listener for global media controller.
         * Set by GlobalMediaViewModel so it can auto-detect when driving mode starts.
         */
        var onServiceStartedListener: (() -> Unit)? = null
    }

    private enum class ChainQuranAudioLanguage {
        ARABIC_ONLY,
        BENGALI_TRANSLATION,
        ENGLISH_TRANSLATION
    }

    inner class DrivingAudioBinder : Binder() {
        fun getService(): DrivingAudioService = this@DrivingAudioService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        isRunning = true
        createNotificationChannel()
        acquireWakeLock()
        initializeMediaSession()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DrivingAudio::WakeLock"
        ).apply {
            acquire(2 * 60 * 60 * 1000L) // 2 hours max
        }
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "DrivingAudioService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession: onPlay")
                    resume()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession: onPause")
                    pause()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession: onStop")
                    stop()
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession: onSkipToNext")
                    skipCurrent()
                }
            })

            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Anything that hits onStartCommand from a startForegroundService() caller
        // must call startForeground() within 5s — or the system kills us with
        // "Context.startForegroundService() did not then call Service.startForeground()".
        // Some action paths (resume, pause, skip, unknown audioType) don't reach
        // startForegroundForPlayback(), so promote unconditionally here. stop()
        // immediately tears it down again so the no-op stop path is still fine.
        ensureForegroundStarted()
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
            ACTION_SKIP -> skipCurrent()
            else -> {
                // Handle audio type requests
                val audioType = intent?.getStringExtra(EXTRA_AUDIO_TYPE)
                when (audioType) {
                    TYPE_TRAVEL_DUA -> {
                        // Ignore duplicate chain starts while a chain is already active.
                        // This can happen when driving is detected again in traffic.
                        if (isChainActive()) {
                            Log.w(
                                TAG,
                                "Ignoring duplicate TYPE_TRAVEL_DUA start; chain already active in state=$currentState"
                            )
                            return START_STICKY
                        }

                        pendingHadithNumber = intent.getIntExtra(EXTRA_HADITH_NUMBER, -1).takeIf { it > 0 }
                        pendingHadithText = intent.getStringExtra(EXTRA_HADITH_TEXT)
                        pendingCourseId = intent.getStringExtra(EXTRA_COURSE_ID)
                        pendingLessonId = intent.getStringExtra(EXTRA_LESSON_ID)
                        playTravelDua()
                        // Notify global media controller that driving audio has started
                        onServiceStartedListener?.invoke()
                    }
                    TYPE_HADITH_AUDIO -> {
                        val hadithNumber = intent.getIntExtra(EXTRA_HADITH_NUMBER, 1)
                        pendingCourseId = intent.getStringExtra(EXTRA_COURSE_ID)
                        pendingLessonId = intent.getStringExtra(EXTRA_LESSON_ID)
                        playHadithAudio(hadithNumber)
                    }
                    TYPE_HADITH_TTS -> {
                        val hadithNumber = intent.getIntExtra(EXTRA_HADITH_NUMBER, 1)
                        val hadithText = intent.getStringExtra(EXTRA_HADITH_TEXT) ?: ""
                        pendingCourseId = intent.getStringExtra(EXTRA_COURSE_ID)
                        pendingLessonId = intent.getStringExtra(EXTRA_LESSON_ID)
                        playHadithTts(hadithNumber, hadithText)
                    }
                }
            }
        }

        return START_STICKY
    }

    // ==================== Foreground Service Type Management ====================

    /**
     * Upgrade the foreground service type to include MICROPHONE before voice recording.
     * On Android 14+ (API 34), the foreground service type MUST include MICROPHONE for
     * the app to access the mic from the background. Without this, AudioRecord captures silence.
     *
     * We start with only MEDIA_PLAYBACK (no RECORD_AUDIO needed) and upgrade here
     * right before we need mic access. This prevents SecurityException during playTravelDua().
     */
    private fun hasRecordAudioPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Track whether startForeground has been called this lifecycle so the
     *  onStartCommand guard doesn't keep re-promoting after we've started. */
    private var hasStartedForeground = false

    private fun ensureForegroundStarted() {
        if (hasStartedForeground) return
        try {
            startForegroundForPlayback()
            hasStartedForeground = true
        } catch (e: Exception) {
            Log.e(TAG, "ensureForegroundStarted failed", e)
        }
    }

    private fun startForegroundForPlayback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (hasRecordAudioPermission()) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }

            try {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    serviceType
                )
                Log.i(
                    TAG,
                    if (serviceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0) {
                        "🚗 Foreground service started with mediaPlayback + microphone"
                    } else {
                        "🚗 Foreground service started with mediaPlayback only"
                    }
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "🚗 Failed to start with microphone type, falling back to mediaPlayback: ${e.message}")
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.i(TAG, "🚗 Foreground service started")
        }
    }

    private fun queuePendingCompletion(courseId: String, lessonId: String, lessonTitle: String, reason: String) {
        Log.w(TAG, "📝 Queueing pending completion for $lessonId ($reason)")
        CourseProgressTracker.setPendingCompletion(this, courseId, lessonId, lessonTitle)
        updateState(PlaybackState.VOICE_PROMPT, lessonTitle, "Couldn't hear YES/NO — open app to confirm")
        updateNotification()
    }

    private fun upgradeForegroundServiceForMicrophone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Check if RECORD_AUDIO is granted before trying to include microphone type
            val hasRecordAudio = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (hasRecordAudio) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                    Log.i(TAG, "🎤 Upgraded foreground service to include MICROPHONE type")
                } catch (e: SecurityException) {
                    Log.w(TAG, "🎤 Cannot upgrade to MICROPHONE type: ${e.message}")
                }
            } else {
                Log.w(TAG, "🎤 RECORD_AUDIO not granted - mic will capture silence from background!")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29-33: re-call startForeground with both types (less strict enforcement)
            try {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
                Log.i(TAG, "🎤 Upgraded foreground service to include MICROPHONE type (API ${Build.VERSION.SDK_INT})")
            } catch (e: Exception) {
                Log.w(TAG, "🎤 Failed to upgrade foreground type: ${e.message}")
            }
        }
    }

    // ==================== Playback Methods ====================

    /**
     * Play travel dua audio
     */
    fun playTravelDua() {
        try {
            Log.i(TAG, "🚗 Playing travel dua")

            // Release existing player
            mediaPlayer?.release()

            val resId = resources.getIdentifier("travel_dua", "raw", packageName)
            if (resId == 0) {
                Log.e(TAG, "Travel dua resource not found")
                onTravelDuaComplete()
                return
            }

            // Alarm-triggered playback intentionally starts this service immediately,
            // without waiting for database work in a BroadcastReceiver. Reconstruct the
            // optional Daily Bukhari continuation here so background reliability does not
            // remove the existing Travel Dua → Hadith chain.
            preparePendingHadithIfNeeded()

            // 🔄 PRE-GENERATE: Start generating hadith TTS in background while travel dua plays
            // This eliminates the gap between travel dua and hadith playback
            preGenerateHadithTtsInBackground()

            mediaPlayer = MediaPlayer.create(this, resId).apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setOnCompletionListener {
                    Log.d(TAG, "🚗 Travel dua completed")
                    onTravelDuaComplete()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    onTravelDuaComplete()
                    true
                }
            }

            updateState(PlaybackState.PLAYING_TRAVEL_DUA, "Travel Dua", "دعاء السفر")
            // If RECORD_AUDIO is already granted while the app is visible, start the
            // foreground service with microphone capability immediately. This preserves
            // background mic access later when the chain reaches the YES/NO prompt.
            startForegroundForPlayback()

            mediaPlayer?.start()
            isPaused = false
            persistTravelDuaStartTime()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing travel dua", e)
            onTravelDuaComplete()
        }
    }

    private fun preparePendingHadithIfNeeded() {
        pendingHadithNumber?.let { existingNumber ->
            // Manual starts already carry the number. Older callers omitted text for
            // Bengali because they assumed the recording would always exist; hydrate the
            // exact English fallback before the travel dua finishes.
            if (pendingHadithText == null) {
                scope.launch {
                    pendingHadithText = getHadithTextForNumber(existingNumber)
                    preGenerateHadithTtsInBackground()
                }
            }
            return
        }

        val prefs = getSharedPreferences("course_progress", Context.MODE_PRIVATE)
        val enrolledCourses = prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()
        if ("daily_bukhari" !in enrolledCourses ||
            !prefs.getBoolean("play_daily_bukhari_after_travel_dua", true)
        ) {
            return
        }

        if (!com.starception.submission.core.hadithdatabase.HadithDatabase.isDatabaseAvailable(
                this,
                "sahih_bukhari.db",
            )
        ) {
            getSharedPreferences("content_prompt_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("missing_bukhari_prompt", true)
                .apply()
            return
        }

        val completedLessons = CourseProgressTracker.getCompletedLessons(this, "daily_bukhari")
        val nextHadithNumber = (1..365).firstOrNull { "hadith_$it" !in completedLessons } ?: 1
        pendingHadithNumber = nextHadithNumber
        pendingCourseId = "daily_bukhari"
        pendingLessonId = "hadith_$nextHadithNumber"

        scope.launch {
            pendingHadithText = getHadithTextForNumber(nextHadithNumber)
            preGenerateHadithTtsInBackground()
            Log.i(TAG, "📚 Prepared alarm-triggered continuation with hadith #$nextHadithNumber")
        }
    }

    /**
     * Pre-generate hadith TTS in background while travel dua plays.
     * This allows hadith playback to start immediately after travel dua completes.
     * Always maintains 3 hadiths in cache.
     */
    private fun preGenerateHadithTtsInBackground() {
        val hadithNumber = pendingHadithNumber

        if (hadithNumber == null || hadithNumber <= 0) {
            Log.d(TAG, "🔄 No hadith number to pre-generate")
            return
        }

        // Fill cache to target size starting from current hadith
        maintainHadithCache(hadithNumber)
    }

    /**
     * Maintain exactly 3 hadiths in cache at all times.
     * Call this after playing a hadith to refill the cache.
     *
     * @param currentHadithNumber The current/next hadith number to start from
     */
    private fun maintainHadithCache(currentHadithNumber: Int) {
        // Check if user has Sherpa-ONNX TTS configured
        val ttsPrefs = getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        val selectedVoiceName = ttsPrefs.getString(
            "selected_voice",
            TtsVoice.KOKORO_EN.name,
        ) ?: TtsVoice.KOKORO_EN.name
        val selectedSpeakerId = ttsPrefs.getInt("selected_speaker_id", 0)

        scope.launch {
            try {
                val voice = try {
                    TtsVoice.valueOf(selectedVoiceName)
                } catch (e: Exception) {
                    TtsVoice.KOKORO_EN
                }
                sherpaOnnxTts.setVoice(voice)

                // Remove hadiths before current one from tracking (they're old)
                cachedHadithNumbers.removeAll { it < currentHadithNumber }

                // Calculate how many more hadiths we need to cache
                val neededCount = CACHE_TARGET_SIZE - cachedHadithNumbers.size
                Log.i(TAG, "📦 Cache status: ${cachedHadithNumbers.size}/$CACHE_TARGET_SIZE hadiths cached, need $neededCount more")

                if (neededCount <= 0) {
                    Log.d(TAG, "📦 Cache full, no pre-generation needed")
                    return@launch
                }

                // Find the next hadith numbers to cache
                var nextHadithToCache = currentHadithNumber
                var generatedCount = 0

                while (generatedCount < neededCount && nextHadithToCache <= 7563) {
                    // Skip if already in cache
                    if (cachedHadithNumbers.contains(nextHadithToCache)) {
                        nextHadithToCache++
                        continue
                    }

                    // Get hadith text from repository
                    val hadithText = getHadithTextForNumber(nextHadithToCache)
                    if (hadithText != null) {
                        val introText = EnglishTtsTextNormalizer.bukhariIntro(nextHadithToCache)
                        val fullText = "$introText $hadithText"

                        // Check if already in TTS cache
                        if (!sherpaOnnxTts.isCached(fullText)) {
                            Log.i(TAG, "🔄 Pre-generating hadith #$nextHadithToCache (${fullText.length} chars, hash=${fullText.hashCode()}) [${cachedHadithNumbers.size + generatedCount + 1}/$CACHE_TARGET_SIZE]")
                            sherpaOnnxTts.preGenerateAsync(
                                text = fullText,
                                speakerId = selectedSpeakerId
                            )
                            cachedHadithNumbers.add(nextHadithToCache)
                            generatedCount++
                            // Small delay between generations to avoid overloading
                            kotlinx.coroutines.delay(500)
                        } else {
                            // Already in TTS cache, just track it
                            cachedHadithNumbers.add(nextHadithToCache)
                            Log.d(TAG, "📦 Hadith #$nextHadithToCache already in TTS cache (hash=${fullText.hashCode()})")
                        }
                    }
                    nextHadithToCache++
                }

                Log.i(TAG, "📦 Cache updated: ${cachedHadithNumbers.size}/$CACHE_TARGET_SIZE hadiths now cached: $cachedHadithNumbers")
                Log.i(TAG, "📦 ${sherpaOnnxTts.getCacheInfo()}")

            } catch (e: Exception) {
                Log.e(TAG, "🔄 Error maintaining cache: ${e.message}")
            }
        }
    }

    /**
     * Mark a hadith as played (remove from cache tracking).
     * Then refill cache to maintain 3 hadiths.
     */
    private fun markHadithPlayed(hadithNumber: Int) {
        cachedHadithNumbers.remove(hadithNumber)
        Log.d(TAG, "📦 Removed hadith #$hadithNumber from cache tracking, remaining: $cachedHadithNumbers")
        // Refill cache starting from next hadith
        maintainHadithCache(hadithNumber + 1)
    }

    /**
     * Get current hadith cache status for debugging.
     * Returns info about both hadith tracking and TTS audio cache.
     */
    fun getCacheStatus(): String {
        val hadithInfo = "📚 Hadith tracking: ${cachedHadithNumbers.size}/$CACHE_TARGET_SIZE hadiths: $cachedHadithNumbers"
        val ttsInfo = sherpaOnnxTts.getCacheInfo()
        Log.i(TAG, "🔍 CACHE STATUS:")
        Log.i(TAG, "   $hadithInfo")
        Log.i(TAG, "   $ttsInfo")
        return "$hadithInfo\n$ttsInfo"
    }

    /**
     * Get hadith text for a specific hadith number.
     * IMPORTANT: Uses BukhariLocalTranslationRepository (same as HadithDetailScreen)
     * to ensure text hash matches for TTS cache hits.
     */
    private suspend fun getHadithTextForNumber(hadithNumber: Int): String? {
        return try {
            // Use the SAME source as HadithDetailScreen for cache compatibility
            val bukhariTranslationRepo = com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository.getInstance(this)
            bukhariTranslationRepo.loadTranslations()

            // Always return the exact English entry. Matching language recordings are
            // handled before this method; a missing recording must use the selected
            // English Sherpa model, never a device-provided translated TTS voice.
            val localEnglish = bukhariTranslationRepo.getEnglishText(hadithNumber)

            if (localEnglish != null) {
                Log.d(TAG, "📚 Loaded hadith #$hadithNumber from BukhariLocalTranslation (${localEnglish.length} chars)")
                localEnglish
            } else {
                // Fallback to database if JSON doesn't have this hadith
                Log.w(TAG, "⚠️ Hadith #$hadithNumber not in BukhariLocalTranslation, falling back to database")
                val hadithRepository = com.starception.submission.core.hadithdatabase.HadithRepository.getInstance(this)
                val hadith = hadithRepository.getHadith("sahih_bukhari.db", hadithNumber)
                hadith?.textPlain ?: hadith?.elaboration
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching hadith #$hadithNumber: ${e.message}")
            null
        }
    }

    private fun onTravelDuaComplete() {
        // Check if we have pending hadith to play
        val hadithNum = pendingHadithNumber
        val hadithText = pendingHadithText

        if (hadithNum != null && hadithNum > 0) {
            // Check user's selected translation language
            val translationService = com.starception.submission.core.translation.TranslationService.getInstance(this)
            val selectedLang = translationService.getSelectedLanguage()
            Log.i(TAG, "📚 Hadith #$hadithNum - selected language: $selectedLang")

            if (selectedLang == "bn") {
                // Bengali: try Bukhari Bengali audio file first, fall back to TTS
                val audioFile = audioDownloadHelper.resolveHadithAudioFile(hadithNum)
                if (audioFile != null) {
                    Log.i(TAG, "📚 Bengali audio file found for #$hadithNum, playing audio")
                    playHadithAudio(hadithNum)
                } else if (hadithText != null) {
                    Log.i(TAG, "📚 No Bengali audio for #$hadithNum, falling back to TTS")
                    playHadithTts(hadithNum, hadithText)
                } else {
                    Log.i(TAG, "📚 No Bengali audio or prepared text for #$hadithNum; loading exact English fallback")
                    playExactEnglishHadithFallback(hadithNum)
                }
            } else if (hadithText != null) {
                // Other languages: use TTS directly
                Log.i(TAG, "📚 Playing hadith #$hadithNum via TTS (language: $selectedLang)")
                playHadithTts(hadithNum, hadithText)
            } else {
                Log.w(TAG, "📚 No text for #$hadithNum, skipping")
                onPlaybackComplete?.invoke()
                updateState(PlaybackState.IDLE, "Driving Mode", "Ready")
            }
        } else {
            // No hadith pending, complete
            onPlaybackComplete?.invoke()
            updateState(PlaybackState.IDLE, "Driving Mode", "Ready")
        }
    }

    /**
     * Play hadith audio from SD card (Bengali)
     */
    fun playHadithAudio(hadithNumber: Int) {
        try {
            Log.i(TAG, "📚 Playing hadith audio #$hadithNumber")

            mediaPlayer?.release()

            // Use AudioDownloadHelper to check CDN downloads + legacy SD card
            var audioFile = audioDownloadHelper.resolveHadithAudioFile(hadithNumber)
            if (audioFile == null) {
                // Fallback: check legacy path directly
                val formattedNumber = String.format("%04d", hadithNumber)
                val legacyFiles = listOf(
                    File(BUKHARI_AUDIO_PATH, "bukhari_$formattedNumber.ogg"),
                    File(BUKHARI_AUDIO_PATH, "bukhari_$formattedNumber.mp3")
                )
                audioFile = legacyFiles.find { it.exists() }
                if (audioFile == null) {
                    Log.w(TAG, "📚 Hadith audio not found for #$hadithNumber")
                    // Cache the recording for next time, but do not make this playback
                    // wait: read the exact English entry with the selected Sherpa voice.
                    val cdnKey = audioDownloadHelper.getHadithCdnKey(hadithNumber)
                    scope.launch {
                        try {
                            Log.i(TAG, "📚 Background-downloading missing Hadith audio: $cdnKey")
                            audioDownloadHelper.downloadAudio(cdnKey)
                        } catch (e: Exception) {
                            Log.w(TAG, "📚 Background download failed for $cdnKey", e)
                        }
                    }
                    playExactEnglishHadithFallback(hadithNumber)
                    return
                }
            }
            Log.i(TAG, "📚 Playing hadith audio from: ${audioFile.absolutePath}")

            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "📚 Hadith audio completed")
                    onHadithComplete(hadithNumber)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    playExactEnglishHadithFallback(hadithNumber)
                    true
                }
                prepare()
            }

            updateState(PlaybackState.PLAYING_HADITH_AUDIO, "Hadith #$hadithNumber", "Sahih Al-Bukhari")
            updateNotification()

            mediaPlayer?.start()
            isPaused = false

        } catch (e: Exception) {
            Log.e(TAG, "Error playing hadith audio", e)
            playExactEnglishHadithFallback(hadithNumber)
        }
    }

    private fun playExactEnglishHadithFallback(hadithNumber: Int) {
        scope.launch {
            val englishText = getHadithTextForNumber(hadithNumber)
            if (englishText.isNullOrBlank()) {
                Log.e(TAG, "📚 Exact English text unavailable for hadith #$hadithNumber")
                onHadithComplete(hadithNumber)
            } else {
                playHadithTts(hadithNumber, englishText)
            }
        }
    }

    /**
     * Play hadith using TTS
     */
    fun playHadithTts(hadithNumber: Int, text: String) {
        Log.i(TAG, "📚 Playing hadith TTS #$hadithNumber")

        // Release travel dua MediaPlayer to free its audio session.
        // Keeping it alive can block the phone microphone later during voice recording.
        mediaPlayer?.release()
        mediaPlayer = null

        updateState(PlaybackState.PLAYING_HADITH_TTS, "Hadith #$hadithNumber", "Sahih Al-Bukhari")
        updateNotification()

        // Check for Sherpa-ONNX TTS preference from Settings
        val ttsPrefs = getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        val selectedVoiceName = ttsPrefs.getString(
            "selected_voice",
            TtsVoice.KOKORO_EN.name,
        ) ?: TtsVoice.KOKORO_EN.name
        val selectedSpeakerId = ttsPrefs.getInt("selected_speaker_id", 0)

        // Bukhari TTS is always the exact English entry through the selected Sherpa model.
        speakWithSherpaOnnx(hadithNumber, text, selectedVoiceName, selectedSpeakerId)
    }

    private fun speakWithSherpaOnnx(hadithNumber: Int, text: String, voiceName: String, speakerId: Int) {
        scope.launch {
            try {
                val voice = try {
                    TtsVoice.valueOf(voiceName)
                } catch (e: Exception) {
                    TtsVoice.KOKORO_EN
                }
                sherpaOnnxTts.setVoice(voice)

                // IMPORTANT: Fetch text using SAME method as maintainHadithCache()
                // to ensure cache hash matches. Don't use the text parameter as it
                // may come from a different source.
                val hadithText = getHadithTextForNumber(hadithNumber) ?: text
                val introText = EnglishTtsTextNormalizer.bukhariIntro(hadithNumber)
                val fullText = "$introText $hadithText"

                // Check if audio was pre-generated during travel dua playback
                val isCached = sherpaOnnxTts.isCached(fullText)
                Log.i(TAG, "📚 Hadith TTS: cached=$isCached, using ${if (isCached) "pre-generated audio 🎯" else "live generation"}")
                if (!isCached) {
                    Log.d(TAG, "📦 Cache miss - text hash: ${fullText.hashCode()}, text length: ${fullText.length}")
                }

                // Use speakCachedOrGenerate to play from cache if available
                val success = sherpaOnnxTts.speakCachedOrGenerate(
                    text = fullText,
                    speakerId = speakerId,
                    onComplete = {
                        Log.d(TAG, "📚 Sherpa-ONNX TTS completed")
                        handler.post { onHadithComplete(hadithNumber) }
                    }
                )

                if (!success) {
                    Log.e(TAG, "Sherpa-ONNX TTS failed; system TTS fallback is disabled")
                    handler.post { onHadithComplete(hadithNumber) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sherpa-ONNX TTS error", e)
                handler.post { onHadithComplete(hadithNumber) }
            }
        }
    }

    private fun speakWithAndroidTts(hadithNumber: Int, text: String) {
        val introText = EnglishTtsTextNormalizer.bukhariIntro(hadithNumber)
        val fullText = EnglishTtsTextNormalizer.normalize("$introText $text")
        val utteranceId = "hadith_$hadithNumber"

        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    textToSpeech?.language = Locale.US
                    setupTtsListener(hadithNumber)
                    textToSpeech?.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                } else {
                    Log.e(TAG, "Android TTS init failed")
                    onHadithComplete(hadithNumber)
                }
            }
        } else if (isTtsInitialized) {
            setupTtsListener(hadithNumber)
            textToSpeech?.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun setupTtsListener(hadithNumber: Int) {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS completed: $utteranceId")
                handler.post { onHadithComplete(hadithNumber) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                handler.post { onHadithComplete(hadithNumber) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error ($errorCode): $utteranceId")
                handler.post { onHadithComplete(hadithNumber) }
            }
        })
    }

    private fun onHadithComplete(hadithNumber: Int) {
        Log.i(TAG, "📚 Hadith #$hadithNumber playback complete - triggering voice prompt")

        // IMPORTANT: Do NOT call markHadithPlayed() here!
        // Background TTS pre-generation competes for ttsMutex and blocks voice prompt.
        // Move to AFTER voice prompt completes to prevent blocking.

        // Cancel background TTS generation and release all audio resources BEFORE
        // starting the voice prompt. This prevents the mic from recording silence
        // due to lingering audio sessions from the travel dua → hadith chain.
        sherpaOnnxTts.cancelBackgroundWork()
        sherpaOnnxTts.stopSpeaking()
        mediaPlayer?.release()
        mediaPlayer = null

        // Upgrade foreground service type to include MICROPHONE before voice recording.
        // Without this, on Android 14+ the app in the background gets silent mic buffers.
        upgradeForegroundServiceForMicrophone()

        // Update state to voice prompt
        updateState(PlaybackState.VOICE_PROMPT, "Say YES or NO", "Mark lesson complete?")
        updateNotification()

        // Allow audio subsystem to fully release resources after the long
        // MediaPlayer + AudioTrack chain before the mic starts recording.
        scope.launch {
            kotlinx.coroutines.delay(500)
            startVoicePromptForHadith(hadithNumber)
        }
    }

    private fun startVoicePromptForHadith(hadithNumber: Int) {
        // Trigger voice completion prompt
        val courseId = pendingCourseId ?: "daily_bukhari"
        val lessonId = pendingLessonId ?: "hadith_$hadithNumber"

        if (!CourseProgressTracker.isCompletionConfirmationMandatory(this, courseId)) {
            Log.i(TAG, "✅ Completion confirmation optional for $lessonId - auto-marking complete")
            CourseProgressTracker.markLessonCompleted(this@DrivingAudioService, courseId, lessonId)
            markHadithPlayed(hadithNumber)
            startQuranPlaybackIfEnrolled(hadithNumber)
            return
        }

        voiceCompletionManager.promptForCompletion(
            courseId = courseId,
            lessonId = lessonId,
            lessonTitle = "Hadith #$hadithNumber",
            onComplete = {
                Log.i(TAG, "✅ Lesson marked complete via voice")
                // Actually mark the lesson as completed in the progress tracker
                CourseProgressTracker.markLessonCompleted(this@DrivingAudioService, courseId, lessonId)
                // NOW start pre-generating next hadiths (after voice prompt done)
                markHadithPlayed(hadithNumber)
                // Continue to Quran playback if enrolled
                startQuranPlaybackIfEnrolled(hadithNumber)
            },
            onSkipped = {
                Log.i(TAG, "⏭️ Lesson explicitly skipped via voice")
                markHadithPlayed(hadithNumber)
                startQuranPlaybackIfEnrolled(hadithNumber)
            },
            onInconclusive = { reason ->
                Log.w(TAG, "⚠️ Voice completion inconclusive for $lessonId: $reason")
                queuePendingCompletion(courseId, lessonId, "Hadith #$hadithNumber", reason)
                markHadithPlayed(hadithNumber)
                startQuranPlaybackIfEnrolled(hadithNumber)
            },
            onError = { error ->
                Log.e(TAG, "Voice prompt error: $error")
                // NOW start pre-generating next hadiths (even on error)
                markHadithPlayed(hadithNumber)
                // Continue to Quran playback if enrolled (even on error)
                startQuranPlaybackIfEnrolled(hadithNumber)
            }
        )
    }

    // ==================== Quran Playback Methods ====================

    /**
     * Check if user is enrolled in Quran listening and start playback if so.
     * Called after voice prompt completion (both YES and NO responses).
     * @param afterHadith The hadith number that was just completed
     */
    private fun startQuranPlaybackIfEnrolled(afterHadith: Int) {
        val isEnrolled = CourseProgressTracker.isEnrolledInQuranListening(this)
        if (!isEnrolled) {
            Log.i(TAG, "🕌 User not enrolled in Quran listening course, finishing flow")
            onPlaybackComplete?.invoke()
            updateState(PlaybackState.IDLE, "Driving Mode", "Ready")
            return
        }

        val coursePrefs = getSharedPreferences("course_progress", Context.MODE_PRIVATE)
        if (!coursePrefs.getBoolean("play_quran_listening_in_driving_chain", true)) {
            Log.i(TAG, "🕌 Quran listening driving-chain playback opted out, finishing flow")
            onPlaybackComplete?.invoke()
            updateState(PlaybackState.IDLE, "Driving Mode", "Ready")
            return
        }

        // Start a new listening session
        CourseProgressTracker.startQuranListeningSession(this)

        // Get saved progress
        val progress = CourseProgressTracker.getQuranListeningProgress(this)
        Log.i(TAG, "🕌 Starting Quran playback - Surah ${progress.currentSurahNumber}, position ${progress.currentPositionMs / 1000}s")

        playQuranSurah(progress.currentSurahIndex, progress.currentPositionMs)
    }

    /**
     * Play a specific Quran surah.
     * @param surahIndex 0-based surah index (0-113)
     * @param startPositionMs Position to resume from (in milliseconds)
     */
    fun playQuranSurah(surahIndex: Int, startPositionMs: Int = 0) {
        try {
            if (surahIndex < 0 || surahIndex >= QuranData.surahs.size) {
                Log.e(TAG, "🕌 Invalid surah index: $surahIndex")
                onQuranComplete()
                return
            }

            // Release existing player once before scanning for a playable surah.
            mediaPlayer?.release()
            mediaPlayer = null

            val selectedAudioLanguage = getSelectedQuranAudioLanguage()
            val selectedPlaybackLanguage = selectedAudioLanguage.toPlaybackLanguage()
            var candidateIndex = surahIndex
            var candidateStartPositionMs = startPositionMs
            var attempts = 0
            var resolvedSurahIndex = -1
            var resolvedSurah = QuranData.surahs[surahIndex]
            var resolvedAudioFile: File? = null

            while (attempts < QuranData.surahs.size) {
                val surah = QuranData.surahs[candidateIndex]
                var audioFile = audioDownloadHelper.resolveQuranAudioFile(candidateIndex, selectedPlaybackLanguage)

                if (audioFile == null && selectedPlaybackLanguage != AudioLanguage.ARABIC_ONLY) {
                    val fallbackArabicFile = audioDownloadHelper.resolveQuranAudioFile(
                        candidateIndex,
                        AudioLanguage.ARABIC_ONLY
                    )
                    if (fallbackArabicFile != null) {
                        Log.w(
                            TAG,
                            "🕌 Selected Quran audio ($selectedAudioLanguage) missing for Surah ${surah.number}; falling back to Arabic"
                        )
                        audioFile = fallbackArabicFile
                    }
                }

                if (audioFile != null) {
                    resolvedSurahIndex = candidateIndex
                    resolvedSurah = surah
                    resolvedAudioFile = audioFile
                    break
                }

                val selectedCdnKey = audioDownloadHelper.getQuranCdnKey(candidateIndex, selectedPlaybackLanguage)
                val fallbackCdnKey = if (selectedPlaybackLanguage != AudioLanguage.ARABIC_ONLY) {
                    audioDownloadHelper.getQuranCdnKey(candidateIndex, AudioLanguage.ARABIC_ONLY)
                } else null
                Log.w(
                    TAG,
                    "🕌 Quran audio missing for Surah ${surah.number}. selected=$selectedAudioLanguage key=${selectedCdnKey ?: "n/a"} fallback=${fallbackCdnKey ?: "n/a"}"
                )
                // Fire-and-forget background download so the next driving session
                // (or a later retry in this chain) will find the audio on disk.
                // Driving mode is hands-free, so we don't surface a UI — we just
                // ensure assets accumulate over time instead of being permanently
                // skipped.
                val keyToFetch = fallbackCdnKey ?: selectedCdnKey
                if (keyToFetch != null) {
                    scope.launch {
                        try {
                            Log.i(TAG, "🕌 Background-downloading missing Quran audio: $keyToFetch")
                            audioDownloadHelper.downloadAudio(keyToFetch)
                        } catch (e: Exception) {
                            Log.w(TAG, "🕌 Background download failed for $keyToFetch", e)
                        }
                    }
                }
                val nextIndex = CourseProgressTracker.completeCurrentSurah(this)
                if (nextIndex == candidateIndex) {
                    break
                }
                candidateIndex = nextIndex
                candidateStartPositionMs = 0
                attempts++
            }

            if (resolvedSurahIndex == -1) {
                Log.e(TAG, "🕌 No playable Quran audio found after checking ${attempts.coerceAtLeast(1)} surahs; ending chain")
                onQuranComplete()
                return
            }

            currentQuranSurahIndex = resolvedSurahIndex
            val surah = resolvedSurah
            val audioFile = resolvedAudioFile ?: run {
                Log.e(TAG, "🕌 Resolved audio file unexpectedly null for Surah ${surah.number}")
                onQuranComplete()
                return
            }

            Log.i(TAG, "🕌 Playing Quran: Surah ${surah.number} - ${surah.nameEnglish} (${surah.nameArabic})")

            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(audioFile.absolutePath)

                setOnPreparedListener { mp ->
                    // Seek to saved position if resuming
                    if (candidateStartPositionMs > 0 && candidateStartPositionMs < mp.duration) {
                        mp.seekTo(candidateStartPositionMs)
                        Log.d(TAG, "🕌 Seeked to saved position: ${candidateStartPositionMs / 1000}s")
                    }
                    mp.start()
                    startQuranPositionUpdates()
                }

                setOnCompletionListener {
                    Log.i(TAG, "🕌 Surah ${surah.number} (${surah.nameEnglish}) playback completed")
                    stopQuranPositionUpdates()
                    promptForSurahCompletion(surahIndex, surah.number, surah.nameEnglish)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "🕌 MediaPlayer error: what=$what, extra=$extra")
                    stopQuranPositionUpdates()
                    onQuranComplete()
                    true
                }

                prepare()
            }

            updateState(PlaybackState.PLAYING_QURAN, "Surah ${surah.nameEnglish}", surah.nameArabic)
            updateNotification()
            isPaused = false

        } catch (e: Exception) {
            Log.e(TAG, "🕌 Error playing Quran surah", e)
            onQuranComplete()
        }
    }

    private fun getSelectedQuranAudioLanguage(): ChainQuranAudioLanguage {
        val prefs = getSharedPreferences(QURAN_PREFS, Context.MODE_PRIVATE)
        val storedValue = prefs.getString(KEY_AUDIO_LANGUAGE, null)
            ?: prefs.getString(KEY_QURAN_AUDIO_LANGUAGE, null)
            ?: ChainQuranAudioLanguage.ARABIC_ONLY.name

        return when (storedValue.uppercase(Locale.US)) {
            "ARABIC_ONLY", "AR", "ARABIC" -> ChainQuranAudioLanguage.ARABIC_ONLY
            "BENGALI_TRANSLATION", "BN", "BENGALI" -> ChainQuranAudioLanguage.BENGALI_TRANSLATION
            "ENGLISH_TRANSLATION", "EN", "ENGLISH" -> ChainQuranAudioLanguage.ENGLISH_TRANSLATION
            else -> {
                Log.w(TAG, "🕌 Unknown quran audio_language='$storedValue', defaulting to Arabic")
                ChainQuranAudioLanguage.ARABIC_ONLY
            }
        }
    }

    private fun ChainQuranAudioLanguage.toPlaybackLanguage(): AudioLanguage {
        return when (this) {
            ChainQuranAudioLanguage.ARABIC_ONLY -> AudioLanguage.ARABIC_ONLY
            ChainQuranAudioLanguage.BENGALI_TRANSLATION -> AudioLanguage.BENGALI_TRANSLATION
            ChainQuranAudioLanguage.ENGLISH_TRANSLATION -> AudioLanguage.ENGLISH_TRANSLATION
        }
    }

    /**
     * Ask for consent before marking Surah as complete.
     * YES -> mark complete and continue to next Surah.
     * NO  -> keep progress without completing current Surah and end Quran flow.
     */
    private fun promptForSurahCompletion(
        surahIndex: Int,
        surahNumber: Int,
        surahNameEnglish: String
    ) {
        val lessonId = "surah_$surahNumber"
        val lessonTitle = "Surah #$surahNumber"

        Log.i(TAG, "🕌 Prompting completion consent for $lessonTitle ($surahNameEnglish)")

        // Release Quran MediaPlayer to free audio session before mic recording
        sherpaOnnxTts.stopSpeaking()
        mediaPlayer?.release()
        mediaPlayer = null

        // Upgrade foreground service type to include MICROPHONE before voice recording.
        upgradeForegroundServiceForMicrophone()

        if (!CourseProgressTracker.isCompletionConfirmationMandatory(this, "complete_quran_listening")) {
            Log.i(TAG, "🕌 Completion confirmation optional for $lessonId - auto-marking complete")
            continueToNextSurahAfterCompletion(surahIndex)
            return
        }

        updateState(PlaybackState.VOICE_PROMPT, "Say YES or NO", "Mark Surah complete?")
        updateNotification()

        // Allow audio subsystem to settle after Quran MediaPlayer release
        scope.launch {
            kotlinx.coroutines.delay(500)
            voiceCompletionManager.promptForCompletion(
            courseId = "complete_quran_listening",
            lessonId = lessonId,
            lessonTitle = lessonTitle,
            onComplete = {
                Log.i(TAG, "🕌 ✅ User confirmed completion for $lessonId")
                continueToNextSurahAfterCompletion(surahIndex)
            },
            onSkipped = {
                Log.i(TAG, "🕌 ⏭️ User explicitly skipped completion for $lessonId - ending Quran flow")
                onQuranComplete()
            },
            onInconclusive = { reason ->
                Log.w(TAG, "🕌 ⚠️ Voice completion inconclusive for $lessonId: $reason")
                queuePendingCompletion("complete_quran_listening", lessonId, lessonTitle, reason)
                onQuranComplete()
            },
            onError = { error ->
                Log.e(TAG, "🕌 Voice completion error for $lessonId: $error - ending Quran flow safely")
                onQuranComplete()
            }
        )
        }
    }

    /**
     * Mark current Surah complete and continue playback with the next one.
     */
    private fun continueToNextSurahAfterCompletion(previousSurahIndex: Int) {
        val nextIndex = CourseProgressTracker.completeCurrentSurah(this)
        Log.i(TAG, "🕌 Advancing to next surah: ${nextIndex + 1}")

        if (nextIndex in QuranData.surahs.indices && nextIndex != previousSurahIndex) {
            playQuranSurah(nextIndex, 0)
        } else {
            Log.i(TAG, "🕌 Quran listening cycle complete")
            onQuranComplete()
        }
    }

    /**
     * Start periodic position updates for Quran playback.
     * Saves progress every 5 seconds.
     */
    private fun startQuranPositionUpdates() {
        stopQuranPositionUpdates()
        quranPositionUpdateRunnable = object : Runnable {
            override fun run() {
                if (mediaPlayer?.isPlaying == true && currentState == PlaybackState.PLAYING_QURAN) {
                    val position = mediaPlayer?.currentPosition ?: 0
                    CourseProgressTracker.updateQuranPosition(this@DrivingAudioService, position)
                    handler.postDelayed(this, QURAN_POSITION_UPDATE_INTERVAL_MS)
                }
            }
        }
        handler.post(quranPositionUpdateRunnable!!)
    }

    /**
     * Stop position updates for Quran playback.
     */
    private fun stopQuranPositionUpdates() {
        quranPositionUpdateRunnable?.let {
            handler.removeCallbacks(it)
            quranPositionUpdateRunnable = null
        }
    }

    /**
     * Called when Quran playback is complete.
     * Ends the listening session and completes the audio flow.
     */
    private fun onQuranComplete() {
        stopQuranPositionUpdates()

        // End the listening session and accumulate stats
        CourseProgressTracker.endQuranListeningSession(this)

        Log.i(TAG, "🕌 Quran playback complete - ending driving audio flow")
        onPlaybackComplete?.invoke()
        updateState(PlaybackState.IDLE, "Driving Mode", "Ready")
    }

    // ==================== Playback Controls ====================

    fun pause() {
        Log.d(TAG, "⏸️ Pause requested")
        when (currentState) {
            PlaybackState.PLAYING_TRAVEL_DUA,
            PlaybackState.PLAYING_HADITH_AUDIO,
            PlaybackState.PLAYING_QURAN -> {
                mediaPlayer?.pause()
                isPaused = true
                if (currentState == PlaybackState.PLAYING_QURAN) {
                    stopQuranPositionUpdates()
                    // Save current position before pausing
                    val position = mediaPlayer?.currentPosition ?: 0
                    CourseProgressTracker.updateQuranPosition(this, position)
                }
                updateState(PlaybackState.PAUSED, currentTitle, "Paused")
                updateNotification()
            }
            PlaybackState.PLAYING_HADITH_TTS -> {
                textToSpeech?.stop()
                sherpaOnnxTts.stopSpeaking()
                isPaused = true
                updateState(PlaybackState.PAUSED, currentTitle, "Paused")
                updateNotification()
            }
            else -> {}
        }
    }

    fun resume() {
        Log.d(TAG, "▶️ Resume requested")
        if (isPaused && mediaPlayer != null) {
            mediaPlayer?.start()
            isPaused = false
            // Restore previous state
            when {
                currentTitle.contains("Travel") -> updateState(PlaybackState.PLAYING_TRAVEL_DUA, currentTitle, "دعاء السفر")
                currentTitle.contains("Hadith") -> updateState(PlaybackState.PLAYING_HADITH_AUDIO, currentTitle, "Sahih Al-Bukhari")
                currentTitle.contains("Surah") -> {
                    val surah = if (currentQuranSurahIndex < QuranData.surahs.size) {
                        QuranData.surahs[currentQuranSurahIndex]
                    } else null
                    updateState(PlaybackState.PLAYING_QURAN, currentTitle, surah?.nameArabic ?: "ٱلْقُرْآنُ")
                    startQuranPositionUpdates()
                }
            }
            updateNotification()
        }
    }

    fun stop() {
        Log.d(TAG, "⏹️ Stop requested")

        // Save Quran position before stopping
        if (currentState == PlaybackState.PLAYING_QURAN) {
            val position = mediaPlayer?.currentPosition ?: 0
            CourseProgressTracker.updateQuranPosition(this, position)
            CourseProgressTracker.endQuranListeningSession(this)
        }

        stopQuranPositionUpdates()
        stopAndReleaseMediaPlayer("stop()")
        textToSpeech?.stop()
        sherpaOnnxTts.stopSpeaking()
        voiceCompletionManager.cancel()

        isPaused = false
        updateState(PlaybackState.IDLE, "Driving Mode", "Stopped")

        stopForeground(STOP_FOREGROUND_REMOVE)
        hasStartedForeground = false
        stopSelf()
    }

    fun skipCurrent() {
        Log.d(TAG, "⏭️ Skip requested")
        when (currentState) {
            PlaybackState.PLAYING_TRAVEL_DUA -> {
                stopAndReleaseMediaPlayer("skip travel dua")
                onTravelDuaComplete()
            }
            PlaybackState.PLAYING_HADITH_AUDIO,
            PlaybackState.PLAYING_HADITH_TTS -> {
                stopAndReleaseMediaPlayer("skip hadith")
                textToSpeech?.stop()
                sherpaOnnxTts.stopSpeaking()
                onPlaybackComplete?.invoke()
                updateState(PlaybackState.IDLE, "Driving Mode", "Skipped")
            }
            PlaybackState.PLAYING_QURAN -> {
                // Save position before skipping
                val position = mediaPlayer?.currentPosition ?: 0
                CourseProgressTracker.updateQuranPosition(this, position)
                stopQuranPositionUpdates()
                stopAndReleaseMediaPlayer("skip quran")
                CourseProgressTracker.endQuranListeningSession(this)
                onPlaybackComplete?.invoke()
                updateState(PlaybackState.IDLE, "Driving Mode", "Skipped")
            }
            PlaybackState.VOICE_PROMPT -> {
                voiceCompletionManager.cancel()
                onPlaybackComplete?.invoke()
                updateState(PlaybackState.IDLE, "Driving Mode", "Skipped")
            }
            else -> {}
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true ||
        currentState == PlaybackState.PLAYING_HADITH_TTS ||
        currentState == PlaybackState.PLAYING_QURAN

    fun getCurrentState(): PlaybackState = currentState

    private fun isChainActive(): Boolean {
        return currentState != PlaybackState.IDLE || mediaPlayer?.isPlaying == true || isPaused
    }

    // ==================== State Management ====================

    private fun updateState(state: PlaybackState, title: String, subtitle: String) {
        currentState = state
        currentTitle = title
        currentSubtitle = subtitle

        onStateChanged?.invoke(state)
        onGlobalStateChanged?.invoke(state)
        updateMediaSessionMetadata()
        updatePlaybackState()
    }

    private fun updateMediaSessionMetadata() {
        val appIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSubtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Driving Mode")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: 0L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, appIcon)
                .build()
        )
    }

    private fun updatePlaybackState() {
        val state = when (currentState) {
            PlaybackState.IDLE -> PlaybackStateCompat.STATE_STOPPED
            PlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PlaybackState.VOICE_PROMPT -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }

        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
                .setState(state, position, 1.0f)
                .build()
        )
    }

    // ==================== Notification ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driving Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Travel dua and hadith playback during driving"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val isPlaying = isPlaying() && currentState != PlaybackState.PAUSED

        // Create intent for opening the app
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, DrivingAudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle)
            .setSubText("Driving Mode")
            .setSmallIcon(R.drawable.ic_audio_notification)
            .setContentIntent(contentPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            // Play/Pause button
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
                )
            )
            // Skip button
            .addAction(
                android.R.drawable.ic_media_next,
                "Skip",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            // Stop button
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    // ==================== Lifecycle ====================

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isRunning = false

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        mediaPlayer?.release()
        mediaPlayer = null

        textToSpeech?.shutdown()
        textToSpeech = null

        wakeLock?.release()
        wakeLock = null
    }

    private fun persistTravelDuaStartTime() {
        try {
            val now = System.currentTimeMillis()
            getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_DUA_PLAY_TIME, now)
                .putBoolean(TravelDuaSettings.KEY_DUA_PLAYED_FOR_CURRENT_TRIP, true)
                .remove(TravelDuaSettings.KEY_DRIVING_STOP_TIME)
                .apply()
            Log.i(TAG, "🚗 Cooldown persisted at playback start")
            FileLogger.i(
                TAG,
                "Travel Dua playback started; current trip marked as already played",
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist travel dua start time: ${e.message}")
            FileLogger.e(TAG, "Failed to persist Travel Dua trip state", e)
        }
    }

    private fun stopAndReleaseMediaPlayer(reason: String) {
        val player = mediaPlayer ?: return
        try {
            player.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "MediaPlayer stop ignored in invalid state ($reason): ${e.message}")
        } finally {
            try {
                player.release()
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer release error ($reason): ${e.message}")
            }
            mediaPlayer = null
        }
    }
}
