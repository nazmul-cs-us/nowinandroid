/**
 * SWIPEABLE BIG TILES COMPONENT
 * 
 * This file contains the main swipeable tiles component for the Prayer Times screen.
 * It provides an interactive horizontal pager with three distinct tiles showing different
 * prayer-related information with Material 3 design and infinite scrolling.
 * 
 * WHAT IT DOES:
 * - Creates a horizontal swipeable pager with 3 tiles (infinite scroll enabled)
 * - Shows Next Prayer, Smart Info, and Daily Stats tiles
 * - Displays page indicators and swipe hints for better UX
 * - Uses asymmetrical Material 3 shapes for visual appeal
 * - Provides real-time prayer status and progress tracking
 * 
 * WHERE IT'S USED:
 * - PrayerTimesScreen.kt: Main prayer times screen (line ~481-502)
 * - Replaces ~308 lines of inline swipeable tiles code
 * - Called through SwipeableBigTiles() composable function
 * 
 * COMPONENTS INCLUDED:
 * - SwipeableBigTiles(): Main composable function (exported)
 * - NextPrayerTile(): Shows current/next prayer with countdown timer
 * - SmartInfoTile(): Context-aware content based on time of day
 * - DailyStatsTile(): Prayer completion progress and statistics
 * 
 * FEATURES:
 * - HorizontalPager with infinite scrolling (Int.MAX_VALUE pages)
 * - Material 3 asymmetrical shapes and elevated surfaces
 * - Real-time countdown timers with circular progress indicators
 * - Dynamic content that changes based on current time and prayer status
 * - Professional page indicators and swipe hints
 * - Responsive layout with proper spacing (12dp between elements)
 * 
 * DEPENDENCIES:
 * - PrayerTimeHelpers.kt: For prayer time calculations and formatting
 * - SmartContentUtils.kt: For smart content generation and progress tracking
 * - DayPrayerTimes model: Prayer times data structure
 * 
 * DESIGN PATTERNS:
 * - Component extraction: Moved from inline code to reusable component
 * - Function parameters: Accepts lambda functions for data access
 * - Material 3 design: Uses elevated cards with custom corner radius
 * - Infinite scrolling: Modulo arithmetic for seamless tile cycling
 */
package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.starception.submission.R
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.BatchPrediction
import androidx.compose.material.icons.filled.BubbleChart
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.feature.prayertimes.components.CompassProgressIndicator
import com.starception.submission.islamic.qibla.presentation.component.QiblaGlobeView
import com.starception.submission.prayer.service.EnhancedLocationService
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sqrt
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.outlined.Fullscreen
import com.starception.submission.feature.prayertimes.components.GlobePopupScreen
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.feature.quran.QuranPlayerViewModel
import com.starception.submission.download.AudioDownloadHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.starception.submission.core.designsystem.theme.QuranFonts
import com.starception.submission.core.qurandatabase.QuranRepository
import com.starception.submission.core.qurandatabase.AyahNoteEntity




/**
 * Hilt entry point to access AudioDownloadHelper from non-Hilt composables.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioDownloadHelperEntryPoint {
    fun audioDownloadHelper(): AudioDownloadHelper
}

/**
 * Request activity detection permissions
 */
private fun requestActivityPermissions(context: Context) {
    try {
        if (context is ComponentActivity) {
            val permissions = mutableListOf<String>()
            
            // Check location permissions
            val hasLocationFine = androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasLocationCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasLocationFine) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (!hasLocationCoarse) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            
            // Check activity recognition permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasActivityRecognition = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!hasActivityRecognition) {
                    permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
            
            // Request missing permissions
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    context,
                    permissions.toTypedArray(),
                    1001 // REQUEST_CODE for activity permissions
                )
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("SwipeableBigTiles", "Error requesting permissions", e)
    }
}

/**
 * Get Arabic calendar information for the current date
 */
private fun getArabicCalendarInfo(): String {
    val today = LocalDate.now()
    
    // Convert Gregorian to Hijri using Umm al-Qura algorithm
    val hijriDate = convertToHijri(today)
    
    // Get Islamic month names
    val islamicMonths = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الثانية",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    
    val day = hijriDate.first
    val month = islamicMonths[hijriDate.second - 1]
    val year = hijriDate.third
    
    return "$day $month, $year AH"
}

/**
 * Convert Gregorian date to Hijri date using Umm al-Qura algorithm
 * Returns Triple(day, month, year) in Hijri calendar
 */
private fun convertToHijri(gregorianDate: LocalDate): Triple<Int, Int, Int> {
    val year = gregorianDate.year
    val month = gregorianDate.monthValue
    val day = gregorianDate.dayOfMonth
    
    // Umm al-Qura algorithm constants
    val epoch = 227015 // Hijri epoch in days since 1 Jan 1 CE
    val cycleLength = 10631 // Length of 30-year cycle in days
    
    // Convert Gregorian date to days since epoch
    val gregorianDays = gregorianDate.toEpochDay() + epoch
    
    // Calculate 30-year cycles
    val cycles = gregorianDays / cycleLength
    var remainingDays = gregorianDays % cycleLength
    
    // Calculate year within cycle
    var hijriYear = cycles * 30 + 1
    
    // Month lengths in 30-year cycle (1=30 days, 0=29 days)
    val monthLengths = intArrayOf(
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 1
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 2
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 3
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 4
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 5
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 6
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 7
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 8
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 9
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 10
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 11
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 12
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 13
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 14
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 15
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 16
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 17
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 18
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 19
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 20
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 21
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 22
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 23
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 24
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 25
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 26
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 27
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 28
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 29
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0  // Year 30
    )
    
    // Find the year and month
    var hijriMonth = 1
    var hijriDay = 1
    
    for (yearIndex in 0 until 30) {
        val yearDays = monthLengths.sliceArray(yearIndex * 12 until (yearIndex + 1) * 12).sum() + 354
        if (remainingDays < yearDays) {
            hijriYear += yearIndex
            break
        }
        remainingDays -= yearDays
    }
    
    // Find the month within the year
    for (monthIndex in 0 until 12) {
        val monthDays = monthLengths[((hijriYear - 1) % 30 * 12 + monthIndex).toInt()] + 29
        if (remainingDays < monthDays) {
            hijriMonth = monthIndex + 1
            hijriDay = (remainingDays + 1).toInt()
            break
        }
        remainingDays -= monthDays
    }
    
    return Triple(hijriDay, hijriMonth, hijriYear.toInt())
}

