package com.example.helpnearby.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val summary = syncManager.processPendingQueueOnce()
        return when {
            summary.skippedOffline -> Result.retry()
            summary.failed > 0 && runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
