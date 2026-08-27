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

// The portable half of the design system: colour palette, colour schemes and
// dimensions. All of it is plain Compose, so it works unchanged on iOS.
//
// core:designsystem keeps everything that cannot cross — dynamic colour
// (Material You), font resources, drawables — and depends on this with `api`,
// so its own consumers see no difference.
//
// Packages are unchanged from when these files lived in core:designsystem, so
// none of the ~60 files importing them needed editing.
plugins {
    alias(libs.plugins.nowinandroid.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.starception.submission.core.theme"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.ui)
            api(compose.material3)
            // Generates the Res accessors for the bundled fonts.
            implementation(compose.components.resources)
        }
    }
}

// Pin the generated resource package. Left to itself the plugin derives
// "submission.core.theme.generated.resources" from the namespace, dropping the
// company prefix — fine but surprising, and not something to have imports depend on.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.starception.submission.core.theme.resources"
}
