package com.starception.submission.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.download.AssetDownloadViewModel
import com.starception.submission.feature.quran.ReciterCatalog
import com.starception.submission.feature.quran.DEFAULT_RECITER_NAME
import com.starception.submission.feature.quran.ReciterChoiceRow
import com.starception.submission.feature.quran.ReciterPreviewButton
import com.starception.submission.feature.quran.ReciterDownloadManager
import com.starception.submission.feature.quran.ReciterGroup
import com.starception.submission.feature.quran.ReciterGroupFilters
import com.starception.submission.feature.quran.ReciterSelectionStore
import com.starception.submission.feature.quran.reciterGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings opens a dedicated library; the reader can embed the browser in its own sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterStorageSection(
    initiallyExpanded: Boolean = false,
    manageDownloads: Boolean = true,
    surahNumber: Int? = null,
    onReciterSelected: () -> Unit = {},
    sectionTitle: String = "Quran recitations",
    onPreviewStart: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (initiallyExpanded) {
        ReciterBrowser(manageDownloads, surahNumber, onReciterSelected, onPreviewStart, modifier)
        return
    }

    val context = LocalContext.current
    val store = remember(context) { ReciterSelectionStore(context) }
    val selection by store.selections.collectAsStateWithLifecycle(initialValue = store.current())
    val translationAudioSelected = store.hasTranslationAudioSelection()
    val manager = remember(context) { ReciterDownloadManager.get(context.applicationContext) }
    val revision by manager.changes.collectAsStateWithLifecycle()
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val offlineReciters by produceState(emptyList<ReciterCatalog.Reciter>(), context, revision, showLibrary) {
        value = ReciterCatalog.offline(context)
    }
    val completedIds by produceState(emptySet<String>(), offlineReciters, revision) {
        value = withContext(Dispatchers.IO) {
            val savedIds = manager.localReciterIds()
            offlineReciters.filter { reciter ->
                if (reciter.id !in savedIds) return@filter false
                val status = manager.snapshot(reciter)
                status.totalSurahs > 0 && status.downloadedSurahs == status.totalSurahs
            }.map(ReciterCatalog.Reciter::id).toSet()
        }
    }
    val completedNames = offlineReciters.filter { it.id in completedIds }.map { it.name }.distinct()
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.clickable(role = Role.Button) { showLibrary = true }.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sectionTitle, style = MaterialTheme.typography.titleSmall)
                Text(if (translationAudioSelected) DEFAULT_RECITER_NAME else selection.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (completedNames.isNotEmpty()) {
                    Text("Available offline: ${completedNames.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
    if (showLibrary) {
        ModalBottomSheet(onDismissRequest = { showLibrary = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (manageDownloads) "Reciters & offline audio" else "Choose reciter",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showLibrary = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close reciters")
                    }
                }
                ReciterBrowser(manageDownloads, surahNumber, onReciterSelected = {
                    if (!manageDownloads) showLibrary = false
                    onReciterSelected()
                }, onPreviewStart = onPreviewStart, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReciterBrowser(
    manageDownloads: Boolean,
    surahNumber: Int?,
    onReciterSelected: () -> Unit,
    onPreviewStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = remember(context) { ReciterDownloadManager.get(context.applicationContext) }
    val revision by manager.changes.collectAsStateWithLifecycle()
    val offlineReciters by produceState(emptyList<ReciterCatalog.Reciter>(), context, revision) {
        value = ReciterCatalog.offline(context)
    }
    val store = remember(context) { ReciterSelectionStore(context) }
    val selection by store.selections.collectAsStateWithLifecycle(initialValue = store.current())
    val translationAudioSelected = store.hasTranslationAudioSelection()
    var reciters by remember { mutableStateOf<List<ReciterCatalog.Reciter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<ReciterGroup?>(null) }
    var expandedReciterId by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard = { focusManager.clearFocus(); keyboard?.hide(); Unit }
    val preview = remember(context) { ReciterPreviewPlayer(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(preview, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) preview.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            preview.stop()
        }
    }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            if (reciters.isEmpty()) reciters = (ReciterCatalog.offline(context) +
                ReciterCatalog.cached(context).orEmpty()).distinctBy(ReciterCatalog.Reciter::id)
            val latest = ReciterCatalog.fetch(context)
            reciters = (latest + ReciterCatalog.offline(context, latest)).distinctBy(ReciterCatalog.Reciter::id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Couldn’t load reciters. Check your connection and try again."
        } finally {
            loading = false
        }
    }
    val allReciters = remember(reciters, offlineReciters) {
        (reciters + offlineReciters).distinctBy(ReciterCatalog.Reciter::id)
    }
    val completedIds by produceState(emptySet<String>(), allReciters, revision) {
        value = withContext(Dispatchers.IO) {
            val savedIds = manager.localReciterIds()
            allReciters.filter { reciter ->
                if (reciter.id !in savedIds) return@filter false
                val status = manager.snapshot(reciter)
                status.totalSurahs > 0 && status.downloadedSurahs == status.totalSurahs
            }.map(ReciterCatalog.Reciter::id).toSet()
        }
    }
    val filtered = remember(allReciters, completedIds, surahNumber, query, group, selection.id) {
        allReciters.filter { (surahNumber == null || surahNumber in it.surahs) &&
            (group == null || reciterGroup(it.name) == group) && ReciterCatalog.matchesSearch(it, query) }
            .sortedWith(compareByDescending<ReciterCatalog.Reciter> { it.id in completedIds }
                .thenByDescending { it.id == selection.id }
                .thenBy { it.name })
    }
    val showDefault = (group == null || group == ReciterGroup.CONTEMPORARY) &&
        "Mishary Rashid Al-Alafasy default".contains(query.trim(), true)
    val downloadedCount = filtered.count { it.id in completedIds }

    // Only search is fixed. Filters and rows share the remaining viewport, even
    // at large font scales or when the keyboard leaves very little height.
    Column(modifier.fillMaxWidth().heightIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = query, onValueChange = { preview.stop(); query = it },
            placeholder = { Text("Search reciters") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { preview.stop(); query = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
            shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().selectableGroup().testTag("reciter-list"),
            contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "filters") {
                ReciterGroupFilters(group) { preview.stop(); group = it; dismissKeyboard() }
            }
            if (loading) item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading reciters" })
            }
            error?.let { message -> item(key = "error") {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            } }
            preview.error?.let { message -> item(key = "preview-error") {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } }
            if (!loading && filtered.isEmpty() && !showDefault) item(key = "empty") {
                Text("No matching reciters", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { query = ""; group = null }) { Text("Clear filters") }
            }
            if (showDefault) item(key = "default-cdn") {
                ReciterChoiceRow(name = "Mishary Rashid Al-Alafasy",
                    description = if (manageDownloads) "Default · Downloads in Audio" else "Default recitation",
                    selected = !translationAudioSelected && selection.id == null,
                    onPreview = {
                        dismissKeyboard()
                        preview.toggle("default-cdn",
                            "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/${surahNumber ?: 1}.mp3", onPreviewStart)
                    },
                    previewing = preview.activeId == "default-cdn",
                    previewLoading = preview.activeId == "default-cdn" && preview.loading,
                    onSelect = { preview.stop(); dismissKeyboard(); store.select(null); onReciterSelected() })
            }
            if (downloadedCount > 0) item(key = "offline-heading") {
                Text("Downloaded reciters", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            itemsIndexed(filtered, key = { _, reciter -> reciter.id }) { index, reciter ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (index == downloadedCount && downloadedCount > 0) {
                    Text("More reciters", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                val onPreview = {
                    dismissKeyboard()
                    val sample = surahNumber ?: reciter.surahs.minOrNull() ?: 1
                    val source = manager.cachedFile(reciter, sample)?.absolutePath
                        ?: reciter.server.trimEnd('/') + "/" +
                            "%03d.mp3".format(java.util.Locale.US, sample)
                    preview.toggle(reciter.id, source, onPreviewStart)
                }
                val select = {
                    preview.stop()
                    dismissKeyboard()
                    store.select(reciter)
                    onReciterSelected()
                }
                if (manageDownloads) {
                    val status by produceState(ReciterDownloadManager.DownloadStatus(totalSurahs = reciter.surahs.size), reciter.id) {
                        withContext(Dispatchers.IO) { manager.observe(reciter).collect { value = it } }
                    }
                    val expanded = expandedReciterId == reciter.id
                    ReciterLibraryRow(reciter, status, !translationAudioSelected && selection.id == reciter.id, expanded,
                        onSelect = select,
                        onToggleDownloads = {
                            dismissKeyboard()
                            if (expanded) {
                                expandedReciterId = null
                            } else {
                                expandedReciterId = reciter.id
                                if (shouldStartReciterDownload(status, expanded)) manager.download(reciter)
                            }
                        },
                        onDownload = { manager.download(reciter) },
                        onCancel = { manager.cancel(reciter.id) },
                        onRemove = {
                            if (preview.activeId == reciter.id) preview.stop()
                            manager.delete(reciter)
                        },
                        onPreview = onPreview,
                        previewing = preview.activeId == reciter.id,
                        previewLoading = preview.activeId == reciter.id && preview.loading)
                } else {
                    ReciterChoiceRow(name = reciter.name,
                        description = if (reciter.id in completedIds) "Available offline · ${reciterDescription(reciter)}"
                            else reciterDescription(reciter),
                         selected = !translationAudioSelected && selection.id == reciter.id, onSelect = select,
                        onPreview = onPreview, previewing = preview.activeId == reciter.id,
                        previewLoading = preview.activeId == reciter.id && preview.loading)
                }
                }
            }
        }
    }
}

private fun reciterDescription(reciter: ReciterCatalog.Reciter) =
    reciter.riwaya.removePrefix("Rewayat ").removeSuffix(" - Murattal").ifBlank { "Arabic recitation" }

internal fun shouldStartReciterDownload(
    status: ReciterDownloadManager.DownloadStatus,
    expanded: Boolean,
): Boolean = !expanded && !status.downloading && status.totalSurahs > 0 &&
    status.downloadedSurahs < status.totalSurahs

@Composable
internal fun ReciterLibraryRow(
    reciter: ReciterCatalog.Reciter,
    status: ReciterDownloadManager.DownloadStatus,
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
                        Text(reciter.name, style = MaterialTheme.typography.titleSmall,
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
                ReciterPreviewButton(reciter.name, onPreview, previewing, previewLoading)
                IconButton(onClick = onToggleDownloads, modifier = Modifier.size(48.dp)) {
                    Icon(when {
                        expanded -> Icons.Filled.ExpandLess
                        status.downloading || (status.totalSurahs > 0 &&
                            status.downloadedSurahs >= status.totalSurahs) -> Icons.Filled.ExpandMore
                        else -> Icons.Filled.Download
                    }, contentDescription = when {
                        expanded -> "Hide downloads for ${reciter.name}"
                        status.downloading -> "Show download progress for ${reciter.name}"
                        status.totalSurahs > 0 && status.downloadedSurahs >= status.totalSurahs ->
                            "Show offline details for ${reciter.name}"
                        else -> "Download remaining audio for ${reciter.name}"
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
                ReciterOfflineActions(status, onDownload, onCancel, onRemove)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReciterOfflineActions(
    status: ReciterDownloadManager.DownloadStatus,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
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
        if (status.downloadedBytes > 0) Text("${AssetDownloadViewModel.formatSize(status.downloadedBytes)} on this device",
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
                FilledTonalButton(onClick = onDownload, enabled = status.totalSurahs > 0,
                    shape = RoundedCornerShape(12.dp)) {
                    Text(when {
                        status.error != null -> "Retry download"
                        status.downloadedSurahs > 0 -> "Download remaining"
                        else -> "Download all"
                    })
                }
            }
            if (status.downloadedSurahs > 0) TextButton(onClick = onRemove,
                enabled = !status.downloading,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Remove audio")
            }
        }
    }
}
