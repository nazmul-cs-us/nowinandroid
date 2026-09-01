package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform WorldWind surface used by the shared Qibla screen. */
@Composable
internal expect fun PlatformWorldWindGlobe(
    latitude: Double,
    longitude: Double,
    headingDegrees: Double?,
    headingAccuracyDegrees: Double?,
    qiblaBearing: Double,
    modifier: Modifier = Modifier,
)
