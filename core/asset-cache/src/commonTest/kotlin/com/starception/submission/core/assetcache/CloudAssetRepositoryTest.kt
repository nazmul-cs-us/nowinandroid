package com.starception.submission.core.assetcache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudAssetRepositoryTest {
    @Test
    fun manifestPreservesWireNamesMapKeysAndHelpers() {
        val manifest = AssetManifest.fromJson(manifestJson(version = 7))

        assertEquals("https://cdn.example", manifest.baseUrl)
        assertEquals("audio/one file!.mp3", manifest.assets.getValue("audio/one file!.mp3").cdnKey)
        assertEquals("audio", manifest.categories.getValue("audio").name)
        assertEquals(2, manifest.getAssetsByCategory("audio").size)
        assertEquals(1, manifest.getRequiredAssets().size)
        assertEquals(
            "https://cdn.example/audio/one%20file%21.mp3",
            manifest.getAssetUrl("audio/one file!.mp3"),
        )

        val encoded = manifest.toJson()
        assertTrue("\"base_url\"" in encoded)
        assertTrue("\"total_size\"" in encoded)
        assertTrue("\"file_count\"" in encoded)
        assertFalse("\"cdn_key\"" in encoded)
        assertEquals(manifest, AssetManifest.fromJson(encoded))
    }

    @Test
    fun manifestLoadingPrefersRemoteAndCachesIt() = runTest {
        val platform = FakeAssetPlatform().apply {
            bundledTexts["manifest.json"] = manifestJson(version = 1)
            remoteTexts["https://cdn.example/manifest.json"] = manifestJson(version = 2)
        }

        val manifest = CloudAssetRepository(platform).loadManifest()

        assertEquals(2, manifest?.version)
        assertEquals(manifestJson(version = 2), platform.cachedTexts["manifest.json"])
        assertEquals(listOf("https://cdn.example/manifest.json"), platform.fetchedUrls)
    }

    @Test
    fun manifestLoadingFallsBackFromInvalidRemoteToCacheThenBundle() = runTest {
        val cachedPlatform = FakeAssetPlatform().apply {
            bundledTexts["manifest.json"] = manifestJson(version = 1)
            cachedTexts["manifest.json"] = manifestJson(version = 2)
            remoteTexts["https://cdn.example/manifest.json"] = "not json"
        }
        assertEquals(2, CloudAssetRepository(cachedPlatform).loadManifest()?.version)

        val bundledPlatform = FakeAssetPlatform().apply {
            bundledTexts["manifest.json"] = manifestJson(version = 1)
            cachedTexts["manifest.json"] = "broken"
        }
        assertEquals(1, CloudAssetRepository(bundledPlatform).loadManifest()?.version)
    }

    @Test
    fun validCachedAssetWinsWithoutDownloading() = runTest {
        val platform = FakeAssetPlatform()
        platform.addCached("audio/one file!.mp3", size = 4, sha256 = "hash-one")
        val repository = CloudAssetRepository(platform)

        val result = repository.resolveAsset(
            cdnKey = "audio/one file!.mp3",
            manifest = AssetManifest.fromJson(manifestJson()),
        )

        assertEquals(AssetSource.CACHE, result?.source)
        assertEquals("/cache/audio/one file!.mp3", result?.absolutePath)
        assertTrue(platform.downloadedKeys.isEmpty())
    }

    @Test
    fun corruptCacheIsDeletedAndValidatedDownloadIsCommitted() = runTest {
        val platform = FakeAssetPlatform().apply {
            addCached("audio/one file!.mp3", size = 3, sha256 = "wrong")
            downloads["audio/one file!.mp3"] = FakeFile(4, "hash-one")
        }
        val progress = mutableListOf<AssetDownloadProgress>()

        val result = CloudAssetRepository(platform).resolveAsset(
            cdnKey = "audio/one file!.mp3",
            manifest = AssetManifest.fromJson(manifestJson()),
            onProgress = progress::add,
        )

        assertEquals(AssetSource.DOWNLOAD, result?.source)
        assertEquals("/cache/audio/one file!.mp3", result?.absolutePath)
        assertTrue("audio/one file!.mp3" in platform.deletedCachedKeys)
        assertEquals(1f, progress.last().fraction)
    }

    @Test
    fun invalidDownloadIsDeletedBeforeBundleFallback() = runTest {
        val platform = FakeAssetPlatform().apply {
            downloads["audio/one file!.mp3"] = FakeFile(4, "wrong-hash")
            addBundled("audio/one file!.mp3", size = 4, sha256 = "bundle-hash")
        }

        val result = CloudAssetRepository(platform).resolveAsset(
            cdnKey = "audio/one file!.mp3",
            manifest = AssetManifest.fromJson(manifestJson()),
        )

        assertEquals(AssetSource.BUNDLE, result?.source)
        assertTrue("/tmp/audio/one file!.mp3" in platform.deletedFiles)
    }

    @Test
    fun categoryDownloadAggregatesProgressAndReportsMissingAssets() = runTest {
        val platform = FakeAssetPlatform().apply {
            addCached("audio/one file!.mp3", size = 4, sha256 = "hash-one")
        }
        val progress = mutableListOf<CategoryDownloadProgress>()

        val result = CloudAssetRepository(platform).downloadCategory(
            category = "audio",
            manifest = AssetManifest.fromJson(manifestJson()),
            onProgress = progress::add,
        )

        assertFalse(result.isComplete)
        assertEquals(listOf("audio/two.mp3"), result.missingAssets)
        assertEquals(4L, progress.last().bytesDownloaded)
        assertEquals(10L, progress.last().totalBytes)
        assertEquals(0.4f, progress.last().fraction)
    }

    @Test
    fun categoryDownloadDoesNotDuplicateBundledAssets() = runTest {
        val platform = FakeAssetPlatform().apply {
            addBundled("audio/one file!.mp3", size = 4, sha256 = "hash-one")
            downloads["audio/two.mp3"] = FakeFile(6, "hash-two")
        }

        val result = CloudAssetRepository(platform).downloadCategory(
            category = "audio",
            manifest = AssetManifest.fromJson(manifestJson()),
        )

        assertTrue(result.isComplete)
        assertEquals(listOf("audio/two.mp3"), platform.downloadedKeys)
        assertEquals(AssetSource.BUNDLE, result.resolvedAssets.first().source)
        assertEquals(AssetSource.DOWNLOAD, result.resolvedAssets.last().source)
    }

    @Test
    fun lookupAndDeletionOperateOnlyOnCache() = runTest {
        val platform = FakeAssetPlatform().apply {
            addCached("audio/one file!.mp3", size = 4, sha256 = "hash-one")
            addBundled("audio/two.mp3", size = 6, sha256 = "hash-two")
        }
        val manifest = AssetManifest.fromJson(manifestJson())
        val repository = CloudAssetRepository(platform)

        assertNotNull(repository.lookupAsset("audio/one file!.mp3", manifest))
        assertEquals(10L, repository.getCategoryDownloadedSize("audio", manifest))
        assertTrue(repository.isCategoryComplete("audio", manifest))
        assertEquals(1, repository.deleteCategory("audio", manifest))
        assertNull(repository.lookupAsset("audio/one file!.mp3", manifest))
        assertNotNull(repository.lookupAsset("audio/two.mp3", manifest))
    }

    @Test
    fun categoryStatusSeparatesDownloadedCacheFromBundledAvailability() = runTest {
        val platform = FakeAssetPlatform().apply {
            addCached("audio/one file!.mp3", size = 4, sha256 = "hash-one")
            addBundled("audio/two.mp3", size = 6, sha256 = "hash-two")
        }

        val status = CloudAssetRepository(platform).getCategoryStatus(
            category = "audio",
            manifest = AssetManifest.fromJson(manifestJson()),
        )

        assertEquals(4L, status.downloadedBytes)
        assertEquals(10L, status.availableBytes)
        assertEquals(1, status.downloadedFiles)
        assertEquals(2, status.availableFiles)
        assertFalse(status.isDownloaded)
        assertTrue(status.isAvailable)
        assertEquals(0, platform.sha256Requests)
    }

    @Test
    fun cancellationStopsResolutionWithoutUsingBundleFallback() = runTest {
        val platform = FakeAssetPlatform().apply {
            downloadError = CancellationException("cancelled")
            addBundled("audio/one file!.mp3", size = 4, sha256 = "bundle-hash")
        }

        assertFailsWith<CancellationException> {
            CloudAssetRepository(platform).resolveAsset(
                cdnKey = "audio/one file!.mp3",
                manifest = AssetManifest.fromJson(manifestJson()),
            )
        }
    }
}

