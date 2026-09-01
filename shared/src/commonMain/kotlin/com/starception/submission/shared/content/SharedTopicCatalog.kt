/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.content

/** A topic shown by Android's Interests destination and its shared iOS counterpart. */
data class SharedTopic(
    val id: Int,
    val name: String,
    val shortDescription: String,
    val longDescription: String,
)

/** A readable item generated from the same source databases as Android news. */
data class SharedTopicArticle(
    val id: Int,
    val topicId: Int,
    val title: String,
    val arabic: String,
    val translation: String,
    val transliteration: String = "",
    val context: String = "",
    val instruction: String = "",
    val additionalContext: String = "",
    val reference: String = "",
)

interface SharedTopicRepository {
    suspend fun topics(): List<SharedTopic>
    suspend fun articles(topicId: Int): List<SharedTopicArticle>
}

expect fun createSharedTopicRepository(): SharedTopicRepository

/**
 * In-memory fallback for previews, tests, and an unavailable platform database.
 *
 * The values mirror core/contentdatabase's tracked topics.db. Runtime iOS reads
 * that database directly, so Android and iOS use the same catalog source.
 */
val SharedTopics = listOf(
    SharedTopic(7, "Holy Quran", "The final revelation from Allah", "Read all 114 chapters of the Holy Quran."),
    SharedTopic(8, "Sahih Bukhari", "صحيح البخاري", "Browse the complete Sahih al-Bukhari collection by book."),
    SharedTopic(11, "Quranic Duas", "Remembrance of Allah and supplications", "Supplications collected directly from the Holy Quran."),
    SharedTopic(21, "Morning & Evening", "أذكار الصباح والمساء", "Fortress of the Muslim invocations for morning and evening."),
    SharedTopic(22, "Prayer", "الصلاة", "Fortress of the Muslim invocations connected with salah."),
    SharedTopic(23, "Home & Daily", "المنزل واليومية", "Everyday invocations for the home and daily routines."),
    SharedTopic(24, "Food & Drink", "الطعام والشراب", "Invocations for eating, drinking, and hospitality."),
    SharedTopic(25, "Travel", "السفر", "Invocations for journeys, transport, and returning home."),
    SharedTopic(26, "Protection", "الحماية", "Supplications seeking Allah's protection."),
    SharedTopic(27, "Distress & Anxiety", "الكرب والقلق", "Supplications for distress, worry, grief, and hardship."),
    SharedTopic(28, "Health & Sickness", "الصحة والمرض", "Invocations for illness, healing, and visiting the sick."),
    SharedTopic(29, "Social & Etiquette", "الاجتماعية والآداب", "Invocations and manners for meeting and living with others."),
    SharedTopic(30, "Death & Funeral", "الموت والجنازة", "Invocations concerning death, funerals, and graves."),
    SharedTopic(31, "Weather & Nature", "الطقس والطبيعة", "Invocations for rain, wind, thunder, and the natural world."),
    SharedTopic(32, "Hajj & Umrah", "الحج والعمرة", "Invocations for pilgrimage and visiting the sacred places."),
    SharedTopic(33, "Forgiveness & Repentance", "الاستغفار والتوبة", "Supplications asking Allah for forgiveness and accepting repentance."),
    SharedTopic(34, "Guidance & Faith", "الهداية والإيمان", "Supplications for guidance, steadfastness, and sound faith."),
    SharedTopic(35, "Remembrance & Dhikr", "الذكر والأذكار", "Words of remembrance and praise of Allah."),
    SharedTopic(36, "Family & Marriage", "الأسرة والزواج", "Invocations for family life, children, and marriage."),
    SharedTopic(37, "Sacrifice & Worship", "الذبيحة والعبادة", "Invocations connected with worship and sacrifice."),
)

fun sharedTopic(id: Int): SharedTopic? = SharedTopics.firstOrNull { it.id == id }

internal fun canonicalInterestKey(value: String): String {
    val normalized = value.trim()
    return SharedTopics.firstOrNull {
        it.id.toString() == normalized || it.name.equals(normalized, ignoreCase = true) ||
            (normalized.equals("Quran", ignoreCase = true) && it.id == 7) ||
            (normalized.equals("Hadith", ignoreCase = true) && it.id == 8) ||
            (normalized.equals("Dua and remembrance", ignoreCase = true) && it.id == 11) ||
            (normalized.equals("Character", ignoreCase = true) && it.id == 29) ||
            (normalized.equals("Family", ignoreCase = true) && it.id == 36) ||
            (normalized.equals("Learning", ignoreCase = true) && it.id == 34)
    }?.id?.toString() ?: normalized
}

/** Fortress chapter grouping used by Android's NewsDbGenerator. */
internal val fortressChaptersByTopic = mapOf(
    21 to listOf(27, 28, 29, 30, 31),
    22 to ((12..25).toList() + listOf(32, 33)),
    23 to (1..11).toList(),
    24 to (69..73).toList(),
    25 to (95..105).toList(),
    26 to listOf(36, 37, 38, 39, 45, 88, 125, 128),
    27 to listOf(34, 35, 41, 43, 46, 82, 83, 106, 126),
    28 to listOf(49, 50, 51, 124),
    29 to ((77..87).toList() + (107..114).toList()),
    30 to (52..60).toList(),
    31 to ((61..67).toList() + 76),
    32 to (115..121).toList(),
    33 to listOf(44, 129),
    34 to listOf(26, 40, 42),
    35 to listOf(130, 131, 132),
    36 to listOf(47, 48, 79, 80, 81),
    37 to ((68..68).toList() + (74..75).toList() + (89..94).toList() + (122..123).toList() + 127),
)
