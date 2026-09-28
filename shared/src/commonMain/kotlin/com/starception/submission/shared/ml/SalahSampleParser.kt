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

/** Parses one JSONL export line (SalahDataSample.toJson) back into a sample. */
internal fun parseSalahSampleLine(line: String): SalahDataSample? {
    if (line.isBlank()) return null
    return runCatching {
        val root = Json.parseToJsonElement(line.trim()).jsonObject
        fun floats(key: String) = root.getValue(key).jsonArray
            .map { it.jsonPrimitive.content.toFloat() }
            .toFloatArray()
        SalahDataSample(
            timestamp = root.getValue("timestamp").jsonPrimitive.content.toLong(),
            sessionId = root.getValue("session_id").jsonPrimitive.content,
            posture = SalahPosture.entries.firstOrNull {
                it.name == root.getValue("posture").jsonPrimitive.content
            } ?: SalahPosture.NOT_PRAYING,
            accelX = floats("accelX"),
            accelY = floats("accelY"),
            accelZ = floats("accelZ"),
            gyroX = floats("gyroX"),
            gyroY = floats("gyroY"),
            gyroZ = floats("gyroZ"),
            pitch = root.getValue("pitch").jsonPrimitive.content.toFloat(),
            roll = root.getValue("roll").jsonPrimitive.content.toFloat(),
            accelMagnitude = root.getValue("accelMagnitude").jsonPrimitive.content.toFloat(),
            gyroMagnitude = root.getValue("gyroMagnitude").jsonPrimitive.content.toFloat(),
        )
    }.getOrNull()
}
