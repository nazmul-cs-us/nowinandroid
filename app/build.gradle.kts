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
@file:Suppress("UnstableApiUsage")

import com.google.samples.apps.nowinandroid.NiaBuildType
import java.io.FileInputStream
import java.util.Properties

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

    // Release signing configuration loaded from keystore.properties
    val keystorePropertiesFile = file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
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

            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.named("release").get()
            } else {
                signingConfigs.named("debug").get()
            }
            // Ensure Baseline Profile is fresh for release builds.
            baselineProfile.automaticGenerationDuringBuild = true
        }
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
        // useLegacyPackaging removed for 16KB page size support (AGP 8.5.1+ handles alignment)
        jniLibs.useLegacyPackaging = false
    }

    androidResources {
        // Audio recitations (quran/hadith/fortress) are CDN-only: staged under
        // src/main/assets/audio/ for upload to R2 but must NOT ship in the APK. The app
        // downloads them on demand into cdn_assets/. Bundling them would (a) bloat the APK
        // ~68 MB and (b) make AssetDownloadManager.isAssetBundled() short-circuit the
        // on-demand download so files never cache and the sync banner never shows.
        ignoreAssetsPattern = "!audio"
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    // android.util.Log is an unimplemented stub on the JVM and throws by default,
    // which fails every unit test that reaches the prayer engine
    // (AstronomicalCalculator logs 112 times). Returning defaults lets the
    // existing prayer calculation tests actually run off-device.
    testOptions.unitTests.isReturnDefaultValues = true
    namespace = "com.starception.submission"
}

dependencies {
    implementation(projects.feature.interests)
    implementation(projects.feature.foryou)
    implementation(projects.feature.bookmarks)
    implementation(projects.feature.topic)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)

    // Sherpa-ONNX for offline TTS and speech recognition
    implementation(files("libs/sherpa-onnx-1.12.26.aar"))

    // PageCurl — real book page curl animation (oleksandrbalan/pagecurl, MIT license)
    implementation("io.github.oleksandrbalan:pagecurl:1.5.1")

    implementation(projects.core.common)
    implementation(projects.core.assetCache)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.logging)
    implementation(projects.core.prayerEngine)
    implementation(projects.core.images)
    implementation(projects.core.analytics)
    implementation(projects.sync.work)

    // Whisper TFLite module for offline speech recognition
    implementation(projects.whisper)

    // LiteRT (successor to TFLite) for salah posture detection - 16KB page aligned
    implementation(libs.litert)
    implementation(libs.litert.support)

    // Compose-native Google Filament renderer for Salah training visualization.
    implementation(libs.sceneview)
    implementation(libs.lottie.compose)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.skydoves.colorpicker.compose)
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
    implementation(libs.coil.kt.compose)
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

    // Glance for the home-screen prayer times app widget. Glance composables are a
    // separate runtime from the app's Compose UI — they render to RemoteViews, so the
    // widget cannot reuse any screen composable and lives entirely under widget/.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Room Database for Quran
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // WorldWind Kotlin (maintained successor to the archived NASA Java SDK), on
    // Maven Central. v2 API lives under earth.worldwind.* and includes the
    // atmosphere/day-night layer that the old gov.nasa.worldwind v0.8.0 lacked.
    // Some transitive deps (mil-sym, geopackage) set allowBackup=false; we override
    // that with tools:replace in the manifest rather than excluding them.
    // Pinned to 1.9.0 (built with Kotlin 2.2.20) — the newest WWK whose metadata our
    // Kotlin 2.2.21 compiler can read. 1.10.0+ require Kotlin 2.3+/2.4 (whole-project
    // toolchain bump). 1.9.0 still ships the atmosphere/day-night layer.
    implementation("earth.worldwind:worldwind:1.9.0")
    // WorldWind -> geopackage-android:6.7.4 transitively pulls mil.nga:sqlite-android:3450200,
    // whose bundled libsqliteX.so predates 16 KB page-size support (4 KB-aligned LOAD
    // segments — the ONLY non-16KB-aligned native lib in this app, per manual ELF
    // inspection). geopackage-android 6.7.5 (same geopackage-core:6.6.7, otherwise
    // identical) bumps to sqlite-android:3500400, which ships a 16KB-aligned build.
    // Force just the leaf artifact up two patch versions rather than the whole
    // geopackage-android jump, since that's the only thing actually broken.
    implementation("mil.nga:sqlite-android:3500400")

    // AndroidLiquidGlass library for glassmorphism effects
    implementation("io.github.kyant0:backdrop:1.0.0")
    implementation("io.github.kyant0:capsule:2.1.2")

    // Sherpa-ONNX provided via api() from feature/search

    // OSS Licenses for About section
    implementation(libs.google.oss.licenses)

    // Social sign-in via Firebase Authentication (Google/Microsoft/Facebook/Apple).
    // Google uses Credential Manager; the rest use Firebase's OAuthProvider web flow.
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

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
