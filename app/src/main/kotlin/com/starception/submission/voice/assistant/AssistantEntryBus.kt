package com.starception.submission.voice.assistant

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AssistantEntryRequest(val id: Long, val routed: Boolean = false)

/** A cold-start request must survive until both navigation and the mic handler exist. */
internal object AssistantEntryBus {
    private val mutable = MutableStateFlow<AssistantEntryRequest?>(null)
    val pending = mutable.asStateFlow()
    private var sequence = 0L

    @Synchronized fun accept(intent: Intent?) {
        if (intent?.action != Intent.ACTION_ASSIST) return
        intent.action = null // No repeat capture on a theme change or activity recreation.
        mutable.value = AssistantEntryRequest(++sequence)
    }

    fun routed(request: AssistantEntryRequest) { mutable.compareAndSet(request, request.copy(routed = true)) }
    fun consumed(request: AssistantEntryRequest): Boolean = mutable.compareAndSet(request, null)
}
