package com.starception.submission.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import java.util.Calendar

internal fun familyTime(time: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
internal fun familyNoticeText(kind: String, count: Int): String = when (kind) {
    "invitation" -> "Invited you to join their family"
    "missed_call" -> if (count > 1) "$count missed calls" else "Missed call"
    else -> if (count > 1) "$count unread messages" else "New message"
}

internal fun familyDay(time: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(Date(time))
internal fun familyDayLabel(time: Long): String {
    val today = Calendar.getInstance()
    if (familyDay(time) == familyDay(today.timeInMillis)) return "Today"
    today.add(Calendar.DATE, -1)
    return if (familyDay(time) == familyDay(today.timeInMillis)) "Yesterday" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
}

@Composable
internal fun FamilyChatContent(state: FamilyUiState, actions: FamilyActions) {
    val peer = state.chatPeer ?: return
    val colors = MaterialTheme.colorScheme
    val brand = familyBrandColor()
    var details by remember(peer) { mutableStateOf(false) }
    var more by remember(peer) { mutableStateOf(false) }
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
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(callDisplayName(peer)) },
        text = { Text(peer) }, confirmButton = { TextButton(onClick = { details = false }) { Text("Done") } })
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.closeChat) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to family", tint = colors.primary) }
            ContactAvatar(peer, 30, online = state.serviceReady && state.members.find { it.jid == peer }?.presence == "online")
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(callDisplayName(peer), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1)
                Text(when (state.members.find { it.jid == peer }?.presence) { "online" -> "Online"; "offline" -> "Offline"; else -> "Family member" }, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = { actions.voice(peer) }, enabled = state.serviceReady && state.call == null, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Call, "Voice call", Modifier.size(21.dp), tint = colors.primary) }
            IconButton(onClick = { actions.video(peer) }, enabled = state.serviceReady && state.call == null, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Videocam, "Video call", Modifier.size(22.dp), tint = colors.primary) }
            IconButton(onClick = { details = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Info, "Contact details", Modifier.size(20.dp), tint = colors.primary) }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .2f))
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.chatLoading) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    else if (state.nextBefore > 0) TextButton(onClick = actions.olderMessages) { Text("Earlier messages") }
                    else if (state.messages.isEmpty()) TextButton(onClick = actions.retryMessages) { Text("Say salaam to ${callDisplayName(peer)}") }
                }
            }
            itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
                val previous = state.messages.getOrNull(index - 1)
                val newDay = previous == null || familyDay(previous.createdAt) != familyDay(message.createdAt)
                val newGroup = newDay || previous?.from != message.from || message.createdAt - (previous?.createdAt ?: 0L) > 300000
                val mine = message.from == state.me
                if (newDay) Text(familyDayLabel(message.createdAt), modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = colors.onSurfaceVariant, fontSize = 10.sp)
                Row(Modifier.fillMaxWidth().padding(top = if (newGroup && !newDay) 14.dp else 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom) {
                    if (!mine) {
                        if (newGroup) Box(Modifier.padding(bottom = 14.dp)) { ContactAvatar(peer, 26) } else Spacer(Modifier.width(26.dp))
                        Spacer(Modifier.width(7.dp))
                    }
                    Column(Modifier.widthIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * .66f).coerceAtMost(288.dp)), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = if (mine) brand else colors.primaryContainer) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(message.text, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                    color = if (mine) androidx.compose.ui.graphics.Color.White else colors.onPrimaryContainer, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp)
                            }
                        }
                        Row(Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(familyTime(message.createdAt), fontSize = 9.sp, color = colors.onSurfaceVariant)
                            if (mine) Icon(Icons.Default.Check, "Saved to server", Modifier.size(12.dp), tint = colors.primary)
                        }
                    }
                }
            }
        }
        state.chatError?.let { message ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = colors.error, fontSize = 12.sp)
                TextButton(onClick = actions.retryMessages) { Text("Refresh") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                IconButton(onClick = { more = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Add, "Conversation actions", Modifier.size(20.dp).background(colors.tertiaryContainer, CircleShape).padding(3.dp), tint = colors.onTertiaryContainer)
                }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text("Voice call") }, enabled = state.serviceReady && state.call == null, onClick = { more = false; actions.voice(peer) })
                    DropdownMenuItem(text = { Text("Video call") }, enabled = state.serviceReady && state.call == null, onClick = { more = false; actions.video(peer) })
                    DropdownMenuItem(text = { Text("Contact details") }, onClick = { more = false; details = true })
                }
            }
            androidx.compose.foundation.text.BasicTextField(state.chatDraft, actions.chatDraft, maxLines = 5,
                textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                modifier = Modifier.weight(1f).background(colors.surfaceContainerLow, RoundedCornerShape(26.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                decorationBox = { field -> Box { if (state.chatDraft.isEmpty()) Text("Message…", color = colors.onSurfaceVariant, fontSize = 15.sp); field() } })
            IconButton(onClick = actions.sendMessage, enabled = state.connected && !state.chatSending && state.chatDraft.isNotBlank(), modifier = Modifier.size(40.dp)) {
                if (state.chatSending) CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, "Send message", tint = colors.primary)
            }
        }
    }
}
