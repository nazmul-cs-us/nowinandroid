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
        when (activeSource) {
            is MediaSource.Quran -> quranService?.togglePlayPause()
            is MediaSource.DrivingMode -> drivingService?.resume()
            is MediaSource.None -> {}
        }
    }

    fun pause() {
        when (activeSource) {
            is MediaSource.Quran -> quranService?.togglePlayPause()
            is MediaSource.DrivingMode -> drivingService?.pause()
            is MediaSource.None -> {}
        }
    }

    fun skipNext() {
        when (activeSource) {
            is MediaSource.Quran -> quranService?.playNext()
            is MediaSource.DrivingMode -> drivingService?.skipCurrent()
            is MediaSource.None -> {}
        }
    }

    fun skipPrevious() {
        when (activeSource) {
            is MediaSource.Quran -> quranService?.playPrevious()
            is MediaSource.DrivingMode -> {} // Driving mode doesn't support previous
            is MediaSource.None -> {}
        }
    }

    fun seekTo(position: Int) {
        when (activeSource) {
            is MediaSource.Quran -> quranService?.seekTo(position)
            is MediaSource.DrivingMode -> {} // Driving mode doesn't support seek
            is MediaSource.None -> {}
        }
    }

    fun setVolume(volume: Float) {
        when (activeSource) {
            is MediaSource.Quran -> quranService?.setVolume(volume)
            is MediaSource.DrivingMode -> {} // Volume managed by system
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

    fun cleanup() {
        // Remove static listener
        DrivingAudioService.onServiceStartedListener = null

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
