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

package com.starception.submission.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.qibla.HeadingProvider
import com.starception.submission.shared.qibla.HeadingReading
import com.starception.submission.shared.qibla.cardinalDirection
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.qibla.relativeQiblaTurn
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveWidget
import io.github.alexzhirkevich.cupertino.adaptive.ExperimentalAdaptiveApi
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalAdaptiveApi::class, ExperimentalCupertinoApi::class)
@Composable
internal fun QiblaScreen(latitude: Double, longitude: Double, onBack: () -> Unit) {
    val bearing = remember(latitude, longitude) { qiblaBearing(latitude, longitude) }
    val provider = remember { HeadingProvider() }
    var reading by remember { mutableStateOf(HeadingReading(unavailableReason = "Waiting for compass heading")) }
    DisposableEffect(provider) {
        provider.start { reading = it }
        onDispose { provider.stop() }
    }

    AdaptiveWidget(
        material = {
            QiblaMaterialPage(latitude, longitude, bearing, reading, onBack)
        },
        cupertino = {
            QiblaCupertinoPage(latitude, longitude, bearing, reading, onBack)
        },
    )
}

@Composable
private fun QiblaMaterialPage(
    latitude: Double,
    longitude: Double,
    bearing: Double,
    reading: HeadingReading,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
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
                Text("Qibla Compass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${bearing.roundToInt()}° ${cardinalDirection(bearing)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = qiblaGuidance(bearing, reading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))

                PlatformWorldWindGlobe(
                    latitude = latitude,
                    longitude = longitude,
                    headingDegrees = reading.headingDegrees,
                    headingAccuracyDegrees = reading.accuracyDegrees,
                    qiblaBearing = bearing,
                    expanded = false,
                    modifier = Modifier
                        .size(310.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = "WorldWind Qibla globe from your location to Makkah"
                        },
                )

                Text(
                    text = "Points toward the Kaaba in Makkah",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(Modifier.height(18.dp))
                CalibrationCard(reading)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalCupertinoApi::class)
private fun QiblaCupertinoPage(
    latitude: Double,
    longitude: Double,
    bearing: Double,
    reading: HeadingReading,
    onBack: () -> Unit,
) {
    val colors = CupertinoTheme.colorScheme
    CupertinoSurface(
        color = colors.systemGroupedBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KaabaTitleIcon()
                    Spacer(Modifier.width(10.dp))
                    CupertinoText(
                        text = "Qibla Compass",
                        style = CupertinoTheme.typography.title1,
                        fontWeight = FontWeight.Bold,
                    )
                }
                CupertinoSurface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = colors.quaternarySystemFill,
                    modifier = Modifier.align(Alignment.CenterEnd).size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CupertinoText(
                            text = "×",
                            style = CupertinoTheme.typography.title1,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlatformWorldWindGlobe(
                    latitude = latitude,
                    longitude = longitude,
                    headingDegrees = reading.headingDegrees,
                    headingAccuracyDegrees = reading.accuracyDegrees,
                    qiblaBearing = bearing,
                    expanded = false,
                    modifier = Modifier
                        .size(272.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = "WorldWind Qibla globe from your location to Makkah"
                        },
                )
                Spacer(Modifier.height(12.dp))
                CupertinoText(
                    text = "${bearing.roundToInt()}° ${cardinalDirection(bearing)}",
                    style = CupertinoTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                )
                CupertinoText(
                    text = qiblaGuidance(bearing, reading),
                    style = CupertinoTheme.typography.subhead,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(17.dp))
                CupertinoCalibrationCard(reading)
            }
        }
    }
}

@Composable
private fun KaabaTitleIcon() {
    Canvas(Modifier.size(40.dp)) {
        fun polygon(vararg points: Pair<Float, Float>) = Path().apply {
            moveTo(points.first().first * size.width, points.first().second * size.height)
            points.drop(1).forEach { (x, y) -> lineTo(x * size.width, y * size.height) }
            close()
        }

        drawPath(
            polygon(0.12f to 0.25f, 0.48f to 0.08f, 0.90f to 0.22f, 0.53f to 0.40f),
            Color(0xFF4B4A47),
        )
        drawPath(
            polygon(0.12f to 0.25f, 0.53f to 0.40f, 0.53f to 0.92f, 0.12f to 0.74f),
            Color(0xFF292929),
        )
        drawPath(
            polygon(0.53f to 0.40f, 0.90f to 0.22f, 0.90f to 0.74f, 0.53f to 0.92f),
            Color(0xFF111111),
        )
        drawPath(
            polygon(0.12f to 0.43f, 0.53f to 0.59f, 0.53f to 0.68f, 0.12f to 0.53f),
            Color(0xFFD4AF37),
        )
        drawPath(
            polygon(0.53f to 0.59f, 0.90f to 0.43f, 0.90f to 0.52f, 0.53f to 0.68f),
            Color(0xFFF0CB52),
        )
    }
}

