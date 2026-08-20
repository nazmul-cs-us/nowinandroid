/*
 * Copyright 2026 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.widget

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a widget tap wants the app to land. */
sealed interface WidgetNavigationTarget {

    data class Hadith(
        val databaseFile: String,
        val hadithNumber: Int,
        val collectionName: String,
    ) : WidgetNavigationTarget

    data class Dua(
        val title: String,
        val content: String,
        val duaNumber: Int,
    ) : WidgetNavigationTarget
}

/**
 * Process-wide bus for "a widget was tapped, open this" requests.
 *
 * The same idiom as [com.starception.submission.ui.search.SearchPrefillBus], and for the
 * same reason: MainActivity receives the intent but the NavController that can act on it
 * lives several composables deep. The alternative was threading another parameter through
 * NiaApp -> NiaAppContent -> the two-pane wrappers -> NiaNavHost, the way deepLinkCourseId
 * already is at six call sites; a bus keeps the widget's business out of those signatures
 * entirely.
 *
 * A held value rather than a SharedFlow, and this is the whole correctness of the thing:
 * a SharedFlow delivers only to collectors that already exist, so an emission made before
 * anyone subscribes is dropped. That is not an edge case here, it is the normal path —
 * the tap cold-starts the app, MainActivity posts during onCreate, and NiaNavHost does not
 * exist to collect for another few hundred milliseconds. The first build of this used
 * `MutableSharedFlow(replay = 0, extraBufferCapacity = 1)` and the tap silently landed on
 * the home screen every time; extraBufferCapacity buffers for slow *existing* collectors,
 * it does not hold a value for a future one.
 *
 * [consume] clears it once acted on, so a recomposition or a configuration change does not
 * replay the navigation and drag the user back out of wherever they have since gone.
 */
object WidgetNavigationBus {

    private val _pending = MutableStateFlow<WidgetNavigationTarget?>(null)
    val pending: StateFlow<WidgetNavigationTarget?> = _pending.asStateFlow()

    fun request(target: WidgetNavigationTarget) {
        _pending.value = target
    }

    /** Clears [target] once it has been navigated to. */
    fun consumed(target: WidgetNavigationTarget) {
        _pending.compareAndSet(target, null)
    }

    /**
     * Reads a target out of a launch intent, or null if this intent is not a widget tap.
     *
     * Extras rather than a deep-link Uri because a dua carries its own translation, which
     * is a sentence rather than an identifier — percent-encoding a paragraph into a path
     * segment works but produces a URI nobody can read in a log.
     *
     * The extras are cleared as they are read: an Activity keeps its launch intent, so on
     * every later recreation — a rotation, a theme change — the same tap would be replayed
     * and yank the user back out of wherever they had navigated to.
     */
    fun consume(intent: Intent?): WidgetNavigationTarget? {
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: return null
        intent.removeExtra(EXTRA_KIND)
        return when (kind) {
            KIND_HADITH -> WidgetNavigationTarget.Hadith(
                databaseFile = intent.getStringExtra(EXTRA_HADITH_DB) ?: return null,
                hadithNumber = intent.getIntExtra(EXTRA_HADITH_NUMBER, -1).takeIf { it > 0 }
                    ?: return null,
                collectionName = intent.getStringExtra(EXTRA_COLLECTION).orEmpty(),
            )

            KIND_DUA -> WidgetNavigationTarget.Dua(
                title = intent.getStringExtra(EXTRA_DUA_TITLE) ?: return null,
                content = intent.getStringExtra(EXTRA_DUA_CONTENT).orEmpty(),
                duaNumber = intent.getIntExtra(EXTRA_DUA_NUMBER, 1),
            )

            else -> null
        }
    }

    /** Puts [target] on an intent in the form [consume] expects. */
    fun put(intent: Intent, target: WidgetNavigationTarget): Intent = when (target) {
        is WidgetNavigationTarget.Hadith -> intent
            .putExtra(EXTRA_KIND, KIND_HADITH)
            .putExtra(EXTRA_HADITH_DB, target.databaseFile)
            .putExtra(EXTRA_HADITH_NUMBER, target.hadithNumber)
            .putExtra(EXTRA_COLLECTION, target.collectionName)

        is WidgetNavigationTarget.Dua -> intent
            .putExtra(EXTRA_KIND, KIND_DUA)
            .putExtra(EXTRA_DUA_TITLE, target.title)
            .putExtra(EXTRA_DUA_CONTENT, target.content)
            .putExtra(EXTRA_DUA_NUMBER, target.duaNumber)
    }

    const val EXTRA_KIND = "widget_target_kind"
    private const val EXTRA_HADITH_DB = "widget_hadith_db"
    private const val EXTRA_HADITH_NUMBER = "widget_hadith_number"
    private const val EXTRA_COLLECTION = "widget_collection"
    private const val EXTRA_DUA_TITLE = "widget_dua_title"
    private const val EXTRA_DUA_CONTENT = "widget_dua_content"
    private const val EXTRA_DUA_NUMBER = "widget_dua_number"
    private const val KIND_HADITH = "hadith"
    private const val KIND_DUA = "dua"
}
