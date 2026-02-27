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
    implementation(files("libs/whisper-cpp.aar"))

    // TensorFlow Lite for Whisper inference (legacy)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // GPU delegate for faster inference (legacy)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.14.0")

    // JTransforms for fast FFT calculations (legacy)
    implementation("com.github.wendykierp:JTransforms:3.1")
}
