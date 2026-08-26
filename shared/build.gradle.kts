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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// This module deliberately does NOT use the `nowinandroid.*` convention plugins.
// configureKotlin() in build-logic (KotlinAndroid.kt) only handles
// KotlinAndroidProjectExtension and KotlinJvmProjectExtension, and hits a TODO()
// for anything else — including KotlinMultiplatformExtension. The Android/JVM
// settings it would have applied (compileSdk 36, minSdk 28, JVM 17) are
// reproduced by hand below and must be kept in sync with that file.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    compilerOptions {
        // `expect`/`actual` on classes and objects is still flagged Beta and warns
        // on every declaration. build-logic honours a `warningsAsErrors` property,
        // so left alone this would break the build the moment anyone sets it.
        freeCompilerArgs.add("-Xexpect-actual-classes")

        // Mirrors configureKotlin() in build-logic/.../KotlinAndroid.kt, which
        // multiplatform modules do not go through. Keep in sync with that file.
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-Xconsistent-data-class-copy-visibility",
        )
    }

    // Apple targets. Declaring them costs nothing without Xcode — only the
    // native compile/link tasks need the iOS SDK, and nothing in the Android
    // build graph depends on them. iosX64 covers Intel simulators;
    // iosSimulatorArm64 covers Apple Silicon simulators and, via
    // "Designed for iPad", is what ultimately runs on M-series Macs.
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
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
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
