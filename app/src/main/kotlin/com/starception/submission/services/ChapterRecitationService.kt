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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.starception.submission.R
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.Hadith
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.translation.TranslationService
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AssetRepository
import com.starception.submission.download.AudioDownloadHelper
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.voice.EnglishTtsTextNormalizer
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import com.starception.submission.voice.SherpaOnnxTtsService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

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
    private var isContinuousHandoff = false
    // Sherpa/Android TTS owns its audio pipeline, but still needs this service's
    // MediaSession and foreground notification. In that mode there is no MediaPlayer.
    private var isExternalPlayback = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bookPlaylistJob: Job? = null
    private var bookPlaylistGeneration = 0
    private var bookPlaylistPaused = false
    private var bookPlaylistCurrent = 0
    private var bookPlaylistStart = 0
    private var bookPlaylistEnd = 0
    private var bookPlaylistOrder: List<Int> = emptyList()
    private var requestedBookTrack: Int? = null
    private var androidTts: TextToSpeech? = null
    private var androidTtsContinuation: CancellableContinuation<Boolean>? = null
    private var recordingContinuation: CancellableContinuation<Boolean>? = null
    private var recordingPrepared = false
    private lateinit var sherpaOnnxTts: SherpaOnnxTtsService
    private lateinit var audioDownloadHelper: AudioDownloadHelper
    private lateinit var assetRepository: AssetRepository

    private enum class BookRenderer { NONE, PREPARING, SHERPA, ANDROID_TTS, RECORDING }

    private var bookRenderer = BookRenderer.NONE
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
        const val ACTION_PLAY_HADITH_BOOK =
            "com.starception.submission.CHAPTER_PLAY_HADITH_BOOK"
        const val ACTION_NEXT = "com.starception.submission.CHAPTER_NEXT"
        const val ACTION_PREVIOUS = "com.starception.submission.CHAPTER_PREVIOUS"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_CONTINUOUS_HANDOFF = "continuous_handoff"
        const val EXTRA_DATABASE_FILE = "database_file"
        const val EXTRA_COLLECTION_NAME = "collection_name"
        const val EXTRA_START_HADITH = "start_hadith"
        const val EXTRA_RANGE_START = "range_start"
        const val EXTRA_RANGE_END = "range_end"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_SPEAKER_ID = "speaker_id"
        const val EXTRA_SHUFFLE = "shuffle"

        /** Start (or switch) playback of an already-resolved [source] with display metadata. */
        fun play(
            context: Context,
            source: String,
            title: String,
            subtitle: String,
            continuousHandoff: Boolean = false,
        ) {
            // A Play-all sequence can advance while the app is backgrounded. Reuse the
            // already-running service directly because Android 12+ rejects another
            // startForegroundService() call from the background, even for a track update.
            if (
                ChapterRecitationState.requestSourcePlayback(
                    source,
                    title,
                    subtitle,
                    continuousHandoff,
                )
            ) return

            val intent = Intent(context, ChapterRecitationService::class.java).apply {
                action = ACTION_PLAY_SOURCE
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_CONTINUOUS_HANDOFF, continuousHandoff)
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

        /**
         * Start a service-owned Bukhari/Shama'il queue. All database loading, translation,
         * synthesis and track advancement happens here, so leaving the book UI cannot cancel it.
         */
        fun playHadithBook(
            context: Context,
            databaseFile: String,
            collectionName: String,
            startHadith: Int,
            rangeStart: Int,
            rangeEnd: Int,
            language: String,
            voiceName: String,
            speakerId: Int,
            shuffle: Boolean = false,
        ) {
            val intent = Intent(context, ChapterRecitationService::class.java).apply {
                action = ACTION_PLAY_HADITH_BOOK
                putExtra(EXTRA_DATABASE_FILE, databaseFile)
                putExtra(EXTRA_COLLECTION_NAME, collectionName)
                putExtra(EXTRA_START_HADITH, startHadith)
                putExtra(EXTRA_RANGE_START, rangeStart)
                putExtra(EXTRA_RANGE_END, rangeEnd)
                putExtra(EXTRA_LANGUAGE, language)
                putExtra(EXTRA_VOICE, voiceName)
                putExtra(EXTRA_SPEAKER_ID, speakerId)
                putExtra(EXTRA_SHUFFLE, shuffle)
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start Hadith book playback", e)
            }
        }

        fun next(context: Context) {
            context.startService(
                Intent(context, ChapterRecitationService::class.java).apply { action = ACTION_NEXT },
            )
        }

        fun previous(context: Context) {
            context.startService(
                Intent(context, ChapterRecitationService::class.java).apply {
                    action = ACTION_PREVIOUS
                },
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
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SherpaOnnxTtsEntryPoint::class.java,
        )
        sherpaOnnxTts = entryPoint.sherpaOnnxTtsService()
        audioDownloadHelper = entryPoint.audioDownloadHelper()
        assetRepository = entryPoint.assetRepository()
        createNotificationChannel()
        initSession()
        ChapterRecitationState.onSourcePlaybackRequested =
            { source, title, subtitle, continuousHandoff ->
                handler.post {
                    startPlayback(source, title, subtitle, continuousHandoff)
                }
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
                    if (ChapterRecitationState.isBookPlaylistActive) {
                        resumeBookPlaylist()
                    } else if (isExternalPlayback) {
                        resumeExternalPlayback()
                    } else if (mediaPlayer?.isPlaying == false) {
                        togglePlayPause()
                    }
                }
                override fun onPause() {
                    if (ChapterRecitationState.isBookPlaylistActive) {
                        pauseBookPlaylist()
                    } else if (isExternalPlayback) {
                        pauseExternalPlayback()
                    } else if (mediaPlayer?.isPlaying == true) {
                        togglePlayPause()
                    }
                }
                override fun onSkipToNext() { skipHadith(next = true) }
                override fun onSkipToPrevious() { skipHadith(next = false) }
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
                        continuousHandoff = intent.getBooleanExtra(
                            EXTRA_CONTINUOUS_HANDOFF,
                            false,
                        ),
                    )
                }
            }
            ACTION_SHOW_EXTERNAL_PLAYBACK -> startExternalPlayback(
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty(),
            )
            ACTION_PLAY_HADITH_BOOK -> startHadithBookPlaylist(
                databaseFile = intent.getStringExtra(EXTRA_DATABASE_FILE).orEmpty(),
                collectionName = intent.getStringExtra(EXTRA_COLLECTION_NAME).orEmpty(),
                startHadith = intent.getIntExtra(EXTRA_START_HADITH, 1),
                rangeStart = intent.getIntExtra(EXTRA_RANGE_START, 1),
                rangeEnd = intent.getIntExtra(EXTRA_RANGE_END, 1),
                language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "en",
                voiceName = intent.getStringExtra(EXTRA_VOICE) ?: TtsVoice.KOKORO_EN.name,
                speakerId = intent.getIntExtra(EXTRA_SPEAKER_ID, 0),
                shuffle = intent.getBooleanExtra(EXTRA_SHUFFLE, false),
            )
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_NEXT -> skipHadith(next = true)
            ACTION_PREVIOUS -> skipHadith(next = false)
            ACTION_STOP -> stopPlaybackAndSelf()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(
        source: String,
        title: String,
        subtitle: String,
        continuousHandoff: Boolean,
    ) {
        // A fresh play request for the same source means replay from the beginning.
        // Play/pause requests arrive through ACTION_TOGGLE and never enter this branch.
        if (source == currentSource && mediaPlayer != null) {
            val player = mediaPlayer ?: return
            runCatching {
                player.seekTo(0)
                player.start()
                ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                startForegroundNotification()
                startProgressUpdates()
            }
            return
        }
        currentSource = source
        currentTitle = title
        currentSubtitle = subtitle
        isContinuousHandoff = continuousHandoff
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
            stopProgressUpdates()
            // Route natural completion to the active content type. Hadith details
            // advance their own numbered sequence; Fortress playback keeps its
            // existing chapter/dua playlist behavior.
            if (currentTitle.startsWith("Hadith #")) {
                val completion = ChapterRecitationState.onHadithCompletion
                if (completion != null && isContinuousHandoff) {
                    // A Play-all sequence may spend a short time loading the next
                    // database row or recording. Keep the MediaSession active during
                    // that handoff. Reporting STOPPED here makes Samsung classify the
                    // media FGS as idle and remove it while the display is locked.
                    ChapterRecitationState.publish(
                        true,
                        currentTitle,
                        currentSubtitle,
                    )
                    updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                    updateNotification()
                    completion.invoke()
                } else {
                    ChapterRecitationState.markStopped()
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                    completion?.invoke()
                    stopPlaybackAndSelf()
                }
            } else {
                ChapterRecitationState.markStopped()
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                stopForeground(STOP_FOREGROUND_REMOVE)
                ChapterRecitationState.onCompletion?.invoke()
            }
        }
        player.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
            val completion = ChapterRecitationState.onHadithCompletion
            if (
                currentTitle.startsWith("Hadith #") &&
                completion != null &&
                isContinuousHandoff
            ) {
                ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
                updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                updateNotification()
                completion.invoke()
            } else {
                ChapterRecitationState.publish(false, currentTitle, currentSubtitle)
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                completion?.invoke()
                stopPlaybackAndSelf()
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

    private fun startHadithBookPlaylist(
        databaseFile: String,
        collectionName: String,
        startHadith: Int,
        rangeStart: Int,
        rangeEnd: Int,
        language: String,
        voiceName: String,
        speakerId: Int,
        shuffle: Boolean,
    ) {
        if (databaseFile.isBlank() || rangeEnd < rangeStart) {
            Log.e(TAG, "Invalid Hadith book request: db=$databaseFile range=$rangeStart..$rangeEnd")
            stopPlaybackAndSelf()
            return
        }

        bookPlaylistGeneration += 1
        val generation = bookPlaylistGeneration
        bookPlaylistJob?.cancel()
        stopCurrentBookRenderer()
        stopProgressUpdates()
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null

        bookPlaylistPaused = false
        bookPlaylistStart = rangeStart
        bookPlaylistEnd = rangeEnd
        bookPlaylistCurrent = startHadith.coerceIn(rangeStart, rangeEnd)
        bookPlaylistOrder = if (shuffle) {
            (rangeStart..rangeEnd).shuffled()
        } else {
            (rangeStart..rangeEnd).toList()
        }
        if (!shuffle) {
            bookPlaylistOrder = bookPlaylistOrder.dropWhile { it < bookPlaylistCurrent }
        }
        requestedBookTrack = null
        ChapterRecitationState.setBookPlaylist(
            active = true,
            currentHadith = bookPlaylistCurrent,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )
        currentTitle = "Hadith #$bookPlaylistCurrent"
        currentSubtitle = collectionName
        isContinuousHandoff = true
        startExternalPlayback(currentTitle, currentSubtitle)
        ChapterRecitationState.publishBookTrack(bookPlaylistCurrent, true)

        val voice = runCatching { TtsVoice.valueOf(voiceName) }
            .getOrDefault(TtsVoice.KOKORO_EN)
        bookPlaylistJob = serviceScope.launch {
            val repository = HadithRepository(applicationContext, assetRepository)
            val bukhariRepository = BukhariLocalTranslationRepository.getInstance(
                applicationContext,
                assetRepository,
            )
            val translationService = TranslationService.getInstance(applicationContext)
            val isBukhari = databaseFile.contains("bukhari", ignoreCase = true)
            val isShamayel = databaseFile.contains("shamayele_tirmidhi", ignoreCase = true)
            if (isBukhari) bukhariRepository.loadTranslations()
            sherpaOnnxTts.setVoice(voice)

            try {
                var orderIndex = if (shuffle) 0 else bookPlaylistOrder.indexOf(bookPlaylistCurrent).coerceAtLeast(0)
                var number = bookPlaylistOrder.getOrNull(orderIndex) ?: bookPlaylistCurrent
                while (isActive && generation == bookPlaylistGeneration && orderIndex < bookPlaylistOrder.size) {
                    while (bookPlaylistPaused && generation == bookPlaylistGeneration) delay(100)
                    if (generation != bookPlaylistGeneration) break

                    requestedBookTrack?.let { requested ->
                        requestedBookTrack = null
                        number = requested
                        orderIndex = bookPlaylistOrder.indexOf(requested).coerceAtLeast(orderIndex)
                    }
                    bookPlaylistCurrent = number
                    ChapterRecitationState.updateBookCurrent(number)
                    bookRenderer = BookRenderer.PREPARING

                    val hadith = runCatching { repository.getHadith(databaseFile, number) }
                        .onFailure { Log.e(TAG, "Unable to load $databaseFile Hadith #$number", it) }
                        .getOrNull()
                    val englishText = if (isBukhari) {
                        bukhariRepository.getEnglishText(number)
                            ?: hadith?.englishTextForServicePlayback()
                    } else {
                        hadith?.englishTextForServicePlayback()
                    }
                    if (englishText.isNullOrBlank()) {
                        Log.w(TAG, "Skipping Hadith #$number because it has no readable text")
                        orderIndex += 1
                        number = bookPlaylistOrder.getOrNull(orderIndex) ?: break
                        continue
                    }

                    // A skip can arrive while the database or translation is loading.
                    if (requestedBookTrack != null) continue
                    while (bookPlaylistPaused && generation == bookPlaylistGeneration) delay(100)
                    if (generation != bookPlaylistGeneration || requestedBookTrack != null) continue

                    val spokenText = if (language == "en" || isShamayel) {
                        englishText
                    } else {
                        runCatching {
                            translationService.translateFromEnglish(englishText, language)
                        }.getOrNull() ?: englishText
                    }

                    while (bookPlaylistPaused && generation == bookPlaylistGeneration) delay(100)
                    if (generation != bookPlaylistGeneration || requestedBookTrack != null) continue

                    currentTitle = "Hadith #$number"
                    currentSubtitle = collectionName
                    currentSource = null
                    isContinuousHandoff = true
                    startExternalPlayback(currentTitle, currentSubtitle)
                    ChapterRecitationState.publishBookTrack(number, true)
                    Log.i(
                        TAG,
                        "Service book playback: $collectionName #$number ($language), " +
                            "range=$rangeStart..$rangeEnd",
                    )

                    val completed = if (language == "bn" && isBukhari) {
                        playBukhariBengaliTrack(
                            number = number,
                            collectionName = collectionName,
                            englishText = englishText,
                            speakerId = speakerId,
                            generation = generation,
                        )
                    } else if (language != "en" && !isBukhari && !isShamayel) {
                        speakBookWithAndroidTts(
                            text = spokenText,
                            language = language,
                            collectionName = collectionName,
                        )
                    } else {
                        speakBookWithSherpa(
                            text = "${EnglishTtsTextNormalizer.hadithIntro(number, collectionName)} " +
                                englishText,
                            speakerId = speakerId,
                        )
                    }

                    if (generation != bookPlaylistGeneration) break
                    val requested = requestedBookTrack
                    if (requested != null) {
                        requestedBookTrack = null
                        number = requested
                        orderIndex = bookPlaylistOrder.indexOf(requested).coerceAtLeast(orderIndex)
                        continue
                    }
                    if (bookPlaylistPaused) {
                        while (bookPlaylistPaused && generation == bookPlaylistGeneration) delay(100)
                        // Android TTS cannot truly pause, so resume by reading this track again.
                        if (generation == bookPlaylistGeneration && !completed) continue
                    }
                    if (!completed) break
                    orderIndex += 1
                    number = bookPlaylistOrder.getOrNull(orderIndex) ?: break
                }
            } catch (e: Exception) {
                if (generation == bookPlaylistGeneration) {
                    Log.e(TAG, "Hadith book playback failed", e)
                }
            } finally {
                if (generation == bookPlaylistGeneration) {
                    Log.i(TAG, "Hadith book playback finished at #$bookPlaylistCurrent")
                    ChapterRecitationState.publishBookTrack(
                        bookPlaylistCurrent,
                        isPlaying = false,
                        isActive = false,
                    )
                    ChapterRecitationState.setBookPlaylist(active = false)
                    stopCurrentBookRenderer()
                    isExternalPlayback = false
                    isContinuousHandoff = false
                    releaseExternalPlaybackWakeLock()
                    ChapterRecitationState.markStopped()
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun playBukhariBengaliTrack(
        number: Int,
        collectionName: String,
        englishText: String,
        speakerId: Int,
        generation: Int,
    ): Boolean {
        var audioFile = audioDownloadHelper.resolveHadithAudioFile(number)
        if (audioFile == null) {
            val key = audioDownloadHelper.getHadithCdnKey(number)
            audioFile = when (audioDownloadHelper.downloadAudio(key)) {
                is AssetDownloadManager.DownloadState.Completed ->
                    audioDownloadHelper.resolveHadithAudioFile(number)
                else -> null
            }
        }
        if (audioFile != null) {
            val introCompleted = speakBookWithSherpa(
                EnglishTtsTextNormalizer.hadithIntro(number, collectionName),
                speakerId,
            )
            if (
                introCompleted &&
                generation == bookPlaylistGeneration &&
                requestedBookTrack == null
            ) {
                startExternalPlayback("Hadith #$number", collectionName)
                val recordingCompleted = playBookRecording(audioFile.absolutePath)
                if (recordingCompleted) return true
            }
        }
        if (generation != bookPlaylistGeneration || requestedBookTrack != null) return false
        return speakBookWithSherpa(
            "${EnglishTtsTextNormalizer.hadithIntro(number, collectionName)} $englishText",
            speakerId,
        )
    }

    private suspend fun speakBookWithSherpa(text: String, speakerId: Int): Boolean {
        if (!sherpaOnnxTts.hasRequiredAssets()) {
            Log.e(TAG, "Selected TTS voice is missing required assets")
            return false
        }
        bookRenderer = BookRenderer.SHERPA
        return try {
            sherpaOnnxTts.speakCachedOrGenerate(text = text, speakerId = speakerId)
        } finally {
            if (bookRenderer == BookRenderer.SHERPA) bookRenderer = BookRenderer.NONE
        }
    }

    private suspend fun playBookRecording(source: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            recordingContinuation = continuation
            recordingPrepared = false
            bookRenderer = BookRenderer.RECORDING
            isExternalPlayback = false
            currentSource = source

            val player = MediaPlayer().apply {
                setWakeMode(this@ChapterRecitationService, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setOnPreparedListener { prepared ->
                    recordingPrepared = true
                    updateMetadata(prepared.duration.toLong())
                    ChapterRecitationState.publishProgress(0, prepared.duration)
                    if (!bookPlaylistPaused) prepared.start()
                    updatePlaybackState(
                        if (bookPlaylistPaused) PlaybackStateCompat.STATE_PAUSED
                        else PlaybackStateCompat.STATE_PLAYING,
                    )
                    updateNotification()
                    startProgressUpdates()
                }
                setOnCompletionListener { finishBookRecording(true) }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Book recording error what=$what extra=$extra")
                    finishBookRecording(false)
                    true
                }
            }
            mediaPlayer = player
            startForegroundNotification()
            try {
                player.setDataSource(source)
                player.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to prepare book recording $source", e)
                finishBookRecording(false)
            }
            continuation.invokeOnCancellation {
                handler.post { finishBookRecording(false) }
            }
        }

    private fun finishBookRecording(completed: Boolean) {
        val continuation = recordingContinuation
        recordingContinuation = null
        recordingPrepared = false
        stopProgressUpdates()
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
        currentSource = null
        if (bookRenderer == BookRenderer.RECORDING) bookRenderer = BookRenderer.NONE
        if (continuation?.isActive == true) continuation.resume(completed)
    }

    private suspend fun speakBookWithAndroidTts(
        text: String,
        language: String,
        collectionName: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        androidTtsContinuation = continuation
        bookRenderer = BookRenderer.ANDROID_TTS
        val utteranceId = "hadith_service_${System.nanoTime()}"
        val locale = language.toTtsLocale()
        val intro = hadithCollectionIntro(collectionName, language)
        val speechText = "$intro $text"

        fun finish(result: Boolean) {
            if (androidTtsContinuation === continuation) androidTtsContinuation = null
            if (bookRenderer == BookRenderer.ANDROID_TTS) bookRenderer = BookRenderer.NONE
            if (continuation.isActive) continuation.resume(result)
        }

        fun start(tts: TextToSpeech) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { if (id == utteranceId) finish(true) }
                override fun onError(id: String?) { if (id == utteranceId) finish(false) }
                override fun onStop(id: String?, interrupted: Boolean) {
                    if (id == utteranceId) finish(false)
                }
            })
            tts.language = locale
            if (tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
                finish(false)
            }
        }

        continuation.invokeOnCancellation { androidTts?.stop() }
        androidTts?.let(::start) ?: run {
            lateinit var created: TextToSpeech
            created = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    androidTts = created
                    start(created)
                } else {
                    created.shutdown()
                    finish(false)
                }
            }
        }
    }

    private fun stopCurrentBookRenderer() {
        when (bookRenderer) {
            BookRenderer.SHERPA -> sherpaOnnxTts.stopSpeaking()
            BookRenderer.ANDROID_TTS -> {
                androidTts?.stop()
                val continuation = androidTtsContinuation
                androidTtsContinuation = null
                if (continuation?.isActive == true) continuation.resume(false)
            }
            BookRenderer.RECORDING -> finishBookRecording(false)
            BookRenderer.NONE, BookRenderer.PREPARING -> Unit
        }
        bookRenderer = BookRenderer.NONE
    }

    fun togglePlayPause() {
        if (ChapterRecitationState.isBookPlaylistActive) {
            if (bookPlaylistPaused) resumeBookPlaylist() else pauseBookPlaylist()
            return
        }
        if (isExternalPlayback) {
            if (ChapterRecitationState.isPlaying) pauseExternalPlayback()
            else resumeExternalPlayback()
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

    private fun pauseExternalPlayback() {
        if (!isExternalPlayback || !ChapterRecitationState.isPlaying) return
        val callback = ChapterRecitationState.onExternalPause
            ?: ChapterRecitationState.onExternalToggle
        if (callback == null) {
            com.starception.submission.media.GlobalMediaViewModel.onHadithFallbackStop?.invoke()
            stopPlaybackAndSelf()
            return
        }
        callback.invoke()
        ChapterRecitationState.publish(false, currentTitle, currentSubtitle)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
    }

    private fun pauseBookPlaylist() {
        if (!ChapterRecitationState.isBookPlaylistActive || bookPlaylistPaused) return
        bookPlaylistPaused = true
        when (bookRenderer) {
            BookRenderer.SHERPA -> sherpaOnnxTts.pauseSpeaking()
            BookRenderer.ANDROID_TTS -> androidTts?.stop()
            BookRenderer.RECORDING -> if (recordingPrepared) {
                runCatching { mediaPlayer?.pause() }
                stopProgressUpdates()
            }
            BookRenderer.NONE, BookRenderer.PREPARING -> Unit
        }
        ChapterRecitationState.publish(false, currentTitle, currentSubtitle)
        ChapterRecitationState.publishBookTrack(bookPlaylistCurrent, false)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
    }

    private fun resumeBookPlaylist() {
        if (!ChapterRecitationState.isBookPlaylistActive || !bookPlaylistPaused) return
        bookPlaylistPaused = false
        when (bookRenderer) {
            BookRenderer.SHERPA -> sherpaOnnxTts.resumeSpeaking()
            BookRenderer.RECORDING -> if (recordingPrepared) {
                runCatching { mediaPlayer?.start() }
                startProgressUpdates()
            }
            BookRenderer.ANDROID_TTS, BookRenderer.NONE, BookRenderer.PREPARING -> Unit
        }
        ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
        ChapterRecitationState.publishBookTrack(bookPlaylistCurrent, true)
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateNotification()
    }

    private fun resumeExternalPlayback() {
        if (!isExternalPlayback || ChapterRecitationState.isPlaying) return
        val callback = ChapterRecitationState.onExternalPlay
            ?: ChapterRecitationState.onExternalToggle
        if (callback == null) {
            stopPlaybackAndSelf()
            return
        }
        callback.invoke()
        ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateNotification()
    }

    /** Route car/Bluetooth previous/next controls into an active Bukhari book playlist. */
    private fun skipHadith(next: Boolean) {
        if (!currentTitle.startsWith("Hadith #")) return
        if (ChapterRecitationState.isBookPlaylistActive) {
            val currentIndex = bookPlaylistOrder.indexOf(bookPlaylistCurrent)
                .coerceAtLeast(0)
            val requestedIndex = (currentIndex + if (next) 1 else -1)
                .coerceIn(0, bookPlaylistOrder.lastIndex)
            val requested = bookPlaylistOrder[requestedIndex]
            if (requested == bookPlaylistCurrent) return
            requestedBookTrack = requested
            bookPlaylistPaused = false
            ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            updateNotification()
            stopCurrentBookRenderer()
            return
        }
        val accepted = if (next) {
            ChapterRecitationState.onSkipNext?.invoke()
        } else {
            ChapterRecitationState.onSkipPrevious?.invoke()
        } == true
        if (!accepted) return

        ChapterRecitationState.publish(true, currentTitle, currentSubtitle)
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
        updateNotification()

        if (!isExternalPlayback && isContinuousHandoff) {
            stopProgressUpdates()
            mediaPlayer?.let { runCatching { it.stop() }; it.release() }
            mediaPlayer = null
            ChapterRecitationState.onHadithCompletion?.invoke()
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
        bookPlaylistGeneration += 1
        bookPlaylistJob?.cancel()
        bookPlaylistJob = null
        stopCurrentBookRenderer()
        if (ChapterRecitationState.isBookPlaylistActive) {
            ChapterRecitationState.publishBookTrack(
                bookPlaylistCurrent,
                isPlaying = false,
                isActive = false,
            )
        }
        ChapterRecitationState.setBookPlaylist(active = false)
        stopProgressUpdates()
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
        currentSource = null
        isExternalPlayback = false
        isContinuousHandoff = false
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
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
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
            mediaPlayer?.isPlaying == true ||
                (isContinuousHandoff && ChapterRecitationState.isPlaying)
        }
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPending = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
                    .setShowActionsInCompactView(
                        *if (ChapterRecitationState.isBookPlaylistActive) {
                            intArrayOf(0, 1, 2)
                        } else {
                            intArrayOf(0)
                        },
                    ),
            )
        if (ChapterRecitationState.isBookPlaylistActive) {
            builder.addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                ),
            )
        }
        builder
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY,
                ),
            )
        if (ChapterRecitationState.isBookPlaylistActive) {
            builder.addAction(
                android.R.drawable.ic_media_next,
                "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                ),
            )
        }
        return builder.build()
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
        bookPlaylistGeneration += 1
        bookPlaylistJob?.cancel()
        bookPlaylistJob = null
        stopCurrentBookRenderer()
        serviceScope.cancel()
        androidTts?.stop()
        androidTts?.shutdown()
        androidTts = null
        ChapterRecitationState.setBookPlaylist(active = false)
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
    /** Distinct commands preserve a suspended Bukhari playlist instead of toggling it off. */
    var onExternalPause: (() -> Unit)? = null
    var onExternalPlay: (() -> Unit)? = null
    /** Car/Bluetooth transport controls. Return true when the playlist accepted the jump. */
    var onSkipNext: (() -> Boolean)? = null
    var onSkipPrevious: (() -> Boolean)? = null
    /** Independent book-screen observer; the foreground service remains the queue owner. */
    var onBookTrackChanged: ((hadithNumber: Int, isPlaying: Boolean, isActive: Boolean, collectionName: String) -> Unit)? = null

    @Volatile
    internal var onSourcePlaybackRequested: ((String, String, String, Boolean) -> Unit)? = null
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
    @Volatile var isBookPlaylistActive: Boolean = false
        private set
    @Volatile var bookCurrentHadith: Int = 0
        private set
    @Volatile var bookRangeStart: Int = 0
        private set
    @Volatile var bookRangeEnd: Int = 0
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

    internal fun setBookPlaylist(
        active: Boolean,
        currentHadith: Int = 0,
        rangeStart: Int = 0,
        rangeEnd: Int = 0,
    ) {
        isBookPlaylistActive = active
        if (active) {
            bookCurrentHadith = currentHadith
            bookRangeStart = rangeStart
            bookRangeEnd = rangeEnd
        } else {
            bookCurrentHadith = 0
            bookRangeStart = 0
            bookRangeEnd = 0
        }
    }

    internal fun updateBookCurrent(hadithNumber: Int) {
        bookCurrentHadith = hadithNumber
    }

    internal fun publishBookTrack(
        hadithNumber: Int,
        isPlaying: Boolean,
        isActive: Boolean = true,
    ) {
        bookCurrentHadith = hadithNumber
        onBookTrackChanged?.invoke(hadithNumber, isPlaying, isActive, subtitle)
    }

    internal fun requestSourcePlayback(
        source: String,
        title: String,
        subtitle: String,
        continuousHandoff: Boolean,
    ): Boolean {
        val callback = onSourcePlaybackRequested ?: return false
        callback(source, title, subtitle, continuousHandoff)
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

private fun Hadith.englishTextForServicePlayback(): String? {
    englishText?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val plain = textPlain?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (plain.startsWith("বাংলা\n")) return null
    if (!plain.startsWith("English\n")) return plain
    return plain
        .removePrefix("English\n")
        .substringBefore("\n\nবাংলা\n")
        .trim()
        .takeIf(String::isNotEmpty)
}

private fun String.toTtsLocale(): Locale = when (this) {
    "ar" -> Locale.forLanguageTag("ar")
    "bn" -> Locale.forLanguageTag("bn-BD")
    "es" -> Locale.forLanguageTag("es-ES")
    "fr" -> Locale.FRANCE
    "id" -> Locale.forLanguageTag("id-ID")
    "ru" -> Locale.forLanguageTag("ru-RU")
    "sv" -> Locale.forLanguageTag("sv-SE")
    "tr" -> Locale.forLanguageTag("tr-TR")
    "ur" -> Locale.forLanguageTag("ur-PK")
    "zh" -> Locale.SIMPLIFIED_CHINESE
    else -> Locale.US
}

private fun hadithCollectionIntro(collectionName: String, language: String): String {
    val isBukhari = collectionName.contains("bukhari", ignoreCase = true)
    if (!isBukhari) {
        val isShamayel = collectionName.contains("shamai", ignoreCase = true) ||
            collectionName.contains("shamay", ignoreCase = true)
        return when {
            isShamayel && language == "bn" -> "শামায়েলে তিরমিযি থেকে।"
            isShamayel && language == "ar" -> "من الشمائل المحمدية."
            else -> "From $collectionName."
        }
    }
    return when (language) {
        "bn" -> "সহীহ আল-বুখারী থেকে।"
        "ar" -> "من صحيح البخاري."
        "es" -> "De Sahih Al-Bujari."
        "fr" -> "De Sahih Al-Boukhari."
        "id" -> "Dari Sahih Al-Bukhari."
        "ru" -> "Из Сахих аль-Бухари."
        "tr" -> "Sahih-i Buhari'den."
        "ur" -> "صحیح البخاری سے۔"
        "zh" -> "来自《布哈里圣训》。"
        else -> "From Sahih Al-Bukhari."
    }
}
