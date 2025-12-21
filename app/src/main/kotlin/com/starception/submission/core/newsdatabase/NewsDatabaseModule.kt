package com.starception.submission.core.newsdatabase

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for News Resources database dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object NewsDatabaseModule {

    /**
     * Provide NewsDatabase instance
     */
    @Provides
    @Singleton
    fun provideNewsDatabase(
        @ApplicationContext context: Context
    ): NewsDatabase {
        return NewsDatabase.getInstance(context)
    }

    /**
     * Provide NewsDao instance
     */
    @Provides
    @Singleton
    fun provideNewsDao(database: NewsDatabase): NewsDao {
        return database.newsDao()
    }

    /**
     * Provide NewsRepository instance
     */
    @Provides
    @Singleton
    fun provideNewsRepository(newsDao: NewsDao): NewsRepository {
        return NewsRepository(newsDao)
    }
}
