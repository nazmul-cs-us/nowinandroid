/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.feature.prayertimes

import com.starception.submission.prayer.model.getPrayerDisplayName as sharedDisplayName
import com.starception.submission.prayer.model.getPrayerNameInLocalLanguage as sharedLocalName
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * java.time front doors for the shared prayer name translator.
 *
 * The translation tables moved to :core:prayer-engine so iOS names the prayers
 * identically, including Jumu'ah on Fridays. The shared functions deliberately
 * take a boolean rather than a date: making them take kotlinx's LocalDate meant
 * converting at every call site, including inside the notification receivers,
 * workers and service — churn in alarm-scheduling code for no gain.
 *
 * These adapters live in the package the call sites already use, so none of them
 * needed touching.
 */
fun isJumuahDay(date: LocalDate = LocalDate.now()): Boolean = date.dayOfWeek == DayOfWeek.FRIDAY

fun getPrayerDisplayName(englishName: String, date: LocalDate = LocalDate.now()): String =
    sharedDisplayName(englishName, isJumuahDay(date))

fun getPrayerNameInLocalLanguage(
    englishName: String,
    countryCode: String?,
    date: LocalDate = LocalDate.now(),
): String = sharedLocalName(englishName, countryCode, isJumuahDay(date))