@Composable
fun Modifier.geminiGradientEdge(
    borderWidth: Dp = 2.0.dp,
    topStart: Dp = 16.dp,
    topEnd: Dp = 16.dp,
    bottomStart: Dp = 16.dp,
    bottomEnd: Dp = 16.dp,
    gradientColors: List<Color> = listOf(
        Color(0xFFFF6B6B), // Red
        Color(0xFFFFD93D), // Yellow
        Color(0xFF6BCF7F), // Green
        Color(0xFF4D96FF), // Blue
        Color(0xFFB565D8)  // Violet
    )
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "aiGlow")
    
    // Slower, more premium animation (4 seconds)
    val shinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shinePosition"
    )
    
    // Subtle pulsing effect for premium feel
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    return this
        .drawWithContent {
        drawContent()
        }
        .drawBehind {
            val cornerRadius = 32.dp.toPx()

            // Draw subtle base glow around entire border (ambient light)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = gradientColors.map { it.copy(alpha = 0.15f * pulseAlpha) },
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                ),
                topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                cornerRadius = CornerRadius(cornerRadius + 4.dp.toPx()),
                blendMode = BlendMode.Screen
            )

            // Calculate traveling light position
        val perimeter = 2 * (size.width + size.height)
            val travelProgress = shinePosition * perimeter

            // Determine position and color based on progress
            val (x, y, colorIndex) = when {
                travelProgress <= size.width -> {
                    // Top edge
                    Triple(travelProgress, 0f, 0)
                }
                travelProgress <= size.width + size.height -> {
                    // Right edge
                    Triple(size.width, travelProgress - size.width, 1)
                }
                travelProgress <= 2 * size.width + size.height -> {
                    // Bottom edge
                    Triple(size.width - (travelProgress - size.width - size.height), size.height, 2)
                }
                else -> {
                    // Left edge
                    Triple(0f, size.height - (travelProgress - 2 * size.width - size.height), 3)
                }
            }

            // Get current gradient color with smooth transitions
            val currentColor = gradientColors[colorIndex % gradientColors.size]
            val nextColor = gradientColors[(colorIndex + 1) % gradientColors.size]

            // Draw main traveling glow with blur effect (larger radius for diffusion)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                        Color.White.copy(alpha = 0.9f * pulseAlpha),
                        currentColor.copy(alpha = 0.8f * pulseAlpha),
                        currentColor.copy(alpha = 0.5f * pulseAlpha),
                        nextColor.copy(alpha = 0.3f * pulseAlpha),
                            Color.Transparent
                        ),
                    radius = 100f
                ),
                radius = 100f,
                center = Offset(x, y),
                blendMode = BlendMode.Screen
            )

            // Draw secondary softer glow (the "bleeding" effect)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                        currentColor.copy(alpha = 0.4f * pulseAlpha),
                        currentColor.copy(alpha = 0.2f * pulseAlpha),
                            Color.Transparent
                        ),
                    radius = 150f
                ),
                radius = 150f,
                center = Offset(x, y),
                blendMode = BlendMode.Screen
            )
        }
        // NOTE: Removed graphicsLayer shadow (shadowElevation = 4dp) as it caused visual artifacts
        // during navigation transitions. The Surface/ElevatedCard already provides elevation shadow.
}

// Compact glow for small UI elements (badges, buttons)
@Composable
fun Modifier.compactGlow(
    glowColors: List<Color> = listOf(
        Color(0xFFFF6B6B), // Red
        Color(0xFFFFD93D), // Yellow
        Color(0xFF6BCF7F), // Green
        Color(0xFF4D96FF), // Blue
        Color(0xFFB565D8)  // Violet
    )
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "compactGlow")

    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    return this.drawWithContent {
        // Draw content first
        drawContent()

        // Calculate glow color
        val colorCount = glowColors.size
        val currentIndex = (shimmerPosition * colorCount).toInt() % colorCount
        val nextIndex = (currentIndex + 1) % colorCount
        val fraction = (shimmerPosition * colorCount) - currentIndex
        val currentColor = glowColors[currentIndex]
        val nextColor = glowColors[nextIndex]

        val glowColor = Color(
            red = currentColor.red + (nextColor.red - currentColor.red) * fraction,
            green = currentColor.green + (nextColor.green - currentColor.green) * fraction,
            blue = currentColor.blue + (nextColor.blue - currentColor.blue) * fraction,
            alpha = pulseAlpha
        )

        // Tight, precise glow that follows the shape
        drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                    glowColor.copy(alpha = 0.15f * pulseAlpha),
                            Color.Transparent
                        ),
                center = center,
                radius = size.minDimension * 0.4f  // Smaller radius for precision
            )
        )
    }
}

