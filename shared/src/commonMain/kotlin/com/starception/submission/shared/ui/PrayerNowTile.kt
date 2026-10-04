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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.PrayerSkyPhase
import com.starception.submission.core.images.PrayerSkyWeather
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_prayer_poster_day
import com.starception.submission.core.images.resources.insight_prayer_poster_moon
import com.starception.submission.core.images.resources.insight_prayer_poster_night
import com.starception.submission.core.images.resources.insight_prayer_poster_night_lights
import com.starception.submission.core.images.resources.insight_prayer_poster_sun
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

/**
 * The "Prayer now" hero tile: sky artwork with the current prayer over it.
 *
 * Uses the same artwork and the same phase/weather selection as the Android home
 * page, from :core:images, so both platforms show the same sky for the same
 * moment and forecast.
 *
 * The poster, mosque-light layer and textured sun/moon are the same high-resolution
 * assets used by Android's Prayer Now card. Text remains native Compose so it stays
 * sharp and accessible on iOS at every card size.
 */
@Composable
fun PrayerNowTile(
    phase: PrayerSkyPhase,
    weather: PrayerSkyWeather,
    headline: String,
    subtitle: String,
    nextPrayer: String,
    forecast: String?,
    timelineProgress: Float? = null,
    nowMinute: Int = 0,
    sunriseMinute: Int = 390,
    maghribMinute: Int = 1_080,
    tileHeight: androidx.compose.ui.unit.Dp = 220.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val isNight = nowMinute < sunriseMinute || nowMinute >= maghribMinute ||
        (nowMinute == 0 && (phase == PrayerSkyPhase.Fajr || phase == PrayerSkyPhase.Isha))
    val weatherScrimBoost = when (weather) {
        PrayerSkyWeather.Clear, PrayerSkyWeather.PartlyCloudy -> 0f
        PrayerSkyWeather.Overcast, PrayerSkyWeather.Fog -> 0.04f
        PrayerSkyWeather.Rain, PrayerSkyWeather.Snow -> 0.07f
        PrayerSkyWeather.Thunderstorm -> 0.11f
    }
    val cycleProgress = if (isNight) {
        val nightEnd = sunriseMinute + 24 * 60
        val current = if (nowMinute < sunriseMinute) nowMinute + 24 * 60 else nowMinute
        ((current - maghribMinute).toFloat() / (nightEnd - maghribMinute).coerceAtLeast(1))
            .coerceIn(0f, 1f)
    } else {
        ((nowMinute - sunriseMinute).toFloat() / (maghribMinute - sunriseMinute).coerceAtLeast(1))
            .coerceIn(0f, 1f)
    }
    val sceneAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = 0.40f)
    val shape = RoundedCornerShape(26.dp)
    CupertinoSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(tileHeight),
        shape = shape,
        color = Color(0xFF635A56),
        shadowElevation = 6.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .clickable(onClick = onClick),
        ) {
        Image(
            painter = painterResource(
                if (isNight) Res.drawable.insight_prayer_poster_night
                else Res.drawable.insight_prayer_poster_day,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = sceneAlignment,
            modifier = Modifier.fillMaxSize(),
        )
        if (isNight) {
            Image(
                painter = painterResource(Res.drawable.insight_prayer_poster_night_lights),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = sceneAlignment,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Match Android's full east-to-west cycle, but anchor the zenith to the
        // mountain ridge so a taller iOS card cannot lift the body out of the scene.
        val altitude = sin(cycleProgress * PI).toFloat().coerceAtLeast(0f)
        val celestialX = maxWidth * (0.17f + 0.66f * cycleProgress)
        val horizonY = maxHeight * 0.60f
        val heightZenithY = maxHeight * if (isNight) 0.17f else 0.15f
        val ridgeZenithY = horizonY - maxWidth * if (isNight) 0.26f else 0.28f
        val zenithY = maxOf(heightZenithY, ridgeZenithY)
        val celestialY = horizonY - (horizonY - zenithY) * altitude
        val bodySize = minOf(maxWidth, maxHeight) * if (isNight) 0.119f else 0.104f
        val glowSize = bodySize * if (isNight) 2.35f else 3f
        Box(
            modifier = Modifier
                .offset(x = celestialX - glowSize / 2, y = celestialY - glowSize / 2)
                .size(glowSize)
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isNight) {
                            listOf(
                                Color(0x9ED8E7FF),
                                Color(0x46D8E7FF),
                                Color.Transparent,
                            )
                        } else {
                            listOf(
                                Color(0xC8FFB72E),
                                Color(0x58FFB72E),
                                Color.Transparent,
                            )
                        },
                    ),
                    shape = CircleShape,
                ),
        )
        Image(
            painter = painterResource(
                if (isNight) Res.drawable.insight_prayer_poster_moon
                else Res.drawable.insight_prayer_poster_sun,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset(x = celestialX - bodySize / 2, y = celestialY - bodySize / 2)
                .size(bodySize)
                .clip(CircleShape),
        )

        // The artwork is bright at the horizon, so the text needs its own
        // contrast rather than relying on the image being dark enough.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(
                                alpha = (if (isNight) 0.12f else 0.03f) + weatherScrimBoost,
                            ),
                            Color.Black.copy(
                                alpha = (if (isNight) 0.68f else 0.60f) + weatherScrimBoost,
                            ),
                        ),
                    ),
                ),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                TileLabel("Prayer now")
            }
            CupertinoSurface(
                onClick = onClick,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.38f),
                contentColor = Color.White,
                modifier = Modifier.height(40.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 11.dp, end = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Compass",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Open prayer compass",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = headline,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            timelineProgress?.let { progress ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Next Prayer",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            letterSpacing = 0.7.sp,
                        ),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = nextPrayer,
                        fontSize = 13.25.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                forecast?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Forecast",
                            fontSize = 9.5.sp,
                            lineHeight = 12.sp,
                            letterSpacing = 0.3.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = it,
                            fontSize = 12.5.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TileLabel(text: String) {
    CupertinoSurface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.38f),
        contentColor = Color.White,
        modifier = Modifier
            .height(40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.sp,
                ),
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}
