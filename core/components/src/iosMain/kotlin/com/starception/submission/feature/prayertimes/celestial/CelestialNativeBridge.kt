/*
 * Copyright 2026 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 */
package com.starception.submission.feature.prayertimes.celestial

import platform.Foundation.NSData

/** Swift implements this bridge with the Metal renderer from celestial-native. */
public interface CelestialNativeViewProviding {
    public fun renderCelestialPng(
        body: Int,
        lunarCyclePhase: Float,
        pixelSize: Int,
        timeSeconds: Float,
    ): NSData?
}

internal var celestialNativeViewProvider: CelestialNativeViewProviding? = null

@Suppress("FunctionName")
public fun RegisterCelestialNativeViewProvider(provider: CelestialNativeViewProviding) {
    celestialNativeViewProvider = provider
}
