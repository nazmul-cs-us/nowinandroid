/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.core.assets

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSLock
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionDownloadTaskResumeData
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.downloadTaskWithResumeData
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setHTTPMethod
import platform.Foundation.writeToFile
import platform.darwin.NSObject
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread
import platform.posix.rename

private const val ASSET_DIRECTORY = "StarceptionAssets"
private const val RESUME_DATA_DIRECTORY = ".resume-data"
private const val HASH_BUFFER_SIZE = 64 * 1024

/**
 * iOS storage and transport boundary for the shared Cloudflare asset repository.
 *
 * CDN keys remain relative paths below Application Support/StarceptionAssets.
 * The directory is excluded from iCloud backups because every file is
 * reproducible from either the app bundle or Cloudflare.
 */
@OptIn(ExperimentalForeignApi::class)
object AssetPlatform {
    private val fileManager: NSFileManager
        get() = NSFileManager.defaultManager

    private val downloadDelegate = AssetDownloadDelegate()
    private val downloadSession by lazy {
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration.apply {
            timeoutIntervalForRequest = 60.0
            timeoutIntervalForResource = 600.0
        }
        NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = downloadDelegate,
            delegateQueue = null,
        )
    }

    fun bundledText(name: String): String? = bundledPath(name)?.let(::readText)

    fun cachedText(cdnKey: String): String? = cachedPath(cdnKey)?.let(::readText)

    fun writeCachedText(cdnKey: String, text: String): Boolean {
        val destination = assetPath(cdnKey)
        createParentDirectory(destination)
        val temporary = "$destination.${NSUUID.UUID().UUIDString}.tmp"
        val data = NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding)
            ?: return false
        if (!data.writeToFile(temporary, atomically = false)) return false
        if (rename(temporary, destination) != 0) {
            fileManager.removeItemAtPath(temporary, error = null)
            return false
        }
        return true
    }

    fun cachedPath(cdnKey: String): String? =
        assetPath(cdnKey).takeIf { fileManager.fileSize(it) > 0L }

    fun bundledPath(cdnKey: String): String? {
        val fileName = BUNDLED_ASSET_NAMES[cdnKey] ?: cdnKey.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val resource = if (extension.isEmpty()) fileName else fileName.removeSuffix(".$extension")
        return NSBundle.mainBundle.pathForResource(
            name = resource,
            ofType = extension.ifEmpty { null },
        )?.takeIf { fileManager.fileSize(it) > 0L }
    }

    /**
     * Streams a response into URLSession's temporary file, validates it, then
     * atomically renames a same-directory staging file over the cached asset.
     */
    suspend fun download(
        url: String,
        cdnKey: String,
        resumeDataKey: String = cdnKey,
        expectedSize: Long? = null,
        expectedSha256: String? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): String? = suspendCancellableCoroutine { continuation ->
        val source = NSURL.URLWithString(url)
        if (source == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val destination = assetPath(cdnKey)
        createParentDirectory(destination)
        val request = NSMutableURLRequest(uRL = source).apply { setHTTPMethod("GET") }
        val resumeData = loadResumeData(resumeDataKey)
        val task = if (resumeData != null) {
            downloadSession.downloadTaskWithResumeData(resumeData)
        } else {
            downloadSession.downloadTaskWithRequest(request)
        }
        downloadDelegate.register(
            task,
            DownloadContext(
                destination = destination,
                resumeDataKey = resumeDataKey,
                expectedSize = expectedSize,
                expectedSha256 = expectedSha256,
                onProgress = onProgress,
                continuation = continuation,
            ),
        )
        continuation.invokeOnCancellation { downloadDelegate.cancel(task) }
        task.resume()
    }

    fun delete(cdnKey: String): Boolean {
        val path = assetPath(cdnKey)
        val deletedAsset = !fileManager.fileExistsAtPath(path) ||
            fileManager.removeItemAtPath(path, error = null)
        val deletedResumeData = clearResumeData(cdnKey)
        return deletedAsset && deletedResumeData
    }

    suspend fun fetchText(url: String): String? = suspendCancellableCoroutine { continuation ->
        val source = NSURL.URLWithString(url)
        if (source == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest(uRL = source).apply { setHTTPMethod("GET") }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            val text = if (error == null && status in 200..299 && data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
            } else {
                null
            }
            if (continuation.isActive) continuation.resume(text)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    fun fileSize(path: String): Long = fileManager.fileSize(path)

    fun fileSha256(path: String): String? = sha256(path)

    fun commit(temporaryPath: String, cdnKey: String): String? {
        val destination = assetPath(cdnKey)
        createParentDirectory(destination)
        if (fileManager.fileExistsAtPath(destination)) {
            fileManager.removeItemAtPath(destination, error = null)
        }
        if (rename(temporaryPath, destination) != 0) return null
        return destination
    }

    private fun stageDownload(
        downloadedPath: String,
        destination: String,
    ): String? {
        val staging = "$destination.${NSUUID.UUID().UUIDString}.download"
        return staging.takeIf {
            fileManager.moveItemAtPath(downloadedPath, toPath = staging, error = null)
        }
    }

    private fun promoteDownload(
        staging: String,
        destination: String,
        expectedSize: Long?,
        expectedSha256: String?,
    ): String? {
        val validSize = expectedSize == null || fileManager.fileSize(staging) == expectedSize
        val validHash = expectedSha256 == null || sha256(staging).equals(expectedSha256, ignoreCase = true)
        if (!validSize || !validHash || rename(staging, destination) != 0) {
            fileManager.removeItemAtPath(staging, error = null)
            return null
        }
        return destination
    }

    private fun assetPath(cdnKey: String): String {
        val normalized = cdnKey.trimStart('/')
        require(normalized.isNotEmpty() && normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "Invalid CDN key: $cdnKey"
        }
        return "${assetRootPath()}/$normalized"
    }

    private fun resumeDataPath(cdnKey: String): String =
        assetPath("$RESUME_DATA_DIRECTORY/${cdnKey.trimStart('/')}.resumeData")

    private fun loadResumeData(cdnKey: String): NSData? {
        val path = resumeDataPath(cdnKey)
        if (fileManager.fileSize(path) <= 0L) {
            clearResumeData(cdnKey)
            return null
        }
        return NSData.dataWithContentsOfFile(path) ?: run {
            clearResumeData(cdnKey)
            null
        }
    }

    private fun persistResumeData(cdnKey: String, data: NSData?): Boolean {
        if (data == null || data.length == 0uL) return clearResumeData(cdnKey)
        val path = resumeDataPath(cdnKey)
        createParentDirectory(path)
        return data.writeToFile(path, atomically = true)
    }

    private fun clearResumeData(cdnKey: String): Boolean {
        val path = resumeDataPath(cdnKey)
        return !fileManager.fileExistsAtPath(path) || fileManager.removeItemAtPath(path, error = null)
    }

    private fun assetRootPath(): String {
        val support = fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: error("The iOS Application Support directory is unavailable")
        val root = support.URLByAppendingPathComponent(ASSET_DIRECTORY, true)
            ?: error("Unable to create the asset directory URL")
        fileManager.createDirectoryAtURL(
            url = root,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        check(root.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
            "Unable to exclude the asset directory from iCloud backup"
        }
        return requireNotNull(root.path) { "The asset directory has no path" }
    }

    private fun createParentDirectory(path: String) {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = assetRootPath())
        fileManager.createDirectoryAtPath(
            path = parent,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    private fun readText(path: String): String? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    private fun sha256(path: String): String? = memScoped {
        val file = fopen(path, "rb") ?: return@memScoped null
        try {
            val buffer = allocArray<ByteVar>(HASH_BUFFER_SIZE)
            val digest = Sha256Digest()
            while (true) {
                val count = fread(buffer, 1.convert(), HASH_BUFFER_SIZE.convert(), file)
                if (count == 0UL) break
                digest.update(ByteArray(count.toInt()) { index -> buffer[index] })
            }
            if (ferror(file) != 0) return@memScoped null
            digest.hex()
        } finally {
            fclose(file)
        }
    }

    private fun NSFileManager.fileSize(path: String): Long {
        val attributes = attributesOfItemAtPath(path, error = null) ?: return 0L
        return (attributes["NSFileSize"] as? NSNumber)?.longLongValue ?: 0L
    }

    private class DownloadContext(
        val destination: String,
        val resumeDataKey: String,
        val expectedSize: Long?,
        val expectedSha256: String?,
        val onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        val continuation: CancellableContinuation<String?>,
        var stagingPath: String? = null,
        var cancellationRequested: Boolean = false,
        var resumeOffset: Long = 0L,
        var resumedExpectedBytes: Long = 0L,
    )

    private class AssetDownloadDelegate : NSObject(), NSURLSessionDownloadDelegateProtocol {
        private val lock = NSLock()
        private val downloads = mutableMapOf<ULong, DownloadContext>()

        fun register(task: NSURLSessionDownloadTask, context: DownloadContext) {
            withLock { downloads[task.taskIdentifier] = context }
        }

        fun cancel(task: NSURLSessionDownloadTask) {
            val context = withLock {
                downloads[task.taskIdentifier]?.also { it.cancellationRequested = true }
            } ?: return
            task.cancelByProducingResumeData { resumeData ->
                persistResumeData(context.resumeDataKey, resumeData)
            }
        }

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didResumeAtOffset: Long,
            expectedTotalBytes: Long,
        ) {
            val context = withLock { downloads[downloadTask.taskIdentifier] } ?: return
            context.resumeOffset = didResumeAtOffset.coerceAtLeast(0L)
            context.resumedExpectedBytes = expectedTotalBytes
            if (context.resumeOffset == 0L) clearResumeData(context.resumeDataKey)
            context.onProgress(context.resumeOffset, expectedTotalBytes)
        }

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didWriteData: Long,
            totalBytesWritten: Long,
            totalBytesExpectedToWrite: Long,
        ) {
            val context = withLock { downloads[downloadTask.taskIdentifier] }
            context ?: return
            val downloaded = context.resumeOffset + totalBytesWritten
            val expected = context.resumedExpectedBytes.takeIf { it > 0L }
                ?: totalBytesExpectedToWrite
            context.onProgress(downloaded, expected)
        }

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL,
        ) {
            val context = withLock { downloads[downloadTask.taskIdentifier] } ?: return
            val status = (downloadTask.response as? NSHTTPURLResponse)?.statusCode?.toInt()
            val downloadedPath = didFinishDownloadingToURL.path
            if (status in 200..299 && downloadedPath != null) {
                context.stagingPath = stageDownload(downloadedPath, context.destination)
            }
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val context = withLock { downloads.remove(task.taskIdentifier) } ?: return
            val stagingPath = context.stagingPath
            val result = if (didCompleteWithError == null && stagingPath != null) {
                promoteDownload(
                    staging = stagingPath,
                    destination = context.destination,
                    expectedSize = context.expectedSize,
                    expectedSha256 = context.expectedSha256,
                )
            } else {
                stagingPath?.let { fileManager.removeItemAtPath(it, error = null) }
                null
            }
            val errorResumeData = didCompleteWithError
                ?.userInfo
                ?.get(NSURLSessionDownloadTaskResumeData) as? NSData
            when {
                result != null -> clearResumeData(context.resumeDataKey)
                context.cancellationRequested -> Unit
                errorResumeData != null -> persistResumeData(context.resumeDataKey, errorResumeData)
                else -> clearResumeData(context.resumeDataKey)
            }
            if (context.continuation.isActive) context.continuation.resume(result)
        }

        private fun <T> withLock(block: () -> T): T {
            lock.lock()
            return try {
                block()
            } finally {
                lock.unlock()
            }
        }
    }
}

private class Sha256Digest {
    private val state = intArrayOf(
        0x6a09e667,
        0xbb67ae85u.toInt(),
        0x3c6ef372,
        0xa54ff53au.toInt(),
        0x510e527f,
        0x9b05688cu.toInt(),
        0x1f83d9ab,
        0x5be0cd19,
    )
    private val block = ByteArray(64)
    private var blockSize = 0
    private var byteCount = 0L

    fun update(bytes: ByteArray) {
        bytes.forEach { byte ->
            block[blockSize++] = byte
            byteCount++
            if (blockSize == block.size) {
                processBlock()
                blockSize = 0
            }
        }
    }

    fun hex(): String {
        val messageBytes = byteCount
        update(byteArrayOf(0x80.toByte()))
        while (blockSize != 56) update(byteArrayOf(0))
        val bitCount = messageBytes * 8
        update(ByteArray(8) { index -> (bitCount ushr (56 - index * 8)).toByte() })
        return state.joinToString("") { value ->
            value.toUInt().toString(16).padStart(8, '0')
        }
    }

    private fun processBlock() {
        val words = IntArray(64)
        for (index in 0 until 16) {
            val offset = index * 4
            words[index] =
                ((block[offset].toInt() and 0xff) shl 24) or
                ((block[offset + 1].toInt() and 0xff) shl 16) or
                ((block[offset + 2].toInt() and 0xff) shl 8) or
                (block[offset + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val s0 = rotate(words[index - 15], 7) xor rotate(words[index - 15], 18) xor
                (words[index - 15] ushr 3)
            val s1 = rotate(words[index - 2], 17) xor rotate(words[index - 2], 19) xor
                (words[index - 2] ushr 10)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        for (index in 0 until 64) {
            val upper = rotate(e, 6) xor rotate(e, 11) xor rotate(e, 25)
            val choose = (e and f) xor (e.inv() and g)
            val temporary1 = h + upper + choose + SHA256_CONSTANTS[index] + words[index]
            val lower = rotate(a, 2) xor rotate(a, 13) xor rotate(a, 22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temporary2 = lower + majority
            h = g
            g = f
            f = e
            e = d + temporary1
            d = c
            c = b
            b = a
            a = temporary1 + temporary2
        }
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    private fun rotate(value: Int, bits: Int): Int =
        (value ushr bits) or (value shl (32 - bits))
}

private val SHA256_CONSTANTS = uintArrayOf(
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u,
    0x923f82a4u, 0xab1c5ed5u, 0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u,
    0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u, 0xe49b69c1u, 0xefbe4786u,
    0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u,
    0x06ca6351u, 0x14292967u, 0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u,
    0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u, 0xa2bfe8a1u, 0xa81a664bu,
    0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au,
    0x5b9cca4fu, 0x682e6ff3u, 0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u,
    0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
).map(UInt::toInt).toIntArray()

private val BUNDLED_ASSET_NAMES = mapOf(
    "manifest.json" to "manifest.json",
    "topics.db" to "topics.db",
    "databases/news.db" to "news.db",
    "databases/quranic_duas.db" to "quranic_duas.db",
    "databases/fortress_of_the_muslim.db" to "fortress_of_the_muslim_v2.db",
    "databases/fortress_of_the_muslim_v2.db" to "fortress_of_the_muslim_v2.db",
    "databases/quran/quran.db" to "quran.db",
    "databases/quran/quran_en.db" to "quran_en.db",
    "databases/hadith/sahih_bukhari.db" to "sahih_bukhari.db",
)
