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

package com.starception.submission.shared.content

/**
 * One Fortress-of-the-Muslim chapter (132 in the database).
 */
data class FortressChapter(
    val id: Int,
    val title: String,
)

/**
 * One Fortress invocation — the full Android Dua model minus the
 * Room-specific fields, including the recorded recitation URL.
 */
data class FortressInvocation(
    val id: Int,
    val chapterId: Int,
    val position: Int,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val context: String,
    val instruction: String,
    val note: String,
    val postContext: String,
    val description: String,
    val audioUrl: String,
)

interface SharedFortressRepository {
    suspend fun getChapters(): List<FortressChapter>
    suspend fun getChapterInvocations(chapterId: Int): List<FortressInvocation>

    /** Scholarly citation per invocation, keyed by invocation id (may be sparse). */
    suspend fun getChapterReferences(chapterId: Int): Map<Int, String>
}

expect fun createSharedFortressRepository(): SharedFortressRepository
