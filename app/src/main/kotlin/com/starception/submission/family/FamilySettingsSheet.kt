package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A feature-local account sheet; all rows dispatch the existing Family actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FamilySettingsSheet(
    state: FamilyUiState, onDismiss: () -> Unit, onInvitations: () -> Unit, actions: FamilyActions,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Family settings", Modifier.weight(1f), color = colors.onSurface,
                fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, "Close settings", tint = colors.onSurfaceVariant)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).testTag("familySettingsList"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(color = FamilyVisuals.forest, shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ContactAvatar(state.me, 52)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(callDisplayName(state.me), color = Color.White, fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            SelectionContainer {
                                Text(state.me, color = FamilyVisuals.mint, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(6.dp).background(
                                    if (state.serviceReady) FamilyVisuals.online else FamilyVisuals.peach, CircleShape))
                                Text(if (state.serviceReady) "Connected" else "Connecting…",
                                    color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            item { SettingsSectionLabel("STAY CONNECTED") }
            item {
                FamilySettingsRow(Icons.Outlined.PersonAdd, "Invitations", "Review your family invitations",
                    badge = state.members.count { it.status != "accepted" }) { onDismiss(); onInvitations() }
            }
            item {
                FamilySettingsRow(Icons.Outlined.NotificationsActive, "Temporary call alerts", "Pair the web helper for 24-hour alerts") {
                    onDismiss(); actions.webHelper()
                }
            }
            item { SettingsSectionLabel("APP") }
            item {
                FamilySettingsRow(Icons.Outlined.Refresh, "Refresh family", "Update your family list") {
                    onDismiss(); actions.refresh()
                }
            }
            item {
                FamilySettingsRow(Icons.AutoMirrored.Filled.ArrowBack, "Back to app", "Return without signing out") {
                    onDismiss(); actions.exit()
                }
            }
            item {
                Surface(onClick = { onDismiss(); actions.signOut() }, color = colors.errorContainer.copy(alpha = .4f),
                    shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(22.dp), tint = colors.error)
                        Text("Sign out", color = colors.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(label, Modifier.padding(start = 4.dp, top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

@Composable
private fun FamilySettingsRow(icon: ImageVector, title: String, description: String, badge: Int = 0, action: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = action, color = colors.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(colors.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(21.dp), tint = colors.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (badge > 0) {
                Surface(color = FamilyVisuals.peach, contentColor = FamilyVisuals.forest, shape = CircleShape) {
                    Text(badge.toString(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }
    }
}
