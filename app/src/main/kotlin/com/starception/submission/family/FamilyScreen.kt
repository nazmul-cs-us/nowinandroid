package com.starception.submission.family

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import com.starception.submission.R
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.lifecycle.repeatOnLifecycle
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

internal data class FamilyActions(
    val signIn: (String, String) -> Unit = { _, _ -> },
    val signUp: (String, String, String) -> Unit = { _, _, _ -> },
    val webHelper: () -> Unit = {}, val dismissWebHelper: () -> Unit = {},
    val refresh: () -> Unit = {}, val signOut: () -> Unit = {},
    val invite: (String) -> Unit = {}, val accept: (String) -> Unit = {},
    val remove: (String) -> Unit = {}, val voice: (String) -> Unit = {},
    val video: (String) -> Unit = {}, val dismissError: () -> Unit = {},
)

@Composable
fun FamilyScreen(viewModel: FamilyViewModel = hiltViewModel()) {
    val client = viewModel.client
    val state by client.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val debug = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    val permission = permissionRequest()
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    FamilyScreenContent(state, FamilyActions(
        signUp = { username, secret, confirmation -> client.signUp(username, secret, confirmation) },
        signIn = { account, password ->
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notification.launch(Manifest.permission.POST_NOTIFICATIONS)
            client.login(account, password)
        }, webHelper = { client.requestWebHelper() }, dismissWebHelper = { client.dismissWebHelper() }, refresh = { client.refresh() }, signOut = { client.disconnect() },
        invite = { client.invite(it) }, accept = { client.acceptMember(it) }, remove = { client.removeMember(it) },
        voice = { peer -> permission(false) { client.call(peer, false) } },
        video = { peer -> permission(true) { client.call(peer, true) } }, dismissError = { client.dismissError() }),
        initialAccount = if (debug) FamilyEndpoint().androidTestAccount else "",
        initialPassword = if (debug) context.getString(R.string.family_test_password) else "",
        suggestedPeer = if (debug) FamilyEndpoint().testPeer(state.me) else "")
}

