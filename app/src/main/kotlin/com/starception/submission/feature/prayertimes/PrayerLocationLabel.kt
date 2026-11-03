package com.starception.submission.feature.prayertimes

import com.starception.submission.prayer.model.Location

/** Shared by the location tile and Home's compact rotating metadata. */
internal data class PrayerLocationLabel(val title: String, val detail: String) {
    val singleLine: String
        get() = listOf(detail, title).filter(String::isNotBlank)
            .distinctBy { it.lowercase() }.joinToString(" · ")
}

internal fun prayerLocationLabel(location: Location?, fallback: () -> String): PrayerLocationLabel {
    val title = location?.city?.trim().orEmpty().ifBlank(fallback)
    val area = location?.area?.trim()?.takeIf(String::isNotBlank)
        ?: location?.subLocality?.trim()?.takeIf(String::isNotBlank)
    val detail = area?.takeUnless { it.equals(title, ignoreCase = true) }
        ?: location?.administrativeArea?.trim()?.takeUnless { it.isBlank() || it.equals(title, ignoreCase = true) }
        ?: location?.country?.trim().orEmpty()
    val countryCode = location?.countryCode?.trim()?.uppercase()?.takeIf { it.length in 2..3 }.orEmpty()
    return PrayerLocationLabel(title, detail.ifBlank { countryCode })
}
