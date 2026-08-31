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
    // Compose Multiplatform, for the UI shared with iOS. Contained to this module:
    // nothing on the Android build path depends on :shared.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose)
    // For parsing the Open-Meteo response in commonMain.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // The framework iosApp/ links against. Everything the iOS host needs should
    // be reachable through here, which is why dependencies below are `api`.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("sqlite3") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/sqlite3.def"))
        }
        binaries.framework {
            baseName = "Shared"
            isStatic = true
            // `api(projects.core.model)` alone only makes the types available to
            // Kotlin; the generated Objective-C header exports declarations from
            // this module only. Without an explicit export the framework ships
            // with nothing but the Kotlin runtime base classes, and iosApp/ can
            // see no model types at all.
            export(projects.core.model)
            export(projects.core.logging)
            export(projects.core.prayerEngine)
            export(projects.core.theme)
            export(projects.core.images)
            export(projects.core.components)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Re-exported so the generated iOS framework exposes the model types
            // directly, rather than making iosApp/ depend on :core:model itself.
            api(projects.core.model)
            api(projects.core.logging)
            api(projects.core.prayerEngine)
            api(projects.core.theme)
            api(projects.core.images)
            api(projects.core.components)
            implementation(libs.compose.navigation)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
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
