package com.starception.submission.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.ubuntu_regular
import org.jetbrains.compose.resources.Font

private val wordmarkRise = CubicBezierEasing(.22f, .61f, .36f, 1f)

/** The whole word rises from behind a fixed lower edge, without fading or scaling. */
@Composable
fun DeenlySplashWordmark(progress: () -> Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds().clearAndSetSemantics { contentDescription = "Deenly" },
        contentAlignment = Alignment.Center) {
        val travel = maxHeight
        Text(
            text = "Deenly",
            fontFamily = FontFamily(Font(Res.font.ubuntu_regular)),
            fontWeight = FontWeight.Normal,
            fontSize = (maxHeight.value * .68f).coerceAtMost(30f).sp,
            letterSpacing = .5.sp,
            color = Color(0xFFE5DCEB),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer {
                val reveal = wordmarkRise.transform((progress() / .58f).coerceIn(0f, 1f))
                translationY = travel.toPx() * (1f - reveal)
            },
        )
    }
}
