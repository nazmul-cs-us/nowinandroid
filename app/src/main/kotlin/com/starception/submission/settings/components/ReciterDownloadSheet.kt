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
    val status by produceState(ReciterDownloadManager.DownloadStatus(totalSurahs = reciter.surahs.size), reciter.id) {
        withContext(Dispatchers.IO) { manager.observe(reciter).collect { value = it } }
    }
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
            ReciterChoiceRow(name = reciter.name, description = reciter.riwaya,
                selected = selection.id == reciter.id, onSelect = { store.select(reciter) })
            ReciterOfflineActions(status,
                onDownload = { manager.download(reciter) }, onCancel = { manager.cancel(reciter.id) },
                onRemove = { manager.delete(reciter) })
        }
    }
}
