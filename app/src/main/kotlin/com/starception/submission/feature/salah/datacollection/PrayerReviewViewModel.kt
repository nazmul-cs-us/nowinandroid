package com.starception.submission.feature.salah.datacollection

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.ml.SalahBatchInference
import com.starception.submission.ml.SalahPosture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

data class PostureSegment(
    val startIndex: Int,
    val endIndex: Int,
    val posture: SalahPosture,
    val predictedPosture: SalahPosture,
    val confidence: Float,
    val wasEdited: Boolean = false
)

data class PrayerReviewState(
    val segments: List<PostureSegment> = emptyList(),
    val selectedSegmentIndex: Int? = null,
    val totalSamples: Int = 0,
    val postureCounts: Map<SalahPosture, Int> = emptyMap(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val filePath: String = "",
    // Model-vs-label quality analysis (null until run; cleared when labels change)
    val isAnalyzing: Boolean = false,
    val analysisProgress: Int = 0,
    val analysis: SalahBatchInference.BatchResult? = null,
    /** timeline segment index -> number of flagged (high-confidence disagreement) windows */
    val flaggedPerSegment: Map<Int, Int> = emptyMap(),
)

@HiltViewModel
class PrayerReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "PrayerReviewVM"
    }

    private val _state = MutableStateFlow(PrayerReviewState())
    val state: StateFlow<PrayerReviewState> = _state.asStateFlow()

    private var rawLines = mutableListOf<String>()
    private var autoAnalyzedPath: String? = null

    fun loadFile(filePath: String) {
        _state.value = _state.value.copy(filePath = filePath, isLoading = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        Log.e(TAG, "File not found: $filePath")
                        _state.value = _state.value.copy(isLoading = false)
                        return@withContext
                    }

                    rawLines = file.readLines().filter { it.isNotBlank() }.toMutableList()
                    Log.i(TAG, "Loaded ${rawLines.size} samples from $filePath")

                    // Group consecutive samples with same posture into segments
                    val segments = mutableListOf<PostureSegment>()
                    var i = 0
                    while (i < rawLines.size) {
                        val json = JSONObject(rawLines[i])
                        val postureName = json.optString("posture", "QIYAM")
                        val posture = try { SalahPosture.valueOf(postureName) } catch (_: Exception) { SalahPosture.QIYAM }
                        val confidence = json.optDouble("predicted_confidence", 0.5).toFloat()

                        val startIdx = i
                        while (i < rawLines.size) {
                            val nextJson = JSONObject(rawLines[i])
                            val nextPosture = try { SalahPosture.valueOf(nextJson.optString("posture", "QIYAM")) } catch (_: Exception) { SalahPosture.QIYAM }
                            if (nextPosture != posture) break
                            i++
                        }

                        segments.add(PostureSegment(
                            startIndex = startIdx,
                            endIndex = i - 1,
                            posture = posture,
                            predictedPosture = posture,
                            confidence = confidence
                        ))
                    }

                    // Count postures
                    val counts = mutableMapOf<SalahPosture, Int>()
                    for (seg in segments) {
                        val count = seg.endIndex - seg.startIndex + 1
                        counts[seg.posture] = (counts[seg.posture] ?: 0) + count
                    }

                    _state.value = _state.value.copy(
                        segments = segments,
                        totalSamples = rawLines.size,
                        postureCounts = counts,
                        isLoading = false
                    )

                    // Instant quality check: recordings open this screen right after
                    // saving, so auto-run the model-vs-label analysis for anything
                    // big enough to classify but small enough to be quick.
                    if (rawLines.size in 20..6000 && autoAnalyzedPath != filePath) {
                        autoAnalyzedPath = filePath
                        analyzeQuality()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading file", e)
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
    }

    /**
     * Run the current on-device model over the whole file and compare against labels.
     * Results drive the quality card and the warning badges on timeline segments.
     */
    fun analyzeQuality() {
        if (_state.value.isAnalyzing) return
        val filePath = _state.value.filePath
        if (filePath.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isAnalyzing = true, analysisProgress = 0)
            try {
                val result = withContext(Dispatchers.Default) {
                    SalahBatchInference(appContext).use { batch ->
                        batch.analyzeFile(filePath) { processed ->
                            _state.value = _state.value.copy(analysisProgress = processed)
                        }
                    }
                }
                _state.value = _state.value.copy(
                    isAnalyzing = false,
                    analysis = result,
                    flaggedPerSegment = mapFlagsToSegments(result, _state.value.segments),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Quality analysis failed", e)
                _state.value = _state.value.copy(isAnalyzing = false)
            }
        }
    }

    /** Select the timeline segment containing [windowIndex] (from a flagged-segment tap). */
    fun selectSegmentAtWindow(windowIndex: Int) {
        val idx = _state.value.segments.indexOfFirst {
            windowIndex in it.startIndex..it.endIndex
        }
        if (idx >= 0) {
            _state.value = _state.value.copy(selectedSegmentIndex = idx)
        }
    }

    private fun mapFlagsToSegments(
        result: SalahBatchInference.BatchResult,
        segments: List<PostureSegment>,
    ): Map<Int, Int> {
        val perSegment = mutableMapOf<Int, Int>()
        for (flag in result.flaggedSegments) {
            segments.forEachIndexed { segIndex, seg ->
                val overlap = minOf(flag.endIndex, seg.endIndex) -
                    maxOf(flag.startIndex, seg.startIndex) + 1
                if (overlap > 0) {
                    perSegment[segIndex] = (perSegment[segIndex] ?: 0) + overlap
                }
            }
        }
        return perSegment
    }

    fun selectSegment(index: Int) {
        _state.value = _state.value.copy(
            selectedSegmentIndex = if (_state.value.selectedSegmentIndex == index) null else index
        )
    }

    fun changeSegmentPosture(segmentIndex: Int, newPosture: SalahPosture) {
        val segments = _state.value.segments.toMutableList()
        if (segmentIndex !in segments.indices) return

        val old = segments[segmentIndex]
        segments[segmentIndex] = old.copy(posture = newPosture, wasEdited = true)

        // Recount
        val counts = mutableMapOf<SalahPosture, Int>()
        for (seg in segments) {
            val count = seg.endIndex - seg.startIndex + 1
            counts[seg.posture] = (counts[seg.posture] ?: 0) + count
        }

        _state.value = _state.value.copy(
            segments = segments,
            postureCounts = counts,
            selectedSegmentIndex = null,
            // Labels changed — the previous model-vs-label comparison is stale.
            analysis = null,
            flaggedPerSegment = emptyMap(),
        )
    }

    fun saveLabels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            withContext(Dispatchers.IO) {
                try {
                    val segments = _state.value.segments
                    val file = File(_state.value.filePath)

                    // Rewrite each line with corrected posture
                    BufferedWriter(FileWriter(file, false)).use { writer ->
                        for (seg in segments) {
                            for (idx in seg.startIndex..seg.endIndex) {
                                if (idx < rawLines.size) {
                                    val json = JSONObject(rawLines[idx])
                                    val originalPosture = json.optString("posture", "")
                                    json.put("posture", seg.posture.name)
                                    if (seg.wasEdited) {
                                        json.put("original_posture", originalPosture)
                                        json.put("manually_labeled", true)
                                    }
                                    writer.write(json.toString())
                                    writer.newLine()
                                }
                            }
                        }
                    }

                    Log.i(TAG, "Labels saved to ${file.absolutePath}")
                    _state.value = _state.value.copy(isSaving = false, isSaved = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving labels", e)
                    _state.value = _state.value.copy(isSaving = false)
                }
            }
        }
    }

    fun discardRecording() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    File(_state.value.filePath).delete()
                    Log.i(TAG, "Discarded recording: ${_state.value.filePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error discarding recording", e)
                }
            }
        }
    }
}
