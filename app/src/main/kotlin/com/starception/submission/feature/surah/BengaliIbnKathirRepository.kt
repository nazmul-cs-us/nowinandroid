package com.starception.submission.feature.surah

import android.content.Context
import com.starception.submission.core.assetcache.CloudAssetRepository
import com.starception.submission.download.AndroidAssetPlatform
import com.starception.submission.feature.quran.PublishedTafsirs
import com.starception.submission.feature.quran.PublishedTafsirEdition
import com.starception.submission.feature.quran.PublishedTafsirRepository
import com.starception.submission.feature.quran.BengaliIbnKathirRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

private val tafsirHttpClient by lazy { OkHttpClient() }

internal fun createBengaliIbnKathirRepository(context: Context): BengaliIbnKathirRepository =
    createPublishedTafsirRepository(context, PublishedTafsirs.bengali)

internal fun createPublishedTafsirRepository(context: Context, edition: PublishedTafsirEdition): PublishedTafsirRepository {
    val assets = CloudAssetRepository(AndroidAssetPlatform(context.applicationContext, tafsirHttpClient))
    return PublishedTafsirRepository { surah ->
        val asset = assets.lookupAsset(edition.chapterKey(surah))
            ?: throw com.starception.submission.feature.quran.PublishedTafsirAssetMissing()
        withContext(Dispatchers.IO) { File(asset.absolutePath).readText(Charsets.UTF_8) }
    }
}

@androidx.compose.runtime.Composable
internal fun MissingTafsirAssetCard(edition: PublishedTafsirEdition, onDownloadComplete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = androidx.compose.runtime.remember(context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,
            com.starception.submission.voice.SherpaOnnxTtsEntryPoint::class.java).assetDownloadManager()
    }
    com.starception.submission.download.MissingContentCard(
        resourceName = "${edition.englishLanguageName} · ${edition.title}",
        category = edition.category,
        description = "Download all 114 surahs to read this tafsir offline. You can manage it in Content & Storage → Tafsir.",
        downloadManager = manager, onDownloadComplete = onDownloadComplete, compact = true,
    )
}