@Composable
fun Modifier.aiTextGlow(
    glowColors: List<Color> = listOf(
        Color(0xFFFF6B6B), // Red
        Color(0xFFFFD93D), // Yellow
        Color(0xFF6BCF7F), // Green
        Color(0xFF4D96FF), // Blue
        Color(0xFFB565D8)  // Violet
    )
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "textGlow")

    // Slower shimmer animation (3 seconds)
    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // Subtle pulsing
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    return this.drawBehind {
        // Calculate color index based on shimmer position
        val colorCount = glowColors.size
        val currentIndex = (shimmerPosition * colorCount).toInt() % colorCount
        val nextIndex = (currentIndex + 1) % colorCount
        val fraction = (shimmerPosition * colorCount) - currentIndex

        // Interpolate between current and next color
        val currentColor = glowColors[currentIndex]
        val nextColor = glowColors[nextIndex]

        val glowColor = Color(
            red = currentColor.red + (nextColor.red - currentColor.red) * fraction,
            green = currentColor.green + (nextColor.green - currentColor.green) * fraction,
            blue = currentColor.blue + (nextColor.blue - currentColor.blue) * fraction,
            alpha = 0.6f * pulseAlpha
        )

        // Draw soft glow behind text
        drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                    glowColor.copy(alpha = 0.4f * pulseAlpha),
                    glowColor.copy(alpha = 0.2f * pulseAlpha),
                            Color.Transparent
                        ),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.maxDimension * 0.8f
            ),
            topLeft = Offset(-8.dp.toPx(), -8.dp.toPx()),
            size = Size(size.width + 16.dp.toPx(), size.height + 16.dp.toPx()),
            cornerRadius = CornerRadius(12.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
fun Modifier.sunshineAura(
    topStart: Dp = 20.dp,
    topEnd: Dp = 20.dp,
    bottomStart: Dp = 20.dp,
    bottomEnd: Dp = 20.dp,
    auraColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant
    )
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "sunshineAura")
    
    val primaryGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "primaryGlow"
    )
    
    val secondaryPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondaryPulse"
    )
    
    val divineShimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "divineShimmer"
    )
    
    return this.drawBehind {
        // Convert custom corner radii to pixels
        val topStartPx = topStart.toPx()
        val topEndPx = topEnd.toPx()
        val bottomStartPx = bottomStart.toPx()
        val bottomEndPx = bottomEnd.toPx()
        
        // Divine aura with theme-aware colors
        val auralayers = listOf(
            Triple(16.dp.toPx(), primaryGlow * 1.1f, auraColors.getOrElse(0) { auraColors.first() }),      // Inner
            Triple(26.dp.toPx(), primaryGlow * 0.9f, auraColors.getOrElse(1) { auraColors.first() }),      // Mid  
            Triple(36.dp.toPx(), primaryGlow * 0.7f, auraColors.getOrElse(2) { auraColors.first() }),      // Light
            Triple(46.dp.toPx(), secondaryPulse * 0.5f, auraColors.getOrElse(3) { auraColors.first() }),   // Outer
        )
        
        // Draw each aura layer
        auralayers.forEachIndexed { index, (glowSize, intensity, baseColor) ->
            val shimmerBoost = kotlin.math.sin(divineShimmer + index * 1.5f) * 0.15f + 0.85f
            val finalAlpha = intensity * shimmerBoost
            
            if (finalAlpha > 0.05f) {
                // Create more sophisticated gradient
                val gradientColors = listOf(
                    baseColor.copy(alpha = finalAlpha * 0.9f),
                    baseColor.copy(alpha = finalAlpha * 0.6f),
                    baseColor.copy(alpha = finalAlpha * 0.3f),
                    baseColor.copy(alpha = finalAlpha * 0.1f),
                    Color.Transparent
                )
                
                // Create custom rounded rect path with asymmetric corners
                val glowRect = androidx.compose.ui.geometry.Rect(
                    offset = Offset(-glowSize / 2, -glowSize / 2),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width + glowSize,
                        height = size.height + glowSize
                    )
                )
                
                val glowPath = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        roundRect = androidx.compose.ui.geometry.RoundRect(
                            rect = glowRect,
                            topLeft = androidx.compose.ui.geometry.CornerRadius(
                                x = topStartPx + glowSize / 8,
                                y = topStartPx + glowSize / 8
                            ),
                            topRight = androidx.compose.ui.geometry.CornerRadius(
                                x = topEndPx + glowSize / 8,
                                y = topEndPx + glowSize / 8
                            ),
                            bottomLeft = androidx.compose.ui.geometry.CornerRadius(
                                x = bottomStartPx + glowSize / 8,
                                y = bottomStartPx + glowSize / 8
                            ),
                            bottomRight = androidx.compose.ui.geometry.CornerRadius(
                                x = bottomEndPx + glowSize / 8,
                                y = bottomEndPx + glowSize / 8
                            )
                        )
                    )
                }
                
                drawPath(
                    path = glowPath,
                    brush = Brush.radialGradient(
                        colors = gradientColors,
                        center = Offset(size.width / 2, size.height / 2),
                        radius = glowSize + (kotlin.math.sin(divineShimmer * 0.7f + index) * 8.dp.toPx())
                    )
                )
            }
        }
        
        // Divine highlights with celestial sparkles (more visible)
        val sparklePhase = kotlin.math.sin(divineShimmer * 1.3f) * 0.5f + 0.5f
        val highlightAlpha = primaryGlow * sparklePhase * 0.45f
        
        if (highlightAlpha > 0.05f) {
            val highlightSize = 6.dp.toPx()
            val highlightRect = androidx.compose.ui.geometry.Rect(
                offset = Offset(-highlightSize, -highlightSize),
                size = androidx.compose.ui.geometry.Size(
                    width = size.width + highlightSize * 2,
                    height = size.height + highlightSize * 2
                )
            )
            
            val highlightPath = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    roundRect = androidx.compose.ui.geometry.RoundRect(
                        rect = highlightRect,
                        topLeft = androidx.compose.ui.geometry.CornerRadius(
                            x = topStartPx + highlightSize,
                            y = topStartPx + highlightSize
                        ),
                        topRight = androidx.compose.ui.geometry.CornerRadius(
                            x = topEndPx + highlightSize,
                            y = topEndPx + highlightSize
                        ),
                        bottomLeft = androidx.compose.ui.geometry.CornerRadius(
                            x = bottomStartPx + highlightSize,
                            y = bottomStartPx + highlightSize
                        ),
                        bottomRight = androidx.compose.ui.geometry.CornerRadius(
                            x = bottomEndPx + highlightSize,
                            y = bottomEndPx + highlightSize
                        )
                    )
                )
            }
            
            drawPath(
                path = highlightPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        auraColors.getOrElse(0) { auraColors.first() }.copy(alpha = highlightAlpha * 0.8f),
                        auraColors.getOrElse(1) { auraColors.first() }.copy(alpha = highlightAlpha * 0.5f),
                        auraColors.getOrElse(2) { auraColors.first() }.copy(alpha = highlightAlpha * 0.3f),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
        }
    }
}


@Composable
fun SparklingStars(
    sparkleAnimation: Float,
    color: Color,
    containerSize: Dp
) {
    // Create 4 star shapes around the icon
    val sparklePositions = listOf(
        Pair(-0.35f, -0.35f), // Top-left
        Pair(0.35f, -0.35f),  // Top-right  
        Pair(-0.35f, 0.35f),  // Bottom-left
        Pair(0.35f, 0.35f)    // Bottom-right
    )
    
    sparklePositions.forEachIndexed { index, (offsetX, offsetY) ->
        // Stagger the sparkle timing for each star
        val staggeredAlpha = ((sparkleAnimation + index * 0.25f) % 1f).coerceIn(0f, 1f)
        val sparkleAlpha = if (staggeredAlpha < 0.5f) staggeredAlpha * 2f else (1f - staggeredAlpha) * 2f
        val sparkleScale = 0.3f + sparkleAlpha * 0.7f
        
        Canvas(
            modifier = Modifier
                .offset(
                    x = (containerSize.value * offsetX).dp,
                    y = (containerSize.value * offsetY).dp
                )
                .size(6.dp)
                .graphicsLayer {
                    scaleX = sparkleScale
                    scaleY = sparkleScale
                    alpha = sparkleAlpha * 0.9f
                    rotationZ = sparkleAnimation * 360f + index * 45f
                }
        ) {
            // Draw a 4-pointed star shape
            val centerX = size.width / 2
            val centerY = size.height / 2
            val outerRadius = size.width / 2
            val innerRadius = outerRadius * 0.4f
            
            val starPath = androidx.compose.ui.graphics.Path().apply {
                // Create 4-pointed star
                moveTo(centerX, centerY - outerRadius) // Top point
                lineTo(centerX + innerRadius * 0.3f, centerY - innerRadius * 0.3f)
                lineTo(centerX + outerRadius, centerY) // Right point
                lineTo(centerX + innerRadius * 0.3f, centerY + innerRadius * 0.3f)
                lineTo(centerX, centerY + outerRadius) // Bottom point
                lineTo(centerX - innerRadius * 0.3f, centerY + innerRadius * 0.3f)
                lineTo(centerX - outerRadius, centerY) // Left point
                lineTo(centerX - innerRadius * 0.3f, centerY - innerRadius * 0.3f)
                close()
            }
            
            drawPath(
                path = starPath,
                color = color.copy(alpha = sparkleAlpha * 0.8f)
            )
        }
    }
}

