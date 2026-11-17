package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

@Composable
fun InlineTafsirHeader(book: String, onSelect: (String) -> Unit, onHide: () -> Unit) {
    val edition = PublishedTafsirs.forBook(book)
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tafsir", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onHide) { Text("Hide tafsir") }
        }
        TafsirBookPicker(book, onSelect)
        if (edition != null) Row(Modifier.fillMaxWidth()) {
            Text(edition.attribution, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { uri.openUri(edition.sourceUrl) }) { Text("Source") }
        }
    }
}

/** A lazy-list child: full commentary participates in the surah's single scroll container. */
@Composable
fun InlineTafsirCommentary(surah: Int, ayah: Int, book: String, revision: Int = 0,
    loadText: suspend () -> String, speechPlayerFactory: () -> TafsirSpeechPlayer,
    missingAsset: @Composable (() -> Unit) -> Unit) {
    key(surah, ayah, book, revision) {
        var body by remember { mutableStateOf<String?>(null) }
        var failed by remember { mutableStateOf(false) }
        var missing by remember { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        val read by rememberUpdatedState(loadText)
        LaunchedEffect(retry) {
            body = null; failed = false; missing = false
            try { body = read().takeIf(String::isNotBlank) ?: throw PublishedTafsirAssetMissing() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: PublishedTafsirAssetMissing) { missing = true }
            catch (_: Exception) { failed = true }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tafsir · Ayah $ayah", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            when {
                missing -> missingAsset { retry++ }
                failed -> {
                    Text("Could not load this ayah’s tafsir.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text("Try again") }
                }
                body == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> {
                    val language = PublishedTafsirs.forBook(book)?.languageCode ?: "ar"
                    TafsirPlaybackControls(body.orEmpty(), language, speechPlayerFactory)
                    SelectionContainer {
                        Text(body.orEmpty(), modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp,
                                textDirection = TextDirection.Content),
                            textAlign = if (language == "ar" || language == "ur") TextAlign.Right else TextAlign.Start)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

/** Each visible ayah owns its controls, while only one ayah may narrate at a time. */
class TafsirSpeechSession {
    private var active: TafsirSpeechPlayer? = null
    fun stop() { active?.stop(); active = null }
    fun createPlayer(factory: () -> TafsirSpeechPlayer): TafsirSpeechPlayer {
        val delegate = factory()
        return object : TafsirSpeechPlayer by delegate {
            override fun play(text: String, language: String) { activate(); delegate.play(text, language) }
            override fun resume() { activate(); delegate.resume() }
            private fun activate() { if (active !== this) active?.stop(); active = this }
            override fun release() { if (active === this) active = null; delegate.release() }
        }
    }
}
