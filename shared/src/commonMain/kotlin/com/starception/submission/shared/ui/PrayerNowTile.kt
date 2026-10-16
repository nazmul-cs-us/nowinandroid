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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.PrayerSkyPhase
import com.starception.submission.core.images.PrayerSkyWeather
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.prayer_now_insight_day
import com.starception.submission.core.images.resources.prayer_now_insight_night
import com.starception.submission.feature.prayertimes.celestial.PrayerSkyCelestialLayer
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import org.jetbrains.compose.resources.painterResource

/**
 * The "Prayer now" hero tile: sky artwork with the current prayer over it.
 *
 * Uses the same artwork and the same phase/weather selection as the Android home
 * page, from :core:images, so both platforms show the same sky for the same
 * moment and forecast.
 *
 * The complete day/night scenes are the same high-resolution assets used by Android's
 * Prayer Now card. Text remains native Compose so it stays sharp and accessible on iOS.
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
    epochSeconds: Double,
    latitude: Double,
    longitude: Double,
    tileHeight: androidx.compose.ui.unit.Dp = 220.dp,
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
    backgroundPageOffset: () -> Float = { 0f },
) {
    val isNight = nowMinute < sunriseMinute || nowMinute >= maghribMinute ||
        (nowMinute == 0 && (phase == PrayerSkyPhase.Fajr || phase == PrayerSkyPhase.Isha))
    val weatherScrimBoost = when (weather) {
        PrayerSkyWeather.Clear, PrayerSkyWeather.PartlyCloudy -> 0f
        PrayerSkyWeather.Overcast, PrayerSkyWeather.Fog -> 0.04f
        PrayerSkyWeather.Rain, PrayerSkyWeather.Snow -> 0.07f
        PrayerSkyWeather.Thunderstorm -> 0.11f
    }
    val sceneAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = 0.40f)
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 32.dp else 26.dp,
        animationSpec = tween(360),
        label = "prayerNowCornerRadius",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isExpanded) 10.dp else 6.dp,
        animationSpec = tween(360),
        label = "prayerNowShadowElevation",
    )
    val shape = RoundedCornerShape(cornerRadius)
    CupertinoSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(tileHeight),
        shape = shape,
        color = Color(0xFF635A56),
        shadowElevation = shadowElevation,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                ),
        ) {
        // The complete generated scene counter-scrolls as one layer, matching
        // Android's Prayer Now depth so the sky does not feel static mid-swipe.
        val sceneBaseScale = 1.14f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pageOffset = backgroundPageOffset().coerceIn(-1f, 1f)
                    val maxCoveredHorizontalShift = (
                        (size.width * (sceneBaseScale - 1f) / 2f) - 1.dp.toPx()
                        ).coerceAtLeast(0f)
                    scaleX = sceneBaseScale
                    scaleY = sceneBaseScale
                    translationX = (-pageOffset * 18.dp.toPx()).coerceIn(
                        -maxCoveredHorizontalShift,
                        maxCoveredHorizontalShift,
                    )
                },
        ) {
        Image(
            painter = painterResource(
                if (isNight) Res.drawable.prayer_now_insight_night
                else Res.drawable.prayer_now_insight_day,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = sceneAlignment,
            modifier = Modifier.fillMaxSize(),
        )
        PrayerSkyCelestialLayer(
            epochSeconds = epochSeconds,
            latitude = latitude,
            longitude = longitude,
            isNight = isNight,
        )
        }

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
            modifier = if (isExpanded) {
                Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, top = 62.dp, end = 18.dp, bottom = 12.dp)
            } else {
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 16.dp)
            },
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            expandedContent?.let { content ->
                Box(
                    modifier = if (isExpanded) {
                        Modifier
                            .fillMaxWidth()
                            // Match every ArtworkTile expanded stage: reclaim
                            // 10dp from each metadata inset without moving the
                            // footer alignment.
                            .layout { measurable, constraints ->
                                val bleed = 10.dp.roundToPx()
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = 0,
                                        maxWidth = (constraints.maxWidth + bleed * 2)
                                            .coerceAtLeast(constraints.minWidth),
                                    ),
                                )
                                layout(constraints.maxWidth, placeable.height) {
                                    placeable.placeRelative(-bleed, 0)
                                }
                            }
                            .weight(1f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color.Black,
                                        endY = 28.dp.toPx(),
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                    } else {
                        Modifier.fillMaxWidth()
                    },
                ) {
                    content()
                }
            }
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
