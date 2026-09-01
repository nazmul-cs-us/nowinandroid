/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.database

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPMethod
import platform.Foundation.writeToFile

private const val ASSET_BASE_URL =
    "https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev"

/**
 * Returns a usable bundled database, or downloads the same manifest asset into
 * the iOS cache when the source checkout contains a zero-byte CDN placeholder.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun resolveDatabaseAsset(
    bundledPath: String?,
    remotePath: String,
    cacheName: String,
): String {
    val manager = NSFileManager.defaultManager
    if (bundledPath != null && manager.fileSize(bundledPath) > 0L) return bundledPath

    val cacheRoot = manager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("The iOS cache directory is unavailable")
    val directory = cacheRoot.URLByAppendingPathComponent("StarceptionDatabases", true)
        ?: error("Unable to create the database cache URL")
    manager.createDirectoryAtURL(
        url = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val destination = directory.URLByAppendingPathComponent(cacheName, false)
        ?: error("Unable to create the database destination")
    val destinationPath = destination.path ?: error("Database destination has no path")
    if (manager.fileSize(destinationPath) > 0L) return destinationPath

    val source = NSURL.URLWithString("$ASSET_BASE_URL/$remotePath")
        ?: error("Invalid database download URL")
    val data = download(source)
        ?: error("Unable to download $cacheName. Check the network connection and try again.")
    check(data.length.toLong() > 0L) { "Downloaded $cacheName was empty" }
    check(data.writeToFile(destinationPath, atomically = true)) { "Unable to cache $cacheName" }
    return destinationPath
}

private suspend fun download(url: NSURL): NSData? = suspendCancellableCoroutine { continuation ->
    val request = NSMutableURLRequest(uRL = url).apply { setHTTPMethod("GET") }
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration.apply {
        timeoutIntervalForRequest = 60.0
        timeoutIntervalForResource = 180.0
    }
    val session = NSURLSession.sessionWithConfiguration(configuration)
    val task = session.dataTaskWithRequest(request) { data, response: NSURLResponse?, error ->
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        continuation.resume(if (error == null && status in 200..299) data else null)
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSFileManager.fileSize(path: String): Long {
    val attributes = attributesOfItemAtPath(path, error = null) ?: return 0L
    return (attributes["NSFileSize"] as? NSNumber)?.longLongValue ?: 0L
}
