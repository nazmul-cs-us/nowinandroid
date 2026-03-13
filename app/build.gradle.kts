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
import com.google.samples.apps.nowinandroid.NiaBuildType

plugins {
    alias(libs.plugins.nowinandroid.android.application)
    alias(libs.plugins.nowinandroid.android.application.compose)
    alias(libs.plugins.nowinandroid.android.application.flavors)
    alias(libs.plugins.nowinandroid.android.application.jacoco)
    alias(libs.plugins.nowinandroid.android.application.firebase)
    alias(libs.plugins.nowinandroid.hilt)
    id("com.google.android.gms.oss-licenses-plugin")
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 36
    defaultConfig {
        applicationId = "com.starception.submission"
        versionCode = 8
        versionName = "0.1.2" // X.Y.Z; X = Major, Y = minor, Z = Patch level
        minSdk = 28
        targetSdk = 36

        // Custom test runner to set up Hilt dependency graph
        testInstrumentationRunner = "com.starception.submission.core.testing.NiaTestRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = NiaBuildType.DEBUG.applicationIdSuffix
            // Coverage instrumentation is breaking Hilt generated injector packaging at runtime.
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false
        }
        release {
            isMinifyEnabled = true
            applicationIdSuffix = NiaBuildType.RELEASE.applicationIdSuffix
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro")

            // To publish on the Play store a private signing key is required, but to allow anyone
            // who clones the code to sign and run the release variant, use the debug signing key.
            // TODO: Abstract the signing configuration to a separate file to avoid hardcoding this.
            signingConfig = signingConfigs.named("debug").get()
            // Ensure Baseline Profile is fresh for release builds.
            baselineProfile.automaticGenerationDuringBuild = true
        }
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    namespace = "com.starception.submission"
}

// Custom configuration for LibGDX native .so extraction
val natives by configurations.creating

dependencies {
    implementation(projects.feature.interests)
    implementation(projects.feature.foryou)
    implementation(projects.feature.bookmarks)
    implementation(projects.feature.topic)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)

    // Sherpa-ONNX for offline TTS and speech recognition
    implementation(files("libs/sherpa-onnx-1.12.26.aar"))

    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.model)
    implementation(projects.core.analytics)
    implementation(projects.sync.work)

    // Whisper TFLite module for offline speech recognition
    implementation(projects.whisper)

    // TFLite for salah posture detection
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

    // LibGDX for 3D sensor data visualization
    implementation(libs.libgdx.core)
    implementation(libs.libgdx.backend.android)
    "natives"(variantOf(libs.libgdx.platform) { classifier("natives-arm64-v8a") })
    "natives"(variantOf(libs.libgdx.platform) { classifier("natives-armeabi-v7a") })
    "natives"(variantOf(libs.libgdx.platform) { classifier("natives-x86") })
    "natives"(variantOf(libs.libgdx.platform) { classifier("natives-x86_64") })

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.runtime.tracing)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    
    // Material Design Components for View-based layouts
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.tracing.ktx)
    implementation(libs.androidx.window.core)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.coil.kt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.accompanist.permissions)

    // Reorderable library for drag-and-drop list reordering
    implementation(libs.reorderable)

    // Media support for playback controls
    implementation("androidx.media:media:1.7.0")

    // Media3 ExoPlayer for video splash screen
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Android Auto support
    implementation(libs.androidx.car.app)

    // WorkManager for reliable prayer notifications
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.ext.work)

    // Room Database for Quran
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // NASA WorldWind for 3D globe visualization via JitPack
    implementation("com.github.NASAWorldWind:WorldWindAndroid:v0.8.0")

    // AndroidLiquidGlass library for glassmorphism effects
    implementation("io.github.kyant0:backdrop:1.0.0")
    implementation("io.github.kyant0:capsule:2.1.2")

    // Sherpa-ONNX provided via api() from feature/search

    // OSS Licenses for About section
    implementation(libs.google.oss.licenses)

    ksp(libs.hilt.compiler)
    ksp(libs.hilt.ext.compiler)
    ksp(libs.room.compiler)

    debugImplementation(libs.androidx.compose.ui.testManifest)
    debugImplementation(projects.uiTestHiltManifest)

    kspTest(libs.hilt.compiler)

    testImplementation(projects.core.dataTest)
    testImplementation(projects.core.datastoreTest)
    testImplementation(libs.hilt.android.testing)
    testImplementation(projects.sync.syncTest)
    testImplementation(libs.kotlin.test)

    testDemoImplementation(libs.androidx.navigation.testing)
    testDemoImplementation(libs.robolectric)
    testDemoImplementation(libs.roborazzi)
    testDemoImplementation(projects.core.screenshotTesting)
    testDemoImplementation(projects.core.testing)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(projects.core.dataTest)
    androidTestImplementation(projects.core.datastoreTest)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlin.test)

    baselineProfile(projects.benchmarks)
}

// Extract LibGDX native .so files from platform JARs into jniLibs at configuration time
// LibGDX packages .so at JAR root, not in lib/<abi>/, so Android plugin can't find them
natives.files.forEach { nativeJar ->
    val abi = when {
        nativeJar.name.contains("arm64-v8a") -> "arm64-v8a"
        nativeJar.name.contains("armeabi-v7a") -> "armeabi-v7a"
        nativeJar.name.contains("x86_64") -> "x86_64"
        nativeJar.name.contains("x86") -> "x86"
        else -> return@forEach
    }
    val outputDir = file("src/main/jniLibs/$abi")
    outputDir.mkdirs()
    copy {
        from(zipTree(nativeJar))
        into(outputDir)
        include("*.so")
    }
}

baselineProfile {
    // Don't build on every iteration of a full assemble.
    // Instead enable generation directly for the release build variant.
    automaticGenerationDuringBuild = false

    // Make use of Dex Layout Optimizations via Startup Profiles
    dexLayoutOptimization = true
}

dependencyGuard {
    configuration("prodReleaseRuntimeClasspath")
}
