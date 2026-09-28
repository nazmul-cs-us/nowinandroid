/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ml

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The model's class order — salah_norm_params.json's posture_labels. */
val MODEL_POSTURE_LABELS = listOf(
    SalahPosture.QIYAM,
    SalahPosture.RUKU,
    SalahPosture.GOING_TO_SUJUD,
    SalahPosture.SUJUD,
    SalahPosture.JALSA,
    SalahPosture.TASHAHHUD,
    SalahPosture.QIYAM_RISING,
)

/** Android's per-posture confidence thresholds (recall-favoring; the
 *  sequence validator does the rest). */
val POSTURE_CONFIDENCE_THRESHOLDS = mapOf(
    SalahPosture.QIYAM to 0.35f,
    SalahPosture.RUKU to 0.35f,
    SalahPosture.GOING_TO_SUJUD to 0.40f,
    SalahPosture.SUJUD to 0.35f,
    SalahPosture.JALSA to 0.40f,
    SalahPosture.TASHAHHUD to 0.40f,
    SalahPosture.QIYAM_RISING to 0.40f,
)

/** One window's model verdict against its recorded label. */
data class SalahWindowVerdict(
    val index: Int,
    val label: SalahPosture,
    val prediction: SalahPosture?,
    val confidence: Float,
    val agrees: Boolean,
)

/** The session's quality report — Android's "Analyze data quality". */
data class SalahQualityReport(
    val totalWindows: Int,
    val analyzedWindows: Int,
    val agreementPercent: Int,
    val perPosture: Map<String, Int>,
    val verdicts: List<SalahWindowVerdict>,
)

/** Loads salah_norm_params.json (mean/std/sequence shape) — the shared
 *  half of Android's SalahDetectionEngine init. */
class SalahNormParams private constructor(
    val mean: FloatArray,
    val std: FloatArray,
    val sequenceLength: Int,
    val featuresPerWindow: Int,
) {
    companion object {
        fun parse(json: String): SalahNormParams {
            val root = Json.parseToJsonElement(json).jsonObject
            fun floats(key: String) = root.getValue(key).jsonArray
                .map { it.jsonPrimitive.content.toFloat() }
                .toFloatArray()
            return SalahNormParams(
                mean = floats("mean"),
                std = floats("std"),
                sequenceLength = root.getValue("sequence_length").jsonPrimitive.content.toInt(),
                featuresPerWindow = root.getValue("features_per_window").jsonPrimitive.content.toInt(),
            )
        }
    }
}

/**
 * Runs the model-vs-label quality analysis over a recorded session — the
 * shared counterpart of Android's PrayerReview quality pass: features are
 * extracted with [SalahFeatureExtractor], z-scored with the norm params,
 * classified by the host's TFLite service, and each verdict compared with
 * the posture the session recorded for that window.
 */
class SalahQualityAnalyzer(
    private val service: SalahTfliteService,
    private val normParams: SalahNormParams,
) {
    fun analyze(samples: List<SalahDataSample>): SalahQualityReport {
        val extractor = SalahFeatureExtractor

        val features = samples.map { extractor.extractFeatures(it) }
        val mean = normParams.mean
        val std = normParams.std
        val sequenceLength = normParams.sequenceLength
        val verdicts = mutableListOf<SalahWindowVerdict>()
        for (index in samples.indices) {
            if (index + 1 < sequenceLength) continue
            val sequence = Array(sequenceLength) { offset ->
                val f = features[index + 1 - sequenceLength + offset]
                FloatArray(f.size) { j -> (f[j] - mean[j]) / std[j] }
            }
            val probabilities = service.classify(sequence) ?: continue
            var bestIndex = -1
            var bestValue = 0f
            probabilities.forEachIndexed { i, p ->
                if (p > bestValue) {
                    bestValue = p
                    bestIndex = i
                }
            }
            val predicted = MODEL_POSTURE_LABELS.getOrNull(bestIndex)
            val threshold = predicted?.let { POSTURE_CONFIDENCE_THRESHOLDS[it] } ?: 0.40f
            val accepted = bestValue >= threshold
            val label = samples[index].posture
            verdicts += SalahWindowVerdict(
                index = index,
                label = label,
                prediction = if (accepted) predicted else null,
                confidence = bestValue,
                agrees = accepted && predicted == label,
            )
        }
        val perPosture = verdicts.groupBy { it.label.name }
            .mapValues { (_, list) -> (list.count { it.agrees } * 100 / list.size).toInt() }
        val agreement = if (verdicts.isEmpty()) {
            0
        } else {
            verdicts.count { it.agrees } * 100 / verdicts.size
        }
        return SalahQualityReport(
            totalWindows = samples.size,
            analyzedWindows = verdicts.size,
            agreementPercent = agreement,
            perPosture = perPosture,
            verdicts = verdicts,
        )
    }
}

/** Implemented by the iOS host with the bundled salah_detector.tflite. */
interface SalahTfliteService {
    /** Classifies one normalized [sequenceLength][features] window; null on inference failure. */
    fun classify(sequence: Array<FloatArray>): FloatArray?
}
