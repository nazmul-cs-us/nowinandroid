package com.starception.submission.feature.prayertimes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Header card showing location and date information with PNG file aesthetic
 */
@Composable
fun PrayerTimesHeaderCard(
    location: String,
    date: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
    ) {
        // Main container with PNG file aesthetic
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8F9FA),
                            Color(0xFFE9ECEF)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header section with gradient background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF4A90E2),
                                    Color(0xFF5BA4F2)
                                )
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🕌 Prayer Times",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Location and date info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📍 $location",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF495057),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "📅 $date",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF6C757D)
                    )
                }
            }
        }
        
        // Corner fold effect (like PNG file icon)
        Box(
            modifier = Modifier
                .size(30.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(topEnd = 20.dp, bottomStart = 15.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFDEE2E6),
                                Color(0xFFADB5BD)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Loading card with progress indicator
 */
@Composable
fun PrayerTimesLoadingCard(
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Calculating prayer times...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Individual prayer time card with PNG file aesthetic
 */
@Composable
fun PrayerTimeCard(
    prayerName: String,
    time: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
    ) {
        // Main card content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF8F9FA)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Prayer name with colored background
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (prayerName) {
                                "Fajr" -> Brush.horizontalGradient(listOf(Color(0xFF667EEA), Color(0xFF764BA2)))
                                "Sunrise" -> Brush.horizontalGradient(listOf(Color(0xFFFDC830), Color(0xFFF37335)))
                                "Dhuhr" -> Brush.horizontalGradient(listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)))
                                "Asr" -> Brush.horizontalGradient(listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)))
                                "Maghrib" -> Brush.horizontalGradient(listOf(Color(0xFFFA8BFF), Color(0xFF2BD2FF)))
                                "Isha" -> Brush.horizontalGradient(listOf(Color(0xFF2193B0), Color(0xFF6DD5ED)))
                                else -> Brush.horizontalGradient(listOf(Color(0xFF4A90E2), Color(0xFF5BA4F2)))
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = prayerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Prayer time
                Text(
                    text = time,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF495057),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Small corner fold effect
        Box(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomStart = 10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE9ECEF),
                                Color(0xFFCED4DA)
                            )
                        )
                    )
            )
        }
    }
}