@Composable
@OptIn(ExperimentalCupertinoApi::class)
private fun CupertinoCalibrationCard(reading: HeadingReading) {
    val colors = CupertinoTheme.colorScheme
    CupertinoSurface(
        color = colors.secondarySystemGroupedBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CupertinoText(
                text = "∞",
                color = Color(0xFF52BF8B).copy(alpha = 0.5f),
                fontSize = 72.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Light,
            )
            CupertinoText("For Better Accuracy", style = CupertinoTheme.typography.headline)
            CupertinoText(
                text = "Move your device in a figure-8 pattern to improve compass accuracy. Keep the phone flat and make smooth, continuous motions.",
                style = CupertinoTheme.typography.subhead,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            CupertinoCalibrationStep("1", "Hold phone flat in your palm")
            CupertinoCalibrationStep("2", "Move in smooth figure-8 motions")
            CupertinoCalibrationStep("3", "Watch the compass border turn green")
            Spacer(Modifier.height(10.dp))
            CupertinoText(
                text = "Color Guide:",
                style = CupertinoTheme.typography.subhead,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            CupertinoAccuracyLegend(Color(0xFF52BF8B), "Green = Good accuracy, reliable direction")
            CupertinoAccuracyLegend(Color(0xFFFFA726), "Orange = Fair accuracy, mostly reliable")
            CupertinoAccuracyLegend(Color(0xFFFF5252), "Red = Poor accuracy, recalibration needed")
            val accuracy = reading.accuracyDegrees
            CupertinoText(
                text = when {
                    reading.headingDegrees == null -> reading.unavailableReason ?: "Waiting for compass"
                    accuracy != null -> "Heading accuracy ±${accuracy.roundToInt()}°"
                    else -> "Compass updates automatically"
                },
                style = CupertinoTheme.typography.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun CupertinoCalibrationStep(number: String, instruction: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF52BF8B)),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoText(number, color = Color.White, fontWeight = FontWeight.Bold)
        }
        CupertinoText(
            instruction,
            style = CupertinoTheme.typography.subhead,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun CupertinoAccuracyLegend(color: Color, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        CupertinoText(label, style = CupertinoTheme.typography.footnote, color = CupertinoTheme.colorScheme.secondaryLabel)
    }
}

@Composable
private fun CalibrationCard(reading: HeadingReading) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "For Better Accuracy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Move your device in a figure-8 pattern to improve compass accuracy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            CalibrationStep("1", "Hold phone flat in your palm")
            CalibrationStep("2", "Move in smooth figure-8 motions")
            CalibrationStep("3", "Watch the compass ring turn green")
            Spacer(Modifier.height(10.dp))
            AccuracyLegend(Color(0xFF10B981), "Green = Good accuracy")
            AccuracyLegend(Color(0xFFFFA500), "Orange = Fair accuracy")
            AccuracyLegend(Color(0xFFFF4444), "Red = Poor accuracy")

            val accuracy = reading.accuracyDegrees
            Text(
                text = when {
                    reading.headingDegrees == null -> reading.unavailableReason ?: "Waiting for compass"
                    accuracy != null -> "Heading accuracy ±${accuracy.roundToInt()}°"
                    else -> "Compass updates automatically"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun CalibrationStep(number: String, instruction: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun AccuracyLegend(color: Color, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun qiblaGuidance(bearing: Double, reading: HeadingReading): String {
    val heading = reading.headingDegrees ?: return reading.unavailableReason ?: "Waiting for compass heading"
    val turn = relativeQiblaTurn(bearing, heading)
    return when {
        abs(turn) <= 5.0 -> "Aligned with Qibla"
        turn > 0 -> "Turn right ${abs(turn).roundToInt()}°"
        else -> "Turn left ${abs(turn).roundToInt()}°"
    }
}
