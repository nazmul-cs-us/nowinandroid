package com.starception.submission.core.contentdatabase

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Content database dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object ContentDatabaseModule {

    // ============= Topics Database =============

    @Provides
    @Singleton
    fun provideTopicsDatabase(
        @ApplicationContext context: Context
    ): TopicsDatabase {
        return TopicsDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideTopicsDao(database: TopicsDatabase): TopicsDao {
        return database.topicsDao()
    }

    // News Database is provided by NewsDatabaseModule in app module
    // to enable CDN asset resolution via AssetRepository

    @Provides
    @Singleton
    fun provideNewsDao(database: NewsDatabase): NewsDao {
        return database.newsDao()
    }
}
