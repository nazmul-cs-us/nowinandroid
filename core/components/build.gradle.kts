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

// Shared UI components, starting with the prayer settings section.
//
// The settings screen is the backbone of the prayer feature, so it is ported
// rather than reimplemented: the same composable renders on both platforms, and
// there is no second version to drift. Its dependencies came with it — the
// outlined button convention, the icon set and the motion specs.
//
// Packages are unchanged, so core:designsystem's ~30 NiaOutlinedButton callers
// and the settings screen's own imports were untouched.
plugins {
    alias(libs.plugins.nowinandroid.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.starception.submission.core.components"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.theme)
            api(projects.core.images)
            api(projects.core.model)
            api(projects.core.prayerEngine)
            api(compose.runtime)
            api(compose.ui)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            implementation(compose.components.uiToolingPreview)
        }
    }
}
