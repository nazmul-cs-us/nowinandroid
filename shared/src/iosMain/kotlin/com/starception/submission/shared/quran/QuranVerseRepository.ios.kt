package com.starception.submission.shared.quran

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import sqlite3.SQLITE_DONE
import sqlite3.SQLITE_OK
import sqlite3.SQLITE_OPEN_READONLY
import sqlite3.SQLITE_ROW
import sqlite3.sqlite3_bind_int
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_int
import sqlite3.sqlite3_column_text
import sqlite3.sqlite3_errmsg
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

actual fun createQuranVerseRepository(): QuranVerseRepository = IosQuranVerseRepository()

private class IosQuranVerseRepository : QuranVerseRepository {
    override suspend fun getVersesBySurah(surahNumber: Int): List<QuranVerse> {
        require(surahNumber in 1..114) { "Surah number must be between 1 and 114" }
        return withContext(Dispatchers.Default) { readVerses(surahNumber) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readVerses(surahNumber: Int): List<QuranVerse> = memScoped {
        val databasePath = NSBundle.mainBundle.pathForResource("quran", ofType = "db")
            ?: error("Bundled Quran database was not found")
        val database = alloc<CPointerVar<sqlite3>>()
        val openResult = sqlite3_open_v2(databasePath, database.ptr, SQLITE_OPEN_READONLY, null)
        if (openResult != SQLITE_OK) {
            val message = database.value.errorMessage()
            database.value?.let(::sqlite3_close)
            error("Unable to open Quran database: $message")
        }

        try {
            val statement = alloc<CPointerVar<sqlite3_stmt>>()
            val sql = """
                SELECT id, surah_number, number_in_surah, text, page, juz_id
                FROM ayahs
                WHERE surah_number = ?
                ORDER BY number_in_surah ASC
            """.trimIndent()
            val prepareResult = sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null)
            if (prepareResult != SQLITE_OK) {
                error("Unable to prepare Quran query: ${database.value.errorMessage()}")
            }

            try {
                check(sqlite3_bind_int(statement.value, 1, surahNumber) == SQLITE_OK) {
                    "Unable to bind surah number: ${database.value.errorMessage()}"
                }
                buildList {
                    while (true) {
                        when (sqlite3_step(statement.value)) {
                            SQLITE_ROW -> add(
                                QuranVerse(
                                    id = sqlite3_column_int(statement.value, 0),
                                    surahNumber = sqlite3_column_int(statement.value, 1),
                                    numberInSurah = sqlite3_column_int(statement.value, 2),
                                    arabicText = cleanQuranText(
                                        sqlite3_column_text(statement.value, 3)
                                            ?.reinterpret<ByteVar>()
                                            ?.toKString()
                                            ?: error("Ayah text is missing"),
                                    ),
                                    page = sqlite3_column_int(statement.value, 4),
                                    juz = sqlite3_column_int(statement.value, 5),
                                ),
                            )
                            SQLITE_DONE -> break
                            else -> error("Unable to read Quran ayahs: ${database.value.errorMessage()}")
                        }
                    }
                }
            } finally {
                statement.value?.let(::sqlite3_finalize)
            }
        } finally {
            database.value?.let(::sqlite3_close)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"
