/*
 * Copyright 2026 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 */
package com.starception.submission.feature.prayertimes.celestial

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformCelestialBody(
    body: CelestialBody,
    lunarCyclePhase: Float,
    playing: Boolean,
    modifier: Modifier,
) {
    val provider = celestialNativeViewProvider
    var renderTime by remember(body) { mutableFloatStateOf(0f) }
    LaunchedEffect(body, playing) {
        while (playing) {
            delay(125)
            renderTime += 0.125f
        }
    }
    val image = remember(provider, body, lunarCyclePhase, renderTime) {
        provider?.renderCelestialPng(
            body = when (body) {
                CelestialBody.Sun -> 1
                CelestialBody.Moon -> 2
            },
            lunarCyclePhase = lunarCyclePhase,
            pixelSize = 160,
            timeSeconds = renderTime,
        )?.toComposeBitmap()
    }
    if (image == null) {
        Box(modifier)
    } else {
        Image(bitmap = image, contentDescription = null, modifier = modifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toComposeBitmap() = runCatching {
    val bytes = ByteArray(length.toInt())
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
