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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.starception.submission.feature.quran.ReciterChoiceRow
import com.starception.submission.feature.quran.ReciterPreviewButton

private data class ReciterDownloads(
    val active: String? = null,
    val sourceId: String? = null,
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
        state.value = ReciterDownloads(active = source.id, sourceId = source.id, total = source.availableSurahs.size)
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
internal fun ReciterSettingsSection(
    manageDownloads: Boolean = false,
    initiallyExpanded: Boolean = false,
    initiallyShowLibrary: Boolean = false,
    surahNumber: Int? = null,
    onSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!initiallyExpanded) {
        ReciterStorageEntry(manageDownloads, surahNumber, onSelected, initiallyShowLibrary)
        return
    }
    val library = QuranReciterLibrary.platform ?: return
    val preview = rememberReciterPreview(library)
    val preferences = remember { QuranReciterPreferences() }
    val selectionRevision by QuranReciterPreferences.changes.collectAsState()
    val selected = remember(selectionRevision) { preferences.selectedReciterId() }
    val translationAudioSelected = com.starception.submission.shared.content.SharedContentStore()
        .quranTranslationLanguage() in setOf("bn", "en", "ur", "hi")
    val store = remember { platformKeyValueStore() }
    var catalog by remember { mutableStateOf<List<QuranReciterSource>>(emptyList()) }
    var openedReciter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<ReciterGroup?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val download by ReciterDownloadState.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    val actionScope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var offlineCatalog by remember { mutableStateOf<List<QuranReciterSource>>(emptyList()) }
    var storage by remember { mutableStateOf<Map<String, Pair<Int, Long>>>(emptyMap()) }
    LaunchedEffect(catalog, download.revision, revision) {
        val offline = withContext(Dispatchers.Default) { library.offline(catalog) }
        val snapshots = withContext(Dispatchers.Default) {
            offline.associate { it.id to (library.count(it) to library.bytes(it)) }
        }
        offlineCatalog = offline
        storage = snapshots
    }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            if (catalog.isEmpty()) catalog = withContext(Dispatchers.Default) {
                runCatching {
                    Json.decodeFromString<List<QuranReciterSource>>(store.getString("quran_reciter_catalog").orEmpty())
                }.getOrDefault(emptyList())
            }
            val result = withContext(Dispatchers.Default) { library.catalog() }
            check(result.isNotEmpty()) { "Couldn’t load reciters. Check your connection and try again." }
            catalog = result
            withContext(Dispatchers.Default) { store.putString("quran_reciter_catalog", Json.encodeToString(result)) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn’t load reciters. Check your connection and try again." }
        finally { loading = false }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard = { focusManager.clearFocus(); keyboard?.hide(); Unit }
    val all = (catalog + offlineCatalog).distinctBy { it.id }
    val completedIds = all.filter { it.availableSurahs.isNotEmpty() &&
        (storage[it.id]?.first ?: 0) >= it.availableSurahs.size }.mapTo(mutableSetOf()) { it.id }
    val filtered = remember(all, completedIds, surahNumber, query, group, selected) {
        all.filter { (surahNumber == null || surahNumber in it.availableSurahs) &&
            (group == null || reciterGroup(it.displayName) == group) && matchesQuranReciterSearch(it, query) }
            .sortedWith(compareByDescending<QuranReciterSource> { it.id in completedIds }
                .thenByDescending { it.id == selected }.thenBy { it.displayName })
    }
    val showDefault = (group == null || group == ReciterGroup.CONTEMPORARY) &&
        "Mishary Rashid Al-Alafasy default".contains(query.trim(), true)
    val downloadedCount = filtered.count { it.id in completedIds }
    Column(modifier.fillMaxWidth().heightIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = query, onValueChange = { preview.stop(); query = it },
            placeholder = { Text("Search reciters") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { preview.stop(); query = "" }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search")
            } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
            shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().testTag("reciter-search"), singleLine = true,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().selectableGroup().testTag("reciter-list"),
            contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "filters") { ReciterGroupFilters(group) { preview.stop(); group = it; dismissKeyboard() } }
            if (loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item(key = "error") {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            } }
            (preview.error ?: actionError)?.let { message -> item(key = "action-error") {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } }
            if (!loading && filtered.isEmpty() && !showDefault) item(key = "empty") {
                Text("No matching reciters", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { query = ""; group = null }) { Text("Clear filters") }
            }
            if (showDefault) item(key = "default-cdn") {
                ReciterChoiceRow(name = "Mishary Rashid Al-Alafasy",
                    description = if (manageDownloads) "Default · Downloads in Audio" else "Default recitation",
                    selected = !translationAudioSelected && selected == null,
                    onPreview = { dismissKeyboard(); preview.toggle(null, surahNumber) },
                    previewing = preview.activeId == "default-cdn", previewLoading = preview.activeId == "default-cdn" && preview.loading,
                    onSelect = { preview.stop(); dismissKeyboard(); preferences.selectSource(null)
                        com.starception.submission.shared.media.QuranPlaybackBus.stop(); onSelected() })
            }
            if (downloadedCount > 0) item(key = "offline-heading") {
                Text("Downloaded reciters", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            items(filtered, key = { it.id }) { source ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (downloadedCount > 0 && source == filtered.getOrNull(downloadedCount)) {
                        Text("More reciters", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    val saved = storage[source.id] ?: (0 to 0L)
                    val active = download.active == source.id
                    val status = ReciterOfflineStatus(if (active) download.completed else saved.first,
                        source.availableSurahs.size, saved.second, active,
                        if (download.sourceId == source.id) download.error else null)
                    val select = {
                        preview.stop(); dismissKeyboard(); preferences.selectSource(source)
                        com.starception.submission.shared.media.QuranPlaybackBus.stop(); onSelected()
                    }
                    val onPreview = { dismissKeyboard(); preview.toggle(source, surahNumber) }
                    if (manageDownloads) ReciterLibraryRow(source, status,
                        selected = !translationAudioSelected && selected == source.id,
                        expanded = openedReciter == source.id,
                        onSelect = select, onToggleDownloads = {
                            dismissKeyboard()
                            val expanded = openedReciter == source.id
                            openedReciter = if (expanded) null else source.id
                            if (!expanded && !status.downloading && !status.complete && status.totalSurahs > 0 && download.active == null)
                                ReciterDownloadState.start(source, library)
                        },
                        onDownload = { ReciterDownloadState.start(source, library) },
                        onCancel = { ReciterDownloadState.cancel() }, onRemove = {
                            if (preview.activeId == source.id) preview.stop()
                            actionScope.launch {
                                deleting = source.id; actionError = null
                                try { withContext(Dispatchers.Default) { library.delete(source) }; revision++ }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { actionError = failure.message ?: "Unable to remove saved audio." }
                                finally { deleting = null }
                            }
                        }, onPreview = onPreview, previewing = preview.activeId == source.id,
                        previewLoading = preview.activeId == source.id && preview.loading,
                        actionsEnabled = deleting == null && (download.active == null || active))
                    else ReciterChoiceRow(name = source.displayName,
                        description = (if (source.id in completedIds) "Available offline · " else "") + reciterDescription(source),
                        selected = !translationAudioSelected && selected == source.id, onSelect = select,
                        onPreview = onPreview, previewing = preview.activeId == source.id,
                        previewLoading = preview.activeId == source.id && preview.loading)
                }
            }
        }
    }
}

private fun reciterDescription(source: QuranReciterSource) = source.riwaya
    .removePrefix("Rewayat ").removeSuffix(" - Murattal").ifBlank { "Arabic recitation" }

internal data class ReciterOfflineStatus(
    val downloadedSurahs: Int, val totalSurahs: Int, val downloadedBytes: Long,
    val downloading: Boolean, val error: String? = null,
) {
    val complete get() = totalSurahs > 0 && downloadedSurahs >= totalSurahs
    val progress get() = if (totalSurahs > 0) (downloadedSurahs.toFloat() / totalSurahs).coerceIn(0f, 1f) else 0f
}

@Composable
internal fun ReciterLibraryRow(
    reciter: QuranReciterSource,
    status: ReciterOfflineStatus,
    selected: Boolean,
    expanded: Boolean,
    onSelect: () -> Unit,
    onToggleDownloads: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    previewing: Boolean,
    previewLoading: Boolean,
    actionsEnabled: Boolean = true,
) {
    Surface(shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = .55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))) {
        Column {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).selectable(selected, role = Role.RadioButton, onClick = onSelect)
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(reciter.displayName, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(reciterDescription(reciter), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            status.downloading -> Text("Saving offline · ${(status.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            status.error != null -> Text("Download paused", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                            status.totalSurahs > 0 && status.downloadedSurahs == status.totalSurahs ->
                                Text("Available offline · ${status.totalSurahs} surahs",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            status.downloadedSurahs > 0 -> Text("${status.downloadedSurahs} of ${status.totalSurahs} saved offline",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            else -> Text("Available to download", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                ReciterPreviewButton(reciter.displayName, onPreview, previewing, previewLoading)
                IconButton(onClick = onToggleDownloads, modifier = Modifier.size(48.dp)) {
                    Icon(when {
                        expanded -> Icons.Filled.ExpandLess
                        status.downloading || (status.totalSurahs > 0 &&
                            status.downloadedSurahs >= status.totalSurahs) -> Icons.Filled.ExpandMore
                        else -> Icons.Filled.Download
                    }, contentDescription = when {
                        expanded -> "Hide downloads for ${reciter.displayName}"
                        status.downloading -> "Show download progress for ${reciter.displayName}"
                        status.totalSurahs > 0 && status.downloadedSurahs >= status.totalSurahs ->
                            "Show offline details for ${reciter.displayName}"
                        else -> "Download remaining audio for ${reciter.displayName}"
                    })
                }
            }
            if (status.downloading && !expanded) {
                LinearProgressIndicator(progress = { status.progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(4.dp))
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                ReciterOfflineActions(status, onDownload, onCancel, onRemove, actionsEnabled)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReciterOfflineActions(
    status: ReciterOfflineStatus,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    actionsEnabled: Boolean = true,
) {
    val complete = status.totalSurahs > 0 && status.downloadedSurahs >= status.totalSurahs
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (complete) "Available offline" else "${status.downloadedSurahs} of ${status.totalSurahs} surahs saved",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (status.downloading) {
            LinearProgressIndicator(progress = { status.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp))
        }
        if (status.downloadedBytes > 0) Text("${formatReciterBytes(status.downloadedBytes)} on this device",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        status.error?.let {
            Text("Download paused. Your saved surahs are safe.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status.downloading) {
                FilledTonalButton(onClick = onCancel, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel download")
                }
            } else if (!complete) {
                FilledTonalButton(onClick = onDownload, enabled = actionsEnabled && status.totalSurahs > 0,
                    shape = RoundedCornerShape(12.dp)) {
                    Text(when {
                        status.error != null -> "Retry download"
                        status.downloadedSurahs > 0 -> "Download remaining"
                        else -> "Download all"
                    })
                }
            }
            if (status.downloadedSurahs > 0) TextButton(onClick = onRemove,
                enabled = actionsEnabled && !status.downloading,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Remove audio")
            }
        }
    }
}

private fun formatReciterBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "${(bytes / (1024.0 * 1024 * 1024) * 10).toLong() / 10.0} GB"
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / 1024} KB"
}

/** Search opens one voice's existing download lifecycle without silently selecting it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReciterDownloadDetails(source: QuranReciterSource, onDismiss: () -> Unit) {
    val library = QuranReciterLibrary.platform ?: return
    val preview = rememberReciterPreview(library)
    val preferences = remember { QuranReciterPreferences() }
    val selectedRevision by QuranReciterPreferences.changes.collectAsState()
    val download by ReciterDownloadState.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    val storage by produceState(0 to 0L, source.id, download.revision, revision) {
        value = withContext(Dispatchers.Default) { library.count(source) to library.bytes(source) }
    }
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reciter & offline audio", style = MaterialTheme.typography.titleLarge)
            val selected = remember(selectedRevision) { preferences.selectedReciterId() }
            com.starception.submission.feature.quran.ReciterChoiceRow(name = source.displayName, description = source.riwaya,
                selected = selected == source.id,
                onPreview = { preview.toggle(source) }, previewing = preview.activeId == source.id,
                previewLoading = preview.activeId == source.id && preview.loading, onSelect = {
                    preview.stop()
                    preferences.selectSource(source)
                    com.starception.submission.shared.media.QuranPlaybackBus.stop()
                })
            Text("${storage.first} of ${source.availableSurahs.size} surahs · ${storage.second / (1024 * 1024)} MB offline",
                style = MaterialTheme.typography.bodyMedium)
            if (download.active == source.id) {
                LinearProgressIndicator(progress = { if (download.total > 0) download.completed.toFloat() / download.total else 0f },
                    modifier = Modifier.fillMaxWidth())
                Text("${download.completed} of ${download.total} surahs")
            }
            preview.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            (error ?: download.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    if (download.active == source.id) ReciterDownloadState.cancel() else ReciterDownloadState.start(source, library)
                }, enabled = download.active == source.id || (download.active == null && storage.first < source.availableSurahs.size)) {
                    Text(if (download.active == source.id) "Cancel" else "Download all")
                }
                if (storage.first > 0) TextButton(onClick = {
                    scope.launch {
                        removing = true
                        try { withContext(Dispatchers.Default) { library.delete(source) }; revision++ }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message }
                        finally { removing = false }
                    }
                }, enabled = download.active == null && !removing) { Text(if (removing) "Removing…" else "Remove") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReciterStorageEntry(manageDownloads: Boolean, surahNumber: Int?, onSelected: () -> Unit, initiallyOpen: Boolean) {
    val library = QuranReciterLibrary.platform ?: return
    var showLibrary by remember { mutableStateOf(initiallyOpen) }
    val preferences = remember { QuranReciterPreferences() }
    val selectionRevision by QuranReciterPreferences.changes.collectAsState()
    val download by ReciterDownloadState.state.collectAsState()
    val completed by produceState(emptyList<String>(), download.revision, showLibrary) {
        value = withContext(Dispatchers.Default) {
            library.offline(emptyList()).filter { library.count(it) == it.availableSurahs.size }.map { it.displayName }.distinct()
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { showLibrary = true }.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (manageDownloads) "Quran recitations" else "Reciter", style = MaterialTheme.typography.titleSmall)
                val selected = remember(selectionRevision) {
                    if (com.starception.submission.shared.content.SharedContentStore().quranTranslationLanguage() in setOf("bn", "en", "ur", "hi"))
                        "Mishary Rashid Al-Alafasy"
                    else preferences.selectedSource()?.displayName ?: "Mishary Rashid Al-Alafasy"
                }
                Text(selected, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (completed.isNotEmpty()) Text("Available offline: ${completed.joinToString()}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open reciter library")
        }
    }
    if (showLibrary) ModalBottomSheet(onDismissRequest = { showLibrary = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (manageDownloads) "Reciters & offline audio" else "Choose reciter",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showLibrary = false }) { Icon(Icons.Filled.Close, contentDescription = "Close reciters") }
            }
            ReciterSettingsSection(manageDownloads = manageDownloads, initiallyExpanded = true,
                surahNumber = surahNumber, modifier = Modifier.weight(1f), onSelected = {
                    if (!manageDownloads) showLibrary = false
                    onSelected()
                })
        }
    }
}
