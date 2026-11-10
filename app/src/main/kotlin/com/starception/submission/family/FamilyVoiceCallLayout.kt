package com.starception.submission.family

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.R

private val voiceCallEndColor = Color(0xFFC62828)

/** Reference-style call card and compact shelf, with only the two real accounts. */
@Composable
internal fun FamilyVoiceCallLayout(state: FamilyUiState, status: String, actions: FamilyCallActions) {
    if (state.call == null) return
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        val compact = maxHeight < 650.dp
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VoiceCallerCard(state, status, Modifier.weight(1f).fillMaxHeight(), compact = true)
                VoiceCallShelf(state, actions, Modifier.weight(1f).fillMaxHeight(), compact = true)
            }
        } else {
            VoiceCallerCard(state, status, Modifier.fillMaxSize().padding(bottom = 96.dp), compact)
            VoiceCallShelf(state, actions, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp), compact)
        }
    }
}

@Composable
private fun VoiceCallerCard(state: FamilyUiState, status: String, modifier: Modifier, compact: Boolean) {
    val call = state.call ?: return
    Box(modifier.clip(RoundedCornerShape(32.dp)).background(FamilyVisuals.forest)) {
        // Decorative tilework, not a substitute photograph of the contact.
        Image(painterResource(R.drawable.family_voice_pattern), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFF061F1C).copy(alpha = .86f),
            Color(0xFF102E29).copy(alpha = .50f),
            Color(0xFF061F1C).copy(alpha = .90f),
        ))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = if (compact) 20.dp else 32.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FAMILY · VOICE CALL", color = FamilyVisuals.mint, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(12.dp))
            Text(callDisplayName(call.peer(state.me)), color = Color.White,
                fontSize = if (compact) 24.sp else 28.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color.White.copy(alpha = .85f), fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontFamily = if (call.stage == "active" && state.mediaConnected) FontFamily.Monospace else FontFamily.SansSerif)
            Spacer(Modifier.height(if (compact) 28.dp else 64.dp))
            Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(32.dp)) {
                Box(Modifier.padding(if (compact) 16.dp else 24.dp), contentAlignment = Alignment.Center) {
                    ContactAvatar(call.peer(state.me), if (compact) 72 else 104)
                }
            }
        }
    }
}

@Composable
private fun VoiceCallShelf(state: FamilyUiState, actions: FamilyCallActions, modifier: Modifier, compact: Boolean) {
    val call = state.call ?: return
    val colors = MaterialTheme.colorScheme
    val incoming = call.callee == state.me && call.stage == "ringing"
    Surface(modifier, color = colors.surfaceContainerHigh.copy(alpha = .96f), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            state.callError?.let {
                Text(it, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (incoming) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        VoiceCallButton(Icons.Default.CallEnd, "Decline", false, actions.hangup, voiceCallEndColor)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        VoiceCallButton(Icons.Outlined.Call, "Accept", false, actions.answer, colors.primary)
                    }
                } else {
                    if (call.stage == "active") {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            VoiceCallButton(if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                                if (state.muted) "Unmute" else "Mute", state.muted, actions.mute)
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            VoiceCallButton(if (state.speaker) Icons.AutoMirrored.Outlined.VolumeUp else Icons.Outlined.Hearing,
                                "Speaker", state.speaker, actions.speaker)
                        }
                    } else {
                        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Voice call", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Waiting for an answer", color = colors.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        VoiceCallButton(Icons.Default.CallEnd, if (call.stage == "ringing") "Cancel" else "End call",
                            false, actions.hangup, voiceCallEndColor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoiceParticipantTile(state.me, "You", if (state.muted) "Muted" else null,
                    Modifier.weight(1f), compact, own = true)
                VoiceParticipantTile(call.peer(state.me), callDisplayName(call.peer(state.me)).substringBefore(' '),
                    when {
                        incoming -> "Incoming"
                        call.stage == "ringing" -> "Waiting"
                        state.mediaConnected -> "Connected"
                        state.connectedAtElapsed != null -> "Reconnecting"
                        else -> "Connecting"
                    }, Modifier.weight(1f), compact)
            }
            if (call.stage == "active") {
                Text(FamilyConnectionRoute().label(state.connectionRoute), color = colors.onSurfaceVariant,
                    fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun VoiceParticipantTile(jid: String, name: String, status: String?, modifier: Modifier, compact: Boolean, own: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, color = if (own) colors.secondaryContainer else FamilyVisuals.forest,
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compact) 10.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ContactAvatar(jid, if (compact) 32 else 40)
            Text(name, color = if (own) colors.onSecondaryContainer else Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                Text(status, color = if (own) colors.onSecondaryContainer else FamilyVisuals.mint,
                    fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun VoiceCallButton(icon: ImageVector, label: String, active: Boolean, action: () -> Unit, color: Color? = null) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledIconButton(onClick = action, modifier = Modifier.size(52.dp).semantics { selected = active },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color ?: if (active) FamilyVisuals.peach else colors.secondaryContainer,
                contentColor = if (color != null) Color.White else if (active) FamilyVisuals.forest else colors.onSecondaryContainer)) {
            Icon(icon, label, Modifier.size(24.dp))
        }
        Text(label, color = colors.onSurface, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