private data class FamilyPalette(val panel: Color, val field: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val header: Color, val onHeader: Color, val button: Color, val onButton: Color)
@Composable
private fun familyPalette(): FamilyPalette {
    val scheme = MaterialTheme.colorScheme
    return FamilyPalette(scheme.surface, scheme.surfaceContainerLow, scheme.onSurface, scheme.onSurfaceVariant, scheme.outlineVariant,
        scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FamilyScreenContent(
    state: FamilyUiState,
    actions: FamilyActions = FamilyActions(),
    initialAccount: String = "", initialPassword: String = "", suggestedPeer: String = "",
) {
    val colors = familyPalette()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    state.webPairing?.let { pairing ->
        AlertDialog(onDismissRequest = actions.dismissWebHelper,
            title = { Text("Temporary call alerts") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Copy this five-minute pairing code, then open the helper. On iPhone, add it to the Home Screen and open its icon to enable alerts. Alerts expire after 24 hours.")
                androidx.compose.foundation.text.selection.SelectionContainer { Text(pairing.code, fontSize = 11.sp, maxLines = 8) }
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(pairing.code)) }) { Text("Copy pairing code") }
            } },
            confirmButton = { TextButton(onClick = { uriHandler.openUri(pairing.url) }) { Text("Open helper") } },
            dismissButton = { TextButton(onClick = actions.dismissWebHelper) { Text("Done") } })
    }
    var account by remember { mutableStateOf(initialAccount) }
    var password by remember { mutableStateOf(initialPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var creatingAccount by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf(suggestedPeer) }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var invitations by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FamilyMember?>(null) }
    var removing by remember { mutableStateOf<FamilyMember?>(null) }
    var menu by remember { mutableStateOf(false) }
    val family = state.members.filter { it.status == "accepted" }
    val requests = state.members.filter { it.status != "accepted" }
    val shown = (if (invitations) requests else family).filter { callDisplayName(it.jid).contains(search, true) || it.jid.contains(search, true) }
    val canCall = state.serviceReady && state.call == null
    LaunchedEffect(suggestedPeer) { if (invitation.isBlank()) invitation = suggestedPeer }
    LaunchedEffect(state.connected) {
        if (!state.connected) { selected = null; inviting = false }
        else { password = ""; confirmation = ""; creatingAccount = false }
    }
    Column(Modifier.fillMaxSize().background(mainPageBackgroundBrush()).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(colors.ink.copy(alpha = 0.10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(19.dp), tint = colors.ink)
            }
            Text("Family", Modifier.padding(start = 10.dp).weight(1f), color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (state.connected) {
                IconButton(onClick = { searching = !searching; if (!searching) search = "" }) { Icon(Icons.Default.Search, "Search family", tint = colors.ink) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "Family settings", tint = colors.ink) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Temporary call alerts") }, onClick = { menu = false; actions.webHelper() })
                        DropdownMenuItem(text = { Text("Refresh family") }, onClick = { menu = false; actions.refresh() })
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; actions.signOut() })
                    }
                }
            }
        }
        if (state.connected) {
            LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Column(Modifier.width(60.dp).clickable(enabled = state.serviceReady, role = androidx.compose.ui.semantics.Role.Button) { inviting = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(60.dp).border(1.dp, colors.ink.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, "Invite a member", tint = colors.ink, modifier = Modifier.size(24.dp))
                        }
                        Text("Invite", fontSize = 11.sp, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                items(family, key = { it.jid }) { member ->
                    Column(Modifier.width(60.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { selected = member }, horizontalAlignment = Alignment.CenterHorizontally) {
                        ContactAvatar(member.jid, 60, ring = true)
                        Text(callDisplayName(member.jid).substringBefore(' '), fontSize = 11.sp, color = colors.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        } else {
            LandingIllustration(Modifier.fillMaxWidth().height(148.dp))
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), color = colors.panel.copy(alpha = 0.78f), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            LazyColumn(contentPadding = PaddingValues(top = 24.dp, bottom = 104.dp)) {
                if (!state.connected) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Your people.\nAlways closer.", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                                Text("A little space for the people who matter most. Connect through voice and video.", fontSize = 14.sp, lineHeight = 21.sp, color = colors.muted)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FamilyField(account, { account = it }, if (creatingAccount) "Choose a username" else "Family account", colors, leading = Icons.Default.PersonOutline)
                                if (creatingAccount) Text("3–32 letters, numbers, dots, underscores or hyphens. Your account will use @${FamilyEndpoint().domain}.", fontSize = 12.sp, color = colors.muted)
                                FamilyField(password, { password = it }, "Password", colors, leading = Icons.Default.Lock,
                                    password = !passwordVisible, trailing = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                if (passwordVisible) "Hide password" else "Show password", tint = colors.muted, modifier = Modifier.size(20.dp))
                                        }
                                    })
                                if (creatingAccount) {
                                    Text("Use a password with 12–128 characters.", fontSize = 12.sp, color = colors.muted)
                                    FamilyField(confirmation, { confirmation = it }, "Confirm password", colors,
                                        leading = Icons.Default.Lock, password = !passwordVisible)
                                }
                                if (creatingAccount) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                                Button(onClick = {
                                    focusManager.clearFocus()
                                    if (creatingAccount) actions.signUp(account, password, confirmation)
                                    else { actions.signIn(account, password); password = "" }
                                },
                                    enabled = !state.busy && account.isNotBlank() && password.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.button, contentColor = colors.onButton)) {
                                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = colors.onButton, strokeWidth = 2.dp)
                                    else Text(if (creatingAccount) "Create account" else "Connect with family", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = {
                                    creatingAccount = !creatingAccount; account = ""; password = ""; confirmation = ""
                                    actions.dismissError()
                                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (creatingAccount) "Already have an account? Sign in" else "New to Family? Create an account", color = colors.accent)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(14.dp), tint = colors.muted)
                                Text("Only people you invite and accept", Modifier.padding(start = 6.dp), fontSize = 11.sp, color = colors.muted)
                            }
                        }
                    }
                } else {
                    item {
                        Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(44.dp).background(colors.field, RoundedCornerShape(24.dp)).padding(4.dp)) {
                            FamilySegment("Family", !invitations, colors, Modifier.weight(1f)) { invitations = false }
                            FamilySegment("Invites", invitations, colors, Modifier.weight(1f), requests.size) { invitations = true }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (searching) item {
                        Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                            FamilyField(search, { search = it }, "Search your family", colors, leading = Icons.Default.Search,
                                trailing = { IconButton(onClick = { search = ""; searching = false }) { Icon(Icons.Default.Close, "Close search", tint = colors.muted) } })
                        }
                    }
                    if (!state.serviceReady) item {
                        Row(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
                            Text("Connecting your family…", fontSize = 13.sp, color = colors.muted)
                        }
                    }
                    if (state.serviceReady && shown.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(72.dp).background(colors.field, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(if (invitations) Icons.Default.MailOutline else Icons.Default.PeopleOutline, null, Modifier.size(30.dp), tint = colors.accent)
                            }
                            Text(if (search.isNotBlank()) "No matches yet" else if (invitations) "All caught up" else "Make room for your people", color = colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Text(if (search.isNotBlank()) "Try another name or account." else if (invitations) "New family invitations will appear here." else "Invite someone close. Once they accept, you can call each other anytime.",
                                color = colors.muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                            if (!invitations && search.isBlank()) Button(onClick = { inviting = true }, colors = ButtonDefaults.buttonColors(containerColor = colors.button, contentColor = colors.onButton), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("Invite family", Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    items(shown, key = { it.jid }) { member ->
                        Column {
                            Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { selected = member }
                                .padding(start = 24.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ContactAvatar(member.jid, 50)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(callDisplayName(member.jid), color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val presence = if (state.connected && state.serviceReady) member.presence else "unknown"
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (member.status == "accepted" && presence == "online") {
                                            Box(Modifier.size(6.dp).background(Color(0xFF2E9D65), CircleShape))
                                        }
                                        Text(when (member.status) { "incoming" -> "Invited you to join"; "outgoing" -> "Waiting for acceptance"
                                            else -> when (presence) { "online" -> "Online"; "offline" -> "Offline"; else -> "Status unavailable" } },
                                            color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (member.status == "accepted") FilledTonalIconButton(onClick = { actions.voice(member.jid) }, enabled = canCall,
                                    modifier = Modifier.size(44.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.field, contentColor = colors.accent)) {
                                    Icon(Icons.Default.Call, "Call ${callDisplayName(member.jid)}", Modifier.size(20.dp))
                                } else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "View invitation", Modifier.size(20.dp), tint = colors.muted)
                            }
                            if (member.status == "incoming") Row(Modifier.padding(start = 88.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { actions.accept(member.jid) }, enabled = state.serviceReady, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.button, contentColor = colors.onButton)) { Text("Accept", fontSize = 12.sp) }
                                TextButton(onClick = { removing = member }) { Text("Decline", fontSize = 12.sp, color = colors.muted) }
                            }
                            HorizontalDivider(Modifier.padding(start = 88.dp, end = 24.dp), color = colors.line.copy(alpha = 0.35f))
                        }
                    }
                }
                if (state.connected) state.pushRegistrationError?.let { message -> item {
                    Text(message, Modifier.padding(horizontal = 28.dp, vertical = 12.dp), fontSize = 12.sp, color = colors.muted)
                } }
                state.error?.takeIf { !creatingAccount || state.connected }?.let { message -> item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth().background(colors.field, RoundedCornerShape(16.dp)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = colors.accent)
                        Text(message, Modifier.weight(1f).padding(10.dp), color = colors.ink, fontSize = 12.sp, lineHeight = 18.sp)
                        IconButton(onClick = actions.dismissError) { Icon(Icons.Default.Close, "Dismiss message", tint = colors.muted, modifier = Modifier.size(18.dp)) }
                    }
                } }
            }
        }
    }
    if (inviting) ModalBottomSheet(onDismissRequest = { inviting = false }, containerColor = colors.panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(48.dp).background(colors.field, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PersonAdd, null, tint = colors.accent) }
            Text("Bring your people closer", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text("Send an invitation. Calls become available when they accept.", fontSize = 14.sp, lineHeight = 21.sp, color = colors.muted)
            FamilyField(invitation, { invitation = it }, "Family account", colors, leading = Icons.Default.PersonOutline)
            Button(onClick = { actions.invite(invitation); invitation = ""; inviting = false; invitations = true }, enabled = state.serviceReady && invitation.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.button, contentColor = colors.onButton)) { Text("Send invitation") }
        }
    }
    selected?.let { member -> ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = colors.panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ContactAvatar(member.jid, 80)
            Text(callDisplayName(member.jid), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(member.jid, fontSize = 12.sp, color = colors.muted, textAlign = TextAlign.Center)
            if (member.status == "accepted") Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactAction("Voice call", Icons.Default.Call, canCall, colors, Modifier.weight(1f)) { selected = null; actions.voice(member.jid) }
                ContactAction("Video call", Icons.Default.Videocam, canCall, colors, Modifier.weight(1f)) { selected = null; actions.video(member.jid) }
            } else Text(if (member.status == "incoming") "Accept this invitation to start calling." else "Your invitation is waiting for acceptance.", fontSize = 14.sp, color = colors.muted, textAlign = TextAlign.Center)
            if (member.status == "incoming") Button(onClick = { actions.accept(member.jid); selected = null }, enabled = state.serviceReady,
                colors = ButtonDefaults.buttonColors(containerColor = colors.button, contentColor = colors.onButton)) { Text("Accept invitation") }
            TextButton(onClick = { selected = null; removing = member }) { Text(if (member.status == "accepted") "Remove from family" else if (member.status == "incoming") "Decline invitation" else "Cancel invitation", color = colors.muted, fontSize = 13.sp) }
        }
    } }
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(if (member.status == "accepted") "Remove ${callDisplayName(member.jid)}?" else "Remove invitation?") },
        text = { Text("You’ll need a new accepted invitation to call each other again.") },
        confirmButton = { TextButton(onClick = { actions.remove(member.jid); removing = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } }) }
}