@Composable
fun SmartIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiWorking")
    
    // Different animations based on the type of AI work
    val (iconAnimation, backgroundAnimation) = when (label) {
        "Smart Prediction" -> {
            // Gentle pulsing to show prediction processing
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "predictionPulse"
            )
            val bgPulse by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "predictionBgPulse"
            )
            Pair(pulse, bgPulse)
        }
        "AI Content" -> {
            // Sparkling effect - slower, more elegant fade
            val sparkle1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "contentSparkle1"
            )
            val bgPulse by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "contentBgPulse"
            )
            Pair(sparkle1, bgPulse)
        }
        else -> { // Smart Analytics
            // Sparkling effect - slower, more elegant twinkling stars
            val sparkle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "analyticsSparkle"
            )
            val bgPulse by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "analysisBgPulse"
            )
            Pair(sparkle, bgPulse)
        }
    }
    
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = backgroundAnimation),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Animated icon showing AI is working
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = color.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main icon
            Icon(
                imageVector = icon,
                contentDescription = "$label - AI Working",
                tint = color,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        when (label) {
                            "Smart Prediction" -> {
                                scaleX = iconAnimation
                                scaleY = iconAnimation
                                alpha = iconAnimation
                            }
                            "AI Content", "Smart Analytics" -> {
                                // Keep main icon stable for sparkling effects
                                alpha = 1f
                            }
                        }
                    }
            )
            
            // Sparkling effects for AI Content and Smart Analytics
            if (label == "AI Content" || label == "Smart Analytics") {
                SparklingStars(
                    sparkleAnimation = iconAnimation,
                    color = color,
                    containerSize = 20.dp
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = color
        )
    }
}

@Composable
fun SwipeableBigTiles(
    prayerTimes: DayPrayerTimes?,
    currentTime: LocalTime,
    locationService: EnhancedLocationService,
    getNextPrayer: () -> Pair<String, LocalTime>?,
    getCurrentPrayer: () -> Pair<String, LocalTime>?,
    getPrayerStatus: (String) -> String,
    getPrayerTimeDisplay: (String) -> String,
    getTimeUntilNextPrayer: () -> String,
    getCurrentDate: () -> String,
    getSmartTitle: () -> String,
    getSmartContent: () -> String,
    getSmartFooter: () -> String,
    getTimeSinceCurrentPrayer: () -> String,
    getPrayerProgress: () -> Pair<Int, Int>,
    getDailyStatsTitle: () -> String,
    getDailyStatsMessage: () -> String,
    getPrayed: () -> Int = { 0 },
    getCurrentActivity: () -> String,
    onCompassClick: () -> Unit,
    timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),
    isLandscape: Boolean = false,
    onSurahClick: (Int) -> Unit = {},
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> }
) {
    // Swipeable Big Tiles - HorizontalPager with 3 tiles and infinite scroll
    val pagerState = rememberPagerState(
        pageCount = { Int.MAX_VALUE }, // Enable infinite scrolling
        initialPage = (Int.MAX_VALUE / 2 / 4) * 4 // Start in middle, adjusted to show Smart Prediction tile (index 0) first
    )

    // Coroutine scope for animated scroll when dots are tapped
    val coroutineScope = rememberCoroutineScope()

    // Globe fullscreen popup state
    var showGlobePopup by remember { mutableStateOf(false) }

    // Activity Recognition Permission for Smart Tracking tile
    val context = LocalContext.current
    var hasActivityPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Pre-Android 10 doesn't need this permission
            }
        )
    }

    val activityPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasActivityPermission = isGranted
        if (isGranted) {
            android.util.Log.i("SmartTracking", "✅ Activity Recognition permission granted")
        } else {
            android.util.Log.w("SmartTracking", "❌ Activity Recognition permission denied")
        }
    }

    // Request Activity Recognition permission and ensure detection is running when on Smart Tracking tile (page 1)
    // Also track tile focus for sound/vibration notification suppression
    // Use settledPage instead of currentPage to avoid triggering during swipe animation
    LaunchedEffect(pagerState.settledPage) {
        val actualPage = pagerState.settledPage % 4

        if (actualPage == 1) {
            // User is on Smart Tracking tile - ensure detection is running
            android.util.Log.i("SmartTracking", "📍 User on Smart Tracking tile")

            // Mark tile as in focus - enables activity change notifications
            com.starception.submission.util.ActivityTracker.setSmartActivityTileInFocus(true)

            // Request permission if needed
            if (!hasActivityPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.util.Log.i("SmartTracking", "📱 Requesting Activity Recognition permission")
                activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }

            // Ensure detection is initialized and running (in case it stopped)
            com.starception.submission.util.ActivityTracker.initialize(context, startDetectionNow = true)
        } else {
            // User swiped away from Smart Tracking tile - disable notifications
            com.starception.submission.util.ActivityTracker.setSmartActivityTileInFocus(false)
        }
    }

    // Use dynamic height based on orientation - wrapped in Box for popup overlay
    Box(modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp),
            modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        ) {
        // In landscape, pager takes most height but leaves room for indicators
        val pagerModifier = if (isLandscape) {
            Modifier
                .fillMaxWidth()
                .weight(1f) // Take remaining space after indicators
        } else {
            Modifier
                .fillMaxWidth()
                .height(200.dp) // Fixed height in portrait
        }

        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            pageSpacing = if (isLandscape) 12.dp else 16.dp,
            contentPadding = PaddingValues(horizontal = if (isLandscape) 4.dp else 8.dp),
            // Performance optimizations for smooth swiping
            beyondViewportPageCount = 1, // Preload 1 page on each side for smoother transitions
            key = { page -> page % 4 }, // Stable keys help avoid unnecessary recomposition
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.35f // Snap earlier (35% instead of 50%) for snappier feel
            )
        ) { page ->
            val actualPage = page % 4 // Map infinite pages to our 4 actual tiles
            val tileShape = RoundedCornerShape(32.dp)

            // Outer wrapper with shadow for sharp edges (like Material Components)
            Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                // Shadow layer rendered outside content
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            this.shadowElevation = 8.dp.toPx()
                            this.shape = tileShape
                            this.clip = false
                            this.ambientShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
                            this.spotShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f)
                        }
                )
                // Content layer
                Box(modifier = Modifier.fillMaxSize()) {
                    when (actualPage) {
                                0 -> NextPrayerTile(
                            prayerTimes = prayerTimes,
                            currentTime = currentTime,
                            locationService = locationService,
                            getNextPrayer = getNextPrayer,
                            getCurrentPrayer = getCurrentPrayer,
                            getPrayerStatus = getPrayerStatus,
                            getPrayerTimeDisplay = getPrayerTimeDisplay,
                            getTimeUntilNextPrayer = getTimeUntilNextPrayer,
                            getTimeSinceCurrentPrayer = getTimeSinceCurrentPrayer,
                            onCompassClick = onCompassClick,
                            timeOffsets = timeOffsets,
                            isLandscape = isLandscape
                        )
                        1 -> SmartInfoTile(
                            getSmartTitle = getSmartTitle,
                            getSmartContent = getSmartContent,
                            getCurrentDate = getCurrentDate,
                            getSmartFooter = getSmartFooter,
                            getCurrentActivity = getCurrentActivity,
                            getPrayed = getPrayed,
                            prayerTimes = prayerTimes,
                            currentTime = currentTime,
                            timeOffsets = timeOffsets,
                            isLandscape = isLandscape
                        )
                        2 -> DailyStatsTile(
                            getPrayerProgress = getPrayerProgress,
                            getDailyStatsTitle = getDailyStatsTitle,
                            getDailyStatsMessage = getDailyStatsMessage,
                            getPrayed = getPrayed,
                            isLandscape = isLandscape,
                            onSurahClick = onSurahClick,
                            onSurahClickWithAyah = onSurahClickWithAyah
                        )
                        3 -> QiblaGlobeTile(
                            prayerTimes = prayerTimes,
                            onFullscreenClick = { showGlobePopup = true }
                        )
                    }
                }
            }
        }
        
        // Page indicators for swipeable tiles - compact in landscape, CLICKABLE & SWIPEABLE to navigate
        val view = LocalView.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isLandscape) 2.dp else 1.dp)
                .pointerInput(Unit) {
                    // Swipe gesture on the dots row
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            // Swipe threshold: 50 pixels
                            if (kotlin.math.abs(totalDrag) > 50) {
                                val direction = if (totalDrag < 0) 1 else -1 // Swipe left = next, right = previous
                                val targetPage = pagerState.currentPage + direction
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                        }
                    )
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Use derivedStateOf to reduce recomposition during swipe
            val currentPageIndex by remember { derivedStateOf { pagerState.currentPage % 4 } }
            repeat(4) { index ->
                val isSelected = currentPageIndex == index
                // Smaller indicators in landscape
                val selectedSize = if (isLandscape) 8.dp else 12.dp
                val unselectedSize = if (isLandscape) 6.dp else 8.dp
                Box(
                    modifier = Modifier
                        .size(if (isSelected) selectedSize else unselectedSize)
                        .clip(CircleShape)
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        .clickable {
                            // Calculate target page for infinite scroll
                            // Find the closest page number that maps to the clicked index
                            val currentPage = pagerState.currentPage
                            val currentIndex = currentPage % 4
                            val diff = index - currentIndex
                            val targetPage = currentPage + diff

                            // Haptic feedback on tap
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                            // Animate scroll to the target page
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                )
                if (index < 3) {
                    Spacer(modifier = Modifier.width(if (isLandscape) 4.dp else 8.dp))
                }
            }
        }

        // Professional swipe hint - compact in landscape, SWIPEABLE to navigate
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isLandscape) 2.dp else 0.dp)
                .pointerInput(Unit) {
                    // Swipe gesture on the hint row
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            // Swipe threshold: 50 pixels
                            if (kotlin.math.abs(totalDrag) > 50) {
                                val direction = if (totalDrag < 0) 1 else -1 // Swipe left = next, right = previous
                                val targetPage = pagerState.currentPage + direction
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                        }
                    )
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Swipe left",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(if (isLandscape) 14.dp else 18.dp)
            )
            Spacer(modifier = Modifier.width(if (isLandscape) 4.dp else 6.dp))
            Text(
                text = if (isLandscape) "Swipe" else "Swipe for more insights",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                fontSize = if (isLandscape) 10.sp else 12.sp
            )
            Spacer(modifier = Modifier.width(if (isLandscape) 4.dp else 6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Swipe right",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(if (isLandscape) 14.dp else 18.dp)
            )
        }
    }

        // Globe fullscreen popup overlay
        if (showGlobePopup) {
            prayerTimes?.location?.let { locationData ->
                GlobePopupScreen(
                    userLatitude = locationData.latitude,
                    userLongitude = locationData.longitude,
                    onDismiss = { showGlobePopup = false }
                )
            }
        }
    }
}

