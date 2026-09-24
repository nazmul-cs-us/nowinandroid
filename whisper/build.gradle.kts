/*
 * Copyright 2026 The Android Open Source Project
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
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.whispertflite"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")

    // Coroutines for whisper.cpp
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // whisper.cpp AAR (native C++ implementation)
    // Use api() so WhisperContext and other classes are transitively visible to consumers
    api(files("libs/whisper-cpp.aar"))

    // LiteRT (successor to TFLite) for Whisper inference (legacy).
    // 1.2.0's libtensorflowlite_jni.so was 16KB-page-aligned on arm64-v8a but NOT on
    // x86_64 (still 4KB) — Play Store's 16KB check covers both 64-bit ABIs. 1.4.0 is
    // the first release with x86_64 fixed too; pinned to 1.4.2 (latest 1.x patch,
    // same org.tensorflow.lite-compatible API surface as 1.2.0).
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.google.ai.edge.litert:litert-support:1.4.2")

    // JTransforms for fast FFT calculations (legacy)
    implementation("com.github.wendykierp:JTransforms:3.1")
}
