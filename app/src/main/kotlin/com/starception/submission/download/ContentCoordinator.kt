package com.starception.submission.download

import android.content.Context
import android.util.Log
import com.starception.submission.core.contentdatabase.NewsDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges "a content category finished downloading" → "rebuild the data derived from it".
 *
 * The topic feed, For You, and search all read `news.db`, which `NewsDbGenerator` builds FROM the
 * source databases (quran.db, sahih_bukhari, …). Those sources are downloaded on demand, so without
 * this coordinator a freshly-downloaded DB never reaches the UI. Centralising the rebuild here means
 * screens only ever need to (a) show a download CTA and (b) observe [isRebuilding] — none of them
 * re-implement regeneration.
 *
 * Observes [AssetDownloadManager.categoryCompleted], debounces bursts, and regenerates news.db once.
 */
@Singleton
class ContentCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: AssetDownloadManager,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    private val _isRebuilding = MutableStateFlow(false)
    /** True while news.db is being regenerated after a content download. */
    val isRebuilding: StateFlow<Boolean> = _isRebuilding.asStateFlow()

    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            downloadManager.categoryCompleted
                .filter { it.feedsNewsDb() }
                .debounce(REBUILD_DEBOUNCE_MS)
                .collect { rebuildNewsDb() }
        }
    }

    private suspend fun rebuildNewsDb() {
        _isRebuilding.value = true
        try {
            val result = NewsDatabase.regenerateFromSources(context)
            Log.i(TAG, "news.db rebuilt after content download: success=${result.success}")
        } catch (e: Exception) {
            Log.e(TAG, "news.db rebuild failed", e)
        } finally {
            _isRebuilding.value = false
        }
    }

    companion object {
        private const val TAG = "ContentCoordinator"
        private const val REBUILD_DEBOUNCE_MS = 1200L
    }
}

/**
 * Categories whose downloaded source feeds news.db generation:
 * - quran_core / quran_translation / quran_enhanced → quran.db (Surahs)
 * - json_data → json/sahih_bukhari.json (Bukhari hadiths)
 */
private fun String.feedsNewsDb(): Boolean =
    startsWith("quran", ignoreCase = true) || equals("json_data", ignoreCase = true)
