package com.starception.submission.widget.samples.text.data

import androidx.glance.GlanceId
import com.starception.submission.widget.samples.text.layout.LongTextLayoutData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.starception.submission.widget.samples.computeIfAbsent as computeIfAbsentExt
/**
 * An fake in-memory repository to provide data for displaying different demo samples in
 * [com.starception.submission.widget.samples.text.layout.LongTextLayout]
 */
class FakeLongTextRepository {
  private var itemIndex: Int = 0
  private var itemsCount: Int = 0
  private val data = MutableStateFlow(demoItems[0])

  fun data(): Flow<LongTextLayoutData> = data

  /**
   * Mimics refresh by returning a different data item from the demo data list.
   *
   * This allows us to try the layout with various texts pertaining to a specific kind of data.
   */
  fun refresh() {
    itemIndex = (itemIndex + 1) % itemsCount

    this.load()
  }

  /** Loads the data and updates the flow */
  fun load(): LongTextLayoutData {
    itemsCount = demoItems.size
    data.value = demoItems[itemIndex]

    return data.value
  }

  companion object {
    private val repositories = mutableMapOf<GlanceId, FakeLongTextRepository>()

    /**
     * Returns the repository instance for the given widget represented by [glanceId].
     */
    fun getRepo(glanceId: GlanceId): FakeLongTextRepository {
      return synchronized(repositories) {
        repositories.computeIfAbsentExt(glanceId) {
          FakeLongTextRepository()
        }!!
      }
    }

    /**
     * Cleans up local data associated with the provided [glanceId].
     */
    fun cleanUp(glanceId: GlanceId) {
      synchronized(repositories) {
        repositories.remove(glanceId)
      }
    }

    // Lorem ipsum text generated with https://loremipsum.io/
    val demoItems = listOf(
      LongTextLayoutData(
        key = "item 0",
        text = "Whoever prays Fajr in congregation is under the protection of Allah for the whole day.",
        caption = "Hadith",
      ),
      LongTextLayoutData(
        key = "item 1",
        text = "Our Lord, give us good in this world and good in the Hereafter, and protect us from the punishment of the Fire.",
        caption = "Dua",
      ),
      LongTextLayoutData(
        key = "item 2",
        text = "The most beloved deeds to Allah are those done consistently, even if they are small.",
        caption = "Hadith",
      ),
      LongTextLayoutData(
        key = "item 3",
        text = "Remember Allah in times of ease, and He will remember you in times of hardship.",
        caption = "Reminder"
      ),
      LongTextLayoutData(
        key = "item 4",
        text = "Two units of prayer before Fajr are better than the world and all it contains.",
        caption = "Hadith"
      ),
    )
  }
}