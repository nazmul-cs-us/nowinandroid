package com.starception.submission.feature.prayertimes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.ubuntu_regular
import com.starception.submission.core.images.resources.ubuntu_medium
import org.jetbrains.compose.resources.Font

enum class HomeHeaderDetailKind { Calendar, Location, Weather }

data class HomeHeaderDetail(val text: String, val kind: HomeHeaderDetailKind)

/** Keeps the Home metadata compact while leaving the full location data intact. */
fun homeHeaderLocation(place: String, country: String, countryCode: String): String {
    val countryName = country.trim()
    val isoCode = countryCode.trim().uppercase().takeIf { code ->
        code.length == 2 && code.all { it in 'A'..'Z' }
    }.orEmpty()
    val shortCountry = when {
        isoCode == "AE" || countryName.equals("United Arab Emirates", ignoreCase = true) -> "UAE"
        isoCode == "GB" || countryName.equals("United Kingdom", ignoreCase = true) -> "UK"
        isoCode == "US" || countryName.equals("United States", ignoreCase = true) -> "US"
        else -> isoCode.ifBlank { countryName }
    }
    return (place.split(" · ", ", ").map(String::trim).filter { part ->
        part.isNotBlank() && !part.equals(countryName, ignoreCase = true) &&
            !part.equals(isoCode, ignoreCase = true) && !part.equals(shortCountry, ignoreCase = true)
    } + shortCountry).filter(String::isNotBlank).distinctBy { it.lowercase() }.joinToString(" · ")
}

/** Use the same quiet Ubuntu family as the brand, with a deliberate title/detail hierarchy. */
object HomeHeaderTypography {
    val greeting: TextStyle
        @Composable get(): TextStyle {
            val family = homeHeaderFontFamily()
            return remember(family) { TextStyle(fontFamily = family, fontSize = 23.sp,
                lineHeight = 29.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.4).sp) }
        }
    val detail: TextStyle
        @Composable get(): TextStyle {
            val family = homeHeaderFontFamily()
            return remember(family) { TextStyle(fontFamily = family, fontSize = 12.5.sp,
                lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = .05.sp) }
        }
}

@Composable
private fun homeHeaderFontFamily(): FontFamily {
    val regular = Font(Res.font.ubuntu_regular, FontWeight.Normal)
    val medium = Font(Res.font.ubuntu_medium, FontWeight.Medium)
    return remember(regular, medium) { FontFamily(regular, medium) }
}

/** One fixed-height metadata line; the greeting above it stays stationary. */
@Composable
fun HomeRotatingHeaderText(
    texts: List<HomeHeaderDetail>,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val entries = remember(texts) { texts.filter { it.text.isNotBlank() }.distinct() }
    var displayedText by remember(entries) { mutableStateOf(entries.firstOrNull()) }
    val rotation = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(entries, lifecycleOwner) {
        var index = 0
        displayedText = entries.firstOrNull()
        rotation.snapTo(0f)
        if (entries.size < 2) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A lifecycle pause can interrupt either half of the flip. Resume
            // with a readable face rather than leaving text edge-on.
            rotation.snapTo(0f)
            while (true) {
                // Metadata remains stationary; long addresses must not create a scrolling ticker.
                delay(6_000L)
                rotation.animateTo(90f, tween(180, easing = FastOutLinearInEasing))
                index = (index + 1) % entries.size
                displayedText = entries[index]
                rotation.snapTo(-90f)
                rotation.animateTo(0f, tween(240, easing = LinearOutSlowInEasing))
            }
        }
    }
    val detail = displayedText ?: return
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            rotationX = rotation.value
            cameraDistance = 12f * density
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = when (detail.kind) {
                HomeHeaderDetailKind.Calendar -> Icons.Outlined.CalendarMonth
                HomeHeaderDetailKind.Location -> Icons.Outlined.LocationOn
                HomeHeaderDetailKind.Weather -> Icons.Outlined.Cloud
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = detail.text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun HomeProfileAvatar(tint: Color) {
    Box(Modifier.size(30.dp).background(tint.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.PersonOutline, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun FamilyProfileEntry(account: String?, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(account?.takeIf { it.isNotBlank() }?.substringBefore('@') ?: "Family account",
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (account == null) "Sign in" else "Open chats")
    }
}
