package com.starception.submission.family

/** Receipt timestamps are written by the server after recipient acknowledgements. */
class FamilyMessageReceiptPolicy {
    fun status(deliveredAt: Long, seenAt: Long): String = when {
        seenAt > 0 -> "seen"
        deliveredAt > 0 -> "delivered"
        else -> "sent"
    }

    fun label(deliveredAt: Long, seenAt: Long): String = when (status(deliveredAt, seenAt)) {
        "seen" -> "Seen"
        "delivered" -> "Delivered"
        else -> "Sent"
    }

    /** Scale-independent visibility; very long messages need not fit entirely on screen. */
    fun visible(top: Float, height: Float, viewportStart: Float, viewportEnd: Float): Boolean {
        if (height <= 0 || viewportEnd <= viewportStart) return false
        val overlap = minOf(top + height, viewportEnd) - maxOf(top, viewportStart)
        return overlap > 0 && overlap >= minOf(height, viewportEnd - viewportStart) * 0.5f
    }
}
