/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.hadith

/**
 * The Android app module owns its typed-question answer pipeline (retrieval
 * plus the on-device answer model); the shared bus search serves the iOS
 * composer, so Android needs no implementation here.
 */
actual suspend fun searchEnglishHadithCollections(
    tokens: List<String>,
    limit: Int,
): List<HadithSearchHit> = emptyList()
