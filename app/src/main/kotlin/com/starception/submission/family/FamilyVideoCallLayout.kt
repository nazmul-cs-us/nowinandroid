package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Video remains dominant; a translucent toolbar and portrait tiles overlap its lower edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FamilyVideoCallLayout(
    state: FamilyUiState, status: String, actions: FamilyCallActions,
    remoteReady: Boolean, localReady: Boolean, remoteVideo: @Composable () -> Unit, localVideo: @Composable () -> Unit,
) {
    val call = state.call ?: return
    val colors = MaterialTheme.colorScheme
    val name = callDisplayName(call.peer(state.me))
    val outgoing = call.stage == "ringing" && call.caller == state.me
    var shelfHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        val tileHeight = if (maxHeight < 650.dp) 88.dp else 112.dp
        val tileWidth = if (maxWidth < 360.dp) 80.dp else 88.dp
        val shelfHeight = if (shelfHeightPx > 0) with(density) { shelfHeightPx.toDp() } else 220.dp
        val videoBottomMargin = 64.dp
        val obscuredVideo = (shelfHeight - videoBottomMargin).coerceAtLeast(0.dp)
        Box(Modifier.fillMaxSize().padding(bottom = videoBottomMargin)
            .clip(RoundedCornerShape(32.dp)).background(colors.surfaceContainerHigh)
            .testTag("familyVideoMainCard")) {
            if (outgoing) {
                if (state.camera && localReady) localVideo()
            } else remoteVideo()
            Box(Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = .65f), Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(bottom = obscuredVideo + 12.dp)) {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(status, color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
                    Text(if (outgoing) "Your camera preview" else FamilyConnectionRoute().label(state.connectionRoute),
                        color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                }
                if ((outgoing && (!state.camera || !localReady)) || (!outgoing && !remoteReady)) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val avatarSize = if (maxHeight < 140.dp) 48 else 88
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            ContactAvatar(call.peer(state.me), avatarSize)
                            Spacer(Modifier.height(12.dp))
                            Text(when {
                                !outgoing -> "Waiting for video…"
                                !state.camera -> "Your camera is off"
                                else -> "Starting your camera…"
                            }, color = colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .onSizeChanged { shelfHeightPx = it.height }.testTag("familyVideoShelf"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Only the toolbar is tinted. There is no large opaque panel around the thumbnails.
            Box(Modifier.fillMaxWidth().background(FamilyVisuals.forest.copy(alpha = .58f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 64.dp)) {
                    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = if (maxWidth < 192.dp) 2 else 4,
                        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (outgoing) {
                            VideoControl(if (state.camera) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                                "Camera", !state.camera, actions.camera)
                            VideoControl(if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                                if (state.muted) "Unmute" else "Mute", state.muted, actions.mute)
                            VideoControl(Icons.Outlined.Cameraswitch, "Flip", false, actions.flip, state.camera)
                        } else {
                            VideoControl(if (state.camera) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                                "Camera", !state.camera, actions.camera)
                            VideoControl(if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                                if (state.muted) "Unmute" else "Mute", state.muted, actions.mute)
                            VideoControl(Icons.Outlined.Cameraswitch, "Flip", false, actions.flip, state.camera)
                            VideoControl(Icons.AutoMirrored.Outlined.VolumeUp, "Speaker", state.speaker, actions.speaker)
                        }
                    }
                }
                Column(Modifier.align(Alignment.TopEnd).offset(y = (-12).dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledIconButton(onClick = actions.hangup, modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)) {
                        Icon(Icons.Default.CallEnd, if (outgoing) "Cancel" else "End call", Modifier.size(24.dp))
                    }
                    if (outgoing) Text("Cancel", color = Color.White, fontSize = 10.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                Column(Modifier.width(tileWidth), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(tileHeight).clip(RoundedCornerShape(16.dp))
                        .background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                        if (outgoing) ContactAvatar(state.me, 36)
                        else if (state.camera && localReady) localVideo()
                        else Icon(Icons.Outlined.VideocamOff, if (state.camera) "Starting your camera" else "Your camera is off",
                            tint = colors.onSecondaryContainer)
                        if (state.muted) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp)
                            .background(FamilyVisuals.peach, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.MicOff, "Your microphone is muted", Modifier.size(14.dp), tint = FamilyVisuals.forest)
                        }
                    }
                    Text("You", color = colors.onSurface, fontSize = 11.sp)
                }
                Column(Modifier.width(tileWidth), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.fillMaxWidth().height(tileHeight).clip(RoundedCornerShape(16.dp))
                        .background(FamilyVisuals.forest).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                        ContactAvatar(call.peer(state.me), 36)
                        Text(if (outgoing) "Waiting" else if (state.mediaConnected) "Connected" else "Connecting…",
                            color = FamilyVisuals.mint, fontSize = 10.sp, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(name.substringBefore(' '), color = colors.onSurface, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            state.callError?.let {
                Surface(color = colors.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(it, Modifier.padding(10.dp), color = colors.onErrorContainer, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VideoControl(icon: ImageVector, label: String, active: Boolean, action: () -> Unit,
    enabled: Boolean = true) {
        IconButton(onClick = action, enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { selected = active }
                .background(if (active) FamilyVisuals.peach else Color.Transparent, CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (active) FamilyVisuals.forest else Color.White,
                disabledContentColor = Color.White.copy(alpha = .4f))) {
            Icon(icon, label, Modifier.size(22.dp))
        }
}
