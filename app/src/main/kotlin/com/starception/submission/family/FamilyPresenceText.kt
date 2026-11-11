package com.starception.submission.family

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun rememberFamilyPresenceTime(): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { value = System.currentTimeMillis(); delay(30000) }
        }
    }.value
}

internal fun familyPresenceText(member: FamilyMember?, available: Boolean, now: Long, compact: Boolean = true): String =
    if (compact) FamilyLastSeenPolicy().shortLabel(member?.presence.orEmpty(), member?.lastSeenAt ?: 0, now, available)
    else FamilyLastSeenPolicy().label(member?.presence.orEmpty(), member?.lastSeenAt ?: 0, now, available)
