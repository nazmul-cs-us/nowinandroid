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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.PrayerSkyPhase
import com.starception.submission.core.images.PrayerSkyWeather
import com.starception.submission.core.images.prayerSkyResource
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.prayer_foreground_nabawi
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
 * Platform-specific parallax remains an enhancement rather than a visual gap.
 */
@Composable
fun PrayerNowTile(
    phase: PrayerSkyPhase,
    weather: PrayerSkyWeather,
    headline: String,
    subtitle: String,
    nextPrayer: String,
    countdown: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(26.dp)),
    ) {
        Image(
            painter = painterResource(prayerSkyResource(phase, weather)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(Res.drawable.prayer_ground_nabawi),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(Res.drawable.prayer_foreground_nabawi),
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

        Box(
            modifier = Modifier
                .padding(10.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Prayer now",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
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
