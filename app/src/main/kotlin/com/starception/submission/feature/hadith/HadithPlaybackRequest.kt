package com.starception.submission.feature.hadith

/** A pending render request must not borrow identity from a later reader page. */
internal data class HadithPlaybackRequest(val databaseFile: String, val recordId: Int, val generation: Int) {
    fun isCurrent(databaseFile: String, recordId: Int, generation: Int): Boolean =
        this.databaseFile == databaseFile && this.recordId == recordId && this.generation == generation
}
