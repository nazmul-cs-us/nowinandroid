package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starception.submission.prayer.service.EnhancedLocationService

/**
 * Full-screen compass popup with calibration guidance
 * 
 * Shows the compass in large size with Islamic theming and sensor calibration instructions.
 * Automatically appears when sensor accuracy is poor or when user taps the compass tile.
 * 
 * ## Features:
 * - **Large Compass Display**: 280dp compass for better visibility and interaction
 * - **Calibration Guidance**: Step-by-step instructions for improving sensor accuracy
 * - **Islamic Theming**: Traditional green colors with Kaaba emoji
 * - **Real-time Feedback**: Visual indicators showing sensor status improvements
 * - **Prayer Integration**: Shows current prayer time countdown within the compass
 * 
 * @param progress Current prayer time progress (0-1)
 * @param timeText Time remaining until next prayer
 * @param locationService Enhanced location service for GPS and Qibla calculations
 * @param onDismiss Callback when user closes the popup
 */
@Composable
fun CompassPopupScreen(
    progress: Float,
    timeText: String,
    locationService: EnhancedLocationService?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
        ) {
            // Close button - positioned to avoid status bar overlap
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp) // Extra top padding to clear status bar
                    .background(
                        Color.White.copy(alpha = 0.2f),
                        RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Title
                Text(
                    text = "🕋 Qibla Compass",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Large compass
                CompassProgressIndicator(
                    progress = progress,
                    timeText = timeText,
                    size = 280.dp,
                    locationService = locationService,
                    modifier = Modifier.padding(bottom = 40.dp)
                )

                // Calibration guidance card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Calibration icon with animation
                        val infiniteTransition = rememberInfiniteTransition(label = "figure8")
                        val animatedAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        Text(
                            text = "∞",
                            fontSize = 48.sp,
                            color = Color(0xFF10B981).copy(alpha = animatedAlpha),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "For Better Accuracy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            text = "Move your device in a figure-8 pattern to improve compass accuracy. Keep the phone flat and make smooth, continuous motions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // Steps
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CalibrationStep(
                                step = "1",
                                instruction = "Hold phone flat in your palm"
                            )
                            CalibrationStep(
                                step = "2", 
                                instruction = "Move in smooth figure-8 motions"
                            )
                            CalibrationStep(
                                step = "3",
                                instruction = "Watch the compass border turn green"
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Color meaning explanation
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Color Guide:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Green - Good accuracy
                            ColorIndicatorRow(
                                color = Color(0xFF10B981),
                                text = "Green = Good accuracy, reliable direction"
                            )
                            
                            // Orange - Medium accuracy
                            ColorIndicatorRow(
                                color = Color(0xFFFFA500),
                                text = "Orange = Fair accuracy, mostly reliable"
                            )
                            
                            // Red - Poor accuracy
                            ColorIndicatorRow(
                                color = Color(0xFFFF4444),
                                text = "Red = Poor accuracy, needs calibration"
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Status indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Compass will update automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Islamic context note
                Text(
                    text = "The green arc points toward the Kaaba in Mecca, Saudi Arabia",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CalibrationStep(
    step: String,
    instruction: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun ColorIndicatorRow(
    color: Color,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator circle
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
    }
}