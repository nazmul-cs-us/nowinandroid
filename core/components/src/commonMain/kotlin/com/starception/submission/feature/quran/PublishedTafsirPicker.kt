package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One language menu keeps all published sources discoverable without an overflowing chip row. */
@Composable
fun PublishedTafsirPicker(selectedBook: String?, onSelect: (PublishedTafsirEdition) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = selectedBook?.let(PublishedTafsirs::forBook)
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("published-tafsir-picker")) {
            Text(selected?.let { "${it.languageName} · ${it.title}" } ?: "Published tafsir")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 440.dp)) {
            PublishedTafsirs.editions.forEach { edition ->
                DropdownMenuItem(text = {
                    Column {
                        Text(edition.languageName, style = MaterialTheme.typography.titleSmall)
                        Text(edition.title, style = MaterialTheme.typography.bodySmall)
                    }
                }, leadingIcon = { RadioButton(selected?.bookKey == edition.bookKey, onClick = null) },
                    onClick = { expanded = false; onSelect(edition) })
            }
            HorizontalDivider()
            DropdownMenuItem(enabled = false, text = {
                Column {
                    Text("Svenska")
                    Text("Published tafsir source unavailable", style = MaterialTheme.typography.bodySmall)
                }
            }, onClick = {})
        }
    }
}
