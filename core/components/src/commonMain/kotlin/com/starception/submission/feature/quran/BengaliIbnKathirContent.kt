package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Published Bengali text only: errors never fall back to Arabic or machine-generated Bengali. */
@Composable
fun BengaliIbnKathirContent(surah: Int, ayah: Int, repository: BengaliIbnKathirRepository, modifier: Modifier = Modifier) {
    var text by remember(surah, ayah) { mutableStateOf<String?>(null) }
    var failed by remember(surah, ayah) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    var completed by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf(false) }
    val textScroll = remember(surah, ayah) { androidx.compose.foundation.ScrollState(0) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    LaunchedEffect(surah, ayah, repository, retry) {
        text = null; failed = false
        try { text = repository.text(surah, ayah) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(BengaliIbnKathir.TITLE, style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
                Text("বাংলা · ${BengaliIbnKathir.PUBLISHER}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { uri.openUri(BengaliIbnKathir.SOURCE_URL) }) { Text("Source") }
        }
        when {
            failed -> {
                Text("Could not load this Bengali tafsir. Connect to download it, or try again if already saved.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text("Try again") }
            }
            text == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> SelectionContainer {
                Text(text.orEmpty(), style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 30.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                        .verticalScroll(textScroll))
            }
        }
        if (downloading) {
            LinearProgressIndicator(progress = { completed / 114f }, modifier = Modifier.fillMaxWidth())
            Text("Saving $completed of 114 surahs…", style = MaterialTheme.typography.bodySmall)
        } else {
            TextButton(enabled = completed != 114, onClick = {
                scope.launch {
                    downloading = true; downloadError = false
                    try { repository.downloadAll { count, _ -> completed = count } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { downloadError = true }
                    finally { downloading = false }
                }
            }) { Text(if (completed == 114) "Full tafsir saved offline" else "Download full tafsir for offline reading") }
        }
        if (downloadError) Text("Download interrupted. Saved chapters remain available; tap download to continue.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
