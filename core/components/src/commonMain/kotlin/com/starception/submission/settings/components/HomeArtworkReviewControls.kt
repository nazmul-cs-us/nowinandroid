package com.starception.submission.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.images.ArtworkReviewState
import com.starception.submission.core.images.ArtworkTheme
import com.starception.submission.core.images.LocalArtworkReviewRepository
import com.starception.submission.core.images.rememberArtworkReviewRepository
import com.starception.submission.core.images.LocalHomeArtworkRepository
import com.starception.submission.core.images.rememberHomeArtworkRepository
import com.starception.submission.core.images.UnreviewedArtwork
import kotlinx.coroutines.launch

/** Explicitly opening the gallery starts a preview session. */
@Composable
fun HomeArtworkReviewControls(theme: ArtworkTheme) {
    var showGallery by rememberSaveable { mutableStateOf(false) }
    val repository = LocalArtworkReviewRepository.current ?: rememberArtworkReviewRepository()
    val state by repository.state.collectAsStateWithLifecycle()
    val home = LocalHomeArtworkRepository.current ?: rememberHomeArtworkRepository()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<UnreviewedArtwork?>(null) }
    var approvalMessage by remember { mutableStateOf<String?>(null) }
    var approving by remember { mutableStateOf(false) }
    DisposableEffect(repository) { onDispose { repository.clear() } }
    LaunchedEffect(showGallery, theme) {
        if (showGallery) repository.fetch(theme, allowUnreviewed = true)
        else repository.clear()
    }
    TextButton(onClick = { showGallery = true }, modifier = Modifier.testTag("homeArtworkReviewOpen")) {
        Text("Choose artwork")
    }
    if (showGallery) ArtworkReviewGallery(theme, state,
        approvalMessage = approvalMessage, approving = approving,
        onApprove = { selected = it },
        onFetch = { repository.fetch(theme, allowUnreviewed = true) },
        onDismissCandidate = repository::dismiss,
        onClose = { showGallery = false; repository.clear() })
    selected?.let { candidate ->
        var contentConfirmed by remember(candidate.artwork.id) { mutableStateOf(true) }
        var rightsConfirmed by remember(candidate.artwork.id) { mutableStateOf(true) }
        AlertDialog(onDismissRequest = { if (!approving) selected = null },
            modifier = Modifier.testTag("homeArtworkApprovalDialog"),
            title = { Text("Approve for my device") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This saves the image privately and adds it to Home. It does not publish it for anyone else.")
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(contentConfirmed, enabled = !approving, role = Role.Checkbox,
                    onValueChange = { contentConfirmed = it }).testTag("homeArtworkConfirmContent"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(contentConfirmed, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                    Text("I checked the full image: no people or animals.")
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(rightsConfirmed, enabled = !approving, role = Role.Checkbox,
                    onValueChange = { rightsConfirmed = it }).testTag("homeArtworkConfirmRights"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rightsConfirmed, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                    Text("I checked the source, credit and reuse rights.")
                }
            } },
            confirmButton = { TextButton(enabled = contentConfirmed && rightsConfirmed && !approving, onClick = {
                approving = true
                scope.launch {
                    try {
                        repository.approveForDevice(candidate, theme, home)
                        approvalMessage = "Approved on this device · Added to Home"
                        selected = null
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { approvalMessage = "Approval could not be saved. If the image changed, fetch and review it again."; selected = null }
                    finally { approving = false }
                }
            }, modifier = Modifier.testTag("homeArtworkApproveConfirm")) { Text(if (approving) "Saving…" else "Approve") } },
            dismissButton = { TextButton(enabled = !approving, onClick = { selected = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ArtworkReviewGallery(
    theme: ArtworkTheme,
    state: ArtworkReviewState,
    onFetch: () -> Unit,
    onDismissCandidate: (String) -> Unit,
    onClose: () -> Unit,
    onApprove: (UnreviewedArtwork) -> Unit,
    approvalMessage: String?,
    approving: Boolean,
) {
    val uri = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(0) }
    val candidate = state.candidates.getOrNull(index.coerceIn(0, (state.candidates.size - 1).coerceAtLeast(0)))
    fun open(url: String) {
        runCatching { uri.openUri(url) }.onFailure { linkError = "No app could open this link." }
    }
    Dialog(onDismissRequest = onClose, properties = artworkDialogProperties()) {
        Surface(Modifier.fillMaxSize().testTag("homeArtworkReviewGallery"), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth > maxHeight
                Column(Modifier.widthIn(max = 1100.dp).fillMaxSize().align(Alignment.TopCenter).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Choose artwork", style = MaterialTheme.typography.titleLarge)
                            Text("${theme.label} · Private on this device", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (candidate == null) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (state.loading) CircularProgressIndicator()
                            Text(if (state.loading) "Finding your next artwork…" else "No artwork loaded",
                                style = MaterialTheme.typography.titleMedium)
                            if (!state.loading) {
                                Text(state.error ?: "Fetch a fresh collection to start reviewing.",
                                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                                Button(onClick = onFetch, enabled = !approving, modifier = Modifier.testTag("homeArtworkReviewRetry")) { Text("Try again") }
                            }
                        }
                    } else {
                        if (wide) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                ArtworkPreview(candidate, onOpen = { open(candidate.artwork.imageUrl) },
                                    modifier = Modifier.weight(1f).fillMaxHeight())
                                ArtworkPreviewActions(candidate, index, state, approvalMessage, linkError, approving,
                                    onPrevious = { index = (index - 1).coerceAtLeast(0) },
                                    onNext = { index = (index + 1).coerceAtMost(state.candidates.lastIndex) },
                                    onSkip = { onDismissCandidate(candidate.artwork.id) },
                                    onApprove = { onApprove(candidate) }, onOpen = ::open,
                                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                            }
                        } else {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ArtworkPreview(candidate, onOpen = { open(candidate.artwork.imageUrl) },
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1.15f))
                                ArtworkPreviewActions(candidate, index, state, approvalMessage, linkError, approving,
                                    onPrevious = { index = (index - 1).coerceAtLeast(0) },
                                    onNext = { index = (index + 1).coerceAtMost(state.candidates.lastIndex) },
                                    onSkip = { onDismissCandidate(candidate.artwork.id) },
                                    onApprove = { onApprove(candidate) }, onOpen = ::open)
                            }
                        }
                    }
                    if (candidate != null) {
                        FilledTonalButton(onClick = { index = 0; onFetch() }, enabled = !state.loading && !approving,
                            modifier = Modifier.fillMaxWidth().testTag("homeArtworkReviewFetchMore")) {
                            Text(if (state.loading) "Fetching…" else "Fetch more")
                        }
                    }
                    Text("Only images you explicitly approve are saved privately for Home.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ArtworkPreview(candidate: UnreviewedArtwork, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.clip(RoundedCornerShape(24.dp)), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        candidate.artwork.bitmap?.let { bitmap ->
            Image(bitmap, candidate.artwork.title, Modifier.fillMaxSize().clickable(onClick = onOpen), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun ArtworkPreviewActions(
    candidate: UnreviewedArtwork,
    index: Int,
    state: ArtworkReviewState,
    approvalMessage: String?,
    linkError: String?,
    approving: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onApprove: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = candidate.artwork
    var details by remember(artwork.id) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Not approved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("${index.coerceAtMost(state.candidates.lastIndex) + 1} / ${state.candidates.size}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(artwork.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(artwork.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Preview only until you approve.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        approvalMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        linkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPrevious, enabled = index > 0) { Text("Previous") }
            TextButton(onClick = onNext, enabled = index < state.candidates.lastIndex) { Text("Next") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSkip, enabled = !approving, modifier = Modifier.weight(1f)) { Text("Skip") }
            Button(onClick = onApprove, enabled = !approving,
                modifier = Modifier.weight(1f).testTag("homeArtworkApprove")) { Text("Approve") }
        }
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Source & rights") }
        if (details) {
            Text("Reported license: ${artwork.licenseName}", style = MaterialTheme.typography.bodySmall)
            Text("Reference: ${artwork.id}", style = MaterialTheme.typography.labelSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpen(artwork.imageUrl) }) { Text("Full image") }
                TextButton(onClick = { onOpen(artwork.sourceUrl) }) { Text("Source") }
                TextButton(onClick = { onOpen(artwork.licenseUrl) }) { Text("Rights") }
            }
        }
    }
}
