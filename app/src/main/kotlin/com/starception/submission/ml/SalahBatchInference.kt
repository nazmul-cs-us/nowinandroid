package com.starception.submission.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.File

/**
 * Offline model-vs-label analysis for recorded training files.
 *
 * Streams a JSONL recording through a [SalahDetectionEngine] exactly the way live
 * detection does (same buffering, EMA smoothing, and thresholds), then compares each
 * window's prediction against its recorded label. High-confidence disagreement runs are
 * flagged: they are either mislabeled data (fix in review) or genuinely hard examples
 * (collect more like them / retrain).
 */
class SalahBatchInference(context: Context) : Closeable {

    companion object {
        private const val TAG = "SalahBatchInference"

        /** A disagreement run must be at least this many windows to be flagged. */
        private const val FLAG_MIN_RUN = 3

        /** Disagreements only count toward flags at or above this confidence. */
        private const val FLAG_MIN_CONFIDENCE = 0.5f
    }

    data class WindowPrediction(
        val index: Int,
        val label: SalahPosture,
        /** Null while the engine is warming up or the prediction was below threshold. */
        val predicted: SalahPosture?,
        val confidence: Float,
    ) {
        val isAgreement: Boolean get() = predicted != null && predicted == label
    }

    data class FlaggedSegment(
        val startIndex: Int,
        val endIndex: Int,
        val label: SalahPosture,
        val predicted: SalahPosture,
        val avgConfidence: Float,
    ) {
        val windowCount: Int get() = endIndex - startIndex + 1
    }

    data class BatchResult(
        val filePath: String,
        val totalWindows: Int,
        val predictions: List<WindowPrediction>,
        /** Agreement over windows that produced a confident prediction. */
        val overallAgreement: Float,
        /** label -> (predicted -> count), confident predictions only. */
        val confusion: Map<SalahPosture, Map<SalahPosture, Int>>,
        val flaggedSegments: List<FlaggedSegment>,
    ) {
        /** Windows the engine never classified (warmup + below threshold). */
        val unclassifiedWindows: Int get() = predictions.count { it.predicted == null }
    }

    private val engine = SalahDetectionEngine(context)

    /**
     * Analyze a JSONL recording. Streams line by line — never holds the file in memory.
     */
    suspend fun analyzeFile(
        filePath: String,
        onProgress: (processed: Int) -> Unit = {},
    ): BatchResult = withContext(Dispatchers.Default) {
        val predictions = File(filePath).bufferedReader().useLines { lines ->
            runInference(
                lines.mapNotNull { line ->
                    if (line.isBlank()) return@mapNotNull null
                    try {
                        SalahDataSample.fromJson(JSONObject(line))
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping unparseable line", e)
                        null
                    }
                },
                onProgress,
            )
        }
        buildResult(filePath, predictions)
    }

    /**
     * Analyze already-loaded samples (e.g. the visualization's combined dataset).
     */
    suspend fun analyzeSamples(
        samples: List<SalahDataSample>,
        onProgress: (processed: Int) -> Unit = {},
    ): BatchResult = withContext(Dispatchers.Default) {
        buildResult("(in-memory)", runInference(samples.asSequence(), onProgress))
    }

    /**
     * Stream samples through the engine the way live detection would. The engine is
     * reset at every session boundary so one recording's buffer/EMA state never
     * bleeds into the next.
     */
    private fun runInference(
        samples: Sequence<SalahDataSample>,
        onProgress: (Int) -> Unit,
    ): List<WindowPrediction> {
        engine.reset()
        val predictions = mutableListOf<WindowPrediction>()
        var index = 0
        var lastSession: String? = null

        for (sample in samples) {
            if (sample.sessionId != lastSession) {
                engine.reset()
                lastSession = sample.sessionId
            }
            val result = engine.addSampleAndClassify(sample)
            predictions.add(
                WindowPrediction(
                    index = index,
                    label = sample.posture,
                    predicted = result?.posture,
                    confidence = result?.confidence ?: 0f,
                ),
            )
            index++
            if (index % 50 == 0) onProgress(index)
        }
        onProgress(index)
        return predictions
    }

    private fun buildResult(
        filePath: String,
        predictions: List<WindowPrediction>,
    ): BatchResult {
        val confident = predictions.filter { it.predicted != null }
        val agreement = if (confident.isEmpty()) {
            0f
        } else {
            confident.count { it.isAgreement }.toFloat() / confident.size
        }

        val confusion = mutableMapOf<SalahPosture, MutableMap<SalahPosture, Int>>()
        for (p in confident) {
            val row = confusion.getOrPut(p.label) { mutableMapOf() }
            row[p.predicted!!] = (row[p.predicted] ?: 0) + 1
        }

        // Flag runs of consecutive same-way disagreements (same label AND same wrong
        // prediction) with high confidence — one flag per run.
        val flagged = mutableListOf<FlaggedSegment>()
        var runStart = -1
        var runConfidenceSum = 0f
        var runLabel: SalahPosture? = null
        var runPredicted: SalahPosture? = null

        fun closeRun(endIndex: Int) {
            val start = runStart
            val label = runLabel
            val predicted = runPredicted
            if (start >= 0 && label != null && predicted != null) {
                val count = endIndex - start + 1
                if (count >= FLAG_MIN_RUN) {
                    flagged.add(
                        FlaggedSegment(
                            startIndex = start,
                            endIndex = endIndex,
                            label = label,
                            predicted = predicted,
                            avgConfidence = runConfidenceSum / count,
                        ),
                    )
                }
            }
            runStart = -1
            runConfidenceSum = 0f
            runLabel = null
            runPredicted = null
        }

        for (p in predictions) {
            val disagrees = p.predicted != null &&
                p.predicted != p.label &&
                p.confidence >= FLAG_MIN_CONFIDENCE
            if (disagrees && p.label == (runLabel ?: p.label) && p.predicted == (runPredicted ?: p.predicted)) {
                if (runStart < 0) {
                    runStart = p.index
                    runLabel = p.label
                    runPredicted = p.predicted
                }
                runConfidenceSum += p.confidence
            } else {
                closeRun(p.index - 1)
                if (disagrees) {
                    runStart = p.index
                    runLabel = p.label
                    runPredicted = p.predicted
                    runConfidenceSum = p.confidence
                }
            }
        }
        closeRun(predictions.lastOrNull()?.index ?: -1)

        Log.i(
            TAG,
            "Analyzed $filePath: ${predictions.size} windows, " +
                "agreement=${"%.1f".format(agreement * 100)}%, flags=${flagged.size}",
        )

        return BatchResult(
            filePath = filePath,
            totalWindows = predictions.size,
            predictions = predictions,
            overallAgreement = agreement,
            confusion = confusion,
            flaggedSegments = flagged,
        )
    }

    override fun close() {
        engine.close()
    }
}
