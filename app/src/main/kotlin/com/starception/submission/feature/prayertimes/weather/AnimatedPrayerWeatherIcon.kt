package com.starception.submission.feature.prayertimes.weather

import android.provider.Settings
import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.IconCompat
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.starception.submission.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt


// Only the current-weather icon uses this, and that icon stays here: it draws
// animated vector drawables through BlendMode, neither of which crosses.
private enum class CurrentWeatherVisual {
    ClearDay,
    ClearNight,
    PartlyCloudyDay,
    PartlyCloudyNight,
    OvercastDay,
    OvercastNight,
    FogDay,
    FogNight,
    Drizzle,
    Rain,
    Snow,
    ThunderstormsDay,
    ThunderstormsNight,
    Cloudy,
}

@Composable
internal fun AnimatedPrayerWeatherIcon(
    visual: PrayerWeatherVisual,
    level: WeatherThresholdLevel = WeatherThresholdLevel.Alert,
    preferFlat: Boolean = false,
    styleOverride: MeteoconStyle? = null,
    preserveOriginalColors: Boolean = false,
    animationSpeed: Float = 0.72f,
    paletteColorOverride: ComposeColor? = null,
    modifier: Modifier = Modifier,
) {
    val useDarkPalette = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val style = styleOverride ?: when {
        preferFlat -> MeteoconStyle.Flat
        useDarkPalette -> MeteoconStyle.Flat
        else -> MeteoconStyle.Fill
    }
    val paletteColor = paletteColorOverride ?: when (visual) {
        PrayerWeatherVisual.Rain -> MaterialTheme.colorScheme.primary
        PrayerWeatherVisual.Humidity -> MaterialTheme.colorScheme.tertiary
        PrayerWeatherVisual.Heat -> MaterialTheme.colorScheme.secondary
    }
    AnimatedMeteocon(
        animationResource = visual.animationResource(level, style),
        paletteColorFilter = remember(paletteColor, preserveOriginalColors) {
            if (preserveOriginalColors) {
                null
            } else {
                themedMeteoconColorFilter(paletteColor.toArgb())
            }
        },
        animationSpeed = animationSpeed,
        modifier = modifier,
    )
}

@Composable
internal fun AnimatedCurrentWeatherIcon(
    weather: CurrentWeather,
    modifier: Modifier = Modifier,
    // Fill is glossy, near-white artwork. Because the tint below is BlendMode.COLOR,
    // which keeps the source luminosity, that style disappears against a pale
    // surface. Callers sitting on one can ask for a style that holds its own.
    styleOverride: MeteoconStyle? = null,
) {
    val useDarkPalette = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val paletteColor = MaterialTheme.colorScheme.primary
    val style = styleOverride ?: if (useDarkPalette) MeteoconStyle.Flat else MeteoconStyle.Fill
    AnimatedMeteocon(
        animationResource = weather.currentWeatherVisual().animationResource(style),
        paletteColorFilter = remember(paletteColor, style) {
            if (style == MeteoconStyle.Monochrome) {
                solidMeteoconColorFilter(paletteColor.toArgb())
            } else {
                themedMeteoconColorFilter(paletteColor.toArgb())
            }
        },
        animationSpeed = 0.72f,
        modifier = modifier,
    )
}

/**
 * Retains the authored Meteocon shading while harmonizing its hue and saturation with the
 * selected Default, Android, Coastal, Royal, or Custom app palette.
 */
private fun themedMeteoconColorFilter(paletteColor: Int): ColorFilter =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        BlendModeColorFilter(paletteColor, BlendMode.COLOR)
    } else {
        @Suppress("DEPRECATION")
        PorterDuffColorFilter(paletteColor, PorterDuff.Mode.MULTIPLY)
    }

/** Replaces monochrome artwork with the theme color so it remains legible in dark mode. */
private fun solidMeteoconColorFilter(paletteColor: Int): ColorFilter =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        BlendModeColorFilter(paletteColor, BlendMode.SRC_IN)
    } else {
        @Suppress("DEPRECATION")
        PorterDuffColorFilter(paletteColor, PorterDuff.Mode.SRC_IN)
    }

