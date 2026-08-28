/**
 * SWIPEABLE BIG TILES COMPONENT
 *
 * The four dashboard tiles of the Prayer Times screen, presented as a calm,
 * full-width hero pager. The visual language is built around soft gradients,
 * generous rounded corners and restrained page transitions.
 *
 * WHAT IT DOES:
 * - Renders Next Prayer, Smart Tracking, Daily Stats and Qibla Globe tiles
 * - Uses a single-item HorizontalPager so every feature gets the full canvas
 * - Provides a compact page indicator in addition to swipe gestures
 *
 * WHERE IT'S USED:
 * - PrayerTimesScreen.kt: main prayer times screen, via SwipeableBigTiles()
 *
 * COMPONENTS INCLUDED:
 * - SwipeableBigTiles(): Main composable function (exported)
 * - NextPrayerTile(): Shows current/next prayer with countdown timer
 * - SmartInfoTile(): Context-aware content based on time of day
 * - DailyStatsTile(): Prayer completion progress and statistics
 * - QiblaGlobeTile(): 3D globe with the great-circle path to the Kaaba
 *
 * DEPENDENCIES:
 * - PrayerTimeHelpers.kt: For prayer time calculations and formatting
 * - SmartContentUtils.kt: For smart content generation and progress tracking
 * - DayPrayerTimes model: Prayer times data structure
 */
package com.starception.submission.feature.prayertimes


import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager as AndroidAccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import android.view.HapticFeedbackConstants
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import com.starception.submission.core.images.PrayerSkyPhase
import com.starception.submission.core.images.PrayerSkyWeather
import com.starception.submission.core.images.prayerSkyPhase
import com.starception.submission.feature.quran.dailyReading
// Aliased: this file already works in java.time, and the shared rule speaks
// kotlinx. Only the conversion at the call site crosses between them.
import kotlinx.datetime.LocalDate as KotlinLocalDate
import com.starception.submission.core.images.prayerSkyResource
import com.starception.submission.core.images.prayerSkyWeather
// The prayer sky artwork moved to :core:images so iOS renders the same skies.
// It loads through Compose Resources rather than R.drawable, hence the alias:
// the other painterResource above still serves the ground and foreground layers.
import com.starception.submission.core.images.resources.Res as ImageRes
import com.starception.submission.core.images.resources.prayer_ground_nabawi
import com.starception.submission.core.images.resources.prayer_foreground_nabawi
import com.starception.submission.core.images.resources.prayer_ground_local
import com.starception.submission.core.images.resources.prayer_ground_kaaba
import com.starception.submission.core.images.resources.prayer_foreground_kaaba
import com.starception.submission.core.images.resources.insight_salah_foreground
import com.starception.submission.core.images.resources.insight_quran_foreground_v2
import com.starception.submission.core.images.resources.insight_qibla_foreground_v2
import com.starception.submission.core.images.resources.insight_prayer_foreground
import com.starception.submission.core.images.resources.insight_prayer_background
import com.starception.submission.core.images.resources.insight_qibla_background
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.core.images.resources.insight_suggestion
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource as composePainterResource
import androidx.compose.foundation.Image
import com.starception.submission.R
import com.starception.submission.core.duadatabase.Dua
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.feature.prayertimes.components.CompassProgressIndicator
import com.starception.submission.feature.prayertimes.components.rememberParallaxTilt
import com.starception.submission.feature.prayertimes.weather.CurrentWeatherRepository
import com.starception.submission.feature.prayertimes.weather.CurrentWeather
import com.starception.submission.feature.prayertimes.weather.AnimatedPrayerWeatherIcon
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherInsight
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherThresholds
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherVisual
import com.starception.submission.feature.prayertimes.weather.getUpcomingPrayerForecastTarget
import com.starception.submission.feature.prayertimes.weather.primaryPrayerWeatherVisual
import com.starception.submission.feature.prayertimes.weather.prayerWeatherThresholdLevel
import com.starception.submission.feature.prayertimes.weather.prayerWeatherVisuals
import com.starception.submission.islamic.qibla.presentation.component.QiblaGlobeView
import com.starception.submission.prayer.service.EnhancedLocationService
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import java.time.LocalTime
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.math.sqrt
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.starception.submission.feature.prayertimes.utils.calculateQiblaDirection

private val HomeReferenceInk = Color(0xFF0A0808)
private val HomeReferenceCard = Color(0xFFFFFDF7)
private val HomeReferenceSlate = Color(0xFF5D6574)
private val HomeReferenceBlue = Color(0xFF4F779D)
private val HomeReferenceRust = Color(0xFF99593C)
private val HomeReferenceGold = Color(0xFFD8AB59)

data class DailyReadingPlaybackState(
    val surahIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val error: String? = null,
)

private data class PrayerUndoState(
    val prayerName: String,
)

@Composable
private fun referenceHeroBrush(accentColor: Color): Brush {
    val scheme = MaterialTheme.colorScheme
    return if (LocalDarkTheme.current) {
        Brush.linearGradient(
            colors = listOf(
                scheme.background,
                scheme.surfaceContainer,
                accentColor.copy(alpha = 0.18f).compositeOver(scheme.surfaceContainer),
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                scheme.surfaceContainerLowest,
                accentColor.copy(alpha = 0.08f).compositeOver(scheme.surfaceContainerLow),
                accentColor.copy(alpha = 0.18f).compositeOver(scheme.surfaceContainer),
            ),
        )
    }
}

@Composable
private fun predictionHeroBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        colors = if (LocalDarkTheme.current) {
            listOf(
                scheme.surfaceContainerLow,
                scheme.surfaceContainerHigh,
                scheme.primary.copy(alpha = 0.2f).compositeOver(scheme.surfaceContainerHigh),
            )
        } else {
            listOf(
                scheme.surfaceContainerLowest,
                scheme.primary.copy(alpha = 0.08f).compositeOver(scheme.surfaceContainerLow),
                scheme.primary.copy(alpha = 0.18f).compositeOver(scheme.surfaceContainer),
            )
        },
    )
}

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

/** Insight cards in the Home carousel, and therefore page dots in its indicator. */
private const val INSIGHT_PAGE_COUNT = 5

/**
 * Air between two page dots. Carried by each dot's hit target rather than by the
 * Row's arrangement, so the spacing stays even as the active dot widens.
 */
private val INSIGHT_INDICATOR_GAP = 10.dp

/** Key in `insight_carousel_preferences` holding the chosen indicator style name. */
private const val INSIGHT_INDICATOR_STYLE_KEY = "insight_indicator_style"

/**
 * Width : height of a resting insight card, from the reference design at
 * 250x288dp. The carousel sizes cards from the height it is given, so the shape
 * holds on viewports that cannot afford the reference height.
 */
private const val INSIGHT_CARD_ASPECT_RATIO = 250f / 288f

private data class PrayerSkyPalette(
    val top: Color,
    val horizon: Color,
    val strength: Float,
    val sceneDarkness: Float,
    val lightX: Float,
    val lightY: Float,
)

private data class PrayerSkyKeyframe(
    val minuteOfDay: Float,
    val palette: PrayerSkyPalette,
)

/**
 * Resolves the current visual phase around the calculated local prayer boundaries.
 *
 * The phase rules themselves live in :core:images alongside the artwork they
 * select, so iOS paints the same sky. This only converts java.time values into
 * the minute-of-day integers that function takes.
 */
private fun prayerSkyPhase(
    currentTime: LocalTime,
    prayerTimes: DayPrayerTimes?,
): PrayerSkyPhase = prayerSkyPhase(
    nowMinute = currentTime.toSecondOfDay() / 60,
    fajrMinute = prayerTimes?.fajr?.let { it.toSecondOfDay() / 60 } ?: 300,
    sunriseMinute = prayerTimes?.sunrise?.let { it.toSecondOfDay() / 60 } ?: 390,
    asrMinute = prayerTimes?.asr?.let { it.toSecondOfDay() / 60 } ?: 930,
    maghribMinute = prayerTimes?.maghrib?.let { it.toSecondOfDay() / 60 } ?: 1_080,
    ishaMinute = prayerTimes?.isha?.let { it.toSecondOfDay() / 60 } ?: 1_200,
)


/**
 * Resolves the insight artwork's sky treatment from the calculated prayer
 * boundaries. This keeps the carousel visually in step with the user's local
 * salah day instead of relying on fixed clock-hour assumptions.
 */
