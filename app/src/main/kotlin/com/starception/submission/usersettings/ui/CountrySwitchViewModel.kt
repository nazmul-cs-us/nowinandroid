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

package com.starception.submission.usersettings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.repository.PrayerSettingsRepository.CountrySwitchProposal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes the pending country-change consent proposal and the user's accept/decline actions.
 * Nothing about prayer settings changes until [apply] is called.
 */
@HiltViewModel
class CountrySwitchViewModel @Inject constructor(
    private val repository: PrayerSettingsRepository,
) : ViewModel() {

    val pending: StateFlow<CountrySwitchProposal?> = repository.pendingCountrySwitch

    /** Accept: apply the proposed country's settings. */
    fun apply() {
        viewModelScope.launch { repository.applyPendingCountrySwitch() }
    }

    /** Decline: keep current settings; don't re-prompt for this country. */
    fun keepCurrent() {
        viewModelScope.launch { repository.keepCurrentForDetectedCountry() }
    }

    /** Swiped away without choosing: hide for now; it reappears on the next app open. */
    fun dismissForNow() {
        repository.dismissProposalForNow()
    }

    /** Re-check on app resume so the prompt reappears every time the app is opened. */
    fun revalidate() {
        viewModelScope.launch { repository.revalidatePendingCountrySwitch() }
    }
}
