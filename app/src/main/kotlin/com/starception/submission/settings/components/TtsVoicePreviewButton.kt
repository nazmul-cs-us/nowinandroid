package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.NiaOutlinedButton

const val TTS_VOICE_SAMPLE_TEXT =
    "Assalamu alaikum, this is your selected narration voice."

/** The standard voice-preview action used by every TTS voice picker. */
@Composable
fun TtsVoicePreviewButton(
    isPreparing: Boolean,
    isPlaying: Boolean,
    isVoiceAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NiaOutlinedButton(
        onClick = onClick,
        enabled = isVoiceAvailable || isPreparing || isPlaying,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        when {
            isPreparing -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )

            isPlaying -> Icon(
                imageVector = Icons.Default.StopCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )

            else -> Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when {
                !isVoiceAvailable -> "Voice model not downloaded"
                isPreparing -> "Preparing sample…"
                isPlaying -> "Stop sample"
                else -> "Play voice sample"
            },
        )
    }
}
