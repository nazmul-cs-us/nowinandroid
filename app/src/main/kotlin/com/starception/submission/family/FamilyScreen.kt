package com.starception.submission.family

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import com.starception.submission.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@Serializable data object FamilyRoute
@HiltViewModel class FamilyViewModel @Inject constructor(val client: FamilyClient) : ViewModel()

@Composable
internal fun permissionRequest(): (Boolean, () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(emptyArray<String>()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = pending; pending = null
        if (requested.isNotEmpty() && requested.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }) action?.invoke() else denied = true
        requested = emptyArray()
    }
    if (denied) AlertDialog(onDismissRequest = { denied = false },
        title = { Text("Call permission needed") },
        text = { Text("Allow microphone access for calls and camera access for video calls in app settings.") },
        confirmButton = { TextButton(onClick = { denied = false }) { Text("OK") } })
    return { video, action ->
        val permissions = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else arrayOf(Manifest.permission.RECORD_AUDIO)
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) action()
        else { pending = action; requested = permissions; launcher.launch(permissions) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(viewModel: FamilyViewModel = hiltViewModel()) {
    val client = viewModel.client
    val state by client.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val debug = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    var account by remember { mutableStateOf(if (debug) FamilyEndpoint().androidTestAccount else "") }
    var password by remember { mutableStateOf(if (debug) context.getString(R.string.family_test_password) else "") }
    var invitation by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var invitations by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<FamilyMember?>(null) }
    var quickCall by remember { mutableStateOf<FamilyMember?>(null) }
    val permissions = permissionRequest()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val family = state.members.filter { FamilyPolicy().canCall(it.status) }
    val requests = state.members.filterNot { FamilyPolicy().canCall(it.status) }
    val shown = (if (invitations) requests else family).filter { it.jid.contains(search, ignoreCase = true) || callDisplayName(it.jid).contains(search, ignoreCase = true) }
    val green = Color(0xFF173F3B)
    LaunchedEffect(state.me) { if (debug && state.me.isNotBlank()) invitation = FamilyEndpoint().testPeer(state.me) }
    Scaffold(containerColor = green, topBar = {
        TopAppBar(title = { Text("Family", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = green, titleContentColor = Color.White, actionIconContentColor = Color.White),
            actions = { if (state.connected) {
                IconButton(onClick = { inviting = true }, enabled = state.serviceReady) { Icon(Icons.Default.PersonAdd, "Invite family member") }
                IconButton(onClick = { client.refresh() }) { Icon(Icons.Default.Refresh, "Refresh family") }
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Account options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; client.disconnect() })
                }
            } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.connected) {
                LazyRow(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                    item {
                        Column(Modifier.clickable(enabled = state.serviceReady) { inviting = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(58.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, "Invite", tint = Color.White)
                            }
                            Text("Invite", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    items(family, key = { it.jid }) { member ->
                        Column(Modifier.width(64.dp).clickable { quickCall = member }, horizontalAlignment = Alignment.CenterHorizontally) {
                            ContactAvatar(member.jid, 58)
                            Text(callDisplayName(member.jid), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface) {
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.connected) {
                        item { Text("Your people, closer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text("Sign in to connect with your family through voice and video.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                        item { OutlinedTextField(account, { account = it }, label = { Text("Family account") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) }
                        item { Button(onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            client.login(account, password); password = ""
                        }, enabled = !state.busy && account.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.busy) "Signing in…" else "Sign in")
                        } }
                        if (debug) item { Text("Test credentials are prefilled in this debug build.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        item { Text(if (state.serviceReady) "Ready to connect" else "Connecting to your family…", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item { OutlinedTextField(search, { search = it }, placeholder = { Text("Search family") },
                            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) }
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(!invitations, { invitations = false }, label = { Text("Family · ${family.size}") })
                            FilterChip(invitations, { invitations = true }, label = { Text("Invitations · ${requests.size}") })
                        } }
                        if (state.serviceReady && shown.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (invitations) Icons.Default.MailOutline else Icons.Default.PeopleOutline, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(if (search.isNotBlank()) "No matches" else if (invitations) "No pending invitations" else "Start your family circle",
                                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                                if (search.isBlank() && !invitations) TextButton(onClick = { inviting = true }) { Text("Invite a family member") }
                            }
                        }
                        items(shown, key = { it.jid }) { member ->
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ContactAvatar(member.jid, 50)
                                    Column(Modifier.weight(1f)) {
                                        Text(callDisplayName(member.jid), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(when (member.status) { "incoming" -> "Wants to join your family"; "outgoing" -> "Invitation sent"; else -> member.jid },
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (member.status == "accepted") {
                                        IconButton(onClick = { permissions(false) { client.call(member.jid, false) } }, enabled = state.serviceReady && state.call == null) {
                                            Icon(Icons.Default.Call, "Voice call ${callDisplayName(member.jid)}", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { permissions(true) { client.call(member.jid, true) } }, enabled = state.serviceReady && state.call == null) {
                                            Icon(Icons.Default.Videocam, "Video call ${callDisplayName(member.jid)}", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    var menu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${callDisplayName(member.jid)}") }
                                        DropdownMenu(menu, { menu = false }) {
                                            DropdownMenuItem(text = { Text(if (member.status == "accepted") "Remove member" else if (member.status == "incoming") "Decline invitation" else "Cancel invitation") },
                                                onClick = { menu = false; removing = member })
                                        }
                                    }
                                }
                                if (member.status == "incoming") Row(Modifier.padding(start = 62.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { client.acceptMember(member.jid) }, enabled = state.serviceReady) { Text("Accept") }
                                    TextButton(onClick = { removing = member }) { Text("Decline") }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                    state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { client.dismissError() }) { Text("Dismiss") } } }
                }
            }
        }
    }
    if (inviting) ModalBottomSheet(onDismissRequest = { inviting = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Invite to your family", style = MaterialTheme.typography.headlineSmall)
            Text("They’ll need to accept before you can call each other.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(invitation, { invitation = it }, label = { Text("Family account") }, placeholder = { Text("name@xmpp.starception.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { client.invite(invitation); invitation = ""; inviting = false }, enabled = state.serviceReady && invitation.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send invitation") }
        }
    }
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(if (member.status == "accepted") "Remove family member?" else "Remove invitation?") },
        text = { Text("Calling access requires an accepted family relationship.") },
        confirmButton = { TextButton(onClick = { client.removeMember(member.jid); removing = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } }) }
    quickCall?.let { member -> AlertDialog(onDismissRequest = { quickCall = null }, title = { Text(callDisplayName(member.jid)) }, text = { Text(member.jid) },
        confirmButton = { TextButton(enabled = state.serviceReady && state.call == null, onClick = { quickCall = null; permissions(false) { client.call(member.jid, false) } }) { Text("Voice call") } },
        dismissButton = { TextButton(enabled = state.serviceReady && state.call == null, onClick = { quickCall = null; permissions(true) { client.call(member.jid, true) } }) { Text("Video call") } }) }
}

@Composable
private fun ContactAvatar(jid: String, size: Int) {
    val initials = callDisplayName(jid).split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
    Box(Modifier.size(size.dp).background(Color(0xFFE0EFE9), CircleShape).padding(3.dp)
        .background(Color(0xFF315A60), CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FamilyCallOverlay(client: FamilyClient) {
    val state by client.state.collectAsStateWithLifecycle()
    val call = state.call ?: return
    val permissions = permissionRequest()
    val incoming = call.callee == state.me && call.stage == "ringing"
    val peer = call.peer(state.me)
    val name = callDisplayName(peer)
    val connectedAt = state.connectedAtElapsed
    var elapsed by remember(call.id) { mutableLongStateOf(0L) }
    LaunchedEffect(call.id, connectedAt) {
        val start = connectedAt ?: return@LaunchedEffect
        while (true) {
            elapsed = (SystemClock.elapsedRealtime() - start) / 1000
            delay(1000)
        }
    }
    val remoteVideo by client.media.remoteVideo.collectAsStateWithLifecycle()
    val videoActive = call.kind == "video" && call.stage == "active"
    val status = when {
        incoming -> "Incoming ${call.kind} call"
        call.stage == "ringing" -> "Calling…"
        state.mediaConnected -> "%02d:%02d".format(elapsed / 60, elapsed % 60)
        connectedAt != null -> "Reconnecting…"
        else -> "Connecting…"
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101D26), contentColor = Color.White) {
          Box(Modifier.fillMaxSize()) {
            if (videoActive) Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    VideoSurface(client.media, false, Modifier.fillMaxSize())
                    if (remoteVideo == null) Text("Waiting for video…", color = Color.White.copy(alpha = 0.7f))
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF22343E)), contentAlignment = Alignment.Center) {
                    if (state.camera) VideoSurface(client.media, true, Modifier.fillMaxSize())
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideocamOff, null, Modifier.size(36.dp), tint = Color.White)
                        Text("Your camera is off", color = Color.White.copy(alpha = 0.7f))
                    }
                    Text("You", Modifier.align(Alignment.TopStart).padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(if (videoActive)
                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.8f))
                else listOf(Color(0xFF193A42), Color(0xFF101D26))))
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (call.kind == "video") Icons.Default.Videocam else Icons.Default.Call, null, Modifier.size(16.dp), tint = Color(0xFF91D5C5))
                    Text(if (call.kind == "video") "FAMILY VIDEO CALL" else "FAMILY VOICE CALL",
                        style = MaterialTheme.typography.labelMedium, color = Color(0xFFB4CBCB))
                }
                Text(name, style = if (videoActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(peer, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB4CBCB), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (state.mediaConnected) FontFamily.Monospace else FontFamily.Default,
                    color = Color(0xFF91D5C5))
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    if (!videoActive) CallAvatar(name)
                }
                state.error?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                if (call.stage == "active") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallControl(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            if (state.muted) "Unmute" else "Mute", state.muted, action = { client.mute() })
                        CallControl(if (state.speaker) Icons.Default.VolumeUp else Icons.Default.Hearing,
                            "Speaker", state.speaker, action = { client.speaker() })
                        if (call.kind == "video") {
                            CallControl(if (state.camera) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                "Camera", !state.camera, action = { client.toggleCamera() })
                            CallControl(Icons.Default.Cameraswitch, "Flip", enabled = state.camera, action = { client.switchCamera() })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControl(Icons.Default.CallEnd, if (incoming) "Decline" else if (call.stage == "ringing") "Cancel" else "End call",
                        color = Color(0xFFD94B59), action = { client.hangup() })
                    if (incoming) CallControl(if (call.kind == "video") Icons.Default.Videocam else Icons.Default.Call, "Answer",
                        color = Color(0xFF24846C), action = { permissions(call.kind == "video") { client.acceptCall() } })
                }
            }
          }
        }
    }
}

