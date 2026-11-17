package com.starception.submission.shared.quran

import com.starception.submission.feature.quran.PublishedTafsirEdition
import com.starception.submission.feature.quran.PublishedTafsirRepository
import com.starception.submission.shared.assets.iosCloudAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile

internal actual fun createPublishedTafsirRepository(edition: PublishedTafsirEdition): PublishedTafsirRepository =
    PublishedTafsirRepository { surah ->
        val asset = iosCloudAssets.resolveAsset(edition.chapterKey(surah))
            ?: error("Published tafsir chapter unavailable")
        withContext(Dispatchers.Default) {
            val data = NSData.dataWithContentsOfFile(asset.absolutePath) ?: error("Published tafsir file missing")
            (NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?)
                ?: error("Invalid Published tafsir encoding")
        }
    }
