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

package com.starception.submission.core.model.deenly

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InsightReadAheadTest {
    private var saved: String? = null
    private fun cache(scope: CoroutineScope) = InsightReadAhead(scope, { it: String -> it }, { it }, { saved }, { saved = it })

    @Test
    fun restartRestoresCurrentAndSpareWithoutWaitingForModel() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val first = cache(scope)
            var loads = 0
            assertEquals("reading-0", first.get("today") { "reading-${loads++}" })
            assertEquals(2, loads)
            val restored = cache(scope)
            assertEquals("reading-0", restored.peek("today"))
            val blocked = CompletableDeferred<String?>()
            assertEquals("reading-1", restored.get("today") { blocked.await() })
            assertEquals("reading-1", restored.peek("today"))
        } finally { scope.cancel() }
    }

    @Test
    fun leavingTileDoesNotCancelSingleFlightPreparation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val readings = cache(scope)
            val model = CompletableDeferred<String?>()
            var calls = 0
            val request = async(Dispatchers.Unconfined) {
                readings.get("Fajr") { calls++; model.await() }
            }
            request.cancel()
            val returnVisit = async(Dispatchers.Unconfined) {
                readings.get("Fajr") { calls++; "unexpected" }
            }
            assertEquals(1, calls)
            model.complete("prepared")
            assertEquals("prepared", returnVisit.await())
        } finally { scope.cancel() }
    }

    @Test
    fun failedRefillKeepsReadableContentAndCanRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val readings = cache(scope)
            assertEquals("good", readings.get("today") { variation -> if (variation == 0) "good" else error("model failed") })
            assertEquals("good", readings.get("today") { null })
            assertEquals("good", readings.peek("today"))
            assertEquals("good", readings.get("today") { "recovered" })
            assertEquals("recovered", readings.get("today") { null })
        } finally { scope.cancel() }
    }

    @Test
    fun prayerAndDateChangesNeverShowPreviousContext() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val readings = cache(scope)
            readings.get("2026-10-24|Fajr") { "Fajr reading" }
            assertNull(readings.peek("2026-10-24|Dhuhr"))
            assertNull(readings.peek("2026-10-25|Fajr"))
        } finally { scope.cancel() }
    }

    @Test
    fun warmPreparesOnlyCurrentAndOneSpare() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val readings = cache(scope)
            var calls = 0
            repeat(5) { readings.warm("today") { "reading-${calls++}" } }
            assertEquals(2, calls)
            assertEquals("reading-0", readings.peek("today"))
        } finally { scope.cancel() }
    }

    @Test
    fun persistencePreservesExactSourceAndRejectsMalformedData() {
        val fields = listOf("", "1:2\nFriday · الصلاة", "💡")
        assertEquals(fields, unpackInsightFields(packInsightFields(fields)))
        saved = "invalid snapshot"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { assertNull(cache(scope).peek("today")) } finally { scope.cancel() }
    }
}
