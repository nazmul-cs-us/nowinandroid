package com.starception.submission.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.produceState
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
fun ReciterStorageSection(
    initiallyExpanded: Boolean = false,
    manageDownloads: Boolean = true,
    surahNumber: Int? = null,
    onReciterSelected: () -> Unit = {},
    sectionTitle: String = "Quran recitations",
) {
    val context = LocalContext.current
    val manager = remember(context) { ReciterDownloadManager.get(context.applicationContext) }
    var reciters by remember { mutableStateOf<List<ReciterCatalog.Reciter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedReciterId by remember { mutableStateOf<String?>(null) }

    val preferences = remember(context) { context.getSharedPreferences("quran_reciter", android.content.Context.MODE_PRIVATE) }
    var preferredReciterId by remember { mutableStateOf(preferences.getString("selected_id", null)) }
    var query by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var fetchedRevision by remember { mutableStateOf(-1) }
    LaunchedEffect(refresh, expanded) {
        if (!expanded || fetchedRevision == refresh) return@LaunchedEffect
        loading = true
        error = null
        try {
            reciters = ReciterCatalog.fetch(context)
            fetchedRevision = refresh
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to load reciters"
        } finally {
            loading = false
        }
    }

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Column {
            if (!initiallyExpanded || manageDownloads) Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(sectionTitle, style = MaterialTheme.typography.titleSmall)
                    Text("Reciters & offline audio", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide reciters" else "Show reciters")
            }
            if (expanded) Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    placeholder = { Text("Search reciters") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    } },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true)
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { refresh++ }) { Text("Try again") }
                }
                Text(if (manageDownloads) "Manage voices and saved audio" else "Choose a voice for Arabic recitation",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val filtered = remember(reciters, surahNumber, query) {
                    reciters.filter { (surahNumber == null || surahNumber in it.surahs) &&
                        (it.name + " " + it.riwaya).contains(query.trim(), true) }
                }
                val showDefault = "Mishary Rashid Al-Alafasy default".contains(query.trim(), true)
                if (!loading && filtered.isEmpty() && !showDefault) Text("No reciters found", style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showDefault) item(key = "default-cdn") {
                com.starception.submission.feature.quran.ReciterChoiceRow(
                    name = "Mishary Rashid Al-Alafasy", description = "Default recitation",
                    selected = preferredReciterId == null,
                    onSelect = {
                        preferences.edit().remove("selected_id").remove("selected_server").remove("selected_surahs")
                            .putString("selected_name", "Mishary Rashid Al-Alafasy").apply()
                        preferredReciterId = null
                        onReciterSelected()
                    },
                )
                    }
                    items(filtered, key = { it.id }) { reciter ->
                        val status by produceState(
                            ReciterDownloadManager.DownloadStatus(totalSurahs = reciter.surahs.size),
                            reciter.id, manageDownloads,
                        ) {
                            if (manageDownloads) withContext(Dispatchers.IO) {
                                manager.observe(reciter).collect { value = it }
                            }
                        }
                        val open = expandedReciterId == reciter.id || status.downloading
                        Column {
                            com.starception.submission.feature.quran.ReciterChoiceRow(
                                name = reciter.name,
                                description = if (manageDownloads) "${status.downloadedSurahs}/${status.totalSurahs} surahs offline"
                                    else reciter.riwaya.ifBlank { "Arabic recitation" },
                                selected = preferredReciterId == reciter.id,
                                onSelect = {
                                    preferences.edit().putString("selected_id", reciter.id)
                                        .putString("selected_name", reciter.name + " · " + reciter.riwaya)
                                        .putString("selected_server", reciter.server)
                                        .putString("selected_surahs", reciter.surahs.joinToString(",")).apply()
                                    preferredReciterId = reciter.id
                                    onReciterSelected()
                                },
                            )
                            if (manageDownloads) TextButton(onClick = {
                                expandedReciterId = if (expandedReciterId == reciter.id) null else reciter.id
                            }) {
                                Text(if (open) "Hide downloads" else "Offline audio" +
                                    (if (status.downloadedBytes > 0) " · ${status.downloadedBytes / (1024 * 1024)} MB" else ""))
                            }
                            if (manageDownloads && open) {
                                if (status.downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("${status.downloadedSurahs} of ${status.totalSurahs} surahs saved", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                status.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = {
                                        if (status.downloading) manager.cancel(reciter.id) else manager.download(reciter)
                                    }, enabled = status.downloading || status.downloadedSurahs < status.totalSurahs) {
                                        Text(if (status.downloading) "Cancel" else "Download all")
                                    }
                                    if (status.downloadedSurahs > 0) TextButton(onClick = { manager.delete(reciter) }, enabled = !status.downloading) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (manageDownloads) Spacer(Modifier.height(4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
