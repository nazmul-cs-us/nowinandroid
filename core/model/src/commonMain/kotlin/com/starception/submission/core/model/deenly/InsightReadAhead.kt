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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * A bounded, persisted current reading and one spare per context. Call on the main thread.
 * The owner supplies an application scope, so leaving a tile never cancels preparation.
 * Successful content is saved before delivery; failures never replace a readable item.
 */
class InsightReadAhead<T : Any>(
    private val scope: CoroutineScope,
    private val encode: (T) -> String,
    private val decode: (String) -> T,
    private val read: () -> String?,
    private val write: (String) -> Unit,
) {
    private class Entry<T>(
        var current: T? = null,
        var next: T? = null,
        var sequence: Int = 0,
        var pending: Deferred<T?>? = null,
    )

    private val entries = linkedMapOf<String, Entry<T>>()
    private var restored = false

    private fun entry(key: String): Entry<T> {
        if (!restored) {
            restored = true
            runCatching {
                read()?.let(::unpackInsightFields)?.chunked(4)?.takeLast(8)?.forEach { fields ->
                    if (fields.size == 4) runCatching {
                        entries[fields[0]] = Entry(
                            current = fields[1].takeIf(String::isNotEmpty)?.let(decode),
                            next = fields[2].takeIf(String::isNotEmpty)?.let(decode),
                            sequence = fields[3].toInt().coerceAtLeast(0),
                        )
                    }
                }
            }
        }
        return entries.getOrPut(key) {
            if (entries.size >= 8) entries.remove(entries.keys.first())
            Entry()
        }
    }

    fun peek(key: String): T? = entry(key).current

    /** Warm the current reading without consuming the spare. */
    fun warm(key: String, load: suspend (variation: Int) -> T?) {
        val entry = entry(key)
        val pending = prepare(entry, load) ?: return
        scope.async {
            if (pending.await() != null) prepare(entry, load)
        }
    }

    /** Promote a ready spare on entry, otherwise keep showing the last successful reading. */
    suspend fun get(key: String, load: suspend (variation: Int) -> T?): T? {
        val entry = entry(key)
        entry.next?.let {
            entry.current = it
            entry.next = null
            persist()
        }
        entry.current?.let {
            prepare(entry, load)
            return it
        }
        val result = prepare(entry, load)?.await()
        if (result != null) prepare(entry, load)
        return result
    }

    private fun prepare(entry: Entry<T>, load: suspend (Int) -> T?): Deferred<T?>? {
        if (entry.next != null) return null
        entry.pending?.let { return it }
        val pending = scope.async(start = CoroutineStart.LAZY) {
            try {
                val result = load(entry.sequence++)
                if (result != null) {
                    if (entry.current == null) entry.current = result else entry.next = result
                    persist()
                }
                result
            } finally {
                entry.pending = null
            }
        }
        entry.pending = pending
        pending.start()
        return pending
    }

    private fun persist() {
        // A corrupt/unwritable preference must not prevent delivery of valid content.
        runCatching {
            write(packInsightFields(entries.flatMap { (key, entry) ->
                listOf(key, entry.current?.let(encode).orEmpty(), entry.next?.let(encode).orEmpty(), entry.sequence.toString())
            }))
        }
    }
}

/** Length framing preserves exact source wording, including newlines and delimiters. */
fun packInsightFields(fields: List<String>): String = fields.joinToString("") { "${it.length}:$it" }

fun unpackInsightFields(value: String): List<String> = buildList {
    var position = 0
    while (position < value.length) {
        val delimiter = value.indexOf(':', position)
        require(delimiter >= position)
        val length = value.substring(position, delimiter).toInt()
        require(length >= 0 && length <= value.length - delimiter - 1)
        position = delimiter + 1
        add(value.substring(position, position + length))
        position += length
    }
}
