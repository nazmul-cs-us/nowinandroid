package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import com.starception.submission.core.designsystem.component.FluidReveal
import com.starception.submission.core.designsystem.icon.TafsirIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

@Composable
fun InlineTafsirHeader(book: String, onSelect: (String) -> Unit, onHide: () -> Unit, playback: (@Composable () -> Unit)? = null) {
    val edition = PublishedTafsirs.forBook(book)
    val uri = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("inline-tafsir-header")
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TafsirIcon(modifier = Modifier.size(20.dp).testTag("inline-tafsir-icon"))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { TafsirBookPicker(book, onSelect, compact = true) }
                if (edition != null) IconButton(onClick = { uri.openUri(edition.sourceUrl) },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "Open tafsir source: ${edition.attribution}"
                    }) {
                    Icon(Icons.Default.OpenInNew, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onHide, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Hide tafsir", modifier = Modifier.size(20.dp))
                }
            }
            playback?.invoke()
        }
    }
}

/** A lazy-list child: full commentary participates in the surah's single scroll container. */
@Composable
fun InlineTafsirCommentary(surah: Int, ayah: Int, book: String, revision: Int = 0,
    loadText: suspend () -> String, speechPlayerFactory: () -> TafsirSpeechPlayer,
    missingAsset: @Composable (() -> Unit) -> Unit, fontScale: Float = 1f, embedded: Boolean = false, narrating: Boolean = false) {
    var panelExpanded by rememberSaveable(surah, ayah, book) { mutableStateOf(false) }
    LaunchedEffect(narrating, surah, ayah, book) { if (narrating) panelExpanded = true }
    key(surah, ayah, book, revision) {
        var body by remember { mutableStateOf<String?>(null) }
        var failed by remember { mutableStateOf(false) }
        var missing by remember { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        var expanded by rememberSaveable { mutableStateOf(false) }
        var overflows by remember { mutableStateOf(false) }
        val read by rememberUpdatedState(loadText)
        LaunchedEffect(retry) {
            body = null; failed = false; missing = false
            try { body = read().takeIf(String::isNotBlank) ?: throw PublishedTafsirAssetMissing() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: PublishedTafsirAssetMissing) { missing = true }
            catch (_: Exception) { failed = true }
        }
        val language = PublishedTafsirs.forBook(book)?.languageCode ?: "ar"
        val playback: @Composable () -> Unit = {
            if (body != null) Box(Modifier.widthIn(max = 144.dp)) {
                TafsirPlaybackControls(body.orEmpty(), language, compact = true, speechPlayerFactory)
            }
        }
        val commentary: @Composable (Boolean) -> Unit = { enabled ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (embedded) 8.dp else 18.dp)) {
                when {
                    missing -> missingAsset { retry++ }
                    failed -> {
                        Text("Could not load this ayah’s tafsir.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retry++ }, enabled = enabled) { Text("Try again") }
                    }
                    body == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> {
                        val passage: @Composable () -> Unit = {
                            Text(body.orEmpty(), modifier = Modifier.fillMaxWidth(),
                                maxLines = if (embedded && !expanded && !narrating) 5 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { if (embedded && !expanded && !narrating) overflows = it.hasVisualOverflow },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (18f * fontScale).sp, lineHeight = ((if (embedded) 28f else 30f) * fontScale).sp,
                                    fontFamily = if (embedded) FontFamily.Serif else MaterialTheme.typography.bodyLarge.fontFamily,
                                    textDirection = TextDirection.Content),
                                textAlign = if (language == "ar" || language == "ur") TextAlign.Right else TextAlign.Start)
                        }
                        if (enabled) SelectionContainer { passage() } else passage()
                    }
                }
                if (embedded && !narrating && body != null && (expanded || overflows)) {
                    TextButton(onClick = { expanded = !expanded }, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(if (expanded) "Show less" else "Read full tafsir")
                    }
                }
            }
        }
        val colors = MaterialTheme.colorScheme
        if (embedded) {
            val chevron by animateFloatAsState(if (panelExpanded) 180f else 0f, label = "tafsirDisclosureChevron")
            FluidReveal(
                expanded = panelExpanded,
                onExpandedChange = { panelExpanded = it },
                containerColor = colors.surfaceContainerLow,
                outlineColor = colors.outlineVariant.copy(alpha = .5f),
                contentColor = colors.onSurface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("tafsir-disclosure-$surah-$ayah"),
                headerVisibleWhenCollapsed = true,
                cornerRadius = 16.dp,
                separation = 8.dp,
                bringContentIntoView = false,
                header = {
                    Row(Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = if (panelExpanded) "Collapse tafsir" else "Expand tafsir") {
                            panelExpanded = !panelExpanded
                        }
                        .semantics { stateDescription = if (panelExpanded) "Tafsir expanded" else "Tafsir collapsed" }
                        .testTag("tafsir-toggle-$surah-$ayah")
                        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TafsirIcon(modifier = Modifier.size(20.dp).testTag("tafsir-disclosure-icon-$surah-$ayah"))
                        Text("Tafsir · $surah:$ayah", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall, color = colors.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        playback()
                        Icon(Icons.Default.ExpandMore, contentDescription = null,
                            tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(chevron))
                    }
                },
            ) { enabled, _ -> commentary(enabled) }
        } else {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .5f)),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLowest)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = colors.primaryContainer) {
                            Text(ayah.toString(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                color = colors.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Tafsir · Ayah $ayah", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold, color = colors.primary)
                            Text(PublishedTafsirs.forBook(book)?.languageName ?: "العربية", style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant)
                        }
                        playback()
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .4f))
                    commentary(true)
                }
            }
        }
    }
}

/** Each visible ayah owns its controls, while only one ayah may narrate at a time. */
class TafsirSpeechSession {
    private var active: TafsirSpeechPlayer? = null
    fun stop() { active?.stop(); active = null }
    fun activate(player: TafsirSpeechPlayer) { if (active !== player) active?.stop(); active = player }
    fun createPlayer(factory: () -> TafsirSpeechPlayer): TafsirSpeechPlayer {
        val delegate = factory()
        return object : TafsirSpeechPlayer by delegate {
            override fun play(text: String, language: String) { activate(); delegate.play(text, language) }
            override fun resume() { activate(); delegate.resume() }
            private fun activate() { this@TafsirSpeechSession.activate(this) }
            override fun release() { if (active === this) active = null; delegate.release() }
        }
    }
}
