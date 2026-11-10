package com.starception.submission.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The same contact profile is used by the directory and a conversation's info action. */
@Composable
internal fun FamilyContactProfile(
    member: FamilyMember,
    state: FamilyUiState,
    actions: FamilyActions,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onRemove: (() -> Unit)? = null,
    backDescription: String = "Back to family",
    bottomClearance: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme
    val brand = familyBrandColor()
    val online = state.serviceReady && member.presence == "online"
    val accepted = member.status == "accepted"
    val canCall = accepted && state.serviceReady && state.call == null
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: activity?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    BackHandler(onBack = onBack)
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        val heroHeight = (maxHeight * .52f).coerceIn(280.dp, 420.dp) *
            LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(bottom = bottomClearance + 24.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(heroHeight)
                    .background(Brush.linearGradient(listOf(brand, androidx.compose.ui.graphics.lerp(brand, colors.primary, .35f)))),
            ) {
                // The account model has no photo. Keep the actual initials, not a stock portrait.
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width * .76f, size.height * .35f)
                    for (radius in listOf(.40f, .64f, .88f)) {
                        drawCircle(Color.White.copy(alpha = .07f), size.width * radius,
                            center, style = Stroke(1.dp.toPx()))
                    }
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, backDescription, tint = Color.White)
                }
                Box(Modifier.align(Alignment.Center).padding(bottom = 24.dp)) {
                    ContactAvatar(member.jid, 144)
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(callDisplayName(member.jid), color = Color.White,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (online) Box(Modifier.size(8.dp).background(FamilyVisuals.online, CircleShape))
                        Text(
                            when {
                                !accepted -> "Family invitation"
                                online -> "Online"
                                !state.serviceReady -> "Status unavailable"
                                member.presence == "offline" -> "Offline"
                                else -> "Family member"
                            },
                            color = Color.White.copy(alpha = .84f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FAMILY ACCOUNT", color = colors.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(member.jid, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (accepted) {
                            IconButton(onClick = { onBack(); actions.voice(member.jid) }, enabled = canCall,
                                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.primary)) {
                                Icon(Icons.Outlined.Call, "Voice call", Modifier.size(24.dp))
                            }
                            IconButton(onClick = { onBack(); actions.video(member.jid) }, enabled = canCall,
                                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.primary)) {
                                Icon(Icons.Outlined.Videocam, "Video call", Modifier.size(24.dp))
                            }
                        }
                    }
                }
                if (accepted) {
                    Button(
                        onClick = onMessage, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = brand, contentColor = Color.White),
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp))
                        Text("Message", Modifier.padding(start = 10.dp))
                    }
                } else {
                    Text(
                        if (member.status == "incoming") "Accept this invitation to start calling and messaging."
                        else "Your invitation is waiting for acceptance.",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (member.status == "incoming") {
                        Button(onClick = { actions.accept(member.jid); onBack() }, enabled = state.serviceReady) {
                            Text("Accept invitation")
                        }
                    }
                }
                onRemove?.let { remove ->
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .4f))
                    TextButton(onClick = remove, modifier = Modifier.fillMaxWidth()) {
                        Text(when (member.status) {
                            "accepted" -> "Remove from family"
                            "incoming" -> "Decline invitation"
                            else -> "Cancel invitation"
                        }, color = colors.error)
                    }
                }
            }
        }
    }
}
