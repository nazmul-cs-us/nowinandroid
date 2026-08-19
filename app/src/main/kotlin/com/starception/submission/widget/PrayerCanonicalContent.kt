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

import androidx.compose.runtime.Composable
import com.starception.submission.R
import com.starception.submission.widget.samples.collections.layout.ActionListItem
import com.starception.submission.widget.samples.collections.layout.ActionListLayout
import com.starception.submission.widget.samples.utils.ActionUtils

/**
 * Today's prayers rendered through the canonical Action List layout.
 *
 * This deliberately delegates to the layout ported from platform-samples rather than
 * styling a lookalike. A hand-written imitation drifts the moment either side changes —
 * and it already had: the prayer widgets were near-black with a green accent while the
 * ported ones were Material You tonal surfaces, which is exactly the mismatch this
 * removes. Sharing the layout means the two are identical because they are the same
 * code, not because they were kept in sync by hand.
 *
 * The mapping onto the layout's own vocabulary:
 *
 *  - title bar icon + title -> app glyph + the user's location
 *  - each row's state icon   -> that prayer's Meteocon
 *  - title / supporting text -> prayer name / time
 *  - "checked" row           -> the next prayer, which the layout fills with the accent
 */
@Composable
internal fun PrayerActionListContent(state: PrayerWidgetState.Available) {
    ActionListLayout(
        title = state.place,
        titleIconRes = R.drawable.ic_prayer_monochrome,
        titleBarActionIconRes = R.drawable.sample_refresh_icon,
        titleBarActionIconContentDescription = "Refresh prayer times",
        titleBarAction = ActionUtils.actionStartDemoActivity("prayer-widget-refresh"),
        items = state.prayers.map { it.toActionListItem() },
        // The layout paints checked rows with the accent container, which is precisely
        // the emphasis the next prayer wants — so "checked" here means "next", not
        // "done". Marking prayers prayed is a separate concern that belongs on the
        // Check List layout.
        checkedItems = state.prayers.filter { it.isNext }.map { it.name },
        actionButtonClick = { /* Tapping a row opens the app via the layout's own action. */ },
    )
}

private fun WidgetPrayer.toActionListItem() = ActionListItem(
    key = name,
    title = name,
    onSupportingText = supportingText(),
    offSupportingText = supportingText(),
    // Only reached when a prayer has no forecast; the layout tints it to match the row.
    stateIconRes = R.drawable.widget_preview_weather,
    stateIconBitmap = weatherIcon,
    onStateActionContentDescription = "",
    offStartActionContentDescription = "",
)

/** Time, plus the temperature for that hour when the forecast reached us. */
private fun WidgetPrayer.supportingText(): String =
    temperature?.let { "$time  ·  $it" } ?: time
