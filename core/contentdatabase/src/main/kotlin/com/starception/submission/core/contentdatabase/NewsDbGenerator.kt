package com.starception.submission.core.contentdatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates news.db from source databases (quran.db, fortress_of_the_muslim.db, quranic_duas.db)
 *
 * This class reads content from source databases and creates/updates news_resources
 * and news_topics tables in news.db. Run this when source databases are updated
 * to regenerate news content.
 *
 * Topic IDs:
 * - 7: Holy Quran (Surahs)
 * - 11: Quranic Duas
 * - 21-37: Fortress of the Muslim categories
 */
object NewsDbGenerator {
    private const val TAG = "NewsDbGenerator"

    // Topic IDs
    private const val TOPIC_HOLY_QURAN = 7
    private const val TOPIC_SAHIH_BUKHARI = 8
    private const val TOPIC_QURANIC_DUAS = 11
    private const val TOPIC_MORNING_EVENING = 21
    private const val TOPIC_PRAYER = 22
    private const val TOPIC_HOME_DAILY = 23
    private const val TOPIC_FOOD_DRINK = 24
    private const val TOPIC_TRAVEL = 25
    private const val TOPIC_PROTECTION = 26
    private const val TOPIC_DISTRESS_ANXIETY = 27
    private const val TOPIC_HEALTH_SICKNESS = 28
    private const val TOPIC_SOCIAL_ETIQUETTE = 29
    private const val TOPIC_DEATH_FUNERAL = 30
    private const val TOPIC_WEATHER_NATURE = 31
    private const val TOPIC_HAJJ_UMRAH = 32
    private const val TOPIC_FORGIVENESS_REPENTANCE = 33
    private const val TOPIC_GUIDANCE_FAITH = 34
    private const val TOPIC_REMEMBRANCE_DHIKR = 35
    private const val TOPIC_FAMILY_MARRIAGE = 36
    private const val TOPIC_SACRIFICE_WORSHIP = 37

