package com.starception.submission.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [MissingContentCard] under a content-backed topic's header (Holy Quran / Sahih Bukhari)
 * when that topic's database isn't downloaded yet — the same on-demand download UX as the
 * Surah/Hadith detail screens. No-op for ordinary topics or when the content is present.
 *
 * It does NOT rebuild derived content itself: [ContentCoordinator] regenerates news.db when the
 * download completes (see its docs). Here we just observe [ContentCoordinator.isRebuilding] to show
 * a brief "preparing" state; once the rebuild finishes the topic's reactive news list fills in.
 */
@Composable
fun TopicMissingContentCard(topicName: String) {
    val spec = remember(topicName) { topicContentSpec(topicName) } ?: return

    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SherpaOnnxTtsEntryPoint::class.java)
    }
    val downloadManager = remember { entryPoint.assetDownloadManager() }
    val rebuilding by entryPoint.contentCoordinator().isRebuilding.collectAsStateWithLifecycle()

    var available by remember(spec) { mutableStateOf<Boolean?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(spec, reloadTrigger, rebuilding) {
        available = withContext(Dispatchers.IO) { downloadManager.isAssetAvailable(spec.cdnKey) }
    }

    when {
        rebuilding -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = "  Preparing ${spec.resourceName} content…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        available == false -> {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                MissingContentCard(
                    resourceName = spec.resourceName,
                    category = spec.category,
                    description = spec.description,
                    downloadManager = downloadManager,
                    // ContentCoordinator handles the news.db rebuild; just re-check availability.
                    onDownloadComplete = { reloadTrigger++ },
                )
            }
        }
    }
}

private data class TopicContentSpec(
    val cdnKey: String,
    val category: String,
    val resourceName: String,
    val description: String,
)

/** Maps a topic name to its downloadable content, or null for topics without bundled-DB content. */
private fun topicContentSpec(name: String): TopicContentSpec? = when {
    name.contains("Quran", ignoreCase = true) -> TopicContentSpec(
        cdnKey = "databases/quran/quran.db",
        category = "quran_core",
        resourceName = "Holy Quran",
        description = "Arabic text, translations, and Tafseer.",
    )
    name.contains("Bukhari", ignoreCase = true) -> TopicContentSpec(
        // The topic feed is generated from the JSON (the reader DB is fetched separately by the
        // Hadith screen on demand), so gate/download on the JSON the generator actually reads.
        cdnKey = "json/sahih_bukhari.json",
        category = "json_data",
        resourceName = "Sahih Bukhari",
        description = "The Sahih Bukhari hadith collection.",
    )
    else -> null
}
