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

package com.starception.submission.shared.quran

/**
 * The shared Quran screens are hosted by the iOS app; the Android app reads
 * the enhanced database through its own Room layer, so the Android target of
 * this expect is a stub.
 */
actual fun createQuranTafseerRepository(): QuranTafseerRepository =
    object : QuranTafseerRepository {
        override suspend fun getTafseer(surahNumber: Int, ayahNumber: Int): AyahTafseer? = null
    }
