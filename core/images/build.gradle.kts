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

// Image assets shared between Android and iOS.
//
// The files were moved here rather than copied: at 2.8 MB the prayer sky family
// is too large to keep two of, and Android reads them through the same Compose
// Resources API rather than R.drawable. One copy, both platforms.
plugins {
    alias(libs.plugins.nowinandroid.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.starception.submission.core.images"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.ui)
            api(compose.components.resources)
        }
    }
}

// `Res` must be public: the Android app module consumes these resources too, and
// an internal accessor would only be reachable from inside this module.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.starception.submission.core.images.resources"
}
