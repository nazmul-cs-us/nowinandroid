package com.starception.submission.feature.prayertimes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Uses the same reversible fluid separation as Home's Explore disclosure. */
@Composable
internal fun FlowingSourceDisclosure(reference: String, selection: String) {
    var expanded by rememberSaveable(reference) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "hadithSourceChevron",
    )
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    contentDescription = "Hadith source"
                    stateDescription = if (expanded) "Source details expanded" else "Source details collapsed"
                }
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "Hide source" else "Hadith source",
                color = Color.White,
                style = HomeTypography.cardTitle,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
        FluidHomeReveal(
            title = "Hadith source",
            subtitle = selection,
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) { _, _ ->
            Text(reference, style = HomeTypography.body, modifier = Modifier.fillMaxWidth())
        }
    }
}
