package com.starception.submission.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.lora_regular
import com.starception.submission.core.images.resources.ubuntu_regular
import org.jetbrains.compose.resources.Font

/** A crescent made of fine rays, with the rising wordmark placed quietly below it. */
@Composable
fun DeenlySplashScene(
    progress: () -> Float,
    onContinue: () -> Unit,
    continueLabel: String,
    versionLabel: String? = null,
    reminder: DeenlySplashReminder? = null,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xFF0C0E18), Color(0xFF191524)),
        )).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 420.dp
        // Position independently: moving the wordmark must not move the crescent up.
        DeenlySpikedCrescent(progress, Modifier.offset(y = if (compact) (-48).dp else (-50).dp)
            .size(if (compact) 104.dp else 148.dp))
        DeenlySplashWordmark(progress, Modifier.offset(y = if (compact) 36.dp else maxHeight * .22f)
            .size(width = if (compact) 160.dp else 190.dp, height = 44.dp))
        // Reserve room for Continue/version and keep short landscape screens uncluttered.
        if (reminder != null && maxHeight >= 700.dp) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * .72f + 46.dp)
                .widthIn(max = 350.dp).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("“${reminder.text}”", style = MaterialTheme.typography.bodyMedium.copy(
                    // Editorial quotation; retain native shaping for Bengali and Arabic.
                    fontFamily = if (reminder.text.any { it in '\u0600'..'\u06ff' || it in '\u0900'..'\u0dff' })
                        FontFamily.Default else FontFamily(Font(Res.font.lora_regular)),
                    fontWeight = FontWeight.Normal,
                    fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-.2).sp),
                    color = Color(0xFFF0E9F3), textAlign = TextAlign.Center, maxLines = 3)
                Text(splashReminderCitation(reminder.reference).uppercase(), style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily(Font(Res.font.ubuntu_regular)),
                    fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 1.6.sp),
                    color = Color(0xFFA99BB4), textAlign = TextAlign.Center)
            }
        }
        TextButton(onClick = onContinue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
            Text(continueLabel, color = Color(0xFFD7CBE5))
        }
        versionLabel?.takeIf { it.isNotBlank() }?.let { version ->
            Text(version, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 28.dp),
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFADA3BA))
        }
    }
}
