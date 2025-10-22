package com.starception.submission.core.qurandatabase

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Quran database dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object QuranDatabaseModule {
    
    /**
     * Provide QuranDatabase instance
     */
    @Provides
    @Singleton
    fun provideQuranDatabase(
        @ApplicationContext context: Context
    ): QuranDatabase {
        return QuranDatabase.getInstance(context)
    }
    
    /**
     * Provide QuranDao instance
     */
    @Provides
    @Singleton
    fun provideQuranDao(database: QuranDatabase): QuranDao {
        return database.quranDao()
    }
    
    /**
     * Provide QuranRepository instance
     */
    @Provides
    @Singleton
    fun provideQuranRepository(
        @ApplicationContext context: Context
    ): QuranRepository {
        return QuranRepository(context)
    }
}

