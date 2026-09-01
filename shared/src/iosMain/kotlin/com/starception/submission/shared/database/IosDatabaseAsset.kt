/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.database

import com.starception.submission.shared.assets.iosCloudAssets

/**
 * Returns a usable bundled database, or downloads the same manifest asset into
 * the iOS cache when the source checkout contains a zero-byte CDN placeholder.
 */
internal suspend fun resolveDatabaseAsset(
    bundledPath: String?,
    remotePath: String,
    cacheName: String,
): String {
    return iosCloudAssets.resolveAsset(remotePath)?.absolutePath
        ?: bundledPath
        ?: error("Unable to resolve $cacheName from Cloudflare or the app bundle")
}
