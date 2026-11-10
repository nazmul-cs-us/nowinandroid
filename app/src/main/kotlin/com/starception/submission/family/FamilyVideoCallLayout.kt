package com.starception.submission.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reference-inspired video card and translucent shelf, using only the two actual participants. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FamilyVideoCallLayout(
    state: FamilyUiState, status: String, actions: FamilyCallActions,
    remoteReady: Boolean, localReady: Boolean, remoteVideo: @Composable () -> Unit, localVideo: @Composable () -> Unit,
) {
    val call = state.call ?: return
    val colors = MaterialTheme.colorScheme
    val name = callDisplayName(call.peer(state.me))
    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.surface).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
    ) {
        val previewHeight = if (maxHeight < 650.dp) 100.dp else 132.dp
        Box(
            Modifier.fillMaxSize().padding(bottom = 104.dp).clip(RoundedCornerShape(32.dp))
                .background(colors.surfaceContainerHigh),
        ) {
            remoteVideo()
            if (!remoteReady) Column(
                Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContactAvatar(call.peer(state.me), 88)
                Text("Waiting for video…", color = colors.onSurfaceVariant, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth().height(168.dp).background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = .6f), Color.Transparent),
            )))
            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(status, color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
                Text(FamilyConnectionRoute().label(state.connectionRoute), color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Surface(
                Modifier.fillMaxWidth().padding(top = 32.dp), shape = RoundedCornerShape(26.dp),
                color = colors.surface.copy(alpha = .9f), shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 64.dp)) {
                        FlowRow(
                            Modifier.fillMaxWidth(), maxItemsInEachRow = if (maxWidth < 192.dp) 2 else 4,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            VideoIcon(if (state.camera) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                                "Camera", !state.camera, actions.camera)
                            VideoIcon(if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                                if (state.muted) "Unmute" else "Mute", state.muted, actions.mute)
                            VideoIcon(Icons.Outlined.Cameraswitch, "Flip", false, actions.flip, state.camera)
                            VideoIcon(Icons.Outlined.VolumeUp, "Speaker", state.speaker, actions.speaker)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(18.dp))
                                    .background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center,
                            ) {
                                if (state.camera && localReady) localVideo() else {
                                    Icon(Icons.Outlined.VideocamOff,
                                        if (state.camera) "Starting your camera" else "Your camera is off",
                                        tint = colors.onSurfaceVariant)
                                }
                                if (state.muted) Box(
                                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp)
                                        .background(FamilyVisuals.peach, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.MicOff, "Your microphone is muted", Modifier.size(16.dp),
                                        tint = FamilyVisuals.forest)
                                }
                            }
                            Text("You", color = colors.onSurface, fontSize = 12.sp)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(18.dp))
                                    .background(FamilyVisuals.forest), contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.matchParentSize()) {
                                    val center = Offset(size.width / 2f, size.height * .4f)
                                    for (radius in listOf(.24f, .36f, .48f)) {
                                        drawCircle(Color.White.copy(alpha = .08f), size.width * radius,
                                            center, style = Stroke(1.dp.toPx()))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ContactAvatar(call.peer(state.me), 42)
                                    Text(if (state.mediaConnected) "Connected" else "Connecting…",
                                        color = Color.White.copy(alpha = .9f), fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(name.substringBefore(' '), color = colors.onSurface, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    state.callError?.let { Text(it, color = colors.error, fontSize = 12.sp) }
                }
            }
            FilledIconButton(
                onClick = actions.hangup,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp).size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.error, contentColor = colors.onError),
            ) { Icon(Icons.Default.CallEnd, "End call", Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun VideoIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean,
    action: () -> Unit, enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = action, enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { this.selected = selected }
            .background(if (selected) colors.primaryContainer else Color.Transparent, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = colors.primary),
    ) { Icon(icon, label, modifier = Modifier.size(24.dp)) }
}
