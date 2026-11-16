package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.feature.quran.ReciterCatalog
import com.starception.submission.feature.quran.ReciterChoiceRow
import com.starception.submission.feature.quran.ReciterDownloadManager
import com.starception.submission.feature.quran.ReciterSelectionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Search opens the same live download actions without changing the selected voice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReciterDownloadSheet(reciter: ReciterCatalog.Reciter, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { ReciterDownloadManager.get(context.applicationContext) }
    val store = remember(context) { ReciterSelectionStore(context) }
    val selection by store.selections.collectAsStateWithLifecycle(initialValue = store.current())
    var recordings by remember(reciter.id) { mutableStateOf(listOf(reciter).filter { com.starception.submission.feature.quran.hasCompleteQuran(it.surahs) }) }
    LaunchedEffect(reciter.id) {
        val key = com.starception.submission.core.images.reciterPersonKey(reciter.name)
        recordings = (listOf(reciter) + ReciterCatalog.cached(context).orEmpty()).filter {
            com.starception.submission.feature.quran.hasCompleteQuran(it.surahs) &&
                com.starception.submission.core.images.reciterPersonKey(it.name) == key
        }.distinctBy { it.id }
    }
    val person = com.starception.submission.feature.quran.groupReciters(recordings, { it.id }, { it.name }).firstOrNull() ?: return
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reciter & offline audio", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close reciter downloads")
                }
            }
            com.starception.submission.feature.quran.ReciterRecordingChooser(person,
                id = { it.id }, style = { it.riwaya }, subtitle = { "114 surahs" }, preferredId = reciter.id,
                savedIds = emptySet(), onChange = {}) { recording ->
                val current = recording.copy(name = person.name)
                val status by produceState(ReciterDownloadManager.DownloadStatus(totalSurahs = 114), current.id) {
                    withContext(Dispatchers.IO) { manager.observe(current).collect { value = it } }
                }
                Column {
                    ReciterChoiceRow(name = person.name, description = com.starception.submission.feature.quran.reciterRecordingLabel(current.riwaya),
                        selected = selection.id == current.id, onSelect = { store.select(current) })
                    ReciterOfflineActions(status,
                        onDownload = { manager.download(current) }, onCancel = { manager.cancel(current.id) },
                        onRemove = { manager.delete(current) })
                }
            }
        }
    }
}
