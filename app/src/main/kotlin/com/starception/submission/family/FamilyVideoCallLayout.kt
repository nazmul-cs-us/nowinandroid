package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush

/** The reference's overlapping video card and control shelf, for the two real participants. */
@Composable
internal fun FamilyVideoCallLayout(state: FamilyUiState, status: String, actions: FamilyCallActions,
    remoteReady: Boolean, localReady: Boolean, remoteVideo: @Composable () -> Unit, localVideo: @Composable () -> Unit) {
    val call = state.call ?: return
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(mainPageBackgroundBrush()).statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
        Box(Modifier.fillMaxSize().padding(bottom = 116.dp).clip(RoundedCornerShape(30.dp)).background(colors.surfaceContainerHigh)) {
            remoteVideo()
            if (!remoteReady) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactAvatar(call.peer(state.me), 80)
                Text("Waiting for video…", color = colors.onSurfaceVariant, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth().height(160.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent))))
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, start = 18.dp, end = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(callDisplayName(call.peer(state.me)), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text(status, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                Text(FamilyConnectionRoute().label(state.connectionRoute), color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(Modifier.fillMaxWidth().padding(top = 28.dp), shape = RoundedCornerShape(26.dp),
                color = colors.surface.copy(alpha = .80f), shadowElevation = 8.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(end = 60.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        VideoIcon(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, if (state.muted) "Unmute" else "Mute", state.muted, actions.mute)
                        VideoIcon(if (state.camera) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera", !state.camera, actions.camera)
                        VideoIcon(Icons.Default.Cameraswitch, "Flip", false, actions.flip, state.camera)
                        VideoIcon(Icons.Default.VolumeUp, "Speaker", state.speaker, actions.speaker)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(110.dp).height(106.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                if (state.camera && localReady) localVideo() else Icon(Icons.Default.VideocamOff, "Your camera is off", tint = colors.onSurfaceVariant)
                            }
                            Text("You", color = colors.onSurface, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.width(110.dp).height(106.dp).clip(RoundedCornerShape(16.dp)).background(familyBrandColor()),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                ContactAvatar(call.peer(state.me), 42)
                                Text(if (state.mediaConnected) "Connected" else "Connecting…", color = Color.White.copy(alpha = .8f), fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                            Text(callDisplayName(call.peer(state.me)).substringBefore(' '), color = colors.onSurface, fontSize = 11.sp)
                        }
                    }
                    state.callError?.let { Text(it, color = colors.error, fontSize = 11.sp) }
                }
            }
            FilledIconButton(onClick = actions.hangup, modifier = Modifier.align(Alignment.TopEnd).padding(end = 10.dp).size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.error, contentColor = colors.onError)) { Icon(Icons.Default.CallEnd, "End call") }
        }
    }
}

@Composable
private fun VideoIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean,
    action: () -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(40.dp)
        .background(if (selected) colors.primaryContainer else Color.Transparent, CircleShape)) {
        Icon(icon, label, tint = if (enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = .4f), modifier = Modifier.size(23.dp))
    }
}
