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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val openChat: (String) -> Unit = {}, val closeChat: () -> Unit = {}, val chatDraft: (String) -> Unit = {},
    val sendMessage: () -> Unit = {}, val olderMessages: () -> Unit = {}, val retryMessages: () -> Unit = {},
    val readChat: () -> Unit = {}, val acknowledgeNotifications: () -> Unit = {},
    val signIn: (String, String) -> Unit = { _, _ -> },
    val signUp: (String, String, String) -> Unit = { _, _, _ -> },
    val webHelper: () -> Unit = {}, val dismissWebHelper: () -> Unit = {},
    val exit: () -> Unit = {},
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
    val view = androidx.compose.ui.platform.LocalView.current
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    DisposableEffect(state.connected, state.reconnecting, state.chatPeer, light, view) {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        val previousContrast = if (android.os.Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        // Family owns the surface beneath navigation on both the chats list
        // and individual conversations, not just while a conversation is open.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window?.isNavigationBarContrastEnforced = false
        }
        controller?.isAppearanceLightStatusBars = (!(state.connected || state.reconnecting) || state.chatPeer != null) && light
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
            if (android.os.Build.VERSION.SDK_INT >= 29 && previousContrast != null) {
                window?.isNavigationBarContrastEnforced = previousContrast
            }
        }
    }
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val debug = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    val permission = permissionRequest()
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    FamilyScreenContent(state, FamilyActions(exit = { backDispatcher?.onBackPressed() },
        openChat = client::openChat, closeChat = client::closeChat, chatDraft = client::chatDraft,
        sendMessage = { client.sendMessage() }, olderMessages = { client.loadMessages(true) }, retryMessages = { client.loadMessages() },
        readChat = { client.markChatRead() }, acknowledgeNotifications = { client.acknowledgeNotifications() },
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

@Composable
internal fun familyBrandColor(): Color = FamilyVisuals.forest

@Composable
internal fun FamilyScreenContent(state: FamilyUiState, actions: FamilyActions = FamilyActions(),
    initialAccount: String = "", initialPassword: String = "", suggestedPeer: String = "") {
    FamilyDesign {
        FamilyScreenBody(state, actions, initialAccount, initialPassword, suggestedPeer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilyScreenBody(
    state: FamilyUiState,
    actions: FamilyActions = FamilyActions(),
    initialAccount: String = "", initialPassword: String = "", suggestedPeer: String = "",
) {
    if (state.chatPeer != null) { FamilyChatContent(state, actions); return }
    val signedIn = state.connected || state.reconnecting
    val colors = familyPalette()
    val brand = familyBrandColor()
    val headerInk = Color.White
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

    var directory by remember { mutableStateOf(false) }
    var invitations by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FamilyMember?>(null) }
    var removing by remember { mutableStateOf<FamilyMember?>(null) }
    var menu by remember { mutableStateOf(false) }
    var inbox by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val family = state.members.filter { it.status == "accepted" }.sortedWith(compareByDescending<FamilyMember> { member -> state.conversations.find { it.peer == member.jid }?.lastMessage?.createdAt ?: 0L }.thenBy { callDisplayName(it.jid) })
    val requests = state.members.filter { it.status != "accepted" }
    val shown = (if (invitations) requests else family).filter { callDisplayName(it.jid).contains(search, true) || it.jid.contains(search, true) }
    val canCall = state.serviceReady && state.call == null
    LaunchedEffect(suggestedPeer) { if (invitation.isBlank()) invitation = suggestedPeer }
    LaunchedEffect(signedIn) {
        if (!signedIn) { selected = null; inviting = false }
        else { password = ""; confirmation = ""; creatingAccount = false }
    }
    Box(Modifier.fillMaxSize().background(colors.panel)) {
    Column(Modifier.fillMaxSize().background(brand).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = if (signedIn) 16.dp else 0.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { focusManager.clearFocus(); actions.exit() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to app", tint = headerInk)
            }
            Text(if (!signedIn) "Family" else if (directory) "Contacts" else "Chats", Modifier.weight(1f),
                color = headerInk, fontSize = if (signedIn) 26.sp else 24.sp,
                fontWeight = if (signedIn) FontWeight.SemiBold else FontWeight.Medium)
            if (signedIn) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Family menu", tint = headerInk) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(if (requests.isEmpty()) "Invitations" else "Invitations · ${requests.size}") }, onClick = { menu = false; invitations = true })
                        DropdownMenuItem(text = { Text("Temporary call alerts") }, onClick = { menu = false; actions.webHelper() })
                        DropdownMenuItem(text = { Text("Refresh family") }, onClick = { menu = false; actions.refresh() })
                        DropdownMenuItem(text = { Text("Back to app") }, onClick = { menu = false; actions.exit() })
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; actions.signOut() })
                    }
                }
            }
        }
        if (signedIn) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 18.dp).fillMaxWidth().height(44.dp)
                .background(Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(19.dp))
                androidx.compose.foundation.text.BasicTextField(search, { search = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).semantics { contentDescription = "Search family" },
                    decorationBox = { field -> Box { if (search.isEmpty()) Text("Search your family", color = Color.White.copy(alpha = .65f), fontSize = 14.sp); field() } })
                if (search.isNotEmpty()) IconButton(onClick = { search = "" }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Close, "Close search", tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
            if (family.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(family, key = { it.jid }) { member ->
                    Column(Modifier.width(52.dp).heightIn(min = 52.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { selected = member }.semantics { contentDescription = "Contact details for ${callDisplayName(member.jid)}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        ContactAvatar(member.jid, 52, ring = false, online = state.serviceReady && member.presence == "online")

                    }
                }
            }
        } else {
            LandingIllustration(Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 24.dp).padding(bottom = 16.dp))
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), color = colors.panel, shape = RoundedCornerShape(topStart = if (signedIn) 40.dp else 32.dp, topEnd = if (signedIn) 0.dp else 32.dp)) {
            LazyColumn(contentPadding = PaddingValues(top = if (signedIn) 16.dp else 24.dp, bottom = if (signedIn) 112.dp else 104.dp)) {
                if (!signedIn) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Your family,\ncloser.", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                                Text("A private space for your mahram family. Share a message, hear a familiar voice, and stay close.", fontSize = 14.sp, lineHeight = 21.sp, color = colors.muted)
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
                        if (invitations) Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Invitations", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                            TextButton(onClick = { invitations = !invitations }) {
                                Text(if (invitations) "Back to chats" else if (requests.isEmpty()) "Invites" else "Invites · ${requests.size}", color = colors.accent, fontSize = 12.sp)
                            }
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
                            Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { if (member.status == "accepted" && !directory) actions.openChat(member.jid) else selected = member }
                                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ContactAvatar(member.jid, 48, online = member.status == "accepted" && state.serviceReady && member.presence == "online")
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(callDisplayName(member.jid), color = colors.ink, fontSize = 16.sp, letterSpacing = 0.sp, fontWeight = if (state.conversations.any { it.peer == member.jid && it.unread > 0 }) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val presence = if (signedIn && state.serviceReady) member.presence else "unknown"
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val conversation = state.conversations.find { it.peer == member.jid }
                                        Text((if (!directory) conversation?.lastMessage?.text else null) ?: when (member.status) { "incoming" -> "Invited you to join"; "outgoing" -> "Waiting for acceptance"
                                            else -> when (presence) { "online" -> "Online"; "offline" -> "Offline"; else -> "Status unavailable" } },
                                            color = if ((conversation?.unread ?: 0) > 0) colors.ink else colors.muted,
                                            fontWeight = if ((conversation?.unread ?: 0) > 0) FontWeight.Medium else FontWeight.Normal,
                                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (!directory) state.conversations.find { it.peer == member.jid }?.let { summary ->
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        summary.lastMessage?.let { Text(familyTime(it.createdAt), fontSize = 10.sp, color = colors.muted) }
                                        if (summary.unread > 0) Surface(
                                            color = FamilyVisuals.peach, contentColor = FamilyVisuals.forest, shape = CircleShape,
                                            modifier = Modifier.semantics { contentDescription = "${summary.unread} unread messages" },
                                        ) {
                                            Text(summary.unread.coerceAtMost(99).toString(),
                                                Modifier.padding(horizontal = 9.dp, vertical = 2.dp), fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                if (directory && member.status == "accepted") IconButton(onClick = { actions.voice(member.jid) }, enabled = canCall) {
                                    Icon(Icons.Default.Call, "Call ${callDisplayName(member.jid)}", tint = colors.accent)
                                }
                                if (member.status != "accepted") Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "View invitation", Modifier.size(20.dp), tint = colors.muted)
                            }
                            if (member.status == "incoming") Row(Modifier.padding(start = 88.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { actions.accept(member.jid) }, enabled = state.serviceReady, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.button, contentColor = colors.onButton)) { Text("Accept", fontSize = 12.sp) }
                                TextButton(onClick = { removing = member }) { Text("Decline", fontSize = 12.sp, color = colors.muted) }
                            }
                            HorizontalDivider(Modifier.padding(start = 88.dp, end = 24.dp), color = colors.line.copy(alpha = 0.22f))
                        }
                    }
                }
                if (signedIn) state.pushRegistrationError?.let { message -> item {
                    Text(message, Modifier.padding(horizontal = 28.dp, vertical = 12.dp), fontSize = 12.sp, color = colors.muted)
                } }
                state.error?.takeIf { !creatingAccount || signedIn }?.let { message -> item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth().background(colors.field, RoundedCornerShape(16.dp)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = colors.accent)
                        Text(message, Modifier.weight(1f).padding(10.dp), color = colors.ink, fontSize = 12.sp, lineHeight = 18.sp)
                        IconButton(onClick = actions.dismissError) { Icon(Icons.Default.Close, "Dismiss message", tint = colors.muted, modifier = Modifier.size(18.dp)) }
                    }
                } }
            }
        }
    }
    if (signedIn) FamilyBottomBar(directory, state.unreadCount,
        contacts = { directory = true; invitations = false }, chats = { directory = false; invitations = false },
        invite = { inviting = true }, alerts = { inbox = true; actions.acknowledgeNotifications() }, settings = { settings = true },
        modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = colors.panel, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Family settings", Modifier.weight(1f), color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = { settings = false }) { Icon(Icons.Default.Close, "Close settings", tint = colors.muted) }
            }
            Text(state.me, color = colors.muted, fontSize = 13.sp)
            TextButton(onClick = { settings = false; invitations = true }) { Text("Invitations") }
            TextButton(onClick = { settings = false; actions.webHelper() }) { Text("Temporary call alerts") }
            TextButton(onClick = { settings = false; actions.refresh() }) { Text("Refresh family") }
            TextButton(onClick = { settings = false; actions.exit() }) { Text("Back to app") }
            TextButton(onClick = { settings = false; actions.signOut() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (inbox) ModalBottomSheet(onDismissRequest = { inbox = false }, containerColor = colors.panel, dragHandle = null, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                IconButton(onClick = { inbox = false }) { Icon(Icons.Default.Close, "Close notifications", tint = colors.muted) }
            } }
            if (state.notifications.isEmpty()) item { Text("You’re all caught up.", color = colors.muted) }
            items(state.notifications, key = { it.id }) { notice ->
                Row(Modifier.fillMaxWidth().clickable {
                    inbox = false
                    if (notice.kind == "invitation") invitations = true
                    else if (notice.kind == "message") actions.openChat(notice.peer)
                    else selected = state.members.find { it.jid == notice.peer }
                }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(notice.peer, 44)
                    Column(Modifier.weight(1f)) {
                        Text(callDisplayName(notice.peer), fontWeight = FontWeight.Medium, color = colors.ink)
                        Text(familyNoticeText(notice.kind, notice.count), fontSize = 12.sp, color = colors.muted)
                    }
                    Text(familyTime(notice.createdAt), fontSize = 10.sp, color = colors.muted)
                }
            }
        }
    }
    if (inviting) ModalBottomSheet(onDismissRequest = { inviting = false }, containerColor = colors.panel, dragHandle = null, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Invite family", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                IconButton(onClick = { inviting = false }) { Icon(Icons.Default.Close, "Close invitation", tint = colors.muted) }
            }
            Text("Invite a mahram family member. You can message and call after they accept.", fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp, color = colors.muted)
            FamilyField(invitation, { invitation = it }, "Family account", colors, leading = Icons.Default.PersonOutline)
            Button(onClick = { actions.invite(invitation); invitation = ""; inviting = false; invitations = true }, enabled = state.serviceReady && invitation.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.button, contentColor = colors.onButton)) { Text("Send invitation") }
        }
    }
    selected?.let { member -> Dialog(onDismissRequest = { selected = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(colors.panel)) {
        val profileView = androidx.compose.ui.platform.LocalView.current
        val lightNavigation = colors.panel.luminance() > .5f
        DisposableEffect(profileView, lightNavigation) {
            (profileView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, profileView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = lightNavigation
                }
            }
            onDispose { }
        }
        FamilyContactProfile(
            member = member, state = state, actions = actions,
            onBack = { selected = null },
            onMessage = { selected = null; actions.openChat(member.jid) },
            onRemove = { selected = null; removing = member },
            bottomClearance = 112.dp,
        )
        FamilyBottomBar(directory = true, unread = state.unreadCount,
            contacts = { selected = null; directory = true }, chats = { selected = null; if (member.status == "accepted") actions.openChat(member.jid) },
            invite = { selected = null; inviting = true }, alerts = { selected = null; inbox = true; actions.acknowledgeNotifications() },
            settings = { selected = null; settings = true }, modifier = Modifier.align(Alignment.BottomCenter))
    } } }
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(if (member.status == "accepted") "Remove ${callDisplayName(member.jid)}?" else "Remove invitation?") },
        text = { Text("Removing this member also deletes your shared chat history. You’ll need a new accepted invitation to connect again.") },
        confirmButton = { TextButton(onClick = { actions.remove(member.jid); removing = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } }) }
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
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.family_landing_hero),
        contentDescription = null, modifier = modifier.clip(RoundedCornerShape(24.dp)),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop)

}

