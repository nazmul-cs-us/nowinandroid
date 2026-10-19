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

package com.starception.submission.core.contentdatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Room Database for Topics
 * Pre-populated from assets/databases/topics.db
 */
@Database(
    entities = [TopicEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TopicsDatabase : RoomDatabase() {

    abstract fun topicsDao(): TopicsDao

    companion object {
        private const val DATABASE_NAME = "topics.db"
        private const val TAG = "TopicsDatabase"

        @Volatile
        private var dbInstance: TopicsDatabase? = null

        fun getInstance(context: Context): TopicsDatabase {
            return dbInstance ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TopicsDatabase::class.java,
                    DATABASE_NAME,
                )
                    .createFromAsset("databases/$DATABASE_NAME")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            ensureShamayeleTopic(db)
                            ensureHadithCollectionTopics(db)
                        }
                    })
                    .build()
                dbInstance = instance
                instance
            }
        }

        /**
         * Close database instance and clear singleton
         */
        fun closeDatabase() {
            dbInstance?.close()
            dbInstance = null
        }

        /** Adds the system topic to databases created by an older app version. */
        private fun ensureShamayeleTopic(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR REPLACE INTO topics (
                    id, name, short_description, long_description, image_url, url, icon,
                    is_system, is_user_created, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    9,
                    "Shamai'l At-Tirmidhi",
                    "الشمائل المحمدية",
                    "Explore 56 books and 322 hadiths describing the Prophet's appearance, " +
                        "character, manners, and daily life.",
                    "https://cdn-icons-png.flaticon.com/512/4556/4556746.png",
                    null,
                    null,
                    1,
                    0,
                    null,
                    null,
                ),
            )
        }

        /**
         * Adds the downloadable hadith-collection topics to databases created by an
         * older app version. Names must match [com.starception.submission.core.model
         * .data.HadithCollections] so the TopicScreen book browser resolves them.
         */
        private fun ensureHadithCollectionTopics(db: SupportSQLiteDatabase) {
            val topics = listOf(
                HadithTopic(
                    38, "Sahih Muslim", "صحيح مسلم",
                    "Sahih Muslim is one of the 'Two Sahihs' — the most authentic hadith " +
                        "collections after the Qur'an, compiled by Imam Muslim ibn al-Hajjaj. " +
                        "Browse 57 books and 7,459 hadiths.",
                    "https://cdn-icons-png.flaticon.com/512/17410/17410809.png",
                ),
                HadithTopic(
                    39, "Jami' at-Tirmidhi", "جامع الترمذي",
                    "Jami' at-Tirmidhi by Imam Abu Isa al-Tirmidhi is one of the six canonical " +
                        "collections, valued for its grading and commentary. Browse 49 books " +
                        "and 4,053 hadiths.",
                    "https://cdn-icons-png.flaticon.com/512/11633/11633008.png",
                ),
                HadithTopic(
                    40, "Sunan Abu Dawud", "سنن أبي داود",
                    "Sunan Abu Dawud by Imam Abu Dawud al-Sijistani is one of the six canonical " +
                        "collections, focused on legal rulings. Browse 43 books and 5,276 hadiths.",
                    "https://cdn-icons-png.flaticon.com/512/17363/17363743.png",
                ),
                HadithTopic(
                    41, "Sunan an-Nasa'i", "سنن النسائي",
                    "Sunan an-Nasa'i by Imam Ahmad ibn Shu'ayb al-Nasa'i is one of the six " +
                        "canonical collections. Browse 52 books and 5,768 hadiths.",
                    "https://cdn-icons-png.flaticon.com/512/3459/3459381.png",
                ),
                HadithTopic(
                    42, "Sunan Ibn Majah", "سنن ابن ماجه",
                    "Sunan Ibn Majah by Imam Ibn Majah al-Qazwini completes the six canonical " +
                        "collections. Browse 38 books and 4,345 hadiths.",
                    "https://cdn-icons-png.flaticon.com/512/3459/3459034.png",
                ),
                HadithTopic(
                    43, "Muwatta Malik", "موطأ مالك",
                    "The Muwatta of Imam Malik ibn Anas is the earliest surviving collection of " +
                        "hadith and Islamic law. Browse 61 books and 1,985 hadiths.",
                    "https://cdn-icons-png.flaticon.com/512/3528/3528211.png",
                ),
                HadithTopic(
                    44, "Sunan ad-Darimi", "سنن الدارمي",
                    "Sunan ad-Darimi by Imam Abdullah ibn Abd ar-Rahman ad-Darimi is an early " +
                        "topical collection. Browse 25 books and 3,534 hadiths in Arabic and English.",
                    "https://cdn-icons-png.flaticon.com/512/9358/9358484.png",
                ),
                HadithTopic(
                    45, "Musnad Ahmad", "مسند أحمد",
                    "The Musnad of Imam Ahmad ibn Hanbal is the largest hadith collection, " +
                        "arranged by companion. Browse 15 sections and 27,642 hadiths in Arabic " +
                        "and English.",
                    "https://cdn-icons-png.flaticon.com/512/17364/17364182.png",
                ),
            )
            for (topic in topics) {
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO topics (
                        id, name, short_description, long_description, image_url, url, icon,
                        is_system, is_user_created, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        topic.id,
                        topic.name,
                        topic.shortDescription,
                        topic.longDescription,
                        topic.imageUrl,
                        null,
                        null,
                        1,
                        0,
                        null,
                        null,
                    ),
                )
            }
        }

        /** One row of [ensureHadithCollectionTopics]. */
        private data class HadithTopic(
            val id: Int,
            val name: String,
            val shortDescription: String,
            val longDescription: String,
            val imageUrl: String,
        )

        /**
         * Refresh database from assets using Room DAO operations.
         * This keeps the database connection alive so Flows continue to work.
         */
        suspend fun refreshFromAssets(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting topics database refresh from assets...")

                    val db = getInstance(context)
                    val dao = db.topicsDao()

                    // Copy asset database to temp file to read from
                    val tempFile = File(context.cacheDir, "temp_topics.db")
                    context.assets.open("databases/$DATABASE_NAME").use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Read topics from asset database
                    val assetDb = SQLiteDatabase.openDatabase(
                        tempFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    )

                    val topics = mutableListOf<TopicEntity>()
                    val cursor = assetDb.rawQuery("SELECT * FROM topics ORDER BY id ASC", null)

                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        val shortDescription = cursor.getString(cursor.getColumnIndexOrThrow("short_description"))
                        val longDescription = cursor.getString(cursor.getColumnIndexOrThrow("long_description"))
                        val url = cursor.getString(cursor.getColumnIndexOrThrow("url"))
                        val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url"))
                        val isSystem = cursor.getInt(cursor.getColumnIndexOrThrow("is_system"))
                        val isUserCreated = cursor.getInt(cursor.getColumnIndexOrThrow("is_user_created"))
                        // Optional columns that might not exist in asset database
                        val icon = cursor.getColumnIndex("icon").takeIf { it >= 0 }?.let { cursor.getString(it) }
                        val createdAt = cursor.getColumnIndex("created_at").takeIf { it >= 0 }?.let { cursor.getString(it) }
                        val updatedAt = cursor.getColumnIndex("updated_at").takeIf { it >= 0 }?.let { cursor.getString(it) }

                        topics.add(
                            TopicEntity(
                                id = id,
                                name = name,
                                shortDescription = shortDescription,
                                longDescription = longDescription,
                                url = url,
                                imageUrl = imageUrl,
                                icon = icon,
                                isSystem = isSystem,
                                isUserCreated = isUserCreated,
                                createdAt = createdAt,
                                updatedAt = updatedAt,
                            ),
                        )
                    }
                    cursor.close()
                    assetDb.close()
                    tempFile.delete()

                    Log.d(TAG, "Read ${topics.size} topics from assets")

                    // Clear and repopulate using DAO (keeps connection alive)
                    dao.deleteAllTopics()
                    dao.insertTopics(topics)

                    // Trigger invalidation so Flows update
                    db.invalidationTracker.refreshVersionsAsync()

                    Log.d(TAG, "Topics database refreshed successfully: ${topics.size} topics")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing topics database", e)
                    false
                }
            }
        }
    }
}
