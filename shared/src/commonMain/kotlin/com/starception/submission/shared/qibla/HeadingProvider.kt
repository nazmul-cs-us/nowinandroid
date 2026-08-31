/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.qibla

data class HeadingReading(
    val headingDegrees: Double? = null,
    val accuracyDegrees: Double? = null,
    val unavailableReason: String? = null,
)

expect class HeadingProvider() {
    fun start(onReading: (HeadingReading) -> Unit)
    fun stop()
}
