/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.hadith

data class SharedHadith(
    val id: Int,
    val arabic: String,
    val english: String,
    val explanation: String = "",
)

interface SharedHadithRepository {
    suspend fun getHadith(id: Int): SharedHadith?
    suspend fun getHadiths(firstId: Int, lastId: Int): List<SharedHadith>
}

expect fun createSharedHadithRepository(): SharedHadithRepository
