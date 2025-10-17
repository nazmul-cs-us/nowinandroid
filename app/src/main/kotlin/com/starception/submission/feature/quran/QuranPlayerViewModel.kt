package com.starception.submission.feature.quran

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class QuranPlayerViewModel(private val context: Context) : ViewModel() {

    private var playbackService: QuranPlaybackService? = null
    private var serviceBound = false

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

    val currentSurah: Surah
        get() = QuranData.surahs[currentSurahIndex]

    private var _audioLanguage = mutableStateOf(AudioLanguage.ARABIC_ONLY)
    val audioLanguage: AudioLanguage get() = _audioLanguage.value

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val myBinder = binder as QuranPlaybackService.QuranBinder
            playbackService = myBinder.getService()
            serviceBound = true

            // Set up callbacks
            playbackService?.onPlaybackStateChanged = { playing ->
                _isPlaying.value = playing
            }
            playbackService?.onSurahChanged = { index ->
                _currentSurahIndex.value = index
            }
            playbackService?.onProgressChanged = { position, duration ->
                _currentPosition.value = position
                _duration.value = duration
            }

            // Set initial language
            playbackService?.setAudioLanguage(audioLanguage)

            // Sync current state
            _isPlaying.value = playbackService?.isPlaying() ?: false
            _currentSurahIndex.value = playbackService?.getCurrentSurahIndex() ?: 0

            Log.d("QuranPlayerViewModel", "Service connected")

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

    fun playSurah(index: Int) {
        if (index < 0 || index >= QuranData.surahs.size) return
        _isLoading.value = true
        _errorMessage.value = null
        _currentSurahIndex.value = index
        playbackService?.playSurah(index)
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
            // First time playing
            playSurah(currentSurahIndex)
        } else {
            playbackService?.togglePlayPause()
        }
    }

    fun playNext() {
        playbackService?.playNext()
    }

    fun playPrevious() {
        playbackService?.playPrevious()
    }

    fun seekTo(position: Int) {
        playbackService?.seekTo(position)
        _currentPosition.value = position
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
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
        // Don't stop the service - let it continue in background
    }
}
