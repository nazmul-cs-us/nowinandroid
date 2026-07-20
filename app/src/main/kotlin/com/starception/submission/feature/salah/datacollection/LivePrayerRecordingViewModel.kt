package com.starception.submission.feature.salah.datacollection

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.ml.SalahPosture
import com.starception.submission.ml.SalahDetectionEngine
import com.starception.submission.ml.SalahFeatureExtractor
import com.starception.submission.ml.SalahSequenceValidator
import com.starception.submission.sensor.SalahDataCollectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveRecordingState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val sampleCount: Int = 0,
    val detectedPosture: SalahPosture? = null,
    val detectedConfidence: Float = 0f,
    val rakahCount: Int = 0,
    val recordedFilePath: String? = null
)

@HiltViewModel
class LivePrayerRecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "LivePrayerRecVM"
    }

    private val _state = MutableStateFlow(LiveRecordingState())
    val state: StateFlow<LiveRecordingState> = _state.asStateFlow()

    private val collectionService = SalahDataCollectionService(context)
    private var detectionEngine: SalahDetectionEngine? = null
    private val sequenceValidator = SalahSequenceValidator()
    private var timerJob: kotlinx.coroutines.Job? = null

    fun startRecording() {
        if (_state.value.isRecording) return

        Log.i(TAG, "Starting live prayer recording...")

        // Initialize ML detection engine for real-time predictions
        try {
            detectionEngine = SalahDetectionEngine(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML engine, recording without predictions", e)
        }

        // Set up callbacks for real-time ML predictions
        collectionService.onSampleRecorded = { sample ->
            try {
                val engine = detectionEngine
                val result = engine?.addSampleAndClassify(sample)
                if (result != null) {
                    // Feed to sequence validator for rak'ah counting
                    sequenceValidator.processDetection(result.posture, result.confidence, System.currentTimeMillis())
                    _state.update {
                        it.copy(
                            detectedPosture = result.posture,
                            detectedConfidence = result.confidence,
                            sampleCount = it.sampleCount + 1,
                            rakahCount = sequenceValidator.completedRakahs
                        )
                    }
                } else {
                    _state.update { it.copy(sampleCount = it.sampleCount + 1) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in real-time ML prediction", e)
            }
        }

        // Keep UI state in sync when the service auto-stops at the 30-minute limit;
        // without this the screen stays in "recording" and the review step is lost.
        collectionService.onLiveAutoStopped = { filePath ->
            Log.i(TAG, "Live recording auto-stopped, saved to: $filePath")
            timerJob?.cancel()
            _state.update { it.copy(isRecording = false, recordedFilePath = filePath) }
            detectionEngine?.close()
            detectionEngine = null
        }

        collectionService.startLivePrayerRecording()
        _state.value = LiveRecordingState(isRecording = true)
        sequenceValidator.reset()

        // Start elapsed time counter
        timerJob = viewModelScope.launch {
            while (_state.value.isRecording) {
                delay(1000)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return

        Log.i(TAG, "Stopping live prayer recording...")
        timerJob?.cancel()

        val filePath = collectionService.stopLivePrayerRecording()
        _state.update { it.copy(isRecording = false, recordedFilePath = filePath) }

        detectionEngine?.close()
        detectionEngine = null

        Log.i(TAG, "Live recording saved to: $filePath")
    }

    override fun onCleared() {
        super.onCleared()
        if (_state.value.isRecording) {
            stopRecording()
        }
        detectionEngine?.close()
    }
}
