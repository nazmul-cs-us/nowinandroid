/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges immersive home content to the app-shell navigation overlay. */
object InsightImmersiveBus {
    private val mutableImmersive = MutableStateFlow(false)
    val immersive = mutableImmersive.asStateFlow()

    fun update(value: Boolean) {
        mutableImmersive.value = value
    }
}
