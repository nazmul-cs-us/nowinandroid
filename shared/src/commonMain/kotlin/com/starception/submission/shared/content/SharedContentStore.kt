/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.shared.content

import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore

data class LocalProfile(
    val displayName: String = "Reader",
    val dailyReadingGoalMinutes: Int = 10,
)

/** Small, local-first state used by the shared iOS screens. */
class SharedContentStore(private val store: KeyValueStore = platformKeyValueStore()) {
    fun profile(): LocalProfile = LocalProfile(
        displayName = store.getString(PROFILE_NAME)?.takeIf(String::isNotBlank) ?: "Reader",
        dailyReadingGoalMinutes = store.getString(READING_GOAL)?.toIntOrNull()
            ?.coerceIn(5, 60) ?: 10,
    )

    fun saveProfile(profile: LocalProfile) {
        store.putString(PROFILE_NAME, profile.displayName.trim().ifEmpty { "Reader" })
        store.putString(READING_GOAL, profile.dailyReadingGoalMinutes.coerceIn(5, 60).toString())
    }

    fun bookmarkedSurahs(): Set<Int> = intSet(SAVED_SURAHS, 1..114)

    fun toggleSurah(number: Int): Set<Int> = toggle(SAVED_SURAHS, number, 1..114)

    fun savedBukhariBooks(): Set<Int> = intSet(SAVED_BUKHARI, 1..97)

    fun toggleBukhariBook(id: Int): Set<Int> = toggle(SAVED_BUKHARI, id, 1..97)

    fun bookmarkedTopicArticles(): Set<String> = stringSet(SAVED_TOPIC_ARTICLES)

    fun toggleTopicArticle(topicId: Int, articleId: Int): Set<String> {
        val itemKey = "$topicId:$articleId"
        val updated = bookmarkedTopicArticles().toMutableSet().apply {
            if (!add(itemKey)) remove(itemKey)
        }
        store.putString(SAVED_TOPIC_ARTICLES, updated.sorted().joinToString(SEPARATOR))
        return updated
    }

    fun interests(): Set<String> {
        val stored = stringSet(INTERESTS)
        val canonical = stored.mapTo(mutableSetOf(), ::canonicalInterestKey)
        if (canonical != stored) persistInterests(canonical)
        return canonical
    }

    fun toggleInterest(interest: String): Set<String> {
        val key = canonicalInterestKey(interest)
        val updated = interests().toMutableSet().apply {
            if (!add(key)) remove(key)
        }
        persistInterests(updated)
        return updated
    }

    fun completedLessons(): Set<Int> = intSet(COMPLETED_LESSONS, 1..COURSE_LESSON_COUNT)

    fun toggleLesson(number: Int): Set<Int> =
        toggle(COMPLETED_LESSONS, number, 1..COURSE_LESSON_COUNT)

    private fun toggle(key: String, value: Int, validRange: IntRange): Set<Int> {
        if (value !in validRange) return intSet(key, validRange)
        val updated = intSet(key, validRange).toMutableSet().apply {
            if (!add(value)) remove(value)
        }
        store.putString(key, updated.sorted().joinToString(SEPARATOR))
        return updated
    }

    private fun intSet(key: String, validRange: IntRange): Set<Int> =
        store.getString(key).orEmpty().split(SEPARATOR)
            .mapNotNull(String::toIntOrNull)
            .filterTo(mutableSetOf()) { it in validRange }

    private fun stringSet(key: String): Set<String> =
        store.getString(key).orEmpty().split(SEPARATOR)
            .map(String::trim)
            .filterTo(mutableSetOf(), String::isNotEmpty)

    private fun persistInterests(interests: Set<String>) {
        store.putString(INTERESTS, interests.sorted().joinToString(SEPARATOR))
    }

    companion object {
        const val COURSE_LESSON_COUNT = 5
        private const val SEPARATOR = "|"
        private const val PROFILE_NAME = "shared_profile_name"
        private const val READING_GOAL = "shared_reading_goal"
        private const val SAVED_SURAHS = "shared_saved_surahs"
        private const val SAVED_BUKHARI = "shared_saved_bukhari"
        private const val SAVED_TOPIC_ARTICLES = "shared_saved_topic_articles"
        private const val INTERESTS = "shared_interests"
        private const val COMPLETED_LESSONS = "shared_completed_lessons"
    }
}
