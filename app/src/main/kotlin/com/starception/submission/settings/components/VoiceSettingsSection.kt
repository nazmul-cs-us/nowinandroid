package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Voice recognition engine options
 */
enum class VoiceRecognitionEngine(
    val displayName: String,
    val description: String,
    val speed: String
) {
    SHERPA_KWS(
        displayName = "Fast Keyword Spotting",
        description = "Real-time yes/no detection using Sherpa-ONNX. Optimized for quick hands-free responses.",
        speed = "~100ms"
    ),
    WHISPER(
        displayName = "Whisper (Full Transcription)",
        description = "Full speech-to-text using Whisper model. More accurate but slower.",
        speed = "~26 seconds"
    )
}

/**
 * Voice settings state
 */
data class VoiceSettingsState(
    val selectedEngine: VoiceRecognitionEngine = VoiceRecognitionEngine.SHERPA_KWS
)

/**
 * Voice Settings Section - allows users to select voice recognition engine
 */
@Composable
fun VoiceSettingsSection(
    state: VoiceSettingsState,
    onEngineSelected: (VoiceRecognitionEngine) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.selectableGroup()) {
        Text(
            text = "Voice Recognition Engine",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        VoiceRecognitionEngine.entries.forEach { engine ->
            VoiceEngineCard(
                engine = engine,
                isSelected = state.selectedEngine == engine,
                onSelect = { onEngineSelected(engine) }
            )
            if (engine != VoiceRecognitionEngine.entries.last()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun VoiceEngineCard(
    engine: VoiceRecognitionEngine,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect
            )
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null // handled by selectable
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = engine.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = engine.speed,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (engine == VoiceRecognitionEngine.SHERPA_KWS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = engine.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
