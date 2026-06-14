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
