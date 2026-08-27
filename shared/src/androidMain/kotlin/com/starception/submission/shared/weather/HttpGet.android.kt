/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.weather

import com.starception.submission.core.logging.SharedLog
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "HttpGet"
private const val TIMEOUT_MS = 5_000

// A generic transport, not a second weather client, so implementing it here
// duplicates no domain logic — unlike LocationProvider, where an Android actual
// would have competed with EnhancedLocationService.
internal actual suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
    var connection: HttpsURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Starception-Android/1.0")
        }
        if (connection.responseCode !in 200..299) {
            SharedLog.w(TAG, "Unexpected status ${connection.responseCode}")
            null
        } else {
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        SharedLog.w(TAG, "Request failed: ${e.message}")
        null
    } finally {
        connection?.disconnect()
    }
}
