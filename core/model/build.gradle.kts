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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Multiplatform as of the iOS/iPad/Mac port. This module keeps its `:core:model`
// path and its `com.starception.submission.core.model.*` packages, so the ~94
// files that import it and the 10 modules that depend on it are unaffected —
// only the source set moved from src/main to src/commonMain.
//
// It cannot use the `nowinandroid.*` convention plugins: configureKotlin() in
// build-logic handles only KotlinAndroidProjectExtension and
// KotlinJvmProjectExtension and hits TODO() for KotlinMultiplatformExtension.
// compileSdk/minSdk/JVM target below must be kept in sync with KotlinAndroid.kt.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    // Every consumer of this module is an android.library or the app itself,
    // so an AAR-producing androidTarget is what they resolve against.
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Mirrors configureKotlin() in build-logic/.../KotlinAndroid.kt, which this
    // module no longer goes through. Dropping these is not cosmetic: without the
    // kotlin.time opt-in, kotlinx.datetime.Instant (a typealias to
    // kotlin.time.Instant since datetime 0.7) fails to compile outright.
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-Xconsistent-data-class-copy-visibility",
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "com.starception.submission.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
