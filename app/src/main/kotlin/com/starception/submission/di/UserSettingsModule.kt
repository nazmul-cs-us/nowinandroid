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

package com.starception.submission.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.starception.submission.usersettings.UserSettingsDao
import com.starception.submission.usersettings.UserSettingsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the writable user-settings SQLite database and its DAO.
 * [com.starception.submission.usersettings.UserSettingsStore] is injected via its @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object UserSettingsModule {

    @Provides
    @Singleton
    fun provideUserSettingsDatabase(
        @ApplicationContext context: Context,
    ): UserSettingsDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            UserSettingsDatabase::class.java,
            UserSettingsDatabase.DATABASE_NAME,
        )
            // TRUNCATE (no WAL side files) keeps the single .db file complete after every commit,
            // so the cloud-sync layer can upload it as one self-contained artifact.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideUserSettingsDao(database: UserSettingsDatabase): UserSettingsDao =
        database.userSettingsDao()
}