private data class FakeFile(val size: Long, val sha256: String)

private class FakeAssetPlatform : AssetPlatform {
    val remoteTexts = mutableMapOf<String, String>()
    val cachedTexts = mutableMapOf<String, String>()
    val bundledTexts = mutableMapOf<String, String>()
    val downloads = mutableMapOf<String, FakeFile>()
    val fetchedUrls = mutableListOf<String>()
    val downloadedKeys = mutableListOf<String>()
    val deletedCachedKeys = mutableListOf<String>()
    val deletedFiles = mutableListOf<String>()
    var downloadError: Exception? = null
    var sha256Requests = 0

    private val files = mutableMapOf<String, FakeFile>()
    private val cachedPaths = mutableMapOf<String, String>()
    private val bundledPaths = mutableMapOf<String, String>()

    fun addCached(cdnKey: String, size: Long, sha256: String) {
        val path = "/cache/$cdnKey"
        cachedPaths[cdnKey] = path
        files[path] = FakeFile(size, sha256)
    }

    fun addBundled(cdnKey: String, size: Long, sha256: String) {
        val path = "/bundle/$cdnKey"
        bundledPaths[cdnKey] = path
        files[path] = FakeFile(size, sha256)
    }

    override suspend fun fetchText(url: String): String? {
        fetchedUrls += url
        return remoteTexts[url]
    }

