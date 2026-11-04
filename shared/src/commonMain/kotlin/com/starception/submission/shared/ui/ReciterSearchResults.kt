package com.starception.submission.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.audio.*
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

/** Cache-first voices in the same global search surface as Quran and Hadith. */
@Composable
internal fun ReciterSearchResults(query: String) {
    val library = QuranReciterLibrary.platform ?: return
    var catalog by remember { mutableStateOf<List<QuranReciterSource>>(emptyList()) }
    var opened by remember { mutableStateOf<QuranReciterSource?>(null) }
    val focus = LocalFocusManager.current
    LaunchedEffect(library) {
        withContext(Dispatchers.Default) {
            val cached = runCatching {
                Json.decodeFromString<List<QuranReciterSource>>(platformKeyValueStore().getString("quran_reciter_catalog").orEmpty())
            }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { catalog = cached }
            try {
                val refreshed = library.catalog()
                withContext(Dispatchers.Main) { if (refreshed.isNotEmpty()) catalog = refreshed }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Cached voices remain searchable while offline. */ }
        }
    }
    val matches = remember(query, catalog) {
        if (query.trim().length < 2) emptyList() else catalog.filter { matchesQuranReciterSearch(it, query) }.take(8)
    }
    if (matches.isNotEmpty()) {
        Text("RECITERS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp))
        matches.forEach { source ->
            ListItem(headlineContent = { Text(source.displayName) },
                supportingContent = { Text("${source.riwaya} · ${source.availableSurahs.size} surahs · Download audio") },
                modifier = Modifier.clickable { focus.clearFocus(); opened = source })
        }
    }
    opened?.let { source -> ReciterDownloadDetails(source, onDismiss = { opened = null }) }
}
