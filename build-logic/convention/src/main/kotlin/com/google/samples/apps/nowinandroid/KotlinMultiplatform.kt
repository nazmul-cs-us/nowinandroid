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

package com.google.samples.apps.nowinandroid

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configure base Kotlin with multiplatform options.
 *
 * This deliberately mirrors [configureKotlinAndroid] rather than reusing it.
 * `configureKotlin` is generic over [org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension]
 * but sets `jvmTarget`, which only exists on the JVM-flavoured compiler options;
 * a multiplatform module's top-level `compilerOptions` are the *common* ones and
 * have no such property. The values themselves come from the shared constants in
 * KotlinAndroid.kt, so the two paths cannot drift.
 */
internal fun Project.configureKotlinMultiplatform(
    extension: KotlinMultiplatformExtension,
) {
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors")
        .map { it.toBoolean() }
        .orElse(false)

    with(extension) {
        androidTarget {
            compilerOptions {
                jvmTarget = JvmTarget.JVM_17
            }
        }

        // Declaring Apple targets is free without Xcode: only their native
        // compile and link tasks require the iOS SDK, and no Android task
        // depends on them. iosSimulatorArm64 is also what runs on Apple Silicon
        // Macs under "Designed for iPad".
        iosX64()
        iosArm64()
        iosSimulatorArm64()

        compilerOptions {
            allWarningsAsErrors = warningsAsErrors
            freeCompilerArgs.addAll(NIA_FREE_COMPILER_ARGS)
            // `expect`/`actual` on classes and objects is still Beta and warns on
            // every declaration, which would fail a warningsAsErrors build.
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    // Every consumer of these modules is an Android library or the app itself,
    // so they publish an AAR. `namespace` stays in each module's own build file.
    extensions.configure<LibraryExtension> {
        compileSdk = NIA_COMPILE_SDK

        defaultConfig {
            minSdk = NIA_MIN_SDK
        }

        compileOptions {
            sourceCompatibility = NIA_JAVA_VERSION
            targetCompatibility = NIA_JAVA_VERSION
        }
    }
}
