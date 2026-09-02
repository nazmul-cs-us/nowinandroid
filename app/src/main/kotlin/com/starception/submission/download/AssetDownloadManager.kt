package com.starception.submission.download

import android.content.Context
import android.util.Log
import com.starception.submission.core.assetcache.AssetSource
import com.starception.submission.core.assetcache.CloudAssetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Singleton
class AssetDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val sharedAssets = CloudAssetRepository(AndroidAssetPlatform(context, okHttpClient))
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
    private var cachedManifest: AssetManifest? = null

    /**
     * Assets whose SHA-256 has already matched the manifest in this process. Hashing re-reads the
     * whole file, so each asset earns its way onto this list once and is then trusted.
     */
    private val verifiedChecksums: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    // Global download progress tracking (persists across ViewModel lifecycles)
    private val _globalDownloadProgress = MutableStateFlow(0f)
    val globalDownloadProgress: StateFlow<Float> = _globalDownloadProgress.asStateFlow()

    private val _isGloballyDownloading = MutableStateFlow(false)
    val isGloballyDownloading: StateFlow<Boolean> = _isGloballyDownloading.asStateFlow()

    private val _globalDownloadLabel = MutableStateFlow("")
    val globalDownloadLabel: StateFlow<String> = _globalDownloadLabel.asStateFlow()

    // Emits a category key whenever its download completes successfully. Lets a coordinator rebuild
    // derived content (e.g. news.db) without baking content-specific rules into this manager.
    private val _categoryCompleted = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val categoryCompleted: SharedFlow<String> = _categoryCompleted.asSharedFlow()

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
            // Try common subdirectories as fallback
            if (!cdnKey.contains("/")) {
                try {
                    context.assets.open("databases/$cdnKey").use { it.available() > 0 }
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        }
    }

    fun getAssetFile(cdnKey: String): File? {
        val file = File(cdnAssetsDir, cdnKey)
        if (!file.exists() || file.length() == 0L) return null
        if (!hasExpectedSize(file, cdnKey)) return null
        return file
    }

    /**
     * True when [file] is exactly the size the manifest says it should be.
     *
     * Size and checksum are verified as the file lands, then never looked at again — so a file
     * truncated *after* that (interrupted write, storage pressure, a half-finished copy) stayed
     * "available" indefinitely and surfaced later as corrupt content, far from the cause.
     * Comparing [File.length] is free, and truncation is the realistic corruption mode.
     *
     * A file whose size is unknown — manifest not fetched yet, or asset not listed in it — is
     * accepted. Reporting those as missing would make every asset look absent on an offline
     * launch, which is worse than missing a rare corruption.
     */
    private fun hasExpectedSize(file: File, cdnKey: String): Boolean {
        val expected = cachedManifest?.assets?.get(cdnKey)?.size ?: return true
        if (file.length() == expected) return true
        Log.w(
            TAG,
            "Size mismatch for $cdnKey: expected $expected bytes, found ${file.length()} — " +
                "treating as unavailable so it is re-downloaded",
        )
        return false
    }

    /**
     * Full SHA-256 check of a downloaded asset against the manifest, run at most once per asset
     * per process. On mismatch the file is deleted so the next download re-fetches it.
     *
     * Reads the whole file, so this is for the repair path — when something already looks wrong —
     * not for routine availability checks. [getAssetFile]'s size comparison is the cheap check
     * that runs everywhere.
     *
     * Returns false only when the asset is *known* to be corrupt. A missing file, a missing
     * manifest entry, or an unreadable file all return true: none of those are this check's
     * question to answer, and callers use it to decide whether re-using a file is safe.
     */
    suspend fun isChecksumValid(cdnKey: String): Boolean = withContext(Dispatchers.IO) {
        if (verifiedChecksums.contains(cdnKey)) return@withContext true

        val entry = cachedManifest?.assets?.get(cdnKey) ?: return@withContext true
        val file = File(cdnAssetsDir, cdnKey)
        if (!file.exists()) return@withContext true

        val actual = try {
            sha256(file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not hash $cdnKey, leaving it alone: ${e.message}")
            return@withContext true
        }

        if (actual == entry.sha256) {
            verifiedChecksums.add(cdnKey)
            return@withContext true
        }

        Log.e(TAG, "Checksum mismatch for $cdnKey — deleting so it is re-downloaded")
        file.delete()
        getOrCreateStateFlow(cdnKey).value = DownloadState.NotStarted
        false
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

        if (sharedAssets.lookupAsset(cdnKey, manifest) != null) {
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

    private suspend fun downloadAssetInternal(
        cdnKey: String,
        manifest: AssetManifest,
        stateFlow: MutableStateFlow<DownloadState>,
    ): DownloadState {
        val entry = manifest.assets[cdnKey]
            ?: return DownloadState.Failed("Asset not in manifest: $cdnKey").also {
                stateFlow.value = it
            }

        stateFlow.value = DownloadState.Downloading(0f, 0L, entry.size)
        var lastReportedProgress = -1f
        val resolved = sharedAssets.resolveAsset(cdnKey, manifest) { progress ->
            val fraction = progress.fraction
            if (fraction - lastReportedProgress >= PROGRESS_UPDATE_INTERVAL || fraction >= 1f) {
                stateFlow.value = DownloadState.Downloading(
                    progress = fraction,
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes,
                )
                _globalDownloadProgress.value = fraction
                lastReportedProgress = fraction
            }
        }

        return if (resolved != null) {
            if (resolved.source == AssetSource.DOWNLOAD) {
                Log.i(TAG, "Downloaded $cdnKey (${entry.size / 1024}KB)")
            }
            DownloadState.Completed.also { stateFlow.value = it }
        } else {
            DownloadState.Failed("Unable to resolve asset: $cdnKey").also {
                stateFlow.value = it
            }
        }
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
            var currentAsset: String? = null
            var currentAssetOffset = 0L
            var lastAggregateBytes = 0L
            var lastReportedProgress = -1f
            val result = sharedAssets.downloadCategory(category, manifest) { progress ->
                val assetChanged = progress.currentAsset != null &&
                    progress.currentAsset != currentAsset
                progress.currentAsset?.let { cdnKey ->
                    if (cdnKey != currentAsset) {
                        currentAsset = cdnKey
                        currentAssetOffset = lastAggregateBytes
                    }
                    manifest.assets[cdnKey]?.let { entry ->
                        val assetBytes = (progress.bytesDownloaded - currentAssetOffset)
                            .coerceIn(0L, entry.size)
                        val assetProgress = if (entry.size > 0L) {
                            assetBytes.toFloat() / entry.size
                        } else {
                            1f
                        }
                        getOrCreateStateFlow(cdnKey).value = DownloadState.Downloading(
                            progress = assetProgress,
                            bytesDownloaded = assetBytes,
                            totalBytes = entry.size,
                        )
                    }
                }

                val fraction = progress.fraction
                if (
                    fraction - lastReportedProgress >= PROGRESS_UPDATE_INTERVAL ||
                    fraction >= 1f ||
                    assetChanged
                ) {
                    _globalDownloadProgress.value = fraction
                    onProgress?.invoke(fraction, progress.bytesDownloaded, progress.totalBytes)
                    lastReportedProgress = fraction
                }
                lastAggregateBytes = progress.bytesDownloaded
            }

            result.resolvedAssets.forEach {
                getOrCreateStateFlow(it.cdnKey).value = DownloadState.Completed
            }
            result.missingAssets.forEach { cdnKey ->
                getOrCreateStateFlow(cdnKey).value =
                    DownloadState.Failed("Unable to resolve asset: $cdnKey")
            }
            if (result.isComplete) _categoryCompleted.tryEmit(category)
            return@withContext result.isComplete
        } finally {
            endGlobalDownload()
        }
    }

    suspend fun loadManifest(): AssetManifest? = withContext(Dispatchers.IO) {
        cachedManifest?.let { return@withContext it }
        sharedAssets.loadManifest()?.also { manifest ->
            cachedManifest = manifest
            Log.i(TAG, "Loaded shared Cloudflare manifest (version=${manifest.version})")
        }
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
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL = 0.01f
    }
}