    // Fortress chapter to topic mapping
    private val CHAPTER_TO_TOPIC = mapOf(
        // Morning & Evening (21)
        27 to TOPIC_MORNING_EVENING, 28 to TOPIC_MORNING_EVENING, 29 to TOPIC_MORNING_EVENING,
        30 to TOPIC_MORNING_EVENING, 31 to TOPIC_MORNING_EVENING,

        // Prayer (22)
        12 to TOPIC_PRAYER, 13 to TOPIC_PRAYER, 14 to TOPIC_PRAYER, 15 to TOPIC_PRAYER,
        16 to TOPIC_PRAYER, 17 to TOPIC_PRAYER, 18 to TOPIC_PRAYER, 19 to TOPIC_PRAYER,
        20 to TOPIC_PRAYER, 21 to TOPIC_PRAYER, 22 to TOPIC_PRAYER, 23 to TOPIC_PRAYER,
        24 to TOPIC_PRAYER, 25 to TOPIC_PRAYER, 32 to TOPIC_PRAYER, 33 to TOPIC_PRAYER,

        // Home & Daily (23)
        1 to TOPIC_HOME_DAILY, 2 to TOPIC_HOME_DAILY, 3 to TOPIC_HOME_DAILY, 4 to TOPIC_HOME_DAILY,
        5 to TOPIC_HOME_DAILY, 6 to TOPIC_HOME_DAILY, 7 to TOPIC_HOME_DAILY, 8 to TOPIC_HOME_DAILY,
        9 to TOPIC_HOME_DAILY, 10 to TOPIC_HOME_DAILY, 11 to TOPIC_HOME_DAILY,

        // Food & Drink (24)
        69 to TOPIC_FOOD_DRINK, 70 to TOPIC_FOOD_DRINK, 71 to TOPIC_FOOD_DRINK,
        72 to TOPIC_FOOD_DRINK, 73 to TOPIC_FOOD_DRINK,

        // Travel (25)
        95 to TOPIC_TRAVEL, 96 to TOPIC_TRAVEL, 97 to TOPIC_TRAVEL, 98 to TOPIC_TRAVEL,
        99 to TOPIC_TRAVEL, 100 to TOPIC_TRAVEL, 101 to TOPIC_TRAVEL, 102 to TOPIC_TRAVEL,
        103 to TOPIC_TRAVEL, 104 to TOPIC_TRAVEL, 105 to TOPIC_TRAVEL,

        // Protection (26)
        38 to TOPIC_PROTECTION, 39 to TOPIC_PROTECTION, 45 to TOPIC_PROTECTION,
        88 to TOPIC_PROTECTION, 125 to TOPIC_PROTECTION, 128 to TOPIC_PROTECTION,
        36 to TOPIC_PROTECTION, 37 to TOPIC_PROTECTION,

        // Distress & Anxiety (27)
        34 to TOPIC_DISTRESS_ANXIETY, 35 to TOPIC_DISTRESS_ANXIETY, 43 to TOPIC_DISTRESS_ANXIETY,
        46 to TOPIC_DISTRESS_ANXIETY, 41 to TOPIC_DISTRESS_ANXIETY, 82 to TOPIC_DISTRESS_ANXIETY,
        83 to TOPIC_DISTRESS_ANXIETY, 106 to TOPIC_DISTRESS_ANXIETY, 126 to TOPIC_DISTRESS_ANXIETY,

        // Health & Sickness (28)
        49 to TOPIC_HEALTH_SICKNESS, 50 to TOPIC_HEALTH_SICKNESS, 51 to TOPIC_HEALTH_SICKNESS,
        124 to TOPIC_HEALTH_SICKNESS,

        // Social & Etiquette (29)
        77 to TOPIC_SOCIAL_ETIQUETTE, 78 to TOPIC_SOCIAL_ETIQUETTE, 84 to TOPIC_SOCIAL_ETIQUETTE,
        85 to TOPIC_SOCIAL_ETIQUETTE, 86 to TOPIC_SOCIAL_ETIQUETTE, 87 to TOPIC_SOCIAL_ETIQUETTE,
        107 to TOPIC_SOCIAL_ETIQUETTE, 108 to TOPIC_SOCIAL_ETIQUETTE, 109 to TOPIC_SOCIAL_ETIQUETTE,
        110 to TOPIC_SOCIAL_ETIQUETTE, 111 to TOPIC_SOCIAL_ETIQUETTE, 112 to TOPIC_SOCIAL_ETIQUETTE,
        113 to TOPIC_SOCIAL_ETIQUETTE, 114 to TOPIC_SOCIAL_ETIQUETTE,

        // Death & Funeral (30)
        52 to TOPIC_DEATH_FUNERAL, 53 to TOPIC_DEATH_FUNERAL, 54 to TOPIC_DEATH_FUNERAL,
        55 to TOPIC_DEATH_FUNERAL, 56 to TOPIC_DEATH_FUNERAL, 57 to TOPIC_DEATH_FUNERAL,
        58 to TOPIC_DEATH_FUNERAL, 59 to TOPIC_DEATH_FUNERAL, 60 to TOPIC_DEATH_FUNERAL,

        // Weather & Nature (31)
        61 to TOPIC_WEATHER_NATURE, 62 to TOPIC_WEATHER_NATURE, 63 to TOPIC_WEATHER_NATURE,
        64 to TOPIC_WEATHER_NATURE, 65 to TOPIC_WEATHER_NATURE, 66 to TOPIC_WEATHER_NATURE,
        67 to TOPIC_WEATHER_NATURE, 76 to TOPIC_WEATHER_NATURE,

        // Hajj & Umrah (32)
        115 to TOPIC_HAJJ_UMRAH, 116 to TOPIC_HAJJ_UMRAH, 117 to TOPIC_HAJJ_UMRAH,
        118 to TOPIC_HAJJ_UMRAH, 119 to TOPIC_HAJJ_UMRAH, 120 to TOPIC_HAJJ_UMRAH,
        121 to TOPIC_HAJJ_UMRAH,

        // Forgiveness & Repentance (33)
        44 to TOPIC_FORGIVENESS_REPENTANCE, 129 to TOPIC_FORGIVENESS_REPENTANCE,

        // Guidance & Faith (34)
        26 to TOPIC_GUIDANCE_FAITH, 40 to TOPIC_GUIDANCE_FAITH, 42 to TOPIC_GUIDANCE_FAITH,

        // Remembrance & Dhikr (35)
        130 to TOPIC_REMEMBRANCE_DHIKR, 131 to TOPIC_REMEMBRANCE_DHIKR, 132 to TOPIC_REMEMBRANCE_DHIKR,

        // Family & Marriage (36)
        47 to TOPIC_FAMILY_MARRIAGE, 48 to TOPIC_FAMILY_MARRIAGE, 79 to TOPIC_FAMILY_MARRIAGE,
        80 to TOPIC_FAMILY_MARRIAGE, 81 to TOPIC_FAMILY_MARRIAGE,

        // Sacrifice & Worship (37)
        68 to TOPIC_SACRIFICE_WORSHIP, 74 to TOPIC_SACRIFICE_WORSHIP, 75 to TOPIC_SACRIFICE_WORSHIP,
        127 to TOPIC_SACRIFICE_WORSHIP, 122 to TOPIC_SACRIFICE_WORSHIP, 123 to TOPIC_SACRIFICE_WORSHIP,
        89 to TOPIC_SACRIFICE_WORSHIP, 90 to TOPIC_SACRIFICE_WORSHIP, 91 to TOPIC_SACRIFICE_WORSHIP,
        92 to TOPIC_SACRIFICE_WORSHIP, 93 to TOPIC_SACRIFICE_WORSHIP, 94 to TOPIC_SACRIFICE_WORSHIP
    )

