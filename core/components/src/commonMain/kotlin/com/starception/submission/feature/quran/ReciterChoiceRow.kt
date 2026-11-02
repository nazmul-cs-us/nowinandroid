package com.starception.submission.feature.quran

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** An accessible choice with a stable selection layout used by both platform readers. */
@Composable
fun ReciterChoiceRow(
    name: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPreview: (() -> Unit)? = null,
    previewing: Boolean = false,
    previewLoading: Boolean = false,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else Color.Transparent,
        animationSpec = tween(160), label = "reciterSelectionColor",
    )
    val outline by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(160), label = "reciterSelectionOutline",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = background,
        border = if (selected) BorderStroke(1.dp, outline.copy(alpha = 0.25f)) else null,
    ) {
        Row(
            Modifier.selectable(selected = selected, enabled = enabled,
                role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onPreview != null) {
                FilledTonalIconButton(onClick = onPreview, enabled = enabled,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (previewing) "Stop preview of $name" else "Preview $name"
                    }) {
                    if (previewLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null)
                    }
                }
            } else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Headphones, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (description.isNotBlank()) Text(description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled,
                modifier = Modifier.size(24.dp))
        }
    }
}
