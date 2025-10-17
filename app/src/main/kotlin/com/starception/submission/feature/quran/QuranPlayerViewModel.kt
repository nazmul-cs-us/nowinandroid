package com.starception.submission.feature.quran

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class QuranPlayerViewModel(private val context: Context) : ViewModel() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

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

    private val quranArabicPath = "/sdcard/Quran/Arabic"
    private val quranBengaliPath = "/sdcard/Quran/Bengali"
    private val quranEnglishPath = "/sdcard/Quran/English"

    init {
        acquireWakeLock()
        initializePlayer()
    }

    private fun acquireWakeLock() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuranPlayer::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours
    }

    private fun initializePlayer() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setOnCompletionListener {
                    // Auto-play next surah
                    playNext()
                }
                setOnPreparedListener { mp ->
                    _duration.value = mp.duration
                    _isLoading.value = false
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracking()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("QuranPlayer", "Error: what=$what, extra=$extra")
                    _errorMessage.value = "Failed to play audio"
                    _isLoading.value = false
                    _isPlaying.value = false
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("QuranPlayer", "Failed to initialize player", e)
            _errorMessage.value = "Failed to initialize player: ${e.message}"
        }
    }

    fun playSurah(index: Int) {
        if (index < 0 || index >= QuranData.surahs.size) return

        try {
            _currentSurahIndex.value = index
            val surah = QuranData.surahs[index]

            // Select file path based on language
            val basePath = when (audioLanguage) {
                AudioLanguage.ARABIC_ONLY -> quranArabicPath
                AudioLanguage.BENGALI_TRANSLATION -> quranBengaliPath
                AudioLanguage.ENGLISH_TRANSLATION -> quranEnglishPath
            }

            val fileName = when (audioLanguage) {
                AudioLanguage.ARABIC_ONLY -> surah.fileName
                AudioLanguage.BENGALI_TRANSLATION -> {
                    // Try to find Bengali file by pattern
                    val bengaliDir = File(basePath)
                    val pattern = String.format("%03d", surah.number)
                    bengaliDir.listFiles()?.find { it.name.startsWith(pattern) }?.name
                        ?: surah.fileNameBengali // Fallback to stored name
                }
                AudioLanguage.ENGLISH_TRANSLATION -> {
                    // English files follow pattern: "001 surah_al_fatihah.ogg"
                    val englishDir = File(basePath)
                    val pattern = String.format("%03d", surah.number)
                    englishDir.listFiles()?.find { it.name.startsWith(pattern) }?.name
                        ?: "${pattern} ${surah.nameEnglish.lowercase().replace(" ", "_")}.ogg"
                }
            }

            val audioFile = File(basePath, fileName)

            if (!audioFile.exists()) {
                _errorMessage.value = "Audio file not found: $fileName"
                Log.e("QuranPlayer", "File not found: ${audioFile.absolutePath}")
                return
            }

            _isLoading.value = true
            _errorMessage.value = null

            mediaPlayer?.apply {
                reset()
                setDataSource(audioFile.absolutePath)
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("QuranPlayer", "Failed to play surah", e)
            _errorMessage.value = "Failed to play: ${e.message}"
            _isLoading.value = false
        }
    }

    fun toggleLanguage() {
        _audioLanguage.value = when (audioLanguage) {
            AudioLanguage.ARABIC_ONLY -> AudioLanguage.BENGALI_TRANSLATION
            AudioLanguage.BENGALI_TRANSLATION -> AudioLanguage.ENGLISH_TRANSLATION
            AudioLanguage.ENGLISH_TRANSLATION -> AudioLanguage.ARABIC_ONLY
        }
        // Replay current surah with new language
        if (isPlaying) {
            playSurah(currentSurahIndex)
        }
    }

    fun selectSurah(index: Int) {
        _currentSurahIndex.value = index
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                if (currentPosition == 0 && duration == 0) {
                    // First time playing
                    playSurah(currentSurahIndex)
                } else {
                    player.start()
                    _isPlaying.value = true
                    startProgressTracking()
                }
            }
        }
    }

    fun playNext() {
        val wasPlaying = isPlaying
        val nextIndex = (currentSurahIndex + 1) % QuranData.surahs.size
        _currentSurahIndex.value = nextIndex
        if (wasPlaying) {
            playSurah(nextIndex)
        }
    }

    fun playPrevious() {
        val wasPlaying = isPlaying
        val prevIndex = if (currentSurahIndex > 0) currentSurahIndex - 1 else QuranData.surahs.size - 1
        _currentSurahIndex.value = prevIndex
        if (wasPlaying) {
            playSurah(prevIndex)
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    private fun startProgressTracking() {
        viewModelScope.launch {
            while (isActive && isPlaying) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition
                        _duration.value = player.duration
                    }
                }
                delay(100) // Update every 100ms
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.release()
        wakeLock = null
    }
}
