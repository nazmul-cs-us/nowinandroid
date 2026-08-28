package com.starception.submission.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.starception.submission.feature.quran.AudioLanguage
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.feature.quran.QuranPlaybackService
import com.starception.submission.services.DrivingAudioService
import com.starception.submission.services.ChapterRecitationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Global media controller that provides unified playback state
 * and controls for all audio sources (Quran, Driving Mode, future sources).
 *
 * Binds to active services and exposes a single [controllerState] flow
 * that the PullToSyncContainer uses to render the media mini-bar and
 * expanded controls on any screen.
 *
 * Created and owned by [MainActivityViewModel] to share its lifecycle.
 */
class GlobalMediaViewModel(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val TAG = "GlobalMediaVM"

        /**
         * Static listener for hadith playback state changes from HadithDetailScreen.
         * Set by GlobalMediaViewModel on init so the composable can notify it
         * without needing a direct reference.
         */
        var onHadithPlaybackChanged: ((isPlaying: Boolean, hadithNumber: Int, collectionName: String, title: String) -> Unit)? = null

        /**
         * Static listener for hadith playback progress updates (position/duration in ms).
         */
        var onHadithProgressChanged: ((currentPosition: Int, duration: Int) -> Unit)? = null

        /**
         * Reverse channel: invoked when the global mini-bar requests play/pause for an active
         * hadith source. HadithDetailScreen registers this and toggles its own playback.
         */
        var onHadithPlayPauseRequested: (() -> Unit)? = null

        /**
         * Reverse channels for skip prev/next on hadith — navigate to adjacent hadith.
         */
        var onHadithSkipNextRequested: (() -> Unit)? = null
        var onHadithSkipPreviousRequested: (() -> Unit)? = null

        /**
         * Fallback stop for hadith TTS when the HadithDetailScreen (which owns
         * the play/pause callbacks) is no longer composed but Sherpa audio is
         * still speaking. Wired by MainActivityViewModel to stop the TTS engine.
         */
        var onHadithFallbackStop: (() -> Unit)? = null

        // ---- Fortress (chapter recitation) — mirrors the hadith callback pattern ----
        /** Notify state changes from ChapterAudioController (via the app bridge). */
        var onFortressPlaybackChanged: ((isPlaying: Boolean, title: String) -> Unit)? = null
        /** Progress updates (position/duration in ms) for the mini-bar sweep. */
        var onFortressProgressChanged: ((currentPosition: Int, duration: Int) -> Unit)? = null
        /** Reverse channel: mini-bar play/pause → toggle ChapterAudioController. */
        var onFortressPlayPauseRequested: (() -> Unit)? = null
        /** Reverse channel: mini-bar seek → seek ChapterAudioController. */
        var onFortressSeekRequested: ((position: Int) -> Unit)? = null
    }

    private val _controllerState = MutableStateFlow(MediaControllerUiState())
    val controllerState: StateFlow<MediaControllerUiState> = _controllerState.asStateFlow()

    // --- Quran service connection ---
    private var quranService: QuranPlaybackService? = null
    private var quranBound = false

    private val quranConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val myBinder = binder as QuranPlaybackService.QuranBinder
            quranService = myBinder.getService()
            quranBound = true
            Log.d(TAG, "Quran service connected")

            // Wire up secondary callbacks (does not overwrite QuranPlayerViewModel's callbacks)
            quranService?.onGlobalPlaybackStateChanged = { playing ->
                if (playing) {
                    // Auto-detect: playback started, show controller
                    activeSource = MediaSource.Quran(
                        surahIndex = quranService?.getCurrentSurahIndex() ?: 0,
                    )
                    updateQuranState()
                } else if (quranService?.getCurrentPosition() == 0) {
                    // Playback stopped completely
                    if (activeSource is MediaSource.Quran) {
                        _controllerState.update { it.copy(
                            playback = it.playback.copy(isPlaying = false)
                        )}
                    }
                } else {
                    // Just paused
                    updateQuranState()
                }
            }
            quranService?.onGlobalSurahChanged = { _ ->
                updateQuranState()
            }

            // Sync initial state — if already playing, show controller immediately
            val isAlreadyPlaying = quranService?.isPlaying() ?: false
            if (isAlreadyPlaying) {
                activeSource = MediaSource.Quran(
                    surahIndex = quranService?.getCurrentSurahIndex() ?: 0,
                )
                updateQuranState()
            }
            startQuranProgressTracking()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            quranService = null
            quranBound = false
            Log.d(TAG, "Quran service disconnected")
            if (activeSource is MediaSource.Quran) {
                hideController()
            }
        }
    }

    // --- Driving service connection ---
    private var drivingService: DrivingAudioService? = null
    private var drivingBound = false

    private val drivingConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val myBinder = binder as DrivingAudioService.DrivingAudioBinder
            drivingService = myBinder.getService()
            drivingBound = true
            Log.d(TAG, "Driving service connected")

            // Wire up secondary state change callback (does not overwrite primary callback)
            drivingService?.onGlobalStateChanged = { state ->
                updateDrivingState(state)
            }

            // Sync initial state
            val currentState = drivingService?.getCurrentState()
                ?: DrivingAudioService.PlaybackState.IDLE
            updateDrivingState(currentState)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            drivingService = null
            drivingBound = false
            Log.d(TAG, "Driving service disconnected")
            if (activeSource is MediaSource.DrivingMode) {
                hideController()
            }
        }
    }

    private var activeSource: MediaSource = MediaSource.None

    private val chapterRecitationStateListener: (Boolean, String, String) -> Unit =
        { isPlaying, title, subtitle ->
            updateChapterRecitationState(isPlaying, title, subtitle)
        }
    private val chapterRecitationProgressListener: (Int, Int) -> Unit =
        { currentPosition, duration ->
            if (activeSource is MediaSource.Hadith || activeSource is MediaSource.Fortress) {
                _controllerState.update { current ->
                    current.copy(
                        playback = current.playback.copy(
                            currentPosition = currentPosition,
                            duration = duration,
                        ),
                    )
                }
            }
        }

    init {
        // Auto-bind to Quran service if it's already running.
        // Uses BIND_AUTO_CREATE so the service is created if needed, but won't
        // show the media controller until actual playback starts (handled by callbacks).
        try {
            val quranIntent = Intent(context, QuranPlaybackService::class.java)
            context.bindService(quranIntent, quranConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Auto-binding to QuranPlaybackService")
        } catch (e: Exception) {
            Log.w(TAG, "QuranPlaybackService not available for auto-bind: ${e.message}")
        }

        // DrivingAudioService is only started explicitly, so register a static listener
        // to auto-detect when it starts and bind to it.
        DrivingAudioService.onServiceStartedListener = {
            Log.d(TAG, "DrivingAudioService started — auto-binding")
            onDrivingPlaybackStarted()
        }

        // Register hadith playback listener so HadithDetailScreen can notify us
        onHadithPlaybackChanged = { isPlaying, hadithNumber, collectionName, title ->
            if (isPlaying) {
                onHadithPlaybackStarted(hadithNumber, collectionName, title)
            } else {
                onHadithPlaybackStopped()
            }
        }

        // Register hadith progress listener for horizontal sweep
        onHadithProgressChanged = { currentPosition, duration ->
            if (activeSource is MediaSource.Hadith) {
                _controllerState.update { current ->
                    current.copy(
                        playback = current.playback.copy(
                            currentPosition = currentPosition,
                            duration = duration,
                        )
                    )
                }
            }
        }

        // Register fortress (chapter recitation) listeners — same shape as hadith.
        onFortressPlaybackChanged = { isPlaying, title ->
            if (isPlaying) {
                onFortressPlaybackStarted(title)
            } else {
                onFortressPlaybackStopped()
            }
        }
        onFortressProgressChanged = { currentPosition, duration ->
            if (activeSource is MediaSource.Fortress) {
                _controllerState.update { current ->
                    current.copy(
                        playback = current.playback.copy(
                            currentPosition = currentPosition,
                            duration = duration,
                        )
                    )
                }
            }
        }

        // Observe the foreground recitation service directly. Bukhari playback can outlive the
        // detail composable, so relying only on its UI callback leaves PullToSyncContainer stale.
        ChapterRecitationState.onGlobalStateChanged = chapterRecitationStateListener
        ChapterRecitationState.onGlobalProgressChanged = chapterRecitationProgressListener
        if (ChapterRecitationState.isActive) {
            updateChapterRecitationState(
                ChapterRecitationState.isPlaying,
                ChapterRecitationState.title,
                ChapterRecitationState.subtitle,
            )
        }
    }

    // --- Public API: called by services/ViewModels to show the controller ---

    /**
     * Called when Quran playback starts. Binds to the service and shows the controller.
     */
    fun onQuranPlaybackStarted() {
        activeSource = MediaSource.Quran()
        if (!quranBound) {
            val intent = Intent(context, QuranPlaybackService::class.java)
            try {
                context.bindService(intent, quranConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind QuranPlaybackService", e)
            }
        } else {
            updateQuranState()
        }
        _controllerState.update { it.copy(
            isVisible = true,
            hasLanguageToggle = true,
        )}
    }

    /**
     * Called when Driving mode audio starts. Binds to the service and shows the controller.
     */
    fun onDrivingPlaybackStarted() {
        activeSource = MediaSource.DrivingMode()
        if (!drivingBound) {
            val intent = Intent(context, DrivingAudioService::class.java)
            try {
                context.bindService(intent, drivingConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind DrivingAudioService", e)
            }
        } else {
            val state = drivingService?.getCurrentState()
                ?: DrivingAudioService.PlaybackState.IDLE
            updateDrivingState(state)
        }
        _controllerState.update { it.copy(
            isVisible = true,
            hasLanguageToggle = false,
        )}
    }

    /**
     * Called when hadith playback starts from HadithDetailScreen.
     */
    private fun onHadithPlaybackStarted(hadithNumber: Int, collectionName: String, title: String) {
        activeSource = MediaSource.Hadith(hadithNumber = hadithNumber, collectionName = collectionName)
        _controllerState.update { current ->
            current.copy(
                isVisible = true,
                hasLanguageToggle = false,
                playback = MediaPlaybackState(
                    isPlaying = true,
                    title = title,
                    subtitle = collectionName,
                    currentPosition = 0,
                    duration = 0,
                    source = activeSource,
                ),
            )
        }
        Log.d(TAG, "Hadith playback started: #$hadithNumber from $collectionName")
    }

    /**
     * Called when hadith playback stops from HadithDetailScreen.
     */
    private fun onHadithPlaybackStopped() {
        if (activeSource is MediaSource.Hadith) {
            val recitation = ChapterRecitationState
            if (recitation.isActive && recitation.title.startsWith("Hadith #")) {
                updateChapterRecitationState(
                    recitation.isPlaying,
                    recitation.title,
                    recitation.subtitle,
                )
            } else {
                hideController()
            }
        }
    }

    /** Mirrors the foreground recitation service into the global media controller. */
    private fun updateChapterRecitationState(
        isPlaying: Boolean,
        title: String,
        subtitle: String,
    ) {
        if (!ChapterRecitationState.isActive) {
            if (activeSource is MediaSource.Hadith || activeSource is MediaSource.Fortress) {
                hideController()
            }
            return
        }

        if (title.startsWith("Hadith #")) {
            val hadithNumber = title.substringAfter('#').toIntOrNull() ?: 0
            val collectionName = subtitle.ifBlank { "Sahih Bukhari" }
            activeSource = MediaSource.Hadith(hadithNumber, collectionName)
            _controllerState.update { current ->
                current.copy(
                    isVisible = true,
                    hasLanguageToggle = false,
                    playback = MediaPlaybackState(
                        isPlaying = isPlaying,
                        title = title,
                        subtitle = collectionName,
                        currentPosition = ChapterRecitationState.positionMs,
                        duration = ChapterRecitationState.durationMs,
                        source = activeSource,
                    ),
                )
            }
        } else {
            onFortressPlaybackStarted(title)
            _controllerState.update { current ->
                current.copy(
                    playback = current.playback.copy(
                        isPlaying = isPlaying,
                        currentPosition = ChapterRecitationState.positionMs,
                        duration = ChapterRecitationState.durationMs,
                    ),
                )
            }
        }
    }

    /**
     * Called when a Fortress chapter recitation starts/resumes (via the app bridge from
     * ChapterAudioController). Shows the mini-bar with a seekable progress sweep.
     */
    private fun onFortressPlaybackStarted(title: String) {
        activeSource = MediaSource.Fortress(title = title)
        // Surface the Interests topic of the playing dua ("Quranic Duas",
        // "Hadith", …) on the subtitle line — set by the Dua screen's play
        // buttons. NOT the chapter name: the title line already carries the
        // chapter, so repeating it read as a bug.
        val topicName = com.starception.submission.core.ui.ChapterAudioController.currentTopic
            ?.trim()?.takeIf { it.isNotEmpty() }
        val subtitle = if (topicName != null) {
            "Fortress of the Muslim · $topicName"
        } else {
            "Fortress of the Muslim"
        }
        _controllerState.update { current ->
            // Preserve position/duration if this is just a resume of the same track.
            val keepProgress = current.playback.source is MediaSource.Fortress &&
                current.playback.title == title
            current.copy(
                isVisible = true,
                hasLanguageToggle = false,
                playback = current.playback.copy(
                    isPlaying = true,
                    title = title,
                    subtitle = subtitle,
                    currentPosition = if (keepProgress) current.playback.currentPosition else 0,
                    duration = if (keepProgress) current.playback.duration else 0,
                    source = activeSource,
                ),
            )
        }
        Log.d(TAG, "Fortress playback started: $title")
    }

    /**
     * Called when Fortress playback pauses or stops. Pause keeps the bar visible (paused);
     * a full stop/completion hides it.
     */
    private fun onFortressPlaybackStopped() {
        if (activeSource is MediaSource.Fortress) {
            hideController()
        }
    }

    /**
     * Called when playback stops from a source. Hides the controller.
     */
    fun onPlaybackStopped() {
        hideController()
    }

    // --- Public API: UI actions dispatched from PullToSyncContainer ---

    fun handleAction(action: MediaAction) {
        when (action) {
            is MediaAction.Play -> play()
            is MediaAction.Pause -> pause()
            is MediaAction.SkipNext -> skipNext()
            is MediaAction.SkipPrevious -> skipPrevious()
            is MediaAction.SeekTo -> seekTo(action.position)
            is MediaAction.SetVolume -> setVolume(action.volume)
            is MediaAction.ToggleLanguage -> toggleLanguage()
            is MediaAction.Dismiss -> dismiss()
        }
    }

    fun play() {
        Log.d(TAG, "▶️ play() | source=${activeSource::class.simpleName} | hadithCb=${onHadithPlayPauseRequested != null}")
        when (activeSource) {
            is MediaSource.Quran -> quranService?.togglePlayPause()
            is MediaSource.DrivingMode -> drivingService?.resume()
            is MediaSource.Hadith -> toggleHadithOrFallbackStop()
            is MediaSource.Fortress -> onFortressPlayPauseRequested?.invoke()
            is MediaSource.None -> {}
        }
    }

    fun pause() {
        Log.d(TAG, "⏸️ pause() | source=${activeSource::class.simpleName} | hadithCb=${onHadithPlayPauseRequested != null}")
        when (activeSource) {
            is MediaSource.Quran -> quranService?.togglePlayPause()
            is MediaSource.DrivingMode -> drivingService?.pause()
            is MediaSource.Hadith -> toggleHadithOrFallbackStop()
            is MediaSource.Fortress -> onFortressPlayPauseRequested?.invoke()
            is MediaSource.None -> {}
        }
    }

    /**
     * Hadith play/pause: normally handled by HadithDetailScreen's registered
     * callback. If that screen is gone (user navigated away while Sherpa TTS
     * keeps reading), stop the TTS engine directly and clear the mini-bar.
     */
    private fun toggleHadithOrFallbackStop() {
        val callback = onHadithPlayPauseRequested
        if (callback != null) {
            callback.invoke()
        } else {
            onHadithFallbackStop?.invoke()
            onHadithPlaybackChanged?.invoke(false, 0, "", "")
        }
    }

    fun skipNext() {
        Log.d(TAG, "⏭️ skipNext() | source=${activeSource::class.simpleName} | hadithCb=${onHadithSkipNextRequested != null}")
        when (activeSource) {
            is MediaSource.Quran -> quranService?.playNext()
            is MediaSource.DrivingMode -> drivingService?.skipCurrent()
            is MediaSource.Hadith -> onHadithSkipNextRequested?.invoke()
            is MediaSource.Fortress -> {} // Single-track player; no chapter skip
            is MediaSource.None -> {}
        }
    }

    fun skipPrevious() {
        Log.d(TAG, "⏮️ skipPrevious() | source=${activeSource::class.simpleName} | hadithCb=${onHadithSkipPreviousRequested != null}")
        when (activeSource) {
            is MediaSource.Quran -> quranService?.playPrevious()
            is MediaSource.DrivingMode -> {} // Driving mode doesn't support previous
            is MediaSource.Hadith -> onHadithSkipPreviousRequested?.invoke()
            is MediaSource.Fortress -> {} // Single-track player; no chapter skip
            is MediaSource.None -> {}
        }
    }

    fun seekTo(position: Int) {
        when (activeSource) {
            is MediaSource.Quran -> quranService?.seekTo(position)
            is MediaSource.DrivingMode -> {} // Driving mode doesn't support seek
            is MediaSource.Hadith -> {} // Hadith doesn't support seek
            is MediaSource.Fortress -> onFortressSeekRequested?.invoke(position)
            is MediaSource.None -> {}
        }
    }

    fun setVolume(volume: Float) {
        when (activeSource) {
            is MediaSource.Quran -> quranService?.setVolume(volume)
            is MediaSource.DrivingMode -> {} // Volume managed by system
            is MediaSource.Hadith -> {} // Volume managed by system
            is MediaSource.Fortress -> {} // Volume managed by system
            is MediaSource.None -> {}
        }
        _controllerState.update { it.copy(volume = volume) }
    }

    fun toggleLanguage() {
        // Only applicable for Quran source
        if (activeSource is MediaSource.Quran) {
            val current = _controllerState.value.currentLanguage
            val next = when (current) {
                AudioLanguage.ARABIC_ONLY -> AudioLanguage.BENGALI_TRANSLATION
                AudioLanguage.BENGALI_TRANSLATION -> AudioLanguage.ENGLISH_TRANSLATION
                AudioLanguage.ENGLISH_TRANSLATION -> AudioLanguage.ARABIC_ONLY
            }
            quranService?.setAudioLanguage(next)
            _controllerState.update { it.copy(
                currentLanguage = next,
                playback = it.playback.copy(
                    source = MediaSource.Quran(
                        surahIndex = quranService?.getCurrentSurahIndex() ?: 0,
                        audioLanguage = next,
                    )
                )
            )}
        }
    }

    fun dismiss() {
        _controllerState.update { it.copy(isVisible = false) }
    }

    // --- Internal state updates ---

    private fun updateQuranState() {
        val service = quranService ?: return
        val surahIndex = service.getCurrentSurahIndex()
        val surah = QuranData.surahs.getOrNull(surahIndex)
        val isPlaying = service.isPlaying()

        activeSource = MediaSource.Quran(
            surahIndex = surahIndex,
            audioLanguage = _controllerState.value.currentLanguage,
        )

        _controllerState.update { current ->
            current.copy(
                isVisible = true,
                hasLanguageToggle = true,
                playback = MediaPlaybackState(
                    isPlaying = isPlaying,
                    title = surah?.nameArabic ?: "Quran",
                    subtitle = surah?.nameEnglish ?: "",
                    currentPosition = service.getCurrentPosition(),
                    duration = service.getDuration(),
                    source = activeSource,
                ),
            )
        }
    }

    private fun updateDrivingState(state: DrivingAudioService.PlaybackState) {
        val service = drivingService ?: return

        if (state == DrivingAudioService.PlaybackState.IDLE) {
            if (activeSource is MediaSource.DrivingMode) {
                hideController()
            }
            return
        }

        val phase = when (state) {
            DrivingAudioService.PlaybackState.PLAYING_TRAVEL_DUA -> "Travel Dua"
            DrivingAudioService.PlaybackState.PLAYING_HADITH_AUDIO -> "Hadith"
            DrivingAudioService.PlaybackState.PLAYING_HADITH_TTS -> "Hadith"
            DrivingAudioService.PlaybackState.PLAYING_QURAN -> "Quran"
            DrivingAudioService.PlaybackState.VOICE_PROMPT -> "Voice Prompt"
            DrivingAudioService.PlaybackState.PAUSED -> "Paused"
            DrivingAudioService.PlaybackState.IDLE -> ""
        }

        activeSource = MediaSource.DrivingMode(phase = phase)

        val isPlaying = state != DrivingAudioService.PlaybackState.PAUSED &&
            state != DrivingAudioService.PlaybackState.IDLE

        _controllerState.update { current ->
            current.copy(
                isVisible = true,
                hasLanguageToggle = false,
                playback = MediaPlaybackState(
                    isPlaying = isPlaying,
                    title = phase,
                    subtitle = "Driving Mode",
                    currentPosition = 0,
                    duration = 0,
                    source = activeSource,
                ),
            )
        }
    }

    private fun hideController() {
        activeSource = MediaSource.None
        _controllerState.update {
            MediaControllerUiState()
        }
    }

    private fun startQuranProgressTracking() {
        scope.launch {
            while (isActive) {
                if (activeSource is MediaSource.Quran && quranService != null) {
                    val service = quranService!!
                    if (service.isPlaying()) {
                        _controllerState.update { current ->
                            current.copy(
                                playback = current.playback.copy(
                                    currentPosition = service.getCurrentPosition(),
                                    duration = service.getDuration(),
                                    isPlaying = true,
                                )
                            )
                        }
                    }
                }
                delay(500)
            }
        }
    }

    /**
     * Re-check active services and restore the controller if something is still playing.
     * Called on app resume so the mini-bar reappears after dismiss + background.
     */
    fun resync() {
        // Check Quran service
        val qs = quranService
        if (qs != null && qs.isPlaying()) {
            activeSource = MediaSource.Quran(
                surahIndex = qs.getCurrentSurahIndex(),
                audioLanguage = _controllerState.value.currentLanguage,
            )
            updateQuranState()
            return
        }

        // Check Driving service
        val ds = drivingService
        if (ds != null) {
            val state = ds.getCurrentState()
            if (state != DrivingAudioService.PlaybackState.IDLE) {
                updateDrivingState(state)
                return
            }
        }

        // Check the Fortress/Hadith recitation service (survives app close/reopen). Its state is
        // a process-wide snapshot, so we can restore the mini-bar without a bound connection.
        val recite = com.starception.submission.services.ChapterRecitationState
        if (recite.isActive) {
            if (recite.title.startsWith("Hadith #")) {
                onHadithPlaybackStarted(
                    hadithNumber = recite.title.substringAfter('#').toIntOrNull() ?: 0,
                    collectionName = recite.subtitle.ifBlank { "Sahih Bukhari" },
                    title = recite.title,
                )
            } else {
                onFortressPlaybackStarted(recite.title)
            }
            _controllerState.update { current ->
                current.copy(
                    playback = current.playback.copy(
                        isPlaying = recite.isPlaying,
                        currentPosition = recite.positionMs,
                        duration = recite.durationMs,
                    ),
                )
            }
            return
        }
    }

    fun cleanup() {
        // Remove static listener
        DrivingAudioService.onServiceStartedListener = null
        if (ChapterRecitationState.onGlobalStateChanged === chapterRecitationStateListener) {
            ChapterRecitationState.onGlobalStateChanged = null
        }
        if (ChapterRecitationState.onGlobalProgressChanged === chapterRecitationProgressListener) {
            ChapterRecitationState.onGlobalProgressChanged = null
        }

        if (quranBound) {
            try {
                // Remove our secondary callbacks before unbinding
                quranService?.onGlobalPlaybackStateChanged = null
                quranService?.onGlobalSurahChanged = null
                context.unbindService(quranConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Quran service not registered: ${e.message}")
            }
            quranBound = false
            quranService = null
        }
        if (drivingBound) {
            try {
                drivingService?.onGlobalStateChanged = null
                context.unbindService(drivingConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Driving service not registered: ${e.message}")
            }
            drivingBound = false
            drivingService = null
        }
    }
}
