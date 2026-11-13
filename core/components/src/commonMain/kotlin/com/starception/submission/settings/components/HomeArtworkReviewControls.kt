package com.starception.submission.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import com.starception.submission.core.images.LocalHomeArtworkRepository
import com.starception.submission.core.images.rememberHomeArtworkRepository
import com.starception.submission.core.images.UnreviewedArtwork
import kotlinx.coroutines.launch

/** Restriction applies to a separate review gallery, never to Home's approval requirement. */
@Composable
fun HomeArtworkReviewControls(theme: ArtworkTheme) {
    var restricted by rememberSaveable { mutableStateOf(true) }
    var confirmUnrestricted by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    val repository = LocalArtworkReviewRepository.current ?: rememberArtworkReviewRepository()
    val state by repository.state.collectAsStateWithLifecycle()
    val home = LocalHomeArtworkRepository.current ?: rememberHomeArtworkRepository()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<UnreviewedArtwork?>(null) }
    var approvalMessage by remember { mutableStateOf<String?>(null) }
    var approving by remember { mutableStateOf(false) }
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
        approvalMessage = approvalMessage, approving = approving,
        onApprove = { selected = it },
        onFetch = { if (!restricted) repository.fetch(theme, allowUnreviewed = true) },
        onDismissCandidate = repository::dismiss,
        onClose = { showGallery = false; repository.clear() },
        onRestrict = { restricted = true; showGallery = false; repository.clear() })
    selected?.let { candidate ->
        var contentConfirmed by remember(candidate.artwork.id) { mutableStateOf(false) }
        var rightsConfirmed by remember(candidate.artwork.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!approving) selected = null },
            title = { Text("Approve for my device") },
            text = { Column {
                Text("This saves the image privately and adds it to Home. It does not publish it for anyone else.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(contentConfirmed, { contentConfirmed = it }, modifier = Modifier.testTag("homeArtworkConfirmContent"))
                    Text("I checked the full image: no people or animals.")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rightsConfirmed, { rightsConfirmed = it }, modifier = Modifier.testTag("homeArtworkConfirmRights"))
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
    onRestrict: () -> Unit,
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("homeArtworkReviewGallery"),
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Choose artwork", style = MaterialTheme.typography.titleLarge)
                        Text("${theme.label} · Private on this device", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Text("Preview only until you approve. Check content and reuse rights.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                approvalMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (candidate != null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                linkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (candidate == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (state.loading) CircularProgressIndicator()
                            Text(if (state.loading) "Finding your next artwork…" else "No artwork loaded",
                                style = MaterialTheme.typography.titleMedium)
                            if (!state.loading) {
                                Text(state.error ?: "Fetch a fresh collection to start reviewing.",
                                    style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Button(onClick = onFetch, enabled = !approving, modifier = Modifier.testTag("homeArtworkReviewRetry")) { Text("Try again") }
                            }
                        }
                    }
                } else {
                        val artwork = candidate.artwork
                            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Not approved", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                artwork.bitmap?.let { bitmap ->
                                    Image(bitmap, artwork.title, Modifier.fillMaxWidth().weight(1f)
                                        .clickable { open(artwork.imageUrl) }, contentScale = ContentScale.Fit)
                                }
                                Text(artwork.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text(artwork.artist, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { index = (index - 1).coerceAtLeast(0) }, enabled = index > 0) { Text("Previous") }
                                    Text("${index.coerceAtMost(state.candidates.lastIndex) + 1} / ${state.candidates.size}", style = MaterialTheme.typography.labelMedium)
                                    TextButton(onClick = { index = (index + 1).coerceAtMost(state.candidates.lastIndex) }, enabled = index < state.candidates.lastIndex) { Text("Next") }
                                }
                                var details by remember(artwork.id) { mutableStateOf(false) }
                                TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Source & rights") }
                                if (details) Text("Reported license: ${artwork.licenseName} · Verify before approval", style = MaterialTheme.typography.bodySmall)
                                if (details) Text("Reference: ${artwork.id}", style = MaterialTheme.typography.labelSmall)
                                if (details) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { open(artwork.imageUrl) }) { Text("Full image") }
                                    TextButton(onClick = { open(artwork.sourceUrl) }) { Text("Source") }
                                    TextButton(onClick = { open(artwork.licenseUrl) }) { Text("Rights") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = { onDismissCandidate(artwork.id) }, enabled = !approving, modifier = Modifier.weight(1f)) { Text("Skip") }
                                    Button(onClick = { onApprove(candidate) }, enabled = !approving,
                                        modifier = Modifier.weight(1f).testTag("homeArtworkApprove")) { Text("Approve") }
                                }
                            }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onRestrict, modifier = Modifier.testTag("homeArtworkReviewRestrict")) { Text("Restrict") }
                    if (candidate != null) FilledTonalButton(onClick = { index = 0; onFetch() }, enabled = !state.loading && !approving, modifier = Modifier.testTag("homeArtworkReviewFetchMore")) {
                        Text(if (state.loading) "Fetching…" else "Fetch more")
                    }
                }
                Text("Only images you explicitly approve are saved privately for Home.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
