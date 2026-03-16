package com.starception.submission.core.qurandatabase

import android.content.Context
import com.starception.submission.download.AssetRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Quran database dependency injection
 * Provides both standard and enhanced Quran databases
 */
@Module
@InstallIn(SingletonComponent::class)
object QuranDatabaseModule {

    /**
     * Provide QuranDatabase instance (standard database with translations)
     */
    @Provides
    @Singleton
    fun provideQuranDatabase(
        @ApplicationContext context: Context,
        assetRepository: AssetRepository,
    ): QuranDatabase {
        return QuranDatabase.getInstance(context, assetRepository)
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
     * Provide QuranEnhancedDatabase instance (comprehensive Arabic database with Tafseer)
     */
    @Provides
    @Singleton
    fun provideQuranEnhancedDatabase(
        @ApplicationContext context: Context,
        assetRepository: AssetRepository,
    ): QuranEnhancedDatabase {
        return QuranEnhancedDatabase.getInstance(context, assetRepository)
    }

    /**
     * Provide QuranEnhancedDao instance
     */
    @Provides
    @Singleton
    fun provideQuranEnhancedDao(database: QuranEnhancedDatabase): QuranEnhancedDao {
        return database.quranEnhancedDao()
    }

    /**
     * Provide QuranRepository instance (standard database)
     */
    @Provides
    @Singleton
    fun provideQuranRepository(
        @ApplicationContext context: Context,
        assetRepository: AssetRepository,
    ): QuranRepository {
        return QuranRepository(context, assetRepository)
    }

    /**
     * Provide QuranEnhancedRepository instance (enhanced database with Tafseer)
     */
    @Provides
    @Singleton
    fun provideQuranEnhancedRepository(
        quranEnhancedDao: QuranEnhancedDao
    ): QuranEnhancedRepository {
        return QuranEnhancedRepository(quranEnhancedDao)
    }

    /**
     * Provide UnifiedQuranRepository instance (both databases)
     */
    @Provides
    @Singleton
    fun provideUnifiedQuranRepository(
        quranDao: QuranDao,
        quranEnhancedDao: QuranEnhancedDao
    ): UnifiedQuranRepository {
        return UnifiedQuranRepository(quranDao, quranEnhancedDao)
    }
}

