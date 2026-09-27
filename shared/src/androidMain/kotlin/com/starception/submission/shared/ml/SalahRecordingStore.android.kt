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

actual class SalahRecordingStore actual constructor() {
    actual fun startSession(prefix: String, sessionId: String): String? {
        error("The shared recording store is packaged by the iOS host only")
    }

    actual fun appendSample(sample: SalahDataSample): Boolean {
        error("The shared recording store is packaged by the iOS host only")
    }

    actual fun stopSession(trimLastMs: Long): String? {
        error("The shared recording store is packaged by the iOS host only")
    }

    actual fun sessions(): List<SalahSessionInfo> = emptyList()

    actual fun deleteSession(fileName: String) = Unit

    actual fun deleteAllSessions() = Unit

    actual fun totalSizeKb(): Long = 0L
}
