package com.starception.submission.family

import android.Manifest
import android.content.pm.PackageManager
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import javax.inject.Inject

@Serializable data object FamilyRoute
@HiltViewModel class FamilyViewModel @Inject constructor(val client: FamilyClient) : ViewModel()

@Composable
private fun permissionRequest(): (Boolean, () -> Unit) -> Unit {
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
    val isDebugBuild = LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    var account by remember { mutableStateOf(if (isDebugBuild) FamilyEndpoint().androidTestAccount else "") }
    var password by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    val permissions = permissionRequest()
    LaunchedEffect(state.me) {
        if (isDebugBuild && state.me.isNotBlank()) invitation = FamilyEndpoint().testPeer(state.me)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Family") }, actions = {
        if (state.connected) TextButton(onClick = { client.disconnect() }) { Text("Sign out") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 110.dp)) {
            item { Text("Stay close to your family", style = MaterialTheme.typography.headlineSmall)
                Text("Invite someone to your family. Voice and video calls become available after they accept.",
                    style = MaterialTheme.typography.bodyMedium) }
            if (!state.connected) {
                if (isDebugBuild && account == FamilyEndpoint().androidTestAccount) item { Text("Android test account selected. Enter its password below.",
                    style = MaterialTheme.typography.bodySmall) }
                item { OutlinedTextField(account, { account = it }, label = { Text("Family account") },
                    placeholder = { Text("name@xmpp.starception.com") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(password, { password = it }, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Button(onClick = { client.login(account, password); password = "" }, enabled = !state.busy && account.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Signing in…" else "Sign in") } }
            } else {
                item { Text(state.me, style = MaterialTheme.typography.labelMedium)
                    Text(if (state.serviceReady) "Family service connected" else "Checking Family service…",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(invitation, { invitation = it }, label = { Text("Family member’s account") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row { Button(onClick = { client.invite(invitation); invitation = "" }, enabled = state.serviceReady && invitation.isNotBlank()) { Text("Send invitation") }
                        TextButton(onClick = { client.refresh() }) { Text("Refresh") } } }
                if (state.serviceReady && state.members.isEmpty()) item { Text("Your family list is empty. Send an invitation to get started.") }
                items(state.members, key = { it.jid }) { member ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(member.jid, style = MaterialTheme.typography.titleMedium)
                            Text(when (member.status) { "incoming" -> "Invited you to join their family"
                                "outgoing" -> "Waiting for acceptance"; else -> "Family member" })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (FamilyPolicy().canCall(member.status)) {
                                    Button(onClick = { permissions(false) { client.call(member.jid, false) } }, enabled = state.serviceReady && state.call == null) { Text("Voice") }
                                    Button(onClick = { permissions(true) { client.call(member.jid, true) } }, enabled = state.serviceReady && state.call == null) { Text("Video") }
                                }
                                if (member.status == "incoming") Button(onClick = { client.acceptMember(member.jid) }) { Text("Accept") }
                                TextButton(onClick = { client.removeMember(member.jid) }) {
                                    Text(when (member.status) { "incoming" -> "Decline"; "outgoing" -> "Cancel"; else -> "Remove" })
                                }
                            }
                        }
                    }
                }
            }
            state.error?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { client.dismissError() }) { Text("Dismiss") }
            } }
        }
    }
}

@Composable
fun FamilyCallOverlay(client: FamilyClient) {
    val state by client.state.collectAsStateWithLifecycle()
    val call = state.call ?: return
    val context = LocalContext.current
    val permissions = permissionRequest()
    val incoming = call.callee == state.me && call.stage == "ringing"
    DisposableEffect(call.id, incoming) {
        val ringtone = if (incoming) runCatching { RingtoneManager.getRingtone(context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)) }.getOrNull() else null
        ringtone?.play()
        onDispose { ringtone?.stop() }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(call.peer(state.me), style = MaterialTheme.typography.titleLarge)
                Text(when { incoming -> "Incoming ${call.kind} call"; call.stage == "ringing" -> "Calling…"
                    state.mediaConnected -> "Connected"; else -> "Connecting…" })
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (call.kind == "video" && call.stage == "active") {
                        VideoSurface(client.media, false, Modifier.fillMaxSize())
                        VideoSurface(client.media, true, Modifier.align(Alignment.TopEnd).size(110.dp, 150.dp))
                    } else Text(if (incoming) "Answer to join" else "Family call", style = MaterialTheme.typography.headlineMedium)
                }
                if (incoming) Button(onClick = { permissions(call.kind == "video") { client.acceptCall() } },
                    modifier = Modifier.fillMaxWidth()) { Text("Answer") }
                if (call.stage == "active") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { client.mute() }) { Text(if (state.muted) "Unmute" else "Mute") }
                        OutlinedButton(onClick = { client.speaker() }) { Text(if (state.speaker) "Earpiece" else "Speaker") }
                    }
                    if (call.kind == "video") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { client.toggleCamera() }) { Text(if (state.camera) "Camera off" else "Camera on") }
                        OutlinedButton(onClick = { client.switchCamera() }) { Text("Switch camera") }
                    }
                }
                Button(onClick = { client.hangup() }, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                    Text(if (incoming) "Decline" else if (call.stage == "ringing") "Cancel" else "End call")
                }
            }
        }
    }
}

@Composable
private fun VideoSurface(media: AndroidFamilyMedia, local: Boolean, modifier: Modifier) {
    val track by (if (local) media.localVideo else media.remoteVideo).collectAsStateWithLifecycle()
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context).apply { init(media.egl.eglBaseContext, null); setMirror(local) } }
    DisposableEffect(track, renderer) {
        runCatching { track?.addSink(renderer) }
        onDispose { runCatching { track?.removeSink(renderer) } }
    }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    AndroidView(factory = { renderer }, modifier = modifier)
}