@Composable
private fun NextPrayerTile(
    prayerTimes: DayPrayerTimes?,
    currentTime: LocalTime,
    locationService: EnhancedLocationService,
    getNextPrayer: () -> Pair<String, LocalTime>?,
    getCurrentPrayer: () -> Pair<String, LocalTime>?,
    getPrayerStatus: (String) -> String,
    getPrayerTimeDisplay: (String) -> String,
    getTimeUntilNextPrayer: () -> String,
    getTimeSinceCurrentPrayer: () -> String,
    onCompassClick: () -> Unit,
    timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),
    isLandscape: Boolean = false
) {
    val view = LocalView.current
    val mainPrayer = getNextPrayer() ?: getCurrentPrayer()
    // Show prayer tile if we have prayer data, even if mainPrayer logic fails
    if (mainPrayer != null || prayerTimes != null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isLandscape) 16.dp else 24.dp,
                        top = if (isLandscape) 16.dp else 24.dp,
                        end = if (isLandscape) 2.dp else 0.dp,
                        bottom = if (isLandscape) 16.dp else 24.dp
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: Icon + Title (matching Quran Player style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatchPrediction,
                        contentDescription = "Smart Prediction",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                    )
                    Text(
                        text = "Smart Prediction",
                        style = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            top = if (isLandscape) 4.dp else 8.dp,
                            bottom = if (isLandscape) 4.dp else 8.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prayer info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (isLandscape) 8.dp else 8.dp),
                        verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top
                    ) {
                        // Get notification-synchronized content using the SAME currentTime that updates every minute
                        // Pass timeOffsets to ensure smart prediction uses adjusted times (base + offset)
                        val syncContent = remember(prayerTimes, currentTime, timeOffsets) {
                            SmartContentUtils.getNotificationSyncContent(prayerTimes, currentTime, timeOffsets)
                        }
                        
                        if (syncContent != null) {
                            // Clean layout with readable fonts and optimized spacing
                            // More compact spacing in landscape
                            Column(
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 6.dp)
                            ) {
                                // Prayer phase title - compact and readable
                                Text(
                                    text = syncContent.title,
                                    style = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Main prayer time content - sized to fit longest text "59 minutes since Maghrib"
                                Text(
                                    text = syncContent.content,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = if (isLandscape) 18.sp else 22.sp,
                                        letterSpacing = (-0.4).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = if (isLandscape) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = if (isLandscape) 20.sp else 24.sp
                                )

                                // Next prayer info - enhanced with prominent chip styling and AI glow
                                if (syncContent.nextPrayerInfo.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.0f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .padding(top = if (isLandscape) 0.dp else 2.dp)
                                            .aiTextGlow()
                                    ) {
                                    Text(
                                        text = syncContent.nextPrayerInfo,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = if (isLandscape) 12.sp else 15.sp,
                                                letterSpacing = (-0.2).sp
                                            ),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 0.dp, vertical = if (isLandscape) 2.dp else 4.dp)
                                    )
                                    }
                                }
                            }
                        } else if (mainPrayer != null) {
                            // Fallback: Show upcoming prayer in notification style
                            val prayerName = mainPrayer.first
                            val prayerStatus = getPrayerStatus(prayerName)
                            val prayerTime = getPrayerTimeDisplay(prayerName)

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Title - notification style
                            Text(
                                    text = "Next Prayer: $prayerName",
                                    style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Main content - prayer time with status
                            Text(
                                    text = "$prayerStatus • $prayerTime",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.4).sp
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp
                            )
                            }
                        } else if (prayerTimes != null) {
                            // Show tomorrow's Fajr in notification style
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Title - notification style
                            Text(
                                    text = "Next Prayer: Fajr",
                                    style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Main content - tomorrow's Fajr time
                            Text(
                                    text = "Tomorrow • ${getPrayerTimeDisplay("Fajr")}",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.4).sp
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp
                            )
                            }
                        }
                    }
                    
                    // Material 3 expressive compass with enhanced interaction feedback
                    var isPressed by remember { mutableStateOf(false) }
                    
                    val compassScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        ),
                        label = "compassPressScale"
                    )
                    
                    val compassElevation by animateDpAsState(
                        targetValue = if (isPressed) 2.dp else 6.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "compassElevation"
                    )
                    
                    // Dynamic compass size based on orientation
                    val compassSize = if (isLandscape) 85.dp else 180.dp

                    // Push compass slightly beyond tile padding so it sits flush at the right edge
                    Box(modifier = Modifier.padding(top = 6.dp).offset(x = 22.dp)) {
                        Box(
                            modifier = Modifier
                                .size(compassSize)
                                .graphicsLayer {
                                    scaleX = compassScale
                                    scaleY = compassScale
                                    clip = true
                                    shape = CircleShape
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressed = true
                                            tryAwaitRelease()
                                            isPressed = false
                                        },
                                        onTap = {
                                            view.performHapticFeedback(
                                                HapticFeedbackConstants.CONTEXT_CLICK,
                                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                            )
                                            onCompassClick()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            CompassProgressIndicator(
                                progress = 0.7f,
                                modifier = Modifier.fillMaxSize(),
                                size = compassSize,
                                locationService = locationService,
                                userLatitude = prayerTimes?.location?.latitude ?: 0.0,
                                userLongitude = prayerTimes?.location?.longitude ?: 0.0,
                                showGlobe = true
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Fallback if no prayer data - Beautiful loading state
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Beautiful loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Calculating Prayer Times",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Getting your location...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmartInfoTile(
    getSmartTitle: () -> String,
    getSmartContent: () -> String,
    getCurrentDate: () -> String,
    getSmartFooter: () -> String,
    getCurrentActivity: () -> String,
    getPrayed: () -> Int = { 0 },
    prayerTimes: DayPrayerTimes? = null,
    currentTime: LocalTime,
    timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),
    isLandscape: Boolean = false
) {
    // State for bubble popup
    var selectedPrayer by remember { mutableStateOf<com.starception.submission.feature.prayertimes.components.PrayerBubbleData?>(null) }
    val view = LocalView.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLandscape) 16.dp else 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            // Header: Icon + Title (matching Quran Player style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(bottom = if (isLandscape) 0.dp else 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BubbleChart,
                    contentDescription = "Smart Tracking",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                )
                Text(
                    text = "Smart Tracking",
                    style = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                    modifier = Modifier
                        .fillMaxWidth()
                    .weight(1f)
                    .padding(top = if (isLandscape) 8.dp else 20.dp, bottom = if (isLandscape) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Main content - Side-by-side layout with proper alignment
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Content row with side-by-side columns for professional layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Prayers Done column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Prayer indicators (F D A M I) - Compact to fit all 5
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val prayers = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
                            val prayerInitials = listOf("F", "D", "A", "M", "I")

                            prayerInitials.forEachIndexed { index, initial ->
                                val isPrayed = com.starception.submission.util.PrayerTracker.isPrayerMarkedToday(prayers[index])
                                val prayerName = prayers[index]

                                // Get prayer time from prayerTimes
                                val prayerTime = prayerTimes?.let {
                                    when (prayerName) {
                                        "Fajr" -> it.fajr
                                        "Dhuhr" -> it.dhuhr
                                        "Asr" -> it.asr
                                        "Maghrib" -> it.maghrib
                                        "Isha" -> it.isha
                                        else -> null
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            // Create bubble data and show popup
                                            android.util.Log.d("PrayerBubble", "Prayer clicked: $prayerName, prayerTime: $prayerTime")
                                            if (prayerTime != null) {
                                                selectedPrayer = com.starception.submission.feature.prayertimes.components.PrayerBubbleData(
                                                    name = prayerName,
                                                    arabicName = com.starception.submission.feature.prayertimes.components.getArabicPrayerName(prayerName),
                                                    time = prayerTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")),
                                                    isPrayed = isPrayed,
                                                    initial = initial,
                                                    prayerTime = prayerTime  // Pass actual prayer time for countdown
                                                )
                                                android.util.Log.d("PrayerBubble", "Bubble data created, showing popup")
                                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            } else {
                                                android.util.Log.e("PrayerBubble", "prayerTime is null for $prayerName")
                                            }
                                        }
                                        .background(
                                            color = if (isPrayed)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = if (isPrayed) 0.dp else 1.dp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .wrapContentSize(Alignment.Center),
                                    contentAlignment = Alignment.Center
                                ) {
                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 11.sp,
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false
                                            )
                                        ),
                                        color = if (isPrayed)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                                }
                            }
                        }
                    
                        // Label
                    Text(
                            text = "Prayers Tracker",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    }

                    // Vertical divider with gradient effect
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(60.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(1.dp)
                            )
                    )

                    // Right: Current Activity column with Position detection
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Value - Use StateFlow for real-time updates
                        val currentActivityFlow = com.starception.submission.util.ActivityTracker.currentActivity
                        val currentActivity by currentActivityFlow.collectAsStateWithLifecycle()
                        val context = LocalContext.current
                        val needsPermissions = currentActivity.startsWith("Need:")
                        
                        Column(
                            modifier = if (needsPermissions) {
                                Modifier.clickable {
                                    // Request permissions when clicked if needed
                                    requestActivityPermissions(context)
                                }
                            } else {
                                Modifier
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Animated flip text like airport board
                            AnimatedFlipText(
                                text = currentActivity,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = if (needsPermissions) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            
                            // Phone Position Display (NEW - based on research paper)
                            val phonePositionFlow = com.starception.submission.util.ActivityTracker.phonePosition
                            val phonePosition by phonePositionFlow.collectAsStateWithLifecycle()
                            
                            // Position badge with icon
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                // Use custom drawable for HAND, Material icons for others
                                if (phonePosition == "HAND") {
                                    Icon(
                                        painter = painterResource(id = R.drawable.mobile_hand_24),
                                        contentDescription = "Phone Position",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = when (phonePosition) {
                                            "POCKET" -> Icons.Default.Checkroom
                                            "DESK" -> Icons.Default.DesktopWindows
                                            else -> Icons.Default.BubbleChart
                                        },
                                        contentDescription = "Phone Position",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (phonePosition) {
                                        "HAND" -> "In Hand"
                                        "POCKET" -> "In Pocket"
                                        "DESK" -> "On Desk"
                                        else -> "Unknown"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        letterSpacing = 0.3.sp
                                    ),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Label - only show when permissions needed
                            if (needsPermissions) {
                                Text(
                                    text = "Tap to grant permissions",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            }

            // Footer with notification mode selector
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable {
                        // Cycle through notification modes with persistence
                        com.starception.submission.util.ActivityTracker.cycleNotificationMode(context)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dynamic icon and text based on notification mode
                val notificationMode by com.starception.submission.util.ActivityTracker.notificationMode.collectAsStateWithLifecycle()

                val (icon, text, alpha) = when (notificationMode) {
                    com.starception.submission.util.NotificationMode.SPEAKER ->
                        Triple(Icons.Default.VolumeUp, "Alerts Enabled", 0.8f)
                    com.starception.submission.util.NotificationMode.VIBRATE ->
                        Triple(Icons.Default.Vibration, "Haptics Only", 0.7f)
                    com.starception.submission.util.NotificationMode.MUTE ->
                        Triple(Icons.Default.VolumeOff, "Silent Mode", 0.4f)
                }

                Icon(
                    imageVector = icon,
                    contentDescription = "Notification mode: $text",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Show 3D Bubble Popup when prayer is selected
    selectedPrayer?.let { prayerData ->
        com.starception.submission.feature.prayertimes.components.PrayerBubblePopup(
            prayerData = prayerData,
            onDismiss = { selectedPrayer = null },
            onTogglePrayer = { prayerName, newStatus ->
                // Toggle prayer status using PrayerTracker
                com.starception.submission.util.PrayerTracker.togglePrayerStatus(prayerName)
                android.util.Log.i("SmartTracking", "Prayer $prayerName ${if (newStatus) "marked" else "unmarked"} from balloon popup")

                // Update the selected prayer data to reflect the new status
                selectedPrayer = prayerData.copy(isPrayed = newStatus)
            }
        )
    }
}

/**
 * Qibla Globe Tile - 3D Earth showing direction from user location to Makkah
 *
 * Displays a NASA WorldWind 3D globe with:
 * - User's current location marker
 * - Kaaba location in Makkah
 * - Great circle path showing Qibla direction
 * - Fullscreen button to open interactive popup
 */
@Composable
private fun QiblaGlobeTile(
    prayerTimes: DayPrayerTimes?,
    onFullscreenClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // Create backdrop for liquid glass effect
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(32.dp),
        color = surfaceColor,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            prayerTimes?.location?.let { locationData ->
                QiblaGlobeView(
                    userLatitude = locationData.latitude,
                    userLongitude = locationData.longitude,
                    modifier = Modifier.fillMaxSize()
                )

                // Fullscreen button in top-left corner (with liquid glass effect)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(36.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                lens(with(density) { 6.dp.toPx() }, with(density) { 12.dp.toPx() })
                            }
                        )
                        .clickable { onFullscreenClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fullscreen,
                        contentDescription = "Open fullscreen globe",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

            } ?: run {
                // Fallback when no location data - show loading or default
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Qibla Globe...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Animated flip text component like airport arrival/departure boards
 */
@Composable
private fun AnimatedFlipText(
    text: String,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    var currentText by remember { mutableStateOf(text) }
    var previousText by remember { mutableStateOf(text) }

    // Detect text change and trigger animation
    LaunchedEffect(text) {
        if (text != currentText) {
            previousText = currentText
            currentText = text
        }
    }

    // Animation for flip effect
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(currentText) {
        if (currentText != previousText) {
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Show previous text flipping out
        if (animationProgress.value < 0.5f) {
            Text(
                text = previousText,
                style = style,
                color = color.copy(alpha = 1f - (animationProgress.value * 2)),
                textAlign = TextAlign.Center,
                fontWeight = fontWeight,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer {
                        rotationX = animationProgress.value * 90f
                        cameraDistance = 8f * density
                    }
            )
        }
        // Show new text flipping in
        else {
            Text(
                text = currentText,
                style = style,
                color = color.copy(alpha = (animationProgress.value - 0.5f) * 2),
                textAlign = TextAlign.Center,
                fontWeight = fontWeight,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer {
                        rotationX = -90f + ((animationProgress.value - 0.5f) * 180f)
                        cameraDistance = 8f * density
                    }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun DailyStatsTile(
    getPrayerProgress: () -> Pair<Int, Int>,
    getDailyStatsTitle: () -> String,
    getDailyStatsMessage: () -> String,
    getPrayed: () -> Int = { 0 },
    isLandscape: Boolean = false,
    onSurahClick: (Int) -> Unit = {},
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> }
) {
    val view = LocalView.current
    val context = LocalContext.current
    val audioDownloadHelper = remember {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AudioDownloadHelperEntryPoint::class.java
            )
            entryPoint.audioDownloadHelper()
        } catch (e: Exception) {
            null
        }
    }
    val viewModel: QuranPlayerViewModel = viewModel {
        QuranPlayerViewModel(context, audioDownloadHelper)
    }

    // QuranRepository for notes search
    val quranRepository = remember { QuranRepository(context) }

    var showSurahList by remember { mutableStateOf(false) }
    
    // Audio permission state for Quran playback
    val audioPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(permission = Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    // Handle permission changes
    LaunchedEffect(audioPermissionState.status) {
        if (audioPermissionState.status is PermissionStatus.Granted) {
            viewModel.clearPermissionError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
        tonalElevation = 4.dp
    ) {
        if (showSurahList) {
            // Search query state
            var searchQuery by remember { mutableStateOf("") }

            // Notes search state
            var filteredNotes by remember { mutableStateOf<List<AyahNoteEntity>>(emptyList()) }
            var isSearchingNotes by remember { mutableStateOf(false) }

            // Search notes when query changes
            LaunchedEffect(searchQuery) {
                if (searchQuery.isBlank()) {
                    filteredNotes = emptyList()
                } else {
                    isSearchingNotes = true
                    filteredNotes = quranRepository.searchNotes(searchQuery)
                    isSearchingNotes = false
                }
            }

            // Filter surahs based on search query
            val filteredSurahs = remember(searchQuery) {
                if (searchQuery.isBlank()) {
                    QuranData.surahs
                } else {
                    QuranData.surahs.filter { surah ->
                        surah.nameEnglish.contains(searchQuery, ignoreCase = true) ||
                        surah.nameArabic.contains(searchQuery, ignoreCase = true) ||
                        surah.number.toString().contains(searchQuery)
                    }
                }
            }

            // Get selected Arabic font from SharedPreferences
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
            val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
            val arabicFontFamily = when (selectedFont) {
                "pdms_saleem" -> QuranFonts.PDMSSaleem
                "noor_e_hidayat" -> QuranFonts.NoorEHidayat
                "thabit" -> QuranFonts.Thabit
                "uthmani_script" -> QuranFonts.UthmanicScript
                "indopak_script" -> QuranFonts.IndoPakScript
                else -> QuranFonts.PDMSSaleem
            }

            // LIST VIEW - Show scrollable Surah list with Material 3 expressive design
        Column(
            modifier = Modifier
                .fillMaxSize()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // Header with search bar and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search bar with Material 3 design - compact size
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        placeholder = {
                            Text(
                                text = "Search by name or number...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            focusedIndicatorColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                        ),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    // Close button
                    Surface(
                        onClick = {
                            view.performHapticFeedback(
                                HapticFeedbackConstants.CONTEXT_CLICK,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                            showSurahList = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
                    }
                }
            
                Spacer(modifier = Modifier.height(6.dp))

                // Results count
                if (searchQuery.isNotEmpty()) {
                    val totalResults = filteredSurahs.size + filteredNotes.size
            Text(
                        text = "${totalResults} result${if (totalResults != 1) "s" else ""}" +
                            if (filteredNotes.isNotEmpty()) " (${filteredNotes.size} note${if (filteredNotes.size != 1) "s" else ""})" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Scrollable Surah list with compact spacing
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredSurahs) { surah ->
                        val index = surah.number - 1
                        Surface(
                            onClick = {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                // Navigate to Surah detail screen
                                showSurahList = false
                                onSurahClick(surah.number)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (index == viewModel.currentSurahIndex)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                            else
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            border = if (index == viewModel.currentSurahIndex)
                                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
                            else
                                null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Smaller number badge
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = "${surah.number}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Surah names - compact and professional
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = surah.nameArabic,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = arabicFontFamily,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = surah.nameEnglish,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                }

                                // Play button - tap to play audio
                                IconButton(
                                    onClick = {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CONTEXT_CLICK,
                                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                        )
                                        viewModel.selectSurah(index)
                                        if (viewModel.needsAudioPermission) {
                                            audioPermissionState.launchPermissionRequest()
                                        } else {
                                            viewModel.playSurah(index)
                                            showSurahList = false  // Go back to player view
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Surah",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Type badge - clean and simple
                                Text(
                                    text = surah.revelationType.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Notes section - show matching notes when searching
                    if (filteredNotes.isNotEmpty()) {
                        item {
                            Text(
                                text = "My Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(filteredNotes, key = { "note_${it.id}" }) { note ->
                            Surface(
                                onClick = {
                                    view.performHapticFeedback(
                                        HapticFeedbackConstants.CONTEXT_CLICK,
                                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                    )
                                    // Navigate to Surah with specific ayah
                                    showSurahList = false
                                    onSurahClickWithAyah(note.surahNumber, note.ayahNumber)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Ayah reference badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "${note.surahNumber}:${note.ayahNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    // Note text preview
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.noteText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Note icon
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Go to ayah",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Get selected Arabic font from SharedPreferences for player view
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
            val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
            val arabicFontFamily = when (selectedFont) {
                "pdms_saleem" -> QuranFonts.PDMSSaleem
                "noor_e_hidayat" -> QuranFonts.NoorEHidayat
                "thabit" -> QuranFonts.Thabit
                "uthmani_script" -> QuranFonts.UthmanicScript
                "indopak_script" -> QuranFonts.IndoPakScript
                else -> QuranFonts.PDMSSaleem
            }

            // PLAYER VIEW - Ultra-compact design optimized for tile height
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isLandscape) 10.dp else 14.dp, vertical = if (isLandscape) 4.dp else 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section: Header and controls grouped together
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 0.dp else 4.dp)
                ) {
                    // Header row: Title + Language badge + Surah info (all in one compact row)
                    Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Icon + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Quran",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                        )
                        Text(
                            text = "The Noble Quran",
                            style = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Right side: Language badge
                    Surface(
                        onClick = {
                            view.performHapticFeedback(
                                HapticFeedbackConstants.CONTEXT_CLICK,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                            viewModel.toggleLanguage()
                        },
                        modifier = Modifier.compactGlow(),
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(
                            text = when (viewModel.audioLanguage) {
                                com.starception.submission.feature.quran.AudioLanguage.ARABIC_ONLY -> "AR"
                                com.starception.submission.feature.quran.AudioLanguage.BENGALI_TRANSLATION -> "বাংলা"
                                com.starception.submission.feature.quran.AudioLanguage.ENGLISH_TRANSLATION -> "EN"
                            },
                            style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Surah info and playback controls in one integrated row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Compact surah badge + name
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Clickable number badge - tiny
                        Surface(
                            onClick = {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                showSurahList = true
                            },
                            modifier = Modifier.compactGlow(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(if (isLandscape) 24.dp else 28.dp)
                            ) {
            Text(
                                    text = "${viewModel.currentSurahIndex + 1}",
                                    style = if (isLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Surah names - very compact, single line (hide English in landscape)
                        Column {
                            Text(
                                text = QuranData.surahs[viewModel.currentSurahIndex].nameArabic,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = arabicFontFamily,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                                    fontSize = if (isLandscape) 12.sp else 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1
                            )
                            if (!isLandscape) {
                                Text(
                                    text = QuranData.surahs[viewModel.currentSurahIndex].nameEnglish,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Right: Inline playback controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button - tiny
                        androidx.compose.material3.IconButton(
                            onClick = {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                viewModel.playPrevious()
                            },
                            modifier = Modifier.size(if (isLandscape) 24.dp else 32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(if (isLandscape) 16.dp else 22.dp)
                            )
                        }

                        // Play/Pause button - compact with circular glow
                        Surface(
                            onClick = {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                if (viewModel.needsAudioPermission) {
                                    audioPermissionState.launchPermissionRequest()
                                } else {
                                    viewModel.togglePlayPause()
                                }
                            },
                            modifier = Modifier
                                .size(if (isLandscape) 32.dp else 42.dp)
                                .compactGlow(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(if (isLandscape) 18.dp else 24.dp)
                                )
                            }
                        }

                        // Next button - tiny
                        androidx.compose.material3.IconButton(
                            onClick = {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                viewModel.playNext()
                            },
                            modifier = Modifier.size(if (isLandscape) 24.dp else 32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(if (isLandscape) 16.dp else 22.dp)
                            )
                        }
                    }
                }

                // Download progress overlay - replaces slider when downloading
                if (viewModel.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Downloading... ${(viewModel.downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                        LinearProgressIndicator(
                            progress = { viewModel.downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                        )
                    }
                } else if (viewModel.downloadError != null) {
                    // Download error with retry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Download failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = { viewModel.retryDownload() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                } else {
                // Bottom section: Seek slider with time display - pushed to bottom
                if (isLandscape) {
                    // Landscape: Time labels inline with slider to save vertical space
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatTime(viewModel.currentPosition),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        androidx.compose.material3.Slider(
                            value = viewModel.currentPosition.toFloat(),
                            onValueChange = { newValue ->
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.TEXT_HANDLE_MOVE,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                viewModel.seekTo(newValue.toInt())
                            },
                            valueRange = 0f..viewModel.duration.toFloat().coerceAtLeast(1f),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                inactiveTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                        )
                        Text(
                            text = formatTime(viewModel.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp
                        )
                    }
                } else {
                    // Portrait: Time labels above slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(viewModel.currentPosition),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = formatTime(viewModel.duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                        androidx.compose.material3.Slider(
                            value = viewModel.currentPosition.toFloat(),
                            onValueChange = { newValue ->
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.TEXT_HANDLE_MOVE,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                viewModel.seekTo(newValue.toInt())
                            },
                            valueRange = 0f..viewModel.duration.toFloat().coerceAtLeast(1f),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                inactiveTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .aiTextGlow()
                        )
                    }
                }
                }
                }
            }
        }
    }
}

// Helper function to format time
private fun formatTime(milliseconds: Int): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}