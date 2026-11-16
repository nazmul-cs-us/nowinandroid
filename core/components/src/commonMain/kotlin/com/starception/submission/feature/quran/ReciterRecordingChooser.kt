package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Switching a library recording changes the preview/download target, never its stored identity. */
@Composable
fun <T> ReciterRecordingChooser(
    person: ReciterPerson<T>,
    id: (T) -> String,
    style: (T) -> String,
    subtitle: (T) -> String,
    preferredId: String?,
    savedIds: Set<String>,
    onChange: (T) -> Unit,
    content: @Composable (T) -> Unit,
) {
    var browsingId by rememberSaveable(person.key) { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var previousPreferredId by rememberSaveable(person.key) { mutableStateOf(preferredId) }
    LaunchedEffect(preferredId) {
        if (preferredId != previousPreferredId) {
            if (person.recordings.any { id(it) == preferredId }) browsingId = preferredId
            previousPreferredId = preferredId
        }
    }
    val recording = person.recordings.firstOrNull { id(it) == browsingId }
        ?: person.recordings.firstOrNull { id(it) == preferredId }
        ?: person.recordings.firstOrNull { id(it) in savedIds }
        ?: person.recordings.first()
    Column(Modifier.fillMaxWidth().testTag("reciterPerson_${person.key}")) {
        key(id(recording)) { content(recording) }
        if (person.recordings.size > 1) {
            Box(Modifier.fillMaxWidth().padding(start = 72.dp, end = 12.dp, bottom = 8.dp)) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reciterRecordings_${person.key}")
                    .semantics { contentDescription = "Choose recording for ${person.name}" }) {
                    Text("${person.recordings.size} recordings" + person.recordings.count { id(it) in savedIds }.let { if (it > 0) " · $it saved" else "" }, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Filled.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 240.dp, max = 480.dp).heightIn(max = 420.dp).testTag("reciterRecordingMenu_${person.key}")) {
                    val labels = person.recordings.map { reciterRecordingLabel(style(it)) }
                    person.recordings.forEachIndexed { index, option ->
                        DropdownMenuItem(onClick = {
                            browsingId = id(option); expanded = false; onChange(option)
                        }, leadingIcon = { RadioButton(id(option) == id(recording), onClick = null) }, text = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(labels[index], style = MaterialTheme.typography.bodyMedium)
                                val duplicateLabel = labels.count { it == labels[index] } > 1
                                Text((if (duplicateLabel) "Recording ${index + 1} · " else "") + subtitle(option),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }, modifier = Modifier.testTag("reciterRecording_${id(option)}"))
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 72.dp, end = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
    }
}

/** Reveal newly opened download controls instead of leaving them below the list viewport. */
@Composable
fun ReciterDownloadPanel(content: @Composable () -> Unit) {
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        requester.bringIntoView()
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) { content() }
}