private fun callDisplayName(jid: String): String = jid.substringBefore('@').replace('.', ' ').replace('_', ' ').replace('-', ' ')
    .split(' ').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::titlecase) }.ifBlank { "Family member" }

@Composable
private fun CallAvatar(name: String) {
    Box(Modifier.size(176.dp).background(Color.White.copy(alpha = 0.04f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(140.dp).background(Color(0xFF315A60), CircleShape), contentAlignment = Alignment.Center) {
            Text(name.split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase(),
                style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium, color = Color(0xFFD5F5EB))
        }
    }
}

@Composable
private fun CallControl(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true,
    color: Color? = null, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledIconButton(onClick = action, enabled = enabled,
            modifier = Modifier.size(56.dp).semantics { selected = active },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color ?: if (active) Color(0xFFD5F5EB) else Color.White.copy(alpha = 0.12f),
                contentColor = if (active && color == null) Color(0xFF173D35) else Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.04f), disabledContentColor = Color.White.copy(alpha = 0.35f))) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f))
    }
}

@Composable
private fun VideoSurface(media: AndroidFamilyMedia, local: Boolean, modifier: Modifier) {
    val track by (if (local) media.localVideo else media.remoteVideo).collectAsStateWithLifecycle()
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context).apply {
        init(media.egl.eglBaseContext, null); setMirror(local); setZOrderMediaOverlay(local)
    } }
    DisposableEffect(track, renderer) {
        runCatching { track?.addSink(renderer) }
        onDispose { runCatching { track?.removeSink(renderer) } }
    }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    AndroidView(factory = { renderer }, modifier = modifier)
}
