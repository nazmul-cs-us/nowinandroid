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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
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
