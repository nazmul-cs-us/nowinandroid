package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.R
import com.starception.submission.prayer.model.DayPrayerTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The prayer anchor stays independent of the content reader and its measured height. */
@Composable
internal fun LivingDayHeader(
    day: DayPrayerTimes?,
    now: LocalTime,
    completed: Set<String>,
    onTogglePrayer: (String) -> Unit,
    onQibla: () -> Unit,
    onListen: () -> Unit,
    onSchedule: () -> Unit,
    scheduleExpanded: Boolean,
    isListening: Boolean = false,
) {
    val prayers = day?.let {
        listOf("Fajr" to it.fajr, "Dhuhr" to it.dhuhr, "Asr" to it.asr,
            "Maghrib" to it.maghrib, "Isha" to it.isha)
    }.orEmpty()
    val next = prayers.firstOrNull { it.second > now } ?: prayers.firstOrNull()
    val markable = prayers.lastOrNull { it.second <= now && it.first !in completed }
    val isNight = day?.let { now < it.sunrise || now >= it.maghrib } ?: (now.hour !in 6..17)
    val context = LocalContext.current
    val timeFormat = DateTimeFormatter.ofPattern(
        if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
    )
    fun display(name: String) = getPrayerDisplayName(name, day?.date?.toLocalDate() ?: LocalDate.now())
    val themeAccent = MaterialTheme.colorScheme.primaryContainer
    val photoAccent = if (themeAccent.luminance() >= .35f) themeAccent else Color.White

    Column {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
            Image(
                painterResource(if (isNight) R.drawable.prayer_widget_poster_night_moonless_hq_v4
                    else R.drawable.prayer_widget_poster_day_no_glare_hq_v4),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = .38f), Color.Black.copy(alpha = .27f), Color.Black.copy(alpha = .92f)),
            )))
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(day?.location?.getDisplayName().orEmpty().ifBlank { stringResource(R.string.living_day_location) },
                    style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                Spacer(Modifier.height(56.dp))
                Text(stringResource(R.string.living_day_next), style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = .8f))
                Text(next?.first?.let(::display) ?: stringResource(R.string.prayer_times_title),
                    style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.SemiBold,
                    color = Color.White, modifier = Modifier.semantics { heading() })
                Text(next?.let { livingDayCountdown(now, it.second) } ?: "—",
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum", letterSpacing = (-2).sp),
                    color = photoAccent, fontWeight = FontWeight.Medium)
                next?.let { Text(it.second.format(timeFormat), style = MaterialTheme.typography.bodyLarge, color = Color.White) }
                Spacer(Modifier.height(48.dp))
                // At large font sizes use rows, keeping complete names and times readable.
                val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 5
                prayers.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (name, time) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                val done = name in completed
                                val status = stringResource(
                                    if (done) R.string.living_day_prayed
                                    else if (name == next?.first) R.string.living_day_is_next else R.string.living_day_not_marked,
                                    display(name),
                                )
                                Text(if (done) "✓" else if (name == next?.first) "◉" else "○",
                                    color = if (done || name == next?.first) photoAccent else Color.White.copy(alpha = .65f),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics {
                                        contentDescription = status
                                    })
                                Text(display(name), color = Color.White, style = MaterialTheme.typography.labelMedium)
                                Text(time.format(timeFormat), color = Color.White.copy(alpha = .8f),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                val actions = listOf(
            HomeAction(markable?.let { stringResource(R.string.living_day_mark, display(it.first)) }
                ?: stringResource(R.string.living_day_salah), Icons.Outlined.CheckCircle,
                { markable?.let { onTogglePrayer(it.first) } ?: onSchedule() }),
            HomeAction(stringResource(R.string.living_day_qibla), Icons.Outlined.Explore, onQibla),
            HomeAction(stringResource(if (isListening) R.string.living_day_pause else R.string.living_day_listen),
                if (isListening) Icons.Outlined.PauseCircleOutline else Icons.Outlined.PlayCircleOutline, onListen),
            HomeAction(stringResource(if (scheduleExpanded) R.string.living_day_close_schedule else R.string.living_day_schedule),
                Icons.Outlined.DateRange, onSchedule),
        )
                val actionColumns = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
                actions.chunked(actionColumns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { action ->
                            Surface(onClick = action.onClick, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = .48f)) {
                                Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp).heightIn(min = 56.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(action.icon, contentDescription = null, tint = photoAccent)
                                    Text(action.title, style = MaterialTheme.typography.labelMedium, color = Color.White,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HomeAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)

/** Wrap through midnight without negative hours or a minutes-only countdown. */
internal fun livingDayCountdown(now: LocalTime, next: LocalTime): String {
    val seconds = Math.floorMod(next.toSecondOfDay() - now.toSecondOfDay(), 24 * 60 * 60)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}
