package com.starception.submission.feature.prayertimes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.FluidReveal

/** Home and the reader share the same reversible stretch → neck → split mechanism. */
@Composable
internal fun FluidHomeReveal(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = 0.dp,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable (enabled: Boolean, collapse: () -> Unit) -> Unit,
) {
    val panelContent = Color(0xFFE3E7F2)
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    FluidReveal(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        containerColor = Color(0xFF1B1E2A).copy(alpha = .97f),
        outlineColor = Color(0xFF323B52),
        contentColor = panelContent,
        modifier = modifier.testTag("homeExploreReveal"),
        bottomClearance = bottomClearance,
        header = {
            Column(
                Modifier.fillMaxWidth()
                    .then(if (expanded) Modifier.clickable(role = Role.Button) { onExpandedChange(false) } else Modifier)
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = HomeTypography.section, color = panelContent)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = HomeTypography.body, textAlign = TextAlign.Center,
                    color = panelContent.copy(alpha = .78f))
            }
        },
        content = content,
    )
}
