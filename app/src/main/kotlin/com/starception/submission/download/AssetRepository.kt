package com.starception.submission.download

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified asset access layer. Checks for downloaded CDN assets first,
 * then falls back to bundled APK assets for files that are still bundled.
 */
@Singleton
class AssetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: AssetDownloadManager,
) {
    /**
     * Get a database file, preferring CDN-downloaded version.
     * Returns the File if available, null if not yet downloaded.
     */
    fun getDatabaseFile(cdnKey: String): File? {
        // Check CDN download first
        val cdnFile = downloadManager.getAssetFile(cdnKey)
        if (cdnFile != null) return cdnFile

        // Fall back to bundled asset by extracting to internal storage
        return tryExtractBundledAsset(cdnKey)
    }

    /**
     * Open an asset as InputStream, preferring CDN-downloaded version.
     */
    fun openAsset(cdnKey: String): InputStream? {
        // Check CDN download first
        val cdnFile = downloadManager.getAssetFile(cdnKey)
        if (cdnFile != null) return cdnFile.inputStream()

        // Fall back to bundled asset
        return tryOpenBundledAsset(cdnKey)
    }

    /**
     * Get the directory where a model category's files are stored.
     */
    fun getModelDirectory(cdnKeyPrefix: String): File? =
        downloadManager.getAssetDir(cdnKeyPrefix)

    /**
     * Check if an asset is available (either downloaded or bundled).
     */
    fun isAvailable(cdnKey: String): Boolean {
        if (downloadManager.isAssetAvailable(cdnKey)) return true
        return isBundled(cdnKey)
    }

    /**
     * Check if essential assets are ready (all required assets downloaded).
     */
    suspend fun hasEssentialAssets(): Boolean {
        val manifest = downloadManager.loadManifest() ?: return false
        return downloadManager.hasEssentialAssets(manifest)
    }

    /**
     * Get download state for UI observation.
     */
    fun getDownloadState(cdnKey: String): Flow<AssetDownloadManager.DownloadState> =
        downloadManager.getDownloadState(cdnKey)

    /**
     * Map from the CDN key back to the original bundled asset path.
     * This handles the path transformations done by generate_manifest.py.
     */
    private fun cdnKeyToBundledPath(cdnKey: String): String = when {
        cdnKey.startsWith("databases/quran/") ->
            "databases/${cdnKey.substringAfterLast('/')}"
        cdnKey.startsWith("json/") ->
            cdnKey.substringAfter("json/")
        cdnKey.startsWith("models/sherpa/") ->
            cdnKey.substringAfter("models/")
        cdnKey.startsWith("models/kws/") ->
            cdnKey.substringAfter("models/")
        cdnKey.startsWith("models/whisper/") ->
            cdnKey.substringAfter("models/")
        cdnKey.startsWith("models/tts/") ->
            cdnKey.substringAfter("models/")
        cdnKey.startsWith("models/") ->
            cdnKey
        else -> cdnKey
    }

    private fun isBundled(cdnKey: String): Boolean {
        val bundledPath = cdnKeyToBundledPath(cdnKey)
        return try {
            context.assets.open(bundledPath).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryOpenBundledAsset(cdnKey: String): InputStream? {
        val bundledPath = cdnKeyToBundledPath(cdnKey)
        return try {
            context.assets.open(bundledPath)
        } catch (_: Exception) {
            Log.d(TAG, "Asset not bundled: $cdnKey (tried $bundledPath)")
            null
        }
    }

    /**
     * Extract a bundled asset to internal storage and return the File.
     * Used as fallback when CDN download is not yet available.
     */
    private fun tryExtractBundledAsset(cdnKey: String): File? {
        val bundledPath = cdnKeyToBundledPath(cdnKey)
        val extractDir = File(context.filesDir, "extracted_assets")
        val targetFile = File(extractDir, cdnKey)

        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        return try {
            targetFile.parentFile?.mkdirs()
            context.assets.open(bundledPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (_: Exception) {
            Log.d(TAG, "Cannot extract bundled asset: $cdnKey (tried $bundledPath)")
            null
        }
    }

    companion object {
        private const val TAG = "AssetRepository"
    }
}
