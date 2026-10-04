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

package com.starception.submission.shared.connectivity

import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide connectivity for the shared UI — feeds the sync strip's
 * persistent offline row ([com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer]'s
 * `isOffline`). One instance for the whole app; the StateFlow is safe to
 * collect from composition.
 */
expect class ConnectivityMonitor() {
    val isOnline: StateFlow<Boolean>
}

/** The single shared instance every screen reads. */
val appConnectivity: ConnectivityMonitor by lazy { ConnectivityMonitor() }
