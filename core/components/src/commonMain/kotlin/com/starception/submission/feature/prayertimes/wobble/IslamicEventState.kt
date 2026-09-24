/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.feature.prayertimes.wobble

/**
 * State for the Hijri-calendar event banner shown in PullToSyncContainer.
 * Surfaces single-day or short-window Islamic events (Arafah, Ashura,
 * Laylat al-Qadr, Eid, Hijri new year) so the whole app reflects today's
 * devotional context, not just the search screen.
 *
 * Stacking inside PullToSyncContainer: prayer alert > download > media >
 * islamic event > silent mode > mushaf.
 */
data class IslamicEventState(
    val isActive: Boolean = false,
    val eventKey: String = "",
    val title: String = "",
    val searchQuery: String = "",
)
