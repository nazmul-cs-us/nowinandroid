package com.starception.submission.di

import android.content.Context
import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.EnhancedLocationService
import com.starception.submission.prayer.service.LocationService
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
        astronomicalCalculator: AstronomicalCalculator
    ): PrayerTimeCalculatorService = PrayerTimeCalculatorService(astronomicalCalculator)

    @Provides
    @Singleton
    fun provideEnhancedLocationService(
        @ApplicationContext context: Context
    ): EnhancedLocationService = EnhancedLocationService(context)

    @Provides
    @Singleton
    fun providePrayerSettingsRepository(
        @ApplicationContext context: Context
    ): PrayerSettingsRepository = PrayerSettingsRepository(context)
}