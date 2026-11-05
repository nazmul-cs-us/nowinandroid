package com.starception.submission.family

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.starception.submission.sync.workers.DelegatingWorker
import com.starception.submission.sync.workers.delegatedData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Retry token rotation after process/network interruptions without storing tokens in work data. */
@HiltWorker
class FamilyTokenWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val client: FamilyClient,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (client.credentials.load() == null) return Result.success()
        return try {
            val token = withContext(Dispatchers.IO) { Tasks.await(FirebaseMessaging.getInstance().token, 15, TimeUnit.SECONDS) }
            withContext(Dispatchers.Main.immediate) {
                if (!client.ensureSession()) return@withContext Result.retry()
                client.registerDevice(token)
                Result.success()
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("family-token-registration", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DelegatingWorker>()
                    .setInputData(FamilyTokenWorker::class.delegatedData())
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
