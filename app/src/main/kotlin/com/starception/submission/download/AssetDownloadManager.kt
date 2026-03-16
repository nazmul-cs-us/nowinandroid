package com.starception.submission.download

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()
    private var cachedManifest: AssetManifest? = null

    // Global download progress tracking (persists across ViewModel lifecycles)
    private val _globalDownloadProgress = MutableStateFlow(0f)
    val globalDownloadProgress: StateFlow<Float> = _globalDownloadProgress.asStateFlow()

    private val _isGloballyDownloading = MutableStateFlow(false)
    val isGloballyDownloading: StateFlow<Boolean> = _isGloballyDownloading.asStateFlow()

    private var activeDownloadCount = 0
    private val activeDownloadLock = Any()

    init {
        cdnAssetsDir.mkdirs()
    }

    fun isAssetAvailable(cdnKey: String): Boolean {
        val file = getAssetFile(cdnKey)
        return file != null && file.exists() && file.length() > 0
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

        val entry = manifest.assets[cdnKey]
            ?: return@withContext DownloadState.Failed("Asset not in manifest: $cdnKey")

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
                            stateFlow.value = DownloadState.Downloading(
                                progress.coerceIn(0f, 1f),
                                bytesDownloaded,
                                totalBytes,
                            )
                        }
                    }
                }

                response.close()

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
                    return@withContext DownloadState.Completed
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
        return@withContext DownloadState.Failed(finalError)
    }

    suspend fun downloadCategory(
        category: String,
        manifest: AssetManifest,
        onProgress: ((Float, Long, Long) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val assets = manifest.getAssetsByCategory(category)
        if (assets.isEmpty()) return@withContext true

        synchronized(activeDownloadLock) {
            activeDownloadCount++
            _isGloballyDownloading.value = true
        }

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

            val result = downloadAsset(asset.cdnKey, manifest)
            if (result is DownloadState.Completed) {
                downloadedBytes += asset.size
            } else {
                allSuccess = false
            }
            val progress = downloadedBytes.toFloat() / totalBytes
            _globalDownloadProgress.value = progress
            onProgress?.invoke(progress, downloadedBytes, totalBytes)
        }

        synchronized(activeDownloadLock) {
            activeDownloadCount--
            if (activeDownloadCount <= 0) {
                activeDownloadCount = 0
                _isGloballyDownloading.value = false
                _globalDownloadProgress.value = 0f
            }
        }

        return@withContext allSuccess
    }

    suspend fun loadManifest(): AssetManifest? = withContext(Dispatchers.IO) {
        cachedManifest?.let { return@withContext it }

        // Try loading bundled manifest from assets first
        try {
            val json = context.assets.open(MANIFEST_ASSET_PATH).bufferedReader().readText()
            val manifest = AssetManifest.fromJson(json)
            cachedManifest = manifest
            return@withContext manifest
        } catch (e: Exception) {
            Log.w(TAG, "No bundled manifest found: ${e.message}")
        }

        // Try loading cached manifest from disk
        val cachedFile = File(cdnAssetsDir, "manifest.json")
        if (cachedFile.exists()) {
            try {
                val json = cachedFile.readText()
                val manifest = AssetManifest.fromJson(json)
                cachedManifest = manifest
                return@withContext manifest
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached manifest", e)
            }
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
