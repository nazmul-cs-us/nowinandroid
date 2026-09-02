package com.starception.submission.core.assetcache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AssetSource {
    CACHE,
    DOWNLOAD,
    BUNDLE,
}

data class ResolvedAsset(
    val cdnKey: String,
    val absolutePath: String,
    val source: AssetSource,
)

data class AssetDownloadProgress(
    val cdnKey: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else
            (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
}

data class CategoryDownloadProgress(
    val category: String,
    val currentAsset: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val completedFiles: Int,
    val totalFiles: Int,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 1f else
            (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
}

data class CategoryDownloadResult(
    val category: String,
    val resolvedAssets: List<ResolvedAsset>,
    val missingAssets: List<String>,
) {
    val isComplete: Boolean get() = missingAssets.isEmpty()
}

data class CategoryAssetStatus(
    val category: String,
    val downloadedBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long,
    val downloadedFiles: Int,
    val availableFiles: Int,
    val totalFiles: Int,
) {
    val isDownloaded: Boolean get() = totalFiles > 0 && downloadedFiles == totalFiles
    val isAvailable: Boolean get() = totalFiles > 0 && availableFiles == totalFiles
}

/**
 * Platform-neutral manifest and cloud asset policy.
 *
 * Manifest loading prefers remote, then the disk cache, then the app bundle. Asset resolution
 * accepts only a size- and SHA-verified cached file, otherwise downloads and validates a new file,
 * and finally falls back to the bundled copy.
 */
class CloudAssetRepository(
    private val platform: AssetPlatform,
    private val manifestPath: String = DEFAULT_MANIFEST_PATH,
    private val remoteManifestUrl: String? = null,
) {
    private val manifestMutex = Mutex()
    private val assetMutexesMutex = Mutex()
    private val assetMutexes = mutableMapOf<String, Mutex>()
    private var cachedManifest: AssetManifest? = null

    suspend fun loadManifest(forceRefresh: Boolean = false): AssetManifest? =
        manifestMutex.withLock {
            if (!forceRefresh) cachedManifest?.let { return@withLock it }

            // The bundled copy supplies the default endpoint, but is selected only after remote
            // and cached manifests fail.
            val bundledJson = try {
                platform.readBundledText(manifestPath)
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                null
            }
            val bundledManifest = bundledJson?.let(::parseManifest)
            val manifestUrl = remoteManifestUrl ?: bundledManifest?.let {
                "${it.baseUrl.trimEnd('/')}/${manifestPath.trimStart('/')}"
            }

            if (manifestUrl != null) {
                val remoteJson = try {
                    platform.fetchText(manifestUrl)
                } catch (error: Exception) {
                    error.rethrowIfCancellation()
                    null
                }
                val remoteManifest = remoteJson?.let(::parseManifest)
                if (remoteManifest != null) {
                    try {
                        platform.writeCachedText(manifestPath, remoteJson)
                    } catch (error: Exception) {
                        error.rethrowIfCancellation()
                        // A cache write failure must not discard a valid remote manifest.
                    }
                    cachedManifest = remoteManifest
                    return@withLock remoteManifest
                }
            }

            val diskJson = try {
                platform.readCachedText(manifestPath)
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                null
            }
            val diskManifest = diskJson?.let(::parseManifest)
            if (diskManifest != null) {
                cachedManifest = diskManifest
                return@withLock diskManifest
            }

            cachedManifest = bundledManifest
            bundledManifest
        }

    fun getCachedManifest(): AssetManifest? = cachedManifest

    fun clearMemoryCache() {
        cachedManifest = null
    }

    suspend fun resolveAsset(
        cdnKey: String,
        manifest: AssetManifest? = null,
        onProgress: ((AssetDownloadProgress) -> Unit)? = null,
    ): ResolvedAsset? {
        val effectiveManifest = manifest ?: loadManifest()
        val entry = effectiveManifest?.assets?.get(cdnKey)
            ?: return bundledAsset(cdnKey)

        return mutexFor(cdnKey).withLock {
            resolveKnownAsset(entry, effectiveManifest, onProgress)
        }
    }

    /** Finds an existing valid cached or bundled asset without starting a download. */
    suspend fun lookupAsset(
        cdnKey: String,
        manifest: AssetManifest? = null,
    ): ResolvedAsset? {
        val effectiveManifest = manifest ?: loadManifest()
        val entry = effectiveManifest?.assets?.get(cdnKey)
            ?: return bundledAsset(cdnKey)

        return mutexFor(cdnKey).withLock {
            validCachedAsset(entry) ?: bundledAsset(cdnKey)
        }
    }

    suspend fun downloadCategory(
        category: String,
        manifest: AssetManifest? = null,
        onProgress: ((CategoryDownloadProgress) -> Unit)? = null,
    ): CategoryDownloadResult {
        val effectiveManifest = manifest ?: loadManifest()
            ?: return CategoryDownloadResult(category, emptyList(), emptyList())
        val assets = effectiveManifest.getAssetsByCategory(category)
        val totalBytes = assets.sumOf { it.size }
        val resolved = mutableListOf<ResolvedAsset>()
        val missing = mutableListOf<String>()
        var completedBytes = 0L

        emitCategoryProgress(
            onProgress,
            CategoryDownloadProgress(category, null, 0L, totalBytes, 0, assets.size),
        )

        assets.forEach { entry ->
            // A category action means "make available", not "copy every bundled file to cache".
            val result = lookupAsset(entry.cdnKey, effectiveManifest) ?: resolveAsset(
                cdnKey = entry.cdnKey,
                manifest = effectiveManifest,
            ) { assetProgress ->
                emitCategoryProgress(
                    onProgress,
                    CategoryDownloadProgress(
                        category = category,
                        currentAsset = entry.cdnKey,
                        bytesDownloaded = completedBytes +
                            assetProgress.bytesDownloaded.coerceIn(0L, entry.size),
                        totalBytes = totalBytes,
                        completedFiles = resolved.size,
                        totalFiles = assets.size,
                    ),
                )
            }
            if (result != null) {
                resolved += result
                completedBytes += entry.size
            } else {
                missing += entry.cdnKey
            }
            emitCategoryProgress(
                onProgress,
                CategoryDownloadProgress(
                    category = category,
                    currentAsset = entry.cdnKey,
                    bytesDownloaded = completedBytes,
                    totalBytes = totalBytes,
                    completedFiles = resolved.size,
                    totalFiles = assets.size,
                ),
            )
        }

        return CategoryDownloadResult(category, resolved, missing)
    }

    suspend fun deleteAsset(cdnKey: String): Boolean =
        mutexFor(cdnKey).withLock { platform.deleteCachedAsset(cdnKey) }

    suspend fun deleteCategory(
        category: String,
        manifest: AssetManifest? = null,
    ): Int {
        val effectiveManifest = manifest ?: loadManifest() ?: return 0
        var deleted = 0
        effectiveManifest.getAssetsByCategory(category).forEach {
            if (deleteAsset(it.cdnKey)) deleted++
        }
        return deleted
    }

    suspend fun getCategoryStatus(
        category: String,
        manifest: AssetManifest? = null,
    ): CategoryAssetStatus {
        val effectiveManifest = manifest ?: loadManifest()
        val assets = effectiveManifest?.getAssetsByCategory(category).orEmpty()
        var downloadedBytes = 0L
        var availableBytes = 0L
        var downloadedFiles = 0
        var availableFiles = 0

        assets.forEach { entry ->
            // Settings refreshes should not hash every cached byte. Full SHA validation remains
            // mandatory in lookupAsset/resolveAsset before a feature consumes the file.
            val cached = lightweightCachedAsset(entry)
            if (cached != null) {
                availableBytes += entry.size
                availableFiles++
                downloadedBytes += entry.size
                downloadedFiles++
            } else if (bundledAsset(entry.cdnKey) != null) {
                availableBytes += entry.size
                availableFiles++
            }
        }

        return CategoryAssetStatus(
            category = category,
            downloadedBytes = downloadedBytes,
            availableBytes = availableBytes,
            totalBytes = assets.sumOf { it.size },
            downloadedFiles = downloadedFiles,
            availableFiles = availableFiles,
            totalFiles = assets.size,
        )
    }

    suspend fun getCategoryDownloadedSize(
        category: String,
        manifest: AssetManifest? = null,
    ): Long {
        val effectiveManifest = manifest ?: loadManifest() ?: return 0L
        var size = 0L
        effectiveManifest.getAssetsByCategory(category).forEach { entry ->
            if (lookupAsset(entry.cdnKey, effectiveManifest) != null) size += entry.size
        }
        return size
    }

    suspend fun isCategoryComplete(
        category: String,
        manifest: AssetManifest? = null,
    ): Boolean {
        val effectiveManifest = manifest ?: loadManifest() ?: return false
        return effectiveManifest.getAssetsByCategory(category)
            .all { lookupAsset(it.cdnKey, effectiveManifest) != null }
    }

    private suspend fun resolveKnownAsset(
        entry: AssetEntry,
        manifest: AssetManifest,
        onProgress: ((AssetDownloadProgress) -> Unit)?,
    ): ResolvedAsset? {
        validCachedAsset(entry)?.let { return it }

        var temporaryPath: String? = null
        try {
            temporaryPath = platform.downloadToTemporaryFile(
                url = manifest.getAssetUrl(entry.cdnKey),
                cdnKey = entry.cdnKey,
            ) { bytesDownloaded, totalBytes ->
                emitAssetProgress(
                    onProgress,
                    AssetDownloadProgress(
                        cdnKey = entry.cdnKey,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes.takeIf { it > 0L } ?: entry.size,
                    ),
                )
            }

            if (temporaryPath != null && isValid(temporaryPath, entry)) {
                val committedPath = platform.commitDownloadedAsset(temporaryPath, entry.cdnKey)
                emitAssetProgress(
                    onProgress,
                    AssetDownloadProgress(entry.cdnKey, entry.size, entry.size),
                )
                return ResolvedAsset(entry.cdnKey, committedPath, AssetSource.DOWNLOAD)
            }
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            // Resolution continues with the bundled fallback.
        }

        temporaryPath?.let {
            try {
                platform.deleteFile(it)
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                // Best-effort temporary-file cleanup.
            }
        }
        return bundledAsset(entry.cdnKey)
    }

    private suspend fun validCachedAsset(entry: AssetEntry): ResolvedAsset? {
        val path = try {
            platform.cachedAssetPath(entry.cdnKey)
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            null
        } ?: return null

        if (isValid(path, entry)) {
            return ResolvedAsset(entry.cdnKey, path, AssetSource.CACHE)
        }

        try {
            platform.deleteCachedAsset(entry.cdnKey)
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            // An invalid cache entry is never returned, even if cleanup fails.
        }
        return null
    }

    private suspend fun lightweightCachedAsset(entry: AssetEntry): ResolvedAsset? {
        val path = try {
            platform.cachedAssetPath(entry.cdnKey)
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            null
        } ?: return null

        val hasExpectedSize = try {
            platform.fileSize(path) == entry.size
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            false
        }
        return path.takeIf { hasExpectedSize }?.let {
            ResolvedAsset(entry.cdnKey, it, AssetSource.CACHE)
        }
    }

    private suspend fun isValid(absolutePath: String, entry: AssetEntry): Boolean = try {
        platform.fileSize(absolutePath) == entry.size &&
            platform.sha256(absolutePath)?.equals(entry.sha256, ignoreCase = true) == true
    } catch (error: Exception) {
        error.rethrowIfCancellation()
        false
    }

    private suspend fun bundledAsset(cdnKey: String): ResolvedAsset? = try {
        platform.bundledAssetPath(cdnKey)?.let {
            ResolvedAsset(cdnKey, it, AssetSource.BUNDLE)
        }
    } catch (error: Exception) {
        error.rethrowIfCancellation()
        null
    }

    private suspend fun mutexFor(cdnKey: String): Mutex = assetMutexesMutex.withLock {
        assetMutexes.getOrPut(cdnKey) { Mutex() }
    }

    private fun parseManifest(json: String): AssetManifest? = try {
        AssetManifest.fromJson(json)
    } catch (_: Exception) {
        null
    }

    private fun emitAssetProgress(
        callback: ((AssetDownloadProgress) -> Unit)?,
        progress: AssetDownloadProgress,
    ) {
        try {
            callback?.invoke(progress)
        } catch (_: Exception) {
            // Observers cannot interrupt storage work.
        }
    }

    private fun emitCategoryProgress(
        callback: ((CategoryDownloadProgress) -> Unit)?,
        progress: CategoryDownloadProgress,
    ) {
        try {
            callback?.invoke(progress)
        } catch (_: Exception) {
            // Observers cannot interrupt storage work.
        }
    }

    private companion object {
        const val DEFAULT_MANIFEST_PATH = "manifest.json"
    }
}

private fun Exception.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
