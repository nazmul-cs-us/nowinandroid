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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.PrayerSkyPhase
import com.starception.submission.core.images.PrayerSkyWeather
import com.starception.submission.core.images.prayerSkyResource
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_salah_foreground
import com.starception.submission.core.images.resources.prayer_foreground_kaaba
import com.starception.submission.core.images.resources.prayer_foreground_nabawi
import com.starception.submission.core.images.resources.prayer_ground_kaaba
import com.starception.submission.core.images.resources.prayer_ground_local
import com.starception.submission.core.images.resources.prayer_ground_nabawi
import org.jetbrains.compose.resources.painterResource

/**
 * The "Prayer now" hero tile: sky artwork with the current prayer over it.
 *
 * Uses the same artwork and the same phase/weather selection as the Android home
 * page, from :core:images, so both platforms show the same sky for the same
 * moment and forecast.
 *
 * The portable ground and foreground layers mirror the Android composition.
 * The scene rotates on long press, matching Android's local, Kaaba and Nabawi
 * variants without making the iOS host own any UI state.
 */
@Composable
fun PrayerNowTile(
    phase: PrayerSkyPhase,
    weather: PrayerSkyWeather,
    headline: String,
    subtitle: String,
    nextPrayer: String,
    countdown: String,
    sceneIndex: Int = 0,
    timelineProgress: Float? = null,
    tileHeight: androidx.compose.ui.unit.Dp = 220.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val scene = sceneIndex.mod(3)
    val foreground = when (scene) {
        0 -> Res.drawable.insight_salah_foreground
        1 -> Res.drawable.prayer_foreground_kaaba
        else -> Res.drawable.prayer_foreground_nabawi
    }
    val ground = when (scene) {
        0 -> Res.drawable.prayer_ground_local
        1 -> Res.drawable.prayer_ground_kaaba
        else -> Res.drawable.prayer_ground_nabawi
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tileHeight)
            .clip(RoundedCornerShape(26.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Image(
            painter = painterResource(prayerSkyResource(phase, weather)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(ground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (scene == 1) 0.94f else if (scene == 2) 0.92f else 1f
                    scaleY = scaleX
                },
        )
        Image(
            painter = painterResource(foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize(),
        )

        // The artwork is bright at the horizon, so the text needs its own
        // contrast rather than relying on the image being dark enough.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel("Prayer now")
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.38f),
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Compass", style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = null,
                        modifier = Modifier.height(16.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            timelineProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    trackColor = Color.White.copy(alpha = 0.22f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Next Prayer",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Text(
                        text = nextPrayer,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun TileLabel(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
