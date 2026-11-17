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
        val asset = assets.resolveAsset(edition.chapterKey(surah))
            ?: error("Published tafsir chapter unavailable")
        withContext(Dispatchers.IO) { File(asset.absolutePath).readText(Charsets.UTF_8) }
    }
}
