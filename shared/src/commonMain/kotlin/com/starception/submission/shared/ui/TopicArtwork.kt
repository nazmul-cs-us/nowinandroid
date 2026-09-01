/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Uses the same bundled artwork files as Android's Interests and topic screens. */
@Composable
internal expect fun TopicArtwork(
    topicName: String,
    modifier: Modifier = Modifier,
)

/** Uses the same bundled mosque artwork as Android's expanded news cards. */
@Composable
internal expect fun NewsHeaderArtwork(
    resourceName: String,
    modifier: Modifier = Modifier,
)

/** Android's Flaticon location marker, tinted by the active Material theme. */
@Composable
internal expect fun LocationMarkerArtwork(
    tint: Color,
    modifier: Modifier = Modifier,
)

internal fun topicArtworkResourceName(topicName: String): String {
    val normalized = topicName.lowercase()
    return when {
        "bukhari" in normalized || "hadith" in normalized -> "topic_sahih_bukhari"
        "dua" in normalized -> "topic_quranic_duas"
        "quran" in normalized || "surah" in normalized -> "topic_holy_quran"
        "morning" in normalized || "evening" in normalized -> "topic_morning_evening"
        "prayer" in normalized -> "topic_prayer"
        "home" in normalized -> "topic_home_daily"
        "food" in normalized || "drink" in normalized -> "topic_food_drink"
        "travel" in normalized -> "topic_travel"
        "protection" in normalized -> "topic_protection"
        "distress" in normalized || "anxiety" in normalized -> "topic_distress_anxiety"
        "health" in normalized || "sick" in normalized -> "topic_health_sickness"
        "social" in normalized || "etiquette" in normalized -> "topic_social_etiquette"
        "death" in normalized || "funeral" in normalized -> "topic_death_funeral"
        "weather" in normalized || "nature" in normalized -> "topic_weather_nature"
        "hajj" in normalized || "umrah" in normalized -> "topic_hajj_umrah"
        "forgive" in normalized || "repent" in normalized -> "topic_forgiveness_repentance"
        "guidance" in normalized || "faith" in normalized -> "topic_guidance_faith"
        "remembrance" in normalized || "dhikr" in normalized -> "topic_remembrance_dhikr"
        "family" in normalized || "marriage" in normalized -> "topic_family_marriage"
        "sacrifice" in normalized || "worship" in normalized -> "topic_sacrifice_worship"
        else -> "topic_holy_quran"
    }
}
