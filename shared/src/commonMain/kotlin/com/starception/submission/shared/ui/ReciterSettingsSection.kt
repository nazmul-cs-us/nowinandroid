package com.starception.submission.shared.ui

import com.starception.submission.feature.quran.ReciterGroup
import com.starception.submission.feature.quran.ReciterGroupFilters
import com.starception.submission.feature.quran.reciterGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.audio.*
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private data class ReciterDownloads(
    val active: String? = null,
    val completed: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val revision: Int = 0,
)

/** Keep downloads alive when the settings sheet closes. */
private object ReciterDownloadState {
    val state = MutableStateFlow(ReciterDownloads())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    fun start(source: QuranReciterSource, library: ReciterLibrary) {
        if (job?.isActive == true) return
        state.value = ReciterDownloads(active = source.id, total = source.availableSurahs.size)
        job = scope.launch {
            try {
                library.download(source) { done, total ->
                    state.value = state.value.copy(completed = done, total = total)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.value = state.value.copy(error = error.message ?: "Download failed. Please retry.")
            } finally {
                state.value = state.value.copy(active = null, revision = state.value.revision + 1)
            }
        }
    }
    fun cancel() { job?.cancel() }
}

@Composable
internal fun ReciterSettingsSection(manageDownloads: Boolean = false, initiallyExpanded: Boolean = false, surahNumber: Int? = null, onSelected: () -> Unit = {}) {
    val library = QuranReciterLibrary.platform ?: return
    val preferences = remember { QuranReciterPreferences() }
    var selected by remember { mutableStateOf(preferences.selectedReciterId()) }
    val store = remember { platformKeyValueStore() }
    var catalog by remember { mutableStateOf<List<QuranReciterSource>>(emptyList()) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var openedReciter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<ReciterGroup?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val download by ReciterDownloadState.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    val actionScope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var fetchedRevision by remember { mutableIntStateOf(-1) }
    LaunchedEffect(expanded, refresh) {
        if (!expanded || fetchedRevision == refresh) return@LaunchedEffect
        loading = true
        try {
            if (catalog.isEmpty()) catalog = withContext(Dispatchers.Default) {
                runCatching {
                    Json.decodeFromString<List<QuranReciterSource>>(store.getString("quran_reciter_catalog").orEmpty())
                }.getOrDefault(emptyList())
            }
            val result = withContext(Dispatchers.Default) { library.catalog() }
            check(result.isNotEmpty()) { "Unable to load reciters. Check your connection and retry." }
            catalog = result
            fetchedRevision = refresh
            withContext(Dispatchers.Default) {
                store.putString("quran_reciter_catalog", Json.encodeToString(result))
            }
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to load reciters."
        } finally { loading = false }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (!initiallyExpanded || manageDownloads) Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (manageDownloads) "Quran recitations" else "Reciter", style = MaterialTheme.typography.titleSmall)
                    Text(if (manageDownloads) "Reciters & offline audio" else
                        (preferences.selectedSource()?.displayName ?: "Mishary Rashid Al-Alafasy"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide reciters" else "Show reciters")
            }
            if (expanded) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        placeholder = { Text("Search reciters") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("reciter-search"), singleLine = true)
                    Box(Modifier.fillMaxWidth().height(4.dp)) {
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { refresh++ }) { Text("Try again") }
                    }
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (manageDownloads) download.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(if (manageDownloads) "Manage voices and saved audio" else "Choose a voice for Arabic recitation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ReciterGroupFilters(selected = group, onSelect = { group = it })
                    val filtered = remember(catalog, surahNumber, query, group) {
                        val available = surahNumber?.let { selectableQuranReciters(it, catalog) } ?: catalog
                        available.filter { (group == null || reciterGroup(it.displayName) == group) &&
                            (it.displayName + " " + it.riwaya).contains(query.trim(), true) }
                    }
                    val showDefault = (group == null || group == ReciterGroup.CONTEMPORARY) &&
                        "Mishary Rashid Al-Alafasy default".contains(query.trim(), true)
                    if (!loading && filtered.isEmpty() && !showDefault) Text("No reciters found. Try another name or riwaya.", style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showDefault) item(key = "default-cdn") {
                            com.starception.submission.feature.quran.ReciterChoiceRow(
                                name = "Mishary Rashid Al-Alafasy",
                                description = "Default recitation",
                                selected = selected == null,
                                onSelect = {
                                    preferences.selectSource(null); selected = null
                                    com.starception.submission.shared.media.QuranPlaybackBus.stop()
                                    onSelected()
                                },
                            )
                        }
                        items(filtered, key = { it.id }) { source ->
                            val storage by produceState(0 to 0L, source.id, download.revision, revision, manageDownloads) {
                                if (manageDownloads) value = withContext(Dispatchers.Default) {
                                    library.count(source) to library.bytes(source)
                                }
                            }
                            val count = if (download.active == source.id) download.completed else storage.first
                            val bytes = storage.second
                            val open = openedReciter == source.id || download.active == source.id
                            Column {
                                com.starception.submission.feature.quran.ReciterChoiceRow(
                                    name = source.displayName,
                                    description = if (manageDownloads) "$count of ${source.availableSurahs.size} surahs offline"
                                        else source.riwaya.ifBlank { "Arabic recitation" },
                                    selected = selected == source.id,
                                    onSelect = {
                                        preferences.selectSource(source); selected = source.id
                                        com.starception.submission.shared.media.QuranPlaybackBus.stop()
                                        onSelected()
                                    },
                                )
                                if (manageDownloads) TextButton(onClick = {
                                    openedReciter = if (openedReciter == source.id) null else source.id
                                }) {
                                    Text(if (open) "Hide downloads" else "Offline audio" +
                                        (if (bytes > 0) " · ${bytes / (1024 * 1024)} MB" else ""))
                                }
                                if (manageDownloads && open) {
                                    if (download.active == source.id) {
                                        LinearProgressIndicator(progress = { if (download.total > 0) download.completed.toFloat() / download.total else 0f },
                                            modifier = Modifier.fillMaxWidth())
                                        Text("${download.completed} of ${download.total} surahs", style = MaterialTheme.typography.bodySmall)
                                    } else Text("$count of ${source.availableSurahs.size} surahs saved", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(onClick = {
                                            if (download.active == source.id) ReciterDownloadState.cancel()
                                            else ReciterDownloadState.start(source, library)
                                        }, enabled = download.active == source.id || (download.active == null && deleting == null && count < source.availableSurahs.size)) {
                                            Text(if (download.active == source.id) "Cancel" else "Download all")
                                        }
                                        if (count > 0) TextButton(onClick = {
                                            actionScope.launch {
                                                deleting = source.id
                                                actionError = null
                                                try {
                                                    withContext(Dispatchers.Default) { library.delete(source) }
                                                    revision++
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (failure: Exception) {
                                                    actionError = failure.message ?: "Unable to remove saved audio."
                                                } finally { deleting = null }
                                            }
                                        }, enabled = download.active == null && deleting == null) {
                                            Text(if (deleting == source.id) "Removing…" else "Remove", color = MaterialTheme.colorScheme.error)
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
}
