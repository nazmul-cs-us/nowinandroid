package com.starception.submission.core.topicsdatabase

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Topics database dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object TopicsDatabaseModule {

    /**
     * Provide TopicsDatabase instance
     */
    @Provides
    @Singleton
    fun provideTopicsDatabase(
        @ApplicationContext context: Context
    ): TopicsDatabase {
        return TopicsDatabase.getInstance(context)
    }

    /**
     * Provide TopicsDao instance
     */
    @Provides
    @Singleton
    fun provideTopicsDao(database: TopicsDatabase): TopicsDao {
        return database.topicsDao()
    }

    /**
     * Provide TopicsRepository instance
     */
    @Provides
    @Singleton
    fun provideTopicsRepository(topicsDao: TopicsDao): TopicsRepository {
        return TopicsRepository(topicsDao)
    }
}
