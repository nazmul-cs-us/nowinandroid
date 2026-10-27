package com.starception.submission.feature.prayertimes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment

/** A source detail stays attached to the passage through a curved intermediate silhouette. */
@Composable
internal fun FlowingSourceDisclosure(reference: String, selection: String) {
    var expanded by rememberSaveable(reference) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "hadithSourceChevron",
    )
    val surface = Color(0xFF152A3B)
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(20.dp),
            color = surface,
            modifier = Modifier.semantics {
                stateDescription = if (expanded) "Source details expanded" else "Source details collapsed"
            },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hadith source", color = Color.White,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ExpandMore, contentDescription = null,
                    tint = Color.White.copy(alpha = .82f),
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation })
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(360, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(180)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                    val center = size.width / 2f
                    val shoulder = 38.dp.toPx()
                    val neck = 13.dp.toPx()
                    val path = Path().apply {
                        moveTo(center - neck, 0f)
                        cubicTo(center - neck, size.height * .58f,
                            center - shoulder, size.height * .55f, center - shoulder, size.height)
                        lineTo(center + shoulder, size.height)
                        cubicTo(center + shoulder, size.height * .55f,
                            center + neck, size.height * .58f, center + neck, 0f)
                        close()
                    }
                    drawPath(path, surface)
                }
                Column(
                    Modifier.fillMaxWidth().background(surface, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(reference, color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(selection, color = Color.White.copy(alpha = .70f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
