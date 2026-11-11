package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FamilyCallHistoryCard(message: FamilyChatMessage, outgoing: Boolean) {
    val call = message.call ?: return
    val policy = FamilyCallHistoryPolicy()
    val colors = MaterialTheme.colorScheme
    val missed = policy.missed(call.outcome, outgoing)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
        Surface(Modifier.widthIn(max = 320.dp).fillMaxWidth().semantics(mergeDescendants = true) {},
            color = colors.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(if (missed) colors.errorContainer else colors.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(if (call.kind == "video") Icons.Outlined.Videocam else Icons.Outlined.Call, null,
                        Modifier.size(23.dp), tint = if (missed) colors.error else colors.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(policy.title(call.kind), color = if (missed) colors.error else colors.onSurface,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${policy.direction(outgoing)} · ${policy.status(call.outcome, outgoing)}",
                        color = colors.onSurfaceVariant, fontSize = 12.sp)
                    val duration = call.durationSeconds?.let { " · ${policy.duration(it)}" }.orEmpty()
                    Text(familyTime(message.createdAt) + duration, color = colors.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
    }
}
