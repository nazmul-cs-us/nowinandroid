/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        @ApplicationContext context: Context,
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
