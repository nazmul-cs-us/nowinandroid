package com.starception.submission.feature.salah.visualization

import com.starception.submission.ml.SalahPosture

enum class VisualizationMode {
    SCATTER,
    PHONE_MODEL,
    GRAVITY_VECTOR,

    /** 30-D feature space projected to 3-D via PCA — shows class separability. */
    FEATURE_PCA,
}

/** Per-window model prediction paired to the sample at the same index. */
data class VizPrediction(
    val predicted: SalahPosture?,
    val confidence: Float,
)

data class VisualizationState(
    val mode: VisualizationMode = VisualizationMode.PHONE_MODEL,
    val visiblePostures: Set<SalahPosture> = SalahPosture.classificationLabels.toSet(),
    val playbackIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val axisX: String = "pitch",
    val axisY: String = "roll",
    val axisZ: String = "am",
    val pointSize: Float = 3f,
    val totalSamples: Int = 0,
    val currentPosture: SalahPosture? = null,
    val currentPitch: Float = 0f,
    val currentRoll: Float = 0f,
    val currentAccelMag: Float = 0f,
    val currentGyroMag: Float = 0f,
    val cameraResetToken: Int = 0,

    // --- Model-vs-label diagnostics (null until an analysis has run) ---
    /** Parallel to the sample list; from SalahBatchInference over the loaded data. */
    val predictions: List<VizPrediction>? = null,
    /** Sample indices inside flagged (high-confidence disagreement) runs. */
    val flaggedIndices: Set<Int> = emptySet(),
    val isAnalyzingPredictions: Boolean = false,
    val showDisagreements: Boolean = false,
    val showEllipsoids: Boolean = false,

    // --- PCA projection (FEATURE_PCA mode) ---
    /** x,y,z triplets parallel to the sample list; null until computed. */
    val pcaPositions: FloatArray? = null,
    /** Fraction of the 30-D variance captured by the 3 components. */
    val pcaVariance: Float? = null,
    val isComputingPca: Boolean = false,
)
