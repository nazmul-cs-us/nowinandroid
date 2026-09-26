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

package com.starception.submission.shared.hadith

/** Android uses its Room-backed HadithRepository; this shared host is currently used by iOS. */
actual fun createSharedHadithRepository(): SharedHadithRepository = object : SharedHadithRepository {
    override suspend fun getHadith(id: Int): SharedHadith? = null
    override suspend fun getHadiths(firstId: Int, lastId: Int): List<SharedHadith> = emptyList()
    override suspend fun getShamayelHadiths(firstId: Int, lastId: Int): List<SharedHadith> = emptyList()
}