    /**
     * Regenerate news.db from source databases
     * @param context Application context
     * @return RegenerationResult with counts and status
     */
    fun regenerateNewsDb(context: Context): RegenerationResult {
        Log.d(TAG, "Starting news.db regeneration...")
        val startTime = System.currentTimeMillis()

        return try {
            // Close existing news database
            NewsDatabase.closeDatabase()

            // Create new database file
            val newsDbPath = context.getDatabasePath("news.db")
            if (newsDbPath.exists()) {
                newsDbPath.delete()
                Log.d(TAG, "Deleted existing news.db")
            }

            // Create directory if needed
            newsDbPath.parentFile?.mkdirs()

            // Create and populate the database
            val db = SQLiteDatabase.openOrCreateDatabase(newsDbPath, null)

            try {
                createSchema(db)

                var surahCount = 0
                var quranicDuaCount = 0
                var fortressCount = 0
                var topicMappings = 0

                // Generate from quran.db
                val quranResult = generateSurahs(context, db, startId = 2001)
                surahCount = quranResult.first
                topicMappings += quranResult.second

                // Generate from quranic_duas.db
                val quranicResult = generateQuranicDuas(context, db, startId = 101)
                quranicDuaCount = quranicResult.first
                topicMappings += quranicResult.second

                // Generate from fortress_of_the_muslim.db
                val fortressResult = generateFortressDuas(context, db, startId = 1001)
                fortressCount = fortressResult.first
                topicMappings += fortressResult.second

                // Generate from sahih_bukhari.json
                var bukhariCount = 0
                val bukhariResult = generateBukhariHadiths(context, db, startId = 3001)
                bukhariCount = bukhariResult.first
                topicMappings += bukhariResult.second

                db.close()

                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "News.db regeneration completed in ${duration}ms")
                Log.d(TAG, "Generated: $surahCount Surahs, $quranicDuaCount Quranic Duas, $fortressCount Fortress Duas, $bukhariCount Bukhari Hadiths")
                Log.d(TAG, "Total topic mappings: $topicMappings")

                RegenerationResult(
                    success = true,
                    surahCount = surahCount,
                    quranicDuaCount = quranicDuaCount,
                    fortressDuaCount = fortressCount,
                    bukhariHadithCount = bukhariCount,
                    topicMappings = topicMappings,
                    durationMs = duration
                )
            } catch (e: Exception) {
                db.close()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to regenerate news.db", e)
            RegenerationResult(
                success = false,
                error = e.message
            )
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS news_resources (
                id INTEGER NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                content TEXT,
                url TEXT,
                header_image_url TEXT,
                publish_date TEXT,
                type TEXT,
                is_system INTEGER NOT NULL DEFAULT 1,
                is_user_created INTEGER NOT NULL DEFAULT 0,
                source TEXT,
                created_at TEXT,
                updated_at TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS news_topics (
                news_id INTEGER NOT NULL,
                topic_id INTEGER NOT NULL,
                PRIMARY KEY (news_id, topic_id)
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)
        """)
        db.execSQL("INSERT INTO android_metadata VALUES ('en_US')")

        Log.d(TAG, "Schema created successfully")
    }

    private fun generateSurahs(context: Context, newsDb: SQLiteDatabase, startId: Int): Pair<Int, Int> {
        Log.d(TAG, "Generating Surahs from quran.db...")

        val quranDbPath = getAssetDbPath(context, "quran.db") ?: return Pair(0, 0)
        val quranDb = SQLiteDatabase.openDatabase(quranDbPath, null, SQLiteDatabase.OPEN_READONLY)

        var count = 0
        var mappings = 0
        val now = getCurrentTimestamp()

        try {
            val cursor = quranDb.rawQuery(
                "SELECT number, name_en, name_en_translation, name_ar, type, total_verses FROM surahs ORDER BY number",
                null
            )

            while (cursor.moveToNext()) {
                val number = cursor.getInt(0)
                val nameEn = cursor.getString(1) ?: ""
                val nameTranslation = cursor.getString(2) ?: ""
                val nameAr = cursor.getString(3) ?: ""
                val type = cursor.getString(4) ?: "Meccan"
                val totalVerses = cursor.getInt(5)

                val newsId = startId + number - 1
                val ordinal = getOrdinal(number)

                // Set mosque image based on revelation type
                val mosqueImage = when (type) {
                    "Meccan" -> "drawable://masjid_al_haram"
                    "Medinan" -> "drawable://masjid_al_nawabi"
                    else -> "drawable://masjid_al_haram" // Default to Makkah
                }

                val title = "Surah $number: $nameEn ($nameTranslation)"
                val content = """**Arabic:** $nameAr

**Type:** $type

**Verses:** $totalVerses

Read and listen to Surah $nameEn, the $nameTranslation. This is the $ordinal chapter of the Holy Quran with $totalVerses verses."""

                newsDb.execSQL(
                    """INSERT INTO news_resources (id, title, content, header_image_url, type, is_system, created_at, updated_at)
                       VALUES (?, ?, ?, ?, 'Surah 📖', 1, ?, ?)""",
                    arrayOf(newsId, title, content, mosqueImage, now, now)
                )

                newsDb.execSQL(
                    "INSERT INTO news_topics (news_id, topic_id) VALUES (?, ?)",
                    arrayOf(newsId, TOPIC_HOLY_QURAN)
                )

                count++
                mappings++
            }

            cursor.close()
        } finally {
            quranDb.close()
        }

        Log.d(TAG, "Generated $count Surahs")
        return Pair(count, mappings)
    }

    private fun generateQuranicDuas(context: Context, newsDb: SQLiteDatabase, startId: Int): Pair<Int, Int> {
        Log.d(TAG, "Generating Quranic Duas from quranic_duas.db...")

        val duaDbPath = getAssetDbPath(context, "quranic_duas.db") ?: return Pair(0, 0)
        val duaDb = SQLiteDatabase.openDatabase(duaDbPath, null, SQLiteDatabase.OPEN_READONLY)

        var count = 0
        var mappings = 0
        val now = getCurrentTimestamp()

        try {
            val cursor = duaDb.rawQuery(
                "SELECT dua_number, title, surah_reference, arabic, transliteration, translation, explanation FROM quranic_duas ORDER BY dua_number",
                null
            )

            while (cursor.moveToNext()) {
                val duaNumber = cursor.getInt(0)
                val title = cursor.getString(1) ?: ""
                val surahRef = cursor.getString(2)
                val arabic = cursor.getString(3)
                val transliteration = cursor.getString(4)
                val translation = cursor.getString(5)
                val explanation = cursor.getString(6)

                val newsId = startId + duaNumber - 1

                val contentParts = mutableListOf<String>()
                if (!arabic.isNullOrBlank()) contentParts.add("**Arabic:**\n$arabic")
                if (!transliteration.isNullOrBlank()) contentParts.add("**Transliteration:**\n$transliteration")
                if (!translation.isNullOrBlank()) contentParts.add("**Translation:**\n$translation")
                if (!explanation.isNullOrBlank()) contentParts.add("**Explanation:**\n$explanation")

                val content = contentParts.joinToString("\n\n")
                var fullTitle = "Quranic Dua $duaNumber: $title"
                if (!surahRef.isNullOrBlank()) {
                    fullTitle += " ($surahRef)"
                }

                // Use Masjid Al-Nawabi for all Quranic Duas
                val mosqueImage = "drawable://masjid_al_nawabi"

                newsDb.execSQL(
                    """INSERT INTO news_resources (id, title, content, header_image_url, type, is_system, created_at, updated_at)
                       VALUES (?, ?, ?, ?, 'Dua 🤲', 1, ?, ?)""",
                    arrayOf(newsId, fullTitle, content, mosqueImage, now, now)
                )

                newsDb.execSQL(
                    "INSERT INTO news_topics (news_id, topic_id) VALUES (?, ?)",
                    arrayOf(newsId, TOPIC_QURANIC_DUAS)
                )

                count++
                mappings++
            }

            cursor.close()
        } finally {
            duaDb.close()
        }

        Log.d(TAG, "Generated $count Quranic Duas")
        return Pair(count, mappings)
    }

    private fun generateFortressDuas(context: Context, newsDb: SQLiteDatabase, startId: Int): Pair<Int, Int> {
        Log.d(TAG, "Generating Fortress of the Muslim duas...")

        val fortressDbPath = getAssetDbPath(context, "fortress_of_the_muslim.db") ?: return Pair(0, 0)
        val fortressDb = SQLiteDatabase.openDatabase(fortressDbPath, null, SQLiteDatabase.OPEN_READONLY)

        var count = 0
        var mappings = 0
        var newsId = startId
        val now = getCurrentTimestamp()

        try {
            val cursor = fortressDb.rawQuery(
                """SELECT c.id, c.title, i.id, i.position, i.arabic, i.transliteration,
                   i.translation, i.context, i.instruction, i.note, i.post_context
                   FROM chapters c
                   JOIN invocations i ON c.id = i.chapter_id
                   ORDER BY c.id, i.position""",
                null
            )

            while (cursor.moveToNext()) {
                val chapterId = cursor.getInt(0)
                val chapterTitle = cursor.getString(1) ?: ""
                // val invId = cursor.getInt(2)
                val position = cursor.getInt(3)
                val arabic = cursor.getString(4)
                val transliteration = cursor.getString(5)
                val translation = cursor.getString(6)
                val contextText = cursor.getString(7)
                val instruction = cursor.getString(8)
                val note = cursor.getString(9)
                val postContext = cursor.getString(10)

                val contentParts = mutableListOf<String>()
                if (!contextText.isNullOrBlank()) contentParts.add("**Context:**\n$contextText")
                if (!arabic.isNullOrBlank()) contentParts.add("**Arabic:**\n$arabic")
                if (!transliteration.isNullOrBlank()) contentParts.add("**Transliteration:**\n$transliteration")
                if (!translation.isNullOrBlank()) contentParts.add("**Translation:**\n$translation")
                if (!instruction.isNullOrBlank()) contentParts.add("**Instruction:**\n$instruction")
                if (!note.isNullOrBlank()) contentParts.add("**Note:**\n$note")
                if (!postContext.isNullOrBlank()) contentParts.add("**Additional Context:**\n$postContext")

                val content = contentParts.joinToString("\n\n")
                val title = "$chapterTitle: Dua $position"

                // Use Masjid Al-Nawabi for all Fortress of the Muslim duas
                val mosqueImage = "drawable://masjid_al_nawabi"

                newsDb.execSQL(
                    """INSERT INTO news_resources (id, title, content, header_image_url, type, is_system, created_at, updated_at)
                       VALUES (?, ?, ?, ?, 'Dua 🤲', 1, ?, ?)""",
                    arrayOf(newsId, title, content, mosqueImage, now, now)
                )

                // Map to topic based on chapter
                val topicId = CHAPTER_TO_TOPIC[chapterId]
                if (topicId != null) {
                    newsDb.execSQL(
                        "INSERT INTO news_topics (news_id, topic_id) VALUES (?, ?)",
                        arrayOf(newsId, topicId)
                    )
                    mappings++
                }

                newsId++
                count++
            }

            cursor.close()
        } finally {
            fortressDb.close()
        }

        Log.d(TAG, "Generated $count Fortress duas")
        return Pair(count, mappings)
    }

    private fun generateBukhariHadiths(context: Context, newsDb: SQLiteDatabase, startId: Int): Pair<Int, Int> {
        Log.d(TAG, "Generating Sahih Bukhari hadiths from JSON...")

        var count = 0
        var mappings = 0
        var newsId = startId
        val now = getCurrentTimestamp()

        try {
            // Check CDN-downloaded file first, then fall back to bundled asset
            val cdnJsonFile = File(context.filesDir, "cdn_assets/json/sahih_bukhari.json")
            val jsonString = if (cdnJsonFile.exists() && cdnJsonFile.length() > 0) {
                Log.d(TAG, "Using CDN-downloaded sahih_bukhari.json (${cdnJsonFile.length()} bytes)")
                cdnJsonFile.readText()
            } else {
                context.assets.open("sahih_bukhari.json").bufferedReader().use { it.readText() }
            }
            val jsonArray = org.json.JSONArray(jsonString)

            for (volIndex in 0 until jsonArray.length()) {
                val volume = jsonArray.getJSONObject(volIndex)
                val books = volume.getJSONArray("books")

                for (bookIndex in 0 until books.length()) {
                    val book = books.getJSONObject(bookIndex)
                    val bookName = book.getString("name")
                    val hadiths = book.getJSONArray("hadiths")

                    for (hadithIndex in 0 until hadiths.length()) {
                        val hadith = hadiths.getJSONObject(hadithIndex)
                        val info = hadith.getString("info")
                        val narrator = hadith.optString("by", "")
                        val text = hadith.getString("text").trim()

                        // Extract hadith number
                        val numberMatch = Regex("""Number\s*(\d+)""", RegexOption.IGNORE_CASE).find(info)
                        val hadithNumber = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue

                        val title = "Hadith $hadithNumber - $bookName"
                        val content = if (narrator.isNotEmpty()) {
                            "$narrator\n\n$text"
                        } else {
                            text
                        }
                        val url = "hadith://sahih_bukhari/$hadithNumber"

                        newsDb.execSQL(
                            """INSERT INTO news_resources (id, title, content, url, header_image_url, publish_date, type, is_system, is_user_created, source, created_at, updated_at)
                               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                            arrayOf(newsId, title, content, url, "drawable://masjid_al_nawabi", now, "Hadith 📖", 1, 0, "Sahih Bukhari", now, now)
                        )

                        newsDb.execSQL(
                            "INSERT INTO news_topics (news_id, topic_id) VALUES (?, ?)",
                            arrayOf(newsId, TOPIC_SAHIH_BUKHARI)
                        )

                        newsId++
                        count++
                        mappings++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Bukhari hadiths", e)
        }

        Log.d(TAG, "Generated $count Bukhari hadiths")
        return Pair(count, mappings)
    }

