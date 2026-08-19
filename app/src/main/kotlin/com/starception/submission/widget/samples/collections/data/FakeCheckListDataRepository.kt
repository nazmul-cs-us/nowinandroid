package com.starception.submission.widget.samples.collections.data

import androidx.glance.GlanceId
import com.starception.submission.widget.samples.collections.layout.CheckListItem
import com.starception.submission.widget.samples.computeIfAbsent as computeIfAbsentExt
import com.starception.submission.widget.samples.removeIf as removeIfExt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A fake in-memory implementation of repository that produces list of [CheckListItem]s.
 */
class FakeCheckListDataRepository {
  private val items = MutableStateFlow<List<CheckListItem>>(listOf())
  private val checkedItems = MutableStateFlow<List<String>>(listOf())

  /**
   * Flow of [CheckListItem]s that can be listened to during a Glance session.
   */
  fun items(): Flow<List<CheckListItem>> = items

  /**
   * Flow of keys of [CheckListItem]s that are checked. This flow can be listened to during a
   * Glance session.
   */
  fun checkedItems(): Flow<List<String>> = checkedItems

  @OptIn(DelicateCoroutinesApi::class)
  fun checkItem(key: String) {
    GlobalScope.launch {
      withContext(Dispatchers.IO) {
        checkedItems.value = checkedItems.value.toMutableList().apply { add(key) }

        // Mimics backend processing that removes the item from backend database.
        // Until its removed from backend, since we added it to the checkedItems, it will display as
        // checked on the screen. Then, once backend is updated, the items list won't contain it and
        // it will be removed from the UI.
        delay(500)

        items.value = items.value.toMutableList().apply {
          removeIfExt { item ->
            item.key == key
          }
        }
        checkedItems.value = checkedItems.value.toMutableList().apply { remove(key) }
      }
    }
  }

  /**
   * Loads the [CheckListItem]s from the currently selected data source.
   */
  fun load(): List<CheckListItem> {
    items.value = demoData
    checkedItems.value = listOf()

    return items.value
  }

  companion object {
    private val repositories = mutableMapOf<GlanceId, FakeCheckListDataRepository>()

    val demoData = listOf(
      CheckListItem(
        key = "0",
        title = "Fajr",
        supportingText = "4:48 AM",
      ),
      CheckListItem(
        key = "1",
        title = "Dhuhr",
        supportingText = "12:25 PM"
      ),
      CheckListItem(
        key = "2",
        title = "Asr",
        supportingText = "3:54 PM",
      ),
      CheckListItem(
        key = "3",
        title = "Maghrib",
        supportingText = "6:53 PM",
      ),
      CheckListItem(
        key = "4",
        title = "Isha",
        supportingText = "8:22 PM",
      ),
      CheckListItem(
        key = "5",
        title = "Witr",
        supportingText = "After Isha",
      ),
      CheckListItem(
        key = "6",
        title = "Tahajjud",
        supportingText = "Last third of night",
      ),
    )

    /**
     * Returns the repository instance for the given widget represented by [glanceId].
     */
    fun getCheckListDataRepo(glanceId: GlanceId): FakeCheckListDataRepository {
      return synchronized(repositories) {
          repositories.computeIfAbsentExt(glanceId) { FakeCheckListDataRepository() }!!
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
  }
}