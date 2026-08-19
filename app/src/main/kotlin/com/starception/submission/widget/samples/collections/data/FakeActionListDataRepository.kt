package com.starception.submission.widget.samples.collections.data

import android.os.Build
import androidx.glance.GlanceId
import com.starception.submission.R
import com.starception.submission.widget.samples.collections.layout.ActionListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A fake in-memory implementation of repository that produces list of [ActionListItem]s/
 */
class FakeActionListDataRepository {
  private val items = MutableStateFlow<List<ActionListItem>>(listOf())
  private val checkedItems = MutableStateFlow<List<String>>(listOf())

  /**
   * Flow of [ActionListItem]s that can be listened to during a Glance session.
   */
  fun items(): Flow<List<ActionListItem>> = items

  /**
   * Flow of keys of [ActionListItem]s that are checked. This flow can be listened to during a
   * Glance session.
   */
  fun checkedItems(): Flow<List<String>> = checkedItems

  fun checkItem(key: String) {
    if (checkedItems.value.contains(key)) {
      checkedItems.value = checkedItems.value.toMutableList().apply { remove(key) }
    } else {
      checkedItems.value = checkedItems.value.toMutableList().apply { add(key) }
    }
  }

  /**
   * Loads the [ActionListItem]s from the currently selected data source.
   */
  fun load(): List<ActionListItem> {
    items.value = demoData

    checkedItems.value = listOf()
    return items.value
  }

  companion object {
    private val repositories = mutableMapOf<GlanceId, FakeActionListDataRepository>()

    val demoData = listOf(
      ActionListItem(
        key = "0",
        title = "Adhan notifications",
        onSupportingText = "ON",
        offSupportingText = "OFF",
        stateIconRes = R.drawable.sample_adhan_icon,
        onStateActionContentDescription = "", // Supporting text already covers this
        offStartActionContentDescription = "", // Supporting text already covers this
      ),
      ActionListItem(
        key = "1",
        title = "Weather alerts",
        onSupportingText = "ON (38°C)",
        offSupportingText = "OFF",
        stateIconRes = R.drawable.sample_thermostat_icon,
        onStateActionContentDescription = "", // Supporting text already covers this
        offStartActionContentDescription = "", // Supporting text already covers this
        trailingIconButtonRes = R.drawable.sample_arrow_right_icon,
        trailingIconButtonContentDescription = "Edit thresholds"
      ),
      ActionListItem(
        key = "2",
        title = "Silent during prayer",
        onSupportingText = "ON",
        offSupportingText = "OFF",
        stateIconRes = R.drawable.sample_silent_icon,
        onStateActionContentDescription = "", // Supporting text already covers this
        offStartActionContentDescription = "" // Supporting text already covers this
      ),
      ActionListItem(
        key = "3",
        title = "Go to mosque",
        onSupportingText = "Reminding",
        offSupportingText = "Off",
        stateIconRes = R.drawable.sample_mosque_icon,
        onStateActionContentDescription = "", // Supporting text already covers this
        offStartActionContentDescription = "" // Supporting text already covers this
      ),
      ActionListItem(
        key = "4",
        title = "Pre-prayer reminder",
        onSupportingText = "ON",
        offSupportingText = "OFF",
        stateIconRes = R.drawable.sample_qibla_icon,
        onStateActionContentDescription = "",  // Supporting text already covers this
        offStartActionContentDescription = "" // Supporting text already covers this
      ),
      ActionListItem(
        key = "5",
        title = "Qibla reminder",
        onSupportingText = "ON",
        offSupportingText = "OFF",
        stateIconRes = R.drawable.sample_reminder_icon,
        onStateActionContentDescription = "",  // Supporting text already covers this
        offStartActionContentDescription = "" // Supporting text already covers this
      ),
    )

    /**
     * Returns the repository instance for the given widget represented by [glanceId].
     */
    fun getActionListDataRepo(glanceId: GlanceId): FakeActionListDataRepository {
      return synchronized(repositories) {
          if (!repositories.contains(glanceId)) {
              repositories[glanceId] = FakeActionListDataRepository()
          }
          return repositories[glanceId]?:FakeActionListDataRepository()
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