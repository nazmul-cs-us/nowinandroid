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

package com.starception.submission.shared

import com.starception.submission.core.logging.SharedLog

/**
 * Entry point of the umbrella framework that `iosApp/` links against.
 *
 * This module is a composition boundary, not a destination for code: it
 * re-exports the modules the iOS host needs and will host the Compose root. Put
 * features in their own multiplatform modules and export them from here.
 *
 * It also has to contain *something*. A module with no sources makes the link
 * task report `NO-SOURCE` and produce no framework at all, while the build still
 * reports success — a green build that ships nothing.
 */
object SharedFramework {

    /** Name the framework is linked under; matches `baseName` in the build file. */
    const val NAME: String = "Shared"

    /**
     * Smoke check for `iosApp/` to call on launch during bring-up.
     *
     * Proves the framework is linked and that shared code actually executes on
     * device, rather than merely that the project compiled.
     */
    fun greeting(): String = "$NAME framework linked"

    /** Writes a line through the shared logger, visible in the Xcode console. */
    fun logSmokeTest() {
        SharedLog.i(NAME, greeting())
    }
}