    override suspend fun readCachedText(relativePath: String): String? = cachedTexts[relativePath]

    override suspend fun writeCachedText(relativePath: String, content: String) {
        cachedTexts[relativePath] = content
    }

    override suspend fun readBundledText(relativePath: String): String? = bundledTexts[relativePath]

    override suspend fun cachedAssetPath(cdnKey: String): String? = cachedPaths[cdnKey]

    override suspend fun bundledAssetPath(cdnKey: String): String? = bundledPaths[cdnKey]

    override suspend fun downloadToTemporaryFile(
        url: String,
        cdnKey: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): String? {
        downloadedKeys += cdnKey
        downloadError?.let { throw it }
        val file = downloads[cdnKey] ?: return null
        onProgress(file.size / 2, file.size)
        onProgress(file.size, file.size)
        val path = "/tmp/$cdnKey"
        files[path] = file
        return path
    }

    override suspend fun fileSize(absolutePath: String): Long? = files[absolutePath]?.size

    override suspend fun sha256(absolutePath: String): String? {
        sha256Requests++
        return files[absolutePath]?.sha256
    }

    override suspend fun commitDownloadedAsset(temporaryPath: String, cdnKey: String): String {
        val path = "/cache/$cdnKey"
        files[path] = files.getValue(temporaryPath)
        files.remove(temporaryPath)
        cachedPaths[cdnKey] = path
        return path
    }

    override suspend fun deleteFile(absolutePath: String): Boolean {
        deletedFiles += absolutePath
        return files.remove(absolutePath) != null
    }

    override suspend fun deleteCachedAsset(cdnKey: String): Boolean {
        deletedCachedKeys += cdnKey
        val path = cachedPaths.remove(cdnKey) ?: return false
        return files.remove(path) != null
    }
}

private fun manifestJson(version: Int = 1): String =
    """
    {
      "version": $version,
      "base_url": "https://cdn.example",
      "total_size": 10,
      "total_files": 2,
      "categories": {
        "audio": {"total_size": 10, "file_count": 2, "required": true}
      },
      "assets": {
        "audio/one file!.mp3": {
          "size": 4,
          "sha256": "hash-one",
          "category": "audio",
          "required": true
        },
        "audio/two.mp3": {
          "size": 6,
          "sha256": "hash-two",
          "category": "audio",
          "required": false
        }
      }
    }
    """.trimIndent()
