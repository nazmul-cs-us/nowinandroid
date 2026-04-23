package com.starception.submission.download

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    sealed class DownloadState {
        data object NotStarted : DownloadState()
        data class Downloading(
            val progress: Float,
            val bytesDownloaded: Long,
            val totalBytes: Long,
        ) : DownloadState()
        data object Completed : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }

    private val cdnAssetsDir = File(context.filesDir, CDN_ASSETS_DIR)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()
    private val downloadMutexes = mutableMapOf<String, Mutex>()
    private var cachedManifest: AssetManifest? = null

    private fun getOrCreateMutex(cdnKey: String): Mutex =
        synchronized(downloadMutexes) { downloadMutexes.getOrPut(cdnKey) { Mutex() } }

    // Global download progress tracking (persists across ViewModel lifecycles)
    private val _globalDownloadProgress = MutableStateFlow(0f)
    val globalDownloadProgress: StateFlow<Float> = _globalDownloadProgress.asStateFlow()

    // Last emitted progress value — used to throttle StateFlow updates to ≥1% change
    // so the main thread isn't flooded with recompositions on every 8KB buffer read.
    private var lastEmittedProgress = 0f

    private val _isGloballyDownloading = MutableStateFlow(false)
    val isGloballyDownloading: StateFlow<Boolean> = _isGloballyDownloading.asStateFlow()

    private val _globalDownloadLabel = MutableStateFlow("")
    val globalDownloadLabel: StateFlow<String> = _globalDownloadLabel.asStateFlow()

    private var activeDownloadCount = 0
    private val activeDownloadLock = Any()

    private fun beginGlobalDownload() {
        synchronized(activeDownloadLock) {
            activeDownloadCount++
            _isGloballyDownloading.value = true
        }
    }

    private fun endGlobalDownload() {
        synchronized(activeDownloadLock) {
            activeDownloadCount = (activeDownloadCount - 1).coerceAtLeast(0)
            if (activeDownloadCount == 0) {
                _isGloballyDownloading.value = false
                _globalDownloadProgress.value = 0f
                _globalDownloadLabel.value = ""
                lastEmittedProgress = 0f
            }
        }
    }

    fun launchCategoryDownload(
        category: String,
        manifest: AssetManifest,
        onProgress: ((Float, Long, Long) -> Unit)? = null,
        onFinished: ((Boolean) -> Unit)? = null,
    ): Job = downloadScope.launch {
        val success = try {
            downloadCategory(category, manifest, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Background category download failed for $category", e)
            false
        }
        onFinished?.invoke(success)
    }

    init {
        cdnAssetsDir.mkdirs()
    }

    fun isAssetAvailable(cdnKey: String): Boolean {
        // First check if downloaded to cdn_assets directory
        val file = getAssetFile(cdnKey)
        if (file != null && file.exists() && file.length() > 0) {
            return true
        }

        // Also check if bundled in APK assets
        return isAssetBundled(cdnKey)
    }

    /**
     * Check if an asset is bundled in the APK's assets folder.
     */
    private fun isAssetBundled(cdnKey: String): Boolean {
        return try {
            context.assets.open(cdnKey).use { it.available() > 0 }
        } catch (e: Exception) {
            false
        }
    }

    fun getAssetFile(cdnKey: String): File? {
        val file = File(cdnAssetsDir, cdnKey)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun getAssetDir(cdnKeyPrefix: String): File? {
        val dir = File(cdnAssetsDir, cdnKeyPrefix)
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    fun getDownloadState(cdnKey: String): Flow<DownloadState> =
        getOrCreateStateFlow(cdnKey).asStateFlow()

    private fun getOrCreateStateFlow(cdnKey: String): MutableStateFlow<DownloadState> =
        downloadStates.getOrPut(cdnKey) {
            MutableStateFlow(
                if (isAssetAvailable(cdnKey)) DownloadState.Completed
                else DownloadState.NotStarted,
            )
        }

    suspend fun downloadAsset(
        cdnKey: String,
        manifest: AssetManifest,
    ): DownloadState = withContext(Dispatchers.IO) {
        val stateFlow = getOrCreateStateFlow(cdnKey)

        if (isAssetAvailable(cdnKey)) {
            stateFlow.value = DownloadState.Completed
            return@withContext DownloadState.Completed
        }

        // Prevent concurrent downloads of the same file
        val mutex = getOrCreateMutex(cdnKey)
        mutex.withLock {
            // Re-check after acquiring lock (another caller may have completed it)
            if (isAssetAvailable(cdnKey)) {
                stateFlow.value = DownloadState.Completed
                return@withContext DownloadState.Completed
            }
            val category = manifest.assets[cdnKey]?.category ?: ""
            _globalDownloadLabel.value = AssetDownloadViewModel.formatCategoryName(category)
            beginGlobalDownload()
            try {
                downloadAssetInternal(cdnKey, manifest, stateFlow)
            } finally {
                endGlobalDownload()
            }
        }
    }

    private suspend fun downloadAssetInternal(
        cdnKey: String,
        manifest: AssetManifest,
        stateFlow: MutableStateFlow<DownloadState>,
        // When called from downloadCategory, batchOffset + perFileProgress*batchScale
        // gives smooth cumulative progress instead of per-file 0→1 resets.
        batchOffset: Float = 0f,
        batchScale: Float = 1f,
    ): DownloadState {

        val entry = manifest.assets[cdnKey]
            ?: return DownloadState.Failed("Asset not in manifest: $cdnKey").also {
                stateFlow.value = it
            }

        val url = manifest.getAssetUrl(cdnKey)
        val targetFile = File(cdnAssetsDir, cdnKey)
        val tmpFile = File(cdnAssetsDir, "$cdnKey.tmp")

        targetFile.parentFile?.mkdirs()

        var lastError: String? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                stateFlow.value = DownloadState.Downloading(0f, 0, entry.size)

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code}: ${response.message}"
                    Log.w(TAG, "Download attempt $attempt failed for $cdnKey: $lastError")
                    response.close()
                    if (attempt < MAX_RETRIES) {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS * attempt)
                    }
                    continue
                }

                val body = response.body ?: run {
                    lastError = "Empty response body"
                    response.close()
                    continue
                }

                val totalBytes = body.contentLength().let {
                    if (it > 0) it else entry.size
                }

                var bytesDownloaded = 0L
                FileOutputStream(tmpFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            val progress = if (totalBytes > 0) {
                                bytesDownloaded.toFloat() / totalBytes
                            } else 0f
                            val clampedProgress = progress.coerceIn(0f, 1f)
                            val globalProgress = (batchOffset + clampedProgress * batchScale).coerceIn(0f, 1f)
                            // Only emit when global progress advances by ≥1% to avoid
                            // flooding the main thread with recompositions on every 8KB chunk.
                            if (globalProgress - lastEmittedProgress >= 0.01f || globalProgress >= 1f) {
                                stateFlow.value = DownloadState.Downloading(
                                    clampedProgress,
                                    bytesDownloaded,
                                    totalBytes,
                                )
                                _globalDownloadProgress.value = globalProgress
                                lastEmittedProgress = globalProgress
                            }
                        }
                    }
                }

                response.close()

                // Verify download size before checksum
                val actualSize = tmpFile.length()
                if (actualSize != entry.size) {
                    tmpFile.delete()
                    lastError = "Size mismatch: expected ${entry.size} bytes, got $actualSize bytes"
                    Log.w(TAG, "Incomplete download for $cdnKey: $lastError")
                    if (attempt < MAX_RETRIES) {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS * attempt)
                    }
                    continue
                }

                // Verify SHA-256
                val actualHash = sha256(tmpFile)
                if (actualHash != entry.sha256) {
                    tmpFile.delete()
                    lastError = "SHA-256 mismatch: expected ${entry.sha256}, got $actualHash"
                    Log.w(TAG, "Checksum failed for $cdnKey: $lastError")
                    if (attempt < MAX_RETRIES) {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS * attempt)
                    }
                    continue
                }

                // Atomic rename
                if (tmpFile.renameTo(targetFile)) {
                    Log.i(TAG, "Downloaded $cdnKey (${bytesDownloaded / 1024}KB)")
                    stateFlow.value = DownloadState.Completed
                    return DownloadState.Completed
                } else {
                    tmpFile.delete()
                    lastError = "Failed to rename temp file"
                }
            } catch (e: Exception) {
                tmpFile.delete()
                lastError = e.message ?: "Unknown error"
                Log.e(TAG, "Download error for $cdnKey (attempt $attempt)", e)
                if (attempt < MAX_RETRIES) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS * attempt)
                }
            }
        }

        val finalError = lastError ?: "Unknown error"
        stateFlow.value = DownloadState.Failed(finalError)
        return DownloadState.Failed(finalError)
    }

    suspend fun downloadCategory(
        category: String,
        manifest: AssetManifest,
        onProgress: ((Float, Long, Long) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val assets = manifest.getAssetsByCategory(category)
        if (assets.isEmpty()) return@withContext true

        _globalDownloadLabel.value = AssetDownloadViewModel.formatCategoryName(category)
        beginGlobalDownload()

        try {
            val totalBytes = assets.sumOf { it.size }
            var downloadedBytes = 0L
            var allSuccess = true

            for (asset in assets) {
                if (isAssetAvailable(asset.cdnKey)) {
                    downloadedBytes += asset.size
                    val progress = downloadedBytes.toFloat() / totalBytes
                    _globalDownloadProgress.value = progress
                    onProgress?.invoke(progress, downloadedBytes, totalBytes)
                    continue
                }

                // Calculate where this file sits in the overall batch so progress
                // advances smoothly from batchOffset to batchOffset+batchScale
                // instead of resetting 0→1 for every individual file.
                val batchOffset = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                val batchScale = if (totalBytes > 0) asset.size.toFloat() / totalBytes else 1f

                val stateFlow = getOrCreateStateFlow(asset.cdnKey)
                val mutex = getOrCreateMutex(asset.cdnKey)
                val result = mutex.withLock {
                    if (isAssetAvailable(asset.cdnKey)) {
                        stateFlow.value = DownloadState.Completed
                        DownloadState.Completed
                    } else {
                        downloadAssetInternal(asset.cdnKey, manifest, stateFlow, batchOffset, batchScale)
                    }
                }

                if (result is DownloadState.Completed) {
                    downloadedBytes += asset.size
                } else {
                    allSuccess = false
                }
                val progress = downloadedBytes.toFloat() / totalBytes
                _globalDownloadProgress.value = progress
                onProgress?.invoke(progress, downloadedBytes, totalBytes)
            }

            return@withContext allSuccess
        } finally {
            endGlobalDownload()
        }
    }

    suspend fun loadManifest(): AssetManifest? = withContext(Dispatchers.IO) {
        cachedManifest?.let { return@withContext it }

        val cachedFile = File(cdnAssetsDir, "manifest.json")

        // 1. Try fetching latest manifest from CDN so hashes are always in sync with served files
        try {
            val bundledBaseUrl = context.assets.open(MANIFEST_ASSET_PATH).bufferedReader().use {
                val json = it.readText()
                AssetManifest.fromJson(json).baseUrl
            }
            val cdnManifestUrl = "${bundledBaseUrl.trimEnd('/')}/$MANIFEST_ASSET_PATH"
            val request = okhttp3.Request.Builder().url(cdnManifestUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                if (!json.isNullOrBlank()) {
                    val manifest = AssetManifest.fromJson(json)
                    cachedFile.parentFile?.mkdirs()
                    cachedFile.writeText(json)
                    cachedManifest = manifest
                    Log.i(TAG, "Loaded manifest from CDN (version=${manifest.version})")
                    return@withContext manifest
                }
            }
            response.close()
            Log.w(TAG, "CDN manifest fetch failed: HTTP ${response.code}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch manifest from CDN, falling back: ${e.message}")
        }

        // 2. Fall back to disk-cached manifest (from a previous successful CDN fetch)
        if (cachedFile.exists()) {
            try {
                val json = cachedFile.readText()
                val manifest = AssetManifest.fromJson(json)
                cachedManifest = manifest
                Log.i(TAG, "Loaded manifest from disk cache (version=${manifest.version})")
                return@withContext manifest
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached manifest", e)
            }
        }

        // 3. Last resort: bundled manifest in APK (may be stale, but better than nothing)
        try {
            val json = context.assets.open(MANIFEST_ASSET_PATH).bufferedReader().readText()
            val manifest = AssetManifest.fromJson(json)
            cachedManifest = manifest
            Log.w(TAG, "Loaded bundled manifest (version=${manifest.version}) — hashes may be stale")
            return@withContext manifest
        } catch (e: Exception) {
            Log.e(TAG, "No manifest available", e)
        }

        return@withContext null
    }

    fun deleteAsset(cdnKey: String): Boolean {
        val file = File(cdnAssetsDir, cdnKey)
        val deleted = file.delete()
        if (deleted) {
            getOrCreateStateFlow(cdnKey).value = DownloadState.NotStarted
        }
        return deleted
    }

    fun deleteCategory(category: String, manifest: AssetManifest) {
        manifest.getAssetsByCategory(category).forEach { deleteAsset(it.cdnKey) }
    }

    fun getCategoryDownloadedSize(category: String, manifest: AssetManifest): Long =
        manifest.getAssetsByCategory(category)
            .filter { isAssetAvailable(it.cdnKey) }
            .sumOf { it.size }

    fun isCategoryComplete(category: String, manifest: AssetManifest): Boolean =
        manifest.getAssetsByCategory(category).all { isAssetAvailable(it.cdnKey) }

    /**
     * Check if all assets in a category are bundled in the APK.
     * Bundled categories should be hidden from the Content & Storage settings
     * since users cannot delete or re-download them.
     */
    fun isCategoryFullyBundled(category: String, manifest: AssetManifest): Boolean =
        manifest.getAssetsByCategory(category).all { isAssetBundled(it.cdnKey) }

    fun hasEssentialAssets(manifest: AssetManifest): Boolean =
        manifest.getRequiredAssets().all { isAssetAvailable(it.cdnKey) }

    fun getCachedManifest(): AssetManifest? = cachedManifest

    fun getTotalDownloadedSize(): Long {
        var total = 0L
        cdnAssetsDir.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "AssetDownloadManager"
        private const val CDN_ASSETS_DIR = "cdn_assets"
        private const val MANIFEST_ASSET_PATH = "manifest.json"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val BUFFER_SIZE = 8192
    }
}
