/*
 * Copyright 2021 The Android Open Source Project
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

plugins {
    alias(libs.plugins.nowinandroid.kmp.library)
}

kotlin {
    // The framework iosApp/ links against. Everything the iOS host needs should
    // be reachable through here, which is why dependencies below are `api`.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
            // `api(projects.core.model)` alone only makes the types available to
            // Kotlin; the generated Objective-C header exports declarations from
            // this module only. Without an explicit export the framework ships
            // with nothing but the Kotlin runtime base classes, and iosApp/ can
            // see no model types at all.
            export(projects.core.model)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Re-exported so the generated iOS framework exposes the model types
            // directly, rather than making iosApp/ depend on :core:model itself.
            api(projects.core.model)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

android {
    namespace = "com.starception.submission.shared"
}
