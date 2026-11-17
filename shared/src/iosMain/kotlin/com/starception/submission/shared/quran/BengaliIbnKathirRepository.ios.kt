package com.starception.submission.shared.quran

import com.starception.submission.feature.quran.BengaliIbnKathir
import com.starception.submission.feature.quran.BengaliIbnKathirRepository
import com.starception.submission.shared.assets.iosCloudAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile

internal actual fun createBengaliIbnKathirRepository(): BengaliIbnKathirRepository =
    BengaliIbnKathirRepository { surah ->
        val asset = iosCloudAssets.resolveAsset(BengaliIbnKathir.chapterKey(surah))
            ?: error("Bengali tafsir chapter unavailable")
        withContext(Dispatchers.Default) {
            val data = NSData.dataWithContentsOfFile(asset.absolutePath) ?: error("Bengali tafsir file missing")
            (NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?)
                ?: error("Invalid Bengali tafsir encoding")
        }
    }