@Composable
private fun AnimatedMeteocon(
    @RawRes animationResource: Int,
    paletteColorFilter: ColorFilter?,
    animationSpeed: Float,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(animationResource),
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = animationsEnabled,
        iterations = LottieConstants.IterateForever,
        speed = animationSpeed,
        restartOnPlay = false,
    )

    if (paletteColorFilter != null) {
        val dynamicProperties = rememberLottieDynamicProperties(
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR_FILTER,
                value = paletteColorFilter,
                keyPath = arrayOf("**"),
            ),
        )
        LottieAnimation(
            composition = composition,
            progress = { if (animationsEnabled) progress else 0.45f },
            modifier = modifier,
            dynamicProperties = dynamicProperties,
            contentScale = ContentScale.Fit,
        )
    } else {
        LottieAnimation(
            composition = composition,
            progress = { if (animationsEnabled) progress else 0.45f },
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@RawRes
internal fun PrayerWeatherVisual.animationResource(
    level: WeatherThresholdLevel = WeatherThresholdLevel.Alert,
    style: MeteoconStyle = MeteoconStyle.Flat,
): Int = when (style) {
    MeteoconStyle.Monochrome -> when (this) {
        PrayerWeatherVisual.Rain -> when (level) {
            // Keep rain visually distinct from Meteocons' humidity droplet.
            WeatherThresholdLevel.Normal -> R.raw.meteocon_mono_rain
            WeatherThresholdLevel.Alert -> R.raw.meteocon_mono_rain
            WeatherThresholdLevel.Severe -> R.raw.meteocon_mono_extreme_rain
        }
        PrayerWeatherVisual.Heat -> when (level) {
            WeatherThresholdLevel.Normal -> R.raw.meteocon_mono_thermometer
            WeatherThresholdLevel.Alert -> R.raw.meteocon_mono_thermometer_warmer
            WeatherThresholdLevel.Severe -> R.raw.meteocon_mono_thermometer_sun
        }
        PrayerWeatherVisual.Humidity -> when (level) {
            WeatherThresholdLevel.Normal,
            WeatherThresholdLevel.Alert,
            WeatherThresholdLevel.Severe,
            -> R.raw.meteocon_mono_humidity
        }
    }
    MeteoconStyle.Fill -> when (this) {
        PrayerWeatherVisual.Rain -> when (level) {
            WeatherThresholdLevel.Normal -> R.raw.meteocon_fill_rain
            WeatherThresholdLevel.Alert -> R.raw.meteocon_fill_rain
            WeatherThresholdLevel.Severe -> R.raw.meteocon_fill_extreme_rain
        }
        PrayerWeatherVisual.Heat -> when (level) {
            WeatherThresholdLevel.Normal -> R.raw.meteocon_fill_thermometer
            WeatherThresholdLevel.Alert -> R.raw.meteocon_fill_thermometer_warmer
            WeatherThresholdLevel.Severe -> R.raw.meteocon_fill_thermometer_sun
        }
        PrayerWeatherVisual.Humidity -> when (level) {
            WeatherThresholdLevel.Normal,
            WeatherThresholdLevel.Alert,
            WeatherThresholdLevel.Severe,
            -> R.raw.meteocon_fill_humidity
        }
    }
    MeteoconStyle.Flat -> when (this) {
        PrayerWeatherVisual.Rain -> when (level) {
            WeatherThresholdLevel.Normal -> R.raw.meteocon_rain
            WeatherThresholdLevel.Alert -> R.raw.meteocon_rain
            WeatherThresholdLevel.Severe -> R.raw.meteocon_extreme_rain
        }
        PrayerWeatherVisual.Heat -> when (level) {
            WeatherThresholdLevel.Normal -> R.raw.meteocon_thermometer
            WeatherThresholdLevel.Alert -> R.raw.meteocon_thermometer_warmer
            WeatherThresholdLevel.Severe -> R.raw.meteocon_thermometer_sun
        }
        PrayerWeatherVisual.Humidity -> when (level) {
            // The percentage cutout stays distinct from the neighboring rain drop at
            // compact location-card sizes; threshold severity is carried by surrounding UI.
            WeatherThresholdLevel.Normal,
            WeatherThresholdLevel.Alert,
            WeatherThresholdLevel.Severe,
            -> R.raw.meteocon_humidity
        }
    }
}

private fun CurrentWeather.currentWeatherVisual(): CurrentWeatherVisual = when (weatherCode) {
    0 -> if (isDay) CurrentWeatherVisual.ClearDay else CurrentWeatherVisual.ClearNight
    1, 2 -> if (isDay) {
        CurrentWeatherVisual.PartlyCloudyDay
    } else {
        CurrentWeatherVisual.PartlyCloudyNight
    }
    3 -> if (isDay) CurrentWeatherVisual.OvercastDay else CurrentWeatherVisual.OvercastNight
    45, 48 -> if (isDay) CurrentWeatherVisual.FogDay else CurrentWeatherVisual.FogNight
    in 51..57 -> CurrentWeatherVisual.Drizzle
    in 61..67, in 80..82 -> CurrentWeatherVisual.Rain
    in 71..77, 85, 86 -> CurrentWeatherVisual.Snow
    in 95..99 -> if (isDay) {
        CurrentWeatherVisual.ThunderstormsDay
    } else {
        CurrentWeatherVisual.ThunderstormsNight
    }
    else -> CurrentWeatherVisual.Cloudy
}

@RawRes
private fun CurrentWeatherVisual.animationResource(style: MeteoconStyle): Int =
    when (style) {
        MeteoconStyle.Monochrome -> when (this) {
        CurrentWeatherVisual.ClearDay -> R.raw.meteocon_mono_clear_day
        CurrentWeatherVisual.ClearNight -> R.raw.meteocon_mono_clear_night
        CurrentWeatherVisual.PartlyCloudyDay -> R.raw.meteocon_mono_partly_cloudy_day
        CurrentWeatherVisual.PartlyCloudyNight -> R.raw.meteocon_mono_partly_cloudy_night
        CurrentWeatherVisual.OvercastDay -> R.raw.meteocon_mono_overcast_day
        CurrentWeatherVisual.OvercastNight -> R.raw.meteocon_mono_overcast_night
        CurrentWeatherVisual.FogDay -> R.raw.meteocon_mono_fog_day
        CurrentWeatherVisual.FogNight -> R.raw.meteocon_mono_fog_night
        CurrentWeatherVisual.Drizzle -> R.raw.meteocon_mono_drizzle
        CurrentWeatherVisual.Rain -> R.raw.meteocon_mono_rain
        CurrentWeatherVisual.Snow -> R.raw.meteocon_mono_snow
        CurrentWeatherVisual.ThunderstormsDay -> R.raw.meteocon_mono_thunderstorms_day
        CurrentWeatherVisual.ThunderstormsNight -> R.raw.meteocon_mono_thunderstorms_night
        CurrentWeatherVisual.Cloudy -> R.raw.meteocon_mono_cloudy
        }
        MeteoconStyle.Fill -> when (this) {
            CurrentWeatherVisual.ClearDay -> R.raw.meteocon_fill_clear_day
            CurrentWeatherVisual.ClearNight -> R.raw.meteocon_fill_clear_night
            CurrentWeatherVisual.PartlyCloudyDay -> R.raw.meteocon_fill_partly_cloudy_day
            CurrentWeatherVisual.PartlyCloudyNight -> R.raw.meteocon_fill_partly_cloudy_night
            CurrentWeatherVisual.OvercastDay -> R.raw.meteocon_fill_overcast_day
            CurrentWeatherVisual.OvercastNight -> R.raw.meteocon_fill_overcast_night
            CurrentWeatherVisual.FogDay -> R.raw.meteocon_fill_fog_day
            CurrentWeatherVisual.FogNight -> R.raw.meteocon_fill_fog_night
            CurrentWeatherVisual.Drizzle -> R.raw.meteocon_fill_drizzle
            CurrentWeatherVisual.Rain -> R.raw.meteocon_fill_rain
            CurrentWeatherVisual.Snow -> R.raw.meteocon_fill_snow
            CurrentWeatherVisual.ThunderstormsDay -> R.raw.meteocon_fill_thunderstorms_day
            CurrentWeatherVisual.ThunderstormsNight -> R.raw.meteocon_fill_thunderstorms_night
            CurrentWeatherVisual.Cloudy -> R.raw.meteocon_fill_cloudy
        }
        MeteoconStyle.Flat -> when (this) {
            CurrentWeatherVisual.ClearDay -> R.raw.meteocon_clear_day
            CurrentWeatherVisual.ClearNight -> R.raw.meteocon_clear_night
            CurrentWeatherVisual.PartlyCloudyDay -> R.raw.meteocon_partly_cloudy_day
            CurrentWeatherVisual.PartlyCloudyNight -> R.raw.meteocon_partly_cloudy_night
            CurrentWeatherVisual.OvercastDay -> R.raw.meteocon_overcast_day
            CurrentWeatherVisual.OvercastNight -> R.raw.meteocon_overcast_night
            CurrentWeatherVisual.FogDay -> R.raw.meteocon_fog_day
            CurrentWeatherVisual.FogNight -> R.raw.meteocon_fog_night
            CurrentWeatherVisual.Drizzle -> R.raw.meteocon_drizzle
            CurrentWeatherVisual.Rain -> R.raw.meteocon_rain
            CurrentWeatherVisual.Snow -> R.raw.meteocon_snow
            CurrentWeatherVisual.ThunderstormsDay -> R.raw.meteocon_thunderstorms_day
            CurrentWeatherVisual.ThunderstormsNight -> R.raw.meteocon_thunderstorms_night
            CurrentWeatherVisual.Cloudy -> R.raw.meteocon_cloudy
        }
}

private val notificationIconCache = ConcurrentHashMap<String, Bitmap>()

/**
 * System notifications cannot host a running Lottie composition. Render one exact monochrome
 * Meteocon frame and invert it to a white-on-transparent system glyph. Pixel lock-screen and
 * notification cards can use a dark surface even when the app reports a light configuration,
 * so app theme detection is not reliable for this system-owned surface.
 */
internal fun prayerWeatherNotificationBitmap(
    context: Context,
    summary: String?,
): Bitmap? {
    val visual = primaryPrayerWeatherVisual(summary) ?: return null
    val level = prayerWeatherThresholdLevel(
        summary = summary,
        thresholds = PrayerWeatherThresholdStore.load(context),
    )
    val sizePx = (48f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val cacheKey = "${visual.name}:${level.name}:monochrome-white:$sizePx"
    return notificationIconCache[cacheKey] ?: run {
        val composition = LottieCompositionFactory
            .fromRawResSync(
                context,
                visual.animationResource(level, MeteoconStyle.Monochrome),
            )
            .value
            ?: return null
        val sourceBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        LottieDrawable().apply {
            setComposition(composition)
            setBounds(0, 0, sizePx, sizePx)
            progress = 0.45f
            draw(Canvas(sourceBitmap))
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val whiteGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
        Canvas(bitmap).drawBitmap(sourceBitmap, 0f, 0f, whiteGlyphPaint)
        sourceBitmap.recycle()
        notificationIconCache.putIfAbsent(cacheKey, bitmap) ?: bitmap
    }
}

/**
 * A compiled, high-contrast Meteocon for the Live Update progress tracker.
 *
 * The notification small icon remains [R.drawable.ic_prayer], so the status chip keeps the
 * prayer identity. A white tint keeps the tracker legible on Pixel's black AOD surface.
 */
internal fun prayerWeatherNotificationTrackerIcon(
    context: Context,
    summary: String?,
): IconCompat {
    val weatherResource = prayerWeatherNotificationTrackerResource(summary)
    return IconCompat.createWithResource(context, weatherResource ?: R.drawable.ic_prayer).apply {
        if (weatherResource != null) setTint(Color.WHITE)
    }
}

@DrawableRes
private fun prayerWeatherNotificationTrackerResource(summary: String?): Int? =
    when (primaryPrayerWeatherVisual(summary)) {
        PrayerWeatherVisual.Rain -> R.drawable.ic_notif_weather_rain
        PrayerWeatherVisual.Heat -> R.drawable.ic_notif_weather_heat
        PrayerWeatherVisual.Humidity -> R.drawable.ic_notif_weather_humidity
        null -> null
    }
