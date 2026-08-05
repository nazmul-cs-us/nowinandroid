package com.starception.submission.feature.salah.datacollection

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AssetManifest
import com.starception.submission.feature.salah.visualization.PosePlaybackSource
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    /** Recording currently loaded into the 3D view; null means every file combined. */
    val vizSourceFile: String? = null,
    // Guided recording state
    val guidedState: GuidedRecordingState = GuidedRecordingState.IDLE,
    val guidedCurrentPosture: SalahPosture? = null,
    val guidedPostureIndex: Int = 0,
    val guidedPostureTimeRemaining: Int = 0,
    val guidedPostureDuration: Int = 0,
    val guidedTotalPostures: Int = GUIDED_POSTURE_SEQUENCE.size,
    val guidedSelectedDuration: Int = 15,
    val guidedSpecificOnly: Boolean = true,
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
    val totalSamples: Int = 0,
    /** A live or legacy recording that can become eligible through explicit review. */
    val isPendingReview: Boolean = false,
    val trainingIssue: String? = null,
)

/** Quality summary of the model currently deployed in assets (from training). */
data class DeployedModelInfo(
    val modelVersion: Int,
    val valAccuracy: Float,
    val testAccuracy: Float,
    val isDeploymentReady: Boolean,
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
 * One step of the guided recording sequence.
 * Transition steps use a short, fixed capture; static postures use the user-selected duration.
 */
data class GuidedStep(
    val posture: SalahPosture,
    val isTransition: Boolean,
    val instruction: String,
    /** Contextual title shown while recording; distinguishes repeated transition labels. */
    val recordingLabel: String = posture.displayName,
    /** Exact cue spoken at the instant capture begins for a movement class. */
    val movementCue: String? = null,
) {
    init {
        require(isTransition == (movementCue != null)) {
            "Every transition must have one movement cue, and static postures must not."
        }
    }
}

/**
 * Posture sequence for guided recording, mirroring a rak'ah that continues into the
 * next rak'ah (same ideal order the SalahSequenceValidator enforces):
 * QIYAM → RUKU → QIYAM_RISING (i'tidal) → GOING_TO_SUJUD → SUJUD → JALSA
 * → GOING_TO_SUJUD → SUJUD → TASHAHHUD → RISING_TO_QIYAM.
 * After ruku the worshipper always straightens back up to standing before descending to
 * sujud. The guided flow then records the next-rak'ah rise after tashahhud so every model
 * class is represented in each complete session and can participate in session-isolated
 * train, validation, and test splits.
 */
val GUIDED_POSTURE_SEQUENCE: List<GuidedStep> = listOf(
    GuidedStep(
        SalahPosture.QIYAM, isTransition = false,
        instruction = "Stand upright in the prayer position, with your hands folded. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.RUKU, isTransition = false,
        instruction = "Now bow into ruku, with your hands on your knees. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.QIYAM_RISING, isTransition = true,
        instruction = "Stay in ruku. Do not move yet. When you hear move now, rise from ruku until you are fully upright. Stop in standing, and do not start going down.",
        recordingLabel = "Ruku → Standing",
        movementCue = "Move now. Rise from ruku until you are fully upright, then stop.",
    ),
    GuidedStep(
        SalahPosture.GOING_TO_SUJUD, isTransition = true,
        instruction = "You should now be fully upright after ruku. Stay standing and do not move yet. When you hear move now, lower from standing into the first prostration.",
        recordingLabel = "Standing → First Sujud",
        movementCue = "Move now. From standing, lower smoothly into the first prostration.",
    ),
    GuidedStep(
        SalahPosture.SUJUD, isTransition = false,
        instruction = "Remain in the first prostration, or sujud. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.JALSA, isTransition = false,
        instruction = "Now sit up into the seated position between the two prostrations. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.GOING_TO_SUJUD, isTransition = true,
        instruction = "You should now be seated between the two prostrations. Stay seated and do not move yet. When you hear move now, lower from sitting into the second prostration.",
        recordingLabel = "Sitting → Second Sujud",
        movementCue = "Move now. From sitting, lower smoothly into the second prostration.",
    ),
    GuidedStep(
        SalahPosture.SUJUD, isTransition = false,
        instruction = "Remain in the second prostration, or sujud. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.TASHAHHUD, isTransition = false,
        instruction = "Now sit up into the seated position for tashahhud. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.RISING_TO_QIYAM, isTransition = true,
        instruction = "Remain seated after tashahhud and do not move yet. When you hear move now, rise naturally into the next rak‘ah and stop fully upright.",
        recordingLabel = "Tashahhud → Next Rak‘ah",
        movementCue = "Move now. Rise naturally into the next rak‘ah and stop fully upright.",
    ),
)

private fun focusedGuidedStep(posture: SalahPosture): GuidedStep = when (posture) {
    SalahPosture.QIYAM -> GuidedStep(
        posture, isTransition = false,
        instruction = "Move into qiyam. Stand upright with your hands folded, become still, and keep holding until recording finishes.",
    )
    SalahPosture.RUKU -> GuidedStep(
        posture, isTransition = false,
        instruction = "Move into ruku with your hands on your knees, become still, and keep holding until recording finishes.",
    )
    SalahPosture.SUJUD -> GuidedStep(
        posture, isTransition = false,
        instruction = "Move fully into sujud, become still, and keep holding until recording finishes.",
    )
    SalahPosture.JALSA -> GuidedStep(
        posture, isTransition = false,
        instruction = "Sit upright between the two prostrations, become still, and keep holding until recording finishes.",
    )
    SalahPosture.TASHAHHUD -> GuidedStep(
        posture, isTransition = false,
        instruction = "Sit in the tashahhud position, become still, and keep holding until recording finishes.",
    )
    SalahPosture.QIYAM_RISING -> GuidedStep(
        posture, isTransition = true,
        instruction = "Begin fully in ruku and do not move yet. When you hear move now, rise until you are fully upright, then stop.",
        recordingLabel = "Ruku → Standing",
        movementCue = "Move now. Rise from ruku until you are fully upright, then stop.",
    )
    SalahPosture.GOING_TO_SUJUD -> GuidedStep(
        posture, isTransition = true,
        instruction = "For this take, choose either standing to first sujud or sitting to second sujud. Begin in that start position and do not move yet. When you hear move now, lower fully into sujud.",
        recordingLabel = "Lowering → Sujud",
        movementCue = "Move now. Lower smoothly from your chosen start position into sujud.",
    )
    SalahPosture.RISING_TO_QIYAM -> GuidedStep(
        posture, isTransition = true,
        instruction = "For this take, begin either in the second sujud or seated after tashahhud. Do not move yet. When you hear move now, rise naturally into the next rak‘ah and stop fully upright.",
        recordingLabel = "Rise to Next Rak‘ah",
        movementCue = "Move now. Rise naturally into the next rak‘ah and stop fully upright.",
    )
}

// With the production stride of 3, five seconds yields at least 10 held-out sequences
// from one complete session, matching the deployment quality gate for every movement class.
internal const val TRANSITION_DURATION = 5
internal const val FOCUSED_MOVEMENT_REPETITIONS = 5
private const val STATIC_SETTLE_MS = 1_500L
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
                val dataset = root.optJSONObject("dataset")
                val testSequences = dataset?.optJSONObject("sequences")?.optJSONObject("test")
                val hasEnoughTestData = SalahPosture.classificationLabels.all { posture ->
                    (testSequences?.optInt(posture.name, 0) ?: 0) >= 10
                }
                val allClassesPass = perClass.size == SalahPosture.classificationLabels.size &&
                    perClass.all { (_, score) -> score >= 0.60f }
                DeployedModelInfo(
                    modelVersion = root.optInt("model_version", 1),
                    valAccuracy = metrics.optDouble("val_accuracy", 0.0).toFloat(),
                    testAccuracy = metrics.optDouble("test_accuracy", 0.0).toFloat(),
                    isDeploymentReady = metrics.optDouble("test_accuracy", 0.0) >= 0.80 &&
                        dataset?.optString("split_strategy") == "session_isolated" &&
                        hasEnoughTestData && allClassesPass,
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
            val trainingIssue = collectionService.getFileTrainingIssue(file.name)
            DataFileInfo(
                name = file.name,
                sizeKb = file.length() / 1024,
                lastModified = file.lastModified(),
                postureCounts = counts,
                totalSamples = counts.values.sum(),
                isPendingReview = trainingIssue != null &&
                    trainingIssue != "Invalid data · excluded" &&
                    trainingIssue != "Empty · excluded",
                trainingIssue = trainingIssue,
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

    fun setGuidedSpecificOnly(specificOnly: Boolean) {
        _uiState.update { it.copy(guidedSpecificOnly = specificOnly) }
    }

    fun startGuidedRecording() {
        // A fast double tap used to cancel the first coroutine and immediately
        // launch the whole welcome/countdown flow again. Only IDLE may start a
        // new guided session; completion/cancellation must be dismissed first.
        if (guidedJob?.isActive == true || _uiState.value.guidedState != GuidedRecordingState.IDLE) {
            Log.w(TAG, "Ignoring duplicate guided recording start")
            return
        }
        guidedJob = viewModelScope.launch {
            val startState = _uiState.value
            val duration = startState.guidedSelectedDuration
            val isSpecific = startState.guidedSpecificOnly
            val focusedStep = focusedGuidedStep(startState.currentPosture)
            val steps = if (isSpecific) {
                if (focusedStep.isTransition) {
                    List(FOCUSED_MOVEMENT_REPETITIONS) { focusedStep }
                } else {
                    listOf(focusedStep)
                }
            } else {
                GUIDED_POSTURE_SEQUENCE
            }
            Log.i(TAG, "Starting guided recording (specific=$isSpecific, steps=${steps.size}, duration=$duration)")

            // 1. WELCOME
            _uiState.update {
                it.copy(
                    guidedState = GuidedRecordingState.WELCOME,
                    guidedMessage = "Turn up the media volume, place the phone securely in one trouser pocket, and leave it there for the whole session.",
                    guidedPostureIndex = 0,
                    guidedTotalPostures = steps.size,
                    trimmedSamples = 0,
                    totalSamples = 0,
                    postureCounts = emptyMap(),
                )
            }
            val welcomeSpoken = speakAndWait(
                (if (isSpecific) "Focused guided recording is starting. " else "Guided prayer recording is starting. ") +
                    "Turn up the media volume. " +
                    "Place the phone fully inside one trouser pocket, in the position you normally carry it. " +
                    "Leave the phone in the same pocket for the entire session. " +
                    (if (isSpecific) {
                        "You will record only ${focusedStep.recordingLabel}. " +
                            if (focusedStep.isTransition) {
                                "The guide will capture $FOCUSED_MOVEMENT_REPETITIONS separate repetitions. Return to the starting position only while capture is paused. "
                            } else ""
                    } else
                        "After bowing, rise fully upright and wait for a separate instruction before lowering into prostration. ") +
                    "Follow only the voice instructions. Move immediately when an instruction begins with: now. " +
                    "If an instruction says: do not move yet, wait for the separate cue: move now."
            )
            if (!welcomeSpoken) {
                failGuidedRecording("Voice instructions could not play. No training recording was saved.")
                return@launch
            }
            delay(1_500L)

            // 2. COUNTDOWN. Sensors do not record this as QIYAM: the user is still
            // handling the phone, so these windows have no valid posture label.
            _uiState.update { it.copy(guidedState = GuidedRecordingState.COUNTDOWN, guidedMessage = "Starting in...") }
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                _uiState.update { it.copy(countdownSeconds = remaining) }
                if (!speakAndWait(remaining.toString())) {
                    failGuidedRecording("Voice instructions stopped before recording began. No training recording was saved.")
                    return@launch
                }
                delay(250L)
            }
            _uiState.update { it.copy(countdownSeconds = 0) }

            // 3. Start one file/session with capture paused. Each guided step resumes
            // capture only after its instruction and starts on a clean sensor window.
            collectionService.startRecording(
                captureImmediately = false,
                mode = SalahDataCollectionService.CollectionMode.GUIDED,
            )
            _uiState.update {
                it.copy(
                    isRecording = true,
                    sessionId = collectionService.sessionId,
                    totalSamples = 0,
                    postureCounts = emptyMap(),
                    lastSample = null
                )
            }

            // 4. Loop through the prayer sequence.
            for ((index, step) in steps.withIndex()) {
                val posture = step.posture
                val postureDuration = if (step.isTransition) TRANSITION_DURATION else duration
                val isRepeatedFocusedMovement = isSpecific && focusedStep.isTransition
                val takeNumber = index + 1
                val preparationMessage = if (isRepeatedFocusedMovement) {
                    if (index == 0) {
                        "Take $takeNumber of ${steps.size}. ${step.instruction}"
                    } else {
                        "Prepare for take $takeNumber of ${steps.size}. Return to the same starting position. " +
                            "Do not move until you hear move now."
                    }
                } else {
                    step.instruction
                }

                // Preparation and movement into static postures are intentionally not
                // captured. Otherwise instruction time and boundary motion would be
                // written under the upcoming static label.
                collectionService.pauseSampleCapture()
                _uiState.update {
                    it.copy(
                        guidedState = GuidedRecordingState.POSTURE_TRANSITION,
                        guidedCurrentPosture = posture,
                        guidedPostureIndex = index,
                        guidedPostureDuration = postureDuration,
                        guidedPostureTimeRemaining = postureDuration,
                        guidedMessage = preparationMessage,
                    )
                }
                if (!speakAndWait(preparationMessage)) {
                    failGuidedRecording("Voice instructions failed. Recording stopped before any uncertain labels were captured.")
                    return@launch
                }
                if (!step.isTransition) delay(STATIC_SETTLE_MS)

                _uiState.update {
                    it.copy(
                        guidedState = GuidedRecordingState.RECORDING_POSTURE,
                        guidedCurrentPosture = posture,
                        guidedPostureIndex = index,
                        guidedPostureDuration = postureDuration,
                        guidedPostureTimeRemaining = postureDuration,
                        guidedMessage = if (isRepeatedFocusedMovement) {
                            "${step.recordingLabel} · Take $takeNumber of ${steps.size}"
                        } else {
                            step.recordingLabel
                        },
                        currentPosture = posture
                    )
                }

                if (step.isTransition) {
                    // Capture begins when cue playback actually starts, not while the
                    // TTS engine is still generating audio. This keeps the five-second
                    // movement label aligned with the user's physical transition.
                    val transitionCaptured = captureSpokenTransition(
                        posture = posture,
                        cue = requireNotNull(step.movementCue),
                        durationSeconds = postureDuration,
                    )
                    if (!transitionCaptured) {
                        failGuidedRecording("The movement cue could not play. Recording stopped before any uncertain labels were captured.")
                        return@launch
                    }
                } else {
                    // Static captures stay voice-free so phone-speaker vibration does
                    // not enter an otherwise steady posture segment.
                    collectionService.resumeSampleCapture(posture)
                    runGuidedCaptureTimer(postureDuration)
                    collectionService.pauseSampleCapture()
                }

                // Repeated movement takes stay in one file/session, but make the
                // pause explicit so it never looks like the recorder finished and
                // unexpectedly restarted. Capture remains paused throughout reset.
                if (isRepeatedFocusedMovement && takeNumber < steps.size) {
                    val resetMessage =
                        "Take $takeNumber complete. Recording is paused. Return to the starting position " +
                            "for take ${takeNumber + 1} of ${steps.size}."
                    _uiState.update {
                        it.copy(
                            guidedState = GuidedRecordingState.POSTURE_TRANSITION,
                            guidedPostureIndex = index,
                            guidedPostureTimeRemaining = 0,
                            guidedMessage = resetMessage,
                        )
                    }
                    if (!speakAndWait(resetMessage)) {
                        failGuidedRecording("Voice instructions stopped between movement takes. Completed takes remain saved.")
                        return@launch
                    }
                    delay(1_000L)
                }
            }

            // 5. COMPLETED
            Log.i(TAG, "Guided recording completed")
            collectionService.stopRecording(trimLastMs = 0) // No trim for guided - data is clean
            val afterCount = collectionService.getSessionStats().second

            _uiState.update {
                it.copy(
                    guidedState = GuidedRecordingState.COMPLETED,
                    guidedMessage = when {
                        isSpecific && focusedStep.isTransition ->
                            "$FOCUSED_MOVEMENT_REPETITIONS movement takes complete and saved."
                        isSpecific -> "${focusedStep.recordingLabel} recording complete and saved."
                        else -> "Full guided prayer session complete and saved."
                    },
                    isRecording = false,
                    totalSamples = afterCount,
                    postureCounts = collectionService.getSessionStats().first,
                    currentPosture = SalahPosture.QIYAM
                )
            }

            speakAndWait("Recording complete. The session was saved. You may take your phone out now.")
            refreshFileList()
        }
    }

    /**
     * Plays an exact movement cue and starts sensor capture at audio playback onset.
     * Generation time is excluded; otherwise a slow first TTS generation could consume
     * most of the fixed transition window before the user hears "move now".
     */
    private suspend fun captureSpokenTransition(
        posture: SalahPosture,
        cue: String,
        durationSeconds: Int,
    ): Boolean = coroutineScope {
        val playbackStarted = CompletableDeferred<Boolean>()
        val voiceJob = launch {
            val spoken = speakAndWait(
                text = cue,
                onPlaybackStart = {
                    collectionService.resumeSampleCapture(posture)
                    playbackStarted.complete(true)
                },
            )
            if (!spoken) playbackStarted.complete(false)
        }

        val started = playbackStarted.await()
        if (!started) {
            voiceJob.join()
            return@coroutineScope false
        }

        runGuidedCaptureTimer(durationSeconds)
        collectionService.pauseSampleCapture()
        voiceJob.join()
        true
    }

    private suspend fun runGuidedCaptureTimer(durationSeconds: Int) {
        val deadlineMs = android.os.SystemClock.elapsedRealtime() + durationSeconds * 1000L
        while (true) {
            val remainingMs = deadlineMs - android.os.SystemClock.elapsedRealtime()
            if (remainingMs <= 0L) break
            val remainingSeconds = ((remainingMs + 999L) / 1000L).toInt()
            _uiState.update { it.copy(guidedPostureTimeRemaining = remainingSeconds) }
            delay(minOf(200L, remainingMs))
        }
        _uiState.update { it.copy(guidedPostureTimeRemaining = 0) }
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
                guidedMessage = "Cancelled by user. Any completed posture segments remain saved.",
                isRecording = false,
                currentPosture = SalahPosture.QIYAM
            )
        }
        refreshFileList()
    }

    fun resetGuidedState() {
        _uiState.update { it.copy(guidedState = GuidedRecordingState.IDLE, guidedMessage = "") }
    }

    private fun failGuidedRecording(message: String) {
        if (collectionService.isRecording()) {
            // Capture is paused before every instruction, so anything already written
            // has a known label. Never begin or retain the failed step as ground truth.
            collectionService.pauseSampleCapture()
            collectionService.stopRecording(trimLastMs = 0)
        }
        _uiState.update {
            it.copy(
                guidedState = GuidedRecordingState.CANCELLED,
                guidedMessage = message,
                isRecording = false,
                currentPosture = SalahPosture.QIYAM,
            )
        }
        refreshFileList()
    }

    private suspend fun speakAndWait(
        text: String,
        onPlaybackStart: (() -> Unit)? = null,
    ): Boolean = try {
            val tts = getTtsService()
            tts.speak(text = text, onPlaybackStart = onPlaybackStart)
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed: ${e.message}")
            false
        }

    // 3D Visualization methods

    fun loadAllSamples() = loadSamplesInto(sourceFile = null) { collectionService.listDataFiles() }

    /**
     * Load a single recording into the 3D view.
     *
     * Visualising one file is the useful case: [loadAllSamples] concatenates every
     * recording and sorts by timestamp, which interleaves unrelated sessions into a
     * sequence that never happened and makes playback meaningless.
     */
    fun loadSamplesForFile(fileName: String) = loadSamplesInto(sourceFile = fileName) {
        listOf(java.io.File(collectionService.getDataDirectory(), fileName)).filter { it.exists() }
    }

    private fun loadSamplesInto(
        sourceFile: String?,
        files: () -> List<java.io.File>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val samples = mutableListOf<SalahDataSample>()
            files().forEach { file ->
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
            _uiState.update { it.copy(vizSourceFile = sourceFile) }
            // Data changed — previous model analysis and PCA projection are stale.
            _vizState.update {
                it.copy(
                    totalSamples = sorted.size,
                    playbackIndex = 0,
                    isPlaying = false,
                    posePlaybackSource = PosePlaybackSource.RECORDED,
                    predictions = null,
                    flaggedIndices = emptySet(),
                    pcaPositions = null,
                    pcaVariance = null,
                )
            }
        }
    }

    /** Absolute path for a recording file name, for navigating to Review & Label. */
    fun filePathFor(fileName: String): String =
        java.io.File(collectionService.getDataDirectory(), fileName).absolutePath

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
