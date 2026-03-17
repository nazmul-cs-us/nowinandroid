package com.starception.submission.feature.salah.datacollection

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.ml.SalahPosture
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val filePath: String = ""
)

@HiltViewModel
class PrayerReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PrayerReviewVM"
    }

    private val _state = MutableStateFlow(PrayerReviewState())
    val state: StateFlow<PrayerReviewState> = _state.asStateFlow()

    private var rawLines = mutableListOf<String>()

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
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading file", e)
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
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
            selectedSegmentIndex = null
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
