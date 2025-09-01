/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import com.google.samples.apps.nowinandroid.configureBadgingTasks
import com.google.samples.apps.nowinandroid.configureGradleManagedDevices
import com.google.samples.apps.nowinandroid.configureKotlinAndroid
import com.google.samples.apps.nowinandroid.configurePrintApksTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * ANDROID APPLICATION CONVENTION PLUGIN: Standard configuration for Android app modules
 * 
 * This Gradle convention plugin provides consistent configuration across all Android application
 * modules in the project. It centralizes common build settings, plugin applications, and
 * configurations to ensure consistency and reduce duplication.
 * 
 * APPLIED PLUGINS:
 * - com.android.application: Core Android application plugin
 * - org.jetbrains.kotlin.android: Kotlin Android support
 * - nowinandroid.android.lint: Custom lint configuration
 * - com.dropbox.dependency-guard: Dependency management and security
 * 
 * CONFIGURED FEATURES:
 * - Kotlin Android compilation settings
 * - Target SDK configuration (currently 36)
 * - Test options and animation settings
 * - Gradle managed devices for testing
 * - APK printing and badging tasks
 * 
 * USAGE:
 * Apply this plugin to any Android application module:
 * ```kotlin
 * plugins {
 *     id("nowinandroid.android.application")
 * }
 * ```
 * 
 * BENEFITS:
 * - Consistent configuration across app modules
 * - Centralized maintenance of common settings
 * - Reduced build script duplication
 * - Standardized testing and deployment setup
 * 
 * CUSTOMIZATION:
 * Edit this file to change default settings for all application modules.
 * Individual modules can override specific settings as needed.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    /**
     * PLUGIN APPLICATION: Applies standard Android application configuration
     * 
     * This method configures the target project with all necessary plugins and settings
     * for consistent Android application development.
     * 
     * CONFIGURATION STEPS:
     * 1. Apply required plugins (Android, Kotlin, Lint, Dependency Guard)
     * 2. Configure ApplicationExtension with standard settings
     * 3. Configure AndroidComponentsExtension with build tasks
     * 
     * @param target The Gradle project to configure
     */
    override fun apply(target: Project) {
        with(target) {
            // PLUGIN APPLICATION: Apply core plugins for Android application development
            apply(plugin = "com.android.application")     // Core Android application plugin
            apply(plugin = "org.jetbrains.kotlin.android") // Kotlin support for Android
            apply(plugin = "nowinandroid.android.lint")    // Custom lint rules and configuration
            apply(plugin = "com.dropbox.dependency-guard") // Dependency security and management

            // APPLICATION EXTENSION CONFIGURATION: Standard Android app settings
            extensions.configure<ApplicationExtension> {
                // Configure Kotlin compilation, source sets, and Android-specific Kotlin settings
                configureKotlinAndroid(this)
                
                // Set target SDK to latest stable version (Android 14)
                // Update this when newer Android versions are stable
                defaultConfig.targetSdk = 36
                
                // TESTING CONFIGURATION: Improve test reliability and speed
                @Suppress("UnstableApiUsage")
                testOptions.animationsDisabled = true  // Disable animations for faster, more reliable tests
                
                // Configure automated test devices for CI/CD
                configureGradleManagedDevices(this)
            }
            
            // ANDROID COMPONENTS EXTENSION: Configure build tasks and variant processing
            extensions.configure<ApplicationAndroidComponentsExtension> {
                // Configure task to print APK information after builds
                configurePrintApksTask(this)
                
                // Configure badging tasks for APK metadata and Play Store compatibility
                configureBadgingTasks(extensions.getByType<BaseExtension>(), this)
            }
        }
    }
}
