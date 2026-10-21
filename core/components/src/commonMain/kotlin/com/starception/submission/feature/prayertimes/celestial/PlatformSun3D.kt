/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.feature.prayertimes.celestial

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal enum class CelestialBody {
    Sun,
    Moon,
}

@Composable
internal expect fun PlatformCelestialBody(
    body: CelestialBody,
    lunarCyclePhase: Float = 0f,
    playing: Boolean = true,
    modifier: Modifier = Modifier,
)