@Composable
internal fun ContactAvatar(jid: String, size: Int, ring: Boolean = false, online: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val initials = callDisplayName(jid).split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
    val shades = listOf(scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceContainerHigh to scheme.onSurface)
    val shade = shades[(jid.hashCode() and Int.MAX_VALUE) % shades.size]
    Box(Modifier.size(size.dp)) {
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }.then(if (ring) Modifier.border(1.5.dp, scheme.primary, CircleShape).padding(4.dp) else Modifier)
        .background(shade.first, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = shade.second, fontSize = (size * 0.30f).sp, fontWeight = FontWeight.Medium)
    }
    if (online) Box(Modifier.align(Alignment.TopEnd).size(11.dp).border(2.dp, scheme.surface, CircleShape).background(FamilyVisuals.online, CircleShape))
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
        val framedVideo = call.kind == "video" && call.stage == "active" && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 500
        val lightStatusBar = if (framedVideo) MaterialTheme.colorScheme.background.luminance() > .5f else !video && MaterialTheme.colorScheme.primaryContainer.luminance() > .5f
        val lightNavigationBar = (framedVideo || !video) && MaterialTheme.colorScheme.surface.luminance() > .5f
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
    FamilyDesign { FamilyCallBody(state, elapsed, actions, remoteReady, localReady, remoteVideo, localVideo) }
}

