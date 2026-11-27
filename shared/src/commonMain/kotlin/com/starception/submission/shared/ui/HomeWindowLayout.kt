package com.starception.submission.shared.ui

/** Use the current Home at every iPhone/iPad size, including short Mac and Split View windows. */
internal fun useArtworkHomeLayout(width: Float, height: Float): Boolean = width > 0 && height > 0

internal data class HomeWindowLayout(
    val contentWidth: Float,
    val headerWidth: Float,
    val readerWidth: Float,
    val gutter: Float,
    val prayerColumns: Int,
)

/** Bound text and cards by the actual window, rather than the device's full screen. */
internal fun homeWindowLayout(width: Float, fontScale: Float = 1f): HomeWindowLayout {
    val available = width.coerceAtLeast(0f)
    val gutter = minOf(if (available >= 840f) 32f else 24f, available / 4f)
    val content = (available - 2f * gutter).coerceIn(0f, 1100f)
    val columns = when {
        content / fontScale.coerceAtLeast(1f) >= 900f -> 3
        content / fontScale.coerceAtLeast(1f) >= 300f -> 2
        else -> 1
    }
    return HomeWindowLayout(content, minOf(available - 2f * gutter, 1200f),
        minOf(available, 960f), gutter, columns)
}

internal fun homeAccountGreetingLabel(account: String?, displayName: String): String =
    if (account.isNullOrBlank()) "Sign In" else
        displayName.trim().takeUnless { it.isBlank() || it == "Reader" }
            ?: account.substringBefore('@').replace('.', ' ').trim()
