/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.shared.settings

import com.starception.submission.shared.location.DeviceLocation
import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LastLocationStore(private val store: KeyValueStore = platformKeyValueStore()) {

    private val json = Json { ignoreUnknownKeys = true }

    fun location(): DeviceLocation? = store.getString(KEY_LOCATION)
        ?.takeIf { it.isNotBlank() }
        ?.let { encoded -> runCatching { json.decodeFromString<StoredLocation>(encoded) }.getOrNull() }
        ?.takeIf {
            it.latitude in -90.0..90.0 &&
                it.longitude in -180.0..180.0 &&
                it.timeZoneOffset in -18.0..18.0
        }
        ?.toDeviceLocation()

    fun save(location: DeviceLocation) {
        val stored = StoredLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            timeZoneOffset = location.timeZoneOffset,
            placeName = location.placeName,
            countryCode = location.countryCode,
        )
        store.putString(KEY_LOCATION, json.encodeToString(stored))
    }

    @Serializable
    private data class StoredLocation(
        val latitude: Double,
        val longitude: Double,
        val timeZoneOffset: Double,
        val placeName: String,
        val countryCode: String,
    ) {
        fun toDeviceLocation() = DeviceLocation(
            latitude = latitude,
            longitude = longitude,
            timeZoneOffset = timeZoneOffset,
            placeName = placeName,
            countryCode = countryCode,
        )
    }

    private companion object {
        const val KEY_LOCATION = "ios_last_successful_location"
    }
}
