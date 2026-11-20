package com.starception.submission.feature.surah

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.settings.components.TtsVoiceSelectionSheet
import com.starception.submission.settings.components.isTtsVoiceModelAvailable
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors

internal data class SurahTtsSelection(val voice: TtsVoice, val speakerId: Int)

internal fun readSurahTtsSelection(prefs: SharedPreferences): SurahTtsSelection {
    val voice = TtsVoice.entries.firstOrNull { it.name == prefs.getString("selected_voice", null) }
        ?: TtsVoice.KOKORO_EN
    return SurahTtsSelection(voice, prefs.getInt("selected_speaker_id", 0).coerceIn(0, voice.totalSpeakers - 1))
}

internal fun selectSurahTtsVoice(prefs: SharedPreferences, voice: TtsVoice) {
    prefs.edit().putString("selected_voice", voice.name).putInt("selected_speaker_id", 0).apply()
}

internal fun selectSurahTtsSpeaker(prefs: SharedPreferences, speakerId: Int) {
    val voice = readSurahTtsSelection(prefs).voice
    prefs.edit().putInt("selected_speaker_id", speakerId.coerceIn(0, voice.totalSpeakers - 1)).apply()
}

@Composable
private fun rememberTtsSelection(prefs: SharedPreferences): State<SurahTtsSelection> {
    val selection = remember(prefs) { mutableStateOf(readSurahTtsSelection(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "selected_voice" || key == "selected_speaker_id") selection.value = readSurahTtsSelection(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return selection
}

@Composable
internal fun SurahTtsSettingsEntry(onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE) }
    val selection by rememberTtsSelection(prefs)
    SurahTtsSettingsRow(onClick, selection.voice, selection.speakerId)
}

/** The same app-owned model and speaker picker used by the other reading screens. */
@Composable
internal fun SurahTtsVoiceSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE) }
    val selection by rememberTtsSelection(prefs)
    val speech = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, SherpaOnnxTtsEntryPoint::class.java)
            .sherpaOnnxTtsService()
    }
    TtsVoiceSelectionSheet(
        selectedVoice = selection.voice,
        selectedSpeakerId = selection.speakerId,
        supportingText = "Same voice and speaker as Settings → Text to speech. Used for English narration.",
        ttsService = speech,
        isVoiceAvailable = { isTtsVoiceModelAvailable(context, it) },
        onVoiceSelected = { voice ->
            if (voice != selection.voice) {
                selectSurahTtsVoice(prefs, voice)
                speech.clearCache()
                speech.setVoice(voice)
            }
        },
        onSpeakerChanged = {
            selectSurahTtsSpeaker(prefs, it)
            speech.clearCache()
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun SurahTtsSettingsRow(onClick: () -> Unit, voice: TtsVoice, speakerId: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Choose narration voice", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("TTS voice settings", style = MaterialTheme.typography.bodyMedium)
            Text("${voice.displayName} · Speaker $speakerId", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
