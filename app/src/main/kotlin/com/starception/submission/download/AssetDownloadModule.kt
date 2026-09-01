package com.starception.submission.download

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssetDownloadModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Fail fast when the network is dead so the manifest fallback chain runs quickly.
        // readTimeout is per-read (not per-request), so streaming downloads are unaffected
        // as long as bytes keep flowing within the window.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideAssetDownloadManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): AssetDownloadManager = AssetDownloadManager(context, okHttpClient)

    @Provides
    @Singleton
    fun provideAssetRepository(
        @ApplicationContext context: Context,
        downloadManager: AssetDownloadManager,
    ): AssetRepository = AssetRepository(context, downloadManager)
}
