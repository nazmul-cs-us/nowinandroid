package com.starception.submission.core.model.data

/**
 * A database row is an application identity, NOT a published hadith number.
 * The downloaded collections use different numbering schemes (some are simply
 * array positions). Never use this ID in an external citation or audio lookup.
 * A publisher citation requires a separately verified, content-bound crosswalk.
 */
object HadithSourceIdentity {
    const val CACHE_REVISION = "local-record-v2"

    fun recordLabel(recordId: Int): String = "Local record $recordId"

    fun reference(collection: String, recordId: Int): String =
        "$collection · ${recordLabel(recordId)}"

    /** No recording-to-database crosswalk has been verified for the legacy set. */
    fun verifiedBukhariRecording(recordId: Int): Int? = null
}
