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

    fun toggleSurah(number: Int): Set<Int> {
        val updated = toggle(SAVED_SURAHS, number, 1..114)
        if (number in 1..114) setNewsBookmarked(SURAH_NEWS_ID_OFFSET + number, number in updated)
        return updated
    }

    fun savedBukhariBooks(): Set<Int> = intSet(SAVED_BUKHARI, 1..97)

    fun toggleBukhariBook(id: Int): Set<Int> = toggle(SAVED_BUKHARI, id, 1..97)

    fun bookmarkedTopicArticles(): Set<String> = stringSet(SAVED_TOPIC_ARTICLES)

    fun toggleTopicArticle(topicId: Int, articleId: Int): Set<String> {
        val itemKey = "$topicId:$articleId"
        val updated = bookmarkedTopicArticles().toMutableSet().apply {
            if (!add(itemKey)) remove(itemKey)
        }
        store.putString(SAVED_TOPIC_ARTICLES, updated.sorted().joinToString(SEPARATOR))
        legacyTopicArticleNewsId(topicId, articleId)?.let { newsId ->
            setNewsBookmarked(newsId, itemKey in updated)
        }
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

    fun followedTopicIds(): Set<Int> = interests()
        .mapNotNull(String::toIntOrNull)
        .filterTo(mutableSetOf()) { it > 0 }

    fun setFollowedTopicIds(topicIds: Set<Int>): Set<Int> {
        val updated = topicIds.filterTo(mutableSetOf()) { it > 0 }
        persistInterests(updated.mapTo(mutableSetOf(), Int::toString))
        return updated
    }

    fun toggleFollowedTopic(topicId: Int): Set<Int> {
        if (topicId <= 0) return followedTopicIds()
        val updated = followedTopicIds().toMutableSet().apply {
            if (!add(topicId)) remove(topicId)
        }
        return setFollowedTopicIds(updated)
    }

    fun setTopicFollowed(topicId: Int, followed: Boolean): Set<Int> {
        if (topicId <= 0) return followedTopicIds()
        val updated = followedTopicIds().toMutableSet().apply {
            if (followed) add(topicId) else remove(topicId)
        }
        return setFollowedTopicIds(updated)
    }

    fun bookmarkedNewsIds(): Set<Int> {
        migrateLegacyBookmarks()
        return positiveIntSet(BOOKMARKED_NEWS_IDS)
    }

    fun setBookmarkedNewsIds(newsIds: Set<Int>): Set<Int> {
        migrateLegacyBookmarks()
        val updated = newsIds.filterTo(mutableSetOf()) { it > 0 }
        persistIntSet(BOOKMARKED_NEWS_IDS, updated)
        synchronizeSurahBookmarks(updated)
        return updated
    }

    fun toggleNewsBookmark(newsId: Int): Set<Int> {
        if (newsId <= 0) return bookmarkedNewsIds()
        val updated = bookmarkedNewsIds().toMutableSet().apply {
            if (!add(newsId)) remove(newsId)
        }
        persistIntSet(BOOKMARKED_NEWS_IDS, updated)
        synchronizeSurahBookmarks(updated)
        return updated
    }

    fun setNewsBookmarked(newsId: Int, bookmarked: Boolean): Set<Int> {
        if (newsId <= 0) return bookmarkedNewsIds()
        val updated = bookmarkedNewsIds().toMutableSet().apply {
            if (bookmarked) add(newsId) else remove(newsId)
        }
        persistIntSet(BOOKMARKED_NEWS_IDS, updated)
        synchronizeSurahBookmarks(updated)
        return updated
    }

    fun viewedNewsIds(): Set<Int> = positiveIntSet(VIEWED_NEWS_IDS)

    fun markNewsViewed(newsId: Int): Set<Int> {
        if (newsId <= 0) return viewedNewsIds()
        val updated = viewedNewsIds() + newsId
        persistIntSet(VIEWED_NEWS_IDS, updated)
        return updated
    }

    fun onboardingHidden(): Boolean = store.getString(ONBOARDING_HIDDEN).toBoolean()

    fun isOnboardingHidden(): Boolean = onboardingHidden()

    fun setOnboardingHidden(hidden: Boolean) {
        store.putString(ONBOARDING_HIDDEN, hidden.toString())
    }

    fun topicOrder(): List<Int> = store.getString(TOPIC_ORDER).orEmpty()
        .split(SEPARATOR)
        .mapNotNull(String::toIntOrNull)
        .filter { it > 0 }
        .distinct()

    fun setTopicOrder(topicIds: List<Int>): List<Int> {
        val updated = topicIds.filter { it > 0 }.distinct()
        store.putString(TOPIC_ORDER, updated.joinToString(SEPARATOR))
        return updated
    }

    fun saveTopicOrder(topicIds: List<Int>): List<Int> = setTopicOrder(topicIds)

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

    private fun positiveIntSet(key: String): Set<Int> =
        store.getString(key).orEmpty().split(SEPARATOR)
            .mapNotNull(String::toIntOrNull)
            .filterTo(mutableSetOf()) { it > 0 }

    private fun persistIntSet(key: String, values: Set<Int>) {
        store.putString(key, values.sorted().joinToString(SEPARATOR))
    }

    private fun synchronizeSurahBookmarks(newsIds: Set<Int>) {
        val surahs = newsIds.mapNotNullTo(mutableSetOf()) { newsId ->
            (newsId - SURAH_NEWS_ID_OFFSET).takeIf { it in 1..114 }
        }
        persistIntSet(SAVED_SURAHS, surahs)
    }

    private fun migrateLegacyBookmarks() {
        if (store.getString(BOOKMARK_MIGRATION_COMPLETE) == "true") return

        val migrated = positiveIntSet(BOOKMARKED_NEWS_IDS).toMutableSet()
        bookmarkedSurahs().mapTo(migrated) { SURAH_NEWS_ID_OFFSET + it }
        bookmarkedTopicArticles().forEach { savedArticle ->
            val parts = savedArticle.split(':', limit = 2)
            if (parts.size != 2) return@forEach
            val topicId = parts[0].toIntOrNull() ?: return@forEach
            val articleId = parts[1].toIntOrNull() ?: return@forEach
            legacyTopicArticleNewsId(topicId, articleId)?.let(migrated::add)
        }
        persistIntSet(BOOKMARKED_NEWS_IDS, migrated)
        store.putString(BOOKMARK_MIGRATION_COMPLETE, "true")
    }

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
        private const val BOOKMARKED_NEWS_IDS = "shared_bookmarked_news_ids"
        private const val VIEWED_NEWS_IDS = "shared_viewed_news_ids"
        private const val ONBOARDING_HIDDEN = "shared_onboarding_hidden"
        private const val TOPIC_ORDER = "shared_topic_order"
        private const val BOOKMARK_MIGRATION_COMPLETE = "shared_news_bookmark_migration_complete"
        private const val SURAH_NEWS_ID_OFFSET = 2000
    }
}

private fun legacyTopicArticleNewsId(topicId: Int, articleId: Int): Int? = when {
    topicId == 11 && articleId in 128..167 -> articleId - 27
    topicId in fortressChaptersByTopic && articleId in 1..(Int.MAX_VALUE - 1000) -> 1000 + articleId
    else -> null
}
