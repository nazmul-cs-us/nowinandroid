package com.starception.submission.core.duadatabase

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Dua (Fortress of the Muslim) database dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object DuaDatabaseModule {

    /**
     * Provide DuaDatabase instance
     */
    @Provides
    @Singleton
    fun provideDuaDatabase(
        @ApplicationContext context: Context
    ): DuaDatabase {
        return DuaDatabase.getInstance(context)
    }

    /**
     * Provide DuaDao instance
     */
    @Provides
    @Singleton
    fun provideDuaDao(database: DuaDatabase): DuaDao {
        return database.duaDao()
    }

    /**
     * Provide DuaRepository instance
     */
    @Provides
    @Singleton
    fun provideDuaRepository(duaDao: DuaDao): DuaRepository {
        return DuaRepository(duaDao)
    }
}
