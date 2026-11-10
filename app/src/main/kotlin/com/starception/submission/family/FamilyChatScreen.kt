package com.starception.submission.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
internal fun FamilyChatContent(
    state: FamilyUiState,
    actions: FamilyActions,
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val peer = state.chatPeer ?: return
    val colors = MaterialTheme.colorScheme
    val brand = familyBrandColor()
    val member = state.members.find { it.jid == peer } ?: FamilyMember(peer, "accepted")
    val online = state.serviceReady && member.presence == "online"
    val canCall = state.serviceReady && state.call == null
    val canSend = state.connected && !state.chatSending && state.chatDraft.isNotBlank()
    val focus = LocalFocusManager.current
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
    if (details) {
        FamilyContactProfile(
            member = member, state = state, actions = actions,
            onBack = { details = false }, onMessage = { details = false },
            backDescription = "Back to conversation",
        )
        return
    }
    Column(Modifier.fillMaxSize().background(colors.surface).windowInsetsPadding(contentInsets)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.closeChat) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to family", tint = colors.primary) }
            Row(
                Modifier.weight(1f).clickable(role = Role.Button) { focus.clearFocus(); details = true }
                    .semantics { contentDescription = "Open profile for ${callDisplayName(peer)}" }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(peer, 40, online = online)
                Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(callDisplayName(peer), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        !state.serviceReady -> "Status unavailable"
                        online -> "Online"
                        member.presence == "offline" -> "Offline"
                        else -> "Family member"
                    }, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { actions.voice(peer) }, enabled = canCall,
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.primary)) { Icon(Icons.Outlined.Call, "Voice call", Modifier.size(23.dp)) }
            IconButton(onClick = { actions.video(peer) }, enabled = canCall,
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.primary)) { Icon(Icons.Outlined.Videocam, "Video call", Modifier.size(24.dp)) }
            IconButton(onClick = { focus.clearFocus(); details = true }) { Icon(Icons.Outlined.Info, "Contact details", Modifier.size(23.dp), tint = colors.primary) }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .16f))
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
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
                val next = state.messages.getOrNull(index + 1)
                val groupEnds = next == null || next.from != message.from ||
                    familyDay(next.createdAt) != familyDay(message.createdAt) ||
                    next.createdAt - message.createdAt > 300000
                if (newDay) Text(familyDayLabel(message.createdAt), modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = colors.onSurfaceVariant, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().padding(top = if (newGroup && !newDay) 14.dp else 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom) {
                    if (!mine) {
                        if (newGroup) Box(Modifier.padding(bottom = 18.dp)) { ContactAvatar(peer, 28, online = online) } else Spacer(Modifier.width(28.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.widthIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * .70f).coerceAtMost(320.dp)), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Surface(shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp,
                            bottomStart = if (mine) 22.dp else 5.dp, bottomEnd = 22.dp),
                            color = if (mine) brand else colors.primaryContainer) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(message.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    color = if (mine) Color.White else colors.onPrimaryContainer,
                                    fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp)
                            }
                        }
                        if (groupEnds) Row(Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(familyTime(message.createdAt), fontSize = 10.sp, color = colors.onSurfaceVariant)
                            if (mine) Icon(Icons.Default.Check, "Saved to server", Modifier.size(12.dp), tint = colors.primary)
                        }
                    }
                }
            }
        }
        state.chatError?.let { message ->
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = colors.errorContainer, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = colors.onErrorContainer, fontSize = 12.sp, lineHeight = 18.sp)
                    TextButton(onClick = actions.retryMessages) { Text("Refresh", color = colors.onErrorContainer) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).background(colors.surfaceContainerLow, RoundedCornerShape(28.dp)),
                verticalAlignment = Alignment.Bottom) {
            Box {
                IconButton(onClick = { more = true }, modifier = Modifier.size(52.dp)) {
                    Box(Modifier.size(30.dp).background(colors.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, "Conversation actions", Modifier.size(21.dp), tint = colors.onTertiaryContainer)
                    }
                }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text("Voice call") }, enabled = state.serviceReady && state.call == null, onClick = { more = false; actions.voice(peer) })
                    DropdownMenuItem(text = { Text("Video call") }, enabled = state.serviceReady && state.call == null, onClick = { more = false; actions.video(peer) })
                    DropdownMenuItem(text = { Text("Contact details") }, onClick = { more = false; details = true })
                }
            }
            BasicTextField(state.chatDraft, actions.chatDraft, maxLines = 5,
                textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface, fontSize = 16.sp,
                    lineHeight = 23.sp, fontFamily = FontFamily.SansSerif),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) actions.sendMessage() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Message" }
                    .heightIn(min = 52.dp).padding(top = 14.dp, bottom = 14.dp, end = 16.dp),
                decorationBox = { field -> Box { if (state.chatDraft.isEmpty()) Text("Message…", color = colors.onSurfaceVariant, fontSize = 16.sp); field() } })
            }
            IconButton(onClick = actions.sendMessage, enabled = canSend,
                modifier = Modifier.size(52.dp).semantics { contentDescription = "Send message" }) {
                if (state.chatSending) CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Outlined.Send, null, tint = colors.primary)
            }
        }
    }
}
