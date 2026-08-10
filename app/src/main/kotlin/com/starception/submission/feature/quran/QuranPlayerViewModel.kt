package com.starception.submission.feature.quran

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AudioDownloadHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class QuranPlayerViewModel(
    private val context: Context,
    private val audioDownloadHelper: AudioDownloadHelper,
) : ViewModel() {

    private var playbackService: QuranPlaybackService? = null
    private var serviceBound = false
    private var pendingPlaySurahIndex: Int? = null

    // Player state - using private backing fields for internal mutation
    private var _isPlaying = mutableStateOf(false)
    val isPlaying: Boolean get() = _isPlaying.value

    private var _currentSurahIndex = mutableStateOf(0)
    val currentSurahIndex: Int get() = _currentSurahIndex.value

    private var _currentPosition = mutableStateOf(0)
    val currentPosition: Int get() = _currentPosition.value

    private var _duration = mutableStateOf(0)
    val duration: Int get() = _duration.value

    private var _isLoading = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value

    private var _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: String? get() = _errorMessage.value

    private var _needsAudioPermission = mutableStateOf(false)
    val needsAudioPermission: Boolean get() = _needsAudioPermission.value

    val currentSurah: Surah
        get() = QuranData.surahs[currentSurahIndex]

    private var _audioLanguage = mutableStateOf(AudioLanguage.ARABIC_ONLY)
    val audioLanguage: AudioLanguage get() = _audioLanguage.value

    // Download state for on-demand audio downloading
    private var _downloadProgress = mutableStateOf(0f)
    val downloadProgress: Float get() = _downloadProgress.value

    private var _isDownloading = mutableStateOf(false)
    val isDownloading: Boolean get() = _isDownloading.value

    private var _downloadError = mutableStateOf<String?>(null)
    val downloadError: String? get() = _downloadError.value

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val myBinder = binder as QuranPlaybackService.QuranBinder
            playbackService = myBinder.getService()
            serviceBound = true

            // Set up callbacks
            playbackService?.onPlaybackStateChanged = { playing ->
                _isPlaying.value = playing
                _isLoading.value = false
            }
            playbackService?.onSurahChanged = { index ->
                _currentSurahIndex.value = index
            }
            playbackService?.onProgressChanged = { position, duration ->
                _currentPosition.value = position
                _duration.value = duration
            }

            // Wire up on-demand download callback
            playbackService?.onAudioNeedsDownload = { cdnKey, _ ->
                Log.i("QuranPlayerViewModel", "Audio needs download: $cdnKey")
                triggerOnDemandDownload(cdnKey)
            }

            // Set initial language (helper is injected directly into the service via Hilt)
            playbackService?.setAudioLanguage(audioLanguage)

            // Sync current state
            _isPlaying.value = playbackService?.isPlaying() ?: false
            _currentSurahIndex.value = playbackService?.getCurrentSurahIndex() ?: 0

            Log.d("QuranPlayerViewModel", "Service connected")

            pendingPlaySurahIndex?.let { index ->
                pendingPlaySurahIndex = null
                playbackService?.playSurah(index)
            }

            // Start progress tracking
            startProgressTracking()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            serviceBound = false
            Log.d("QuranPlayerViewModel", "Service disconnected")
        }
    }

    init {
        // Bind to the service
        bindToService()
    }

    private fun bindToService() {
        val intent = Intent(context, QuranPlaybackService::class.java)
        context.startService(intent) // Start the service first
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun playSurah(index: Int) {
        if (index < 0 || index >= QuranData.surahs.size) return

        // Clear any previous download error
        _downloadError.value = null

        _isLoading.value = true
        _errorMessage.value = null
        _currentSurahIndex.value = index
        val service = playbackService
        if (service == null) {
            pendingPlaySurahIndex = index
        } else {
            service.playSurah(index)
        }
    }

    fun toggleLanguage() {
        _audioLanguage.value = when (audioLanguage) {
            AudioLanguage.ARABIC_ONLY -> AudioLanguage.BENGALI_TRANSLATION
            AudioLanguage.BENGALI_TRANSLATION -> AudioLanguage.ENGLISH_TRANSLATION
            AudioLanguage.ENGLISH_TRANSLATION -> AudioLanguage.ARABIC_ONLY
        }
        playbackService?.setAudioLanguage(audioLanguage)
        // Replay current surah with new language
        if (isPlaying) {
            playSurah(currentSurahIndex)
        }
    }

    fun selectSurah(index: Int) {
        _currentSurahIndex.value = index
    }

    fun togglePlayPause() {
        if (currentPosition == 0 && duration == 0 && !isPlaying) {
            // First time playing - try to play
            _downloadError.value = null
            playSurah(currentSurahIndex)
        } else {
            playbackService?.togglePlayPause()
        }
    }

    fun playNext() {
        val nextIndex = (currentSurahIndex + 1) % QuranData.surahs.size
        _currentSurahIndex.value = nextIndex
        playbackService?.playNext()
    }

    fun playPrevious() {
        val prevIndex = if (currentSurahIndex > 0) currentSurahIndex - 1 else QuranData.surahs.size - 1
        _currentSurahIndex.value = prevIndex
        playbackService?.playPrevious()
    }

    fun seekTo(position: Int) {
        playbackService?.seekTo(position)
        _currentPosition.value = position
    }

    fun setVolume(volume: Float) {
        playbackService?.setVolume(volume)
    }

    fun clearPermissionError() {
        _needsAudioPermission.value = false
        _errorMessage.value = null
    }

    /**
     * Trigger on-demand download for missing audio file.
     * Called when service reports audio needs downloading.
     */
    private fun triggerOnDemandDownload(cdnKey: String) {
        viewModelScope.launch {
            try {
                _isDownloading.value = true
                _isLoading.value = false
                _downloadProgress.value = 0f
                _downloadError.value = null
                Log.i("QuranPlayerViewModel", "Starting on-demand download: $cdnKey")

                // Collect download progress
                val progressJob = launch {
                    audioDownloadHelper.getDownloadProgress(cdnKey).collect { state ->
                        when (state) {
                            is AssetDownloadManager.DownloadState.Downloading -> {
                                _downloadProgress.value = state.progress
                            }
                            is AssetDownloadManager.DownloadState.Completed -> {
                                _downloadProgress.value = 1f
                            }
                            else -> {}
                        }
                    }
                }

                val result = audioDownloadHelper.downloadAudio(cdnKey)
                progressJob.cancel()

                when (result) {
                    is AssetDownloadManager.DownloadState.Completed -> {
                        Log.i("QuranPlayerViewModel", "Download completed: $cdnKey")
                        _isDownloading.value = false
                        _downloadProgress.value = 0f
                        // Auto-play after download
                        playbackService?.playAfterDownload(currentSurahIndex)
                    }
                    is AssetDownloadManager.DownloadState.Failed -> {
                        Log.e("QuranPlayerViewModel", "Download failed: ${result.error}")
                        _isDownloading.value = false
                        _downloadProgress.value = 0f
                        _downloadError.value = result.error
                        _isLoading.value = false
                    }
                    else -> {
                        _isDownloading.value = false
                        _downloadProgress.value = 0f
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("QuranPlayerViewModel", "Download error", e)
                _isDownloading.value = false
                _downloadProgress.value = 0f
                _downloadError.value = e.message ?: "Download failed"
                _isLoading.value = false
            }
        }
    }

    /**
     * Retry a failed download
     */
    fun retryDownload() {
        _downloadError.value = null
        playSurah(currentSurahIndex)
    }

    /**
     * Cleanup method to unbind service connection.
     * Called from DisposableEffect when composable leaves composition.
     */
    fun cleanup() {
        pendingPlaySurahIndex = null
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
                Log.d("QuranPlayerViewModel", "Service unbound via cleanup()")
            } catch (e: IllegalArgumentException) {
                Log.w("QuranPlayerViewModel", "Service was not registered when trying to unbind: ${e.message}")
            }
            serviceBound = false
            playbackService = null
        }
    }

    private fun startProgressTracking() {
        viewModelScope.launch {
            while (isActive) {
                playbackService?.let { service ->
                    if (service.isPlaying()) {
                        _currentPosition.value = service.getCurrentPosition()
                        _duration.value = service.getDuration()
                        _isPlaying.value = true
                    } else {
                        _isPlaying.value = false
                    }
                    _currentSurahIndex.value = service.getCurrentSurahIndex()
                }
                delay(500) // Update every 500ms
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
        // Don't stop the service - let it continue in background
    }
}
