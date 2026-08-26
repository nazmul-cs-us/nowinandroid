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

import platform.Foundation.NSLog

/**
 * `NSLog` is the closest analogue to logcat: it reaches both the Xcode console
 * and the device log, and needs no setup. If the volume becomes a problem once
 * the prayer engine moves over (it is chatty — 112 calls in the calculator
 * alone), this is the single place to swap in `os_log` with proper subsystems.
 */
actual object SharedLog {
    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val suffix = throwable?.let { "\n$it\n${it.stackTraceToString()}" } ?: ""
        NSLog("%s", "$level/$tag: $message$suffix")
    }

    actual fun v(tag: String, message: String) = log("V", tag, message)

    actual fun d(tag: String, message: String) = log("D", tag, message)

    actual fun i(tag: String, message: String) = log("I", tag, message)

    actual fun w(tag: String, message: String, throwable: Throwable?) =
        log("W", tag, message, throwable)

    actual fun e(tag: String, message: String, throwable: Throwable?) =
        log("E", tag, message, throwable)
}
