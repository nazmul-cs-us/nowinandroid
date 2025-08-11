package com.starception.dua.di

import com.starception.dua.prayer.calculator.AstronomicalCalculator
import com.starception.dua.prayer.repository.PrayerSettingsRepository
import com.starception.dua.prayer.service.EnhancedLocationService
import com.starception.dua.prayer.service.LocationService
import com.starception.dua.prayer.service.PrayerTimeCalculatorService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}