@Composable
private fun ContactAction(label: String, icon: ImageVector, enabled: Boolean, colors: FamilyPalette, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(20.dp), color = colors.field) {
        Column(Modifier.padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = colors.accent); Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink)
        }
    }
}

@Composable
private fun FamilySegment(label: String, active: Boolean, colors: FamilyPalette, modifier: Modifier, count: Int = 0, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxHeight().semantics { selected = active }, shape = RoundedCornerShape(20.dp),
        color = if (active) colors.panel else Color.Transparent, shadowElevation = if (active) 1.dp else 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) colors.ink else colors.muted)
            if (count > 0) Text(count.toString(), Modifier.padding(start = 7.dp).background(colors.accent.copy(alpha = 0.12f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
        }
    }
}

@Composable
private fun FamilyField(value: String, changed: (String) -> Unit, placeholder: String, colors: FamilyPalette, leading: ImageVector,
    password: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    TextField(value, changed, modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }, placeholder = { Text(placeholder, fontSize = 14.sp, color = colors.muted) },
        singleLine = true, shape = RoundedCornerShape(16.dp), textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
        leadingIcon = { Icon(leading, null, Modifier.size(20.dp), tint = colors.muted) }, trailingIcon = trailing,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false,
            keyboardType = if (placeholder.contains("password", ignoreCase = true)) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Email),
        colors = TextFieldDefaults.colors(focusedContainerColor = colors.field, unfocusedContainerColor = colors.field,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = colors.accent))
}

