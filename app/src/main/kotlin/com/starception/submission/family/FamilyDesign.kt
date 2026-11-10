package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.theme.LocalDarkTheme

/** Reference colours belong to Family only; the rest of the app keeps its chosen brand. */
internal object FamilyVisuals {
    val forest = Color(0xFF1C3932)
    val mint = Color(0xFFD2E7E0)
    val peach = Color(0xFFF3D3AB)
    val online = Color(0xFF00BFA0)
    fun canvas(dark: Boolean): Color = if (dark) Color(0xFF14221C) else Color.White
}

/** A cohesive reference-inspired Family palette and sans-serif typography, including dark mode. */
@Composable
internal fun FamilyDesign(referenceColors: Boolean = true, content: @Composable () -> Unit) {
    val original = MaterialTheme.typography
    val originalColors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val colors = remember(originalColors, dark, referenceColors) {
        if (!referenceColors) originalColors else if (dark) originalColors.copy(
            primary = Color(0xFF88D7B9), onPrimary = Color(0xFF0C3024),
            primaryContainer = Color(0xFF294B40), onPrimaryContainer = Color(0xFFE3F3EA),
            secondaryContainer = Color(0xFF2B4036), onSecondaryContainer = Color(0xFFE3F3EA),
            tertiaryContainer = FamilyVisuals.peach, onTertiaryContainer = FamilyVisuals.forest,
            background = Color(0xFF101A16), onBackground = Color(0xFFEDF5F0),
            surface = FamilyVisuals.canvas(true), onSurface = Color(0xFFEDF5F0),
            onSurfaceVariant = Color(0xFFB3C5BA), outlineVariant = Color(0xFF354B40),
            surfaceContainerLow = Color(0xFF1B2D25), surfaceContainerHigh = Color(0xFF253D32),
        ) else originalColors.copy(
            primary = Color(0xFF006C53), onPrimary = Color.White,
            primaryContainer = FamilyVisuals.mint, onPrimaryContainer = Color(0xFF172E26),
            secondaryContainer = Color(0xFFE5EFEB), onSecondaryContainer = FamilyVisuals.forest,
            tertiaryContainer = FamilyVisuals.peach, onTertiaryContainer = FamilyVisuals.forest,
            background = Color.White, onBackground = Color(0xFF111B16),
            surface = FamilyVisuals.canvas(false), onSurface = Color(0xFF111B16),
            onSurfaceVariant = Color(0xFF5E6F65), outlineVariant = Color(0xFFE5EDE8),
            surfaceContainerLow = Color(0xFFF5F7F5), surfaceContainerHigh = Color(0xFFEDF3EF),
        )
    }
    val typography = remember(original) {
        fun androidx.compose.ui.text.TextStyle.family() = copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp)
        original.copy(displayLarge = original.displayLarge.family(), displayMedium = original.displayMedium.family(), displaySmall = original.displaySmall.family(),
            headlineLarge = original.headlineLarge.family(), headlineMedium = original.headlineMedium.family(), headlineSmall = original.headlineSmall.family(),
            titleLarge = original.titleLarge.family(), titleMedium = original.titleMedium.family(), titleSmall = original.titleSmall.family(),
            bodyLarge = original.bodyLarge.family(), bodyMedium = original.bodyMedium.family(), bodySmall = original.bodySmall.family(),
            labelLarge = original.labelLarge.family(), labelMedium = original.labelMedium.family(), labelSmall = original.labelSmall.family())
    }
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge, content = content)
    }
}

/** Feature navigation replaces the app-wide bar while signed into Family. */
@Composable
internal fun FamilyBottomBar(directory: Boolean, unread: Int, contacts: () -> Unit, chats: () -> Unit,
    invite: () -> Unit, alerts: () -> Unit, settings: () -> Unit, modifier: Modifier = Modifier,
    navigationInsets: WindowInsets = WindowInsets.navigationBars) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth()) {
        Column {
        Spacer(Modifier.height(24.dp))
        // Include the gesture area inside the elevated surface. Padding the
        // whole bar instead leaves its bottom shadow visible in that area.
        Surface(Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = colors.surface, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(navigationInsets).height(72.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = contacts, modifier = Modifier.semantics { selected = directory }) {
                Box(Modifier.size(36.dp).background(if (directory) colors.primaryContainer else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.People, "Family contacts", tint = if (directory) colors.primary else colors.primary.copy(alpha = .35f))
                }
            }
            IconButton(onClick = chats, modifier = Modifier.semantics { selected = !directory }) {
                Box(Modifier.size(36.dp).background(if (!directory) colors.primaryContainer else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ChatBubbleOutline, "Family chats", tint = if (!directory) colors.primary else colors.primary.copy(alpha = .35f))
                }
            }
            Spacer(Modifier.width(64.dp))
            IconButton(onClick = alerts) { BadgedBox(badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }) {
                Icon(Icons.Outlined.NotificationsNone, "Family notifications", tint = colors.primary.copy(alpha = .55f))
            } }
            IconButton(onClick = settings) { Icon(Icons.Outlined.Settings, "Family settings", tint = colors.primary.copy(alpha = .55f)) }
        }
        }
        }
        Box(Modifier.align(Alignment.TopCenter).size(76.dp).background(colors.surface, CircleShape).padding(7.dp)) {
        FilledIconButton(onClick = invite, modifier = Modifier.fillMaxSize(),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer)) {
            Icon(Icons.Default.Add, "Invite a member", Modifier.size(27.dp))
        }
        }
    }
}
