package com.starception.submission.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AssetDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadManager: AssetDownloadManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val category = inputData.getString(KEY_CATEGORY)
        val manifest = downloadManager.loadManifest()
            ?: return Result.failure(Data.Builder().putString("error", "No manifest").build())

        createNotificationChannel()

        val categories = if (category != null) {
            listOf(category)
        } else {
            // Download all required categories
            manifest.categories.filter { it.value.required }.map { it.key }
        }

        var totalBytes = 0L
        var downloadedBytes = 0L
        categories.forEach { cat ->
            manifest.categories[cat]?.let { totalBytes += it.totalSize }
        }

        for (cat in categories) {
            if (isStopped) return Result.failure()

            val success = downloadManager.downloadCategory(cat, manifest) { progress, downloaded, total ->
                val overallProgress = if (totalBytes > 0) {
                    ((downloadedBytes + downloaded).toFloat() / totalBytes).coerceIn(0f, 1f)
                } else 0f

                setForegroundAsync(createForegroundInfo(overallProgress, cat))
            }

            if (success) {
                downloadedBytes += manifest.categories[cat]?.totalSize ?: 0
            } else {
                Log.w(TAG, "Failed to download category: $cat")
            }
        }

        Log.i(TAG, "Download work completed. Downloaded $downloadedBytes bytes")
        return Result.success()
    }

    private fun createForegroundInfo(progress: Float, currentCategory: String): ForegroundInfo {
        val title = "Downloading content"
        val text = "${formatCategory(currentCategory)} - ${(progress * 100).toInt()}%"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asset Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Downloads essential app content"
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatCategory(category: String): String = when (category) {
        "quran_core" -> "Quran"
        "quran_translation" -> "Translations"
        "hadith" -> "Hadith"
        "json_data" -> "Islamic Data"
        "news" -> "News"
        "model_tts" -> "Text-to-Speech"
        "model_tts_kokoro" -> "TTS (Kokoro)"
        "model_tts_vits" -> "TTS (VITS)"
        "model_tts_ryan" -> "TTS (Ryan)"
        "model_tts_espeak" -> "TTS (eSpeak)"
        "model_asr" -> "Speech Recognition"
        "model_whisper" -> "Whisper"
        "model_kws" -> "Keyword Detection"
        else -> category
    }

    companion object {
        private const val TAG = "AssetDownloadWorker"
        private const val CHANNEL_ID = "asset_download"
        private const val NOTIFICATION_ID = 9001
        private const val KEY_CATEGORY = "category"
        const val WORK_NAME_ESSENTIAL = "asset_download_essential"
        const val WORK_NAME_PREFIX = "asset_download_"

        fun enqueueEssentialDownloads(context: Context) {
            val request = OneTimeWorkRequestBuilder<AssetDownloadWorker>()
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ESSENTIAL,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueCategory(context: Context, category: String) {
            val request = OneTimeWorkRequestBuilder<AssetDownloadWorker>()
                .setInputData(Data.Builder().putString(KEY_CATEGORY, category).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME_PREFIX$category",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
