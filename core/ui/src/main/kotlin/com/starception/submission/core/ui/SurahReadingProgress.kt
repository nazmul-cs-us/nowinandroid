/*
 * Copyright 2026 The Starception Submission Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.core.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import org.json.JSONObject

/**
 * Per-Surah reading progress.
 *
 * - [lastAyahIndex]   1-based ayah number that was first visible on screen last time
 *                     the user read this Surah. Use this to resume.
 * - [maxAyahReached]  Furthest ayah the user ever reached (monotonic — never decreases
 *                     even if they jump back to an earlier ayah).
 * - [totalAyahs]      Total ayahs in this Surah, recorded at the time of update.
 * - [lastReadTimestampMillis] Last time progress was updated for this Surah.
 */
data class SurahReadingProgress(
    val surahNumber: Int,
    val lastAyahIndex: Int,
    val maxAyahReached: Int,
    val totalAyahs: Int,
    val lastReadTimestampMillis: Long,
) {
    val isComplete: Boolean get() = totalAyahs > 0 && maxAyahReached >= totalAyahs
    val percent: Float get() = if (totalAyahs > 0) maxAyahReached.toFloat() / totalAyahs else 0f
}

/**
 * Stores Surah reading progress in SharedPreferences (under [PREFS_NAME] / [KEY]).
 *
 * Persists as a single JSON object keyed by surah number. Writes bump a Compose
 * snapshot-state version counter so Composables that call [progressFor] recompose
 * when progress changes.
 *
 * Lives in core/ui (rather than the app module) because the Surah-card badge in
 * NewsResourceCard needs to read it, and that's a core/ui composable.
 */
object SurahReadingProgressRepository {
    private const val PREFS_NAME = "quran_prefs"
    private const val KEY = "surah_progress_json"

    // Compose snapshot state — every successful write bumps this so composables
    // observing it recompose. Reads-from-prefs key off this in `remember`.
    private val version = mutableIntStateOf(0)

    /**
     * Record that the user is currently looking at [currentAyahNumber] of Surah
     * [surahNumber], which has [totalAyahs] total ayahs.
     *
     * Safe to call frequently on scroll — callers should debounce.
     */
    fun update(
        context: Context,
        surahNumber: Int,
        currentAyahNumber: Int,
        totalAyahs: Int,
    ) {
        if (surahNumber <= 0 || currentAyahNumber <= 0 || totalAyahs <= 0) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val root = try {
            prefs.getString(KEY, null)?.let { JSONObject(it) } ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }

        val key = surahNumber.toString()
        val previousMax = root.optJSONObject(key)?.optInt("max", 0) ?: 0
        val newMax = maxOf(previousMax, currentAyahNumber).coerceAtMost(totalAyahs)

        val entry = JSONObject().apply {
            put("last", currentAyahNumber)
            put("max", newMax)
            put("total", totalAyahs)
            put("ts", System.currentTimeMillis())
        }
        root.put(key, entry)

        prefs.edit().putString(KEY, root.toString()).apply()
        version.intValue++
    }

    /**
     * Look up reading progress for [surahNumber]. Returns null if the user has
     * never opened this Surah.
     *
     * Compose-aware: recomposes the caller when any progress entry is written.
     */
    @Composable
    fun progressFor(context: Context, surahNumber: Int): SurahReadingProgress? {
        val v = version.intValue  // subscribe to version for recomposition
        val appContext = context.applicationContext
        return remember(v, surahNumber) {
            if (surahNumber <= 0) return@remember null
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY, null) ?: return@remember null
            try {
                val root = JSONObject(json)
                val entry = root.optJSONObject(surahNumber.toString()) ?: return@remember null
                SurahReadingProgress(
                    surahNumber = surahNumber,
                    lastAyahIndex = entry.optInt("last", 1),
                    maxAyahReached = entry.optInt("max", 1),
                    totalAyahs = entry.optInt("total", 0),
                    lastReadTimestampMillis = entry.optLong("ts", 0L),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