private fun prayerSkyPalette(
    currentTime: LocalTime,
    prayerTimes: DayPrayerTimes?,
): PrayerSkyPalette {
    fun LocalTime.minuteOfDay(): Float = toSecondOfDay() / 60f

    val midnight = PrayerSkyPalette(
        top = Color(0xFF06142F),
        horizon = Color(0xFF263F68),
        strength = 0.32f,
        sceneDarkness = 0.18f,
        lightX = 0.78f,
        lightY = 0.18f,
    )
    val fajr = PrayerSkyPalette(
        top = Color(0xFF344B7D),
        horizon = Color(0xFFE48672),
        strength = 0.43f,
        sceneDarkness = 0.08f,
        lightX = 0.12f,
        lightY = 0.64f,
    )
    val sunrise = PrayerSkyPalette(
        top = Color(0xFF63A7D5),
        horizon = Color(0xFFFFCC82),
        strength = 0.44f,
        sceneDarkness = 0f,
        lightX = 0.20f,
        lightY = 0.45f,
    )
    val midday = PrayerSkyPalette(
        top = Color(0xFF318CCB),
        horizon = Color(0xFFB8E5F4),
        strength = 0.42f,
        sceneDarkness = 0f,
        lightX = 0.52f,
        lightY = 0.12f,
    )
    val afternoon = PrayerSkyPalette(
        top = Color(0xFF4C86B8),
        horizon = Color(0xFFF0BE76),
        strength = 0.40f,
        sceneDarkness = 0.01f,
        lightX = 0.72f,
        lightY = 0.28f,
    )
    val sunset = PrayerSkyPalette(
        top = Color(0xFF603D7A),
        horizon = Color(0xFFF26C4E),
        strength = 0.49f,
        sceneDarkness = 0.06f,
        lightX = 0.88f,
        lightY = 0.62f,
    )
    val isha = PrayerSkyPalette(
        top = Color(0xFF0D1D42),
        horizon = Color(0xFF3A557D),
        strength = 0.34f,
        sceneDarkness = 0.16f,
        lightX = 0.76f,
        lightY = 0.20f,
    )

    val keyframes = listOf(
        PrayerSkyKeyframe(0f, midnight),
        PrayerSkyKeyframe(prayerTimes?.fajr?.minuteOfDay() ?: 300f, fajr),
        PrayerSkyKeyframe(prayerTimes?.sunrise?.minuteOfDay() ?: 390f, sunrise),
        PrayerSkyKeyframe(prayerTimes?.dhuhr?.minuteOfDay() ?: 720f, midday),
        PrayerSkyKeyframe(prayerTimes?.asr?.minuteOfDay() ?: 930f, afternoon),
        PrayerSkyKeyframe(prayerTimes?.maghrib?.minuteOfDay() ?: 1_080f, sunset),
        PrayerSkyKeyframe(prayerTimes?.isha?.minuteOfDay() ?: 1_200f, isha),
        PrayerSkyKeyframe(1_440f, midnight),
    ).sortedBy(PrayerSkyKeyframe::minuteOfDay)

    val nowMinute = currentTime.minuteOfDay()
    val (start, end) = keyframes.zipWithNext().firstOrNull { (left, right) ->
        nowMinute >= left.minuteOfDay && nowMinute <= right.minuteOfDay
    } ?: (keyframes[keyframes.lastIndex - 1] to keyframes.last())
    val duration = (end.minuteOfDay - start.minuteOfDay).coerceAtLeast(1f)
    val linearFraction = ((nowMinute - start.minuteOfDay) / duration).coerceIn(0f, 1f)
    // Smoothstep removes visible speed changes as the clock crosses a prayer boundary.
    val fraction = linearFraction * linearFraction * (3f - (2f * linearFraction))

    return PrayerSkyPalette(
        top = lerpColor(start.palette.top, end.palette.top, fraction),
        horizon = lerpColor(start.palette.horizon, end.palette.horizon, fraction),
        strength = lerp(start.palette.strength, end.palette.strength, fraction),
        sceneDarkness = lerp(start.palette.sceneDarkness, end.palette.sceneDarkness, fraction),
        lightX = lerp(start.palette.lightX, end.palette.lightX, fraction),
        lightY = lerp(start.palette.lightY, end.palette.lightY, fraction),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    prayedPrayers: Set<String> = emptySet(),
    onTogglePrayer: (String) -> Unit = {},
    dailyReadingPlayback: DailyReadingPlaybackState = DailyReadingPlaybackState(),
    onDailyReadingPlayPause: (surahIndex: Int) -> Unit = {},
    onDailyReadingRetry: () -> Unit = {},
    getCurrentActivity: () -> String,
    onCompassClick: () -> Unit,
    timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),
    isLandscape: Boolean = false,
    portraitStripHeight: Dp = 288.dp,
    compactForExpandedPrayers: Boolean = false,
    onSurahClick: (Int) -> Unit = {},
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onFortressDuaClick: (Dua) -> Unit = {},
    onBukhariBookPlayClick: (Int) -> Unit = {},
    fortressDuasByChapter: Map<Int, List<Dua>> = emptyMap(),
    goToMosqueDurationMinutes: (String) -> Int = { 20 },
    isInteractionBlocked: Boolean = false,
    weatherThresholds: PrayerWeatherThresholds = PrayerWeatherThresholds(),
) {
    val insightPageCount = INSIGHT_PAGE_COUNT
    val middleLoopStart = insightPageCount
    val pagerState = rememberPagerState(
        initialPage = middleLoopStart,
        pageCount = { insightPageCount * 3 },
    )
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val carouselScope = rememberCoroutineScope()
    var showGlobePopup by rememberSaveable { mutableStateOf(false) }
    var showAutoSwipeSettings by remember { mutableStateOf(false) }
    val carouselPreferences = remember(context) {
        context.getSharedPreferences("insight_carousel_preferences", Context.MODE_PRIVATE)
    }
    var autoSwipeEnabled by remember {
        mutableStateOf(carouselPreferences.getBoolean("auto_swipe_enabled", true))
    }
    var autoSwipeDurationSeconds by remember {
        mutableIntStateOf(carouselPreferences.getInt("auto_swipe_duration_seconds", 30).coerceIn(6, 30))
    }
    var indicatorStyle by remember {
        // Stored by name so reordering the enum can't silently repoint the setting.
        val stored = carouselPreferences.getString(INSIGHT_INDICATOR_STYLE_KEY, null)
        mutableStateOf(
            InsightIndicatorStyle.entries.firstOrNull { it.name == stored }
                ?: InsightIndicatorStyle.Capsules,
        )
    }
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val touchExplorationEnabled = remember(context) {
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AndroidAccessibilityManager)
            ?.isTouchExplorationEnabled == true
    }
    val systemAnimationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    var isUserTouching by remember { mutableStateOf(false) }
    var interactionEpoch by remember { mutableIntStateOf(0) }
    var isAutoAdvancing by remember { mutableStateOf(false) }
    var expandedLogicalPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingPrayerUndo by remember { mutableStateOf<PrayerUndoState?>(null) }
    val autoAdvanceProgress = remember { Animatable(0f) }
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 420f,
        ),
        snapPositionalThreshold = 0.24f,
    )
    var lastSettledPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.settledPage) {
        val settledLogicalPage = pagerState.settledPage % insightPageCount
        if (settledLogicalPage != lastSettledPage && !isAutoAdvancing) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastSettledPage = settledLogicalPage

        // Keep one complete copy of the carousel available on either side.
        // The duplicate pages make the first and last cards connect naturally;
        // recentering on identical content is visually imperceptible.
        when {
            pagerState.settledPage < middleLoopStart -> {
                pagerState.scrollToPage(pagerState.settledPage + insightPageCount)
            }

            pagerState.settledPage >= middleLoopStart + insightPageCount -> {
                pagerState.scrollToPage(pagerState.settledPage - insightPageCount)
            }
        }
    }

    val nextPrayer = getNextPrayer() ?: getCurrentPrayer()
    val nextPrayerName = nextPrayer?.first?.let(::getPrayerDisplayName) ?: "Prayer time"
    val nextPrayerTime = nextPrayer?.first?.let(getPrayerTimeDisplay).orEmpty()
    val prayerTitle = if (nextPrayerTime.isNotBlank()) {
        "Your next prayer is $nextPrayerName at $nextPrayerTime"
    } else {
        "Your next prayer is $nextPrayerName"
    }
    val prayerPrediction = remember(
        prayerTimes,
        currentTime,
        timeOffsets,
        goToMosqueDurationMinutes,
    ) {
        SmartContentUtils.getNotificationSyncContent(
            prayerTimes = prayerTimes,
            currentTime = currentTime,
            timeOffsets = timeOffsets,
            goToMosqueDurationMinutes = goToMosqueDurationMinutes,
        )
    }
    val livePrayerSkyPalette = remember(currentTime, prayerTimes) {
        prayerSkyPalette(currentTime = currentTime, prayerTimes = prayerTimes)
    }
    // Use real local time in every build. The accelerated debug preview made the
    // generated skies loop continuously instead of remaining at the current phase.
    val prayerNowEffectTime = currentTime
    val prayerNowSkyPalette = remember(prayerNowEffectTime, prayerTimes) {
        prayerSkyPalette(currentTime = prayerNowEffectTime, prayerTimes = prayerTimes)
    }
    val currentPrayerWeather by produceState<CurrentWeather?>(
        initialValue = null,
        key1 = prayerTimes?.location?.latitude,
        key2 = prayerTimes?.location?.longitude,
    ) {
        value = prayerTimes?.location?.let { location ->
            CurrentWeatherRepository.get(
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }
    }
    val prayerNowSkyPhase = remember(prayerNowEffectTime, prayerTimes) {
        prayerSkyPhase(prayerNowEffectTime, prayerTimes)
    }
    val prayerNowSkyWeather = remember(currentPrayerWeather?.weatherCode) {
        prayerSkyWeather(currentPrayerWeather?.weatherCode)
    }
    val prayerNowSkyResource = remember(prayerNowSkyPhase, prayerNowSkyWeather) {
        prayerSkyResource(prayerNowSkyPhase, prayerNowSkyWeather)
    }
    // Start each day on the next architectural scene. A long press can then cycle
    // through all three scenes immediately without changing the sky selection.
    val prayerNowDay = LocalDate.now().toEpochDay()
    var prayerNowManualSceneOffset by rememberSaveable(prayerNowDay) {
        mutableIntStateOf(0)
    }
    val prayerNowSceneIndex = (
        prayerNowDay.mod(3).toInt() + prayerNowManualSceneOffset
        ).mod(3)
    val prayerNowForegroundResource = remember(prayerNowSceneIndex) {
        when (prayerNowSceneIndex) {
            0 -> ImageRes.drawable.insight_salah_foreground
            1 -> ImageRes.drawable.prayer_foreground_kaaba
            else -> ImageRes.drawable.prayer_foreground_nabawi
        }
    }
    val prayerNowGroundResource = remember(prayerNowSceneIndex) {
        when (prayerNowSceneIndex) {
            0 -> ImageRes.drawable.prayer_ground_local
            1 -> ImageRes.drawable.prayer_ground_kaaba
            else -> ImageRes.drawable.prayer_ground_nabawi
        }
    }
    val prayerNowGroundFadeStart = if (prayerNowSceneIndex == 0) 0.68f else 0.84f
    val prayerNowForegroundScale = when (prayerNowSceneIndex) {
        1 -> 0.94f
        2 -> 0.92f
        else -> 1f
    }
    val prayerNowForegroundContentScale = if (prayerNowSceneIndex == 0) {
        ContentScale.Crop
    } else {
        ContentScale.Fit
    }
    val prayerNowSceneDarkness = when (prayerNowSkyPhase) {
        PrayerSkyPhase.Isha -> 0.18f
        // Fajr begins well before the sun crosses the horizon. Keep the generated
        // dawn artwork visibly pre-sunrise until the calculated Sunrise boundary;
        // otherwise its bright horizon reads as full daylight immediately after Fajr.
        PrayerSkyPhase.Fajr -> 0.34f
        PrayerSkyPhase.Maghrib -> 0.04f
        else -> 0f
    }
    val forecastTarget = remember(prayerTimes, timeOffsets, currentTime) {
        prayerTimes?.let {
            getUpcomingPrayerForecastTarget(
                prayerTimes = it,
                timeOffsets = timeOffsets,
                now = java.time.LocalDateTime.of(LocalDate.now(), currentTime),
            )
        }
    }
    val prayerWeatherInsight by produceState<PrayerWeatherInsight?>(
        null,
        prayerTimes?.location?.latitude,
        prayerTimes?.location?.longitude,
        forecastTarget,
        weatherThresholds,
    ) {
        value = if (prayerTimes != null && forecastTarget != null) {
            CurrentWeatherRepository.getPrayerInsight(
                latitude = prayerTimes.location.latitude,
                longitude = prayerTimes.location.longitude,
                prayerName = getPrayerDisplayName(forecastTarget.prayerName, forecastTarget.date),
                prayerDate = forecastTarget.date,
                prayerTime = forecastTarget.time,
                thresholds = weatherThresholds,
            )
        } else {
            null
        }
    }
    val (_, totalPrayers) = getPrayerProgress()
    val prayedCount = getPrayed().coerceIn(0, totalPrayers)
    val prayerRecap = remember(prayedPrayers, currentTime, prayerTimes) {
        val prayerStartTimes = prayerTimes?.let { times ->
            mapOf(
                "Fajr" to times.fajr,
                "Dhuhr" to times.dhuhr,
                "Asr" to times.asr,
                "Maghrib" to times.maghrib,
                "Isha" to times.isha,
            )
        }.orEmpty()
        listOf(
            "Fajr" to "F",
            "Dhuhr" to "D",
            "Asr" to "A",
            "Maghrib" to "M",
            "Isha" to "I",
        ).map { (name, initial) ->
            PrayerRecapIndicator(
                name = name,
                initial = initial,
                isPrayed = name in prayedPrayers,
                isAvailable = prayerStartTimes[name]?.let { prayerTime ->
                    !currentTime.isBefore(prayerTime)
                } ?: false,
            )
        }
    }
    val prayersRemaining = (totalPrayers - prayedCount).coerceAtLeast(0)
    val nextUnmarkedPrayer = prayerRecap.firstOrNull { !it.isPrayed }?.name
    val today = LocalDate.now()
    // The Friday/day-of-year rule lives in shared code so iOS suggests the same
    // surah on the same day; this only converts java.time to kotlinx.
    val dailySurah = remember(today) {
        dailyReading(KotlinLocalDate(today.year, today.monthValue, today.dayOfMonth))
    }
    val aiRecommendation = remember(today, currentTime.hour, fortressDuasByChapter) {
        buildContextualInsightRecommendation(
            date = today,
            time = currentTime,
            fortressDuasByChapter = fortressDuasByChapter,
        )
    }
    val qiblaBearing = remember(prayerTimes?.location) {
        prayerTimes?.location?.let { location ->
            calculateQiblaDirection(location.latitude, location.longitude).roundToInt()
        }
    }
    val focusedLogicalPage = pagerState.settledPage % insightPageCount
    val liveQiblaHeading = rememberLiveCompassHeading(
        enabled = focusedLogicalPage == 3 && isResumed && !showGlobePopup,
        latitude = prayerTimes?.location?.latitude,
        longitude = prayerTimes?.location?.longitude,
    )
    val dailySurahIsActive = dailyReadingPlayback.surahIndex == dailySurah.number - 1
    val readingKeepsFocus = focusedLogicalPage == 2 && dailySurahIsActive &&
        (dailyReadingPlayback.isPlaying || dailyReadingPlayback.isDownloading || dailyReadingPlayback.isLoading)
    val autoAdvanceEnabled = autoSwipeEnabled && isResumed && systemAnimationsEnabled && !touchExplorationEnabled &&
        !isUserTouching && (!pagerState.isScrollInProgress || isAutoAdvancing) && !isInteractionBlocked &&
        !showGlobePopup && !showAutoSwipeSettings && pendingPrayerUndo == null && !readingKeepsFocus &&
        expandedLogicalPage == null

    BackHandler(enabled = expandedLogicalPage != null) {
        expandedLogicalPage = null
        interactionEpoch++
    }

    // Expanded mode remains a carousel. Once a swipe settles, transfer the
    // expanded identity to the newly focused logical page so another long press
    // collapses that card and accessibility reports the correct state.
    LaunchedEffect(pagerState.settledPage, expandedLogicalPage) {
        if (expandedLogicalPage != null) {
            val settledLogicalPage = pagerState.settledPage % insightPageCount
            if (expandedLogicalPage != settledLogicalPage) {
                expandedLogicalPage = settledLogicalPage
            }
        }
    }

    LaunchedEffect(pendingPrayerUndo) {
        if (pendingPrayerUndo != null) {
            delay(4_000)
            pendingPrayerUndo = null
        }
    }

    LaunchedEffect(pagerState.settledPage, interactionEpoch, autoAdvanceEnabled) {
        autoAdvanceProgress.snapTo(0f)
        if (!autoAdvanceEnabled) return@LaunchedEffect
        autoAdvanceProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = autoSwipeDurationSeconds * 1_000, easing = LinearEasing),
        )
        isAutoAdvancing = true
        try {
            pagerState.animateScrollToPage(
                page = pagerState.settledPage + 1,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 420f,
                ),
            )
        } finally {
            isAutoAdvancing = false
        }
    }
    val compact = compactForExpandedPrayers && !isLandscape
    val compactTransition = updateTransition(
        targetState = compact,
        label = "insightCompactTransition",
    )
    val compactProgress by compactTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 520, easing = FastOutSlowInEasing)
        },
        label = "insightCompactProgress",
    ) { isCompact ->
        if (isCompact) 1f else 0f
    }
    val animatedExpansionProgress by animateFloatAsState(
        targetValue = if (expandedLogicalPage != null && !isLandscape) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 250f,
        ),
        label = "insightExpansionProgress",
    )
    // A spring is intentionally allowed to overshoot its target. Layout values
    // are not: values above 1 would make the pager's interpolated padding and
    // spacing negative during the overshoot frame, which Compose rejects.
    val expansionProgress = animatedExpansionProgress.coerceIn(0f, 1f)
    // The host derives the normal portrait height from the real window height,
    // so the content below the carousel is not pushed under floating navigation
    // on shorter/taller phone profiles. The expanded-prayer state remains 170dp.
    val stripHeight = (
        portraitStripHeight.value +
            ((170f - portraitStripHeight.value) * compactProgress)
        ).dp
    val expandedStripHeight = maxOf(portraitStripHeight + 92.dp, 348.dp)
    val animatedStripHeight = (
        stripHeight.value +
            ((expandedStripHeight.value - stripHeight.value) * expansionProgress)
        ).dp
    val headerFontSize = (20f - (4f * compactProgress)).sp
    val headerLineHeight = (24f - (4f * compactProgress)).sp
    val headerBottomPadding = (10f - (4f * compactProgress)).dp

    Column(
        modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = headerBottomPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Insights",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = headerFontSize,
                    lineHeight = headerLineHeight,
                    letterSpacing = (-0.25).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                InsightPageIndicator(
                    style = indicatorStyle,
                    pageCount = insightPageCount,
                    currentIndex = pagerState.currentPage % insightPageCount,
                    progress = autoAdvanceProgress.value,
                    onSelect = { index ->
                        interactionEpoch++
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val wasExpanded = expandedLogicalPage != null
                        expandedLogicalPage = null
                        carouselScope.launch {
                            if (wasExpanded) delay(180)
                            val currentLogical = pagerState.currentPage % insightPageCount
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + (index - currentLogical),
                            )
                        }
                    },
                    onLongPress = {
                        interactionEpoch++
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        showAutoSwipeSettings = true
                    },
                )
            }
        }

        val stripModifier = if (isLandscape) {
            Modifier
                .fillMaxWidth()
                .weight(1f)
        } else {
            Modifier
                .fillMaxWidth()
                .height(animatedStripHeight)
        }

        BoxWithConstraints(modifier = stripModifier) {
            val normalCardWidth = if (isLandscape) {
                // The split-screen home layout is already narrow enough to signal
                // that this is a carousel through its indicator. A portrait-style
                // peek here exposed half of the next card and clipped its copy at
                // the prayer-column boundary, so give the focused insight the full
                // left-column canvas in landscape.
                maxWidth
            } else {
                // Proportions follow the measured height. Clamping width and
                // height against independent constants made the same card render
                // portrait (0.87) on a 455x1015dp Pixel and landscape (1.12) on a
                // 384x832dp handset, because each viewport hit the opposite end of
                // its own clamp. Deriving width from height also covers the
                // sync-compression case portraitCardScale used to handle by hand.
                (maxHeight * INSIGHT_CARD_ASPECT_RATIO)
                    .coerceAtMost(maxWidth * 0.64f)
                    .coerceAtLeast(140.dp)
            }
            val compactCardWidth = if (isLandscape) {
                normalCardWidth
            } else {
                (maxWidth * 0.38f).coerceIn(138.dp, 152.dp)
            }
            val restingCardWidth = (
                normalCardWidth.value +
                    ((compactCardWidth.value - normalCardWidth.value) * compactProgress)
                ).dp
            val cardWidth = (
                restingCardWidth.value +
                    ((maxWidth.value - restingCardWidth.value) * expansionProgress)
                ).dp
            val restingPageSpacing = if (isLandscape) 10.dp else (14f - (4f * compactProgress)).dp
            val pageSpacing = (restingPageSpacing.value * (1f - expansionProgress)).dp
            val endContentPadding = if (isLandscape) {
                0.dp
            } else {
                (18f * (1f - expansionProgress)).dp
            }

            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(cardWidth),
                pageSpacing = pageSpacing,
                contentPadding = PaddingValues(end = endContentPadding),
                // One page of runway either side. At 0 the card entering the peek
                // slot composed exactly as it became visible, which showed up as a
                // pop partway through the drag.
                beyondViewportPageCount = 1,
                flingBehavior = pagerFlingBehavior,
                userScrollEnabled = true,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val touching = event.changes.any { it.pressed }
                                if (touching != isUserTouching) {
                                    isUserTouching = touching
                                    if (!touching) interactionEpoch++
                                }
                            }
                        }
                    },
            ) { page ->
                val logicalPage = page % insightPageCount
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // The scroll offset is read inside the layer block, not in
                        // composition: currentPageOffsetFraction changes every frame
                        // of a drag, and reading it above would re-run this whole
                        // card — background image, texts, compass — at frame rate.
                        // Here it only re-runs the draw phase.
                        .graphicsLayer {
                            val pageOffset = kotlin.math.abs(
                                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
                            ).coerceIn(0f, 1f)
                            val focus = 1f - pageOffset
                            val restingScale = lerp(0.97f, 1f, focus)
                            val expansionModeActive = expandedLogicalPage != null
                            val cardScale = if (expansionModeActive) {
                                lerp(restingScale, 1f, expansionProgress)
                            } else {
                                restingScale
                            }
                            scaleX = cardScale
                            scaleY = cardScale
                            val restingAlpha = lerp(0.9f, 1f, focus)
                            alpha = if (expansionModeActive) {
                                lerp(restingAlpha, 1f, expansionProgress)
                            } else {
                                restingAlpha
                            }
                        },
                ) {
                    val isFocused = page == pagerState.settledPage && !pagerState.isScrollInProgress
                    val isExpandedCard = logicalPage == expandedLogicalPage
                    val onCardLongPress: (() -> Unit)? = when {
                        !isFocused -> null
                        logicalPage == 0 -> {
                            {
                                interactionEpoch++
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                prayerNowManualSceneOffset =
                                    (prayerNowManualSceneOffset + 1).mod(3)
                                if (!isLandscape) {
                                    expandedLogicalPage = if (isExpandedCard) null else logicalPage
                                }
                            }
                        }
                        !isLandscape -> {
                            {
                                interactionEpoch++
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                expandedLogicalPage = if (isExpandedCard) null else logicalPage
                            }
                        }
                        else -> null
                    }
                    when (logicalPage) {
                        0 -> InsightPreviewCard(
                            label = "Prayer now",
                            title = prayerPrediction?.title ?: prayerTitle,
                            supportingText = prayerPrediction?.content
                                ?: getTimeUntilNextPrayer().takeIf { it.isNotBlank() },
                            statusText = prayerPrediction?.nextPrayerInfo
                                .orEmpty()
                                .let { nextPrayerInfo ->
                                    nextPrayerInfo.takeIf { it.isNotBlank() }
                                        ?: listOf(nextPrayerName, nextPrayerTime)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                            .takeIf { it.isNotBlank() }
                                },
                            // Keep every threshold condition. Taking only the text
                            // after the last separator dropped humidity whenever a
                            // later condition (usually heat) was also active.
                            statusMetaText = prayerWeatherInsight?.summary,
                            weatherThresholds = weatherThresholds,
                            backgroundPainterRes = prayerNowSkyResource,
                            groundPainterRes = prayerNowGroundResource,
                            groundFadeStartFraction = prayerNowGroundFadeStart,
                            foregroundPainterRes = prayerNowForegroundResource,
                            foregroundBaseScale = prayerNowForegroundScale,
                            foregroundContentScale = prayerNowForegroundContentScale,
                            foregroundSceneDarkness = prayerNowSceneDarkness,
                            skyTintTop = prayerNowSkyPalette.top,
                            skyTintHorizon = prayerNowSkyPalette.horizon,
                            // Every time × weather combination has its own graded
                            // photograph, so retain its natural color instead of
                            // tinting it a second time.
                            skyTintStrength = 0f,
                            sceneDarkness = prayerNowSceneDarkness,
                            compactProgress = compactProgress,
                            expansionProgress = if (expandedLogicalPage != null) expansionProgress else 0f,
                            isExpanded = isExpandedCard,
                            onLongPress = onCardLongPress,
                            longPressLabel = if (isExpandedCard) {
                                "Change mosque and collapse Prayer Now"
                            } else {
                                "Change mosque and enlarge Prayer Now"
                            },
                            fullDayEffect = true,
                            isFocused = isFocused,
                            backgroundPageOffset = {
                                (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                            },
                            timelineProgress = prayerWindowProgress(prayerTimes, currentTime),
                            actionLabel = "Compass",
                            actionDescription = "Open prayer compass",
                            onClick = {
                                interactionEpoch++
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onCompassClick()
                            },
                        )

                        1 -> InsightPreviewCard(
                            label = "Today's salah",
                            title = when {
                                prayedCount == totalPrayers -> "All prayers complete"
                                prayedCount == 1 -> "1 prayer complete"
                                else -> "$prayedCount prayers complete"
                            },
                            supportingText = when {
                                prayersRemaining == 0 -> "All five marked for today"
                                prayersRemaining == 1 && nextUnmarkedPrayer != null ->
                                    "$nextUnmarkedPrayer remains today"
                                nextUnmarkedPrayer != null ->
                                    "$prayersRemaining remain · $nextUnmarkedPrayer is next"
                                else -> "$prayersRemaining prayers remain today"
                            },
                            backgroundPainterRes = ImageRes.drawable.insight_prayer_background,
                            foregroundPainterRes = ImageRes.drawable.insight_prayer_foreground,
                            foregroundBaseScale = 0.84f,
                            foregroundOffsetFraction = Offset(0f, 0.08f),
                            skyTintTop = livePrayerSkyPalette.top,
                            skyTintHorizon = livePrayerSkyPalette.horizon,
                            skyTintStrength = livePrayerSkyPalette.strength * 0.58f,
                            sceneDarkness = livePrayerSkyPalette.sceneDarkness * 0.72f,
                            compactProgress = compactProgress,
                            expansionProgress = if (expandedLogicalPage != null) expansionProgress else 0f,
                            isExpanded = isExpandedCard,
                            onLongPress = onCardLongPress,
                            isFocused = isFocused,
                            backgroundPageOffset = {
                                (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                            },
                            prayerRecap = prayerRecap,
                            onPrayerToggle = { prayerName ->
                                prayerRecap.firstOrNull { it.name == prayerName }?.let { prayer ->
                                    if (prayer.isAvailable || prayer.isPrayed) {
                                        interactionEpoch++
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        onTogglePrayer(prayerName)
                                        pendingPrayerUndo = if (prayer.isAvailable) {
                                            PrayerUndoState(prayerName)
                                        } else {
                                            null
                                        }
                                    }
                                }
                            },
                            undoPrayerName = pendingPrayerUndo?.prayerName,
                            onPrayerUndo = {
                                pendingPrayerUndo?.let { undo ->
                                    val prayer = prayerRecap.firstOrNull { it.name == undo.prayerName }
                                    if (prayer?.isAvailable == true) {
                                        interactionEpoch++
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onTogglePrayer(undo.prayerName)
                                    }
                                    pendingPrayerUndo = null
                                }
                            },
                        )

                        2 -> InsightPreviewCard(
                            label = "Today's reading",
                            title = dailySurah.nameEnglish,
                            supportingText = dailySurah.nameArabic,
                            footerText = "Surah ${dailySurah.number} · ${dailySurah.revelationType}",
                            backgroundPainterRes = ImageRes.drawable.insight_quran_background,
                            foregroundPainterRes = ImageRes.drawable.insight_quran_foreground_v2,
                            // Let more of the reading scene breathe around the Quran.
                            foregroundBaseScale = 0.74f,
                            foregroundOffsetFraction = Offset(0f, 0.09f),
                            skyTintTop = livePrayerSkyPalette.top,
                            skyTintHorizon = livePrayerSkyPalette.horizon,
                            skyTintStrength = livePrayerSkyPalette.strength * 0.46f,
                            sceneDarkness = livePrayerSkyPalette.sceneDarkness * 0.64f,
                            compactProgress = compactProgress,
                            expansionProgress = if (expandedLogicalPage != null) expansionProgress else 0f,
                            isExpanded = isExpandedCard,
                            onLongPress = onCardLongPress,
                            isFocused = isFocused,
                            backgroundPageOffset = {
                                (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                            },
                            readingSurahIndex = dailySurah.number - 1,
                            readingPlayback = dailyReadingPlayback,
                            onReadingPlayPause = {
                                interactionEpoch++
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onDailyReadingPlayPause(dailySurah.number - 1)
                            },
                            onReadingRetry = {
                                interactionEpoch++
                                onDailyReadingRetry()
                            },
                            actionLabel = "Read",
                            actionDescription = "Open ${dailySurah.nameEnglish}",
                            onClick = {
                                interactionEpoch++
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSurahClick(dailySurah.number)
                            },
                        )

                        3 -> InsightPreviewCard(
                            label = "Qibla",
                            title = qiblaBearing?.let { "$it° toward Makkah" }
                                ?: "Direction to Makkah",
                            supportingText = qiblaBearing?.let(::qiblaCardinalDirection),
                            footerText = "Open the live compass and 3D globe",
                            backgroundPainterRes = ImageRes.drawable.insight_qibla_background,
                            foregroundPainterRes = ImageRes.drawable.insight_qibla_foreground_v2,
                            // Keep the compass present without dominating the Qibla scene.
                            foregroundBaseScale = 0.75f,
                            foregroundOffsetFraction = Offset(0.025f, 0.11f),
                            skyTintTop = livePrayerSkyPalette.top,
                            skyTintHorizon = livePrayerSkyPalette.horizon,
                            skyTintStrength = livePrayerSkyPalette.strength * 0.54f,
                            sceneDarkness = livePrayerSkyPalette.sceneDarkness * 0.68f,
                            compactProgress = compactProgress,
                            expansionProgress = if (expandedLogicalPage != null) expansionProgress else 0f,
                            isExpanded = isExpandedCard,
                            onLongPress = onCardLongPress,
                            isFocused = isFocused,
                            backgroundPageOffset = {
                                (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                            },
                            directionalHintBearing = qiblaBearing,
                            deviceHeadingDegrees = liveQiblaHeading,
                            actionDescription = "Open Qibla compass",
                            onClick = {
                                interactionEpoch++
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                showGlobePopup = true
                            },
                        )

                        else -> InsightPreviewCard(
                            label = "AI suggested",
                            title = aiRecommendation.title,
                            supportingText = aiRecommendation.supportingText,
                            footerText = aiRecommendation.footerText,
                            backgroundPainterRes = ImageRes.drawable.insight_suggestion,
                            skyTintTop = livePrayerSkyPalette.top,
                            skyTintHorizon = livePrayerSkyPalette.horizon,
                            skyTintStrength = livePrayerSkyPalette.strength * 0.50f,
                            sceneDarkness = livePrayerSkyPalette.sceneDarkness * 0.70f,
                            compactProgress = compactProgress,
                            expansionProgress = if (expandedLogicalPage != null) expansionProgress else 0f,
                            isExpanded = isExpandedCard,
                            onLongPress = onCardLongPress,
                            isFocused = isFocused,
                            backgroundPageOffset = {
                                (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                            },
                            actionLabel = when (aiRecommendation.target) {
                                is ContextualRecommendationTarget.Surah -> "Read"
                                is ContextualRecommendationTarget.FortressDua -> "Open dua"
                                is ContextualRecommendationTarget.Bukhari -> "Play book"
                            },
                            actionDescription = aiRecommendation.actionDescription,
                            onClick = {
                                interactionEpoch++
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                when (val target = aiRecommendation.target) {
                                    is ContextualRecommendationTarget.Surah -> {
                                        onSurahClick(target.number)
                                    }

                                    is ContextualRecommendationTarget.FortressDua -> {
                                        onFortressDuaClick(target.dua)
                                    }

                                    is ContextualRecommendationTarget.Bukhari -> {
                                        onBukhariBookPlayClick(target.book.id)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAutoSwipeSettings) {
        ModalBottomSheet(
            onDismissRequest = { showAutoSwipeSettings = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The indicator options push the sheet past a half-expanded
                    // ModalBottomSheet, and the last one was unreachable without this.
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Insight carousel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Choose whether the cards move automatically and how long each insight stays visible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Auto-swipe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (autoSwipeEnabled) "On" else "Off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoSwipeEnabled,
                            onCheckedChange = { enabled ->
                                autoSwipeEnabled = enabled
                                interactionEpoch++
                                carouselPreferences.edit()
                                    .putBoolean("auto_swipe_enabled", enabled)
                                    .apply()
                            },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Time per insight",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$autoSwipeDurationSeconds seconds",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (autoSwipeEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = autoSwipeDurationSeconds.toFloat(),
                        onValueChange = { value ->
                            autoSwipeDurationSeconds = value.roundToInt().coerceIn(6, 30)
                            interactionEpoch++
                        },
                        onValueChangeFinished = {
                            carouselPreferences.edit()
                                .putInt("auto_swipe_duration_seconds", autoSwipeDurationSeconds)
                                .apply()
                        },
                        enabled = autoSwipeEnabled,
                        valueRange = 6f..30f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("6s", style = MaterialTheme.typography.labelSmall)
                        Text("30s", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Page indicator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Each option is drawn live, on the real page position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    InsightIndicatorStyle.entries.forEach { option ->
                        val isCurrent = option == indicatorStyle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    indicatorStyle = option
                                    interactionEpoch++
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    carouselPreferences.edit()
                                        .putString(INSIGHT_INDICATOR_STYLE_KEY, option.name)
                                        .apply()
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                // Selection is the primary colour plus this rule —
                                // the sheet stays borderless, with no chip around
                                // the choice.
                                Box(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .width(if (isCurrent) 22.dp else 0.dp)
                                        .height(2.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            // The preview is the real indicator, on the real page,
                            // so what is chosen is exactly what was seen.
                            InsightPageIndicator(
                                style = option,
                                pageCount = insightPageCount,
                                currentIndex = pagerState.currentPage % insightPageCount,
                                progress = autoAdvanceProgress.value,
                                onSelect = {
                                    indicatorStyle = option
                                    carouselPreferences.edit()
                                        .putString(INSIGHT_INDICATOR_STYLE_KEY, option.name)
                                        .apply()
                                },
                                onLongPress = {},
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGlobePopup) {
        prayerTimes?.location?.let { locationData ->
            GlobePopupScreen(
                userLatitude = locationData.latitude,
                userLongitude = locationData.longitude,
                onDismiss = { showGlobePopup = false },
            )
        }
    }
}

private data class PrayerRecapIndicator(
    val name: String,
    val initial: String,
    val isPrayed: Boolean,
    val isAvailable: Boolean,
)

/**
 * A continuous-corner rectangle similar to the softly squared silhouette in
 * the reference. Its Bezier handles stay flatter near each edge than a normal
 * quarter-circle, avoiding the pinched look of [RoundedCornerShape].
 */
private data class ContinuousCornerShape(
    val cornerExtent: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val extent = with(density) { cornerExtent.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)
        if (extent == 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        // 0.32 keeps the curve close to the outer corner for longer than the
        // 0.448 circular control point, producing a calm iOS-style squircle.
        val innerControl = extent * 0.32f
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(extent, 0f)
            lineTo(width - extent, 0f)
            cubicTo(
                width - innerControl, 0f,
                width, innerControl,
                width, extent,
            )
            lineTo(width, height - extent)
            cubicTo(
                width, height - innerControl,
                width - innerControl, height,
                width - extent, height,
            )
            lineTo(extent, height)
            cubicTo(
                innerControl, height,
                0f, height - innerControl,
                0f, height - extent,
            )
            lineTo(0f, extent)
            cubicTo(
                0f, innerControl,
                innerControl, 0f,
                extent, 0f,
            )
            close()
        }
        return Outline.Generic(path)
    }
}

/** Converts threshold warnings into a compact, value-first forecast line. */
internal fun formatPrayerForecastMetrics(summary: String?): String? {
    val conditions = summary
        ?.split('·')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        .orEmpty()
    if (conditions.isEmpty()) return null

    fun percentage(condition: String): String? = condition
        .split(' ')
        .firstOrNull { token -> token.trimEnd(',', '.').endsWith('%') }
        ?.trimEnd(',', '.')

    val temperature = conditions
        .asSequence()
        .flatMap { it.split(' ').asSequence() }
        .firstOrNull { '°' in it }
        ?.trimEnd(',', '.')
    val humidity = conditions
        .firstOrNull { it.contains("humidity", ignoreCase = true) }
        ?.let(::percentage)
        ?.let { "$it RH" }
    val rain = conditions
        .firstOrNull { it.contains("rain", ignoreCase = true) }
        ?.let(::percentage)
        ?.let { "$it rain" }

    return listOfNotNull(temperature, humidity, rain)
        .distinct()
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
}

/**
 * Returns the single most actionable forecast value for space-constrained cards.
 * The animated icon supplies the condition, so repeating "rain" or "RH" here
 * would spend width without adding meaning.
 */
internal fun formatPrimaryPrayerForecastMetric(summary: String?): String? {
    val visual = primaryPrayerWeatherVisual(summary) ?: return null
    return formatPrayerForecastMetric(summary, visual)
}

/** Formats the compact value paired with one rotating forecast visual. */
internal fun formatPrayerForecastMetric(
    summary: String?,
    visual: PrayerWeatherVisual,
): String? {
    val conditions = summary
        ?.split('·')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        .orEmpty()
    if (conditions.isEmpty()) return null

    fun percentage(condition: String): String? = condition
        .split(' ')
        .firstOrNull { token -> token.trimEnd(',', '.').endsWith('%') }
        ?.trimEnd(',', '.')

    return when (visual) {
        PrayerWeatherVisual.Rain -> conditions
            .firstOrNull { it.contains("rain", ignoreCase = true) }
            ?.let(::percentage)
        PrayerWeatherVisual.Heat -> conditions
            .asSequence()
            .flatMap { it.split(' ').asSequence() }
            .firstOrNull { '°' in it }
            ?.trimEnd(',', '.')
        PrayerWeatherVisual.Humidity -> conditions
            .firstOrNull { it.contains("humidity", ignoreCase = true) }
            ?.let(::percentage)
    }
}

@Composable
private fun InsightPreviewCard(
    label: String,
    title: String,
    backgroundPainterRes: DrawableResource,
    groundPainterRes: DrawableResource? = null,
    groundFadeStartFraction: Float = 0.68f,
    foregroundPainterRes: DrawableResource? = null,
    foregroundBaseScale: Float = 1f,
    foregroundOffsetFraction: Offset = Offset.Zero,
    foregroundContentScale: ContentScale = ContentScale.Crop,
    foregroundSceneDarkness: Float = 0f,
    skyTintTop: Color? = null,
    skyTintHorizon: Color? = null,
    skyTintStrength: Float = 0f,
    sceneDarkness: Float = 0f,
    compactProgress: Float,
    expansionProgress: Float = 0f,
    isExpanded: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    fullDayEffect: Boolean = false,
    isFocused: Boolean = false,
    backgroundPageOffset: () -> Float = { 0f },
    supportingText: String? = null,
    statusText: String? = null,
    statusMetaText: String? = null,
    weatherThresholds: PrayerWeatherThresholds = PrayerWeatherThresholds(),
    footerText: String? = null,
    timelineProgress: Float? = null,
    prayerRecap: List<PrayerRecapIndicator> = emptyList(),
    onPrayerToggle: ((String) -> Unit)? = null,
    undoPrayerName: String? = null,
    onPrayerUndo: (() -> Unit)? = null,
    readingSurahIndex: Int? = null,
    readingPlayback: DailyReadingPlaybackState = DailyReadingPlaybackState(),
    onReadingPlayPause: (() -> Unit)? = null,
    onReadingRetry: (() -> Unit)? = null,
    directionalHintBearing: Int? = null,
    deviceHeadingDegrees: Float? = null,
    actionLabel: String? = null,
    actionDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    var suppressClickUntilMs by remember { mutableLongStateOf(0L) }
    val hasPrayerContext = !supportingText.isNullOrBlank() ||
        !statusText.isNullOrBlank() ||
        !statusMetaText.isNullOrBlank() ||
        !footerText.isNullOrBlank() ||
        prayerRecap.isNotEmpty()
    val cornerExtent = (26f - (8f * compactProgress) + (6f * expansionProgress)).dp
    val shape = ContinuousCornerShape(cornerExtent)
    val shadowElevation = (6f - (2f * compactProgress) + (4f * expansionProgress)).dp
    val rawDirectionalRotation = directionalHintBearing?.let { bearing ->
        normalizeDegrees(bearing - (deviceHeadingDegrees ?: 0f))
    }
    var continuousDirectionalRotation by remember(directionalHintBearing) {
        mutableFloatStateOf(rawDirectionalRotation ?: 0f)
    }
    LaunchedEffect(rawDirectionalRotation) {
        rawDirectionalRotation?.let { target ->
            val shortestDelta = shortestAngleDelta(continuousDirectionalRotation, target)
            continuousDirectionalRotation += shortestDelta
        }
    }
    val animatedDirectionalRotation by animateFloatAsState(
        targetValue = continuousDirectionalRotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "liveQiblaDirection",
    )
    val animatedSkyTop by animateColorAsState(
        targetValue = skyTintTop ?: Color.Transparent,
        animationSpec = tween(durationMillis = 4_000, easing = FastOutSlowInEasing),
        label = "prayerSkyTop",
    )
    val animatedSkyHorizon by animateColorAsState(
        targetValue = skyTintHorizon ?: Color.Transparent,
        animationSpec = tween(durationMillis = 4_000, easing = FastOutSlowInEasing),
        label = "prayerSkyHorizon",
    )
    val animatedSkyStrength by animateFloatAsState(
        targetValue = skyTintStrength,
        animationSpec = tween(durationMillis = 4_000, easing = FastOutSlowInEasing),
        label = "prayerSkyStrength",
    )
    val animatedSceneDarkness by animateFloatAsState(
        targetValue = sceneDarkness,
        animationSpec = tween(durationMillis = 4_000, easing = FastOutSlowInEasing),
        label = "insightSceneDarkness",
    )
    val animatedForegroundSceneDarkness by animateFloatAsState(
        targetValue = foregroundSceneDarkness,
        animationSpec = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
        label = "insightForegroundSceneDarkness",
    )
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            )
            // The clickable indication belongs inside the same squircle as the
            // image. Without this clip, a long press reveals a rectangular
            // ripple around the rounded card.
            .clip(shape)
            .then(
                when {
                    onLongPress != null -> Modifier.combinedClickable(
                        onClick = {
                            if (SystemClock.uptimeMillis() >= suppressClickUntilMs) {
                                onClick?.invoke()
                            }
                        },
                        onLongClickLabel = longPressLabel ?: if (isExpanded) {
                            "Collapse insight"
                        } else {
                            "Expand insight"
                        },
                        onLongClick = {
                            // Expanding changes the card's measured bounds while
                            // the pointer is still held. Guard the release so the
                            // newly measured clickable cannot interpret it as a
                            // separate tap and launch the card action as well.
                            suppressClickUntilMs = SystemClock.uptimeMillis() + 900L
                            onLongPress()
                        },
                    )

                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            ),
        shape = shape,
        color = Color(0xFF635A56),
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cardMaxWidth = maxWidth
            val tagInset = (11f - (5f * compactProgress) + (3f * expansionProgress)).dp
            val labelFontSize = (11f - (3f * compactProgress) + expansionProgress).sp
            val titleFontSize = (17f - (5f * compactProgress) + (3f * expansionProgress)).sp
            val titleLineHeight = (22f - (8f * compactProgress) + (4f * expansionProgress)).sp
            val defaultHorizontalPadding = (
                22f - (10f * compactProgress) + (4f * expansionProgress)
                ).dp
            val titleHorizontalPadding = if (hasPrayerContext && maxWidth < 240.dp) {
                // A half-width insight card cannot afford desktop-like 22dp side
                // insets. Preserve the original footer composition while giving
                // the prayer countdown enough room to remain complete.
                minOf(defaultHorizontalPadding, 14.dp)
            } else {
                defaultHorizontalPadding
            }
            val titleBottomPadding = (16f - (7f * compactProgress) + (4f * expansionProgress)).dp
            val contentSpacing = (7f - (4f * compactProgress) + (2f * expansionProgress)).dp
            val supportingFontSize = (13f - (3f * compactProgress) + (2f * expansionProgress)).sp
            val supportingLineHeight = (17f - (4f * compactProgress) + (3f * expansionProgress)).sp
            val footerFontSize = (12f - (3f * compactProgress) + expansionProgress).sp
            val footerLineHeight = (16f - (4f * compactProgress) + (2f * expansionProgress)).sp
            val recapIndicatorSize = (24f - (7f * compactProgress)).dp
            val recapFontSize = (11f - (3f * compactProgress)).sp
            val requestedHeroAccent = MaterialTheme.colorScheme.primaryContainer
            val heroAccent = if (requestedHeroAccent.luminance() >= 0.35f) {
                requestedHeroAccent
            } else {
                // Custom and monochrome themes can supply a near-black accent.
                // This content always sits over a dark photographic scrim, so
                // fall back locally instead of sacrificing legibility.
                Color.White
            }
            // MaterialTheme's dynamic scheme is derived from the user's wallpaper on
            // Android 12+. Tint the meaningful card artwork with that palette instead of
            // trying to read the wallpaper bitmap (which Android 14+ intentionally blocks).
            // The brush is remembered, so carousel motion changes only a layer alpha and
            // does not recreate or decode an image while the pager is moving.
            val wallpaperPrimary = MaterialTheme.colorScheme.primary
            val wallpaperSecondary = MaterialTheme.colorScheme.secondary
            val wallpaperTertiary = MaterialTheme.colorScheme.tertiary
            val wallpaperTintBrush = remember(
                wallpaperPrimary,
                wallpaperSecondary,
                wallpaperTertiary,
            ) {
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to wallpaperPrimary,
                        0.52f to wallpaperSecondary,
                        1f to wallpaperTertiary,
                    ),
                )
            }
            val ambientMotion by rememberInfiniteTransition(label = "heroBackgroundMotion")
                .animateFloat(
                    initialValue = -1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 12_000,
                            easing = FastOutSlowInEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "heroBackgroundDrift",
                )
            val focusedMotionStrength by animateFloatAsState(
                targetValue = if (isFocused) 1f else 0f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                label = "heroBackgroundFocus",
            )

            Crossfade(
                targetState = backgroundPainterRes,
                animationSpec = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
                label = "prayerSkyCrossfade",
                modifier = Modifier.fillMaxSize(),
            ) { resolvedBackgroundRes ->
                Image(
                    painter = composePainterResource(resolvedBackgroundRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // A single decoded still stays on the GPU. Moving it opposite
                            // the pager creates depth without competing with the swipe for
                            // animated-image decoding or causing a loop-boundary jump.
                            val pageOffset = backgroundPageOffset().coerceIn(-1f, 1f)
                            val restingMotion = ambientMotion * focusedMotionStrength
                            val imageScale = 1.065f + (restingMotion * 0.006f)
                            val requestedTranslationX = -pageOffset * 18.dp.toPx()
                            // The old parallax could move the next page farther than
                            // this scaled layer's overscan, revealing the card's grey
                            // Surface along its leading edge. Restrict the shift to
                            // the pixels that are guaranteed to remain covered.
                            val maxCoveredHorizontalShift = (
                                (size.width * (imageScale - 1f) / 2f) - 1.dp.toPx()
                                ).coerceAtLeast(0f)
                            scaleX = imageScale
                            scaleY = imageScale
                            translationX = requestedTranslationX.coerceIn(
                                -maxCoveredHorizontalShift,
                                maxCoveredHorizontalShift,
                            )
                            translationY = restingMotion * 4.dp.toPx()
                        },
                )
            }

            if (animatedSceneDarkness > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = animatedSceneDarkness)),
                )
            }

            if (animatedSkyStrength > 0f) {
                // Concentrate the time-of-day treatment in the sky and horizon;
                // fading before the lower content keeps the mosque lighting and
                // text scrim natural. Every insight supplies the same local-time
                // palette at a strength suited to its artwork.
                val skyBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to animatedSkyTop.copy(alpha = animatedSkyStrength),
                        0.42f to animatedSkyHorizon.copy(
                            alpha = animatedSkyStrength * 0.82f,
                        ),
                        0.68f to Color.Transparent,
                    ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (fullDayEffect && animatedSceneDarkness < 0.08f) {
                                Modifier.drawBehind {
                                    drawRect(
                                        brush = skyBrush,
                                        blendMode = BlendMode.Screen,
                                    )
                                }
                            } else {
                                Modifier.background(skyBrush)
                            },
                        ),
                )
            }

            if (fullDayEffect) {
                // The source photograph is intentionally warm and moody. During
                // daylight, lift the architecture as well as recoloring the sky so
                // the card reads as daytime instead of a blue-tinted evening shot.
                val daylight = (1f - (animatedSceneDarkness / 0.18f).coerceIn(0f, 1f))
                val daylightLift = daylight * animatedSkyStrength * 0.18f
                if (daylightLift > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = daylightLift)),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val restingLight = ((ambientMotion + 1f) * 0.5f) * focusedMotionStrength
                        alpha = 0.13f + (restingLight * 0.05f)
                    }
                    .background(wallpaperTintBrush),
            )

            groundPainterRes?.let { groundRes ->
                Crossfade(
                    targetState = groundRes,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "insightGroundCrossfade",
                    modifier = Modifier.fillMaxSize(),
                ) { resolvedGroundRes ->
                    Image(
                        painter = composePainterResource(resolvedGroundRes),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset = backgroundPageOffset().coerceIn(-1f, 1f)
                                translationX = -pageOffset * 3.dp.toPx()
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                // The generated courtyard contains more distant
                                // floor than the card needs. Mask its upper area so
                                // it remains a floor beneath the architecture instead
                                // of becoming a wall-like backdrop behind the mosque.
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Transparent,
                                            groundFadeStartFraction to Color.Transparent,
                                            (groundFadeStartFraction + 0.07f)
                                                .coerceAtMost(1f) to Color.White,
                                            1f to Color.White,
                                        ),
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    )
                }
            }

            foregroundPainterRes?.let { foregroundRes ->
                Crossfade(
                    targetState = foregroundRes,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "insightForegroundCrossfade",
                    modifier = Modifier.fillMaxSize(),
                ) { resolvedForegroundRes ->
                    Image(
                        painter = composePainterResource(resolvedForegroundRes),
                        contentDescription = null,
                        contentScale = foregroundContentScale,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // The subject remains materially stable while the
                                // independently controlled environment breathes.
                                // A smaller movement preserves depth without making
                                // the object appear to float over its background.
                                val pageOffset = backgroundPageOffset().coerceIn(-1f, 1f)
                                val restingMotion = ambientMotion * focusedMotionStrength
                                val foregroundScale = (1.025f * foregroundBaseScale) +
                                    (restingMotion * 0.002f)
                                scaleX = foregroundScale
                                scaleY = foregroundScale
                                translationX = (-pageOffset * 6.dp.toPx()) +
                                    (restingMotion * 1.5.dp.toPx()) +
                                    (size.width * foregroundOffsetFraction.x)
                                translationY = (restingMotion * 1.dp.toPx()) +
                                    (size.height * foregroundOffsetFraction.y)
                            },
                    )
                }
            }

            if (animatedForegroundSceneDarkness > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = animatedForegroundSceneDarkness)),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                (if (hasPrayerContext) 0.32f else 0.50f) to Color.Transparent,
                                (if (hasPrayerContext) 0.56f else 0.72f) to
                                    Color(0xFF241D19).copy(alpha = 0.12f),
                                1f to Color(0xFF241D19).copy(
                                    alpha = if (hasPrayerContext) 0.78f else 0.66f,
                                ),
                            ),
                        ),
                    ),
            )

            val isReadingHeader = readingSurahIndex != null && onReadingPlayPause != null
            val useCompactReadingHeader = isReadingHeader &&
                (compactProgress >= 0.08f || maxWidth < 240.dp)
            val displayLabel = if (useCompactReadingHeader) "Reading" else label
            val showHeaderActions = compactProgress < 0.5f &&
                (onClick != null || onReadingPlayPause != null || directionalHintBearing != null || onPrayerUndo != null)

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(tagInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // This tag sits in the top strip, above where the scrim gradient
                // starts, so it floats on the raw photo. Theme on-surface colors
                // have no surface to sit on there; match the header actions to
                // the right instead — dark scrim pill, white label.
                Surface(
                    onClick = onClick ?: {},
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.34f),
                    shadowElevation = 0.dp,
                    // TextButton, which this replaced, applied these minimums and
                    // this content padding. Keep them so the pill holds its old
                    // size and stays aligned with the 40dp actions to the right.
                    modifier = Modifier.defaultMinSize(minWidth = 58.dp, minHeight = 40.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = labelFontSize,
                                letterSpacing = 0.sp,
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }

                if (showHeaderActions) {
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isReadingHeader) 4.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (undoPrayerName != null && onPrayerUndo != null) {
                        Surface(
                            onClick = onPrayerUndo,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 0.dp,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "$undoPrayerName changed. Undo"
                            },
                        ) {
                            Text(
                                text = "Undo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }

                    if (readingSurahIndex != null && onReadingPlayPause != null) {
                        val isCurrentReading = readingPlayback.surahIndex == readingSurahIndex
                        Surface(
                            onClick = if (readingPlayback.error != null) {
                                onReadingRetry ?: onReadingPlayPause
                            } else {
                                onReadingPlayPause
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.42f),
                            shadowElevation = 0.dp,
                            modifier = Modifier.size(if (useCompactReadingHeader) 36.dp else 40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when {
                                    readingPlayback.isDownloading && isCurrentReading -> {
                                        CircularProgressIndicator(
                                            progress = { readingPlayback.downloadProgress.coerceIn(0.02f, 1f) },
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(if (useCompactReadingHeader) 20.dp else 22.dp),
                                        )
                                    }

                                    readingPlayback.isLoading && isCurrentReading -> {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(if (useCompactReadingHeader) 20.dp else 22.dp),
                                        )
                                    }

                                    readingPlayback.error != null && isCurrentReading -> {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry daily Surah audio",
                                            tint = Color.White,
                                            modifier = Modifier.size(if (useCompactReadingHeader) 18.dp else 20.dp),
                                        )
                                    }

                                    readingPlayback.isPlaying && isCurrentReading -> {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pause daily Surah",
                                            tint = Color.White,
                                            modifier = Modifier.size(if (useCompactReadingHeader) 18.dp else 20.dp),
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Listen to daily Surah",
                                            tint = Color.White,
                                            modifier = Modifier.size(if (useCompactReadingHeader) 19.dp else 21.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (directionalHintBearing != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.38f),
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    val pulse = if (isFocused) {
                                        ambientMotion * 0.035f
                                    } else {
                                        0f
                                    }
                                    scaleX = 1f + pulse
                                    scaleY = 1f + pulse
                                },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = if (deviceHeadingDegrees != null) {
                                        "Live Qibla direction"
                                    } else {
                                        "Bearing $directionalHintBearing degrees from north"
                                    },
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(animatedDirectionalRotation),
                                )
                            }
                        }
                    // The reading card itself is the standard "Read" target. Keeping
                    // a second text button here crowded the header and wrapped during
                    // pull-to-sync, so reading exposes only its distinct Play action.
                    } else if (onClick != null && !isReadingHeader) {
                        Surface(
                            modifier = (if (actionLabel != null) {
                                Modifier.height(40.dp)
                            } else {
                                Modifier.size(40.dp)
                                })
                                .graphicsLayer {
                                    val pulse = if (isFocused) {
                                        ((ambientMotion + 1f) * 0.5f) * 0.025f
                                    } else {
                                        0f
                                    }
                                    scaleX = 1f + pulse
                                    scaleY = 1f + pulse
                                },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.34f),
                            shadowElevation = 0.dp,
                        ) {
                            Row(
                                modifier = if (actionLabel != null) {
                                    Modifier.padding(
                                        start = if (isReadingHeader) 8.dp else 11.dp,
                                        end = if (isReadingHeader) 4.dp else 7.dp,
                                    )
                                } else {
                                    Modifier
                                },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                actionLabel?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = actionDescription,
                                    tint = Color.White,
                                    modifier = Modifier.size(
                                        if (isReadingHeader) 16.dp else if (actionLabel != null) 18.dp else 20.dp,
                                    ),
                                )
                            }
                        }
                    }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = titleHorizontalPadding,
                        end = titleHorizontalPadding,
                        bottom = titleBottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
            ) {
                if (prayerRecap.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        prayerRecap.forEach { prayer ->
                            Box(
                                modifier = Modifier
                                    // Divide the available row evenly instead of
                                    // reserving five fixed 40dp slots. The fixed
                                    // widths overflowed when pull-to-sync narrowed
                                    // the card and visually squeezed the indicators.
                                    .weight(1f)
                                    .height(42.dp)
                                    .semantics {
                                        contentDescription = when {
                                            prayer.isPrayed ->
                                                "${prayer.name} completed. Tap to unmark"
                                            prayer.isAvailable ->
                                                "${prayer.name} not completed. Tap to mark"
                                            else ->
                                                "${prayer.name} prayer time has not arrived"
                                        }
                                        selected = prayer.isPrayed
                                    }
                                    .then(
                                        if (
                                            onPrayerToggle != null &&
                                            (prayer.isAvailable || prayer.isPrayed)
                                        ) {
                                            Modifier.clickable { onPrayerToggle(prayer.name) }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(recapIndicatorSize)
                                            .background(
                                                color = when {
                                                    prayer.isPrayed -> MaterialTheme.colorScheme.primary
                                                    prayer.isAvailable -> Color.Black.copy(alpha = 0.28f)
                                                    else -> Color.Black.copy(alpha = 0.14f)
                                                },
                                                shape = CircleShape,
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = when {
                                                    prayer.isPrayed ->
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                                    prayer.isAvailable -> Color.White.copy(alpha = 0.48f)
                                                    else -> Color.White.copy(alpha = 0.24f)
                                                },
                                                shape = CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (prayer.isPrayed) "✓" else prayer.initial,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = recapFontSize,
                                                lineHeight = recapFontSize,
                                                platformStyle = PlatformTextStyle(
                                                    includeFontPadding = false,
                                                ),
                                            ),
                                            color = when {
                                                prayer.isPrayed -> MaterialTheme.colorScheme.onPrimary
                                                prayer.isAvailable -> Color.White.copy(alpha = 0.78f)
                                                else -> Color.White.copy(alpha = 0.38f)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                        )
                                    }
                                    Text(
                                        text = prayer.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = (8.5f - (1.5f * compactProgress)).sp,
                                            lineHeight = (9.5f - compactProgress).sp,
                                            letterSpacing = 0.sp,
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false,
                                            ),
                                        ),
                                        color = Color.White.copy(
                                            alpha = if (prayer.isAvailable || prayer.isPrayed) {
                                                0.72f
                                            } else {
                                                0.40f
                                            },
                                        ),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (hasPrayerContext) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )

                supportingText?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = supportingFontSize,
                            lineHeight = supportingLineHeight,
                            letterSpacing = 0.sp,
                        ),
                        color = Color.White.copy(alpha = 0.88f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                timelineProgress?.let { progress ->
                    // Material 3 Expressive linear indicator: rounded caps on both the
                    // active segment and the track, separated by gapSize, with the stop
                    // indicator at the end. Previously a hand-rolled track and fill —
                    // square-ended, no gap — which predates gapSize landing in Material3.
                    // The home-screen widget draws the same shape from primitives, since
                    // RemoteViews cannot host this component.
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = heroAccent.copy(alpha = 0.92f),
                        trackColor = Color.White.copy(alpha = 0.22f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 4.dp,
                    )
                }

                statusText?.takeIf { it.isNotBlank() }?.let { text ->
                    val statusDetail = text
                        .substringAfter('•', text)
                        .removePrefix("Next")
                        .trimStart(' ', '·', '•')
                    val nextPrayerName = statusDetail.substringBefore(" in ").trim()
                    val nextPrayerCountdown = statusDetail
                        .substringAfter(" in ", missingDelimiterValue = "")
                        .trim()
                    val forecastVisuals = prayerWeatherVisuals(statusMetaText)
                    var forecastVisualIndex by remember(statusMetaText) {
                        mutableIntStateOf(0)
                    }
                    LaunchedEffect(forecastVisuals) {
                        forecastVisualIndex = 0
                        if (forecastVisuals.size > 1) {
                            while (true) {
                                delay(4_500L)
                                forecastVisualIndex =
                                    (forecastVisualIndex + 1) % forecastVisuals.size
                            }
                        }
                    }
                    val forecastVisual = forecastVisuals.getOrNull(forecastVisualIndex)
                    val forecastLevel = forecastVisual?.let { visual ->
                        prayerWeatherThresholdLevel(
                            summary = statusMetaText,
                            thresholds = weatherThresholds,
                            visual = visual,
                        )
                    }
                    val displayedForecastMetrics = forecastVisual?.let { visual ->
                        formatPrayerForecastMetric(statusMetaText, visual)
                    }
                    val forecastPresentation = if (
                        forecastVisual != null &&
                        forecastLevel != null &&
                        displayedForecastMetrics != null
                    ) {
                        Triple(forecastVisual, forecastLevel, displayedForecastMetrics)
                    } else {
                        null
                    }
                    // A card sized for a short viewport leaves the metric column
                    // about 39dp, and "55%" behind a 16dp icon needs 44dp, so the
                    // value alone was clipping to "5...". Shift a little of the
                    // row to the metric and trim its icon at that size.
                    val narrowFooter = cardMaxWidth < 200.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(
                            modifier = Modifier.weight(if (narrowFooter) 1.42f else 1.48f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Next Prayer",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = (11f - compactProgress).sp,
                                    lineHeight = (13f - compactProgress).sp,
                                    letterSpacing = 0.7.sp,
                                ),
                                color = heroAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    ) {
                                        append(nextPrayerName)
                                    }
                                    if (nextPrayerCountdown.isNotBlank()) {
                                        withStyle(
                                            SpanStyle(
                                                color = Color.White.copy(alpha = 0.82f),
                                                fontWeight = FontWeight.Medium,
                                            ),
                                        ) {
                                            append(" in ")
                                        }
                                        withStyle(
                                            SpanStyle(
                                                color = heroAccent,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        ) {
                                            append(nextPrayerCountdown)
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = if (cardMaxWidth < 240.dp) {
                                        13.25.sp
                                    } else {
                                        (15.5f - (2.5f * compactProgress)).sp
                                    },
                                    lineHeight = (19f - (2f * compactProgress)).sp,
                                    letterSpacing = 0.sp,
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (forecastPresentation != null) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height((footerLineHeight.value + 9f).dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.22f to Color.White.copy(alpha = 0.10f),
                                            0.5f to Color.White.copy(alpha = 0.38f),
                                            0.78f to Color.White.copy(alpha = 0.10f),
                                            1f to Color.Transparent,
                                        ),
                                        shape = RoundedCornerShape(50),
                                    ),
                            )
                            Column(
                                modifier = Modifier.weight(if (narrowFooter) 0.58f else 0.52f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    text = "Forecast",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = (9.5f - (0.5f * compactProgress)).sp,
                                        lineHeight = (12f - compactProgress).sp,
                                        letterSpacing = 0.3.sp,
                                    ),
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                AnimatedContent(
                                    targetState = forecastPresentation,
                                    transitionSpec = {
                                        // Use the same compact fade-and-scale morph as the
                                        // prayer-tile notification/weather icon.
                                        (fadeIn(tween(220)) + scaleIn(
                                            initialScale = 0.72f,
                                            animationSpec = tween(
                                                260,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )) togetherWith
                                            (fadeOut(tween(150)) + scaleOut(
                                                targetScale = 0.78f,
                                                animationSpec = tween(190),
                                            ))
                                    },
                                    contentAlignment = Alignment.CenterEnd,
                                    // A stable slot prevents the next-prayer column
                                    // from moving as values of different widths fade.
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "prayerNowForecastMorph",
                                ) { (visual, level, metric) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            space = 2.dp,
                                            alignment = Alignment.End,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AnimatedPrayerWeatherIcon(
                                            visual = visual,
                                            level = level,
                                            preferFlat = true,
                                            animationSpeed = 1f,
                                            paletteColorOverride = heroAccent,
                                            // Humidity's authored flat artwork uses dark
                                            // source layers. A hue-only filter preserves
                                            // that darkness and loses the glyph against
                                            // Prayer Now's night scrim; thermometer/rain
                                            // retain their authored shading normally.
                                            useSolidTint = visual == PrayerWeatherVisual.Humidity,
                                            modifier = Modifier.size(if (narrowFooter) 17.dp else 20.dp),
                                        )
                                        Text(
                                            text = metric,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = (12.5f - compactProgress).sp,
                                                lineHeight = (14f - compactProgress).sp,
                                                letterSpacing = 0.sp,
                                            ),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                footerText
                    ?.takeIf {
                        it.isNotBlank() &&
                            // The recap explanation is optional context. Reclaim its
                            // line while pull-to-sync compacts Today's Salah, keeping
                            // the prayer indicators and remaining-count legible.
                            !(prayerRecap.isNotEmpty() && compactProgress >= 0.08f)
                    }
                    ?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = footerFontSize,
                            lineHeight = footerLineHeight,
                            letterSpacing = 0.sp,
                        ),
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * How far the clock has travelled between the last prayer and the next, 0..1.
 *
 * Internal rather than private because the home-screen widget draws the same bar, and
 * a second copy of this arithmetic would be free to drift out of step with the tile.
 */
internal fun prayerWindowProgress(
    prayerTimes: DayPrayerTimes?,
    currentTime: LocalTime,
): Float? {
    val times = prayerTimes?.let {
        listOf(it.fajr, it.sunrise, it.dhuhr, it.asr, it.maghrib, it.isha)
    } ?: return null
    val nowMinute = currentTime.hour * 60 + currentTime.minute
    val scheduleMinutes = times
        .map { it.hour * 60 + it.minute }
        .sorted()
    val previous = scheduleMinutes.lastOrNull { it <= nowMinute }
        ?: scheduleMinutes.last() - (24 * 60)
    val next = scheduleMinutes.firstOrNull { it > nowMinute }
        ?: scheduleMinutes.first() + (24 * 60)
    if (next <= previous) return null
    return ((nowMinute - previous).toFloat() / (next - previous).toFloat())
        .coerceIn(0f, 1f)
}

private fun qiblaCardinalDirection(bearing: Int): String {
    val directions = listOf(
        "North", "Northeast", "East", "Southeast",
        "South", "Southwest", "West", "Northwest",
    )
    val normalized = ((bearing % 360) + 360) % 360
    val index = ((normalized + 22.5) / 45.0).toInt() % directions.size
    return "${directions[index]} from your location"
}

private fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

private fun shortestAngleDelta(from: Float, to: Float): Float =
    normalizeDegrees(to - from + 180f) - 180f

/** Reads a smoothed true-north heading only while the Qibla preview is visible. */
@Composable
private fun rememberLiveCompassHeading(
    enabled: Boolean,
    latitude: Double?,
    longitude: Double?,
): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    val magneticDeclination = remember(latitude, longitude) {
        if (latitude != null && longitude != null) {
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                0f,
                System.currentTimeMillis(),
            ).declination
        } else {
            0f
        }
    }

    DisposableEffect(context, enabled, magneticDeclination) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            return@DisposableEffect onDispose { }
        }

        val orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var lastPublishedHeading = heading
        var lastUpdateAt = 0L

        fun publishHeading(rawMagneticHeading: Float) {
            val now = System.currentTimeMillis()
            if (now - lastUpdateAt < 50L) return

            val correctedHeading = normalizeDegrees(rawMagneticHeading + magneticDeclination)
            val previous = lastPublishedHeading
            val nextHeading = if (previous == null) {
                correctedHeading
            } else {
                normalizeDegrees(previous + shortestAngleDelta(previous, correctedHeading) * 0.24f)
            }
            if (previous == null || kotlin.math.abs(shortestAngleDelta(previous, nextHeading)) >= 0.35f) {
                lastPublishedHeading = nextHeading
                heading = nextHeading
            }
            lastUpdateAt = now
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ORIENTATION -> publishHeading(event.values[0])

                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        publishHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values.copyInto(gravity, endIndex = 3)
                        hasGravity = true
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        event.values.copyInto(geomagnetic, endIndex = 3)
                        hasGeomagnetic = true
                    }
                }

                if (orientationSensor == null && rotationVectorSensor == null && hasGravity && hasGeomagnetic &&
                    SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                ) {
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    publishHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        when {
            orientationSensor != null -> sensorManager.registerListener(
                listener,
                orientationSensor,
                SensorManager.SENSOR_DELAY_UI,
            )

            rotationVectorSensor != null -> sensorManager.registerListener(
                listener,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_UI,
            )

            else -> {
                accelerometer?.let {
                    sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                }
                magnetometer?.let {
                    sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return heading
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun FullWidthSwipeableBigTiles(
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
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    goToMosqueDurationMinutes: (String) -> Int = { 20 },
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val currentTile by remember { derivedStateOf { pagerState.currentPage } }
    val settledTile by remember { derivedStateOf { pagerState.settledPage } }
    val globeLive by remember {
        derivedStateOf { settledTile == 3 && !pagerState.isScrollInProgress }
    }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val isDarkTheme = LocalDarkTheme.current
    val overlayDensity = LocalDensity.current
    var showGlobePopup by rememberSaveable { mutableStateOf(false) }
    var carouselHostInRoot by remember { mutableStateOf(Offset.Zero) }
    var globeOverlayRect by remember { mutableStateOf<Rect?>(null) }
    var hasActivityPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasActivityPermission = granted
    }

    LaunchedEffect(settledTile) {
        val smartTileFocused = settledTile == 1
        com.starception.submission.util.ActivityTracker.setSmartActivityTileInFocus(smartTileFocused)
        if (smartTileFocused) {
            if (!hasActivityPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            com.starception.submission.util.ActivityTracker.initialize(
                context,
                startDetectionNow = true,
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            com.starception.submission.util.ActivityTracker.setSmartActivityTileInFocus(false)
        }
    }

    val cardShape = RoundedCornerShape(32.dp)
    Box(
        modifier = (if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .onGloballyPositioned { carouselHostInRoot = it.positionInRoot() },
    ) {
        Column(
            modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val pagerModifier = if (isLandscape) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(253.dp)
            }

            HorizontalPager(
                state = pagerState,
                modifier = pagerModifier,
                contentPadding = PaddingValues(vertical = 4.dp),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                var itemInRoot by remember(page) { mutableStateOf(Offset.Zero) }
                var itemSize by remember(page) {
                    mutableStateOf(androidx.compose.ui.unit.IntSize(0, 0))
                }
                val pageDistance by remember {
                    derivedStateOf {
                        kotlin.math.abs(
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
                        ).coerceIn(0f, 1f)
                    }
                }

                LaunchedEffect(
                    page,
                    settledTile,
                    pagerState.isScrollInProgress,
                    itemInRoot,
                    itemSize,
                    carouselHostInRoot,
                ) {
                    if (
                        page == 3 &&
                        settledTile == page &&
                        !pagerState.isScrollInProgress &&
                        itemSize.width > 0 &&
                        itemSize.height > 0
                    ) {
                        val localItem = itemInRoot - carouselHostInRoot
                        globeOverlayRect = Rect(
                            left = localItem.x,
                            top = localItem.y,
                            right = localItem.x + itemSize.width,
                            bottom = localItem.y + itemSize.height,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val focus = 1f - pageDistance
                            scaleX = lerp(0.985f, 1f, focus)
                            scaleY = lerp(0.985f, 1f, focus)
                            alpha = lerp(0.86f, 1f, focus)
                        }
                        .shadow(3.dp, cardShape, clip = false)
                        .clip(cardShape)
                        .onGloballyPositioned { coordinates ->
                            if (page == 3) {
                                itemInRoot = coordinates.positionInRoot()
                                itemSize = coordinates.size
                            }
                        }
                        .background(
                            if (page == 3 && globeLive) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ),
                ) {
                    when (page) {
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
                            isLandscape = isLandscape,
                            isCarouselScrolling = pagerState.isScrollInProgress,
                            isActiveTile = settledTile == 0,
                            goToMosqueDurationMinutes = goToMosqueDurationMinutes,
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
                            isLandscape = isLandscape,
                        )

                        2 -> DailyStatsTile(
                            getPrayerProgress = getPrayerProgress,
                            getDailyStatsTitle = getDailyStatsTitle,
                            getDailyStatsMessage = getDailyStatsMessage,
                            getPrayed = getPrayed,
                            isLandscape = isLandscape,
                            onSurahClick = onSurahClick,
                            onSurahClickWithAyah = onSurahClickWithAyah,
                        )

                        // A SurfaceView cannot safely travel inside a moving pager page.
                        // The single live globe is hoisted into
                        // the fixed overlay below; this moving card is only its backdrop.
                        3 -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (globeLive) Color.Transparent else Color(0xFF070B10),
                                ),
                        ) {
                            if (globeLive) {
                                IconButton(
                                    onClick = { showGlobePopup = true },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                        .size(40.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            shape = CircleShape,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Fullscreen,
                                        contentDescription = "Open fullscreen globe",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 12.dp else 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    val selected = currentTile == index
                    Box(
                        modifier = Modifier
                            .height(5.dp)
                            .width(if (selected) 20.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                },
                            )
                            .clickable {
                                if (index != currentTile) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                            },
                    )
                    if (index < 3) Spacer(modifier = Modifier.width(6.dp))
                }
            }

        }

        // Keep one fixed, warmed GL surface alive after the globe tile is first visited.
        // It never participates in carousel transforms, eliminating SurfaceView ghosting,
        // neighbor bleed and EGL recreation on every swipe.
        globeOverlayRect?.let { bounds ->
            val overlayWidth = with(overlayDensity) { bounds.width.toDp() }
            val overlayHeight = with(overlayDensity) { bounds.height.toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = bounds.left.toInt(),
                            y = bounds.top.toInt(),
                        )
                    }
                    .requiredSize(overlayWidth, overlayHeight)
                    .clip(cardShape)
                    .alpha(if (globeLive) 1f else 0f)
                    .zIndex(-1f),
            ) {
                QiblaGlobeTile(
                    prayerTimes = prayerTimes,
                    isActiveTile = globeLive,
                    showFullscreenButton = false,
                )
            }
        }

        if (showGlobePopup) {
            prayerTimes?.location?.let { locationData ->
                GlobePopupScreen(
                    userLatitude = locationData.latitude,
                    userLongitude = locationData.longitude,
                    onDismiss = { showGlobePopup = false },
                )
            }
        }
    }
}

@Composable
private fun ReferenceTileIcon(
    glyph: String,
    contentDescription: String,
    accentColor: Color,
    compact: Boolean = false,
    onDarkSurface: Boolean = false,
) {
    val isDarkTheme = LocalDarkTheme.current
    val iconShape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Box(
        modifier = Modifier
            .size(if (compact) 30.dp else 36.dp)
            .background(
                color = if (onDarkSurface) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                } else if (isDarkTheme) {
                    accentColor.copy(alpha = 0.24f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                },
                shape = iconShape,
            )
            .border(
                width = 1.dp,
                color = if (onDarkSurface) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                } else {
                    accentColor.copy(alpha = 0.26f)
                },
                shape = iconShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FlaticonIcon(
            glyph = glyph,
            contentDescription = contentDescription,
            tint = if (onDarkSurface || isDarkTheme) Color.White else accentColor,
            fontSize = if (compact) 15.sp else 18.sp,
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun LegacySwipeableBigTilesDeck(
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
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    goToMosqueDurationMinutes: (String) -> Int = { 20 },
) {
    // Swipeable Big Tiles — vertical card DECK with the 4 tiles and infinite
    // cycling. Swipe up to toss the front card to the back of the deck; swipe
    // down to bring the previous card back onto the front. Tile visuals unchanged.
    var currentTile by remember { mutableStateOf(0) }
    var deckSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    // The GL globe (tile 3) shows/hides its surface when it enters/leaves the
    // front slot (a SurfaceView ignores the deck's transforms, so it can't be
    // visible mid-flight). The toggle is DEFERRED until the deck settles:
    // while a toss/wheel is animating the globe stays hidden, and a wheel
    // merely passing through tile 3 never touches it.
    var deckAnimating by remember { mutableStateOf(false) }
    // Where the current toss/wheel will land. Lets the globe surface unpark
    // the moment the deck COMMITS onto tile 3 — the exact instant motion
    // stops — instead of also waiting out the settle spring's invisible tail,
    // while a wheel merely PASSING THROUGH tile 3 keeps it parked.
    var deckTargetTile by remember { mutableStateOf(0) }
    var globeLive by remember { mutableStateOf(false) }
    LaunchedEffect(deckAnimating, currentTile, deckTargetTile) {
        globeLive = currentTile == 3 && (!deckAnimating || deckTargetTile == 3)
    }
    // The one in-flight deck animation (wheel spin, advance, spring home).
    // Every gesture/tap that moves the deck cancels this job first, so a new
    // flick never fights a wheel that is still spinning — it takes over from
    // wherever the cards are.
    var deckJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // True while a finger is scrubbing the deck — gates the self-healer so it
    // never commits a card out from under an active drag.
    var deckDragging by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Coroutine scope for animated scroll when dots are tapped
    val coroutineScope = rememberCoroutineScope()

    // Globe fullscreen popup state
    var showGlobePopup by rememberSaveable { mutableStateOf(false) }

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
    // currentTile only changes once a toss settles, so this fires post-swipe
    LaunchedEffect(currentTile) {
        val actualPage = currentTile

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
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 2.dp),
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
                // Card (~211dp) + 10.dp shadow clearance top & bottom; trimmed
                // 2dp so the home column's location card clears the floating
                // nav pill comfortably.
                .height(231.dp)
        }

        // One tile card (shadow wrapper + content), reused for every deck layer.
        // `depth` (0 front .. 3 back) shades and de-shadows the stacked cards
        // progressively so the deck reads with physical depth; it is read at
        // draw time, never at composition.
        @Composable
        fun TileCard(
            actualPage: Int,
            modifier: Modifier = Modifier,
            isActiveOverride: Boolean? = null,
            depth: () -> Float = { 0f },
            elevated: Boolean = true,
        ) {
            // The card floats on an EVEN drop shadow (equal on all four edges). The
            // platform elevation shadow (Surface.shadowElevation) casts DOWNWARD, so
            // it pools a heavy dark band at the bottom and almost nothing at the top —
            // making the top/bottom edges look inconsistent with the left/right. To
            // avoid that we draw our OWN symmetric shadow: a shadow-tinted rounded box
            // sized to the card, uniformly blurred, sitting directly behind it (no
            // vertical offset) so the soft halo is identical top/bottom/left/right.
            // 10.dp inset gives the blur room to render fully rounded on every side.
            // Behind-cards are covered with a clean face in the tile's OWN
            // container color (matching each tile's real background) — raw
            // content would peek as a busy/dark lip, but a generic surface
            // scrim made every card in the stack look white.
            val scrimColor = when (actualPage) {
                0 -> MaterialTheme.colorScheme.primaryContainer
                1 -> MaterialTheme.colorScheme.secondaryContainer
                2 -> MaterialTheme.colorScheme.tertiaryContainer
                // The globe card is a space scene — its face is space-black,
                // so the reveal never flashes a light frame around the globe.
                else -> Color(0xFF070B10)
            }
            val evenShadowColor = Color.Black.copy(alpha = 0.16f)
            Box(modifier = modifier.fillMaxSize().padding(10.dp)) {
                if (elevated) {
                    // Inset the shadow box a few dp INSIDE the card so the card's
                    // opaque surface fully covers the shadow's solid core. Otherwise
                    // the un-blurred edge of the tint box shows as a hard dark seam
                    // right at the card's straight edges (the corners hid it, the
                    // straight sides didn't). With the inset, only the soft blurred
                    // halo bleeds out past the card — even on all sides.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(4.dp)
                            .blur(8.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                            .background(evenShadowColor, RoundedCornerShape(28.dp)),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                  Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            // Behind-cards in the fan show a clean face in their own
                            // container color instead of their real content (so the
                            // black globe card, etc. never peeks as a harsh dark lip).
                            // Ramps in across the first depth unit so a card promoting
                            // to front reveals its real face smoothly.
                            val cover = depth().coerceIn(0f, 1f)
                            // Fully covered cards skip their content ENTIRELY —
                            // re-recording four full tile draw trees on every
                            // animation frame was the deck's main per-frame cost
                            // (13-25ms UI-thread frames during flings).
                            if (cover < 0.999f) drawContent()
                            if (cover > 0.005f) drawRect(scrimColor.copy(alpha = cover))
                        },
                  ) {
                    // Own layer boundary: the scrim draw above re-executes on
                    // every animation frame, and without this layer each pass
                    // re-issued the tile's entire draw tree; with it, the pass
                    // just re-references the cached layer.
                    Box(Modifier.fillMaxSize().graphicsLayer()) {
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
                            isLandscape = isLandscape,
                            goToMosqueDurationMinutes = goToMosqueDurationMinutes,
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
                            onFullscreenClick = { showGlobePopup = true },
                            // Surface visible only when front AND the deck is at
                            // rest — a SurfaceView ignores the deck's transforms,
                            // so it must never punch through mid-flight.
                            isActiveTile = (isActiveOverride ?: true) && globeLive,
                        )
                    }
                    }
                  }
                }
            }
        }

        // ── CARD DECK ENGINE ────────────────────────────────────────────────
        // Stacked-cards microinteraction (Dribbble reference): the extra cards
        // peek out BELOW the front card. Swiping up lifts the front card off
        // the deck, lays it back, and tucks it in at the BACK while the cards
        // behind promote one slot — a true restack, no card ever fades out of
        // existence. Swiping down plays the exact same flight in reverse: the
        // card at the back rises behind the deck and lands on the front.
        //
        // One progress value drives every card:
        //     0 at rest      +1 forward cycle done      -1 backward cycle done
        // Drag, release-spring and commit all travel the same curve, so the
        // deck is fully scrubbable and can be caught mid-flight and reversed.
        // Every continuous value is read inside graphicsLayer/draw lambdas —
        // a moving deck redraws but never recomposes.
        val density = LocalDensity.current
        val deckProgress = remember { Animatable(0f) }

        // Device-tilt parallax — tilting the phone shifts each layer by a
        // depth-scaled amount so the deck gains real 3D depth.
        val tilt by rememberParallaxTilt()
        val parallaxFrontPx = with(density) { (if (isLandscape) 8.dp else 12.dp).toPx() }
        val parallaxBackPx = parallaxFrontPx * 0.3f
        // Vertical (pitch) parallax gets a larger throw than horizontal so tilting
        // the phone up/down gives a more pronounced, engaging 3D lift. (The stack
        // rests neutral at any hold angle, so this only shows while actively
        // tilting — no overlap at rest.)
        val parallaxFrontPxY = with(density) { (if (isLandscape) 16.dp else 24.dp).toPx() }
        val parallaxBackPxY = parallaxFrontPxY * 0.3f

        // Resting geometry: a RIGHT-TO-LEFT fan. The front card sits in place; each
        // card behind it steps to the LEFT only (no vertical offset) and tapers
        // slightly smaller, so the deck peeks out on the LEFT side while the TOP and
        // BOTTOM edges stay single & clean — matching the left/right edges instead of
        // showing stacked horizontal "ledge" borders. Reserve space on the left so
        // the fanned cards aren't clipped.
        // fanX must exceed the shadow blur radius (8.dp) so each card steps far
        // enough left to clear the card-in-front's shadow halo — otherwise the next
        // card sits flush inside that halo and occludes it, leaving the front card's
        // left edge a hard cut with no visible shadow (unlike its other edges). With
        // a step wider than the blur, every card (including the front) shows an even
        // soft shadow on its left edge, so all the left edges match.
        val fanX = if (isLandscape) 14.dp else 16.dp   // leftward step per slot (> blur radius)
        // Upward step per slot: slightly more than the bottom-anchored scale
        // taper pulls tops down (~7.5dp/slot), so each deeper card's top edge
        // peeks a couple of dp above the one in front — the stack reads as a
        // diagonal from top-left (back) to bottom-right (front).
        val fanY = 10.dp
        val fanXPx = with(density) { fanX.toPx() }
        val fanYPx = with(density) { fanY.toPx() }
        val scaleStep = 0.035f   // subtle taper — deeper cards read slightly smaller
        // Kept for the flight math below (bottom peek band no longer used at rest).
        val fanStep = 0.dp
        val fanPx = 0f
        val fanReserve = 0.dp

        // Flight tuning for the travelling card.
        val liftFraction = 0.32f   // apex height as a fraction of the deck height
        val apexScale = 0.90f      // card size at the apex
        val apexTilt = 32f         // rotationX at the apex — the card "lies back" in flight
        val flightSplit = 0.5f     // progress where the card crosses the deck (z-order flips)

        // Promotion easing — ease-in, so the deck holds its shape while the
        // travelling card lifts clear (the "spread"), then restacks briskly.
        fun promote(x: Float) = x * x

        // Finger travel equal to one full cycle; keeps the lift ~1:1 with the finger.
        fun dragDistancePx() = (deckSize.height * 0.7f).coerceAtLeast(1f)
        var pastThreshold by remember { mutableStateOf(false) }

        // Toss ONE card in `dir` (+1 forward, -1 backward) with the given spring,
        // carrying `velocity` (progress units/sec) into the flight, then commit
        // with a haptic tick as the card lands.
        val settleSpring = spring(dampingRatio = 0.85f, stiffness = 340f, visibilityThreshold = 0.001f)
        val tossOne: suspend (Int, Float, AnimationSpec<Float>) -> Unit = { dir, velocity, spec ->
            val t0 = android.os.SystemClock.uptimeMillis()
            val from = deckProgress.value
            deckProgress.animateTo(dir.toFloat(), spec, initialVelocity = velocity)
            currentTile = (currentTile + if (dir > 0) 1 else 3) % 4
            deckProgress.snapTo(0f)
            Log.d(
                "DeckFling",
                "🃏 tossOne dir=$dir vel=%.2f from=%.2f flight=%dms -> tile=$currentTile"
                    .format(velocity, from, android.os.SystemClock.uptimeMillis() - t0),
            )
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        // Single deliberate advance (dots, hint row, gentle swipes).
        val advance: suspend (Int, Float) -> Unit = { dir, velocity ->
            deckAnimating = true
            try {
                tossOne(dir, velocity, settleSpring)
            } finally {
                deckAnimating = false
            }
        }

        // ROULETTE fling — a continuous decelerating spin across every card.
        // Per-card spring flights gave the wheel a pulsing fast-slow-fast
        // rhythm (each spring decelerates into its own landing). Instead the
        // velocity is the through-line: intermediate cards fly at constant
        // speed matched to the finger, friction decays it card to card, and
        // only the last card decelerates — one spin, ticking past like a
        // roulette wheel under friction (haptic per tick).
        val flingWheel: suspend (Int, Float) -> Unit = { dir, speed ->
            // Cap at 3 — all four would spin a full loop back to the same tile.
            val cards = (1 + (kotlin.math.abs(speed) / 3f).toInt()).coerceIn(1, 3)
            deckTargetTile = (((currentTile + dir * cards) % 4) + 4) % 4
            deckAnimating = true
            Log.d("DeckFling", "🎡 wheel start: speed=%.2f cards=$cards".format(speed))
            // DEBUG frame monitor: flags any frame gap over ~2 vsyncs while the
            // wheel spins — catches heavy tiles (GL globe restack, etc.)
            // stalling the flight at card boundaries.
            val monitor = coroutineScope.launch {
                var last = androidx.compose.runtime.withFrameNanos { it }
                while (true) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    val ms = (now - last) / 1_000_000
                    if (ms > 20) Log.w("DeckFling", "🐢 SLOW FRAME ${ms}ms during wheel (tile=$currentTile)")
                    last = now
                }
            }
            try {
                // Each intermediate card flies at CONSTANT velocity, its tween
                // duration derived from the live speed — so every card enters
                // exactly as fast as the previous one left (no per-card spring
                // landings, no pulsing). Friction decays the speed card to
                // card, and the LAST card spring-settles from the remaining
                // velocity, gliding the wheel to rest.
                var vel = kotlin.math.abs(speed).coerceAtMost(8f)
                for (i in 0 until cards - 1) {
                    // Remaining distance of this cycle (first card may already
                    // be partly carried by the finger).
                    val distance = 1f - (deckProgress.value * dir).coerceIn(0f, 1f)
                    val ms = (1000f * distance / vel).toInt().coerceIn(40, 400)
                    tossOne(dir, dir * vel, tween(ms, easing = LinearEasing))
                    vel *= 0.7f
                }
                tossOne(dir, dir * vel, settleSpring)
            } finally {
                monitor.cancel()
                deckAnimating = false
                Log.d("DeckFling", "🎡 wheel done at tile=$currentTile")
            }
        }

        // SELF-HEALER: a release/tap job cancelled in the wrong window can
        // strand the deck displaced without a commit — worst case fully
        // promoted (|progress| ≈ 1), where the card the user SEES in front is
        // not the card `currentTile` says (dots point at the wrong tile and
        // the globe never activates). Whenever the deck sits idle displaced,
        // finish the toss it was on (or spring home from a small displacement).
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(300)
                if (deckDragging || deckAnimating) continue
                if (deckJob?.isActive == true || deckProgress.isRunning) continue
                val p = deckProgress.value
                if (kotlin.math.abs(p) < 0.02f) continue
                Log.w("DeckFling", "🩹 heal: deck stranded at %.2f — reconciling".format(p))
                deckJob = coroutineScope.launch {
                    if (kotlin.math.abs(p) > 0.5f) {
                        val dir = if (p > 0f) 1 else -1
                        deckTargetTile = (((currentTile + dir) % 4) + 4) % 4
                        advance(dir, 0f)
                    } else {
                        deckProgress.animateTo(0f, settleSpring)
                    }
                }
            }
        }

        Box(
            modifier = pagerModifier
                // Reserve room on the START (left) so the right-to-left fan of
                // behind-cards has space to peek left without being clipped.
                // 2.5 steps is enough: the bottom-center scale taper pulls the
                // deep cards back in, so the fan never reaches the full 3-step
                // extent even with the blur halo — the saved space goes to
                // card width.
                .padding(
                    start = fanX * 2.5f,
                    end = if (isLandscape) 4.dp else 2.dp,
                )
                .onSizeChanged { deckSize = it }
                // NOTE: no hard clip — the travelling card must rise above the
                // deck. Every card clips itself to its own rounded shape.
                // 2-AXIS TOSS: both axes scrub the deck — up OR LEFT tosses the
                // front card back (matching the leftward fan and the ‹ › hint row),
                // down or right brings the previous card forward. A gentle carry
                // settles one card on the spring; a hard flick hands off to the
                // wheel and keeps the stack shuffling, more cards for harder flicks.
                .pointerInput(Unit) {
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    // Scrub target accumulated locally: reading deckProgress.value
                    // per event lags behind the queued snapTo coroutines and makes
                    // fast scrubs feel rubbery.
                    var target = 0f
                    detectDragGestures(
                        onDragStart = {
                            // Catch the deck mid-flight: stop the wheel/settle so
                            // the finger scrubs from wherever the cards are now.
                            deckJob?.cancel()
                            deckDragging = true
                            tracker.resetTracking()
                            target = deckProgress.value
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // Up (−y) and left (−x) both advance; clamped to one
                            // cycle per gesture. A tick marks the point of no return.
                            target = (target - (dragAmount.y + dragAmount.x) / dragDistancePx())
                                .coerceIn(-1f, 1f)
                            val next = target
                            val crossed = kotlin.math.abs(next) > 0.3f
                            if (crossed && !pastThreshold) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            pastThreshold = crossed
                            coroutineScope.launch { deckProgress.snapTo(next) }
                        },
                        onDragEnd = {
                            pastThreshold = false
                            deckDragging = false
                            // Velocity in progress units/sec; up or left = forward = positive.
                            val pxPerSec = tracker.calculateVelocity()
                            val v = (-(pxPerSec.y + pxPerSec.x) / dragDistancePx()).coerceIn(-8f, 8f)
                            val here = target
                            val dir = when {
                                v > 0.8f -> 1                    // flick up/left
                                v < -0.8f -> -1                  // flick down/right
                                here > 0.25f && v > -0.4f -> 1   // carried far enough, not flicked back
                                here < -0.25f && v < 0.4f -> -1
                                else -> 0
                            }
                            Log.d(
                                "DeckFling",
                                "👆 release: px/s=(%.0f, %.0f) v=%.2f here=%.2f dir=$dir -> %s"
                                    .format(
                                        pxPerSec.x, pxPerSec.y, v, here,
                                        when {
                                            dir == 0 -> "spring home"
                                            kotlin.math.abs(v) > 0.8f -> "WHEEL"
                                            else -> "single advance"
                                        },
                                    ),
                            )
                            deckJob = coroutineScope.launch {
                                if (dir != 0) {
                                    if (kotlin.math.abs(v) > 0.8f) {
                                        // FAST FLING: harder flicks shuffle more cards.
                                        flingWheel(dir, v)
                                    } else {
                                        // Slow carry past the commit point — soft spring settle.
                                        deckTargetTile = (((currentTile + dir) % 4) + 4) % 4
                                        advance(dir, v)
                                    }
                                } else {
                                    // Not enough intent — spring home with the leftover momentum.
                                    deckProgress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f, visibilityThreshold = 0.001f),
                                        initialVelocity = v,
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            pastThreshold = false
                            deckDragging = false
                            deckJob = coroutineScope.launch { deckProgress.animateTo(0f, settleSpring) }
                        },
                    )
                }
        ) {
            // The only discrete state the deck recomposes on: which card is in
            // flight (drag direction) and which side of the deck it is on.
            val forward by remember { derivedStateOf { deckProgress.value >= 0f } }
            val flyingOnTop by remember {
                derivedStateOf {
                    val v = deckProgress.value
                    (if (v >= 0f) v else 1f + v) < flightSplit
                }
            }

            repeat(4) { position ->
                val page = (currentTile + position) % 4
                val isFlying = if (forward) position == 0 else position == 3

                // Keyed by page: when the deck order rotates on commit, Compose
                // MOVES each tile's subtree instead of rebuilding it, so tile
                // state (players, sensors, the GL globe) survives every toss.
                key(page) {
                    TileCard(
                        actualPage = page,
                        isActiveOverride = if (position == 0) null else false,
                        // Every card in the fan casts a shadow so the stack reads with
                        // real depth (they're offset up-left, so shadows don't leak as
                        // a hard line behind the front card).
                        elevated = true,
                        depth = {
                            val v = deckProgress.value.coerceIn(-1f, 1f)
                            if (isFlying) {
                                val q = if (v >= 0f) v else 1f + v
                                if (q < flightSplit) 0f
                                else 3f * FastOutSlowInEasing.transform((q - flightSplit) / (1f - flightSplit))
                            } else {
                                val slot = if (v >= 0f) position - promote(v) else position + promote(-v)
                                slot.coerceIn(0f, 3f)
                            }
                        },
                        modifier = Modifier
                            .zIndex(if (!isFlying) 6f - position else if (flyingOnTop) 10f else 2f)
                            .padding(bottom = fanReserve)
                            .graphicsLayer {
                                val v = deckProgress.value.coerceIn(-1f, 1f)
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                if (isFlying) {
                                    // FLIGHT: lift off ~1:1 with the finger, lie back
                                    // and shrink to the apex, then descend behind the
                                    // deck into the back slot. Backward runs q from 1
                                    // to 0 — the same path, time-reversed.
                                    cameraDistance = 20f * this.density
                                    val q = if (v >= 0f) v else 1f + v
                                    val lift = deckSize.height * liftFraction
                                    if (q < flightSplit) {
                                        val t = q / flightSplit
                                        translationX = tilt.x * parallaxFrontPx
                                        translationY = -lift * t + tilt.y * parallaxFrontPxY
                                        val s = 1f - (1f - apexScale) * t
                                        scaleX = s; scaleY = s
                                        rotationX = apexTilt * t
                                    } else {
                                        val t = FastOutSlowInEasing.transform((q - flightSplit) / (1f - flightSplit))
                                        val par = lerp(parallaxFrontPx, parallaxBackPx, t)
                                        val parY = lerp(parallaxFrontPxY, parallaxBackPxY, t)
                                        // Descend into the deepest fan slot (slot 3):
                                        // up-and-left, matching the resting fan.
                                        translationX = lerp(0f, -fanXPx * 3f, t) + tilt.x * par
                                        translationY = lerp(-lift, -fanYPx * 3f, t) + tilt.y * parY
                                        val s = lerp(apexScale, 1f - scaleStep * 3f, t)
                                        scaleX = s; scaleY = s
                                        rotationX = apexTilt * (1f - t)
                                    }
                                } else {
                                    // STACK: diagonal right-to-left fan. `slot` runs 0
                                    // (front) .. 3 (deepest); each step shifts the card
                                    // up-and-to-the-left and tapers it smaller, so the
                                    // deck opens toward the top-left like a fanned hand.
                                    val slot = (if (v >= 0f) position - promote(v) else position + promote(-v))
                                        .coerceIn(0f, 3f)
                                    val par = lerp(parallaxFrontPx, parallaxBackPx, slot / 3f)
                                    val parY = lerp(parallaxFrontPxY, parallaxBackPxY, slot / 3f)
                                    translationX = -fanXPx * slot + tilt.x * par
                                    translationY = -fanYPx * slot + tilt.y * parY
                                    val s = 1f - scaleStep * slot
                                    scaleX = s; scaleY = s
                                }
                            },
                    )
                }
            }
        }

        // Page indicators for swipeable tiles - compact in landscape, CLICKABLE & SWIPEABLE to navigate
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
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                deckJob?.cancel()
                                deckTargetTile = (((currentTile + if (totalDrag < 0) 1 else -1) % 4) + 4) % 4
                                deckJob = coroutineScope.launch {
                                    advance(if (totalDrag < 0) 1 else -1, 0f)
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
            repeat(4) { index ->
                val isSelected = currentTile == index
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
                            // Haptic feedback on tap
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                            // Tap on the tile that's already front with the deck
                            // at rest: nothing to do — and cancelling deckJob here
                            // could kill another toss's pending commit.
                            if (index == currentTile && kotlin.math.abs(deckProgress.value) < 0.02f) {
                                return@clickable
                            }

                            // Toss the deck onto the tapped tile via the shortest
                            // path, one card at a time.
                            deckJob?.cancel()
                            deckTargetTile = index
                            deckJob = coroutineScope.launch {
                                var guard = 0
                                while (currentTile != index && guard < 4) {
                                    val fwd = ((index - currentTile) + 4) % 4
                                    advance(if (fwd <= 2) 1 else -1, 0f)
                                    guard++
                                }
                            }
                        }
                )
                if (index < 3) {
                    Spacer(modifier = Modifier.width(if (isLandscape) 4.dp else 8.dp))
                }
            }
        }

        // Professional swipe hint - hidden in landscape to maximize tile space, SWIPEABLE to navigate
        if (!isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // Swipe gesture on the hint row
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            // Swipe threshold: 50 pixels
                            if (kotlin.math.abs(totalDrag) > 50) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                deckJob?.cancel()
                                deckTargetTile = (((currentTile + if (totalDrag < 0) 1 else -1) % 4) + 4) % 4
                                deckJob = coroutineScope.launch {
                                    advance(if (totalDrag < 0) 1 else -1, 0f)
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
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Swipe for more insights",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Swipe right",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
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
    isLandscape: Boolean = false,
    isCarouselScrolling: Boolean = false,
    isActiveTile: Boolean = true,
    goToMosqueDurationMinutes: (String) -> Int = { 20 },
) {
    val view = LocalView.current
    val isDarkTheme = LocalDarkTheme.current
    val tileInk = MaterialTheme.colorScheme.onSurface
    val mainPrayer = getNextPrayer() ?: getCurrentPrayer()
    // Show prayer tile if we have prayer data, even if mainPrayer logic fails
    if (mainPrayer != null || prayerTimes != null) {
        // Match the deck's outer card exactly: 32dp corners, NO border. The inner
        // tile previously used 22dp + a 1.5dp border, which showed as a darker rim
        // and a mismatched corner against the 32dp outer card. Keep only the colored
        // fill + tonal elevation.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = predictionHeroBrush(),
                    shape = RoundedCornerShape(32.dp),
                ),
            shape = RoundedCornerShape(32.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            // Shared compass interaction state
            var isPressed by remember { mutableStateOf(false) }
            val compassScale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "compassPressScale"
            )
            val compassSizeFallback = if (isLandscape) 140.dp else 148.dp

            // Shared sync content
            val syncContent = remember(prayerTimes, currentTime, timeOffsets, goToMosqueDurationMinutes) {
                SmartContentUtils.getNotificationSyncContent(prayerTimes, currentTime, timeOffsets, goToMosqueDurationMinutes)
            }

            // Shared compass composable
            @Composable
            fun CompassGlobe(modifier: Modifier = Modifier) {
                Box(
                    modifier = Modifier
                        .then(
                            // Portrait: a larger globe that bleeds slightly past the row
                            // height into the tile padding, leaving the text column
                            // narrower (it wraps to more lines).
                            if (isLandscape) Modifier.fillMaxHeight(0.85f).aspectRatio(1f)
                            else Modifier.requiredSize(148.dp)
                        )
                        .offset(x = if (isLandscape) 0.dp else 12.dp)
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxSize()
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
                            size = compassSizeFallback,
                            locationService = locationService,
                            userLatitude = prayerTimes?.location?.latitude ?: 0.0,
                            userLongitude = prayerTimes?.location?.longitude ?: 0.0,
                            showGlobe = isActiveTile && !isCarouselScrolling
                        )
                    }
                }
            }

            // Shared text content composable
            @Composable
            fun PrayerTextContent() {
                if (syncContent != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
                    ) {
                        Text(
                            text = syncContent.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = tileInk.copy(alpha = 0.68f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = syncContent.content,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 22.sp,
                                letterSpacing = (-0.4).sp
                            ),
                            color = tileInk,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 24.sp
                        )
                        if (syncContent.nextPrayerInfo.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(top = if (isLandscape) 2.dp else 8.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    text = "NEXT PRAYER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = syncContent.nextPrayerInfo.substringAfter('•').trim(),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 14.sp,
                                        letterSpacing = 0.sp,
                                    ),
                                    color = tileInk.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else if (mainPrayer != null) {
                    val prayerName = mainPrayer.first
                    val prayerStatus = getPrayerStatus(prayerName)
                    val prayerTime = getPrayerTimeDisplay(prayerName)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Next Prayer: ${getPrayerDisplayName(prayerName)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = tileInk.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$prayerStatus • $prayerTime",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, letterSpacing = (-0.4).sp),
                            color = tileInk,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp
                        )
                    }
                } else if (prayerTimes != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Next Prayer: Fajr",
                            style = MaterialTheme.typography.titleSmall,
                            color = tileInk.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tomorrow • ${getPrayerTimeDisplay("Fajr")}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, letterSpacing = (-0.4).sp),
                            color = tileInk,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp
                        )
                    }
                }
            }

            if (isLandscape) {
                // LANDSCAPE: Column(SpaceBetween) matching Smart Tracking layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header at top
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ReferenceTileIcon(
                            glyph = FlaticonIcons.SCHEDULE,
                            contentDescription = "Smart Prediction",
                            accentColor = MaterialTheme.colorScheme.primary,
                            compact = true,
                        )
                        Text(
                            text = "Smart Prediction",
                            style = MaterialTheme.typography.labelLarge,
                            color = tileInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Content in middle with text + compass
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PrayerTextContent()
                        }
                        CompassGlobe()
                    }
                }
            } else {
                // PORTRAIT: Column with header on top, then Row(text + compass) filling space
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, top = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ReferenceTileIcon(
                            glyph = FlaticonIcons.SCHEDULE,
                            contentDescription = "Smart Prediction",
                            accentColor = MaterialTheme.colorScheme.primary,
                            compact = true,
                        )
                        Text(
                            text = "Smart Prediction",
                            style = MaterialTheme.typography.labelLarge,
                            color = tileInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Content Row with text + compass
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            verticalArrangement = Arrangement.Top
                        ) {
                            PrayerTextContent()
                        }
                        CompassGlobe()
                    }
                }
            }
        }
    } else {
        // Fallback if no prayer data - Beautiful loading state
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(22.dp),
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
    val isDarkTheme = LocalDarkTheme.current
    val tileInk = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else HomeReferenceInk
    // Match the deck's outer card: 32dp corners, no border (see NextPrayerTile).
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(
                    brush = referenceHeroBrush(MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(32.dp),
            ),
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLandscape) 16.dp else 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            // Header: Icon + Title (matching Quran Player style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(bottom = if (isLandscape) 0.dp else 2.dp)
            ) {
                ReferenceTileIcon(
                    glyph = FlaticonIcons.SALAH_TRAINING,
                    contentDescription = "Smart Tracking",
                    accentColor = HomeReferenceGold,
                    compact = isLandscape,
                )
                Text(
                    text = "Smart Tracking",
                    style = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = tileInk,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = if (isLandscape) 8.dp else 12.dp, bottom = if (isLandscape) 4.dp else 6.dp),
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
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Prayers Done column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
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

                            // Phone Position Display (text only, no icon)
                            val phonePositionFlow = com.starception.submission.util.ActivityTracker.phonePosition
                            val phonePosition by phonePositionFlow.collectAsStateWithLifecycle()

                            // Position badge without icon
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
    onFullscreenClick: () -> Unit = {},
    isActiveTile: Boolean = true,
    showFullscreenButton: Boolean = true,
) {
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // Create backdrop for liquid glass effect
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // Match the deck's outer card: 32dp corners, no border (see NextPrayerTile).
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(32.dp),
        color = surfaceColor,
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            prayerTimes?.location?.let { locationData ->
                // ONE live globe for the lifetime of the deck. Stacking the tile
                // no longer swaps the GL view for a static box (the recreate
                // flashed black every time the card came back); instead the view
                // stays composed and QiblaGlobeView hides its surface + pauses
                // its render thread and sensors while inactive, then shows and
                // refreshes when the card lands on front.
                QiblaGlobeView(
                    userLatitude = locationData.latitude,
                    userLongitude = locationData.longitude,
                    modifier = Modifier.fillMaxSize(),
                    showControls = isActiveTile,
                    isActiveTile = isActiveTile,
                    surfaceCornerRadius = 32.dp,
                )

                // Fullscreen button in top-left corner (with liquid glass effect)
                if (isActiveTile && showFullscreenButton) Box(
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
    val isDarkTheme = LocalDarkTheme.current
    val tileInk = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else HomeReferenceInk
    val audioDownloadHelper = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AudioDownloadHelperEntryPoint::class.java
        ).audioDownloadHelper()
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

    // Match the deck's outer card: 32dp corners, no border (see NextPrayerTile).
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(
                    brush = referenceHeroBrush(MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(32.dp),
            ),
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
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

            // PLAYER VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = if (isLandscape) 8.dp else 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header row: Title + Language badge
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
                        ReferenceTileIcon(
                            glyph = FlaticonIcons.QURAN,
                            contentDescription = "Quran",
                            accentColor = HomeReferenceRust,
                            compact = isLandscape,
                        )
                        Text(
                            text = "The Noble Quran",
                            style = MaterialTheme.typography.labelLarge,
                            color = tileInk,
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

                // Surah info and playback controls - fills middle space
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
                                modifier = Modifier.size(28.dp)
                            ) {
            Text(
                                    text = "${viewModel.currentSurahIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Surah names
                        Column {
                            Text(
                                text = QuranData.surahs[viewModel.currentSurahIndex].nameArabic,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = arabicFontFamily,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1
                            )
                            Text(
                                text = QuranData.surahs[viewModel.currentSurahIndex].nameEnglish,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                maxLines = 1,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Right: Inline playback controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button
                        androidx.compose.material3.IconButton(
                            onClick = {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                viewModel.playPrevious()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(22.dp)
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
                                .size(42.dp)
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
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Next button
                        androidx.compose.material3.IconButton(
                            onClick = {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.CONTEXT_CLICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                                viewModel.playNext()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(22.dp)
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
                // Bottom section: Seek slider with time display
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

// Helper function to format time
private fun formatTime(milliseconds: Int): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}

// ── Insight page indicator ───────────────────────────────────────────────────
// Treatments for the row to the right of the "Insights" heading. The choice is a
// user setting, picked in the carousel bottom sheet and persisted; each option is
// previewed live there so it is judged by eye rather than by name.

private enum class InsightIndicatorStyle(val label: String) {
    /** Capsule dots; the active one widens and fills with the auto-advance. */
    Capsules("Capsules"),

    /** Uniform hairlines — the calmest of the four, closest to the editorial style. */
    Hairlines("Hairlines"),

    /** Position as a figure — the least decorated of the three. */
    Counter("Counter"),
}

@Composable
private fun InsightPageIndicator(
    style: InsightIndicatorStyle,
    pageCount: Int,
    currentIndex: Int,
    progress: Float,
    onSelect: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (style) {
        InsightIndicatorStyle.Capsules -> CapsuleIndicator(
            pageCount, currentIndex, progress, onSelect, onLongPress, modifier,
        )

        InsightIndicatorStyle.Hairlines -> HairlineIndicator(
            pageCount, currentIndex, progress, onSelect, onLongPress, modifier,
        )

        InsightIndicatorStyle.Counter -> CounterIndicator(
            pageCount, currentIndex, progress, onSelect, onLongPress, modifier,
        )
    }
}

/** Shared hit target: a comfortable 28dp tall row slot carrying its own gap. */
@Composable
private fun Modifier.indicatorSlot(
    index: Int,
    pageCount: Int,
    selected: Boolean,
    width: Dp,
    onSelect: (Int) -> Unit,
    onLongPress: () -> Unit,
): Modifier = this
    .size(width = width, height = 28.dp)
    .semantics {
        contentDescription = "Insight ${index + 1} of $pageCount"
        this.selected = selected
    }
    .combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onSelect(index) },
        onLongClick = onLongPress,
    )

@Composable
private fun CapsuleIndicator(
    pageCount: Int,
    currentIndex: Int,
    progress: Float,
    onSelect: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            val selected = index == currentIndex
            val width by animateDpAsState(
                targetValue = if (selected) 18.dp else 6.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "capsuleWidth",
            )
            val track by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                },
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "capsuleTrack",
            )
            Box(
                modifier = Modifier.indicatorSlot(
                    index, pageCount, selected,
                    width + INSIGHT_INDICATOR_GAP, onSelect, onLongPress,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(track),
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HairlineIndicator(
    pageCount: Int,
    currentIndex: Int,
    progress: Float,
    onSelect: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every segment keeps the same width. Nothing resizes as pages change, so the
    // row stays perfectly still and only the ink moves.
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            val selected = index == currentIndex
            val restColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
            Box(
                modifier = Modifier.indicatorSlot(
                    index, pageCount, selected, 13.dp + 5.dp, onSelect, onLongPress,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(13.dp)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(restColor),
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0.06f, 1f))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterIndicator(
    pageCount: Int,
    currentIndex: Int,
    progress: Float,
    onSelect: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No row of marks at all — the position is stated, and the auto-advance runs
    // underneath it as a hairline. Takes the least width of the three.
    Row(
        modifier = modifier
            .height(28.dp)
            .semantics {
                contentDescription = "Insight ${currentIndex + 1} of $pageCount"
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSelect((currentIndex + 1) % pageCount) },
                onLongClick = onLongPress,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${currentIndex + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
