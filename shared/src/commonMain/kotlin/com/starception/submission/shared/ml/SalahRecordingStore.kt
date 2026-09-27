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

/**
 * One recorded session file — the shared counterpart of Android's DataFileInfo.
 */
data class SalahSessionInfo(
    val fileName: String,
    val sizeKb: Long,
    val sampleCount: Int,
    val postureCounts: Map<String, Int>,
    val lastModifiedMs: Long,
)

/**
 * Persists labeled [SalahDataSample] windows as JSONL session files — the
 * shared counterpart of Android's SalahDataCollectionService file pipeline:
 * guided/manual prefixes, auto-trim of the trailing window, and the
 * descriptive rename that names a file for the postures it actually holds.
 */
expect class SalahRecordingStore() {
    /** Opens a session file; returns its working name, or null on failure. */
    fun startSession(prefix: String, sessionId: String): String?

    /** Appends one window. Returns false once the file write fails. */
    fun appendSample(sample: SalahDataSample): Boolean

    /**
     * Closes the session: drops samples from the last [trimLastMs], renames
     * the file to name the postures it kept (Android's SalahRecordingName),
     * and returns the final file name.
     */
    fun stopSession(trimLastMs: Long): String?

    /** All saved sessions, newest first. */
    fun sessions(): List<SalahSessionInfo>

    fun deleteSession(fileName: String)

    fun deleteAllSessions()

    fun totalSizeKb(): Long
}

/** Posture slugs for file names — Android's POSTURE_SLUGS. */
internal fun postureSlug(posture: SalahPosture): String = when (posture) {
    SalahPosture.QIYAM -> "qiyam"
    SalahPosture.RUKU -> "ruku"
    SalahPosture.GOING_TO_SUJUD -> "going2sujud"
    SalahPosture.SUJUD -> "sujud"
    SalahPosture.JALSA -> "jalsa"
    SalahPosture.TASHAHHUD -> "tashahhud"
    SalahPosture.QIYAM_RISING -> "qiyamrising"
    SalahPosture.RISING_TO_QIYAM -> "rising2qiyam"
    SalahPosture.NOT_PRAYING -> "notpraying"
}
