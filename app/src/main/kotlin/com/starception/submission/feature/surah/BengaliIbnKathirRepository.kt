package com.starception.submission.feature.surah

import android.content.Context
import com.starception.submission.core.assetcache.CloudAssetRepository
import com.starception.submission.download.AndroidAssetPlatform
import com.starception.submission.feature.quran.BengaliIbnKathir
import com.starception.submission.feature.quran.BengaliIbnKathirRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

internal fun createBengaliIbnKathirRepository(context: Context): BengaliIbnKathirRepository {
    val assets = CloudAssetRepository(AndroidAssetPlatform(context.applicationContext, OkHttpClient()))
    return BengaliIbnKathirRepository { surah ->
        val asset = assets.resolveAsset(BengaliIbnKathir.chapterKey(surah))
            ?: error("Bengali tafsir chapter unavailable")
        withContext(Dispatchers.IO) { File(asset.absolutePath).readText(Charsets.UTF_8) }
    }
}
