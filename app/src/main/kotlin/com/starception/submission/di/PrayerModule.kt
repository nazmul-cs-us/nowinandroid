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

package com.starception.submission.di

import android.content.Context
import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.EnhancedLocationService
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing prayer-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object PrayerModule {

    @Provides
    @Singleton
    fun provideAstronomicalCalculator(): AstronomicalCalculator = AstronomicalCalculator()

    @Provides
    @Singleton
    fun providePrayerTimeCalculatorService(
        astronomicalCalculator: AstronomicalCalculator,
    ): PrayerTimeCalculatorService = PrayerTimeCalculatorService(astronomicalCalculator)

    @Provides
    @Singleton
    fun provideEnhancedLocationService(
        @ApplicationContext context: Context,
    ): EnhancedLocationService = EnhancedLocationService(context)

    @Provides
    @Singleton
    fun providePrayerSettingsRepository(
        @ApplicationContext context: Context,
        userSettingsStore: com.starception.submission.usersettings.UserSettingsStore,
    ): PrayerSettingsRepository = PrayerSettingsRepository(context, userSettingsStore)
}
