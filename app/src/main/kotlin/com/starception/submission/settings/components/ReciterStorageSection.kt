package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.starception.submission.feature.quran.ReciterCatalog
import com.starception.submission.feature.quran.ReciterDownloadManager

/**
 * External reciter recordings are managed separately from manifest-backed assets.
 * This panel lives inside the regular Content & Storage settings section.
 */
@Composable
fun ReciterStorageSection() {
    val context = LocalContext.current
    val manager = remember(context) { ReciterDownloadManager.get(context.applicationContext) }
    var reciters by remember { mutableStateOf<List<ReciterCatalog.Reciter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            reciters = ReciterCatalog.fetch()
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to load reciters"
        } finally {
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quran reciter downloads", style = MaterialTheme.typography.titleMedium)
        if (loading) Text("Loading reciter catalog…")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && reciters.isEmpty() && error == null) Text("No reciters available")
        reciters.forEach { reciter ->
            val status by manager.observe(reciter).collectAsState()
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { selectedId = if (selectedId == reciter.id) null else reciter.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${reciter.name} · ${reciter.riwaya} — ${status.downloadedSurahs}/${status.totalSurahs} offline")
                }
                if (selectedId == reciter.id) {
                    Text(
                        "${status.downloadedBytes / (1024 * 1024)} MB stored",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (status.downloading) manager.cancel(reciter.id) else manager.download(reciter)
                        }) { Text(if (status.downloading) "Cancel" else "Download all") }
                        if (status.downloadedSurahs > 0) {
                            OutlinedButton(onClick = { manager.delete(reciter) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
