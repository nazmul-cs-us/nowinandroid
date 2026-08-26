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

package com.starception.submission.core.logging

import android.util.Log

/**
 * Delegates straight to logcat, so the existing `adb logcat -s "..."` debugging
 * workflows documented in CLAUDE.md keep working unchanged after the move.
 */
actual object SharedLog {
    actual fun v(tag: String, message: String) {
        Log.v(tag, message)
    }

    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
