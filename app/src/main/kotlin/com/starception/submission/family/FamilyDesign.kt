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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Family's compact sans-serif treatment; colors still come from the selected app theme. */
@Composable
internal fun FamilyDesign(content: @Composable () -> Unit) {
    val original = MaterialTheme.typography
    val typography = remember(original) {
        fun androidx.compose.ui.text.TextStyle.family() = copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp)
        original.copy(displayLarge = original.displayLarge.family(), displayMedium = original.displayMedium.family(), displaySmall = original.displaySmall.family(),
            headlineLarge = original.headlineLarge.family(), headlineMedium = original.headlineMedium.family(), headlineSmall = original.headlineSmall.family(),
            titleLarge = original.titleLarge.family(), titleMedium = original.titleMedium.family(), titleSmall = original.titleSmall.family(),
            bodyLarge = original.bodyLarge.family(), bodyMedium = original.bodyMedium.family(), bodySmall = original.bodySmall.family(),
            labelLarge = original.labelLarge.family(), labelMedium = original.labelMedium.family(), labelSmall = original.labelSmall.family())
    }
    MaterialTheme(typography = typography) { CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge, content = content) }
}

/** Feature navigation replaces the app-wide bar while signed into Family. */
@Composable
internal fun FamilyBottomBar(directory: Boolean, unread: Int, contacts: () -> Unit, chats: () -> Unit,
    invite: () -> Unit, alerts: () -> Unit, settings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().height(92.dp)) {
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(68.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = contacts) { Icon(Icons.Outlined.People, "Family contacts", tint = if (directory) colors.primary else colors.primary.copy(alpha = .35f)) }
            IconButton(onClick = chats) { Icon(Icons.Outlined.ChatBubbleOutline, "Family chats", tint = if (!directory) colors.primary else colors.primary.copy(alpha = .35f)) }
            Spacer(Modifier.width(64.dp))
            IconButton(onClick = alerts) { BadgedBox(badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }) {
                Icon(Icons.Outlined.NotificationsNone, "Family notifications", tint = colors.primary.copy(alpha = .55f))
            } }
            IconButton(onClick = settings) { Icon(Icons.Outlined.Settings, "Family settings", tint = colors.primary.copy(alpha = .55f)) }
        }
        FilledIconButton(onClick = invite, modifier = Modifier.align(Alignment.TopCenter).size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer)) {
            Icon(Icons.Default.Add, "Invite a member", Modifier.size(27.dp))
        }
    }
}