@Composable
private fun LandingIllustration(modifier: Modifier = Modifier) {
    val colors = familyPalette()
    val scheme = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(160.dp).background(colors.ink.copy(alpha = 0.05f), CircleShape))
        Box(Modifier.offset(x = (-43).dp, y = 7.dp).size(76.dp).background(scheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, Modifier.size(42.dp), tint = scheme.onSecondaryContainer)
        }
        Box(Modifier.offset(x = 32.dp, y = (-8).dp).size(92.dp).border(5.dp, colors.header, CircleShape).background(scheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, Modifier.size(52.dp), tint = scheme.onTertiaryContainer)
        }
        Box(Modifier.offset(x = 54.dp, y = 32.dp).size(34.dp).background(colors.panel, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Call, null, Modifier.size(17.dp), tint = colors.button)
        }
    }
}

@Composable
private fun ContactAvatar(jid: String, size: Int, ring: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val initials = callDisplayName(jid).split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
    val shades = listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer, scheme.surfaceContainerHigh)
    val shade = shades[(jid.hashCode() and Int.MAX_VALUE) % shades.size]
    Box(Modifier.size(size.dp).clearAndSetSemantics { }.then(if (ring) Modifier.border(1.5.dp, scheme.primary, CircleShape).padding(4.dp) else Modifier)
        .background(shade, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = scheme.onSurface, fontSize = (size * 0.30f).sp, fontWeight = FontWeight.Medium)
    }
}

