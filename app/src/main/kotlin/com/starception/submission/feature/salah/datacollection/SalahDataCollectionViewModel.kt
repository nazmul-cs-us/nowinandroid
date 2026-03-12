package com.starception.submission.feature.salah.datacollection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import com.starception.submission.sensor.SalahDataCollectionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SalahDataCollectionUiState(
    val isRecording: Boolean = false,
    val isCountingDown: Boolean = false,
    val countdownSeconds: Int = 0,
    val sessionId: String = "",
    val currentPosture: SalahPosture = SalahPosture.NOT_PRAYING,
    val totalSamples: Int = 0,
    val postureCounts: Map<SalahPosture, Int> = emptyMap(),
    val lastSample: SalahDataSample? = null,
    val dataFiles: List<DataFileInfo> = emptyList(),
    val totalDataSizeKb: Long = 0,
    val trimmedSamples: Int = 0,
    val globalPostureCounts: Map<String, Int> = emptyMap(),
    val globalTotalSamples: Int = 0
)

data class DataFileInfo(
    val name: String,
    val sizeKb: Long,
    val lastModified: Long
)

class SalahDataCollectionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val COUNTDOWN_SECONDS = 5
        const val TRIM_LAST_MS = 3000L // Trim last 3 seconds when stopping
    }

    private val collectionService = SalahDataCollectionService(application)

    private val _uiState = MutableStateFlow(SalahDataCollectionUiState())
    val uiState: StateFlow<SalahDataCollectionUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        // Set up callbacks
        collectionService.onSampleRecorded = { sample ->
            _uiState.update { state ->
                state.copy(lastSample = sample)
            }
        }
        collectionService.onStatsUpdated = { counts, total ->
            _uiState.update { state ->
                state.copy(postureCounts = counts, totalSamples = total)
            }
        }
        refreshFileList()
    }

    fun startRecording() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            // Countdown phase — user puts phone in pocket
            _uiState.update { it.copy(isCountingDown = true, countdownSeconds = COUNTDOWN_SECONDS, trimmedSamples = 0) }
            for (i in COUNTDOWN_SECONDS downTo 1) {
                _uiState.update { it.copy(countdownSeconds = i) }
                delay(1000)
            }
            _uiState.update { it.copy(isCountingDown = false, countdownSeconds = 0) }

            // Actually start recording
            collectionService.startRecording()
            _uiState.update { state ->
                state.copy(
                    isRecording = true,
                    sessionId = collectionService.sessionId,
                    totalSamples = 0,
                    postureCounts = emptyMap(),
                    lastSample = null
                )
            }
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(isCountingDown = false, countdownSeconds = 0) }
    }

    fun stopRecording() {
        // Stop recording and trim the last 3 seconds of data
        // (the time you spent pulling the phone from pocket)
        val beforeCount = collectionService.getSessionStats().second
        collectionService.stopRecording(trimLastMs = TRIM_LAST_MS)
        val afterCount = collectionService.getSessionStats().second
        val trimmed = beforeCount - afterCount

        _uiState.update { state ->
            state.copy(
                isRecording = false,
                currentPosture = SalahPosture.NOT_PRAYING,
                totalSamples = afterCount,
                postureCounts = collectionService.getSessionStats().first,
                trimmedSamples = trimmed
            )
        }
        refreshFileList()
    }

    fun setPosture(posture: SalahPosture) {
        collectionService.currentPosture = posture
        _uiState.update { state ->
            state.copy(currentPosture = posture)
        }
    }

    fun deleteAllData() {
        collectionService.deleteAllData()
        refreshFileList()
    }

    private fun refreshFileList() {
        val files = collectionService.listDataFiles().map { file ->
            DataFileInfo(
                name = file.name,
                sizeKb = file.length() / 1024,
                lastModified = file.lastModified()
            )
        }
        val (globalCounts, globalTotal) = collectionService.getGlobalPostureCounts()
        _uiState.update { state ->
            state.copy(
                dataFiles = files,
                totalDataSizeKb = collectionService.getTotalDataSizeKb(),
                globalPostureCounts = globalCounts,
                globalTotalSamples = globalTotal
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (collectionService.isRecording()) {
            collectionService.stopRecording()
        }
    }
}