    /**
     * Copy asset database to a readable location and return the path.
     * Checks CDN-downloaded files first, then falls back to bundled APK assets.
     */
    private fun getAssetDbPath(context: Context, dbName: String): String? {
        // Check CDN-downloaded files in multiple locations
        val cdnPaths = listOf(
            "databases/quran/$dbName",  // For quran.db, quran_enhanced.db
            "databases/$dbName"          // For quranic_duas.db, fortress_of_the_muslim.db
        )

        for (cdnKey in cdnPaths) {
            val cdnFile = File(context.filesDir, "cdn_assets/$cdnKey")
            if (cdnFile.exists() && cdnFile.length() > 0) {
                Log.d(TAG, "Using CDN-downloaded database: $dbName from $cdnKey (${cdnFile.length()} bytes)")
                return cdnFile.absolutePath
            }
        }

        // Fall back to bundled APK asset
        return try {
            val cacheDir = File(context.cacheDir, "temp_dbs")
            cacheDir.mkdirs()

            val outFile = File(cacheDir, dbName)

            // Always copy fresh from assets
            context.assets.open("databases/$dbName").use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Database not available (CDN or bundled): $dbName", e)
            null
        }
    }

    private fun getOrdinal(n: Int): String {
        val suffix = when {
            n in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
    }

    /**
     * Regenerate news database using Room DAO
     * This works with the existing singleton database connection
     */
    suspend fun regenerateWithRoom(context: Context, dao: NewsDao): RegenerationResult {
        Log.d(TAG, "Starting news.db regeneration with Room...")
        val startTime = System.currentTimeMillis()

        return try {
            // Clear existing data
            dao.deleteAllNewsTopics()
            dao.deleteAllNewsResources()
            Log.d(TAG, "Cleared existing news data")

            val now = getCurrentTimestamp()
            val newsResources = mutableListOf<NewsResourceEntity>()
            val crossRefs = mutableListOf<NewsTopicCrossRef>()

            var surahCount = 0
            var quranicDuaCount = 0
            var fortressCount = 0

            // Generate Surahs
            val quranDbPath = getAssetDbPath(context, "quran.db")
            if (quranDbPath != null) {
                val quranDb = SQLiteDatabase.openDatabase(quranDbPath, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    val cursor = quranDb.rawQuery(
                        "SELECT number, name_en, name_en_translation, name_ar, type, total_verses FROM surahs ORDER BY number",
                        null
                    )
                    while (cursor.moveToNext()) {
                        val number = cursor.getInt(0)
                        val nameEn = cursor.getString(1) ?: ""
                        val nameTranslation = cursor.getString(2) ?: ""
                        val nameAr = cursor.getString(3) ?: ""
                        val type = cursor.getString(4) ?: "Meccan"
                        val totalVerses = cursor.getInt(5)

                        val newsId = 2001 + number - 1
                        val ordinal = getOrdinal(number)

                        // Set mosque image based on revelation type
                        val mosqueImage = when (type) {
                            "Meccan" -> "drawable://masjid_al_haram"
                            "Medinan" -> "drawable://masjid_al_nawabi"
                            else -> "drawable://masjid_al_haram" // Default to Makkah
                        }

                        // Concatenate from ayah 2 onward so the single-line preview fills the
                        // card width (the Text uses maxLines=1 + ellipsis). Ayah 1 is Bismillah
                        // for almost every surah in this DB, so it's skipped. Falls back to ayah
                        // 1 only when there is no second ayah.
                        val firstAyah: String? = quranDb.rawQuery(
                            "SELECT text FROM ayahs WHERE surah_number = ? AND number_in_surah >= 2 ORDER BY number_in_surah ASC LIMIT 6",
                            arrayOf(number.toString())
                        ).use { ac ->
                            val parts = mutableListOf<String>()
                            while (ac.moveToNext()) {
                                ac.getString(0)?.takeIf { it.isNotBlank() }?.let { parts.add(it.trim()) }
                            }
                            parts.joinToString(separator = " ").takeIf { it.isNotBlank() }
                        } ?: quranDb.rawQuery(
                            "SELECT text FROM ayahs WHERE surah_number = ? ORDER BY number_in_surah ASC LIMIT 1",
                            arrayOf(number.toString())
                        ).use { ac ->
                            if (ac.moveToFirst()) ac.getString(0)?.takeIf { it.isNotBlank() } else null
                        }

                        val title = "Surah $number: $nameEn ($nameTranslation)"
                        val content = buildString {
                            append("**Arabic:** $nameAr\n\n")
                            if (firstAyah != null) {
                                append("**FirstAyah:** $firstAyah\n\n")
                            }
                            append("**Type:** $type\n\n")
                            append("**Verses:** $totalVerses\n\n")
                            append("Read and listen to Surah $nameEn, the $nameTranslation. This is the $ordinal chapter of the Holy Quran with $totalVerses verses.")
                        }

                        newsResources.add(NewsResourceEntity(
                            id = newsId,
                            title = title,
                            content = content,
                            url = "",
                            headerImageUrl = mosqueImage,
                            publishDate = now,
                            type = "Surah 📖",
                            isSystem = 1,
                            isUserCreated = 0,
                            source = null,
                            createdAt = now,
                            updatedAt = now
                        ))
                        crossRefs.add(NewsTopicCrossRef(newsId, TOPIC_HOLY_QURAN))
                        surahCount++
                    }
                    cursor.close()
                } finally {
                    quranDb.close()
                }
                Log.d(TAG, "Generated $surahCount Surahs")
            }

            // Generate Quranic Duas
            val duaDbPath = getAssetDbPath(context, "quranic_duas.db")
            if (duaDbPath != null) {
                val duaDb = SQLiteDatabase.openDatabase(duaDbPath, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    val cursor = duaDb.rawQuery(
                        "SELECT dua_number, title, surah_reference, arabic, transliteration, translation, explanation FROM quranic_duas ORDER BY dua_number",
                        null
                    )
                    while (cursor.moveToNext()) {
                        val duaNumber = cursor.getInt(0)
                        val title = cursor.getString(1) ?: ""
                        val surahRef = cursor.getString(2)
                        val arabic = cursor.getString(3)
                        val transliteration = cursor.getString(4)
                        val translation = cursor.getString(5)
                        val explanation = cursor.getString(6)

                        val newsId = 101 + duaNumber - 1

                        val contentParts = mutableListOf<String>()
                        if (!arabic.isNullOrBlank()) contentParts.add("**Arabic:**\n$arabic")
                        if (!transliteration.isNullOrBlank()) contentParts.add("**Transliteration:**\n$transliteration")
                        if (!translation.isNullOrBlank()) contentParts.add("**Translation:**\n$translation")
                        if (!explanation.isNullOrBlank()) contentParts.add("**Explanation:**\n$explanation")

                        val content = contentParts.joinToString("\n\n")
                        var fullTitle = "Quranic Dua $duaNumber: $title"
                        if (!surahRef.isNullOrBlank()) {
                            fullTitle += " ($surahRef)"
                        }

                        // Use Masjid Al-Nawabi for all Quranic Duas
                        val mosqueImage = "drawable://masjid_al_nawabi"

                        newsResources.add(NewsResourceEntity(
                            id = newsId,
                            title = fullTitle,
                            content = content,
                            url = "",
                            headerImageUrl = mosqueImage,
                            publishDate = now,
                            type = "Dua 🤲",
                            isSystem = 1,
                            isUserCreated = 0,
                            source = null,
                            createdAt = now,
                            updatedAt = now
                        ))
                        crossRefs.add(NewsTopicCrossRef(newsId, TOPIC_QURANIC_DUAS))
                        quranicDuaCount++
                    }
                    cursor.close()
                } finally {
                    duaDb.close()
                }
                Log.d(TAG, "Generated $quranicDuaCount Quranic Duas")
            }

            // Generate Fortress Duas from the v2 database (clean text + references in
            // the hadith_references table instead of the `note` field).
            val fortressDbPath = getAssetDbPath(context, "fortress_of_the_muslim_v2.db")
            if (fortressDbPath != null) {
                val fortressDb = SQLiteDatabase.openDatabase(fortressDbPath, null, SQLiteDatabase.OPEN_READONLY)
                var newsId = 1001
                try {
                    val cursor = fortressDb.rawQuery(
                        """SELECT c.id, c.title, i.id, i.position, i.arabic, i.transliteration,
                           i.translation, i.context, i.instruction, i.note, i.post_context,
                           (SELECT h.reference_str FROM hadith_references h
                              WHERE h.invocation_id = i.id LIMIT 1) AS reference,
                           c.audio_url
                           FROM chapters c
                           JOIN invocations i ON c.id = i.chapter_id
                           ORDER BY c.id, i.position""",
                        null
                    )
                    while (cursor.moveToNext()) {
                        val chapterId = cursor.getInt(0)
                        val chapterTitle = cursor.getString(1) ?: ""
                        val position = cursor.getInt(3)
                        val arabic = cursor.getString(4)
                        val transliteration = cursor.getString(5)
                        val translation = cursor.getString(6)
                        val contextText = cursor.getString(7)
                        val instruction = cursor.getString(8)
                        val note = cursor.getString(9)
                        val postContext = cursor.getString(10)
                        val reference = cursor.getString(11)
                        val audioUrl = cursor.getString(12)

                        val contentParts = mutableListOf<String>()
                        if (!contextText.isNullOrBlank()) contentParts.add("**Context:**\n$contextText")
                        if (!arabic.isNullOrBlank()) contentParts.add("**Arabic:**\n$arabic")
                        if (!transliteration.isNullOrBlank()) contentParts.add("**Transliteration:**\n$transliteration")
                        if (!translation.isNullOrBlank()) contentParts.add("**Translation:**\n$translation")
                        if (!instruction.isNullOrBlank()) contentParts.add("**Instruction:**\n$instruction")
                        if (!note.isNullOrBlank()) contentParts.add("**Note:**\n$note")
                        if (!reference.isNullOrBlank()) contentParts.add("**Reference:**\n$reference")
                        if (!postContext.isNullOrBlank()) contentParts.add("**Additional Context:**\n$postContext")
                        // Hidden marker consumed by the news card to show a play button;
                        // parseDuaContent ignores unknown **sections** so the detail view is unaffected.
                        if (!audioUrl.isNullOrBlank()) contentParts.add("**Audio:**\n$audioUrl")

                        val content = contentParts.joinToString("\n\n")
                        val title = "$chapterTitle: Dua $position"

                        // Use Masjid Al-Nawabi for all Fortress of the Muslim duas
                        val mosqueImage = "drawable://masjid_al_nawabi"

                        newsResources.add(NewsResourceEntity(
                            id = newsId,
                            title = title,
                            content = content,
                            url = "",
                            headerImageUrl = mosqueImage,
                            publishDate = now,
                            type = "Dua 🤲",
                            isSystem = 1,
                            isUserCreated = 0,
                            source = null,
                            createdAt = now,
                            updatedAt = now
                        ))

                        val topicId = CHAPTER_TO_TOPIC[chapterId]
                        if (topicId != null) {
                            crossRefs.add(NewsTopicCrossRef(newsId, topicId))
                        }

                        newsId++
                        fortressCount++
                    }
                    cursor.close()
                } finally {
                    fortressDb.close()
                }
                Log.d(TAG, "Generated $fortressCount Fortress duas")
            }

            // Generate Bukhari Hadiths from JSON
            var bukhariCount = 0
            try {
                // Check CDN-downloaded file first, then fall back to bundled asset
                val cdnJsonFile = File(context.filesDir, "cdn_assets/json/sahih_bukhari.json")
                val jsonString = if (cdnJsonFile.exists() && cdnJsonFile.length() > 0) {
                    Log.d(TAG, "Using CDN-downloaded sahih_bukhari.json (${cdnJsonFile.length()} bytes)")
                    cdnJsonFile.readText()
                } else {
                    context.assets.open("sahih_bukhari.json").bufferedReader().use { it.readText() }
                }
                val jsonArray = org.json.JSONArray(jsonString)
                var newsId = 3001

                for (volIndex in 0 until jsonArray.length()) {
                    val volume = jsonArray.getJSONObject(volIndex)
                    val books = volume.getJSONArray("books")

                    for (bookIndex in 0 until books.length()) {
                        val book = books.getJSONObject(bookIndex)
                        val bookName = book.getString("name")
                        val hadiths = book.getJSONArray("hadiths")

                        for (hadithIndex in 0 until hadiths.length()) {
                            val hadith = hadiths.getJSONObject(hadithIndex)
                            val info = hadith.getString("info")
                            val narrator = hadith.optString("by", "")
                            val text = hadith.getString("text").trim()

                            // Extract hadith number
                            val numberMatch = Regex("""Number\s*(\d+)""", RegexOption.IGNORE_CASE).find(info)
                            val hadithNumber = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue

                            val title = "Hadith $hadithNumber - $bookName"
                            val content = if (narrator.isNotEmpty()) {
                                "$narrator\n\n$text"
                            } else {
                                text
                            }
                            val url = "hadith://sahih_bukhari/$hadithNumber"

                            newsResources.add(NewsResourceEntity(
                                id = newsId,
                                title = title,
                                content = content,
                                url = url,
                                headerImageUrl = "drawable://masjid_al_nawabi",
                                publishDate = now,
                                type = "Hadith 📖",
                                isSystem = 1,
                                isUserCreated = 0,
                                source = "Sahih Bukhari",
                                createdAt = now,
                                updatedAt = now
                            ))
                            crossRefs.add(NewsTopicCrossRef(newsId, TOPIC_SAHIH_BUKHARI))
                            newsId++
                            bukhariCount++
                        }
                    }
                }
                Log.d(TAG, "Generated $bukhariCount Bukhari hadiths")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate Bukhari hadiths", e)
            }

            // Insert all data via Room
            dao.insertNewsResources(newsResources)
            dao.insertNewsTopicCrossRefs(crossRefs)

            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "News.db regeneration completed in ${duration}ms")
            Log.d(TAG, "Generated: $surahCount Surahs, $quranicDuaCount Quranic Duas, $fortressCount Fortress Duas, $bukhariCount Bukhari Hadiths")
            Log.d(TAG, "Total topic mappings: ${crossRefs.size}")

            RegenerationResult(
                success = true,
                surahCount = surahCount,
                quranicDuaCount = quranicDuaCount,
                fortressDuaCount = fortressCount,
                bukhariHadithCount = bukhariCount,
                topicMappings = crossRefs.size,
                durationMs = duration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to regenerate news.db with Room", e)
            RegenerationResult(
                success = false,
                error = e.message
            )
        }
    }
}

/**
 * Result of news.db regeneration
 */
data class RegenerationResult(
    val success: Boolean,
    val surahCount: Int = 0,
    val quranicDuaCount: Int = 0,
    val fortressDuaCount: Int = 0,
    val bukhariHadithCount: Int = 0,
    val topicMappings: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null
) {
    val totalNewsResources: Int
        get() = surahCount + quranicDuaCount + fortressDuaCount + bukhariHadithCount
}
