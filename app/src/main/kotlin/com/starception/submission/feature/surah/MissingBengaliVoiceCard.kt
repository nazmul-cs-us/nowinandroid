package com.starception.submission.feature.surah

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.starception.submission.BuildConfig
import com.starception.submission.download.MissingContentCard
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.settings.components.isTtsVoiceModelAvailable
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** A playback request opens the app's standard missing-content download surface. */
internal object MissingBengaliVoice {
    val requested = MutableStateFlow(false)
    const val category = "model_tts_mms_bengali"
    const val licenseWarning = "MMS Bengali · CC BY-NC 4.0. Non-commercial use only. Commercial use requires separate permission. Downloading does not grant commercial rights."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MissingBengaliVoiceCard() {
    val requested by MissingBengaliVoice.requested.collectAsState()
    if (!requested) return
    val context = LocalContext.current.applicationContext
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, SherpaOnnxTtsEntryPoint::class.java).assetDownloadManager()
    }
    var acknowledged by remember { mutableStateOf(false) }
    var published by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(manager) {
        published = withContext(Dispatchers.IO) {
            runCatching {
                val cached = manager.loadManifest()
                (if (cached?.categories?.containsKey(MissingBengaliVoice.category) == true) cached
                    else manager.refreshManifest())?.categories?.containsKey(MissingBengaliVoice.category) == true
            }
                .getOrDefault(false)
        }
    }
    ModalBottomSheet(onDismissRequest = { MissingBengaliVoice.requested.value = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bengali voice model missing", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(MissingBengaliVoice.licenseWarning, Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                Text("I understand the non-commercial license restriction.", Modifier.weight(1f))
            }
            when {
                !BuildConfig.BENGALI_MMS_PREVIEW -> Text(
                    "MMS downloads are disabled in this build while distribution permission is unresolved.",
                    Modifier.padding(horizontal = 24.dp))
                published == null -> CircularProgressIndicator(Modifier.padding(horizontal = 24.dp))
                published == false -> Text("The Bengali model is not available from the download catalog yet. No download has started.",
                    Modifier.padding(horizontal = 24.dp))
                acknowledged -> MissingContentCard(
                    resourceName = "MMS Bengali voice",
                    category = MissingBengaliVoice.category,
                    description = "${MissingBengaliVoice.licenseWarning} Download once for offline Bengali Tafsir narration.",
                    downloadManager = manager,
                    onDownloadComplete = {
                        if (isTtsVoiceModelAvailable(context, TtsVoice.MMS_BENGALI)) {
                            MissingBengaliVoice.requested.value = false
                        } else message = "The voice model is still incomplete. Please retry the download."
                    },
                )
            }
            Text(message ?: "After downloading, tap Tafsir play again. Playback will not start automatically.",
                Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
