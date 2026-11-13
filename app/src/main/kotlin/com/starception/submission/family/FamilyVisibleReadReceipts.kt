package com.starception.submission.family

import kotlinx.coroutines.delay

/** Called inside repeatOnLifecycle; leaving the visible chat cancels retries. */
internal suspend fun acknowledgeVisibleChat(
    ids: List<String>,
    me: String,
    messages: () -> List<FamilyChatMessage>,
    acknowledge: (List<String>) -> Unit,
) {
    if (ids.isEmpty()) return
    acknowledge(ids)
    while (true) {
        delay(3_000)
        val pending = messages().any { it.id in ids && it.to == me && it.call == null && it.seenAt == 0L }
        if (!pending) return
        acknowledge(ids)
    }
}
