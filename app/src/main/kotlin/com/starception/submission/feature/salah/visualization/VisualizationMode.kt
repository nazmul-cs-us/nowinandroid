package com.starception.submission.feature.salah.visualization

import com.starception.submission.ml.SalahPosture

enum class VisualizationMode {
    SCATTER,
    PHONE_MODEL,
    GRAVITY_VECTOR
}

data class VisualizationState(
    val mode: VisualizationMode = VisualizationMode.SCATTER,
    val visiblePostures: Set<SalahPosture> = SalahPosture.classificationLabels.toSet(),
    val playbackIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 5f,
    val axisX: String = "pitch",
    val axisY: String = "roll",
    val axisZ: String = "am",
    val pointSize: Float = 3f,
    val totalSamples: Int = 0,
    val currentPosture: SalahPosture? = null,
    val currentPitch: Float = 0f,
    val currentRoll: Float = 0f,
    val currentAccelMag: Float = 0f,
    val currentGyroMag: Float = 0f
)
