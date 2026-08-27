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

package com.starception.submission.shared.location

/**
 * Deliberately not implemented.
 *
 * Android already has a location stack — `EnhancedLocationService` with its
 * caching, country detection, permission handling and timeout fallbacks — and
 * writing a second one here to satisfy the `expect` would be a competing source
 * of truth for where the user is. The failure mode is the two disagreeing and
 * the app computing prayer times for one place while displaying another.
 *
 * `LocationProvider` exists for iOS, which had nothing. When the Android
 * location work moves into shared code, the real implementation replaces this
 * and the app's service delegates to it, the same way `DayPrayerTimes` now
 * delegates to `PrayerWindows`.
 */
actual class LocationProvider actual constructor() {
    actual suspend fun current(): DeviceLocation? = null
}
