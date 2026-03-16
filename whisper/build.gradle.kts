/*
 * Whisper Module
 * Provides offline speech-to-text using:
 * - whisper.cpp (native C++ implementation) - primary
 * - TensorFlow Lite implementation - legacy
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

    // LiteRT (successor to TFLite) for Whisper inference (legacy) - 16KB page aligned
    implementation("com.google.ai.edge.litert:litert:1.2.0")
    implementation("com.google.ai.edge.litert:litert-support:1.2.0")

    // JTransforms for fast FFT calculations (legacy)
    implementation("com.github.wendykierp:JTransforms:3.1")
}
