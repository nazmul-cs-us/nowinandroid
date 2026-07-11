package com.starception.submission.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.starception.submission.core.designsystem.R

/**
 * Maps a topic name to its bundled Twemoji vector drawable. Matched by keyword so
 * minor name variations still resolve. Returns null for unknown topics, letting the
 * caller fall back to its remote image / default icon.
 */
@DrawableRes
fun topicIconResFor(name: String): Int? {
    val n = name.lowercase()
    return when {
        "bukhari" in n || "hadith" in n -> R.drawable.topic_sahih_bukhari
        "dua" in n -> R.drawable.topic_quranic_duas
        "quran" in n || "surah" in n -> R.drawable.topic_holy_quran
        "morning" in n || "evening" in n -> R.drawable.topic_morning_evening
        "prayer" in n -> R.drawable.topic_prayer
        "home" in n -> R.drawable.topic_home_daily
        "food" in n || "drink" in n -> R.drawable.topic_food_drink
        "travel" in n -> R.drawable.topic_travel
        "protection" in n -> R.drawable.topic_protection
        "distress" in n || "anxiety" in n -> R.drawable.topic_distress_anxiety
        "health" in n || "sick" in n -> R.drawable.topic_health_sickness
        "social" in n || "etiquette" in n -> R.drawable.topic_social_etiquette
        "death" in n || "funeral" in n -> R.drawable.topic_death_funeral
        "weather" in n || "nature" in n -> R.drawable.topic_weather_nature
        "hajj" in n || "umrah" in n -> R.drawable.topic_hajj_umrah
        "forgive" in n || "repent" in n -> R.drawable.topic_forgiveness_repentance
        "guidance" in n || "faith" in n -> R.drawable.topic_guidance_faith
        "remembrance" in n || "dhikr" in n -> R.drawable.topic_remembrance_dhikr
        "family" in n || "marriage" in n -> R.drawable.topic_family_marriage
        "sacrifice" in n || "worship" in n -> R.drawable.topic_sacrifice_worship
        else -> null
    }
}

/**
 * True for topic icons that are single-color glyphs and must be tinted to the theme
 * (otherwise they vanish against a dark background). Colorful icons return false.
 * Currently none — all topic icons are colorful — but kept for future solid-glyph icons.
 */
@Suppress("UNUSED_PARAMETER")
fun isMonochromeTopicIcon(name: String): Boolean = false
