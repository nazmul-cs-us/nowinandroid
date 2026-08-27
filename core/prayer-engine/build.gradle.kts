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

// The astronomical calculation engine, shared with iOS.
//
// Scope is deliberately narrow: `Location` plus the solar maths. The calculator
// needs nothing else from the prayer model — every other entry point takes and
// returns `Double`. The schedule assembly, settings and presentation types stay
// in `app` on java.time for now and convert at the boundary, which keeps this
// module free of the JVM-only date API without forcing a ~410-edit migration
// across 25 consumer files.
//
// Packages are unchanged from when these files lived in `app`, so nothing
// downstream needed re-importing. A package spanning two Gradle modules is fine
// for Kotlin/Android.
plugins {
    alias(libs.plugins.nowinandroid.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.starception.submission.core.prayerengine"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.core.logging)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
