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

package com.starception.submission.shared.storage

/**
 * In memory, and deliberately so — note this does NOT persist.
 *
 * SharedPreferences needs a Context, which would mean an initialisation hook
 * this module does not otherwise require, and Android already persists prayer
 * completion through its own storage. A second store here would let the two
 * disagree about which prayers the user has marked.
 *
 * Nothing on the Android path uses this yet. When Android's prayer state moves
 * to shared code this becomes the real store and the app delegates to it, as
 * DayPrayerTimes now does to PrayerWindows.
 */
actual fun platformKeyValueStore(): KeyValueStore = InMemoryKeyValueStore()
