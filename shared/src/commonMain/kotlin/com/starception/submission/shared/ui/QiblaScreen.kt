/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.qibla.HeadingProvider
import com.starception.submission.shared.qibla.HeadingReading
import com.starception.submission.shared.qibla.cardinalDirection
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.qibla.relativeQiblaTurn
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun QiblaScreen(latitude: Double, longitude: Double, onBack: () -> Unit) {
    val bearing = remember(latitude, longitude) { qiblaBearing(latitude, longitude) }
    val provider = remember { HeadingProvider() }
    var reading by remember { mutableStateOf(HeadingReading(unavailableReason = "Waiting for compass heading")) }
    DisposableEffect(provider) {
        provider.start { reading = it }
        onDispose { provider.stop() }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter)
                    .safeDrawingPadding().padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconTapTarget(
                        icon = NiaIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = onBack,
                    )
                    Text("Qibla compass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(bottom = 20.dp),
                ) {
                    CompassPanel(
                        latitude = latitude,
                        longitude = longitude,
                        bearing = bearing,
                        reading = reading,
                        modifier = Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassPanel(
    latitude: Double,
    longitude: Double,
    bearing: Double,
    reading: HeadingReading,
    modifier: Modifier = Modifier,
) {
    val heading = reading.headingDegrees
    val turn = heading?.let { relativeQiblaTurn(bearing, it) }
    val aligned = turn != null && abs(turn) <= 5.0
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (aligned) "Aligned with Qibla" else "${bearing.roundToInt()}° ${cardinalDirection(bearing)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    heading == null -> reading.unavailableReason ?: "Heading unavailable"
                    aligned -> "Keep the top of the phone pointing ahead"
                    turn!! > 0 -> "Turn right ${abs(turn).roundToInt()}°"
                    else -> "Turn left ${abs(turn).roundToInt()}°"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            PlatformWorldWindGlobe(
                latitude = latitude,
                longitude = longitude,
                headingDegrees = heading,
                headingAccuracyDegrees = reading.accuracyDegrees,
                qiblaBearing = bearing,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = "WorldWind Qibla compass from your location to Makkah"
                    },
            )
            Spacer(Modifier.height(12.dp))
            val accuracy = reading.accuracyDegrees
            Text(
                when {
                    heading == null -> "The fixed bearing remains valid; live turn guidance needs a physical compass."
                    accuracy != null -> "Heading ${heading.roundToInt()}° · accuracy ±${accuracy.roundToInt()}°"
                    else -> "Heading ${heading.roundToInt()}° · accuracy unavailable"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
