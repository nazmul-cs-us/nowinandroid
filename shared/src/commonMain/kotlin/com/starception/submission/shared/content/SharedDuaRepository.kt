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

package com.starception.submission.shared.content

/**
 * A Quranic invocation from the quranic_duas database — the 40 duas
 * drawn from the Quran itself, mirroring the Android Dua model.
 */
data class SharedQuranicDua(
    val id: Int,
    val duaNumber: Int,
    val title: String,
    val surahReference: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val explanation: String,
)

interface SharedDuaRepository {
    suspend fun getQuranicDuas(): List<SharedQuranicDua>
}

expect fun createSharedDuaRepository(): SharedDuaRepository
