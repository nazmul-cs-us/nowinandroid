package com.starception.submission.settings.components

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.MissingContentCard
import com.starception.submission.voice.handsfree.HandsFreeStatus
import com.starception.submission.voice.handsfree.HandsFreeVoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal fun deenlyIsDefaultAssistant(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 29) return context.getSystemService(RoleManager::class.java)
        .let { it.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && it.isRoleHeld(RoleManager.ROLE_ASSISTANT) }
    val selected = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
    return selected?.let(ComponentName::unflattenFromString)?.packageName == context.packageName
}

internal fun assistantChoiceIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= 29) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !roles.isRoleHeld(RoleManager.ROLE_ASSISTANT))
            return roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }
    return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
}

@Composable
internal fun HandsFreeVoiceSection(downloadManager: AssetDownloadManager?, onModelsChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val status by HandsFreeVoiceState.status.collectAsStateWithLifecycle()
    var selectedAssistant by remember { mutableStateOf(deenlyIsDefaultAssistant(context)) }
    var revision by remember { mutableIntStateOf(0) }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val models by produceState<Pair<Boolean, Boolean>?>(null, revision) {
        value = withContext(Dispatchers.IO) {
            fun bundled(path: String) = runCatching { context.assets.open(path).use { it.available() > 0 } }.getOrDefault(false)
            val kws = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt").all {
                downloadManager?.getLocallyAvailableAssetFile("models/kws/$it") != null || bundled("kws/$it")
            }
            val whisper = bundled("models/ggml-tiny.en.bin") ||
                File(context.filesDir, "cdn_assets/models/ggml-tiny.en.bin").let { it.isFile && it.length() > 100_000 }
            kws to whisper
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { selectedAssistant = deenlyIsDefaultAssistant(context); revision++ }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun enableWhenVisible() {
        scope.launch { lifecycle.withResumed { HandsFreeVoiceState.enable(context) } }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (HandsFreeVoiceState.hasPermissions(context)) enableWhenVisible()
        else error = "Allow microphone and notification access in Android app settings to use hands-free mode."
    }
    val assistantPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        selectedAssistant = deenlyIsDefaultAssistant(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HandsFreeVoiceOptions(status, models?.let { it.first && it.second } == true, selectedAssistant,
            onToggle = { if (it) consent = true else HandsFreeVoiceState.disable(context) },
            onChooseAssistant = {
                try { assistantPicker.launch(assistantChoiceIntent(context)) }
                catch (_: RuntimeException) {
                    try { assistantPicker.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                    catch (_: RuntimeException) { error = "Default-assistant selection is unavailable on this device." }
                }
            })
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (models?.first == false && downloadManager != null) MissingContentCard(
            resourceName = "Keyword Detection Model", category = "model_kws",
            description = "Required for on-device “Hey Deenly” detection.",
            downloadManager = downloadManager, onDownloadComplete = { revision++; onModelsChanged() })
        if (models?.second == false && downloadManager != null) MissingContentCard(
            resourceName = "Full Transcription (Whisper)", category = "model_whisper",
            description = "Required to understand the command after the wake phrase.",
            downloadManager = downloadManager, onDownloadComplete = { revision++; onModelsChanged() })
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text("Enable hands-free microphone?") },
        text = { Text("Deenly will listen for “Hey Deenly” while the screen is off, then recognize a Quran playback command. " +
            "Audio is processed on-device. A microphone notification stays visible. This uses extra battery and stops after 12 hours, " +
            "when you turn it off, close the app, or restart your phone. It does not start automatically.",
            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = {
            consent = false
            error = null
            if (HandsFreeVoiceState.hasPermissions(context)) enableWhenVisible()
            else permissions.launch(if (Build.VERSION.SDK_INT >= 33)
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.RECORD_AUDIO))
        }) { Text("Enable") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
}

@Composable
internal fun HandsFreeVoiceOptions(status: HandsFreeStatus, modelsReady: Boolean, assistantSelected: Boolean,
    onToggle: (Boolean) -> Unit, onChooseAssistant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(status.enabled, enabled = modelsReady || status.enabled, role = Role.Switch, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Hey Deenly", style = MaterialTheme.typography.titleSmall)
                Text("Screen-off Quran commands · Beta", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = status.enabled, enabled = modelsReady || status.enabled, onCheckedChange = null)
        }
        Text(if (status.enabled) status.message else "Off by default. Say “Hey Deenly”, wait for the tone, then “play Bakarah”, “pause”, “resume”, or “stop”.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status.phase == com.starception.submission.voice.handsfree.HandsFreePhase.ERROR)
            Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text("Microphone notification required · extra battery use · 12-hour sessions. Downloaded recitations play offline.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        OutlinedButton(onClick = onChooseAssistant, modifier = Modifier.fillMaxWidth()) {
            Text(if (assistantSelected) "Deenly is your default assistant" else "Choose Deenly as default assistant")
        }
        Text("Android controls the assistant button/gesture and may require unlocking. Choosing Deenly does not enable hands-free listening.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
