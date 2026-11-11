package com.starception.submission.download

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReadingAssetsEntryPoint {
    fun assetRepository(): AssetRepository
}

/** Bridges legacy context-only readers to the same downloaded files used by setup. */
internal fun readingAssetRepository(context: Context): AssetRepository? = runCatching {
    EntryPointAccessors.fromApplication(context.applicationContext, ReadingAssetsEntryPoint::class.java).assetRepository()
}.getOrNull()
