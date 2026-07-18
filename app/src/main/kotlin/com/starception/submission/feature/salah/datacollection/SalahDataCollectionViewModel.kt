package com.starception.submission.feature.salah.datacollection

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AssetManifest
import com.starception.submission.feature.salah.visualization.VisualizationMode
import com.starception.submission.feature.salah.visualization.VisualizationState
import com.starception.submission.feature.salah.visualization.VizPrediction
import com.starception.submission.ml.FeatureSpacePCA
import com.starception.submission.ml.SalahBatchInference
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import com.starception.submission.sensor.SalahDataCollectionService
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import com.starception.submission.voice.SherpaOnnxTtsService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

enum class GuidedRecordingState {
    IDLE,
    WELCOME,
    COUNTDOWN,
    RECORDING_POSTURE,
    POSTURE_TRANSITION,
    COMPLETED,
    CANCELLED
}

data class SalahDataCollectionUiState(
    val isRecording: Boolean = false,
    val isCountingDown: Boolean = false,
    val countdownSeconds: Int = 0,
    val sessionId: String = "",
    val currentPosture: SalahPosture = SalahPosture.QIYAM,
    val totalSamples: Int = 0,
    val postureCounts: Map<SalahPosture, Int> = emptyMap(),
    val lastSample: SalahDataSample? = null,
    val dataFiles: List<DataFileInfo> = emptyList(),
    val totalDataSizeKb: Long = 0,
    val trimmedSamples: Int = 0,
    val globalPostureCounts: Map<String, Int> = emptyMap(),
    val globalTotalSamples: Int = 0,
    // Guided recording state
    val guidedState: GuidedRecordingState = GuidedRecordingState.IDLE,
    val guidedCurrentPosture: SalahPosture? = null,
    val guidedPostureIndex: Int = 0,
    val guidedPostureTimeRemaining: Int = 0,
    val guidedPostureDuration: Int = 0,
    val guidedTotalPostures: Int = GUIDED_POSTURE_SEQUENCE.size,
    val guidedSelectedDuration: Int = 15,
    val guidedMessage: String = "",
    // TTS download state
    val isTtsAvailable: Boolean = false,
    val isTtsDownloading: Boolean = false,
    val ttsDownloadError: String? = null
)

data class DataFileInfo(
    val name: String,
    val sizeKb: Long,
    val lastModified: Long,
    val postureCounts: Map<String, Int> = emptyMap(),
    val totalSamples: Int = 0
)

/** Quality summary of the model currently deployed in assets (from training). */
data class DeployedModelInfo(
    val modelVersion: Int,
    val valAccuracy: Float,
    val testAccuracy: Float,
    /** The 2 classes with the lowest test F1 — where more/better data helps most. */
    val weakestClasses: List<Pair<String, Float>>,
)

/** Cached model-vs-label result for one recording file. */
data class FileQuality(
    val agreement: Float,
    val flaggedCount: Int,
    val isAnalyzing: Boolean = false,
)

/**
 * Posture sequence for guided recording.
 * Each pair is (posture, isTransition).
 * Transition postures use a fixed 8s duration; static postures use user-selected duration.
 */
val GUIDED_POSTURE_SEQUENCE: List<Pair<SalahPosture, Boolean>> = listOf(
    SalahPosture.QIYAM to false,
    SalahPosture.RUKU to false,
    SalahPosture.GOING_TO_SUJUD to true,
    SalahPosture.SUJUD to false,
    SalahPosture.JALSA to false,
    SalahPosture.SUJUD to false,           // Second sujud
    SalahPosture.QIYAM_RISING to true,
    SalahPosture.TASHAHHUD to false
)

private const val TRANSITION_DURATION = 8 // seconds for transition postures
private const val TAG = "GuidedRecording"

class SalahDataCollectionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val COUNTDOWN_SECONDS = 5
        const val TRIM_LAST_MS = 3000L // Trim last 3 seconds when stopping
    }

    private val collectionService = SalahDataCollectionService(application)

    private val _uiState = MutableStateFlow(SalahDataCollectionUiState())
    val uiState: StateFlow<SalahDataCollectionUiState> = _uiState.asStateFlow()

    // 3D Visualization state
    private val _vizState = MutableStateFlow(VisualizationState())
    val vizState: StateFlow<VisualizationState> = _vizState.asStateFlow()

    private val _allSamples = MutableStateFlow<List<SalahDataSample>>(emptyList())
    val allSamples: StateFlow<List<SalahDataSample>> = _allSamples.asStateFlow()

    // Per-file model-vs-label quality, keyed by file name. Backed by a
    // SharedPreferences cache keyed on name+mtime so results survive restarts
    // and invalidate automatically when a file is relabeled.
    private val _fileQuality = MutableStateFlow<Map<String, FileQuality>>(emptyMap())
    val fileQuality: StateFlow<Map<String, FileQuality>> = _fileQuality.asStateFlow()

    private val qualityPrefs by lazy {
        getApplication<Application>().getSharedPreferences("salah_file_quality", 0)
    }

    // Quality report of the deployed model, if training ever shipped one
    // (export_tflite.py --deploy copies it into assets as last_training_report.json).
    private val _deployedModel = MutableStateFlow<DeployedModelInfo?>(null)
    val deployedModel: StateFlow<DeployedModelInfo?> = _deployedModel.asStateFlow()

    private var countdownJob: Job? = null
    private var guidedJob: Job? = null

    // Lazy TTS service and download manager via Hilt EntryPoint
    private var ttsService: SherpaOnnxTtsService? = null
    private var downloadManager: AssetDownloadManager? = null
    private var ttsDownloadJob: Job? = null

    private fun getEntryPoint(): SherpaOnnxTtsEntryPoint =
        EntryPointAccessors.fromApplication(
            getApplication<Application>().applicationContext,
            SherpaOnnxTtsEntryPoint::class.java
        )

    private fun getTtsService(): SherpaOnnxTtsService {
        if (ttsService == null) {
            ttsService = getEntryPoint().sherpaOnnxTtsService()
            Log.i(TAG, "TTS service obtained via EntryPoint")
        }
        return ttsService!!
    }

    private fun getDownloadManager(): AssetDownloadManager {
        if (downloadManager == null) {
            downloadManager = getEntryPoint().assetDownloadManager()
        }
        return downloadManager!!
    }

    init {
        loadDeployedModelReport()
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
        checkTtsAvailability()
    }

    // ═══════════════════════════════════════════════════════
    // TTS DOWNLOAD MANAGEMENT
    // ═══════════════════════════════════════════════════════

    fun checkTtsAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dm = getDownloadManager()
                val manifest = dm.loadManifest()
                if (manifest == null) {
                    Log.w(TAG, "Could not load manifest for TTS check")
                    _uiState.update { it.copy(isTtsAvailable = false) }
                    return@launch
                }
                val kokoroReady = dm.isCategoryComplete("model_tts_kokoro", manifest)
                val espeakReady = dm.isCategoryComplete("model_tts_espeak", manifest)
                val available = kokoroReady && espeakReady
                Log.i(TAG, "TTS availability: kokoro=$kokoroReady, espeak=$espeakReady, available=$available")
                _uiState.update { it.copy(isTtsAvailable = available, ttsDownloadError = null) }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TTS availability", e)
                _uiState.update { it.copy(isTtsAvailable = false) }
            }
        }
    }

    fun downloadTtsEngine() {
        if (_uiState.value.isTtsDownloading) return
        ttsDownloadJob?.cancel()
        ttsDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTtsDownloading = true, ttsDownloadError = null) }
            try {
                val dm = getDownloadManager()
                val manifest = dm.loadManifest()
                if (manifest == null) {
                    _uiState.update { it.copy(isTtsDownloading = false, ttsDownloadError = "Could not load manifest") }
                    return@launch
                }

                // Download espeak first (smaller, ~18MB), then kokoro (~158MB)
                val categories = listOf("model_tts_espeak", "model_tts_kokoro")
                val totalCategories = categories.size
                var completedCategories = 0

                for (category in categories) {
                    if (dm.isCategoryComplete(category, manifest)) {
                        completedCategories++
                        continue
                    }
                    Log.i(TAG, "Downloading TTS category: $category")
                    // Progress tracked globally by AssetDownloadManager's top banner
                    val success = dm.downloadCategory(category, manifest) { _, _, _ -> }

                    if (!success) {
                        _uiState.update {
                            it.copy(
                                isTtsDownloading = false,
                                ttsDownloadError = "Failed to download $category"
                            )
                        }
                        return@launch
                    }
                    completedCategories++
                }

                Log.i(TAG, "TTS engine download complete")
                _uiState.update {
                    it.copy(
                        isTtsDownloading = false,
                        isTtsAvailable = true,
                        ttsDownloadError = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS download failed", e)
                _uiState.update {
                    it.copy(
                        isTtsDownloading = false,
                        ttsDownloadError = e.message ?: "Download failed"
                    )
                }
            }
        }
    }

    fun startRecording() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            // Countdown phase - user puts phone in pocket
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
                currentPosture = SalahPosture.QIYAM,
                totalSamples = afterCount,
                postureCounts = collectionService.getSessionStats().first,
                trimmedSamples = trimmed
            )
        }
        refreshFileList()
    }

    private fun loadDeployedModelReport() {
        viewModelScope.launch(Dispatchers.IO) {
            _deployedModel.value = runCatching {
                val json = getApplication<Application>().assets
                    .open("last_training_report.json").bufferedReader().readText()
                val root = JSONObject(json)
                val metrics = root.getJSONObject("metrics")
                val f1 = metrics.getJSONObject("per_class_f1_test")
                val perClass = buildList {
                    f1.keys().forEach { key -> add(key to f1.getDouble(key).toFloat()) }
                }.sortedBy { it.second }
                DeployedModelInfo(
                    modelVersion = root.optInt("model_version", 1),
                    valAccuracy = metrics.optDouble("val_accuracy", 0.0).toFloat(),
                    testAccuracy = metrics.optDouble("test_accuracy", 0.0).toFloat(),
                    weakestClasses = perClass.take(2),
                )
            }.getOrNull() // asset absent until the first training run deploys it
        }
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

    fun deleteFile(fileName: String) {
        collectionService.deleteFile(fileName)
        refreshFileList()
    }

    private fun refreshFileList() {
        val files = collectionService.listDataFiles().map { file ->
            val counts = collectionService.getFilePostureCounts(file.name)
            DataFileInfo(
                name = file.name,
                sizeKb = file.length() / 1024,
                lastModified = file.lastModified(),
                postureCounts = counts,
                totalSamples = counts.values.sum()
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
        // Restore cached quality results for the listed files (mtime-keyed).
        val cached = mutableMapOf<String, FileQuality>()
        for (file in files) {
            val raw = qualityPrefs.getString("${'$'}{file.name}:${'$'}{file.lastModified}", null) ?: continue
            val parts = raw.split(",")
            val agreement = parts.getOrNull(0)?.toFloatOrNull() ?: continue
            val flagged = parts.getOrNull(1)?.toIntOrNull() ?: 0
            cached[file.name] = FileQuality(agreement, flagged)
        }
        _fileQuality.value = cached
    }

    // ═══════════════════════════════════════════════════════
    // GUIDED RECORDING
    // ═══════════════════════════════════════════════════════

    fun setGuidedDuration(seconds: Int) {
        _uiState.update { it.copy(guidedSelectedDuration = seconds) }
    }

    fun startGuidedRecording() {
        guidedJob?.cancel()
        guidedJob = viewModelScope.launch {
            val duration = _uiState.value.guidedSelectedDuration
            Log.i(TAG, "Starting guided recording (duration=$duration s per posture)")

            // 1. WELCOME
            _uiState.update {
                it.copy(
                    guidedState = GuidedRecordingState.WELCOME,
                    guidedMessage = "Place your phone in your pocket and get ready.",
                    guidedPostureIndex = 0,
                    trimmedSamples = 0
                )
            }
            speakAndWait("Guided salah recording is starting. Place your phone in your pocket and get ready.")

            // 2. Start sensor recording
            collectionService.startRecording()
            _uiState.update {
                it.copy(
                    isRecording = true,
                    sessionId = collectionService.sessionId,
                    totalSamples = 0,
                    postureCounts = emptyMap(),
                    lastSample = null
                )
            }

            // 3. COUNTDOWN
            _uiState.update { it.copy(guidedState = GuidedRecordingState.COUNTDOWN, guidedMessage = "Starting in...") }
            speakAndWait("3")
            _uiState.update { it.copy(countdownSeconds = 3) }
            delay(800)
            speakAndWait("2")
            _uiState.update { it.copy(countdownSeconds = 2) }
            delay(800)
            speakAndWait("1")
            _uiState.update { it.copy(countdownSeconds = 1) }
            delay(800)
            _uiState.update { it.copy(countdownSeconds = 0) }

            // 4. Loop through posture sequence
            for ((index, entry) in GUIDED_POSTURE_SEQUENCE.withIndex()) {
                val (posture, isTransition) = entry
                val postureDuration = if (isTransition) TRANSITION_DURATION else duration

                // Set posture label on collection service
                collectionService.currentPosture = posture

                // Update UI
                _uiState.update {
                    it.copy(
                        guidedState = GuidedRecordingState.RECORDING_POSTURE,
                        guidedCurrentPosture = posture,
                        guidedPostureIndex = index,
                        guidedPostureDuration = postureDuration,
                        guidedPostureTimeRemaining = postureDuration,
                        guidedMessage = posture.displayName,
                        currentPosture = posture
                    )
                }

                // Speak posture instruction
                val instruction = if (isTransition) {
                    "Now perform ${posture.displayName}."
                } else {
                    "Now perform ${posture.displayName}. Hold this position."
                }
                speakAndWait(instruction)

                // Count down the posture duration
                for (remaining in postureDuration downTo 1) {
                    _uiState.update { it.copy(guidedPostureTimeRemaining = remaining) }

                    // Alert at 5 seconds remaining (only for longer durations)
                    if (remaining == 5 && postureDuration > 8) {
                        speakAndWait("5 seconds remaining.")
                    }

                    delay(1000)
                }
                _uiState.update { it.copy(guidedPostureTimeRemaining = 0) }

                // Transition announcement (if not the last posture)
                if (index < GUIDED_POSTURE_SEQUENCE.size - 1) {
                    val nextPosture = GUIDED_POSTURE_SEQUENCE[index + 1].first
                    _uiState.update {
                        it.copy(
                            guidedState = GuidedRecordingState.POSTURE_TRANSITION,
                            guidedMessage = "Next: ${nextPosture.displayName}"
                        )
                    }
                    speakAndWait("Get ready for ${nextPosture.displayName}.")
                    delay(1500)
                }
            }

            // 5. COMPLETED
            Log.i(TAG, "Guided recording completed")
            val beforeCount = collectionService.getSessionStats().second
            collectionService.stopRecording(trimLastMs = 0) // No trim for guided - data is clean
            val afterCount = collectionService.getSessionStats().second

            _uiState.update {
                it.copy(
                    guidedState = GuidedRecordingState.COMPLETED,
                    guidedMessage = "Recording complete!",
                    isRecording = false,
                    totalSamples = afterCount,
                    postureCounts = collectionService.getSessionStats().first,
                    currentPosture = SalahPosture.QIYAM
                )
            }

            speakAndWait("Recording complete. You can take your phone out now.")
            refreshFileList()
        }
    }

    fun cancelGuidedRecording() {
        Log.i(TAG, "Guided recording cancelled")
        guidedJob?.cancel()
        guidedJob = null

        // Stop TTS
        try {
            ttsService?.stopSpeaking()
        } catch (_: Exception) {}

        // Stop recording if active
        if (collectionService.isRecording()) {
            collectionService.stopRecording(trimLastMs = TRIM_LAST_MS)
        }

        _uiState.update {
            it.copy(
                guidedState = GuidedRecordingState.CANCELLED,
                isRecording = false,
                currentPosture = SalahPosture.QIYAM
            )
        }
        refreshFileList()
    }

    fun resetGuidedState() {
        _uiState.update { it.copy(guidedState = GuidedRecordingState.IDLE, guidedMessage = "") }
    }

    private suspend fun speakAndWait(text: String) {
        try {
            val tts = getTtsService()
            suspendCancellableCoroutine { continuation ->
                viewModelScope.launch {
                    tts.speak(
                        text = text,
                        onComplete = {
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                    )
                }
                continuation.invokeOnCancellation {
                    tts.stopSpeaking()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed: ${e.message}")
            // Continue even if TTS fails - timing is more important
            delay(500) // Brief pause as fallback
        }
    }

    // 3D Visualization methods

    fun loadAllSamples() {
        viewModelScope.launch(Dispatchers.IO) {
            val samples = mutableListOf<SalahDataSample>()
            collectionService.listDataFiles().forEach { file ->
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            try {
                                samples.add(SalahDataSample.fromJson(JSONObject(line)))
                            } catch (_: Exception) { }
                        }
                    }
                }
            }
            val sorted = samples.sortedBy { it.timestamp }
            _allSamples.value = sorted
            // Data changed — previous model analysis and PCA projection are stale.
            _vizState.update {
                it.copy(
                    totalSamples = sorted.size,
                    predictions = null,
                    flaggedIndices = emptySet(),
                    pcaPositions = null,
                    pcaVariance = null,
                )
            }
        }
    }

    /** Analyze one recording file against the deployed model; cache by name+mtime. */
    fun analyzeFileQuality(file: DataFileInfo) {
        if (_fileQuality.value[file.name]?.isAnalyzing == true) return
        _fileQuality.update { it + (file.name to FileQuality(0f, 0, isAnalyzing = true)) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val path = java.io.File(collectionService.getDataDirectory(), file.name).absolutePath
                val result = SalahBatchInference(getApplication()).use { it.analyzeFile(path) }
                val quality = FileQuality(result.overallAgreement, result.flaggedSegments.size)
                qualityPrefs.edit()
                    .putString(
                        "${'$'}{file.name}:${'$'}{file.lastModified}",
                        "${'$'}{quality.agreement},${'$'}{quality.flaggedCount}",
                    )
                    .apply()
                _fileQuality.update { it + (file.name to quality) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("SalahDataVM", "File quality analysis failed for ${'$'}{file.name}", e)
                _fileQuality.update { it - file.name }
            }
        }
    }

    /**
     * Playback ticks arrive up to 50x/s from the GL thread's callback; merging into
     * the latest state here (instead of copying the composition snapshot) keeps them
     * from clobbering concurrent async updates (predictions, PCA, flags).
     */
    fun onVizPlaybackTick(
        index: Int,
        posture: SalahPosture?,
        pitch: Float,
        roll: Float,
        accelMag: Float,
        gyroMag: Float,
        playing: Boolean,
    ) {
        _vizState.update {
            it.copy(
                playbackIndex = index,
                currentPosture = posture,
                currentPitch = pitch,
                currentRoll = roll,
                currentAccelMag = accelMag,
                currentGyroMag = gyroMag,
                isPlaying = playing,
            )
        }
    }

    fun updateVizState(state: VisualizationState) {
        val previous = _vizState.value
        _vizState.value = state
        // Entering the PCA view computes the projection on first use.
        if (state.mode == VisualizationMode.FEATURE_PCA && previous.mode != state.mode) {
            computePcaProjection()
        }
    }

    /** Run the deployed model over the loaded dataset for disagreement highlighting. */
    fun analyzeVizPredictions() {
        val samples = _allSamples.value
        if (samples.isEmpty() || _vizState.value.isAnalyzingPredictions) return
        _vizState.update { it.copy(isAnalyzingPredictions = true) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = SalahBatchInference(getApplication()).use { it.analyzeSamples(samples) }
                val flagged = buildSet {
                    result.flaggedSegments.forEach { f -> addAll(f.startIndex..f.endIndex) }
                }
                _vizState.update {
                    it.copy(
                        isAnalyzingPredictions = false,
                        predictions = result.predictions.map { p -> VizPrediction(p.predicted, p.confidence) },
                        flaggedIndices = flagged,
                        showDisagreements = true,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("SalahDataVM", "Viz model analysis failed", e)
                _vizState.update { it.copy(isAnalyzingPredictions = false) }
            }
        }
    }

    fun computePcaProjection() {
        val samples = _allSamples.value
        val current = _vizState.value
        if (samples.size < 2 || current.isComputingPca || current.pcaPositions != null) return
        _vizState.update { it.copy(isComputingPca = true) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = FeatureSpacePCA.project(samples)
                _vizState.update {
                    it.copy(
                        isComputingPca = false,
                        pcaPositions = result.positions,
                        pcaVariance = result.varianceExplained,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("SalahDataVM", "PCA projection failed", e)
                _vizState.update { it.copy(isComputingPca = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        guidedJob?.cancel()
        try {
            ttsService?.stopSpeaking()
        } catch (_: Exception) {}
        if (collectionService.isRecording()) {
            collectionService.stopRecording()
        }
    }
}
