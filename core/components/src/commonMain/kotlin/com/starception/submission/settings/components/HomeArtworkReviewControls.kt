package com.starception.submission.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.images.ArtworkReviewState
import com.starception.submission.core.images.ArtworkTheme
import com.starception.submission.core.images.LocalArtworkReviewRepository
import com.starception.submission.core.images.rememberArtworkReviewRepository

/** Restriction applies to a separate review gallery, never to Home's approval requirement. */
@Composable
fun HomeArtworkReviewControls(theme: ArtworkTheme) {
    var restricted by rememberSaveable { mutableStateOf(true) }
    var confirmUnrestricted by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    val repository = LocalArtworkReviewRepository.current ?: rememberArtworkReviewRepository()
    val state by repository.state.collectAsStateWithLifecycle()
    DisposableEffect(repository) { onDispose { repository.clear() } }
    LaunchedEffect(showGallery, restricted, theme) {
        if (showGallery && !restricted) repository.fetch(theme, allowUnreviewed = true)
        else repository.clear()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Restricted mode", style = MaterialTheme.typography.bodyMedium)
                Text(if (restricted) "Approved artwork only" else "Unapproved previews · Manual review only",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = restricted, onCheckedChange = { value ->
                if (!value) confirmUnrestricted = true
                else { restricted = true; showGallery = false; repository.clear() }
            }, modifier = Modifier.testTag("homeArtworkRestrictedMode"))
        }
        if (!restricted) {
            TextButton(onClick = { showGallery = true }, modifier = Modifier.testTag("homeArtworkReviewOpen")) {
                Text("Review unapproved images")
            }
        }
    }
    if (confirmUnrestricted) AlertDialog(
        onDismissRequest = { confirmUnrestricted = false },
        title = { Text("View unapproved images?") },
        text = { Text("This fetches internet images that have not passed content or rights review. They may contain people or animals. Previews never replace your Home artwork.") },
        confirmButton = { TextButton(onClick = {
            confirmUnrestricted = false
            restricted = false
            showGallery = true
        }, modifier = Modifier.testTag("homeArtworkReviewConsent")) { Text("Fetch previews") } },
        dismissButton = { TextButton(onClick = { confirmUnrestricted = false }) { Text("Cancel") } },
    )
    if (showGallery && !restricted) ArtworkReviewGallery(theme, state,
        onFetch = { if (!restricted) repository.fetch(theme, allowUnreviewed = true) },
        onDismissCandidate = repository::dismiss,
        onClose = { showGallery = false; repository.clear() },
        onRestrict = { restricted = true; showGallery = false; repository.clear() })
}

@Composable
private fun ArtworkReviewGallery(
    theme: ArtworkTheme,
    state: ArtworkReviewState,
    onFetch: () -> Unit,
    onDismissCandidate: (String) -> Unit,
    onClose: () -> Unit,
    onRestrict: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    fun open(url: String) {
        runCatching { uri.openUri(url) }.onFailure { linkError = "No app could open this link." }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("homeArtworkReviewGallery"),
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Unapproved artwork", style = MaterialTheme.typography.titleLarge)
                        Text("${theme.label} · Manual review", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text("May contain people or animals. Check the full image, source and reuse rights yourself. Nothing here is approved for Home.",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                linkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(state.candidates, key = { it.artwork.id }) { candidate ->
                        val artwork = candidate.artwork
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("UNREVIEWED · ${artwork.id}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                                artwork.bitmap?.let { bitmap ->
                                    Image(bitmap, artwork.title, Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit)
                                }
                                Text(artwork.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text(artwork.artist, style = MaterialTheme.typography.bodySmall)
                                Text("Reported license: ${artwork.licenseName} · Not verified", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { open(artwork.imageUrl) }) { Text("Full image") }
                                    TextButton(onClick = { open(artwork.sourceUrl) }) { Text("Source") }
                                    TextButton(onClick = { open(artwork.licenseUrl) }) { Text("Rights") }
                                }
                                TextButton(onClick = { onDismissCandidate(artwork.id) }) { Text("Dismiss this preview") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onRestrict, modifier = Modifier.testTag("homeArtworkReviewRestrict")) { Text("Restrict") }
                    FilledTonalButton(onClick = onFetch, enabled = !state.loading, modifier = Modifier.testTag("homeArtworkReviewFetchMore")) {
                        Text(if (state.loading) "Fetching…" else "Fetch more")
                    }
                }
                Text("Home remains approved-only. Preview images are not added to your offline Home collection.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