@Composable
private fun FamilyCallBody(state: FamilyUiState, elapsed: Long = 0, actions: FamilyCallActions = FamilyCallActions(),
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
        if (video && !compact) {
            FamilyVideoCallLayout(state, status, actions, remoteReady, localReady, remoteVideo, localVideo)
            return@BoxWithConstraints
        }
        if (preview) Box(Modifier.fillMaxSize()) { localVideo() }
        if (video) Box(Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            remoteVideo()
            if (!remoteReady) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ContactAvatar(call.peer(state.me), 72)
                Text("Waiting for video…", color = colors.onSurfaceVariant, fontSize = 12.sp)
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
            if (call.stage == "active") Text(FamilyConnectionRoute().label(state.connectionRoute),
                Modifier.padding(top = 4.dp).background(if (immersive) Color.Black.copy(alpha = .25f) else colors.surfaceContainerHigh,
                    RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (preview) Arrangement.Top else Arrangement.Center) {
                if (video) Box(Modifier.fillMaxSize().padding(bottom = 18.dp), contentAlignment = Alignment.BottomEnd) {
                    Box(Modifier.width(104.dp).height(148.dp).clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        if (state.camera && localReady) localVideo()
                        else Icon(Icons.Default.VideocamOff, "Your camera is off", tint = colors.onSurfaceVariant)
                        Text("You", Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = .4f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 10.sp)
                    }
                }
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
                    if (compact || video) CallControl(Icons.Default.CallEnd, "End call", color = colors.error, onVideo = immersive, action = actions.hangup)
                }
                if (!compact) Spacer(Modifier.height(24.dp))
            }
            if ((!compact && !video) || call.stage != "active") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (incoming) CallControl(if (call.kind == "video") Icons.Default.Videocam else Icons.Default.Call, "Accept", color = colors.primary, large = true, onVideo = video, action = actions.answer)
                CallControl(Icons.Default.CallEnd, if (incoming) "Decline" else if (call.stage == "ringing") "Cancel" else "End call",
                    color = colors.error, large = incoming, onVideo = immersive, action = actions.hangup)
            }
        }
    }
}

internal fun callDisplayName(jid: String): String = jid.substringBefore('@').replace('.', ' ').replace('_', ' ').replace('-', ' ')
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
