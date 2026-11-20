package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
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

/** A single selector for Arabic books and published language editions. */
@Composable
fun TafsirBookPicker(selectedBook: String, onSelect: (String) -> Unit, includeWordMeanings: Boolean = false,
    compact: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val arabic = listOf("saadi" to "As-Sa'di", "moysar" to "Al-Moyassar", "baghawi" to "Al-Baghawi") +
        if (includeWordMeanings) listOf("word_meanings" to "Word meanings") else emptyList()
    val edition = PublishedTafsirs.forBook(selectedBook)
    Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("published-tafsir-picker"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(vertical = 4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!compact) Text(edition?.languageName ?: "العربية · Arabic", style = MaterialTheme.typography.labelMedium)
                Text(edition?.title ?: arabic.firstOrNull { it.first == selectedBook }?.second ?: "Choose commentary",
                    style = if (compact) MaterialTheme.typography.titleSmall else
                        MaterialTheme.typography.titleLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif),
                    maxLines = if (compact) 1 else 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (compact) Text(edition?.let { "${it.languageName} · ${it.attribution}" } ?: "العربية · Arabic",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null,
                modifier = Modifier.padding(start = if (compact) 4.dp else 12.dp).size(if (compact) 16.dp else 20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 440.dp)) {
            arabic.forEach { (key, title) ->
                DropdownMenuItem(text = { Column {
                    Text(title); Text("العربية · Arabic", style = MaterialTheme.typography.bodySmall)
                } }, leadingIcon = { RadioButton(selectedBook == key, onClick = null) },
                    onClick = { expanded = false; onSelect(key) })
            }
            HorizontalDivider()
            PublishedTafsirs.editions.forEach { item ->
                DropdownMenuItem(text = { Column {
                    Text(item.languageName, style = MaterialTheme.typography.titleSmall)
                    Text(item.title, style = MaterialTheme.typography.bodySmall)
                } }, leadingIcon = { RadioButton(selectedBook == item.bookKey, onClick = null) },
                    onClick = { expanded = false; onSelect(item.bookKey) })
            }
            DropdownMenuItem(enabled = false, text = { Text("Svenska · Published source unavailable") }, onClick = {})
        }
    }
}