internal data class FamilyCallActions(
    val answer: () -> Unit = {}, val hangup: () -> Unit = {}, val mute: () -> Unit = {},
    val speaker: () -> Unit = {}, val camera: () -> Unit = {}, val flip: () -> Unit = {},
)

/** Handles an already-resumed screen as well as every subsequent resume/stop. */
@Composable
internal fun FamilyCameraPreviewEffect(callId: String, lifecycle: androidx.lifecycle.Lifecycle,
    onStart: () -> Unit, onStop: () -> Unit) {
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    LaunchedEffect(callId, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            start()
            try { kotlinx.coroutines.awaitCancellation() } finally { stop() }
        }
    }
}

@Composable
fun FamilyCallOverlay(client: FamilyClient) {
    val state by client.state.collectAsStateWithLifecycle()
    val call = state.call ?: return
    val permission = permissionRequest()
    val remote by client.media.remoteVideo.collectAsStateWithLifecycle()
    val local by client.media.localVideo.collectAsStateWithLifecycle()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    FamilyCameraPreviewEffect(call.id, lifecycle,
        onStart = { client.previewCamera(call.id) }, onStop = { client.stopPreview(call.id) })
    var elapsed by remember(call.id) { mutableLongStateOf(0L) }
    LaunchedEffect(call.id, state.connectedAtElapsed) {
        val start = state.connectedAtElapsed ?: return@LaunchedEffect
        while (true) { elapsed = (SystemClock.elapsedRealtime() - start) / 1000; delay(1000) }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        val video = call.kind == "video" && (call.stage == "active" || (call.caller == state.me && local != null && state.camera))
        val lightStatusBar = !video && MaterialTheme.colorScheme.primaryContainer.luminance() > 0.5f
        val lightNavigationBar = !video && MaterialTheme.colorScheme.surface.luminance() > 0.5f
        DisposableEffect(view, lightStatusBar, lightNavigationBar) {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightStatusBar
                    isAppearanceLightNavigationBars = lightNavigationBar
                }
            }
            onDispose { }
        }
        FamilyCallContent(state, elapsed, FamilyCallActions(
            answer = { permission(call.kind == "video") { client.acceptCall() } }, hangup = { client.hangup() },
            mute = { client.mute() }, speaker = { client.speaker() }, camera = { client.toggleCamera(); client.previewCamera() }, flip = { client.switchCamera() }),
            remoteReady = remote != null, localReady = local != null,
            remoteVideo = { VideoSurface(client.media, false, Modifier.fillMaxSize()) },
            localVideo = { VideoSurface(client.media, true, Modifier.fillMaxSize()) })
    }
}

