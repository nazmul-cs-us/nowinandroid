/*
 * Copyright 2022 The Android Open Source Project
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

// Multiplatform as of the iOS/iPad/Mac port. This module keeps its `:core:model`
// path and its `com.starception.submission.core.model.*` packages, so the ~94
// files that import it and the 10 modules that depend on it are unaffected —
// only the source set moved from src/main to src/commonMain.
plugins {
    alias(libs.plugins.nowinandroid.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "com.starception.submission.core.model"
}
