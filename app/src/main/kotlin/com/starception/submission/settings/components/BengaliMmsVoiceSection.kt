package com.starception.submission.settings.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.starception.submission.BuildConfig
import com.starception.submission.feature.quran.SurahTafsirPlayback
import com.starception.submission.feature.surah.MissingBengaliVoice
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opt-in Bengali narration, restricted to explicitly non-commercial builds. */
@Composable
fun BengaliMmsVoiceSection() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("bengali_mms_enabled", false)) }
    var consent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val downloadSheetOpen by MissingBengaliVoice.requested.collectAsState()
    val available by produceState(false, downloadSheetOpen) {
        value = BuildConfig.BENGALI_MMS_PREVIEW && withContext(Dispatchers.IO) {
            isTtsVoiceModelAvailable(context, TtsVoice.MMS_BENGALI)
        }
    }
    val speech = remember(context) {
        if (BuildConfig.BENGALI_MMS_PREVIEW) EntryPointAccessors.fromApplication(
            context.applicationContext, SherpaOnnxTtsEntryPoint::class.java).bengaliMmsTtsService() else null
    }
    DisposableEffect(speech) { onDispose { if (preview?.isActive == true) { preview?.cancel(); speech?.stopSpeaking() } } }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Bengali Tafsir voice", style = MaterialTheme.typography.titleSmall)
        Text("MMS Bengali · CC BY-NC 4.0 · non-commercial use only",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (available) "Model ready. One Bengali speaker. Arabic quotations may not be pronounced correctly."
            else if (BuildConfig.BENGALI_MMS_PREVIEW) "Enable MMS, then play Bengali Tafsir to download the missing voice model."
            else "MMS is disabled in this build. A non-commercial build is required.",
            style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use MMS for Bengali Tafsir", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enabled && BuildConfig.BENGALI_MMS_PREVIEW, enabled = BuildConfig.BENGALI_MMS_PREVIEW, onCheckedChange = {
                if (it) consent = true else {
                    SurahTafsirPlayback.owner.stop()
                    speech?.stopSpeaking()
                    enabled = false
                    prefs.edit().putBoolean("bengali_mms_enabled", false).apply()
                }
            })
        }
        Text("When off, Bengali uses the device voice. English Kokoro/VCTK selections are unchanged.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(enabled = BuildConfig.BENGALI_MMS_PREVIEW, onClick = {
            if (!available) MissingBengaliVoice.requested.value = true
            else if (preview?.isActive == true) { preview?.cancel(); speech?.stopSpeaking(); preview = null }
            else preview = scope.launch {
                SurahTafsirPlayback.owner.stop()
                val result = speech?.speak("এটি বাংলা তাফসির পড়ার জন্য একটি কণ্ঠস্বরের নমুনা।", speakerId = 0) == true
                if (!result) message = "Could not play MMS Bengali. Check the local preview assets."
            }
        }) { Text(if (!available) "Download Bengali voice"
            else if (preview?.isActive == true) "Stop Bengali preview" else "Preview Bengali voice") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Non-commercial MMS voice") },
        text = { Text("MMS Bengali weights are CC BY-NC 4.0. Enable only for non-commercial use or if you have separate applicable permission. Downloading does not grant commercial rights.") },
        confirmButton = { TextButton(onClick = {
            SurahTafsirPlayback.owner.stop()
            enabled = true
            prefs.edit().putBoolean("bengali_mms_enabled", true).apply()
            consent = false
        }) { Text("Enable Bengali voice") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
}