@Composable
internal fun FamilyCallContent(state: FamilyUiState, elapsed: Long = 0, actions: FamilyCallActions = FamilyCallActions(),
    remoteReady: Boolean = false, localReady: Boolean = false, remoteVideo: @Composable () -> Unit = {}, localVideo: @Composable () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val call = state.call ?: return
    val incoming = call.callee == state.me && call.stage == "ringing"
    val video = call.kind == "video" && call.stage == "active"
    val preview = call.kind == "video" && call.stage == "ringing" && !incoming && localReady && state.camera
    val immersive = video || preview
    val name = callDisplayName(call.peer(state.me))
    val ink = if (immersive) Color.White else colors.onSurface
    val muted = if (immersive) Color.White.copy(alpha = 0.75f) else colors.onSurfaceVariant
    val status = when {
        incoming -> "Incoming ${call.kind} call"
        call.stage == "ringing" -> "Calling…"
        state.mediaConnected -> "%02d:%02d".format(elapsed / 60, elapsed % 60)
        state.connectedAtElapsed != null -> "Reconnecting…"
        else -> "Connecting…"
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        val compact = maxHeight < 500.dp
        if (preview) Box(Modifier.fillMaxSize()) { localVideo() }
        if (video) Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                remoteVideo()
                if (!remoteReady) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ContactAvatar(call.peer(state.me), 72)
                    Text("Waiting for video…", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Box(Modifier.weight(1f).fillMaxWidth().background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                if (state.camera) localVideo()
                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VideocamOff, null, Modifier.size(30.dp), tint = Color.White.copy(alpha = 0.6f))
                    Text("Camera off", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Text("You", Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 11.sp)
            }
        }
        Box(Modifier.fillMaxSize().background(if (immersive) Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.78f)))
            else mainPageBackgroundBrush()))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(16.dp), tint = muted)
                Text(if (video) name else "Family", Modifier.padding(start = 8.dp).weight(1f), color = ink, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (video) Text(status, Modifier.background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (preview) Arrangement.Top else Arrangement.Center) {
                if (!video) {
                    if (preview) Spacer(Modifier.height(28.dp))
                    ContactAvatar(call.peer(state.me), if (preview) 64 else if (compact) 48 else 128)
                    Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
                    Text(name, color = ink, fontSize = if (preview) 20.sp else if (compact) 20.sp else 30.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(status, color = muted, fontSize = 14.sp,
                        fontFamily = if (state.mediaConnected) FontFamily.Monospace else FontFamily.Default, modifier = Modifier.padding(top = 8.dp))
                }
            }
            state.callError?.let { Text(it, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp)) }
            if (call.kind == "video" && call.stage == "ringing" && !incoming) {
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControl(Icons.Default.Cameraswitch, "Flip", enabled = state.camera, onVideo = preview, action = actions.flip)
                }
            }
            if (call.stage == "active") {
                Row(Modifier.fillMaxWidth().background((if (immersive) Color.White else colors.primary).copy(alpha = if (video) 0.13f else 0.055f), RoundedCornerShape(28.dp)).padding(vertical = 16.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControl(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, if (state.muted) "Unmute" else "Mute", state.muted, onVideo = video, action = actions.mute)
                    CallControl(if (state.speaker) Icons.Default.VolumeUp else Icons.Default.Hearing, "Speaker", state.speaker, onVideo = video, action = actions.speaker)
                    if (call.kind == "video") {
                        CallControl(if (state.camera) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera", !state.camera, onVideo = video, action = actions.camera)
                        CallControl(Icons.Default.Cameraswitch, "Flip", enabled = state.camera, onVideo = video, action = actions.flip)
                    }
                    if (compact) CallControl(Icons.Default.CallEnd, "End call", color = colors.error, onVideo = immersive, action = actions.hangup)
                }
                if (!compact) Spacer(Modifier.height(24.dp))
            }
            if (!compact || call.stage != "active") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (incoming) CallControl(if (call.kind == "video") Icons.Default.Videocam else Icons.Default.Call, "Accept", color = colors.primary, large = true, onVideo = video, action = actions.answer)
                CallControl(Icons.Default.CallEnd, if (incoming) "Decline" else if (call.stage == "ringing") "Cancel" else "End call",
                    color = colors.error, large = incoming, onVideo = immersive, action = actions.hangup)
            }
        }
    }
}

private fun callDisplayName(jid: String): String = jid.substringBefore('@').replace('.', ' ').replace('_', ' ').replace('-', ' ')
    .split(' ').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::titlecase) }.ifBlank { "Family member" }

@Composable
private fun CallControl(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true,
    color: Color? = null, large: Boolean = false, onVideo: Boolean = false, action: () -> Unit) {
    val theme = MaterialTheme.colorScheme
    val ink = if (onVideo) Color.White else theme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledIconButton(onClick = action, enabled = enabled,
            modifier = Modifier.size(if (large) 72.dp else 52.dp).semantics { selected = active },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color ?: if (active) theme.primaryContainer else if (onVideo) Color.White.copy(alpha = 0.12f) else theme.surfaceContainerHigh,
                contentColor = when { color == theme.error -> theme.onError; color != null -> theme.onPrimary; active -> theme.onPrimaryContainer; else -> ink },
                disabledContainerColor = ink.copy(alpha = 0.04f), disabledContentColor = ink.copy(alpha = 0.35f))) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(if (large) 30.dp else 23.dp))
        }
        Text(label, fontSize = 11.sp, color = ink.copy(alpha = if (enabled) 0.85f else 0.4f))
    }
}

@Composable
private fun VideoSurface(media: AndroidFamilyMedia, local: Boolean, modifier: Modifier) {
    val track by (if (local) media.localVideo else media.remoteVideo).collectAsStateWithLifecycle()
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context).apply {
        init(media.egl.eglBaseContext, null); setMirror(local); setZOrderMediaOverlay(local)
        setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        setEnableHardwareScaler(true)
    } }
    DisposableEffect(track, renderer) {
        runCatching { track?.addSink(renderer) }
        onDispose { runCatching { track?.removeSink(renderer) } }
    }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    AndroidView(factory = { renderer }, modifier = modifier)
}
