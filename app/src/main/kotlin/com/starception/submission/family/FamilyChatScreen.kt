package com.starception.submission.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush
import kotlinx.coroutines.awaitCancellation
import java.text.DateFormat
import java.util.Date

internal fun familyTime(time: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
internal fun familyNoticeText(kind: String, count: Int): String = when (kind) {
    "invitation" -> "Invited you to join their family"
    "missed_call" -> if (count > 1) "$count missed calls" else "Missed call"
    else -> if (count > 1) "$count unread messages" else "New message"
}

@Composable
internal fun FamilyChatContent(state: FamilyUiState, actions: FamilyActions) {
    val peer = state.chatPeer ?: return
    val colors = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    BackHandler { actions.closeChat() }
    LaunchedEffect(peer, state.messages.lastOrNull()?.id, state.call?.id) {
        if (state.call == null) lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            actions.readChat(); awaitCancellation()
        }
    }
    LaunchedEffect(peer, state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.size)
    }
    Column(Modifier.fillMaxSize().background(mainPageBackgroundBrush()).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(70.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.closeChat) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to family", tint = colors.onSurface) }
            Column(Modifier.weight(1f)) {
                Text(callDisplayName(peer), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text(when (state.members.find { it.jid == peer }?.presence) { "online" -> "Online"; "offline" -> "Offline"; else -> "Family member" }, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = { actions.voice(peer) }, enabled = state.serviceReady && state.call == null) { Icon(Icons.Default.Call, "Voice call", tint = colors.primary) }
            IconButton(onClick = { actions.video(peer) }, enabled = state.serviceReady && state.call == null) { Icon(Icons.Default.Videocam, "Video call", tint = colors.primary) }
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), color = colors.surface.copy(alpha = .84f)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your private family conversation", color = colors.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                        if (state.chatLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (state.nextBefore > 0) TextButton(onClick = actions.olderMessages) { Text("Earlier messages") }
                        else if (state.messages.isEmpty()) TextButton(onClick = actions.retryMessages) { Text("Say salaam to ${callDisplayName(peer)}") }
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    val mine = message.from == state.me
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(Modifier.widthIn(max = 300.dp), shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 5.dp else 20.dp, if (mine) 20.dp else 5.dp),
                            color = if (mine) colors.primaryContainer else colors.surfaceContainerLow) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(message.text, color = if (mine) colors.onPrimaryContainer else colors.onSurface, fontSize = 15.sp)
                                }
                                Text(familyTime(message.createdAt), Modifier.align(Alignment.End), fontSize = 10.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        state.chatError?.let { message ->
            Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = colors.error, fontSize = 12.sp)
                TextButton(onClick = actions.retryMessages) { Text("Refresh") }
            }
        }
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(state.chatDraft, actions.chatDraft, modifier = Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 5,
                shape = RoundedCornerShape(24.dp))
            FilledIconButton(onClick = actions.sendMessage, enabled = state.connected && !state.chatSending && state.chatDraft.isNotBlank(), modifier = Modifier.padding(bottom = 4.dp).size(48.dp)) {
                if (state.chatSending) CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, "Send message")
            }
        }
    }
}
